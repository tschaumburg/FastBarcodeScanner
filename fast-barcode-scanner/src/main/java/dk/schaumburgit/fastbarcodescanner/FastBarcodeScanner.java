package dk.schaumburgit.fastbarcodescanner;
/*
 * Copyright 2015 Schaumburg IT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;

import java.security.InvalidParameterException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.EnumSet;
import java.util.Map;

import dk.schaumburgit.stillsequencecamera.IStillSequenceCamera;
import dk.schaumburgit.stillsequencecamera.camera.StillSequenceCamera;
import dk.schaumburgit.stillsequencecamera.camera2.StillSequenceCamera2;
import dk.schaumburgit.trackingbarcodescanner.TrackingBarcodeScanner;

/**
 * The FastBarcodeScanner captures images from your front-facing camera at the fastest
 * possible rate, scans them for barcodes and reports any changes to the caller
 * via a listener callback.
 *
 * The image capture is done unobtrusively without any visible UI, using a background thread.
 *
 * For newer Android versions (Lollipop and later), the new, faster Camera2 API is supported.
 * For older versions, FastBarcodeScanner falls back to using the older, slower camera API.
 *
 * When the Camera2 API is available, the FastBarcodeScanner can be created with a TextureView
 * if on-screen preview is desired, or without for headless operation.
 *
 * For older Android versions, the FastBarcodeScanner *must* be created with a SurfaceView,
 * and the SurfaceView *must* be visible on-screen. Setting the SurfaceView to 1x1 pixel
 * will however make it effectively invisible.
 *
 * Regardless of Android version, the FastbarcodeScanner *must* be supplied with a reference
 * to the current Activity (used for accessing e.g. the camera, and other system resources).
 *
 */
public class FastBarcodeScanner
{
    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "FastBarcodeScanner";

    private boolean mIncludeImagesInCallback = false;
    private Activity mActivity;
    private Handler mBarcodeListenerHandler;
    private HandlerThread mProcessingThread;
    private Handler mProcessingHandler;

    private Activity getActivity() {
        return mActivity;
    }

    private final IStillSequenceCamera mImageSource;
    private final TrackingBarcodeScanner mBarcodeFinder;
    private int mPictureFormat = -1;
    /**
     * Creates a headless FastBarcodeScanner (i.e. one without any UI)
     *
     * FastBarcodeScanner instances created using this constructor will use
     * the new, efficient Camera2 API for controlling the camera.
     *
     * This boosts performance by a factor 5x - but it only works on Android
     * Lollipop (API version 21) and later.
     *
     * As an alternative, consider using the #FastBarcodeScanner constructor
     * which will create a FastBarcodeScanner working on older versions of
     * Android too - albeit much less efficiently.
     *
     * The following properties control the behaviour of the barcode scanning
     * (see #TrackingBarcodeScanner for details): UseTracking, RelativeTrackingMargin,
     * NoHitsBeforeTrackingLoss.
     * @param activity Non null
     */
    @TargetApi(21)
    public FastBarcodeScanner(Activity activity)
    {
        this(activity, (TextureView)null);
    }

    /**
     * Creates a FastBarcodeScanner using the given TextureView for preview.
     *
     * FastBarcodeScanner instances created using this constructor will use
     * the new, much more efficient Camera2 API for controlling the camera.
     *
     * This boosts performance by a factor 5x - but it only works on Android
     * Lollipop (API version 21) and later.
     *
     * As an alternative, consider using the #FastBarcodeScanner constructor
     * which will create a FastBarcodeScanner working on older versions of
     * Android too - albeit much less efficiently.
     * @param activity Non null
     * @param textureView Nullable
     */
    @TargetApi(21)
    public FastBarcodeScanner(Activity activity, TextureView textureView) {
        if (activity == null)
            throw new InvalidParameterException("activity cannot be null");

        this.mActivity = activity;
        this.mBarcodeFinder = new TrackingBarcodeScanner();
        this.mImageSource = new StillSequenceCamera2(activity, textureView, 1024*768);

        Map<Integer, Double> cameraFormatCosts = mImageSource.getSupportedImageFormats();
        Map<Integer, Double> zxingFormatCosts = mBarcodeFinder.getPreferredImageFormats();

        mPictureFormat = calculateCheapestFormat(cameraFormatCosts, zxingFormatCosts);
        this.mImageSource.setup(mPictureFormat);
    }

    private static int calculateCheapestFormat(Map<Integer, Double> cost1, Map<Integer, Double> cost2)
    {
        double bestYet = 1;
        int result = ImageFormat.JPEG;
        for (int format: cost1.keySet())
        {
            if (cost2.containsKey(format))
            {
                double c1 = cost1.get(format);
                double c2 = cost2.get(format);
                double totalCost = c1 * c2;

                if (totalCost < bestYet)
                {
                    result = format;
                    bestYet = totalCost;
                }
            }
        }
        return result;
    }

