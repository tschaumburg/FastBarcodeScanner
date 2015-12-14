package dk.schaumburgit.stillsequencecamera.camera2;

import android.app.Activity;
import android.content.Context;
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
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dk.schaumburgit.stillsequencecamera.IStillSequenceCamera;

/**
 * Created by Thomas on 08-12-2015.
 */
public class CaptureManager {
    private static final String TAG = "StillSequenceCamera2";

    private final Activity mActivity;
    private final int mMinPixels;

    // Set by setup(), freed by close():
    private ImageReader mImageReader;

    // Set by start(), cleared by stop():
    private IStillSequenceCamera.OnImageAvailableListener mImageListener = null;
    private CameraCaptureSession mCameraCaptureSession;
    private CaptureRequest mStillCaptureRequest = null;

    public CaptureManager(Activity activity, int minPixels)
    {
        if (activity==null)
            throw new NullPointerException("CaptureManager requires an Activity");

        this.mActivity = activity;

        if (minPixels < 1024*768)
            minPixels = 1024*768;
        this.mMinPixels = minPixels;
    }

    public Map<Integer,Double> getSupportedImageFormats(String cameraId) {
        try {
            CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
            StreamConfigurationMap map = manager
                    .getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map == null) {
                throw new UnsupportedOperationException("Insufficient camera info available");
            }

            Map<Integer, Double> res = new HashMap<Integer, Double>();
            for (int format : map.getOutputFormats()) {
                res.put(format, getFormatCost(format));
                Log.i(TAG, "CAMERA FORMAT: " + format);
            }

            return res;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.e(TAG, "Camera2 API is not supported");
            throw new UnsupportedOperationException("Camera2 API is not supported");
        }

