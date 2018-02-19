package dk.schaumburgit.fastbarcodescanner;

import android.graphics.Point;

/**
 * Created by Thomas on 18-02-2018.
 */

public class BarcodeInfo {
    public final String barcode;
    public final Point[] points;

    public BarcodeInfo(String barcode, Point[] points) {
        this.barcode = barcode;
        this.points = points;
    }
}
