package dk.schaumburgit.fastbarcodescanner.fluent;

import android.app.Activity;
import android.view.SurfaceView;
import android.view.TextureView;

import dk.schaumburgit.fastbarcodescanner.BarcodeDetectedListener;
import dk.schaumburgit.fastbarcodescanner.CallBackOptions;
import dk.schaumburgit.fastbarcodescanner.FastBarcodeScanner;
import dk.schaumburgit.fastbarcodescanner.FilterOptions;
import dk.schaumburgit.fastbarcodescanner.fluent.IScannerBuilder;
import dk.schaumburgit.stillsequencecamera.camera.StillSequenceCameraOptions;
import dk.schaumburgit.stillsequencecamera.camera2.StillSequenceCamera2Options;
import dk.schaumburgit.trackingbarcodescanner.ScanOptions;
import dk.schaumburgit.trackingbarcodescanner.TrackingOptions;

public class ScannerBuilder implements IScannerBuilder {
    private final FilterOptions filterOptions = new FilterOptions();
    //private final CallBackOptions callbackOptions = new CallBackOptions();

    //private final Activity activity;
    private final StillSequenceCameraOptions cameraOptions;
    private final StillSequenceCamera2Options camera2Options;

    private final TrackingOptions trackingOptions = new TrackingOptions();
    private final ScanOptions scanOptions = new ScanOptions();

    //******************************************************************
    // Cameras:
    //******************************************************************
    public static ScannerBuilder fromCamera(SurfaceView preview) {
        return new ScannerBuilder(new StillSequenceCameraOptions(preview));
    }

    private ScannerBuilder(StillSequenceCameraOptions camOpts) {
        this.cameraOptions = camOpts;
        this.camera2Options = null;
    }

    public static ScannerBuilder fromCamera(TextureView preview) {
        return new ScannerBuilder(new StillSequenceCamera2Options(preview));
    }

    private ScannerBuilder(StillSequenceCamera2Options cam2Opts) {
        this.cameraOptions = null;
        this.camera2Options = cam2Opts;
    }

    @Override
    public IScannerBuilder resolution(int minPixels) {
        ScannerBuilder res = this; // new FastBarcodeScannerBuilder(this);

        if (res.cameraOptions != null)
            res.cameraOptions.minPixels = minPixels;

        if (res.camera2Options != null)
            res.camera2Options.minPixels = minPixels;

        return res;
    }

    @Override
    public IScannerBuilder findQR() {
        IScannerBuilder res = this; // new FastBarcodeScannerBuilder(this);
        return res;
    }

    @Override
    public IScannerBuilder emptyMarker(String emptyMarkerContents) {
        IScannerBuilder res = this;// new FastBarcodeScannerBuilder(this);
        this.scanOptions.emptyMarkerContents = emptyMarkerContents;
        return res;
    }

    @Override
    public IScannerBuilder beginsWith(String prefix) {
        IScannerBuilder res = this;// new FastBarcodeScannerBuilder(this);
        this.scanOptions.beginsWith = prefix;
        return res;
    }

    @Override
    public IScannerBuilder errorDeglitch(int nSamples) {
        IScannerBuilder res = this;// new FastBarcodeScannerBuilder(this);
        this.filterOptions.errorDeglitchLevel = nSamples;
        return res;
    }

    @Override
    public IScannerBuilder emptyDeglitch(int nSamples) {
        IScannerBuilder res = this;// new FastBarcodeScannerBuilder(this);
        this.filterOptions.emptyDeglitchLevel = nSamples;
        return res;
    }

    @Override
    public IScannerBuilder resultVerbosity(FilterOptions.ResultVerbosity verbosity) {
        IScannerBuilder res = this;// new FastBarcodeScannerBuilder(this);
        this.filterOptions.resultVerbosity = verbosity;
        return res;
    }

    @Override
    public IScannerBuilder emptyVerbosity(FilterOptions.BlankVerbosity verbosity) {
        IScannerBuilder res = this;// new FastBarcodeScannerBuilder(this);
        this.filterOptions.blankVerbosity = verbosity;
        return res;
    }

    @Override
    public IScannerBuilder errorVerbosity(FilterOptions.ErrorVerbosity verbosity) {
        IScannerBuilder res = this;// new FastBarcodeScannerBuilder(this);
        this.filterOptions.errorVerbosity = verbosity;
        return res;
    }

    @Override
    public IScannerBuilder track(double relativeTrackingMargin, int nRetries) {
        ScannerBuilder res = this;// new FastBarcodeScannerBuilder(this);
        res.trackingOptions.relativeTrackingMargin = relativeTrackingMargin;
        res.trackingOptions.noHitsBeforeTrackingLoss = nRetries;
        return res;
    }

    @Override
    public FastBarcodeScanner build(
            Activity activity
    ) {
        if (this.camera2Options != null) {
            return new FastBarcodeScanner(activity, this.camera2Options, this.scanOptions, this.trackingOptions, this.filterOptions);
        }

        if (this.cameraOptions != null) {
            return new FastBarcodeScanner(activity, this.cameraOptions, this.scanOptions, this.trackingOptions, this.filterOptions);
        }

        return null;
    }


    //@Override
    private FastBarcodeScanner start(
            Activity activity,
            BarcodeDetectedListener listener,
            boolean includeImagesInCallback
    )
    {
        FastBarcodeScanner res = this.build(activity);
        res.StartScan(includeImagesInCallback, listener, null);
        return res;
    }
}
