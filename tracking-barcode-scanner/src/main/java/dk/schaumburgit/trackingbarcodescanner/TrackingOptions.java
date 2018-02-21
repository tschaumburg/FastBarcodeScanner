package dk.schaumburgit.trackingbarcodescanner;

/**
 * A set of options controlling the tracking behavior of the fast-barcode-scanner
 * library,
 *
 * Tracking is essentially an optimistic assumption that you'll find a barcode
 * "near" the place it was found in the last scan.
 *
 * If this narrowed search fails a number of times, the scanner will revert to
 * searching the full image.
 */
public class TrackingOptions
{
    public double trackingMargin = 1.0;
    public int trackingPatience = 5;
}
