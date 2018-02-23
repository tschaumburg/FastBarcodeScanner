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
    public final double trackingMargin;
    public final int trackingPatience;
    public TrackingOptions(double margin, int patience)
    {
        this.trackingMargin = margin;
        this.trackingPatience = patience;
    }
    public TrackingOptions()
    {
        this.trackingMargin = 1.0;
        this.trackingPatience = 5;
    }
    public TrackingOptions clone(double margin, int patience)
    {
        if (margin < 0)
            margin = this.trackingMargin;

        if (patience < 0)
            patience = this.trackingPatience;

        return new TrackingOptions(margin, patience);
    }
}
