package dk.schaumburgit.stillsequencecamera.camera;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import dk.schaumburgit.stillsequencecamera.IStillSequenceCamera;

/**
 * Created by Thomas Schaumburg on 08-12-2015.
 */
public class StillSequenceCamera implements IStillSequenceCamera {
    private static final String TAG = "StillSequenceCamera";
    private final int mCameraId;
    private Camera mCamera;
    private final Activity mActivity;
    private final SurfaceView mPreview;
    private IStillSequenceCamera.OnImageAvailableListener mImageListener = null;
    private Handler mCallbackHandler;
    private final static int CLOSED = 0;
    private final static int INITIALIZED = 1;
    private final static int CAPTURING = 2;
    private boolean mLockFocus = true;
    private int mState = CLOSED;

    public StillSequenceCamera(Activity activity, SurfaceView preview)
    {
        mActivity = activity;
        mPreview = preview;
        mState = CLOSED;

        // Open a camera:
        int chosenCameraId = -1;
        //get the number of cameras
        int numberOfCameras = Camera.getNumberOfCameras();
        //for every camera check
        for (int i = 0; i < numberOfCameras; i++) {
            CameraInfo info = new CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
                chosenCameraId = i;
                break;
            }
        }

        if (chosenCameraId < 0)
            throw new UnsupportedOperationException("Cannot find a back-facing camera");

        mCameraId = chosenCameraId;

