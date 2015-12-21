package dk.schaumburgit.trackingbarcodescanner;

import android.graphics.Point;

import com.google.zxing.BarcodeFormat;

/**
 * Created by Thomas on 15-12-2015.
 */
public class Barcode
{
    public final String contents;
    public final BarcodeFormat format;
    public final Point[] points;

    public Barcode(String contents, BarcodeFormat format, Point[] points) {
        this.contents = contents;
        this.format = format;
        this.points = points;
    }
}

