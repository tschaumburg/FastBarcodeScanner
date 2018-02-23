package dk.schaumburgit.fastbarcodescanner.callbackmanagers;

import android.graphics.ImageFormat;
import android.graphics.Point;
import android.media.Image;
import android.os.Handler;
import android.util.Log;

import java.util.Objects;

import dk.schaumburgit.fastbarcodescanner.IBarcodeScanner.BarcodeDetectedListener;
import dk.schaumburgit.fastbarcodescanner.IBarcodeScanner.BarcodeInfo;
import dk.schaumburgit.fastbarcodescanner.imageutils.ImageDecoder;
import dk.schaumburgit.trackingbarcodescanner.Barcode;
import dk.schaumburgit.trackingbarcodescanner.ScanOptions;

public class SingleCallbackManager //extends ErrorCallbackHandler
{
    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "BarcodeScanner";

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

    public void onBlank(Image source) {
        //this.onBarcode(null, source);
        Log.v(TAG, "Found barcode: " + null);
        mConsecutiveBlankCount++;
        mConsecutiveErrorCount = 0;
        //if (mLastReportedBarcode != null && mNoBarcodeCount >= this.mFilterOptions.blankReluctance) {
        if (mConsecutiveBlankCount >= this.callbackOptions.blankReluctance) {
            //mLastReportedBarcode = null;
            sendBlank(callbackOptions.includeImage ? source : null);
        }
    }

    public void onBarcode(Barcode bc, Image source) {
        // Found nothing
        if (bc == null || bc.contents == null) {
            this.onBlank(source);
            return;
        }

        // Found an empty-marker
        if (bc.contents.equalsIgnoreCase(this.mScanOptions.emptyMarker)) {
            this.onBlank(source);
            mConsecutiveBlankCount = 0;
            return;
        }

        // Found a proper barcode
        {
            Log.v(TAG, "Found barcode: " + bc.contents);
            mConsecutiveBlankCount = 0;
            mConsecutiveErrorCount = 0;
            sendBarcode(bc.contents, bc.points, callbackOptions.includeImage ? source : null);
        }
    }


    public void onError(final Exception error) {
        mConsecutiveErrorCount++;
        if (mConsecutiveErrorCount >= this.callbackOptions.errorReluctance) {
            sendError(error);
        }
    }

    //************************************************************************
    //* Sending events to listener
    //*
    //************************************************************************
    private enum ELastEvent {None, Barcode, Blank, Error}
    private ELastEvent mLatestEvent = ELastEvent.None;

    private String mLastReportedBarcode = "some random text 1234056g"; // null=> blank

    private void sendBarcode(String barcode, Point[] points, final Image source) {
        switch (this.callbackOptions.resultVerbosity)
        {
            case None:
                return;
            case First:
                if (mLatestEvent == ELastEvent.Barcode)
                    return;
                break;
            case Changes:
                if (mLatestEvent == ELastEvent.Barcode)
                {
                    if (Objects.equals(barcode, mLastReportedBarcode))
                        return;
                }
                break;
            case Allways:
                break;
        }

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

        mLastReportedBarcode = barcode;
        mLatestEvent = ELastEvent.Barcode;
    }

    private void sendBlank(final Image source) {
        switch (this.callbackOptions.blankVerbosity)
        {
            case None:
                mLatestEvent = ELastEvent.Blank;
                return;
            case First:
                if (this.mLatestEvent == ELastEvent.Blank)
                    return;
                break;
            case Allways:
                break;
        }

        callbackHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onSingleBarcodeAvailable(null, null, ImageFormat.UNKNOWN, 0, 0);
                    }
                }
        );

        mLatestEvent = ELastEvent.Blank;
    }

    private void sendError(final Exception error) {
        switch (this.callbackOptions.errorVerbosity)
        {
            case None:
                return;
            case First:
                if (this.mLatestEvent == ELastEvent.Error)
                    return;
                break;
            case Allways:
                break;
        }

        callbackHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onError(error);
                    }
                }
        );

        mLatestEvent = ELastEvent.Error;
    }
}
