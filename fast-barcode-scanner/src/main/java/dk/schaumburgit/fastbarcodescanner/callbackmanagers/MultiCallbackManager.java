package dk.schaumburgit.fastbarcodescanner.callbackmanagers;
import android.graphics.ImageFormat;
import android.media.Image;
import android.os.Handler;
import android.util.Log;

import dk.schaumburgit.fastbarcodescanner.IBarcodeScanner.BarcodeInfo;
import dk.schaumburgit.fastbarcodescanner.IBarcodeScanner.MultipleBarcodesDetectedListener;
import dk.schaumburgit.fastbarcodescanner.imageutils.ImageDecoder;
import dk.schaumburgit.trackingbarcodescanner.Barcode;
import dk.schaumburgit.trackingbarcodescanner.ScanOptions;

public class MultiCallbackManager //extends ErrorCallbackHandler
{
    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "BarcodeScanner";

    private final ScanOptions mScanOptions;
    private final Handler callbackHandler;

    protected final MultipleBarcodesDetectedListener listener;

    private final CallBackOptions callbackOptions;

    public MultiCallbackManager(
            ScanOptions scanOptions,
            CallBackOptions callbackOptions,
            MultipleBarcodesDetectedListener listener,
            Handler callbackHandler
    )
    {
        if (scanOptions == null)
            throw new IllegalArgumentException("scanOptions is null");
        this.mScanOptions = scanOptions;

        if (callbackHandler == null)
            throw new IllegalArgumentException("callbackHandler is null");
        this.callbackHandler= callbackHandler;

        if (callbackOptions == null)
            throw new IllegalArgumentException("callbackOptions is null");
        this.callbackOptions = callbackOptions;

        if (listener == null)
            throw new IllegalArgumentException("listener is null");
        this.listener = listener;
    }

    private Barcode[] mLastReportedMultiBarcode = null;

    private int mNoBarcodeCount = 0;

    public void onMultipleBarcodesFound(Barcode[] bcs, Image source) {
        if (bcs == null) {
            Log.v(TAG, "Found 0 barcodes");
            mNoBarcodeCount++;
            //if (mLastReportedMultiBarcode != null && mNoBarcodeCount >= NO_BARCODE_IGNORE_LIMIT) {
            if (mNoBarcodeCount >= this.callbackOptions.blankReluctance) {
                //mLastReportedMultiBarcode = null;
                _onMultipleBarcodes(mLastReportedMultiBarcode, callbackOptions.includeImage ? source : null, listener, callbackHandler);
            }
        } else {
            Log.v(TAG, "Found " + bcs.length + " barcodes");
            mNoBarcodeCount = 0;
            if (!_equals(bcs, mLastReportedMultiBarcode)) {
                mLastReportedMultiBarcode = bcs;
                _onMultipleBarcodes(mLastReportedMultiBarcode, callbackOptions.includeImage ? source : null, listener, callbackHandler);
            }
        }
    }

    protected int mConsecutiveErrorCount = 0;
    public void onError(final Exception error) {
        mConsecutiveErrorCount++;
        if (mConsecutiveErrorCount >= this.callbackOptions.errorReluctance) {
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
}
