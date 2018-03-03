package dk.schaumburgit.stillsequencecamera.camera2;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadFactory;

/**
 * Created by Thomas Schaumburg on 05-12-2015.
 */
public class FocusManager {
    private static final String TAG = "FocusManager";
    private final Activity mActivity;

    public FocusManager(Activity activity)
    {
        this.mActivity = activity;
    }

    private ImageReader mPreviewImageReader;
    private FocusingStateMachine mStateMachine;

    /**
     * Sets up member variables related to the on-screen preview (if any).
     * @param cameraId Id of the camera
     */
    public void setup(String cameraId) {
        try {
            mPreviewImageReader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 2);
            mPreviewImageReader.setOnImageAvailableListener(
                    new ImageReader.OnImageAvailableListener() {
                        @Override
                        public void onImageAvailable(ImageReader reader) {
                            Image image = reader.acquireLatestImage();
                            if (image != null)
                                image.close();
                        }
                    }, null);
            mPreviewSurface = mPreviewImageReader.getSurface();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.e(TAG, "Camera2 API is not supported");
            throw new UnsupportedOperationException("Camera2 API is not supported");
        } catch (Exception e) {
            if (mPreviewImageReader != null) {
                mPreviewImageReader.setOnImageAvailableListener(null, null);
                mPreviewImageReader = null;
            }
            if (mPreviewSurface != null)
            {
                mPreviewSurface.release();
                mPreviewSurface = null;
            }
            e.printStackTrace();
        }
    }

    public void close() {
        if (mPreviewImageReader != null)
            mPreviewImageReader.setOnImageAvailableListener(null, null);
    }


    private Surface mPreviewSurface = null;

    public Surface getSurface() {
        return mPreviewSurface;
    }

    //*********************************************************************
    //* The AF/AE state machine:
    //* =========================
    //* When image capture is started, the state machine starts auto-focus (AF)
    //* and auto-exposure (AE) measurements, so the images can be focused
    //* and well-exposed.
    //* When the camera has achieved focus and exposure lock (or has gotten
    //* as close as it can), the state machine will lock the AF and AE settings,
    //* and start capturing still images.
    //*
    //* Entry points into this section:
    //*   - Instantiated (STATE_IDLE) when this class is instantiated
    //*   - startFocusing() starts the search for focus and exposure locks:
    //*     - every time the repeating preview request mPreviewRequest
    //*       (started by createCameraPreviewSession() in section "Start the
    //*       preview") captures an image, the state machine polls the AF and
    //*       AE states.
    //*     - when focus is achieved, ...
    //*   - unlockFocus() stops the focus and exposure search
    //*
    //* Calls to other sections:
    //*   - captureStillPicture() is called when focus and exposure is
    //*     adjusted
    //*********************************************************************

    public static interface FocusListener
    {
        public void focusLocked();
        public void error(Exception error);
    }

    private CameraCaptureSession mCameraCaptureSession;

    /**
     *
     * @param cameraCaptureSession Session
     * @param callbackHandler the handler on which the listener should be invoked, or
     * {@code null} to use the current thread's {@link android.os.Looper
     * looper}.
     * @param listener Listener receiving callbacks
     * @param lockFocus l
     */
    public void start(CameraCaptureSession cameraCaptureSession, boolean lockFocus, Handler callbackHandler, FocusListener listener)
    {
        mCameraCaptureSession = cameraCaptureSession;
        // When the session is ready, we start displaying the preview.
        try {
            Log.i(TAG, "StartFocusing");
            mStateMachine = new FocusingStateMachine(cameraCaptureSession, lockFocus, callbackHandler, listener);
            mStateMachine.start(mPreviewSurface);
        } catch (Exception e) {
            if (cameraCaptureSession != null) {
                stop();//cameraCaptureSession.stopRepeating();
            }
            //mPreviewImageReader.setOnImageAvailableListener(null, null);
            mPreviewImageReader = null;
            e.printStackTrace();
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    public void stop() {
        if (mStateMachine == null)
            return;

        try {
            mStateMachine.close();
            mStateMachine = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class FocusingStateMachine extends CameraCaptureSession.CaptureCallback {
        private final FocusListener mListener;
        private final Handler mCallbackHandler;
        private final CameraCaptureSession mCaptureSession;
        private final boolean mLockFocus;

        private HandlerThread mFocusingStateMachineThread;
        private Handler mFocusingStateMachineHandler;
        private CaptureRequest.Builder mPreviewRequestBuilder;

        /*
        * Focusing states:
        */
        private static final int STATE_IDLE = 0;
        private static final int STATE_WAITING_LOCK = 1;
        private static final int STATE_WAITING_PRECAPTURE = 2;
        private static final int STATE_WAITING_NON_PRECAPTURE = 3;
        private static final int STATE_PICTURE_TAKEN = 4;
        private int mState = STATE_IDLE;

        FocusingStateMachine(CameraCaptureSession cameraCaptureSession, boolean lockFocus, Handler callbackHandler, FocusListener listener) {
            mState = STATE_IDLE;
            mLockFocus = lockFocus;
            mCaptureSession = cameraCaptureSession;
            mListener = listener;
            if (callbackHandler != null) {
                mCallbackHandler = callbackHandler;
            } else {
                mCallbackHandler = new Handler();
            }

            mFocusingStateMachineThread = new HandlerThread("Camera Focus Background");
            mFocusingStateMachineThread.start();
            mFocusingStateMachineHandler = new Handler(mFocusingStateMachineThread.getLooper());

            // We set up a CaptureRequest.Builder with the output Surface.
            try {
                mPreviewRequestBuilder
                        = mCaptureSession.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            }
            catch (CameraAccessException cae)
            {
                onError(cae);
            }
        }

        void start(Surface previewSurface)
        {
            if (mPreviewRequestBuilder == null)
                throw new IllegalStateException("There was an error when setting up the FocusManager.FocusingStateMachine - did you ignore an exception from the constructor....?");

            try {
                mPreviewRequestBuilder.addTarget(previewSurface);

                // Auto focus should be continuous for camera preview.
                mPreviewRequestBuilder.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                );

                // Automatic exposure control, NO FLASH
                mPreviewRequestBuilder.set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON
                );

                // Now, we start streaming frames from the camera:
                mCaptureSession.setRepeatingRequest(
                        mPreviewRequestBuilder.build(),
                        mStateMachine,
                        mFocusingStateMachineHandler
                );

                // This is how to tell the camera to attempt a focus lock:
                mPreviewRequestBuilder.set(
                        CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_START
                );

                // Tell the state machine to wait for the focus lock.
                mState = STATE_WAITING_LOCK;

                // Send a single request to the camera with the focus-lock instruction:
                mCaptureSession.capture(
                        mPreviewRequestBuilder.build(),
                        mStateMachine,
                        mFocusingStateMachineHandler
                );
            }
            catch (CameraAccessException cae)
            {
                onError(cae);
            }
            catch (Exception e)
            {
                onError(e);
            }
        }

        public void close()
        {
            mState = STATE_IDLE;
            if (mPreviewRequestBuilder != null) {
                mPreviewRequestBuilder.set(
                        CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
                );
                mPreviewRequestBuilder.set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON
                );

                if (mCameraCaptureSession != null) {
                    try {
                        // Send request to camera:
                        mCameraCaptureSession.capture(
                                mPreviewRequestBuilder.build(),
                                null, // => no callbacks
                                null // no callbacks => callback handler irrelevant
                        );
                        mCaptureSession.setRepeatingRequest(
                                mPreviewRequestBuilder.build(),
                                null, // => no callbacks
                                null // no callbacks => callback handler irrelevant
                        );

                        mCameraCaptureSession.stopRepeating();
                        Log.i(TAG, "Stopped repeating");

                    } catch (CameraAccessException cae) {
                        onError(cae);
                    }

                    mCameraCaptureSession = null;
                }

                mPreviewRequestBuilder = null;
            }

        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session,
                                        CaptureRequest request,
                                        CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session,
                                       CaptureRequest request,
                                       TotalCaptureResult result) {
            process(result);
        }

        @Override
        public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
            if (mState == STATE_IDLE)
            {
                if (mFocusingStateMachineThread != null) {
                    Log.i(TAG, "Killed focusing thread");
                    try {
                        // At this point, all camera requests have been
                        // cancelled, and no new images will enter the
                        // camera pipeline.
                        //
                        // All we need to do now is to clean up the focusing
                        // thread and handler.
                        //
                        // ...BUT there will still be a couple of images
                        // trickling thorough the camera pipeline, which
                        // will cause ugly exceptions on their arrival if
                        // we do the cleanup now.
                        //
                        // SO instead we'll schedule the cleanup to happen
                        // in a few seconds, when the pipeline is presumed
                        // to be clear.
                        //
                        // Ugly? Oh yes
                        final Handler handler = mFocusingStateMachineHandler;
                        final HandlerThread thread = mFocusingStateMachineThread;
                        mFocusingStateMachineThread = null;
                        mFocusingStateMachineHandler = null;
                        Timer timer = new Timer();
                        timer.schedule(
                                new TimerTask() {
                                    @Override
                                    public void run() {
                                        try {
                                            handler.removeCallbacksAndMessages(null);
                                            thread.quitSafely();
                                            thread.join();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                    }
                                },
                                1500
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void process(CaptureResult result) {
            Log.i(TAG, "FocusingStateMachine.process() mState = " + mState);
            switch (mState) {
                case STATE_IDLE: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer autoFocusState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (autoFocusState == null) {
                        onFocusLocked();
                        mState = STATE_PICTURE_TAKEN;
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == autoFocusState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == autoFocusState) {
                        // CONTROL_AE_STATE can be null on some devices
                        autoFocusState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (autoFocusState == null ||
                                autoFocusState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            onFocusLocked();
                            mState = STATE_PICTURE_TAKEN;
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer autoExposureState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (autoExposureState == null ||
                            autoExposureState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            autoExposureState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer autoExposureState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (autoExposureState == null || autoExposureState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        onFocusLocked();
                        mState = STATE_PICTURE_TAKEN;
                    }
                    break;
                }
            }
        }

        /**
         * Run the precapture sequence for capturing a still image.
         */
        private void runPrecaptureSequence() {
            try {
                // This is how to tell the camera to trigger.
                mPreviewRequestBuilder.set(
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
                );
                // Tell #mCaptureCallback to wait for the precapture sequence to be set.
                mState = STATE_WAITING_PRECAPTURE;
                mCaptureSession.capture(
                        mPreviewRequestBuilder.build(),
                        null, // the repeating request will generate the necessary callbacks
                        null // no callback => no handler needed
                );
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        private void onFocusLocked()
        {
            Log.i(TAG, "focus lock");
            try {
                if (mLockFocus)
                    mCaptureSession.stopRepeating();
                if (mListener != null) {
                    mCallbackHandler.post(
                            new Runnable() {
                                @Override
                                public void run() {
                                    mListener.focusLocked();
                                }
                            }
                    );
                }
            } catch (CameraAccessException e)
            {
                e.printStackTrace();
                throw new UnsupportedOperationException("Camera access required 3");
            }
        }

        private void onError(final Exception error)
        {
            Log.e(TAG, "focusing error");
            try {
                mCaptureSession.stopRepeating();
                if (mListener != null) {
                    mCallbackHandler.post(
                            new Runnable() {
                                @Override
                                public void run() {
                                    mListener.error(error);
                                }
                            }
                    );
                }
            } catch (CameraAccessException e)
            {
                e.printStackTrace();
                throw new UnsupportedOperationException("Camera access required 4");
            }
        }
    }
}
