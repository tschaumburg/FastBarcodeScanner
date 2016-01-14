package dk.schaumburgit.stillsequencecamera.camera2;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Thomas on 13-01-2016.
 */
public class PreviewManager
{
    private static final String TAG = "PreviewManager";
    private final Activity mActivity;
    private final TextureView mTextureView;
    private Size mPreviewSize;
    private Surface mPreviewSurface = null;

    public PreviewManager(Activity activity, TextureView textureView)
    {
        assert textureView != null;
        this.mActivity = activity;
        this.mTextureView = textureView;
    }

    // Users must call
    //    mCameraDevice.createCaptureSession(
    //       Arrays.asList(previewManager.getSurface(), ...),
    //       new CameraCaptureSession.StateCallback() {
    //          @Override
    //          public void onConfigured(CameraCaptureSession cameraCaptureSession) {
    //             previewRequestBuilder = cameraCaptureSession.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
    //             previewRequestBuilder.addTarget(previewManager.getSurface());
    //             ...configure previewRequestBuilder...
    //             cameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), listener, handler)
    public Surface getSurface() {
        return mPreviewSurface;
    }

    /**
     * Sets up member variables related to the on-screen preview (if any).
     * @param cameraId Id of the camera
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
                //mPreviewImageReader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 2);
                //mPreviewImageReader.setOnImageAvailableListener(
                //        new ImageReader.OnImageAvailableListener() {
                //            @Override
                //            public void onImageAvailable(ImageReader reader) {
                //                Image image = reader.acquireLatestImage();
                //                if (image != null)
                //                    image.close();
                //            }
                //        }, null);
                //mPreviewSurface = mPreviewImageReader.getSurface();
            }
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.e(TAG, "Camera2 API is not supported");
            throw new UnsupportedOperationException("Camera2 API is not supported");
        } catch (Exception e) {
            //if (mPreviewImageReader != null) {
            //    mPreviewImageReader.setOnImageAvailableListener(null, null);
            //    mPreviewImageReader = null;
            //}
            //if (mPreviewSurface != null)
            //{
            //    mPreviewSurface.release();
            //    mPreviewSurface = null;
            //}
            e.printStackTrace();
        }
    }

    public void close() {
        if (mTextureView != null)
            mTextureView.setSurfaceTextureListener(null);

        //if (mPreviewImageReader != null)
        //    mPreviewImageReader.setOnImageAvailableListener(null, null);
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



    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = width; // aspectRatio.getWidth();
        int h = height; //aspectRatio.getHeight();
        for (Size option : choices) {
            if (/*option.getHeight() == option.getWidth() * h / w &&*/
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size for " + width + "x" + height);
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
}
