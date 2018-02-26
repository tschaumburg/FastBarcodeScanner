package dk.schaumburgit.fastbarcodescanner;

import android.app.Activity;

import dk.schaumburgit.fastbarcodescanner.callbackmanagers.CallBackOptions;

/**
 * Created by Thomas on 08-02-2018.
 */

public interface IBarcodeScannerBuilder
{
    //ICaptureBuilder
    IBarcodeScannerBuilder resolution(int minPixels);

    // void filterImages(...rgb...);
    //IBarcodeScannerBuilder scanQR(int subtype);
    //IBarcodeScannerBuilder findBarcode(int barcodeType);
    //IBarcodeScannerBuilder findQR();

    IBarcodeScannerBuilder emptyMarker(String emptyMarkerContents);

    IBarcodeScannerBuilder debounceBlanks(int nSamples);
    IBarcodeScannerBuilder debounceErrors(int nSamples);

    IBarcodeScannerBuilder conflateHits(EventConflation hitConflation);
    IBarcodeScannerBuilder conflateBlanks(EventConflation blankConflation);
    IBarcodeScannerBuilder conflateErrors(EventConflation errorConflation);



    IBarcodeScannerBuilder beginsWith(String prefix);
    IBarcodeScannerBuilder track(
            double relativeTrackingMargin,
            int nRetries
    );

    // ICallbackBuilder
    //IBarcodeScannerBuilder setListener(BarcodeScanner.BarcodeDetectedListener listener);
    //IBarcodeScannerBuilder setVerbose();
//    IBarcodeScannerBuilder includeImagesInCallback();

    IBarcodeScanner build(Activity activity);
    //BarcodeScanner start(
    //        Activity activity,
    //        BarcodeDetectedListener listener,
    //        boolean includeImagesInCallback,
    //        TextureView preview
    //);


}