    /**
     * Creates a FastBarcodeScanner using the deprecated Camera API supported
     * on Android versions prior to Lollipop (API level lower than 21).
     *
     * The created FastBarcodeScanner will display preview output in the supplied
     * SurfaceView. This parameter *must* be non-null, and the referenced SurfaceView
     * *must* be displayed on-screen, with a minimum size of 1x1 pixels. This is a
     * non-negotiable requirement from the camera API (upgrade to API level 21 for
     * true headless operation).
     * @param activity Non-null
     * @param surfaceView Non-null
     */
    public FastBarcodeScanner(Activity activity, SurfaceView surfaceView) {
        if (activity == null)
            throw new InvalidParameterException("activity cannot be null");

        if (surfaceView == null)
            throw new InvalidParameterException("surfaceView cannot be null");

        this.mActivity = activity;
        this.mBarcodeFinder = new TrackingBarcodeScanner();
        this.mImageSource = new StillSequenceCamera(activity, surfaceView);
        //this.mImageSource.setup(ImageFormat.JPEG);

        Map<Integer, Double> cameraFormatCosts = mImageSource.getSupportedImageFormats();
        Map<Integer, Double> zxingFormatCosts = mBarcodeFinder.getPreferredImageFormats();

        mPictureFormat = calculateCheapestFormat(cameraFormatCosts, zxingFormatCosts);
        this.mImageSource.setup(mPictureFormat);
    }

    /**
     * Starts scanning on a background thread, calling the supplied listener whenever
     * there's a *change* in the barcode seen (i.e. if 200 consecutive images contain
     * the same barcode, only the first will generate a callback).
     *
     * "No barcode" is signalled with a null value via the callback.
     *
     * Example: After StartScan is called, the first 20 images contain no barcode, the
     * next 200 have barcode A, the next 20 have nothing. This will generate the
     * following callbacks:
     *
     * Frame#1:   onBarcodeAvailable(null)
     * Frame#21:  onBarcodeAvailable("A")
     * Frame#221: onBarcodeAvailable(null)
     *
     * @param listener A reference to the listener receiving the above mentioned callbacks
     * @param callbackHandler Identifies the thread that the callbacks will be made on.
     *                        Null means "use the thread that called StartScan()".
     */
    public void StartScan(BarcodeDetectedListener listener, Handler callbackHandler)
    {
        mBarcodeListenerHandler = callbackHandler;
        if (mBarcodeListenerHandler == null)
            mBarcodeListenerHandler = new Handler();
        mBarcodeListener = listener;

        mProcessingThread = new HandlerThread("FastBarcodeScanner processing thread");
        mProcessingThread.start();
        mProcessingHandler = new Handler(mProcessingThread.getLooper());

        mImageSource.start(
                new IStillSequenceCamera.OnImageAvailableListener()
                {

                    @Override
                    public void onImageAvailable(int format, byte[] data, int width, int height) {
                        processImage(format, data, width, height);
                    }

                    @Override
                    public void onError(Exception error) {
                        FastBarcodeScanner.this.onError(error);
                    }

                },
                mProcessingHandler
        );
    }

