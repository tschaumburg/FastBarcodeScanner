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
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;
import java.sql.Timestamp;
import java.util.Date;

import dk.schaumburgit.stillsequencecamera.IStillSequenceCamera;
import dk.schaumburgit.stillsequencecamera.camera.StillSequenceCamera;
import dk.schaumburgit.stillsequencecamera.camera2.StillSequenceCamera2;
import dk.schaumburgit.trackingbarcodescanner.TrackingBarcodeScanner;

public class FastBarcodeScanner
        implements IStillSequenceCamera.OnImageAvailableListener//, IStillSequenceCamera.CameraStateChangeListener
{
    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "FastBarcodeScanner";

    private Activity mActivity;
    private Activity getActivity() {
        return mActivity;
    }

    private IStillSequenceCamera mImageSource;
    private TrackingBarcodeScanner mBarcodeFinder;

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
        this.mImageSource = new StillSequenceCamera2(activity, textureView, mBarcodeFinder.GetPreferredFormats(), 1024*768);
        this.mImageSource.setup();
    }

    /**
     *
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
        this.mImageSource.setup();
    }

    public void StartScan()
    {
        mImageSource.start(this);
    }

    public void StopScan()
    {
        mImageSource.stop();
    }

    public void close()
    {
        this.mImageSource.close();
    }

    @Override
    public void onImageAvailable(int format, byte[] bytes, int width, int height) {
        // Get the image data (we requested JPEG) into a byte buffer:
        Date first = new Date();

        // Decode the image:
        Date second = new Date();
        try {
            String newBarcode = mBarcodeFinder.find(format, width, height, bytes);
            Log.i(TAG, "Found barcode: " + newBarcode);
            Date third = new Date();

            // Tell the world:
            callBarcodeListener(newBarcode);
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

    private int nImagesProcessed = 0;
    private void saveImage(byte[] bytes) {
        File dir = getActivity().getExternalFilesDir(null);
        nImagesProcessed++;
        File saveAs = new File(dir, "qr" + nImagesProcessed + ".jpg");

        FileOutputStream output = null;
        try {
            output = new FileOutputStream(saveAs);
            output.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != output) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //*********************************************************************
    //* Managing Barcode events:
    //* ========================
    //*
    //*********************************************************************

    private BarcodeDetectedListener mBarcodeListener = null;
    public void setBarcodeListener(BarcodeDetectedListener listener)
    {
        mBarcodeListener = listener;
    }

    private String mLastReportedBarcode = null;
    private int mNoBarcodeCount = 0;
    private void callBarcodeListener(String barcode)
    {
        //mBarcodeListener.onBarcodeAvailable(barcode);
        //Log.d(TAG, "Scanned " + barcode);
        //if ( 1 == 1)
        //    return;

        if (barcode == null)
        {
            mNoBarcodeCount++;
            if (mLastReportedBarcode != null && mNoBarcodeCount >= 5)
            {
                mLastReportedBarcode = null;
                mBarcodeListener.onBarcodeAvailable(mLastReportedBarcode);
            }
        }
        else
        {
            mNoBarcodeCount = 0;
            if (!barcode.equals(mLastReportedBarcode))
            {
                mLastReportedBarcode = barcode;
                if (mBarcodeListener != null)
                    mBarcodeListener.onBarcodeAvailable(mLastReportedBarcode);
            }
        }
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
     * with a null barcode parameter.
     * </p>
     */
    public interface BarcodeDetectedListener {
        /**
         * Callback that is called when a new image is available from ImageReader.
         *
         * @param barcode the barcode detected.
         */
        void onBarcodeAvailable(String barcode);
    }
}

