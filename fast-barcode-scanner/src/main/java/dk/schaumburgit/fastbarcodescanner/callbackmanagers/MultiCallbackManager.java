package dk.schaumburgit.fastbarcodescanner.callbackmanagers;
import android.media.Image;
import android.os.Handler;
import android.util.Log;

import dk.schaumburgit.fastbarcodescanner.IBarcodeScanner.BarcodeInfo;
import dk.schaumburgit.fastbarcodescanner.IBarcodeScanner.MultipleBarcodesDetectedListener;
import dk.schaumburgit.stillsequencecamera.ISource;
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

    public void onMultipleBarcodesFound(Barcode[] bcs, ISource source) {
        if (bcs == null || bcs.length == 0) {
            Log.v(TAG, "Found 0 barcodes");
            mNoBarcodeCount++;
            //if (mLastReportedMultiBarcode != null && mNoBarcodeCount >= NO_BARCODE_IGNORE_LIMIT) {
            if (mNoBarcodeCount >= this.callbackOptions.debounceBlanks) {
                //mLastReportedMultiBarcode = null;
                _onBlank(listener, callbackHandler);
            }
        } else {
            Log.v(TAG, "Found " + bcs.length + " barcodes");
            mNoBarcodeCount = 0;
            if (!_equals(bcs, mLastReportedMultiBarcode)) {
                mLastReportedMultiBarcode = bcs;
                _onMultipleBarcodes(mLastReportedMultiBarcode, callbackOptions.includeImage ? source.save() : null, listener, callbackHandler);
            }
        }
    }

    protected int mConsecutiveErrorCount = 0;
    public void onError(final Exception error) {
        mConsecutiveErrorCount++;
        if (mConsecutiveErrorCount >= this.callbackOptions.debounceErrors) {
            callbackHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            listener.OnError(error);
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

    private void _onMultipleBarcodes(final Barcode[] barcodes, final String sourceUrl, final MultipleBarcodesDetectedListener listener, Handler callbackHandler) {
        if (listener != null) {
            callbackHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            listener.OnHits(_convert(barcodes), sourceUrl);
                        }
                    }
            );
        }
    }

    private void _onBlank(final MultipleBarcodesDetectedListener listener, Handler callbackHandler) {
        if (listener != null) {
            callbackHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            listener.OnBlank();
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
