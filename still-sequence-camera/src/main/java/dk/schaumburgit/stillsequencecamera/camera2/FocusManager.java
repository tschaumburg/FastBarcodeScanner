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
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Thomas on 05-12-2015.
 */
public class FocusManager {
    private static final String TAG = "FocusManager";
    private final Activity mActivity;
    //private Handler mFocusHandler;

    public FocusManager(Activity activity, TextureView textureView)
    {
        this.mActivity = activity;
        this.mTextureView = textureView;
    }

    /**
     * An {@link TextureView} for camera preview.
     */
    private TextureView mTextureView;
    private Size mPreviewSize;
    private ImageReader mPreviewImageReader;

    /**
     * Sets up member variables related to the on-screen preview (if any).
     * @param cameraId
     */
    public void setup(String cameraId) {
        try {
            CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
            StreamConfigurationMap map = manager
                    .getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map == null) {
                throw new UnsupportedOperationException("Insufficient camera info available");
            }

            // Set up the preview target:
            // ==========================
            // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
            // garbage capture data.
            // We fit the aspect ratio of TextureView to the size of preview we picked.
            if (mTextureView != null) {
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        mTextureView.getWidth(), mTextureView.getHeight());//, captureSize);

                int orientation = mActivity.getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    //mTextureView.setAspectRatio(
                    //        mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    //mTextureView.setAspectRatio(
                    //        mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                mTextureView.setSurfaceTextureListener(
                        new TextureView.SurfaceTextureListener() {
                            @Override
                            public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
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

                if (mTextureView.isAvailable())
                    configureTransform();


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
                mPreviewSurface = mPreviewImageReader.getSurface();
            }

            return;
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
    }
    public Surface getPreviewSurface() {
        return mPreviewSurface;
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * //@param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    //private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = width; // aspectRatio.getWidth();
        int h = height; //aspectRatio.getHeight();
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

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setupPreview and also the size of `mTextureView` is fixed.
     */
    private void configureTransform() {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }

        int viewWidth = mTextureView.getWidth();
        int viewHeight = mTextureView.getHeight();

        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
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

        // set up the preview surface
        if (mPreviewSurface == null) {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            mPreviewSurface = new Surface(texture);
        }
    }










    /*
     * Camera states:
     */
    private static final int STATE_IDLE = 0;
    private static final int STATE_WAITING_LOCK = 1;
    private static final int STATE_WAITING_PRECAPTURE = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private static final int STATE_PICTURE_TAKEN = 4;
    private int mState = STATE_IDLE;

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;
    private Surface mPreviewSurface = null;

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
    }

    private CameraCaptureSession mCameraCaptureSession;
    public void start(CameraCaptureSession cameraCaptureSession, Handler cameraHandler, FocusListener listener)
    {
        mCameraCaptureSession = cameraCaptureSession;
        // When the session is ready, we start displaying the preview.
        try {
            Log.i(TAG, "StartFocusing");
            CameraDevice camera = cameraCaptureSession.getDevice();

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(getPreviewSurface());

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

            FocusingStateMachine stateMachine = new FocusingStateMachine(cameraCaptureSession, cameraHandler, listener);

            // Now, we start streaming frames from the camera:
            mPreviewRequest = mPreviewRequestBuilder.build();
            cameraCaptureSession.setRepeatingRequest(
                    mPreviewRequest,
                    stateMachine,
                    cameraHandler
            );

            // This is how to tell the camera to attempt a focus lock:
            mPreviewRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START
            );

            // Tell the state machine to wait for the focus lock.
            mState = STATE_WAITING_LOCK;

            // Send a single request to the camera with the focus-lock instruction:
            cameraCaptureSession.capture(
                    mPreviewRequestBuilder.build(),
                    stateMachine,
                    cameraHandler
            );
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
        } catch (Exception e) {
            if (cameraCaptureSession != null) {
                try {
                    cameraCaptureSession.stopRepeating();
                } catch (CameraAccessException cae) {
                }
            }
            mPreviewRequestBuilder = null;
            mPreviewRequest = null;
            //mPreviewImageReader.setOnImageAvailableListener(null, null);
            mPreviewImageReader = null;
            mState = STATE_IDLE;
            e.printStackTrace();
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    public void stop(Handler cameraHandler) {
        try {
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
                    // Send request to camera:
                    mCameraCaptureSession.capture(
                            mPreviewRequestBuilder.build(),
                            null, // => no callbacks
                            cameraHandler
                    );

                    mCameraCaptureSession.stopRepeating();

                    //// TODO: is this used at all...?
                    //mCameraCaptureSession.setRepeatingRequest(
                    //        mPreviewRequest,
                    //        null, // => no callbacks
                    //        cameraHandler
                    //);

                    mCameraCaptureSession = null;
                }

                mPreviewRequestBuilder = null;
            }

            mState = STATE_IDLE;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class FocusingStateMachine extends CameraCaptureSession.CaptureCallback {
        private FocusListener mListener = null;
        private Handler mCameraHandler = null;
        private CameraCaptureSession mCaptureSession = null;

        FocusingStateMachine(CameraCaptureSession cameraCaptureSession, Handler cameraHandler, FocusListener listener) {
            mCaptureSession = cameraCaptureSession;
            mListener = listener;
            mCameraHandler = cameraHandler;
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
                            runPrecaptureSequence(mCaptureSession, mCameraHandler);
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
        private void runPrecaptureSequence(CameraCaptureSession captureSession, Handler cameraHandler) {
            try {
                // This is how to tell the camera to trigger.
                mPreviewRequestBuilder.set(
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                        CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
                );
                // Tell #mCaptureCallback to wait for the precapture sequence to be set.
                mState = STATE_WAITING_PRECAPTURE;
                captureSession.capture(
                        mPreviewRequestBuilder.build(),
                        null, // the repeating request will generate the necessary callbacks
                        cameraHandler
                );
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        private void onFocusLocked()
        {
            Log.i(TAG, "lock");
            try {
                mCaptureSession.stopRepeating();
                if (mListener != null)
                    mListener.focusLocked();
            } catch (CameraAccessException e)
            {
                throw new UnsupportedOperationException("Camera access required");
            }
        }
    }
}
