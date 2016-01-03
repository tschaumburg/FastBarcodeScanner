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
import android.graphics.Point;
import android.media.Image;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;

import java.nio.ByteBuffer;
import java.security.InvalidParameterException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.EnumSet;
import java.util.Map;

import dk.schaumburgit.stillsequencecamera.IStillSequenceCamera;
import dk.schaumburgit.stillsequencecamera.camera.StillSequenceCamera;
import dk.schaumburgit.stillsequencecamera.camera2.StillSequenceCamera2;
import dk.schaumburgit.trackingbarcodescanner.Barcode;
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
public class FastBarcodeScanner {
    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "FastBarcodeScanner";

    private final Activity mActivity;
    private HandlerThread mProcessingThread;
    private Handler mProcessingHandler;

    private final IStillSequenceCamera mImageSource;
    private final TrackingBarcodeScanner mBarcodeFinder;

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
     *
     * @param activity Non null
     * @param resolution The requested minimum resolution of the photos
     *                   taken during scanning.
     */
    @TargetApi(21)
    public FastBarcodeScanner(Activity activity, int resolution) {
        this(activity, (TextureView) null, resolution);
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
     *
     * @param activity    Non null
     * @param textureView Nullable
     * @param resolution The requested minimum resolution of the photos
     *                   taken during scanning.
     */
    @TargetApi(21)
    public FastBarcodeScanner(Activity activity, TextureView textureView, int resolution) {
        if (activity == null)
            throw new InvalidParameterException("activity cannot be null");

        this.mActivity = activity;
        this.mBarcodeFinder = new TrackingBarcodeScanner();
        this.mImageSource = new StillSequenceCamera2(activity, textureView, resolution);

        Map<Integer, Double> cameraFormatCosts = mImageSource.getSupportedImageFormats();
        Map<Integer, Double> zxingFormatCosts = mBarcodeFinder.getPreferredImageFormats();

        int pictureFormat = calculateCheapestFormat(cameraFormatCosts, zxingFormatCosts);
        this.mImageSource.setup(pictureFormat);
    }

    private static int calculateCheapestFormat(Map<Integer, Double> cost1, Map<Integer, Double> cost2) {
        double bestYet = 1;
        int result = ImageFormat.JPEG;
        for (int format : cost1.keySet()) {
            if (cost2.containsKey(format)) {
                double c1 = cost1.get(format);
                double c2 = cost2.get(format);
                double totalCost = c1 * c2;

                if (totalCost < bestYet) {
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
     *
     * @param activity    Non-null
     * @param surfaceView Non-null
     * @param resolution The requested minimum resolution of the photos
     *                   taken during scanning.
     *
     * @deprecated This constructor uses the deprecated Camera API. We recommend using
     * one of the other FastBarcodeScanner constructors (using the new
     * {@link android.hardware.camera2} API) for new applications.
     */
    @Deprecated
    public FastBarcodeScanner(Activity activity, SurfaceView surfaceView, int resolution) {
        if (activity == null)
            throw new InvalidParameterException("activity cannot be null");

        if (surfaceView == null)
            throw new InvalidParameterException("surfaceView cannot be null");

        this.mActivity = activity;
        this.mBarcodeFinder = new TrackingBarcodeScanner();
        this.mImageSource = new StillSequenceCamera(activity, surfaceView, resolution);

        Map<Integer, Double> cameraFormatCosts = mImageSource.getSupportedImageFormats();
        Map<Integer, Double> zxingFormatCosts = mBarcodeFinder.getPreferredImageFormats();

        int pictureFormat = calculateCheapestFormat(cameraFormatCosts, zxingFormatCosts);
        this.mImageSource.setup(pictureFormat);
    }

    /**
     * Starts recording anything the back-camera sees, looking for any single barcode
     * in the captured photos. If several barcodes are in view, only the first one found
     * will be detected (consider using the less efficient #startMultiScan if you want
     * all barcodes)
     *
     * The scanning is performed on a background thread. The supplied listener will
     * be called using the supplied handler whenever
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
     * @param includeImagesInCallback Whether the callbacks to the listener will contain the
     *                                images that the callback was based on.
     * @param listener        A reference to the listener receiving the above mentioned callbacks
     * @param callbackHandler Identifies the thread that the callbacks will be made on.
     *                        Null means "use the thread that called StartScan()".
     */
    public void StartScan(final boolean includeImagesInCallback, final BarcodeDetectedListener listener, Handler callbackHandler) {
        if (callbackHandler == null)
            callbackHandler = new Handler();
        final Handler finalHandler = callbackHandler;

        mProcessingThread = new HandlerThread("FastBarcodeScanner processing thread");
        mProcessingThread.start();
        mProcessingHandler = new Handler(mProcessingThread.getLooper());

        mImageSource.start(
                new IStillSequenceCamera.OnImageAvailableListener() {

                    @Override
                    public void onImageAvailable(Image image) {
                        if (mPaused)
                            image.close();
                        else
                            processSingleImage(image, includeImagesInCallback, listener, finalHandler);
                    }

                    @Override
                    public void onJpegImageAvailable(byte[] jpegData, int width, int height) {
                        if (!mPaused)
                            processSingleJpeg(jpegData, width, height, includeImagesInCallback, listener, finalHandler);
                    }

                    @Override
                    public void onError(Exception error) {
                        if (!mPaused)
                        FastBarcodeScanner.this.onError(error, listener, finalHandler);
                    }

                },
                mProcessingHandler
        );
    }

    /**
     * Similar to #StartScan, except that it scans all barcodes in view, not just the first
     * one found.
     *
     * @param includeImagesInCallback Whether the callbacks to the listener will contain the
     *                                images that the callback was based on.
     * @param listener        A reference to the listener receiving the above mentioned callbacks
     * @param callbackHandler Identifies the thread that the callbacks will be made on.
     *                        Null means "use the thread that called StartScan()".
     */
    public void StartMultiScan(final boolean includeImagesInCallback, final MultipleBarcodesDetectedListener listener, Handler callbackHandler) {
        if (callbackHandler == null)
            callbackHandler = new Handler();
        final Handler finalHandler = callbackHandler;

        mProcessingThread = new HandlerThread("FastBarcodeScanner processing thread");
        mProcessingThread.start();
        mProcessingHandler = new Handler(mProcessingThread.getLooper());

        mImageSource.start(
                new IStillSequenceCamera.OnImageAvailableListener() {

                    @Override
                    public void onImageAvailable(Image source) {
                        if (mPaused)
                            source.close();
                        else
                            processMultiImage(source, includeImagesInCallback, listener, finalHandler);
                    }

                    @Override
                    public void onJpegImageAvailable(byte[] jpegData, int width, int height) {
                        if (!mPaused)
                            processMultiJpeg(jpegData, width, height, includeImagesInCallback, listener, finalHandler);
                    }

                    @Override
                    public void onError(Exception error) {
                        if (!mPaused)
                            FastBarcodeScanner.this.onError(error, listener, finalHandler);
                    }

                },
                mProcessingHandler
        );
    }

    private boolean mPaused = false;
    public void Pause()
    {
        mPaused = true;
    }

    public void Resume()
    {
        mPaused = false;
    }

    /**
     * Stops the scanning process started by StartScan() or StartMultiScan() and frees any shared system resources
     * (e.g. the camera). StartScan() or StartMultiScan() can always be called to restart.
     *
     * StopScan() and StartScan()/StartMultiScan() are thus well suited for use from the onPause() and onResume()
     * handlers of a calling application.
     */
    public void StopScan() {
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
    }

    /**
     * Disposes irrevocably of all resources. This instance cannot be used after close() is called.
     */
    public void close() {
        this.mImageSource.close();
    }

    //*********************************************************************
    //*********************************************************************

    public interface ErrorDetectedListener {
        void onError(Exception error);
    }

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
    public interface BarcodeDetectedListener extends ErrorDetectedListener {
        void onSingleBarcodeAvailable(BarcodeInfo barcode, byte[] image, int format, int width, int height);
    }

    public interface MultipleBarcodesDetectedListener extends ErrorDetectedListener {
        void onMultipleBarcodeAvailable(BarcodeInfo[] barcodes, byte[] image, int format, int width, int height);
    }

    public class BarcodeInfo {
        public final String barcode;
        public final Point[] points;

        BarcodeInfo(String barcode, Point[] points) {
            this.barcode = barcode;
            this.points = points;
        }
    }

    private void processSingleImage(Image source, boolean includeImagesInCallback, BarcodeDetectedListener listener, Handler callbackHandler) {
        // Decode the image:
        try {
            if (listener == null) {
                // Important: free the image as soon as possible,
                // thus making room for a new capture to begin:
                source.close();
                return;
            }

            BinaryBitmap bitmap = mBarcodeFinder.DecodeImage(source);
            Barcode bc = mBarcodeFinder.findSingle(bitmap);
            if (bc == null) {
                source.close();
                onSingleBarcodeFound(null, null, listener, callbackHandler);
            } else {
                onSingleBarcodeFound(bc, includeImagesInCallback ? source : null, listener, callbackHandler);
                source.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            onError(e, listener, callbackHandler);
        }
        finally {
            source.close();
        }
    }

    private void processSingleJpeg(byte[] jpegData, int width, int height, boolean includeImagesInCallback, BarcodeDetectedListener listener, Handler callbackHandler) {
        if (listener == null) {
            return;
        }

        try {
            BinaryBitmap bitmap = mBarcodeFinder.DecodeImage(jpegData, width, height);
            Barcode bc = mBarcodeFinder.findSingle(bitmap);
            if (bc == null) {
                onSingleBarcodeFound(null, null, listener, callbackHandler);
            } else {
                onSingleBarcodeFound(bc, null/*source*/, listener, callbackHandler);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            onError(e, listener, callbackHandler);
        }
    }

    private void processMultiImage(Image source, boolean includeImagesInCallback, MultipleBarcodesDetectedListener listener, Handler callbackHandler) {
        // Decode the image:
        try {
            if (listener == null) {
                // Important: free the image as soon as possible,
                // thus making room for a new capture to begin:
                source.close();
                return;
            }

            BinaryBitmap bitmap = mBarcodeFinder.DecodeImage(source);
            Barcode[] bcs = mBarcodeFinder.findMultiple(bitmap);
            if (bcs == null) {
                source.close();
                onMultipleBarcodesFound(null, null, listener, callbackHandler);
            } else {
                onMultipleBarcodesFound(bcs, includeImagesInCallback ? source : null, listener, callbackHandler);
                source.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            onError(e, listener, callbackHandler);
        }
        finally {
            source.close();
        }
    }

    private void processMultiJpeg(byte[] jpegData, int width, int height, boolean includeImagesInCallback, MultipleBarcodesDetectedListener listener, Handler callbackHandler)
    {
        if (listener == null) {
            return;
        }

        try {
            BinaryBitmap bitmap = mBarcodeFinder.DecodeImage(jpegData, width, height);
            Barcode[] bcs = mBarcodeFinder.findMultiple(bitmap);
            if (bcs == null) {
                onMultipleBarcodesFound(null, null, listener, callbackHandler);
            } else {
                onMultipleBarcodesFound(bcs, null/*source*/, listener, callbackHandler);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            onError(e, listener, callbackHandler);
        }
    }
    //*********************************************************************
    //* Managing Barcode events:
    //* ========================
    //* Only bother the listener if a *new* barcode is detected.
    //*
    //* Furthermore,
    //*********************************************************************

    private String mLastReportedBarcode = null;
    private int mNoBarcodeCount = 0;
    private final int NO_BARCODE_IGNORE_LIMIT = 5;

    private void onSingleBarcodeFound(Barcode bc, Image source, BarcodeDetectedListener listener, Handler callbackHandler) {
        if (bc == null || bc.contents == null) {
            Log.v(TAG, "Found barcode: " + null);
            mNoBarcodeCount++;
            if (mLastReportedBarcode != null && mNoBarcodeCount >= NO_BARCODE_IGNORE_LIMIT) {
                mLastReportedBarcode = null;
                _onSingleBarcode(null, null, source, listener, callbackHandler);
            }
        } else {
            Log.v(TAG, "Found barcode: " + bc.contents);
            mNoBarcodeCount = 0;
            if (!bc.contents.equals(mLastReportedBarcode)) {
                mLastReportedBarcode = bc.contents;
                _onSingleBarcode(mLastReportedBarcode, bc.points, source, listener, callbackHandler);
            }
        }
    }

    private void _onSingleBarcode(String barcode, Point[] points, final Image source, final BarcodeDetectedListener listener, Handler callbackHandler) {
        if (listener != null) {
            final BarcodeInfo bc = new BarcodeInfo(barcode, points);
            final byte[] serialized = (source == null) ? null : ImageDecoder.Serialize(source);
            final int width = (source == null) ? 0 : source.getWidth();
            final int height = (source == null) ? 0 : source.getHeight();
            final int format = (source == null) ? ImageFormat.UNKNOWN : source.getFormat();
            callbackHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            listener.onSingleBarcodeAvailable(bc, serialized, format, width, height);
                        }
                    }
            );
        }
    }

    private Barcode[] mLastReportedMultiBarcode = null;

    private void onMultipleBarcodesFound(Barcode[] bcs, Image source, MultipleBarcodesDetectedListener listener, Handler callbackHandler) {
        if (bcs == null) {
            Log.v(TAG, "Found 0 barcodes");
            mNoBarcodeCount++;
            if (mLastReportedMultiBarcode != null && mNoBarcodeCount >= NO_BARCODE_IGNORE_LIMIT) {
                mLastReportedMultiBarcode = null;
                _onMultipleBarcodes(mLastReportedMultiBarcode, source, listener, callbackHandler);
            }
        } else {
            Log.v(TAG, "Found " + bcs.length + " barcodes");
            mNoBarcodeCount = 0;
            if (!_equals(bcs, mLastReportedMultiBarcode)) {
                mLastReportedMultiBarcode = bcs;
                _onMultipleBarcodes(mLastReportedMultiBarcode, source, listener, callbackHandler);
            }
        }
    }

    private static boolean _equals(Barcode[] bcs1, Barcode[] bcs2) {
        if (bcs1 == bcs2)
            return true;

        if (bcs1 == null)
            return false;

        if (bcs2 == null)
            return false;

        if (bcs1.length != bcs2.length)
            return false;

        for (int n = 0; n < bcs1.length; n++) {
            if (bcs1[n] != bcs2[n])
                return false;
        }

        return true;
    }

    private void _onMultipleBarcodes(final Barcode[] barcodes, final Image source, final MultipleBarcodesDetectedListener listener, Handler callbackHandler) {
        if (listener != null) {
            final byte[] serialized = (source == null) ? null : ImageDecoder.Serialize(source);
            final int width = (source == null) ? 0 : source.getWidth();
            final int height = (source == null) ? 0 : source.getHeight();
            final int format = (source == null) ? ImageFormat.UNKNOWN : source.getFormat();
            callbackHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            listener.onMultipleBarcodeAvailable(_convert(barcodes), serialized, format, width, height);
                        }
                    }
            );
        }
    }

    private BarcodeInfo[] _convert(Barcode[] barcodes) {
        if (barcodes == null)
            return null;

        BarcodeInfo[] res = new BarcodeInfo[barcodes.length];
        for (int n = 0; n < barcodes.length; n++)
            res[n] = new BarcodeInfo(barcodes[n].contents, barcodes[n].points);

        return res;
    }

    private void onError(final Exception error, final ErrorDetectedListener listener, Handler callbackHandler) {
        if (listener != null) {
            callbackHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            listener.onError(error);
                        }
                    }
            );
        }
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
}
