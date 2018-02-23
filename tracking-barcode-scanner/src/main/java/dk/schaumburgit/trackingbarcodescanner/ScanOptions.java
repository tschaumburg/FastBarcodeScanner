package dk.schaumburgit.trackingbarcodescanner;

/**
 * Created by Thomas on 06-02-2018.
 */

public class ScanOptions
{
    public final String emptyMarker;
    public final String beginsWith;
    public ScanOptions()
    {
        this.emptyMarker = null;
        this.beginsWith = null;
    }
    public ScanOptions(String emptyMarker, String beginsWith)
    {
        this.emptyMarker = emptyMarker;
        this.beginsWith = beginsWith;
    }
}
