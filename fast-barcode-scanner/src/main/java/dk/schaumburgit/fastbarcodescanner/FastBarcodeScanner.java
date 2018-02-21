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

import java.security.InvalidParameterException;
import java.util.EnumSet;
import java.util.Map;

import dk.schaumburgit.fastbarcodescanner.callbackmanagers.MultiCallbackManager;
import dk.schaumburgit.fastbarcodescanner.callbackmanagers.SingleCallbackManager;
import dk.schaumburgit.fastbarcodescanner.imageutils.ImageDecoder;
import dk.schaumburgit.stillsequencecamera.IStillSequenceCamera;
import dk.schaumburgit.stillsequencecamera.camera.StillSequenceCamera;
import dk.schaumburgit.stillsequencecamera.camera.StillSequenceCameraOptions;
import dk.schaumburgit.stillsequencecamera.camera2.StillSequenceCamera2;
import dk.schaumburgit.stillsequencecamera.camera2.StillSequenceCamera2Options;
import dk.schaumburgit.trackingbarcodescanner.Barcode;
import dk.schaumburgit.trackingbarcodescanner.ScanOptions;
import dk.schaumburgit.trackingbarcodescanner.TrackingBarcodeScanner;
import dk.schaumburgit.trackingbarcodescanner.TrackingOptions;

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

    private final ScanOptions mScanOptions;
    private final CallBackOptions mCallBackOptions;

    @TargetApi(21)
    public FastBarcodeScanner(
            Activity activity,
            StillSequenceCamera2Options cameraOptions,
            ScanOptions scanOptions,
            TrackingOptions trackingOptions,
            CallBackOptions callBackOptions
    ) {
        if (activity == null)
            throw new InvalidParameterException("activity cannot be null");

        this.mScanOptions = scanOptions;
        this.mCallBackOptions = callBackOptions;

        this.mActivity = activity;
        this.mBarcodeFinder = new TrackingBarcodeScanner(scanOptions, trackingOptions);
        this.mImageSource = new StillSequenceCamera2(activity, cameraOptions);

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

    public FastBarcodeScanner(
            Activity activity,
            TextureView textureView,
            int resolution
    ) {
        this(
                activity,
                new StillSequenceCamera2Options(textureView, resolution, StillSequenceCamera2Options.Facing.Back),
                new ScanOptions(),
                new TrackingOptions(),
                new CallBackOptions()
        );
    }

    /**
     * Creates a FastBarcodeScanner using the deprecated Camera API supported
     * on Android versions prior to Lollipop (API level lower than 21).
     * <p>
     * The created FastBarcodeScanner will display preview output in the supplied
     * SurfaceView. This parameter *must* be non-null, and the referenced SurfaceView
     * *must* be displayed on-screen, with a minimum size of 1x1 pixels. This is a
     * non-negotiable requirement from the camera API (upgrade to API level 21 for
     * true headless operation).
     *
     * @param activity Non-null
     * @deprecated This constructor uses the deprecated Camera API. We recommend using
     * one of the other FastBarcodeScanner constructors (using the new
     * {@link android.hardware.camera2} API) for new applications.
     */
    @Deprecated
    public FastBarcodeScanner(
            Activity activity,
            StillSequenceCameraOptions cameraOptions,
            ScanOptions scanOptions,
            TrackingOptions trackingOptions,
            CallBackOptions callBackOptions
    ) {
        if (activity == null)
            throw new InvalidParameterException("activity cannot be null");

        this.mScanOptions = scanOptions;
        this.mCallBackOptions = callBackOptions;

        if (cameraOptions == null)
            throw new InvalidParameterException("cameraOptions cannot be null");

        if (cameraOptions.preview == null)
            throw new InvalidParameterException("cameraOptions.preview cannot be null");

        this.mActivity = activity;
        this.mBarcodeFinder = new TrackingBarcodeScanner(scanOptions, trackingOptions);
        this.mImageSource = new StillSequenceCamera(activity, cameraOptions);

        Map<Integer, Double> cameraFormatCosts = mImageSource.getSupportedImageFormats();
        Map<Integer, Double> zxingFormatCosts = mBarcodeFinder.getPreferredImageFormats();

        int pictureFormat = calculateCheapestFormat(cameraFormatCosts, zxingFormatCosts);
        this.mImageSource.setup(pictureFormat);
    }

    /**
     * Creates a FastBarcodeScanner using the deprecated Camera API supported
     * on Android versions prior to Lollipop (API level lower than 21).
     * <p>
     * The created FastBarcodeScanner will display preview output in the supplied
     * SurfaceView. This parameter *must* be non-null, and the referenced SurfaceView
     * *must* be displayed on-screen, with a minimum size of 1x1 pixels. This is a
     * non-negotiable requirement from the camera API (upgrade to API level 21 for
     * true headless operation).
     *
     * @param activity    Non-null
     * @param surfaceView Non-null
     * @param resolution  The requested minimum resolution of the photos
     *                    taken during scanning.
     * @deprecated This constructor uses the deprecated Camera API. We recommend using
     * one of the other FastBarcodeScanner constructors (using the new
     * {@link android.hardware.camera2} API) for new applications.
     */
    @Deprecated
    public FastBarcodeScanner(
            Activity activity,
            SurfaceView surfaceView,
            int resolution
    ) {
        this(activity, new StillSequenceCameraOptions(surfaceView, resolution), new ScanOptions(), new TrackingOptions(), new CallBackOptions());
    }

    /**
     * Starts processing anything the back-camera sees, looking for any single barcode
     * in the captured photos. If several barcodes are in view, only the first one found
     * will be detected (consider using the less efficient #startMultiScan if you want
     * all barcodes)
     * <p>
     * The scanning is performed on a background thread. The supplied listener will
     * be called using the supplied handler whenever
     * there's a *change* in the barcode seen (i.e. if 200 consecutive images contain
     * the same barcode, only the first will generate a callback).
     * <p>
     * "No barcode" is signalled with a null value via the callback.
     * <p>
     * Example: After StartScan is called, the first 20 images contain no barcode, the
     * next 200 have barcode A, the next 20 have nothing. This will generate the
     * following callbacks:
     * <p>
     * Frame#1:   onBarcodeAvailable(null)
     * Frame#21:  onBarcodeAvailable("A")
     * Frame#221: onBarcodeAvailable(null)
     *
     * @param includeImagesInCallback Whether the callbacks to the listener will contain the
     *                                images that the callback was based on.
     * @param listener                A reference to the listener receiving the above mentioned
     *                                callbacks
     * @param callbackHandler         Identifies the thread that the callbacks will be made on.
     *                                Null means "use the thread that called StartScan()".
     */
    public void StartScan(
            final boolean includeImagesInCallback,
            final BarcodeDetectedListener listener,
            Handler callbackHandler
    ) {
        if (callbackHandler == null)
            callbackHandler = new Handler();
        final Handler finalHandler = callbackHandler;

        mProcessingThread = new HandlerThread("FastBarcodeScanner processing thread");
        mProcessingThread.start();
        mProcessingHandler = new Handler(mProcessingThread.getLooper());

        final SingleCallbackManager callbackManager = new SingleCallbackManager(this.mScanOptions, new CallBackOptions(includeImagesInCallback), listener, finalHandler);
        mImageSource.start(
                new IStillSequenceCamera.OnImageAvailableListener() {

                    @Override
                    public void onImageAvailable(Image image) {
                        if (mPaused)
                            image.close();
                        else
                            processSingleImage(image, callbackManager);
                    }

                    @Override
                    public void onJpegImageAvailable(byte[] jpegData, int width, int height) {
                        if (!mPaused)
                            processSingleJpeg(jpegData, width, height, callbackManager);
                    }

                    @Override
                    public void onError(Exception error) {
                        if (!mPaused)
                            callbackManager.onError(error);
                    }

                },
                mProcessingHandler
        );
    }

    /**
     * Similar to #StartScan, except that it scans all barcodes in view, not just the first
     * one found.
     *
     * @param listener        A reference to the listener receiving the above mentioned callbacks
     * @param callbackHandler Identifies the thread that the callbacks will be made on.
     *                        Null means "use the thread that called StartScan()".
     */
    public void StartMultiScan(final MultipleBarcodesDetectedListener listener, Handler callbackHandler) {
        StartMultiScan(false, 1, listener, callbackHandler);
    }

    /**
     * Similar to #StartScan, except that it scans all barcodes in view, not just the first
     * one found.
     *
     * @param includeImagesInCallback Whether the callbacks to the listener will contain the
     *                                images that the callback was based on.
     * @param minNoOfBarcodes
     * @param listener                A reference to the listener receiving the above mentioned callbacks
     * @param callbackHandler         Identifies the thread that the callbacks will be made on.
     *                                Null means "use the thread that called StartScan()".
     */
    public void StartMultiScan(
            final boolean includeImagesInCallback,
            final int minNoOfBarcodes,
            final MultipleBarcodesDetectedListener listener,
            Handler callbackHandler
    ) {
        if (callbackHandler == null)
            callbackHandler = new Handler();
        final Handler finalHandler = callbackHandler;

        mProcessingThread = new HandlerThread("FastBarcodeScanner processing thread");
        mProcessingThread.start();
        mProcessingHandler = new Handler(mProcessingThread.getLooper());

        final MultiCallbackManager callbackManager = new MultiCallbackManager(this.mScanOptions, new CallBackOptions(includeImagesInCallback), listener, finalHandler);
        mImageSource.start(
                new IStillSequenceCamera.OnImageAvailableListener() {

                    @Override
                    public void onImageAvailable(Image source) {
                        if (mPaused)
                            source.close();
                        else
                            processMultiImage(source, minNoOfBarcodes, callbackManager);
                    }

                    @Override
                    public void onJpegImageAvailable(byte[] jpegData, int width, int height) {
                        if (!mPaused)
                            processMultiJpeg(jpegData, width, height, minNoOfBarcodes, callbackManager);
                    }

                    @Override
                    public void onError(Exception error) {
                        if (!mPaused)
                            callbackManager.onError(error);
                    }

                },
                mProcessingHandler
        );
    }

    private boolean mPaused = false;

    public void Pause() {
        mPaused = true;
    }

    public void Resume() {
        mPaused = false;
    }

    /**
     * Stops the scanning process started by StartScan() or StartMultiScan() and frees any shared system resources
     * (e.g. the camera). StartScan() or StartMultiScan() can always be called to restart.
     * <p>
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

    private void processSingleImage(Image source, SingleCallbackManager callbackManager) {
        // Decode the image:
        try {
            BinaryBitmap bitmap = mBarcodeFinder.DecodeImage(source);
            Barcode bc = mBarcodeFinder.findSingle(bitmap);
            if (bc == null) {
                source.close();
                callbackManager.onBlank(null);
            } else {
                callbackManager.onBarcode(bc, source);
                source.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            callbackManager.onError(e);
        } finally {
            source.close();
        }
    }

    private void processSingleJpeg(byte[] jpegData, int width, int height, SingleCallbackManager callbackManager) {
        try {
            BinaryBitmap bitmap = mBarcodeFinder.DecodeImage(jpegData, width, height);
            Barcode bc = mBarcodeFinder.findSingle(bitmap);
            if (bc == null) {
                callbackManager.onBlank(null);
            } else {
                callbackManager.onBarcode(bc, null/*source*/);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            callbackManager.onError(e);
        }
    }

    private void processMultiImage(Image source, int minNoOfBarcodes, MultiCallbackManager callbackManager) {
        // Decode the image:
        try {
            BinaryBitmap bitmap = mBarcodeFinder.DecodeImage(source);
            Barcode[] bcs = mBarcodeFinder.findMultiple(bitmap);
            if (bcs == null) {
                source.close();
                callbackManager.onMultipleBarcodesFound(null, null);
            } else if (bcs.length < minNoOfBarcodes) {
                source.close();
                callbackManager.onMultipleBarcodesFound(null, null);
            } else {
                callbackManager.onMultipleBarcodesFound(bcs, source);
                source.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            callbackManager.onError(e);
        } finally {
            source.close();
        }
    }

    private void processMultiJpeg(byte[] jpegData, int width, int height, int minNoOfBarcodes, MultiCallbackManager callbackManager) {
        try {
            BinaryBitmap bitmap = mBarcodeFinder.DecodeImage(jpegData, width, height);
            Barcode[] bcs = mBarcodeFinder.findMultiple(bitmap);
            if (bcs == null) {
                callbackManager.onMultipleBarcodesFound(null, null);
            } else if (bcs.length < minNoOfBarcodes) {
                callbackManager.onMultipleBarcodesFound(null, null);
            } else {
                callbackManager.onMultipleBarcodesFound(bcs, null/*source*/);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            callbackManager.onError(e);
        }
    }


    public boolean isLockFocus() {
        return mImageSource.isLockFocus();
    }

    public void setLockFocus(boolean lockFocus) {
        mImageSource.setLockFocus(lockFocus);
    }
    //*********************************************************************
    //* Managing Barcode events:
    //* ========================
    //* Only bother the listener if a *new* barcode is detected.
    //*
    //* Furthermore,
    //*********************************************************************

}


