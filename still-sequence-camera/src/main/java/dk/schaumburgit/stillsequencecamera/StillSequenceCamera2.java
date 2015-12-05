package dk.schaumburgit.stillsequencecamera;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
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
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
//import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Implements a fast still sequence camera using the Android
 * Camera2 API (available from level 21).
 *
 * StillSequenceCamera2 can currently sustain a rate of 7-8 fps
 * (using YUV420_888 or JPEG on a Nexus5)
 *
 * Created by Thomas on 21-11-2015.
 */
public class StillSequenceCamera2 implements IStillSequenceCamera {
    private static final String TAG = "StillSequenceCamera2";
    private Activity mActivity;
    private Activity getActivity() {
        return mActivity;
    }

    private int mMinPixels;
    private int[] mPrioritizedFormats;
    private int mOutputFormat;

    public StillSequenceCamera2(Activity activity, TextureView textureView, int[] prioritizedImageFormats, int minPixels) {
        this.mMinPixels = minPixels;
        if (mMinPixels < 1024*768)
            mMinPixels = 1024*768;

        this.mPrioritizedFormats = prioritizedImageFormats;
        this.mActivity = activity;
        this.mTextureView = textureView;
    }

    @Override
    public void StartCapture() {
        startFocusThread();
        // This will send the camera into the Setup state, from which it will eventually
        // move into the Focusing and then Capturing states:
        startSetup();
    }

