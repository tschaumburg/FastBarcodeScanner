package dk.schaumburgit.fastbarcodescanner;

import android.annotation.TargetApi;
import android.app.Activity;
import android.view.SurfaceView;
import android.view.TextureView;

import dk.schaumburgit.fastbarcodescanner.callbackmanagers.CallBackOptions;
import dk.schaumburgit.stillsequencecamera.camera.StillSequenceCameraOptions;
import dk.schaumburgit.stillsequencecamera.camera2.StillSequenceCamera2Options;
import dk.schaumburgit.trackingbarcodescanner.ScanOptions;
import dk.schaumburgit.trackingbarcodescanner.TrackingOptions;

/**
 * Created by Thomas on 23-02-2018.
 */

public class BarcodeScannerFactory {
    public enum FeatureSupportLevels
    {
        /**
         * The Android platform (HW + OS) only supports the legacy (pre-Lollipop)
         * camera API
         */
        LegacyOnly,
        Full
    }
    public static FeatureSupportLevels SupportedFeatures()
    {
        return FeatureSupportLevels.Full;
    }

    @TargetApi(21)
    public static IBarcodeScanner Create(
            Activity activity,
            StillSequenceCamera2Options cameraOptions,
            ScanOptions scanOptions,
            TrackingOptions trackingOptions,
            CallBackOptions callBackOptions
    )
    {
        return new BarcodeScanner(activity, cameraOptions, scanOptions, trackingOptions, callBackOptions);
    }

    @TargetApi(21)
    public static IBarcodeScanner Create(
            Activity activity,
            TextureView textureView,
            int resolution
    )
    {
        return Create(
                activity,
                new StillSequenceCamera2Options(textureView, resolution, StillSequenceCamera2Options.Facing.Back),
                new ScanOptions(),
                new TrackingOptions(),
                new CallBackOptions()
        );
    }

    public static IBarcodeScannerBuilder Builder(TextureView preview)
    {
        return new BarcodeScannerBuilder(new StillSequenceCamera2Options(preview));
    }

    public static IBarcodeScannerBuilder BuilderLegacy(SurfaceView preview, int minPixels)
    {
        return new BarcodeScannerBuilder(new StillSequenceCameraOptions(preview, minPixels));
    }

    /**
     * Creates a BarcodeScanner using the deprecated Camera API supported
     * on Android versions prior to Lollipop (API level lower than 21).
     * <p>
     * The created BarcodeScanner will display preview output in the supplied
     * SurfaceView. This parameter *must* be non-null, and the referenced SurfaceView
     * *must* be displayed on-screen, with a minimum size of 1x1 pixels. This is a
     * non-negotiable requirement from the camera API (upgrade to API level 21 for
     * true headless operation).
     *
     * @param activity Non-null
     * @deprecated This constructor uses the deprecated Camera API. We recommend using
     * one of the other BarcodeScanner constructors (using the new
     * {@link android.hardware.camera2} API) for new applications.
     */
    @Deprecated
    public static IBarcodeScanner CreateLegacy(
            Activity activity,
            StillSequenceCameraOptions cameraOptions,
            ScanOptions scanOptions,
            TrackingOptions trackingOptions,
            CallBackOptions callBackOptions
    ) {
        return new BarcodeScanner(activity, cameraOptions, scanOptions, trackingOptions, callBackOptions);
    }

    /**
     * Creates a BarcodeScanner using the deprecated Camera API supported
     * on Android versions prior to Lollipop (API level lower than 21).
     * <p>
     * The created BarcodeScanner will display preview output in the supplied
     * SurfaceView. This parameter *must* be non-null, and the referenced SurfaceView
     * *must* be displayed on-screen, with a minimum size of 1x1 pixels. This is a
     * non-negotiable requirement from the camera API (upgrade to API level 21 for
     * true headless operation).
     *
     * @param activity    Non-null
     * @param surfaceView Non-null
     * @param resolution  The requested minimum resolution of the photos
     *                    taken during scanning.
     * @deprecated This constructor uses the deprecated Camera API. We recommend using
     * one of the other BarcodeScanner constructors (using the new
     * {@link android.hardware.camera2} API) for new applications.
     */
    @Deprecated
    public static IBarcodeScanner CreateLegacy(
            Activity activity,
            SurfaceView surfaceView,
            int resolution
    ) {
        return new BarcodeScanner(activity, surfaceView, resolution);
    }
}
