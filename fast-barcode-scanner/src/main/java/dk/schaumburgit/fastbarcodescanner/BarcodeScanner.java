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
import android.media.Image;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.SurfaceView;

import com.google.zxing.BinaryBitmap;

import java.security.InvalidParameterException;

import dk.schaumburgit.fastbarcodescanner.callbackmanagers.CallBackOptions;
import dk.schaumburgit.fastbarcodescanner.callbackmanagers.MultiCallbackManager;
import dk.schaumburgit.fastbarcodescanner.callbackmanagers.SingleCallbackManager;
import dk.schaumburgit.stillsequencecamera.ISource;
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
 * The BarcodeScanner captures images from your front-facing camera at the fastest
 * possible rate, scans them for barcodes and reports any changes to the caller
 * via a listener callback.
 *
 * The image capture is done unobtrusively without any visible UI, using a background thread.
 *
 * For newer Android versions (Lollipop and later), the new, faster Camera2 API is supported.
 * For older versions, BarcodeScanner falls back to using the older, slower camera API.
 *
 * When the Camera2 API is available, the BarcodeScanner can be created with a TextureView
 * if on-screen preview is desired, or without for headless operation.
 *
 * For older Android versions, the BarcodeScanner *must* be created with a SurfaceView,
 * and the SurfaceView *must* be visible on-screen. Setting the SurfaceView to 1x1 pixel
 * will however make it effectively invisible.
 *
 * Regardless of Android version, the FastbarcodeScanner *must* be supplied with a reference
 * to the current Activity (used for accessing e.g. the camera, and other system resources).
 *
 */
class BarcodeScanner implements IBarcodeScanner {

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "BarcodeScanner";

    private final Activity mActivity;
    private HandlerThread mProcessingThread;
    private Handler mProcessingHandler;

    private final IStillSequenceCamera mImageSource;
    private final TrackingBarcodeScanner mBarcodeFinder;
    private final ConfigManager mFormatChooser;

    private final ScanOptions mScanOptions;
    private final CallBackOptions mCallBackOptions;

    @TargetApi(21)
    BarcodeScanner(
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
        this.mImageSource = new StillSequenceCamera2(activity, cameraOptions);
        this.mBarcodeFinder = new TrackingBarcodeScanner(scanOptions, trackingOptions);
        this.mFormatChooser = new ConfigManager(mImageSource, mBarcodeFinder);

        ConfigInfo bestFormatInfo = this.mFormatChooser.calculateBestFormat(cameraOptions.minPixels);
        this.mImageSource.setup(bestFormatInfo.imageFormat, bestFormatInfo.imageWidth, bestFormatInfo.imageHeight);
    }


    /**
     * Creates a BarcodeScanner using the deprecated Camera API supported
     * on Android versions prior to Lollipop (API level lower than 21).
     * <p>
     * The created BarcodeScanner will display preview output in the supplied
     * SurfaceView. This parameter *must* be non-null, and the referenced SurfaceView
     * *must* be displayed on-screen, with a minimum size of 1x1 pixels. This is a
     * non-negotiable requirement from the camera API (upgrade to API level 21 for
     * true headless operation).
     *
     * @param activity Non-null
     * @deprecated This constructor uses the deprecated Camera API. We recommend using
     * one of the other BarcodeScanner constructors (using the new
     * {@link android.hardware.camera2} API) for new applications.
     */
    @Deprecated
    BarcodeScanner(
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
        this.mFormatChooser = new ConfigManager(mImageSource, mBarcodeFinder);

        ConfigInfo bestFormatInfo = this.mFormatChooser.calculateBestFormat(cameraOptions.minPixels);
        this.mImageSource.setup(bestFormatInfo.imageFormat, bestFormatInfo.imageWidth, bestFormatInfo.imageHeight);
    }

    /**
     * Creates a BarcodeScanner using the deprecated Camera API supported
     * on Android versions prior to Lollipop (API level lower than 21).
     * <p>
     * The created BarcodeScanner will display preview output in the supplied
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
     * one of the other BarcodeScanner constructors (using the new
     * {@link android.hardware.camera2} API) for new applications.
     */
    @Deprecated
    BarcodeScanner(
            Activity activity,
            SurfaceView surfaceView,
            int resolution
    ) {
        this(activity, new StillSequenceCameraOptions(surfaceView, resolution), new ScanOptions(), new TrackingOptions(), new CallBackOptions());
    }

    @Override
    public void StartScan(final BarcodeDetectedListener listener) {
        StartScan(listener, null);
    }

