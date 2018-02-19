package dk.schaumburgit.fastbarcodescanner.fluent;

import android.app.Activity;
import android.view.TextureView;

import dk.schaumburgit.fastbarcodescanner.BarcodeDetectedListener;
import dk.schaumburgit.fastbarcodescanner.FastBarcodeScanner;
import dk.schaumburgit.fastbarcodescanner.FilterOptions;

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
    IScannerBuilder emptyVerbosity(FilterOptions.BlankVerbosity verbosity);

    IScannerBuilder errorDeglitch(int nSamples);
    IScannerBuilder errorVerbosity(FilterOptions.ErrorVerbosity verbosity);

    IScannerBuilder resultVerbosity(FilterOptions.ResultVerbosity verbosity);

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
