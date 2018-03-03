package dk.schaumburgit.stillsequencecamera.camera2;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;
//import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import dk.schaumburgit.stillsequencecamera.IStillSequenceCamera;

/**
 * Implements a fast still sequence camera using the Android
 * Camera2 API (available from level 21).
 *
 * StillSequenceCamera2 can currently sustain a rate of 7-8 fps
 * (using YUV420_888 or JPEG on a Nexus5)
 *
 * Created by Thomas Schaumburg on 21-11-2015.
 */
public class StillSequenceCamera2 implements IStillSequenceCamera {
    private static final String TAG = "StillSequenceCamera2";
    private final Activity mActivity;

    private HandlerThread mFocusThread;
    private Handler mFocusHandler;
    private CameraCaptureSession mCaptureSession;
    private final String mCameraId;
    private CameraDevice mCameraDevice;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private FocusManager mFocusManager;
    private CaptureManager mImageCapture;
    private PreviewManager mPreview = null;

    private final static int CLOSED = 0;
    private final static int STOPPED = 1;
    private final static int OPENING = 2;
    private final static int STARTING = 3;
    private final static int CAPTURING = 4;
    private final static int STOPPING = 5;
    private final static int FOCUSING = 6;
    private final static int ERROR = 7;
    private int mState = CLOSED;
    private boolean mLockFocus = true;

