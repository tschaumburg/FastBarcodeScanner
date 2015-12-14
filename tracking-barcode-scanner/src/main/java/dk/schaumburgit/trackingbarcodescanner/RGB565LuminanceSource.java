package dk.schaumburgit.trackingbarcodescanner;

import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;

/**
 * Created by Thomas on 12-12-2015.
 */

/**
 * This class is used to help decode images from files which arrive as RGB data from
 * an ARGB pixel array. It does not support rotation.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Betaminos
 */
public final class RGB565LuminanceSource extends LuminanceSource {

    private final byte[] luminances;
    private final int dataWidth;
    private final int dataHeight;
    private final int left;
    private final int top;

    public RGB565LuminanceSource(int width, int height, int[] pixels) {
        super(width, height);

        dataWidth = width;
        dataHeight = height;
        left = 0;
        top = 0;

        // In order to measure pure decoding speed, we convert the entire image to a greyscale array
        // up front, which is the same as the Y channel of the YUVLuminanceSource in the real app.
        luminances = new byte[width * height];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                int pixel = pixels[offset + x];
                int r = (pixel >> 11) & 0x1f; // 5 bits
                int g = (pixel >> 5) & 0x3f; // 6 bits
                int b = pixel & 0x1f; // 5 bits
                if (r == g && g == b) {
                    // Image is already greyscale, so pick any channel.
                    luminances[offset + x] = (byte) r;
                } else {
                    // Comparison to original RGBLuminanceSource:
                    // ==========================================
                    //
                    // "favoring green": since green has 6 bits available, we'll *assume* that
                    // a luminosity for green is already has double the numeric value of the
                    // same luminosity in the 5-bit red or blue channels.
                    // Or in other words, green has a range of 0...63, where the others have
                    // 0...31 - so green is already favored.

                    // "<<1" below: used to be "/4" when adding 4 8-bit values (4*2^8 => 2^10).
                    // Now we're adding two 5-bits and one 6-bit (2^5 + 2^6 + 2^5 => 2^7)
                    //

                    // Calculate luminance cheaply, favoring green.
                    luminances[offset + x] = (byte) ((r + g + b) << 1);
                }
            }
        }
    }

    private RGB565LuminanceSource(byte[] luminances,
                               int dataWidth,
                               int dataHeight,
                               int left,
                               int top,
                               int width,
                               int height) {
        super(width, height);
        if (left + width > dataWidth || top + height > dataHeight) {
            throw new IllegalArgumentException("Crop rectangle does not fit within image data.");
        }
        this.luminances = luminances;
        this.dataWidth = dataWidth;
        this.dataHeight = dataHeight;
        this.left = left;
        this.top = top;
    }

    @Override
    public byte[] getRow(int y, byte[] row) {
        if (y < 0 || y >= getHeight()) {
            throw new IllegalArgumentException("Requested row is outside the image: " + y);
        }
        int width = getWidth();
        if (row == null || row.length < width) {
            row = new byte[width];
        }
        int offset = (y + top) * dataWidth + left;
        System.arraycopy(luminances, offset, row, 0, width);
        return row;
    }

    @Override
    public byte[] getMatrix() {
        int width = getWidth();
        int height = getHeight();

        // If the caller asks for the entire underlying image, save the copy and give them the
        // original data. The docs specifically warn that result.length must be ignored.
        if (width == dataWidth && height == dataHeight) {
            return luminances;
        }

        int area = width * height;
        byte[] matrix = new byte[area];
        int inputOffset = top * dataWidth + left;

        // If the width matches the full width of the underlying data, perform a single copy.
        if (width == dataWidth) {
            System.arraycopy(luminances, inputOffset, matrix, 0, area);
            return matrix;
        }

        // Otherwise copy one cropped row at a time.
        byte[] rgb = luminances;
        for (int y = 0; y < height; y++) {
            int outputOffset = y * width;
            System.arraycopy(rgb, inputOffset, matrix, outputOffset, width);
            inputOffset += dataWidth;
        }
        return matrix;
    }

    @Override
    public boolean isCropSupported() {
        return true;
    }

    @Override
    public LuminanceSource crop(int left, int top, int width, int height) {
        return new RGB565LuminanceSource(luminances,
                dataWidth,
                dataHeight,
                this.left + left,
                this.top + top,
                width,
                height);
    }

}
