package dk.schaumburgit.fastbarcodescanner;

import android.app.Activity;
import android.view.SurfaceView;
import android.view.TextureView;

import dk.schaumburgit.fastbarcodescanner.IBarcodeScanner.BarcodeDetectedListener;
import dk.schaumburgit.fastbarcodescanner.callbackmanagers.CallBackOptions;
import dk.schaumburgit.stillsequencecamera.camera.StillSequenceCameraOptions;
import dk.schaumburgit.stillsequencecamera.camera2.StillSequenceCamera2Options;
import dk.schaumburgit.trackingbarcodescanner.ScanOptions;
import dk.schaumburgit.trackingbarcodescanner.TrackingOptions;

class BarcodeScannerBuilder implements IBarcodeScannerBuilder {
    private final StillSequenceCameraOptions cameraOptions;
    private final StillSequenceCamera2Options camera2Options;
    private final ScanOptions scanOptions;
    private final TrackingOptions trackingOptions;
    private final CallBackOptions callbackOptions;

    //******************************************************************
    // Constructors:
    //******************************************************************
    BarcodeScannerBuilder(StillSequenceCameraOptions camOpts) {
        this.cameraOptions = camOpts;
        this.camera2Options = null;
        this.trackingOptions = new TrackingOptions();
        this.scanOptions = new ScanOptions();
        this.callbackOptions = new CallBackOptions();
    }

    BarcodeScannerBuilder(StillSequenceCamera2Options cam2Opts) {
        this.cameraOptions = null;
        this.camera2Options = cam2Opts;
        this.trackingOptions = new TrackingOptions();
        this.scanOptions = new ScanOptions();
        this.callbackOptions = new CallBackOptions();
    }

    //******************************************************************
    // Cloning:
    //******************************************************************
    private BarcodeScannerBuilder(StillSequenceCameraOptions cameraOptions, StillSequenceCamera2Options camera2Options, TrackingOptions trackingOptions, ScanOptions scanOptions, CallBackOptions callbackOptions)
    {
        this.cameraOptions = cameraOptions;
        this.camera2Options = camera2Options;
        this.trackingOptions = trackingOptions;
        this.scanOptions = scanOptions;
        this.callbackOptions = callbackOptions;
    }

    private BarcodeScannerBuilder clone(final StillSequenceCameraOptions cameraOptions)
    {
        return new BarcodeScannerBuilder(cameraOptions, this.camera2Options, this.trackingOptions, this.scanOptions, this.callbackOptions);
    }

    private BarcodeScannerBuilder clone(final StillSequenceCamera2Options camera2Options)
    {
        return new BarcodeScannerBuilder(this.cameraOptions, camera2Options, this.trackingOptions, this.scanOptions, this.callbackOptions);
    }

    private BarcodeScannerBuilder clone(final TrackingOptions trackingOptions)
    {
        return new BarcodeScannerBuilder(this.cameraOptions, this.camera2Options, trackingOptions, this.scanOptions, this.callbackOptions);
    }

    private BarcodeScannerBuilder clone(final ScanOptions scanOptions)
    {
        return new BarcodeScannerBuilder(this.cameraOptions, this.camera2Options, this.trackingOptions, scanOptions, this.callbackOptions);
    }

    private BarcodeScannerBuilder clone(final CallBackOptions callbackOptions)
    {
        return new BarcodeScannerBuilder(this.cameraOptions, this.camera2Options, this.trackingOptions, this.scanOptions, callbackOptions);
    }

    //******************************************************************
    // Image resolution:
    //******************************************************************
    @Override
    public IBarcodeScannerBuilder resolution(int minPixels)
    {
        if (this.cameraOptions != null)
            return clone(this.cameraOptions.clone(minPixels));

        if (this.camera2Options != null)
            return clone(this.camera2Options.clone(minPixels));

        return clone(new StillSequenceCamera2Options(null));
    }

    //******************************************************************
    // Empty handling:
    //******************************************************************
    @Override
    public IBarcodeScannerBuilder emptyDeglitch(int nSamples) {
        return clone(this.callbackOptions.clone(nSamples, -1));
    }

    @Override
    public IBarcodeScannerBuilder emptyVerbosity(CallBackOptions.BlankVerbosity verbosity) {
        return clone(this.callbackOptions.clone(verbosity));
    }

    @Override
    public IBarcodeScannerBuilder emptyMarker(String emptyMarkerContents) {
        return clone(new ScanOptions(emptyMarkerContents, this.scanOptions.beginsWith));
    }

    //******************************************************************
    // Error handling:
    //******************************************************************
    @Override
    public IBarcodeScannerBuilder errorDeglitch(int nSamples) {
        return clone(this.callbackOptions.clone(-1, nSamples));
    }

    @Override
    public IBarcodeScannerBuilder errorVerbosity(CallBackOptions.ErrorVerbosity verbosity) {
        return clone(this.callbackOptions.clone(verbosity));
    }

    //******************************************************************
    // Result handling:
    //******************************************************************
    @Override
    public IBarcodeScannerBuilder beginsWith(String prefix) {
        return clone(new ScanOptions(this.scanOptions.emptyMarker, prefix));
    }

    @Override
    public IBarcodeScannerBuilder resultVerbosity(CallBackOptions.ResultVerbosity verbosity) {
        return clone(this.callbackOptions.clone(verbosity));
    }

    @Override
    public IBarcodeScannerBuilder track(double relativeTrackingMargin, int nRetries) {
        return clone(this.trackingOptions.clone(relativeTrackingMargin, nRetries));
    }

    @Override
    public IBarcodeScanner build(
            Activity activity
    ) {
        if (this.camera2Options != null) {
            return BarcodeScannerFactory.Create(activity, this.camera2Options, this.scanOptions, this.trackingOptions, this.callbackOptions);
        }

        if (this.cameraOptions != null) {
            return BarcodeScannerFactory.CreateLegacy(activity, this.cameraOptions, this.scanOptions, this.trackingOptions, this.callbackOptions);
        }

        return null;
    }


    //@Override
    private IBarcodeScanner start(
            Activity activity,
            BarcodeDetectedListener listener,
            boolean includeImagesInCallback
    )
    {
        IBarcodeScanner res = this.build(activity);
        res.StartScan(includeImagesInCallback, listener, null);
        return res;
    }
}
