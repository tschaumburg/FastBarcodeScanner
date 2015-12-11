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

import dk.schaumburgit.stillsequencecamera.IStillSequenceCamera;

/**
 * Created by Thomas Schaumburg on 08-12-2015.
 */
public class StillSequenceCamera implements IStillSequenceCamera {
    private static final String TAG = "StillSequenceCamera";
    private int mCameraId = -1;
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
    public void setup()
            throws UnsupportedOperationException, IllegalStateException
    {
        if (mCamera != null || mState != CLOSED)
            throw new IllegalStateException("StillSequenceCamera.setup() can only be called on a new instance");

        // Open a camera:
        mCameraId = -1;
        //get the number of cameras
        int numberOfCameras = Camera.getNumberOfCameras();
        //for every camera check
        for (int i = 0; i < numberOfCameras; i++) {
            CameraInfo info = new CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
                mCameraId = i;
                break;
            }
        }

        if (mCameraId < 0)
            throw new UnsupportedOperationException("Cannot find a back-facing camera");

        mCamera = Camera.open(mCameraId);

        Camera.Parameters pars = mCamera.getParameters();
        pars.setPictureSize(1024, 768);
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
        mCameraId = -1;
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
