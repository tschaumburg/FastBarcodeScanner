package dk.schaumburgit.stillsequencecamera.camera;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.media.Image;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.io.IOException;

import dk.schaumburgit.stillsequencecamera.IStillSequenceCamera;

/**
 * Created by Thomas Schaumburg on 08-12-2015.
 */
public class StillSequenceCamera implements IStillSequenceCamera {
    private int mCameraId = -1;
    private Camera mCamera;
    private final Activity mActivity;
    private final SurfaceView mPreview;
    private IStillSequenceCamera.OnImageAvailableListener mImageListener = null;

    public StillSequenceCamera(Activity activity, SurfaceView preview)
    {
        mActivity = activity;
        mPreview = preview;
    }

    public void setup() {
        if (mCamera != null)
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
    }

    public void start(OnImageAvailableListener listener) {
        mImageListener = listener;

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
    }

    public void stop()
    {
        mImageListener = null;
        stopTakingPictures();
    }

    private boolean mContinueTakingPictures = false;
    private static final String TAG = "StillSequenceCamera";

    private void startTakingPictures()
    {
        if (mContinueTakingPictures)
            return;

        if (mCameraId < 0)
            throw new IllegalStateException("StillSequenceCamera.start() cannot be called before setup()");

        mContinueTakingPictures = true;
        takePicture();
    }

    private void stopTakingPictures() {
        mContinueTakingPictures = false;
    }

    private void takePicture() {
        mCamera.takePicture(
                null,
                null,
                new PictureCallback() {

                    @Override
                    public void onPictureTaken(byte[] jpegData, Camera camera) {
                        Camera.Size size = camera.getParameters().getPictureSize();
                        Log.i(TAG, "Captured JPEG " + jpegData.length + " bytes (" + size.width + "x" + size.height + ")");

                        if (mImageListener != null) {
                            mImageListener.onImageAvailable(ImageFormat.JPEG, jpegData, size.width, size.height);
                        }
                        if (mContinueTakingPictures) {
                                    mCamera.startPreview();
                                    takePicture();
                        }
                    }
                }
        );
    }

    public void close() {
        mContinueTakingPictures = false;
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
        mCameraId = -1;
        mImageListener = null;
    }

}
