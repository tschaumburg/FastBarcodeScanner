package dk.schaumburgit.stillsequencecamera.camera2;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import com.google.zxing.BinaryBitmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dk.schaumburgit.stillsequencecamera.CaptureFormatInfo;
import dk.schaumburgit.stillsequencecamera.IStillSequenceCamera;
import dk.schaumburgit.stillsequencecamera.imageformats.LuminanceSourceFactory;

import static dk.schaumburgit.stillsequencecamera.imageformats.ImageConverter.DecodeImage;

/**
 * Created by Thomas on 08-12-2015.
 */

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CaptureManager {
    private static final String TAG = "StillSequenceCamera2";

    private final Activity mActivity;
    private final PreviewManager mPreview;
    private final int mMinPixels;

    // Set by setup(), freed by close():
    private ImageReader mImageReader;

    // Set by start(), cleared by stop():
    private IStillSequenceCamera.OnImageAvailableListener mImageListener = null;
    private CameraCaptureSession mCameraCaptureSession;
    private CaptureRequest mStillCaptureRequest = null;


    public CaptureManager(Activity activity, PreviewManager preview, int minPixels)
    {
        if (activity==null)
            throw new NullPointerException("CaptureManager requires an Activity");

        this.mActivity = activity;
        this.mPreview = preview;

        if (minPixels < 1024*768)
            minPixels = 1024*768;
        this.mMinPixels = minPixels;
    }

    public long minExposureInNanos(String cameraId)
    {
        try {
            // Calculate default max fps from auto-exposure ranges in case getOutputMinFrameDuration() is
            // not supported.
            CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);

            final Range<Integer>[] fpsRanges =
                    manager
                            .getCameraCharacteristics(cameraId)
                            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);

            int maxFps = 0;
            for (Range<Integer> fpsRange : fpsRanges) {
                int lower = fixAndroidFpsBug(fpsRange.getLower());
                int upper = fixAndroidFpsBug(fpsRange.getUpper());
                maxFps = Math.max(maxFps, upper);
            }

            if (maxFps != 0)
                return 1000000000 / maxFps;

            return 0;
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return 0;
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.e(TAG, "Camera2 API is not supported");
            throw new UnsupportedOperationException("Camera2 API is not supported");
        }
    }

    // see https://stackoverflow.com/questions/38370583/getcameracharacteristics-control-ae-available-target-fps-ranges-in-camerachara?rq=1
    private int fixAndroidFpsBug(int fps) {
        if (fps > 999)
            return (int) (fps / 1000);

        return fps;
    }

    public double sourceAspectRatio(String cameraId)
    {
        try {
            CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);

            Rect sensorPixels = manager
                    .getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Log.v(TAG, "Sensor is " + sensorPixels.width() + " x " + sensorPixels.height());

            return sensorPixels.width() / sensorPixels.height();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            Log.e(TAG, "Camera2 API is not supported");
            throw new UnsupportedOperationException("Camera2 API is not supported");
        }

        return 4/3;
    }

    /**
     *
     * @param cameraId
     * @return map (format) => (size, cost in nanos)
     */
    public List<CaptureFormatInfo> getSupportedImageFormats(String cameraId, double relativeDevicePerformance) {
        try {
            CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);

            StreamConfigurationMap map = manager
                    .getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map == null) {
                throw new UnsupportedOperationException("Insufficient camera info available");
            }

            List<CaptureFormatInfo> res = new ArrayList<CaptureFormatInfo>();
            for (int format : map.getOutputFormats())
            {
                for (Size size : map.getOutputSizes(format))
                {
                    boolean unsupported = false;
                    String comment = "";

                    long nanosPerFrameCapture = map.getOutputMinFrameDuration(format, size);

                    if (nanosPerFrameCapture <=0)
                        nanosPerFrameCapture = minExposureInNanos(cameraId);

                    if (nanosPerFrameCapture <=0)
                        nanosPerFrameCapture = 33000000; // => 30 FPS

                    double nanosPerFrameConversion = LuminanceSourceFactory.nanosPerFrameConversion(format, size.getWidth(), size.getHeight(), relativeDevicePerformance);
                    if (nanosPerFrameConversion < 0)
                    {
                        unsupported = true;
                        comment += "Format cannot be converted to a scannable bitmap, ";
                    }

                    res.add(
                            new CaptureFormatInfo(
                                    format,
                                    size.getWidth(),
                                    size.getHeight(),
                                    unsupported,
                                    nanosPerFrameCapture,
                                    nanosPerFrameConversion,
                                    comment)
                    );
                }
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


    public void setup(String cameraId, int outputFormat, int imageWidth, int imageHeight) {
        try {
            /*CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
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
            */

            // Set up the still image reader:
            // ==============================
            mImageReader = ImageReader.newInstance(
                    imageWidth,
                    imageHeight,
                    outputFormat,
                    /*maxImages*/4
            );
        //} catch (CameraAccessException e) {
        //    e.printStackTrace();
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
    public void start(final CameraCaptureSession cameraCaptureSession, final Handler callbackHandler, final IStillSequenceCamera.OnImageAvailableListener listener) {
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

                    private synchronized Image getLatestImage() {
                        Image image = mLatestImage;
                        mLatestImage = null;
                        return image;
                    }

                    private synchronized void setLatestImage(Image image) {
                        if (mLatestImage != null)
                            mLatestImage.close();
                        mLatestImage = image;
                    }

                    private void sendImageAvailable(Image image) {
                        // begin protected region
                        setLatestImage(image);
                        // end protected region

                        if (listener == null) {
                            image.close();
                            return;
                        }

                        if (image == null) {
                            return;
                        }

                        callbackHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                // begin protected region
                                Image image = getLatestImage();
                                // end protected region

                                if (image == null) {
                                    return;
                                }

                                IStillSequenceCamera.OnImageAvailableListener listener = mImageListener;
                                if (listener == null) {
                                    image.close();
                                    return;
                                }

                                final BinaryBitmap bitmap = DecodeImage(image);

                                try {
                                    listener.onImageAvailable(new SourceImage(image), bitmap);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error extracting image", e);
                                } finally {
                                    image.close();
                                }
                            }
                        });
                    }
                },
                mInternalCaptureHandler
        );

        configureRequest(cameraDevice);
    }

    private void configureRequest(CameraDevice cameraDevice)
    {
        try {
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());
            if (mPreview != null)
                captureBuilder.addTarget(mPreview.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON);

            // Orientation
            //int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
            //captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            mStillCaptureRequest = captureBuilder.build();

            //captureNext();
            mCameraCaptureSession.setRepeatingRequest(
                    mStillCaptureRequest,
                    new CameraCaptureSession.CaptureCallback (){
                        @Override
                        public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId, long frameNumber) {
                            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
                            {
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