    @Override
    public Image GetLatestImage() {
        // Caller should do:
        // Image image = stillequenceCamera.GetLatestImage();
        // if (image != null) {
        //    ...quick as you can, extract what you need from image, so it can be closed:
        //    image.close();
        //    ...now do any lengthy processing
        // }

        try {
            // Important for performance: start the new capture as
            // soon as possible:
            // (capture is done using dedicated hardware, so it might
            // as well run while the CPU-intensive image processing
            // is performed in callImageListener)
            Image image = mImageReader.acquireLatestImage();
            if (image != null) {
                captureNext();
            }
            return image;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void StopCapture() {
        new Thread(new Runnable() {
            public void run() {
                //unlockFocus();
                closeCamera();
                stopFocusThread();
            }
        }).start();
    }

    private OnImageAvailableListener mImageListener = null;

    @Override
    public void setImageListener(OnImageAvailableListener listener) {
        mImageListener = listener;
    }

    private boolean callImageListener()
    {
        if (mImageListener != null) {
            mImageListener.onImageAvailable();
            return true;
        }

        return false;
    }

    //*********************************************************************
    //* Start and stop:
    //* ===============
    //*********************************************************************


    private void onResume() {
        // We used to auto-start a lot here (open camera, start focusing, etc),
        // but have moved to requiring an explicit call to startCapture.

        // startCapture();
    }

    //@Override
    public void onPause() {
        StopCapture();
    }

    private HandlerThread mFocusThread;
    private Handler mFocusHandler;
    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startFocusThread() {
        mFocusThread = new HandlerThread("CameraBackground");
        mFocusThread.start();
        mFocusHandler = new Handler(mFocusThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopFocusThread() {
        if (mFocusThread == null)
            return;
        try {
            mFocusThread.quitSafely();
            mFocusThread.join();
            mFocusThread = null;
            mFocusHandler = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //*********************************************************************
    //* Setup phase: Open and initialize camera:
    //* =======================================
    //* The entry points to this section are
    //*   startSetup()                      - called from onResume()
    //*   closeCamera()                     - called from onPause()
    //*   configureTransform(width, height) - called whenever the preview
    //*                                       window (if any) resizes
    //*********************************************************************

    private String mCameraId;
    private CameraDevice mCameraDevice;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private void startSetup() {
        if (mTextureView == null) {
            openCamera();
        } else if (mTextureView.isAvailable()) {
            // When the screen is turned off and turned back on, the SurfaceTexture is already
            // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
            // a camera and start preview from here (otherwise, we wait until the surface is ready in
            // the SurfaceTextureListener).
            openCamera();
            configureTransform();
        } else {
            mTextureView.setSurfaceTextureListener(
                    new TextureView.SurfaceTextureListener() {
                        @Override
                        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                            openCamera();
                        }
                        @Override
                        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
                            configureTransform();
                        }
                        @Override
                        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                            return true;
                        }
                        @Override
                        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
                        }
                    }
            );
        }
    }

    private void openCamera() {
        setUpCameraOutputs();
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                closeCameraOutputs(); // reverse of setUpCameraOutputs()
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            // Open camera and hook into our camera state machine:
            manager.openCamera(mCameraId, mStateCallback, mFocusHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException("CAMERA permission required");
        } catch (SecurityException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException("CAMERA permission required");
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets up member variables related to camera.
     */
    private void setUpCameraOutputs() {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        try {
            // Choose a camera:
            // ================
            StreamConfigurationMap map = null;
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                mCameraId = cameraId;
            }

            // Choose an output format:
            // ========================
            mOutputFormat = ImageFormat.JPEG;
            if (mPrioritizedFormats != null) {
                for (int preferredFormat : mPrioritizedFormats) {
                    if (map.isOutputSupportedFor(preferredFormat)) {
                        mOutputFormat = preferredFormat;
                        break;
                    }
                }
            }

            // Set up the still image reader:
            // ==============================
            List<Size> choices = Arrays.asList(map.getOutputSizes(mOutputFormat));

            // We'll prefer the smallest size larger than 1024*768
            List<Size> bigEnough = new ArrayList<>();
            for (Size option : choices) {
                if (option.getWidth() * option.getHeight() >= 1024 * 768) {
                    bigEnough.add(option);
                }
            }

            Size captureSize = null;
            if (bigEnough.isEmpty())
                captureSize = Collections.max(choices, new CompareSizesByArea());
            else
                captureSize = Collections.min(bigEnough, new CompareSizesByArea());

            // Set up the target of the still images:
            mImageReader = ImageReader.newInstance(captureSize.getWidth(), captureSize.getHeight(),
                    mOutputFormat, /*maxImages*/2);
            mImageReader.setOnImageAvailableListener(
                    mOnImageAvailableListener, mFocusHandler);

            // Set up the preview target:
            // ==========================
            // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
            // garbage capture data.
            // We fit the aspect ratio of TextureView to the size of preview we picked.
            if (mTextureView != null) {
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        mTextureView.getWidth(), mTextureView.getHeight(), captureSize);

                int orientation = getActivity().getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    //mTextureView.setAspectRatio(
                    //        mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    //mTextureView.setAspectRatio(
                    //        mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }
            }

            return;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.e(TAG, "Camera2 API is not supported");
            throw new UnsupportedOperationException("Camera2 API is not supported");
        }
    }

    /**
     * Clean up after setUpCameraOutputs
     */
    private void closeCameraOutputs()
    {
        //mImageReader.setOnImageAvailableListener(null, null);
        mImageReader = null;
    }
    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     */
    private void configureTransform() {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }

        int viewWidth = mTextureView.getWidth();
        int viewHeight = mTextureView.getHeight();

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened( CameraDevice cameraDevice) {
            Log.d(TAG, "CameraDevice opened");
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected( CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError( CameraDevice cameraDevice, int error) {
            Log.e(TAG, "CameraDevice.StateCallback.onError(" + error + ")");
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            mIsCapturing = false;
            if (null != mCaptureSession) {
                mCaptureSession.close();
                try {
                    mCaptureSession.stopRepeating();
                } catch (Exception e) {};
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
            if (null != mPreviewImageReader) {
                mPreviewImageReader.close();
                mPreviewImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
        onCameraStateChanged();
    }

    //*********************************************************************
    //* Start the preview:
    //* ==================
    //* Entry points into this section:
    //*   createCameraPreviewSession - called by mStateCallback event handler
    //*                                (in the "Open and initialize camera"
    //*                                section)
    //*********************************************************************

    private ImageReader mPreviewImageReader;

    /**
     * An {@link TextureView} for camera preview.
     */
    private TextureView mTextureView;

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            Surface surface = null;
            if (mTextureView != null) {
                SurfaceTexture texture = mTextureView.getSurfaceTexture();
                assert texture != null;

                // We configure the size of default buffer to be the size of camera preview we want.
                texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

                // This is the output Surface we need to start preview.
                surface = new Surface(texture);
            } else {
                mPreviewImageReader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 2); //fps * 10 min
                mPreviewImageReader.setOnImageAvailableListener(
                        new ImageReader.OnImageAvailableListener() {
                            @Override
                            public void onImageAvailable(ImageReader reader) {
                                Image image = reader.acquireLatestImage();
                                if (image != null)
                                    image.close();
                            }
                        }, null);
                surface = mPreviewImageReader.getSurface();
            }

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            // (this may take several hundred milliseconds)
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured( CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            Log.d(TAG, "CameraDevice configured");

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mFocusHandler);
                                StillSequenceCamera2.this.onSetupDone();
                            } catch (Exception e) {
                                mCaptureSession = null;
                                StillSequenceCamera2.this.onSetupFailed();
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                 CameraCaptureSession cameraCaptureSession) {
                            StillSequenceCamera2.this.onSetupFailed();
                            Log.e(TAG, "Failed");
                        }
                    }, null
            );
        } catch (Exception e) {
            mPreviewRequestBuilder = null;
            //mPreviewImageReader.setOnImageAvailableListener(null, null);
            mPreviewImageReader = null;
            e.printStackTrace();
        }
    }

