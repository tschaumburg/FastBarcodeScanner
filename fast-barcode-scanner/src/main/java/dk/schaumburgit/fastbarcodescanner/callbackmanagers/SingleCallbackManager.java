package dk.schaumburgit.fastbarcodescanner.callbackmanagers;

import android.graphics.Point;
import android.media.Image;
import android.os.Handler;
import android.util.Log;

import java.util.Objects;

import dk.schaumburgit.fastbarcodescanner.IBarcodeScanner.BarcodeDetectedListener;
import dk.schaumburgit.fastbarcodescanner.IBarcodeScanner.BarcodeInfo;
import dk.schaumburgit.stillsequencecamera.ISource;
import dk.schaumburgit.trackingbarcodescanner.Barcode;
import dk.schaumburgit.trackingbarcodescanner.ScanOptions;

public class SingleCallbackManager //extends ErrorCallbackHandler
{
    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "fast-barcode-scanner";

    private final ScanOptions mScanOptions;
    private final BarcodeDetectedListener listener;
    private final CallBackOptions callbackOptions;
    private final Handler callbackHandler;

    public SingleCallbackManager(
            ScanOptions scanOptions,
            CallBackOptions callbackOptions,
            BarcodeDetectedListener listener,
            Handler callbackHandler
    ) {
        if (scanOptions == null)
            throw new IllegalArgumentException("scanOptions is null");
        this.mScanOptions = scanOptions;

        if (callbackHandler == null)
            throw new IllegalArgumentException("callbackHandler is null");
        this.callbackHandler = callbackHandler;

        if (callbackOptions == null)
            throw new IllegalArgumentException("callbackOptions is null");
        this.callbackOptions = callbackOptions;

        if (listener == null)
            throw new IllegalArgumentException("listener is null");
        this.listener = listener;
    }

    //************************************************************************
    //* Receiving events from the scanner
    //* ==================================
    //*
    //************************************************************************
    private int mConsecutiveBlankCount = 0;
    private int mConsecutiveErrorCount = 0;

    public void onBlank() {
        if (this.mScanOptions.emptyMarker != null) {
            this.onError(new Exception("False blank detected"));
            Log.v(TAG, ": False blank detected" );
            return;
        }

        Log.v(TAG, "Found barcode: " + null);

        // Debounce:
        mConsecutiveBlankCount++;
        mConsecutiveErrorCount = 0;
        if (mConsecutiveBlankCount >= this.callbackOptions.debounceBlanks) {
            sendBlank();
            //sendBarcode("testblank", null, callbackOptions.includeImage ? source : null);
        } else {
            Log.v(TAG, "Debounced barcode: " + null);
        }
    }

    public void onBarcode(Barcode bc, ISource source) {
        // Found nothing
        if (bc == null || bc.contents == null) {
            this.onBlank();
            return;
        }

        if (callbackOptions.includeImage == false) {
            if (source != null) {
                source.close();
                source = null;
            }
        }

        if (this.mScanOptions.emptyMarker != null) {
            // If this is an empty-marker, it should be processed as a blank,
            // but without any debouncing:
            if (bc.contents.equalsIgnoreCase(this.mScanOptions.emptyMarker)) {
                if (source != null) {
                    source.close();
                    source = null;
                }
                mConsecutiveBlankCount = 0;
                mConsecutiveErrorCount = 0;
                sendBlank();
                return;
            }
        }

        Log.v(TAG, "Found barcode: " + bc.contents);

        // Debounce:
        mConsecutiveBlankCount = 0;
        mConsecutiveErrorCount = 0;
        sendBarcode(bc.contents, bc.points, source);
    }


    public void onError(final Exception error) {
        // Debounce:
        mConsecutiveErrorCount++;
        if (mConsecutiveErrorCount >= this.callbackOptions.debounceErrors) {
            sendError(error);
        } else {
            Log.v(TAG, "Debounced error" );
        }
    }

    //************************************************************************
    //* Sending events to listener
    //*
    //************************************************************************
    private enum ELastEvent {None, Barcode, Blank, Error}
    private ELastEvent mLatestEvent = ELastEvent.None;

    private String mLastReportedBarcode = "some random text 1234056g"; // null=> blank

    private void sendBarcode(String barcode, Point[] points, final ISource source)
    {
        try {
            // Conflation:
            switch (this.callbackOptions.conflateHits) {
                case None:
                    Log.v(TAG, "Conflating barcode: " + barcode);
                    return;
                case First:
                    if (mLatestEvent == ELastEvent.Barcode) {
                        Log.v(TAG, "Conflating barcode: " + barcode);
                        return;
                    }
                    break;
                case Changes:
                    if (mLatestEvent == ELastEvent.Barcode) {
                        if (stringEquals(barcode, mLastReportedBarcode)) {
                            Log.v(TAG, "Conflating barcode: " + barcode);
                            return;
                        }
                    }
                    break;
                case All:
                    break;
            }

            final BarcodeInfo bc = new BarcodeInfo(barcode, points);
            final String sourceUrl = (source == null) ? null : source.save();
            Log.v(TAG, "Sending barcode: " + bc.barcode + " (image: " + (sourceUrl == null ? "none" : sourceUrl));
            callbackHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            listener.OnHit(bc, sourceUrl);
                        }
                    }
            );

            mLastReportedBarcode = barcode;
            mLatestEvent = ELastEvent.Barcode;
        }
        finally {
            if (source!=null)
                source.close();
        }
    }

    public static boolean stringEquals(String str1, String str2) {
        return (str1 == null ? str2 == null : str1.equals(str2));
    }

    private void sendBlank() {
        // Conflation:
        switch (this.callbackOptions.conflateBlanks)
        {
            case None:
                mLatestEvent = ELastEvent.Blank;
                Log.v(TAG, "Conflated blank");
                return;
            case First:
            case Changes:
                if (this.mLatestEvent == ELastEvent.Blank) {
                    Log.v(TAG, "Conflated blank");
                    return;
                }
                break;
            case All:
                break;
        }

        Log.v(TAG, "Sending blank");
        callbackHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.OnBlank();
                    }
                }
        );

        mLatestEvent = ELastEvent.Blank;
    }

    private void sendError(final Exception error) {
        // Conflation:
        switch (this.callbackOptions.conflateErrors)
        {
            case None:
                return;
            case First:
            case Changes:
                if (this.mLatestEvent == ELastEvent.Error)
                    return;
                break;
            case All:
                break;
        }

        callbackHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.OnError(error);
                    }
                }
        );

        mLatestEvent = ELastEvent.Error;
    }
}
