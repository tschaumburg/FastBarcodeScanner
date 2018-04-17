package dk.schaumburgit.fastbarcodescanner;

import android.graphics.Point;
import android.media.Image;
import android.os.Handler;

/**
 * Created by Thomas on 23-02-2018.
 */

public interface IBarcodeScanner {
    /**
     * Starts processing anything the back-camera sees, looking for any single barcode
     * in the captured photos. If several barcodes are in view, only the first one found
     * will be detected (consider using the less efficient #startMultiScan if you want
     * all barcodes)
     * <p>
     * The scanning is performed on a background thread. The supplied listener will
     * be called using the supplied handler whenever
     * there's a *change* in the barcode seen (i.e. if 200 consecutive images contain
     * the same barcode, only the first will generate a callback).
     * <p>
     * "No barcode" is signalled with a null value via the callback.
     * <p>
     * Example: After StartScan is called, the first 20 images contain no barcode, the
     * next 200 have barcode A, the next 20 have nothing. This will generate the
     * following callbacks:
     * <p>
     * Frame#1:   onBarcodeAvailable(null)
     * Frame#21:  onBarcodeAvailable("A")
     * Frame#221: onBarcodeAvailable(null)
     *
     * @param listener                A reference to the listener receiving the above mentioned
     *                                callbacks
     * @param callbackHandler         Identifies the thread that the callbacks will be made on.
     *                                Null means "use the thread that called StartScan()".
     */
    void StartScan(
            BarcodeDetectedListener listener,
            Handler callbackHandler
    );

    /**
     * Shorthand for StartScan(listener, null)
     *
     * @param listener                A reference to the listener receiving the
     *                                callbacks
     */
    void StartScan(
            BarcodeDetectedListener listener
    );

    /**
     * Similar to #StartScan, except that it scans all barcodes in view, not just the first
     * one found.
     *
     * @param listener        A reference to the listener receiving the above mentioned callbacks
     * @param callbackHandler Identifies the thread that the callbacks will be made on.
     *                        Null means "use the thread that called StartScan()".
     */
    void StartMultiScan(MultipleBarcodesDetectedListener listener, Handler callbackHandler);

    /**
     * Similar to #StartScan, except that it scans all barcodes in view, not just the first
     * one found.
     *
     * @param includeImagesInCallback Whether the callbacks to the listener will contain the
     *                                images that the callback was based on.
     * @param minNoOfBarcodes
     * @param listener                A reference to the listener receiving the above mentioned callbacks
     * @param callbackHandler         Identifies the thread that the callbacks will be made on.
     *                                Null means "use the thread that called StartScan()".
     */
    void StartMultiScan(
            boolean includeImagesInCallback,
            int minNoOfBarcodes,
            MultipleBarcodesDetectedListener listener,
            Handler callbackHandler
    );

    void Pause();

    void Resume();

    /**
     * Stops the scanning process started by StartScan() or StartMultiScan() and frees any shared system resources
     * (e.g. the camera). StartScan() or StartMultiScan() can always be called to restart.
     * <p>
     * StopScan() and StartScan()/StartMultiScan() are thus well suited for use from the onPause() and onResume()
     * handlers of a calling application.
     */
    void StopScan();

    /**
     * Disposes irrevocably of all resources. This instance cannot be used after close() is called.
     */
    void Close();

    boolean isLockFocus();

    void setLockFocus(boolean lockFocus);

    /**
     * Callback interface for being notified that a barcode has been detected.
     * <p>
     * <p>
     * The onBarcodeAvailable is called when a new barcode is detected - that is,
     * if the same barcode is detected in 20 consecutive frames, onBarcodeAvailable
     * is only called on the first.
     * </p>
     * <p>
     * When no barcodes have been detected in 3 consecutive frames, onBarcodeAvailable
     * is called with a null barcode parameter ().
     * </p>
     */
    public interface BarcodeDetectedListener //extends ErrorDetectedListener
    {
        void OnHit(BarcodeInfo barcode, String sourceUrl);
        void OnBlank();
        void OnError(Exception error);
    }

    public interface MultipleBarcodesDetectedListener
    {
        void OnHits(BarcodeInfo[] barcodes, String sourceUrl);
        void OnBlank();
        void OnError(Exception error);
    }

    public static class BarcodeInfo {
        public final String barcode;
        //public final int format;
        public final Point[] points;

        public BarcodeInfo(String barcode, Point[] points) {
            this.barcode = barcode;
            //this.format = format;
            this.points = points;
        }
    }
}
