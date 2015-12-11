package dk.schaumburgit.stillsequencecamera.camera2;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
//import android.support.annotation.NonNull;
import android.util.Log;
import android.view.TextureView;

import java.util.Arrays;
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
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private FocusManager mFocusManager;
    private CaptureManager mImageCapture;

    private final static int CLOSED = 0;
    private final static int INITIALIZED = 1;
    private final static int CAPTURING = 2;
    private final static int CHANGING = 3;
    private final static int FOCUSING = 4;
    private final static int FAILED = 5;
    private final static int ERROR = 6;
    private int mState = CLOSED;
    private boolean mLockFocus = true;

    /**
     * Creates a headless #StillSequenceCamera2
     *
     * @param activity The activity associated with the calling app.
     */
    public StillSequenceCamera2(Activity activity)
    {
        this(activity, null, new int[] {ImageFormat.JPEG}, 1024*768);
    }

    /**
     * Creates a headless #StillSequenceCamera2
     *
     * @param activity The activity associated with the calling app.
     * @param prioritizedImageFormats The preferred formats to capture images in
     *                                (see #ImageFormat for values)
     * @param minPixels The preferred minimum number of pixels in the captured images
     *                  (i.e. width*height)
     */
    public StillSequenceCamera2(Activity activity, int[] prioritizedImageFormats, int minPixels)
    {
        this(activity, null, prioritizedImageFormats, minPixels);
    }

    /**
     * Creates a #StillSequenceCamera2 with a preview
     *
     * @param activity The activity associated with the calling app.
     * @param prioritizedImageFormats The preferred formats to capture images in
     *                                (see #ImageFormat for values)
     * @param minPixels The preferred minimum number of pixels in the captured images
     *                  (i.e. width*height)
     * @param textureView The #TextureView to display the preview in (use null for headless scanning)
     */
    public StillSequenceCamera2(Activity activity, TextureView textureView, int[] prioritizedImageFormats, int minPixels)
    {
        if (activity==null)
            throw new NullPointerException("StillSequenceCamera2 requires an Activity");

        if (minPixels < 1024*768)
            minPixels = 1024*768;

        this.mActivity = activity;

        mFocusManager = new FocusManager(activity, textureView);
        mImageCapture = new CaptureManager(activity, prioritizedImageFormats, minPixels);

        mState = CLOSED;
    }

    /**
     * Chooses a back-facing camera satisfying the requirements from the constructor (i.e. format
     * and resolution).
     *
     *
     *
     * @throws IllegalStateException if the StillSequenceCamera2 is in any but the CLOSED state.
     */
    @Override
    public void setup()
            throws IllegalStateException
    {
        if (mState != CLOSED)
            throw new IllegalStateException("StillSequenceCamera2.setup() can only be called in the CLOSED state");

        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);

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
                mCameraId = cameraId;
            }
            mImageCapture.setup(mCameraId);
            mFocusManager.setup(mCameraId);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.e(TAG, "Camera2 API is not supported");
            throw new UnsupportedOperationException("Camera2 API is not supported");
        }

        mState = INITIALIZED;
    }

    @Override
    public void start(final OnImageAvailableListener listener, Handler callbackHandler)
    {
        if (mState != INITIALIZED)
            throw new IllegalStateException("StillSequenceCamera2.start() can only be called in the INITIALIZED state");

        if (callbackHandler == null)
            callbackHandler = new Handler();
        final Handler _callbackHandler = callbackHandler;

        mFocusThread = new HandlerThread("CameraBackground");
        mFocusThread.start();
        mFocusHandler = new Handler(mFocusThread.getLooper());

        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);

        mState = CHANGING;
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            // Open camera and hook into our camera state machine:
            manager.openCamera(
                    mCameraId,
                    new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(CameraDevice cameraDevice) {
                            Log.d(TAG, "CameraDevice opened");
                            // This method is called when the camera is opened.  We start camera preview here.
                            mCameraOpenCloseLock.release();
                            mCameraDevice = cameraDevice;
                            try {
                                // Here, we create a CameraCaptureSession for camera preview.
                                // (this may take several hundred milliseconds)
                                mCameraDevice.createCaptureSession(
                                        Arrays.asList(mFocusManager.getSurface(), mImageCapture.getSurface()),
                                        new CameraCaptureSession.StateCallback() {
                                            @Override
                                            public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                                                // The camera is already closed
                                                if (null == mCameraDevice) {
                                                    return;
                                                }
                                                Log.d(TAG, "CameraDevice configured");
                                                mCaptureSession = cameraCaptureSession;
                                                mState = FOCUSING;
                                                mFocusManager.start(
                                                        mCaptureSession,
                                                        mLockFocus,
                                                        mFocusHandler,
                                                        new FocusManager.FocusListener() {
                                                            @Override
                                                            public void focusLocked() {
                                                                //startCapturePhase();
                                                                mState = CAPTURING;
                                                                mImageCapture.start(mCaptureSession, _callbackHandler, listener);
                                                                if (mLockFocus)
                                                                    mFocusManager.stop();
                                                            }

                                                            @Override
                                                            public void error(final Exception error) {
                                                                mState = ERROR;
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
                                                Log.e(TAG, "Failed");
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
                                mState = FAILED;
                                throw new UnsupportedOperationException("Camera access required");
                            }
                        }

                        @Override
                        public void onDisconnected(CameraDevice cameraDevice) {
                            mCameraOpenCloseLock.release();
                            cameraDevice.close();
                            mCameraDevice = null;
                        }

                        @Override
                        public void onError(CameraDevice cameraDevice, int error) {
                            Log.e(TAG, "CameraDevice.StateCallback.onError(" + error + ")");
                            mCameraOpenCloseLock.release();
                            cameraDevice.close();
                            mCameraDevice = null;
                            if (null != mActivity) {
                                mActivity.finish();
                            }
                        }

                    },
                    mFocusHandler
            );
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

    @Override
    public void stop()
    {
        if (mState == CLOSED)
            return;

        if (mState != CAPTURING)
            throw new IllegalStateException("StillSequenceCamera2.stop() can only be called in the STARTED state");

        mState = CHANGING;

        new Thread(new Runnable() {
            public void run() {

                try {
                    mFocusManager.stop();
                    mImageCapture.stop();
                    mCameraOpenCloseLock.acquire();
                    if (mCaptureSession != null) {
                        mCaptureSession.close();
                        mCaptureSession = null;
                    }
                    if (null != mCameraDevice) {
                        mCameraDevice.close();
                        mCameraDevice = null;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted while trying to lock camera for closing.", e);
                } finally {
                    mCameraOpenCloseLock.release();
                }

                if (mFocusThread != null) {
                    try {
                        mFocusThread.quitSafely();
                        mFocusThread.join();
                        mFocusThread = null;
                        mFocusHandler = null;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                mState = INITIALIZED;
            }
        }).start();
    }

    @Override
    public void close() {
        if (mState == CLOSED)
            return;

        if (mState == CAPTURING)
            stop();

        if (mState != INITIALIZED)
            throw new IllegalStateException("StillSequenceCamera2.close() can only be called in the INITIALIZED state");

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
