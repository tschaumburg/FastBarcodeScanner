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
import android.media.Image;
import android.os.Build;
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

    /**
     *
     * @param activity
     */
    public StillSequenceCamera2(Activity activity)
    {
        this(activity, null, new int[] {ImageFormat.JPEG}, 1024*768);
    }

    public StillSequenceCamera2(Activity activity, int[] prioritizedImageFormats, int minPixels)
    {
        this(activity, null, prioritizedImageFormats, minPixels);
    }

    public StillSequenceCamera2(Activity activity, TextureView textureView, int[] prioritizedImageFormats, int minPixels)
    {
        if (activity==null)
            throw new NullPointerException("StillSequenceCamera2 requires an Activity");

        if (minPixels < 1024*768)
            minPixels = 1024*768;

        this.mActivity = activity;

        mFocusManager = new FocusManager(activity, textureView);
        mImageCapture = new CaptureManager(activity, prioritizedImageFormats, minPixels);
    }

    @Override
    public void setup() {
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

                map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                mCameraId = cameraId;
            }

            mImageCapture.setup(mCameraId);
            mFocusManager.setup(mCameraId);

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

    @Override
    public void start(final OnImageAvailableListener listener) {
        // This will send the camera into the Setup state, from which it will eventually
        // move into the Focusing and then Capturing states:

        //if (mTextureView == null) {
        //      openCamera();
        //} else if (mTextureView.isAvailable()) {
        //    // When the screen is turned off and turned back on, the SurfaceTexture is already
        //    // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        //    // a camera and start preview from here (otherwise, we wait until the surface is ready in
        //    // the SurfaceTextureListener).
        //    openCamera();
        //    configureTransform();
        //} else {
        //    mTextureView.setSurfaceTextureListener(
        //            new TextureView.SurfaceTextureListener() {
        //                @Override
        //                public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        //                    openCamera();
        //                }
        //                @Override
        //                public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        //                    configureTransform();
        //                }
        //                @Override
        //                public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        //                    return true;
        //                }
        //                @Override
        //                public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        //                }
        //            }
        //    );
        //}

        mFocusThread = new HandlerThread("CameraBackground");
        mFocusThread.start();
        mFocusHandler = new Handler(mFocusThread.getLooper());

        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
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
                                                mFocusManager.start(
                                                        mCaptureSession,
                                                        mFocusHandler,
                                                        new FocusManager.FocusListener() {
                                                            @Override
                                                            public void focusLocked() {
                                                                //startCapturePhase();
                                                                mImageCapture.start(mCaptureSession, mFocusHandler, listener);
                                                                mFocusManager.stop(mFocusHandler);
                                                            }
                                                        }
                                                );
                                            }

                                            @Override
                                            public void onConfigureFailed(
                                                    CameraCaptureSession cameraCaptureSession) {
                                                Log.e(TAG, "Failed");
                                            }
                                        },
                                        null
                                );
                            } catch (CameraAccessException e) {
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
    public void stop() {
        new Thread(new Runnable() {
            public void run() {

                try {
                    mFocusManager.stop(mFocusHandler);
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
            }
        }).start();
    }

    @Override
    public void close() {
        mFocusManager.close();
        mImageCapture.close();
    }
}