        return null;
    }

    private static double getFormatCost(int format)
    {
        // TODO: 14-12-2015
        // Consider introducing *device profiles* for image format capture costs
        // - relative costs of various formats will probably depend on eg. hardware encoder support.
        switch (format)
        {
            case ImageFormat.UNKNOWN:
                return 1.0;
            case ImageFormat.NV21:
                // Doc: ...The YUV_420_888 format is recommended for YUV output instead
                return 0.7;
            case ImageFormat.NV16:
                // This format has never been seen in the wild, but is compatible as we only care
                // about the Y channel, so allow it.
                // Doc: ...The YUV_420_888 format is recommended for YUV output instead
                return 0.8;
            case ImageFormat.YV12:
                // Doc: ...The YUV_420_888 format is recommended for YUV output instead
                return 0.8;
            case ImageFormat.YUY2:
                // Doc: ...The YUV_420_888 format is recommended for YUV output instead
                return 0.8;
            case ImageFormat.YUV_420_888:
                return 0.71; // measured on a Nexus 6P (0.64) and 5X (0.78)
            case ImageFormat.YUV_422_888:
                // only varies from yuv_420_888 in chroma-subsampling, which I'm guessing
                // doesn't affect the luminance much
                // (see https://en.wikipedia.org/wiki/Chroma_subsampling)
                return 0.71;
            case ImageFormat.YUV_444_888:
                // only varies from yuv_420_888 in chroma-subsampling, which I'm guessing
                // doesn't affect the luminance much
                // (see https://en.wikipedia.org/wiki/Chroma_subsampling)
                return 0.71;
            case ImageFormat.FLEX_RGB_888:
            case ImageFormat.FLEX_RGBA_8888:
            case ImageFormat.RGB_565:
                return 0.8; // pure guesswork
            case ImageFormat.JPEG:
                return 1.0; // duh...?
            case ImageFormat.RAW_SENSOR:
                return 2.02; // measured on a Nexus 6P (2.06) and 5X ()1.98) - surprisingly *slower* than JPEG!
            case ImageFormat.RAW10:
                return 0.66; // measured on a Nexus 6P (0.64) and 5X (0.67)
            case ImageFormat.RAW12:
                return 0.66; // guesswork - setting it to the same as RAW10
            case ImageFormat.DEPTH16:
            case ImageFormat.DEPTH_POINT_CLOUD:
                return 2.5; // sound terribly complicated - but I'm just guessing....
            //ImageFormat.Y8:
            //ImageFormat.Y16:
        }

        return 1.0;
    }

    public void setup(String cameraId, int imageFormat) {
        try {
            CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
            StreamConfigurationMap map = manager
                    .getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map == null) {
                throw new UnsupportedOperationException("Insufficient camera info available");
            }

            // Check the output format:
            // ========================
            if (map.isOutputSupportedFor(imageFormat) == false)
                throw new UnsupportedOperationException("Camera cannot capture images in format " + imageFormat);
            int outputFormat = imageFormat;

            // Choose an image size:
            // =====================
            List<Size> choices = Arrays.asList(map.getOutputSizes(outputFormat));

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

            // Set up the still image reader:
            // ==============================
            mImageReader = ImageReader.newInstance(captureSize.getWidth(), captureSize.getHeight(),
                    outputFormat, /*maxImages*/4);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.e(TAG, "Camera2 API is not supported");
            throw new UnsupportedOperationException("Camera2 API is not supported");
        }
    }

    public Surface getSurface()
    {
        if (mImageReader == null)
            throw new IllegalStateException("CaptureManager.getSurface() cannot be called before setup() has completed");

        return mImageReader.getSurface();
    }

    private HandlerThread mInternalCaptureThread;
    private Handler mInternalCaptureHandler;
    public void start(final CameraCaptureSession cameraCaptureSession, final Handler callbackHandler, final IStillSequenceCamera.OnImageAvailableListener listener)
    {
        if (mImageReader == null)
            throw new IllegalStateException("CaptureManager: start() may only be called after setup() and before close()");

        if (null == cameraCaptureSession) {
            return;
        }
        mCameraCaptureSession = cameraCaptureSession;
        mImageListener = listener;

        CameraDevice cameraDevice = cameraCaptureSession.getDevice();
        if (null == cameraDevice) {
            return;
        }

        // Use a dedicated thread for handling all the incoming images
        mInternalCaptureThread = new HandlerThread("Camera Image Capture Background");
        mInternalCaptureThread.start();
        mInternalCaptureHandler = new Handler(mInternalCaptureThread.getLooper());

        mImageReader.setOnImageAvailableListener(
                new ImageReader.OnImageAvailableListener() {

                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        Image image = reader.acquireLatestImage();
                        if (image != null) {
                            sendImageAvailable(image);
                        }
                    }

                    private Image mLatestImage = null;
                    private synchronized Image getLatestImage()
                    {
                        Image image = mLatestImage;
                        mLatestImage = null;
                        return image;
                    }
                    private synchronized void setLatestImage(Image image)
                    {
                        if (mLatestImage != null)
                            mLatestImage.close();
                        mLatestImage = image;
                    }
                    private void sendImageAvailable(Image image)
                    {
                        // begin protected region
                        setLatestImage(image);
                        // end protected region

                        if (listener == null)
                            return;

                        callbackHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                // begin protected region
                                Image image = getLatestImage();
                                // end protected region

                                if (image != null)
                                {
                                    try {
                                        IStillSequenceCamera.OnImageAvailableListener listener = mImageListener;
                                        if (listener != null) {
                                            byte[] bytes = null;
                                            int format = image.getFormat();
                                            int width = image.getWidth();
                                            int height = image.getHeight();
                                            Image.Plane plane = image.getPlanes()[0];
                                            ByteBuffer buffer = plane.getBuffer();
                                            bytes = new byte[buffer.remaining()];
                                            buffer.get(bytes);
                                            // Important: free the image as soon as possible,
                                            // thus making room for a new capture to begin:
                                            image.close();
                                            image = null;
                                            listener.onImageAvailable(format, bytes, width, height);
                                        }
                                    } catch (Exception e) {
                                        if (image != null)
                                            image.close();
                                        Log.e(TAG, "Error extracting image", e);
                                    }
                                }
                            }
                        });
                    }
                },
                mInternalCaptureHandler
        );

        try {
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON);

            // Orientation
            int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            mStillCaptureRequest = captureBuilder.build();

            //captureNext();
            mCameraCaptureSession.setRepeatingRequest(
                    mStillCaptureRequest,
                    new CameraCaptureSession.CaptureCallback (){
                        @Override
                        public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId, long frameNumber) {
                            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
                            {
                                if (mInternalCaptureThread != null) {
                                    Log.i(TAG, "Killed capture thread");
                                    try {
                                        mInternalCaptureHandler.removeCallbacksAndMessages(null);
                                        mInternalCaptureThread.quitSafely();
                                        mInternalCaptureThread.join();
                                        mInternalCaptureThread = null;
                                        mInternalCaptureHandler = null;
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    },
                    mInternalCaptureHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void stop()
    {
        mImageListener = null;
        if (null != mImageReader) {
            mImageReader.setOnImageAvailableListener(null, null);
        }
        mStillCaptureRequest = null;
        mCameraCaptureSession = null;
    }

    public void close()
    {
        if (mImageReader != null) {
            mImageReader.setOnImageAvailableListener(null, null); // just making sure...
            mImageReader.close();
            mImageReader = null;
        }
    }

    //*********************************************************************
    //* Still image capture:
    //* ====================
    //*
    //*********************************************************************
    private void captureNext() {
        if (mCameraCaptureSession == null) {
            return;
        }
        try {
            //mCameraOpenCloseLock.acquire();
            mCameraCaptureSession.capture(mStillCaptureRequest, null, null);
        } catch (IllegalStateException e) {
            // this happens if captureNext is called at the same time as closeCamera()
            // calls mCaptureSession.close()
        //} catch (InterruptedException e) {
        //    // hmm...probably part of app shutdown, so we'll just go quietly
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } finally {
            //mCameraOpenCloseLock.release();
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