    @Override
    public void StartScan(
            final BarcodeDetectedListener listener,
            Handler callbackHandler
    ) {
        if (callbackHandler == null)
            callbackHandler = new Handler();
        final Handler finalHandler = callbackHandler;

        mProcessingThread = new HandlerThread("BarcodeScanner processing thread");
        mProcessingThread.start();
        mProcessingHandler = new Handler(mProcessingThread.getLooper());

        final SingleCallbackManager callbackManager = new SingleCallbackManager(this.mScanOptions, this.mCallBackOptions, listener, finalHandler);
        mImageSource.start(
                new IStillSequenceCamera.OnImageAvailableListener() {

                    @Override
                    public void onImageAvailable(ISource source, BinaryBitmap bitmap) {
                        if (!mPaused)
                            processSingleImage(source, bitmap, callbackManager);
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

    @Override
    public void StartMultiScan(final MultipleBarcodesDetectedListener listener, Handler callbackHandler) {
        StartMultiScan(false, 1, listener, callbackHandler);
    }

    @Override
    public void StartMultiScan(
            final boolean includeImagesInCallback,
            final int minNoOfBarcodes,
            final MultipleBarcodesDetectedListener listener,
            Handler callbackHandler
    ) {
        if (callbackHandler == null)
            callbackHandler = new Handler();
        final Handler finalHandler = callbackHandler;

        mProcessingThread = new HandlerThread("BarcodeScanner processing thread");
        mProcessingThread.start();
        mProcessingHandler = new Handler(mProcessingThread.getLooper());

        final MultiCallbackManager callbackManager = new MultiCallbackManager(this.mScanOptions, this.mCallBackOptions, listener, finalHandler);
        mImageSource.start(
                new IStillSequenceCamera.OnImageAvailableListener() {

                    @Override
                    public void onImageAvailable(ISource source, BinaryBitmap bitmap) {
                        if (!mPaused)
                            processMultiImage(source, bitmap, minNoOfBarcodes, callbackManager);
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

    @Override
    public void Pause() {
        mPaused = true;
    }

    @Override
    public void Resume() {
        mPaused = false;
    }

    @Override
    public void StopScan() {
        mImageSource.stop();

        if (mProcessingThread != null) {
            try {
                mProcessingThread.quit();
                mProcessingThread.join();
                mProcessingThread = null;
                mProcessingHandler = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void Close() {
        this.mImageSource.close();
    }

    //*********************************************************************
    //*********************************************************************

    private void processSingleImage(ISource source, BinaryBitmap bitmap, SingleCallbackManager callbackManager) {
        // Decode the image:
        try {
            Barcode bc = mBarcodeFinder.findSingle(bitmap);
            if (bc == null) {
                if (source!=null)
                {
                    source.close();
                    source = null;
                }
                callbackManager.onBlank();
            } else {
                callbackManager.onBarcode(bc, source);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            if (source!=null)
            {
                source.close();
                source = null;
            }
            callbackManager.onError(e);
        } finally {
            if (source!=null)
            {
                source.close();
                source = null;
            }
        }
    }

    /*
    private void processSingleJpeg(byte[] jpegData, int width, int height, SingleCallbackManager callbackManager) {
        try {
            BinaryBitmap bitmap = mBarcodeFinder.DecodeImage(jpegData, width, height);
            Barcode bc = mBarcodeFinder.findSingle(bitmap);
            if (bc == null) {
                callbackManager.onBlank(null);
            } else {
                callbackManager.onBarcode(bc, null);//source);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            callbackManager.onError(e);
        }
    }
*/

    private void processMultiImage(ISource source, BinaryBitmap bitmap, int minNoOfBarcodes, MultiCallbackManager callbackManager) {
        // Decode the image:
        try {
            //BinaryBitmap bitmap = mBarcodeFinder.DecodeImage(source);
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

    /*
    private void processMultiJpeg(byte[] jpegData, int width, int height, int minNoOfBarcodes, MultiCallbackManager callbackManager) {
        try {
            BinaryBitmap bitmap = mBarcodeFinder.DecodeImage(jpegData, width, height);
            Barcode[] bcs = mBarcodeFinder.findMultiple(bitmap);
            if (bcs == null) {
                callbackManager.onMultipleBarcodesFound(null, null);
            } else if (bcs.length < minNoOfBarcodes) {
                callbackManager.onMultipleBarcodesFound(null, null);
            } else {
                callbackManager.onMultipleBarcodesFound(bcs, null);//source);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
            callbackManager.onError(e);
        }
    }
*/

    @Override
    public boolean isLockFocus() {
        return mImageSource.isLockFocus();
    }

    @Override
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