        // Open a camera:
        mCamera = Camera.open(mCameraId);
    }

    @Override
    public Map<Integer, Double> getSupportedImageFormats()
    {
        Map<Integer, Double> res = new HashMap<Integer, Double>();

        for (int format : mCamera.getParameters().getSupportedPictureFormats()) {
            res.put(format, getFormatCost(format));
            Log.i(TAG, "CAMERA FORMAT: " + format);
        }
        return res;
    }

    private static double getFormatCost(int format)
    {
        switch (format)
        {
            case ImageFormat.UNKNOWN:
                return 1.0;
            case ImageFormat.NV21:
                return 0.8;
            case ImageFormat.NV16:
                // This format has never been seen in the wild, but is compatible as we only care
                // about the Y channel, so allow it.
                return 0.8;
            case ImageFormat.YV12:
            case ImageFormat.YUY2:
            case ImageFormat.YUV_420_888:
                return 0.5; // pure guesswork - but it IS faster than JPEG
            case ImageFormat.YUV_422_888:
                // only varies from yuv_420_888 in chroma-subsampling, which I'm guessing
                // doesn't affect the luminance much
                // (see https://en.wikipedia.org/wiki/Chroma_subsampling)
                return 0.5;
            case ImageFormat.YUV_444_888:
                // only varies from yuv_420_888 in chroma-subsampling, which I'm guessing
                // doesn't affect the luminance much
                // (see https://en.wikipedia.org/wiki/Chroma_subsampling)
                return 0.5;
            case ImageFormat.FLEX_RGB_888:
            case ImageFormat.FLEX_RGBA_8888:
            case ImageFormat.RGB_565:
                return 0.8; // pure guesswork
            case ImageFormat.JPEG:
                return 1.0; // duh...?
            case ImageFormat.RAW_SENSOR:
            case ImageFormat.RAW10:
            case ImageFormat.RAW12:
                return 0.4; // pure guesswork - but any RAW format must be optimal (wrt capture speed)?
            case ImageFormat.DEPTH16:
            case ImageFormat.DEPTH_POINT_CLOUD:
                return 1.5; // sound terribly complicated - but I'm just guessing....
            //ImageFormat.Y8:
            //ImageFormat.Y16:
        }

        return 1.0;
    }

    /**
     * Selects a back-facing camera, opens it and starts focusing.
     *
     * The #start() method can be called immediately when this method returns
     *
     * If setup() returns successfully, the StillSequenceCamera enters the INITIALIZED state.
     *
     * @throws IllegalStateException if the StillSequenceCamera is in any but the CLOSED state
     * @throws UnsupportedOperationException if no back-facing camera is available
     * @throws RuntimeException if opening the camera fails (for example, if the
     *     camera is in use by another process or device policy manager has
     *     disabled the camera).
     */
    public void setup(int format)
            throws UnsupportedOperationException, IllegalStateException
    {
        if (mState != CLOSED)
            throw new IllegalStateException("StillSequenceCamera.setup() can only be called on a new instance");

        Camera.Parameters pars = mCamera.getParameters();
        pars.setPictureSize(1024, 768);
        pars.setPictureFormat(format);
        pars.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        mCamera.setParameters(pars);

        mState = INITIALIZED;
    }

    /**
     * Starts the preview (displaying it in the #SurfaceView provided in the constructor),
     * and starts taking pictures as rapidly as possible.
     *
     * This continues until #stop() is called.
     *
     * If start() returns successfully, the StillSequenceCamera enters the CAPTURING state.
     *
     * @param listener Every time a picture is taken, this callback interface is called.
     *
     * @throws IllegalStateException if the StillSequenceCamera is in any but the INITIALIZED state
     */
    @Override
    public void start(OnImageAvailableListener listener, Handler callbackHandler)
            throws IllegalStateException
    {
        if (mState != INITIALIZED)
            throw new IllegalStateException("StillSequenceCamera.start() can only be called in the INITIALIZED state");

        mImageListener = listener;
        mCallbackHandler = callbackHandler;
        if (mCallbackHandler == null)
            mCallbackHandler = new Handler();

        if (mPreview.getHolder().getSurface() != null) {
            try {
                mCamera.setPreviewDisplay(mPreview.getHolder());
                mCamera.startPreview();
                startTakingPictures();
            } catch (IOException e) {
                Log.d(TAG, "Error setting camera preview: " + e.getMessage());
            }
        }

        mPreview.getHolder().addCallback(
                new SurfaceHolder.Callback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        try {
                            // create the surface and start camera preview
                            if (mCamera != null) {
                                mCamera.setPreviewDisplay(holder);
                                mCamera.startPreview();
                                startTakingPictures();
                            }
                        } catch (IOException e) {
                            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
                        }
                    }

                    @Override
                    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
                        // If your preview can change or rotate, take care of those events here.
                        // Make sure to stop the preview before resizing or reformatting it.
                        try {
                            mCamera.stopPreview();
                        } catch (Exception e) {
                            // ignore: tried to stop a non-existent preview
                        }
                        try {
                            mCamera.setPreviewDisplay(mPreview.getHolder());
                            mCamera.startPreview();
                        } catch (Exception e) {
                            Log.d(TAG, "Error re-starting camera preview: " + e.getMessage());
                        }
                    }

                    @Override
                    public void surfaceDestroyed(SurfaceHolder holder) {
                        try {
                            stopTakingPictures();
                            mCamera.stopPreview();
                        } catch (Exception e) {
                            // ignore: tried to stop a non-existent preview
                        }
                    }
                }
        );
        // deprecated setting, but required on Android versions prior to 3.0
        mPreview.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mState = CAPTURING;
    }

    /**
     * Stops the preview, and stops the capture of still images.
     *
     * If stop() returns successfully, the StillSequenceCamera enters the STOPPED state.
     *
     * @throws IllegalStateException if stop is called in any but the STARTED state
     */
    public void stop()
        throws IllegalStateException
    {
        if (mState == CLOSED)
            return;

        if (mState != CAPTURING)
            throw new IllegalStateException("StillSequenceCamera.stop() can only be called in the STARTED state");

        mImageListener = null;
        mCallbackHandler = null;
        stopTakingPictures();

        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
        }

        mState = INITIALIZED;
    }

    public void close() {
        if (mState == CLOSED)
            return;

        if (mState == CAPTURING)
            stop();

        if (mState != INITIALIZED)
            throw new IllegalStateException("StillSequenceCamera.stop() can only be called after start()");

        mContinueTakingPictures = false;
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
        mImageListener = null;

        mState = CLOSED;
    }

    private boolean mContinueTakingPictures = false;

    private void startTakingPictures()
            throws IllegalStateException
    {
        if (mContinueTakingPictures)
            return;

        if (mCameraId < 0)
            throw new IllegalStateException("StillSequenceCamera.start() cannot be called before setup()");

        mContinueTakingPictures = true;
        takePicture();
    }

    private void stopTakingPictures()
    {
        mContinueTakingPictures = false;
    }

    private void takePicture()
    {
        mCamera.takePicture(
                null,
                null,
                new PictureCallback() {

                    @Override
                    public void onPictureTaken(final byte[] jpegData, Camera camera) {
                        final Camera.Size size = camera.getParameters().getPictureSize();
                        Log.i(TAG, "Captured JPEG " + jpegData.length + " bytes (" + size.width + "x" + size.height + ")");

                        if (mImageListener != null) {
                            mCallbackHandler.post(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            mImageListener.onImageAvailable(ImageFormat.JPEG, jpegData, size.width, size.height);
                                        }
                                    }
                            );
                        }
                        if (mContinueTakingPictures) {
                                    mCamera.startPreview();
                                    takePicture();
                        }
                    }
                }
        );
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