    /**
    /**
     * Creates a #StillSequenceCamera2 with a preview
     *
     * @param activity The activity associated with the calling app.
     * @param camOptions minPixels The preferred minimum number of pixels in the captured images
     *                  (i.e. width*height)
     */
    public StillSequenceCamera2(Activity activity, StillSequenceCamera2Options camOptions)
    {
        if (activity==null)
            throw new NullPointerException("StillSequenceCamera2 requires an Activity");

        //if (camOptions.minPixels < 1024*768)
        //    camOptions.minPixels = 1024*768;

        this.mActivity = activity;

        mFocusManager = new FocusManager(activity);
        if (camOptions.preview !=null)
            mPreview = new PreviewManager(activity, camOptions.preview);
        mImageCapture = new CaptureManager(activity, mPreview, camOptions.minPixels);

        mState = CLOSED;

        // Choose a camera:
        // ================
        String selection = null;
        try {
            CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);

            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                selection = cameraId;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.e(TAG, "Camera2 API is not supported");
            throw new UnsupportedOperationException("Camera2 API is not supported");
        }
        mCameraId = selection;
    }

    @Override
    public Map<Integer, Double> getSupportedImageFormats() {
        return mImageCapture.getSupportedImageFormats(mCameraId);
    }

    /**
     * Chooses a back-facing camera satisfying the requirements from the constructor (i.e. format
     * and resolution).
     *
     *
     * @param imageFormat The preferred format to capture images in
     *                                (see #ImageFormat for values)
     *
     * @throws IllegalStateException if the StillSequenceCamera2 is in any but the CLOSED state.
     */
    @Override
    public void setup(int imageFormat)
            throws IllegalStateException
    {
        if (mState != CLOSED)
            throw new IllegalStateException("StillSequenceCamera2.setup() can only be called in the CLOSED state");

        try {
            mImageCapture.setup(mCameraId, imageFormat);
            mFocusManager.setup(mCameraId);
            if (mPreview!=null)
                mPreview.setup(mCameraId);
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.e(TAG, "Camera2 API is not supported");
            throw new UnsupportedOperationException("Camera2 API is not supported");
        }

        mState = STOPPED;
    }

    @Override
    public void start(final OnImageAvailableListener listener, final Handler callbackHandler)
    {
        if (mState == OPENING)
            return;

        if (mState == STARTING)
            return;

        if (mState == FOCUSING)
            return;

        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }

        mState = OPENING;
        Log.v(TAG, "start(): state => OPENING");

        final Handler _callbackHandler = callbackHandler == null ? new Handler() : callbackHandler;

        mFocusThread = new HandlerThread("CameraBackground");
        mFocusThread.start();
        mFocusHandler = new Handler(mFocusThread.getLooper());

        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);

        try {
            // Open camera and hook into our camera state machine:
            manager.openCamera(
                    mCameraId,
                    new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(CameraDevice cameraDevice) {
                            mCameraDevice = cameraDevice;
                            try {
                                // Here, we create a CameraCaptureSession for camera preview.
                                // (this may take several hundred milliseconds)
                                mState = STARTING;
                                Log.v(TAG, "start(): state => STARTING");
                                List<Surface> surfaces;
                                if (mPreview != null)
                                    surfaces = Arrays.asList(mFocusManager.getSurface(), mImageCapture.getSurface(), mPreview.getSurface());
                                else
                                    surfaces = Arrays.asList(mFocusManager.getSurface(), mImageCapture.getSurface());
                                mCameraDevice.createCaptureSession(
                                        //Arrays.asList(mFocusManager.getSurface(), mImageCapture.getSurface()),
                                        surfaces,
                                        new CameraCaptureSession.StateCallback() {
                                            @Override
                                            public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                                                // Special case: the camera is already closed
                                                // - probably due to some system error or
                                                // higher priority request:
                                                if (null == mCameraDevice) {
                                                    mCameraOpenCloseLock.release();
                                                    return;
                                                }
                                                mCaptureSession = cameraCaptureSession;
                                                mState = FOCUSING;
                                                mCameraOpenCloseLock.release();
                                                Log.v(TAG, "start(): state => FOCUSING");
                                                mFocusManager.start(
                                                        mCaptureSession,
                                                        mLockFocus,
                                                        mFocusHandler,
                                                        new FocusManager.FocusListener() {
                                                            @Override
                                                            public void focusLocked() {
                                                                mState = CAPTURING;
                                                                Log.v(TAG, "start(): state => CAPTURING");
                                                                if (mLockFocus)
                                                                    mFocusManager.stop();
                                                                mImageCapture.start(mCaptureSession, _callbackHandler, listener);
                                                            }

                                                            @Override
                                                            public void error(final Exception error) {
                                                                mState = ERROR;
                                                                Log.v(TAG, "start(): state => ERROR");
                                                                mFocusManager.stop();
                                                                if (listener != null)
                                                                    _callbackHandler.post(
                                                                            new Runnable() {
                                                                                @Override
                                                                                public void run() {
                                                                                    listener.onError(error);
                                                                                }
                                                                            }
                                                                    );
                                                            }
                                                        }
                                                );
                                            }

                                            @Override
                                            public void onConfigureFailed(
                                                    CameraCaptureSession cameraCaptureSession) {
                                                mState = ERROR;
                                                Log.v(TAG, "start(): state => ERROR");
                                                Log.e(TAG, "Failed");
                                                mCameraOpenCloseLock.release();
                                                if (listener != null)
                                                    mFocusHandler.post(
                                                            new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    listener.onError(null);
                                                                }
                                                            }
                                                    );
                                            }
                                        },
                                        null
                                );
                            } catch (CameraAccessException e) {
                                mState = ERROR;
                                mCameraOpenCloseLock.release();
                                Log.v(TAG, "start(): state => FAILED");
                                e.printStackTrace();
                                throw new UnsupportedOperationException("Camera access required 2: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onDisconnected(CameraDevice cameraDevice) {
                            //mCameraOpenCloseLock.release();
                            cameraDevice.close();
                            mCameraDevice = null;
                            mState = ERROR;
                            mCameraOpenCloseLock.release();
                            Log.v(TAG, "start(): state => ERROR");
                        }

                        @Override
                        public void onError(CameraDevice cameraDevice, int error) {
                            Log.e(TAG, "CameraDevice.StateCallback.onError(" + error + ")");
                            //mCameraOpenCloseLock.release();
                            cameraDevice.close();
                            mCameraDevice = null;
                            mState = ERROR;
                            mCameraOpenCloseLock.release();
                            Log.v(TAG, "start(): state => ERROR");
                        }

                    },
                    mFocusHandler
            );
        } catch (CameraAccessException e) {
            mState = ERROR;
            Log.v(TAG, "start(): state => ERROR½");
            mCameraOpenCloseLock.release();
            e.printStackTrace();
            throw new UnsupportedOperationException("CAMERA access required");
        } catch (SecurityException e) {
            mState = ERROR;
            Log.v(TAG, "start(): state => ERROR");
            mCameraOpenCloseLock.release();
            e.printStackTrace();
            throw new UnsupportedOperationException("CAMERA permission required");
        } catch (Exception e) {
            mState = ERROR;
            Log.v(TAG, "start(): state => ERROR");
            mCameraOpenCloseLock.release();
            e.printStackTrace();
            throw e;
        }
    }

    @Override
    public void stop()
    {
        if (mState == STOPPED)
            return;

        if (mState == STOPPING)
            return;

        if (mState == CLOSED)
            return;

        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }

        if (mState != CAPTURING && mState != FOCUSING)
            throw new IllegalStateException("stop(): StillSequenceCamera2.stop() can only be called in the STARTED state (" + mState + ")");

        mState = STOPPING;
        Log.v(TAG, "stop(): state => STOPPING");

        new Thread(new Runnable() {
            public void run() {

                try {
                    mFocusManager.stop();
                    mImageCapture.stop();
                    if (mCaptureSession != null) {
                        mCaptureSession.close();
                        mCaptureSession = null;
                    }
                    if (null != mCameraDevice) {
                        mCameraDevice.close();
                        mCameraDevice = null;
                    }
                } finally {
                    //mCameraOpenCloseLock.release();
                }

                if (mFocusThread != null) {
                    try {
                        mFocusThread.quitSafely();
                        mFocusThread.join();
                        mFocusThread = null;
                        mFocusHandler = null;
                    } catch (Exception e) {
                        Log.v(TAG, "stop(): state => A");
                        e.printStackTrace();
                    }
                }
                mState = STOPPED;
                Log.v(TAG, "stop(): state => STOPPED");
                mCameraOpenCloseLock.release();
            }
        }).start();
    }

    @Override
    public void close() {
        if (mState == CLOSED)
            return;

        if (mState == CAPTURING)
            stop();

        if (mState != STOPPED)
            throw new IllegalStateException("StillSequenceCamera2.close() can only be called in the STOPPED state");

        mFocusManager.close();
        mImageCapture.close();

        mState = CLOSED;
    }

    @Override
    public boolean isLockFocus() {
        return mLockFocus;
    }

    @Override
    public void setLockFocus(boolean lockFocus) {
        this.mLockFocus = lockFocus;
    }
}
