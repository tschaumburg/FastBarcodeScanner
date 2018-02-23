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
    IBarcodeScannerBuilder emptyDeglitch(int nSamples);
    IBarcodeScannerBuilder emptyVerbosity(CallBackOptions.BlankVerbosity verbosity);

    IBarcodeScannerBuilder errorDeglitch(int nSamples);
    IBarcodeScannerBuilder errorVerbosity(CallBackOptions.ErrorVerbosity verbosity);

    IBarcodeScannerBuilder resultVerbosity(CallBackOptions.ResultVerbosity verbosity);

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
