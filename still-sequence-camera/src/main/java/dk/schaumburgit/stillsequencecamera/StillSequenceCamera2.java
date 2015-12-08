package dk.schaumburgit.stillsequencecamera;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
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
import java.util.concurrent.TimeUnit;

import dk.schaumburgit.stillsequencecamera.camera2.FocusManager;

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
    private FocusManager mFocusManager;

    private static final String TAG = "StillSequenceCamera2";
    private Activity mActivity;
    private Activity getActivity() {
        return mActivity;
    }

    private int mMinPixels = 1024 * 768;
    private int[] mPrioritizedFormats;
    private int mOutputFormat;

    public StillSequenceCamera2(Activity activity, TextureView textureView, int[] prioritizedImageFormats, int minPixels) {
        mFocusManager = new FocusManager(activity, textureView);

        this.mMinPixels = minPixels;
        if (mMinPixels < 1024*768)
            mMinPixels = 1024*768;

        this.mPrioritizedFormats = prioritizedImageFormats;
        this.mActivity = activity;
    }

    @Override
    public void setup() {
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

            // We'll prefer the smallest size larger than mMinPixels (default 1024*768)
            List<Size> bigEnough = new ArrayList<>();
            for (Size option : choices) {
                if (option.getWidth() * option.getHeight() >= mMinPixels) {
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
    public void start() {
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

        mImageReader.setOnImageAvailableListener(
                mOnImageAvailableListener, mFocusHandler);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                closeCameraOutputs(); // reverse of setupPreview()
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
                                        Arrays.asList(mFocusManager.getPreviewSurface(), mImageReader.getSurface()),
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
                                                        cameraCaptureSession,
                                                        mFocusHandler,
                                                        new FocusManager.FocusListener() {
                                                            @Override
                                                            public void focusLocked() {
                                                                startCapturePhase();
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
                            Activity activity = getActivity();
                            if (null != activity) {
                                activity.finish();
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
                    mCameraOpenCloseLock.acquire();
                    mIsCapturing = false;
                    if (mCaptureSession != null) {
                        mCaptureSession.close();
                        mCaptureSession = null;
                    }
                    if (null != mCameraDevice) {
                        mCameraDevice.close();
                        mCameraDevice = null;
                    }
                    if (null != mImageReader) {
                        mImageReader.setOnImageAvailableListener(null, null);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
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
        if (mImageReader != null) {
            mImageReader.setOnImageAvailableListener(null, null);
            mImageReader.close();
            mImageReader = null;
        }
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

    private HandlerThread mFocusThread;
    private Handler mFocusHandler;

    //*********************************************************************
    //* Setup phase: Open and initialize camera:
    //* =======================================
    //* The entry points to this section are
    //*   Setup()                      - called from onResume()
    //*   closeCamera()                     - called from onPause()
    //*   configureTransform(width, height) - called whenever the preview
    //*                                       window (if any) resizes
    //*********************************************************************

    private String mCameraId;
    private CameraDevice mCameraDevice;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Clean up after setupPreview
     */
    private void closeCameraOutputs()
    {
        mImageReader.setOnImageAvailableListener(null, null);
        mImageReader = null;
    }


    //*********************************************************************
    //* Sending camera state change events:
    //* ===================================
    //*
    //*********************************************************************

    @Override
    public void setCameraStateChangeListener(CameraStateChangeListener listener)
    {
        //mCameraStateChangeListener = listener;
    }

    //private CameraStateChangeListener mCameraStateChangeListener;
    //private void onCameraStateChanged() {
    //    final CameraStateChangeListener tmp = mCameraStateChangeListener;
    //   if (tmp != null)
    //        AsyncTask.execute(new Runnable() {
    //            @Override
    //            public void run() {
    //                tmp.onCameraStateChanged(mAutoFocusState, mAutoExposureState, mIsCapturing);
    //            }
    //        });
    //}

    //*********************************************************************
    //* Still image capture:
    //* ====================
    //*
    //*********************************************************************

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

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

            mStillCaptureRequest = captureBuilder.build();

            captureNext();

            mIsCapturing = true;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

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
    //*   mImageReader              - created by setupPreview (in the
    //*                               "Open and initialize camera" section)
    //*   mOnImageAvailableListener - hooked up with mImageReader by
    //*                               setupPreview (in the "Open and
    //*                               initialize camera" section)
    //*********************************************************************
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