    /**
     * Stops the scanning process started by StartScan() and frees any shared system resources
     * (e.g. the camera). StartScan() can always be called to restart.
     *
     * StopScan() and StartScan() are thus well suited for use from the onPause() and onResume()
     * handlers of a calling application.
     */
    public void StopScan()
    {
        mImageSource.stop();

        if (mProcessingThread != null) {
            try {
                mProcessingThread.quitSafely();
                mProcessingThread.join();
                mProcessingThread = null;
                mProcessingHandler = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        mBarcodeListener = null;
        mBarcodeListenerHandler = null;
    }

    /**
     * Disposes irrevocably of all resources. This instance cannot be used after close() is called.
     */
    public void close()
    {
        this.mImageSource.close();
    }

    private void processImage(int format, byte[] bytes, int width, int height) {
        // Get the image data (we requested JPEG) into a byte buffer:
        Date first = new Date();

        // Decode the image:
        Date second = new Date();
        try {
            String newBarcode = mBarcodeFinder.find(format, width, height, bytes);
            Log.v(TAG, "Found barcode: " + newBarcode);
            Date third = new Date();

            // Tell the world:
            Bitmap source = null;
            if (mIncludeImagesInCallback)
            {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888; // JavaDoc: "...It should be used whenever possible."
                opts.inDither = false;
                source = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
            }

            onBarcodeFound(newBarcode, source);

            // Performance tracing:
            Date fourth = new Date();
            if (false)
                Log.v(
                    TAG, "Timing;"
                            + new Timestamp(first.getTime())
                            + ";"
                            + new Timestamp(second.getTime())
                            + ";"
                            + new Timestamp(mBarcodeFinder.a.getTime())
                            + ";"
                            + new Timestamp(mBarcodeFinder.b.getTime())
                            + ";"
                            + new Timestamp(mBarcodeFinder.c.getTime())
                            + ";"
                            + new Timestamp(mBarcodeFinder.d.getTime())
                            + ";"
                            + new Timestamp(mBarcodeFinder.f.getTime())
                            + ";"
                            + new Timestamp(mBarcodeFinder.g.getTime())
                            + ";"
                            + new Timestamp(third.getTime())
                            + ";"
                            + new Timestamp(fourth.getTime())
                            + ";");
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
        }
    }

    //*********************************************************************
    //* Managing Barcode events:
    //* ========================
    //* Only bother the listener if a *new* barcode is detected.
    //*
    //* Furthermore,
    //*********************************************************************

    private BarcodeDetectedListener mBarcodeListener = null;
    private String mLastReportedBarcode = null;
    private int mNoBarcodeCount = 0;
    private final int NO_BARCODE_IGNORE_LIMIT = 5;
    private void onBarcodeFound(String barcode, Bitmap source)
    {
        //mBarcodeListener.onBarcodeAvailable(barcode);
        //Log.d(TAG, "Scanned " + barcode);
        //if ( 1 == 1)
        //    return;

        if (barcode == null)
        {
            mNoBarcodeCount++;
            if (mLastReportedBarcode != null && mNoBarcodeCount >= NO_BARCODE_IGNORE_LIMIT) {
                mLastReportedBarcode = null;
                _onBarcode(mLastReportedBarcode, source);
            }
        }
        else
        {
            mNoBarcodeCount = 0;
            if (!barcode.equals(mLastReportedBarcode))
            {
                mLastReportedBarcode = barcode;
                _onBarcode(mLastReportedBarcode, source);
            }
        }
    }

    private void _onBarcode(final String barcode, final Bitmap source)
    {
        if (mBarcodeListener != null) {
            mBarcodeListenerHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            mBarcodeListener.onBarcodeAvailable(barcode, source);
                        }
                    }
            );
        }

    }

    private void onError(Exception error)
    {
        if (mBarcodeListener != null)
            mBarcodeListener.onError(error);
    }


    //*********************************************************************
    //*********************************************************************

    /**
     * Callback interface for being notified that a barcode has been detected.
     *
     * <p>
     * The onBarcodeAvailable is called when a new barcode is detected - that is,
     * if the same barcode is detected in 20 consecutive frames, onBarcodeAvailable
     * is only called on the first.
     * </p>
     * <p>
     * When no barcodes have been detected in 3 consecutive frames, onBarcodeAvailable
     * is called with a null barcode parameter ().
     * </p>
     */
    public interface BarcodeDetectedListener {
        /**
         * Callback that is called when a new image is available from ImageReader.
         *
         * @param barcode the barcode detected.
         */
        void onBarcodeAvailable(String barcode, Bitmap source);

        void onError(Exception error);
    }

    //*********************************************************************
    // Pass-through properties for the barcode scanner
    //*********************************************************************
    public double getRelativeTrackingMargin() {
        return mBarcodeFinder.getRelativeTrackingMargin();
    }

    public void setRelativeTrackingMargin(double relativeTrackingMargin) {
        mBarcodeFinder.setRelativeTrackingMargin(relativeTrackingMargin);
    }

    public int getNoHitsBeforeTrackingLoss() {
        return mBarcodeFinder.getNoHitsBeforeTrackingLoss();
    }

    public void setNoHitsBeforeTrackingLoss(int noHitsBeforeTrackingLoss) {
        mBarcodeFinder.setNoHitsBeforeTrackingLoss(noHitsBeforeTrackingLoss);
    }

    public EnumSet<BarcodeFormat> getPossibleBarcodeFormats() {
        return mBarcodeFinder.getPossibleBarcodeFormats();
    }

    public void setPossibleBarcodeFormats(EnumSet<BarcodeFormat> possibleFormats) {
        mBarcodeFinder.setPossibleBarcodeFormats(possibleFormats);
    }

    public boolean isUseTracking() {
        return mBarcodeFinder.isUseTracking();
    }

    public void setUseTracking(boolean useTracking) {
        mBarcodeFinder.setUseTracking(useTracking);
    }

    public boolean isLockFocus() {
        return mImageSource.isLockFocus();
    }

    public void setLockFocus(boolean lockFocus) {
        mImageSource.setLockFocus(lockFocus);
    }

    public boolean isIncludeImagesInCallback() {
        return mIncludeImagesInCallback;
    }

    public void setIncludeImagesInCallback(boolean mIncludeImagesInCallback) {
        this.mIncludeImagesInCallback = mIncludeImagesInCallback;
    }

    public int getPictureFormat() {
        return mPictureFormat;
    }}

