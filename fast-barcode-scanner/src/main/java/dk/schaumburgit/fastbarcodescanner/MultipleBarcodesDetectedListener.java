package dk.schaumburgit.fastbarcodescanner;

/**
 * Created by Thomas on 18-02-2018.
 */

public interface MultipleBarcodesDetectedListener extends ErrorDetectedListener {
    void onMultipleBarcodeAvailable(BarcodeInfo[] barcodes, byte[] image, int format, int width, int height);
}