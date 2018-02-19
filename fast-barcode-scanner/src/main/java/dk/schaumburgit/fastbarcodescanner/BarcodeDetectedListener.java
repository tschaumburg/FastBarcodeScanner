package dk.schaumburgit.fastbarcodescanner;

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
    void onSingleBarcodeAvailable(BarcodeInfo barcode, byte[] image, int format, int width, int height);
    void onError(Exception error);
}
