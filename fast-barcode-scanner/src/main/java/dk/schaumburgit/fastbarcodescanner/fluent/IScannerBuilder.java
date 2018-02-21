package dk.schaumburgit.fastbarcodescanner.fluent;

import android.app.Activity;
import dk.schaumburgit.fastbarcodescanner.CallBackOptions;
import dk.schaumburgit.fastbarcodescanner.FastBarcodeScanner;

/**
 * Created by Thomas on 08-02-2018.
 */

public interface IScannerBuilder
{
    //ICaptureBuilder
    IScannerBuilder resolution(int minPixels);

    // void filterImages(...rgb...);
    //IScannerBuilder scanQR(int subtype);
    //IScannerBuilder findBarcode(int barcodeType);
    IScannerBuilder findQR();

    IScannerBuilder emptyMarker(String emptyMarkerContents);
    IScannerBuilder emptyDeglitch(int nSamples);
    IScannerBuilder emptyVerbosity(CallBackOptions.BlankVerbosity verbosity);

    IScannerBuilder errorDeglitch(int nSamples);
    IScannerBuilder errorVerbosity(CallBackOptions.ErrorVerbosity verbosity);

    IScannerBuilder resultVerbosity(CallBackOptions.ResultVerbosity verbosity);

    IScannerBuilder beginsWith(String prefix);
    IScannerBuilder track(
            double relativeTrackingMargin,
            int nRetries
    );

    // ICallbackBuilder
    //IScannerBuilder setListener(FastBarcodeScanner.BarcodeDetectedListener listener);
    //IScannerBuilder setVerbose();
//    IScannerBuilder includeImagesInCallback();

    FastBarcodeScanner build(Activity activity);
    //FastBarcodeScanner start(
    //        Activity activity,
    //        BarcodeDetectedListener listener,
    //        boolean includeImagesInCallback,
    //        TextureView preview
    //);


}