    private void onSetupDone()
    {
        startFocusing();
    }

    private void onSetupFailed()
    {}

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
    //*   - Instantiated (STATE_PREVIEW) when this class is instantiated
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

    /*
     * Camera states:
     */
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_LOCK = 1;
    private static final int STATE_WAITING_PRECAPTURE = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void startFocusing() {
        try {
            mAutoFocusState = null;
            mAutoExposureState = null;

            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);

            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mFocusHandler);

            onCameraStateChanged();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mAutoFocusState = null;

            if (mPreviewRequestBuilder == null)
                return;
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mAutoExposureState = null;
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            if (mCaptureSession == null)
                return;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mFocusHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mFocusHandler);

            onCameraStateChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Integer mAutoFocusState = null; // From CaptureResult.get(CaptureResult.CONTROL_AF_STATE)
    private Integer mAutoExposureState = null; // From CaptureResult.get(CaptureResult.CONTROL_AE_STATE)
    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to image capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    mAutoFocusState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (mAutoFocusState == null) {
                        StillSequenceCamera2.this.onFocusingDone();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == mAutoFocusState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == mAutoFocusState) {
                        // CONTROL_AE_STATE can be null on some devices
                        mAutoExposureState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (mAutoExposureState == null ||
                                mAutoExposureState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            StillSequenceCamera2.this.onFocusingDone();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    onCameraStateChanged();
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    mAutoExposureState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (mAutoExposureState == null ||
                            mAutoExposureState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            mAutoExposureState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    onCameraStateChanged();
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    mAutoExposureState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (mAutoExposureState == null || mAutoExposureState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        StillSequenceCamera2.this.onFocusingDone();
                    }
                    onCameraStateChanged();
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed( CameraCaptureSession session,
                                         CaptureRequest request,
                                         CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted( CameraCaptureSession session,
                                        CaptureRequest request,
                                        TotalCaptureResult result) {
            process(result);
        }

    };

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #startFocusing()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mFocusHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void onFocusingDone()
    {
        mState = STATE_PICTURE_TAKEN;
        startCapturePhase();
    }

    //*********************************************************************
    //* Sending camera state change events:
    //* ===================================
    //*
    //*********************************************************************

    @Override
    public void setCameraStateChangeListener(CameraStateChangeListener listener)
    {
        mCameraStateChangeListener = listener;
    }

    private CameraStateChangeListener mCameraStateChangeListener;
    private void onCameraStateChanged() {
        final CameraStateChangeListener tmp = mCameraStateChangeListener;
        if (tmp != null)
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    tmp.onCameraStateChanged(mAutoFocusState, mAutoExposureState, mIsCapturing);
                }
            });
    }

    //*********************************************************************
    //* Still image capture:
    //* ====================
    //*
    //*********************************************************************
    private boolean mIsCapturing = false;

    private void startCapturePhase() {
        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            mCaptureSession.stopRepeating();
            mStillCaptureRequest = captureBuilder.build();

            captureNext();

            mIsCapturing = true;
            onCameraStateChanged();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CaptureRequest mStillCaptureRequest = null;
    private void captureNext() {
        if (mCaptureSession == null) {
            return;
        }
        try {
            mCameraOpenCloseLock.acquire();
            mCaptureSession.capture(mStillCaptureRequest, null, null);
        } catch (IllegalStateException e) {
            // this happens if captureNext is called at the same time as closeCamera()
            // calls mCaptureSession.close()
        } catch (InterruptedException e) {
            // hmm...probably part of app shutdown, so we'll just go quitly
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    //*********************************************************************
    //* Processing the captured images:
    //* ===============================
    //* Entry points to this section:
    //*   mImageReader              - created by setUpCameraOutputs (in the
    //*                               "Open and initialize camera" section)
    //*   mOnImageAvailableListener - hooked up with mImageReader by
    //*                               setUpCameraOutputs (in the "Open and
    //*                               initialize camera" section)
    //*********************************************************************
    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            // Important for performance: start the new capture as
            // soon as possible:
            // (capture is done using dedicated hardware, so it might
            // as well run while the CPU-intensive image processing
            // is performed in callImageListener)
            captureNext();
            if (!callImageListener()) {
                Image image = reader.acquireLatestImage();
                if (image != null)
                    image.close();
            }
        }
    };

    //************************************************************************
    //************************************************************************
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }
}
