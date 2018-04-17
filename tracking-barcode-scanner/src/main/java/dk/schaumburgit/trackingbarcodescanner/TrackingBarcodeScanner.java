package dk.schaumburgit.trackingbarcodescanner;

/**
 * Created by Thomas on 21-11-2015.
 */

import android.util.Size;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;


/**
 * The TrackingBarcodeScanner class looks for barcodes in the images supplied by the called.
 *
 * What distinguishes it from other barcode scanners is the tracking algorithm used. The tracking
 * algorithm is optimized for use with sequential images (like the frames in a video recording).
 * Once it has found a barcode in one area, it will look in the same area first in the next frame
 * - only if that fails will it look in the entire image.
 *
 * This optimization yields speed-ups of 2-5 times (i.e. finding a barcode falls from 100ms to
 * 20ms) in optimum situations (meaning where the barcode moves relatively little between frames).
 *
 * In situations where the images are completely unrelated, the tracking may actually cause a
 * slowdown, due to the time wasted on the initial scan.
 *
 * The tracking algorithm may be switched on and off dynamically, so you can experiment with it's
 * applicability for you scenario (see UseTracking bellow).
 *
 * The tracking algorithm is affected by the following properties:
 *
 * UseTracking (boolean): switches the entire tracking on or off (default: true)
 *
 * RelativeTrackingMargin (double): Specifies the relative margin to around the previous hit when
 * running the initial tracking scan. Large values allow relatively large movements between frames,
 * at the cost of lowered performance. Smaller values are faster, but allow less movement before
 * tracking is lost (default 1.0)
 *
 * NoHitsBeforeTrackingLoss (int): Due to bad frames (e.g. motion blur), no-hit scans may
 * occasionally occur. This parameter specifies how many consecutive bad frames will cause
 * a tracking loss (default 5).
 *
 * PreferredImageFormats (readonly, int[]): Specifies the image formats supported by
 * TrackingBarcodeScanner - using values from the ImageFormats enum - in order of preference
 * (default {YUV_420_888, JPEG})
 */
public class TrackingBarcodeScanner {
    private static final String TAG = "BarcodeFinder";
    private final Scanner mScanner;
    private final Tracker mTracker;
    public TrackingBarcodeScanner(ScanOptions scanOptions, TrackingOptions trackingOptions)
    {
        this.mTracker = new Tracker(trackingOptions, scanOptions);
        this.mScanner = new Scanner(scanOptions, trackingOptions);
    }

    public Barcode findSingle(BinaryBitmap bitmap) {
        return mTracker.findSingle(
                bitmap,
                new Tracker.MyUnaryFunction<BinaryBitmap, Result>() {
                    @Override
                    public Result apply(BinaryBitmap binaryBitmap) throws NotFoundException {
                        return mScanner.doFind(binaryBitmap);
                    }
                }
        );
    }

    public Barcode[] findMultiple(BinaryBitmap bitmap)
    {
        return mTracker.findMultiple(
                bitmap,
                new Tracker.MyUnaryFunction<BinaryBitmap, Result[]>() {
                    @Override
                    public Result[] apply(BinaryBitmap binaryBitmap) throws NotFoundException {
                        return mScanner.doFindMultiple(binaryBitmap);
                    }
                }
        );
    }

    /**
     * Calculate the number of nanoseconds needed by this processor to scan
     * a single frame at the given format and size.
     * @param imageSize
     * @return
     */
    public double nanosPerFrameScanned(Size imageSize, double relativeDevicePerformance) {
        // The number of nanoseconds that this device will use to
        // scan the luminized image for barcodes

        double nanosPerMegapixelScanned = 10000000; // 100 fps
        double deviceSpeed = 1.0;
        double megaPixels = imageSize.getHeight() * imageSize.getWidth() / (1024*1024);

        return nanosPerMegapixelScanned * megaPixels / relativeDevicePerformance;
    }
    private double relativeProcessorSpeed()
    {
        return 1.0;
    }
}

