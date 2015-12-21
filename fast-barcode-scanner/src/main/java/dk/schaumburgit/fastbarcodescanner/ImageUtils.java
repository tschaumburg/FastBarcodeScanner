package dk.schaumburgit.fastbarcodescanner;

import android.graphics.ImageFormat;
import android.media.Image;

import java.nio.ByteBuffer;

/**
 * Created by Thomas on 17-12-2015.
 */
public class ImageUtils {

    public static byte[] getPlane(Image image, int planeNo)
    {
        ByteBuffer buffer;
        int rowStride;
        int pixelStride;
        int pixelWidth = image.getWidth();
        int pixelHeight = image.getHeight();
        int encodedRowStart = 0;

        Image.Plane[] planes = image.getPlanes();
        int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
        byte[] pixels = new byte[image.getWidth() * image.getHeight() * bytesPerPixel];
        byte[] rowData = new byte[planes[0].getRowStride()];

        for (int i = 0; i < planes.length; i++) {
            buffer = planes[i].getBuffer();
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            int encodedWidthInPixels = (i == 0) ? pixelWidth : pixelWidth / 2;
            int encodedHeightInPixels = (i == 0) ? pixelHeight : pixelHeight / 2;
            for (int row = 0; row < encodedHeightInPixels; row++) {
                if (pixelStride == bytesPerPixel) {
                    int encodedWidthInBytes = encodedWidthInPixels * bytesPerPixel;
                    buffer.get(pixels, encodedRowStart, encodedWidthInBytes);

                    // Advance buffer the remainder of the row stride, unless on the last row.
                    // Otherwise, this will throw an IllegalArgumentException because the buffer
                    // doesn't include the last padding.
                    if (encodedHeightInPixels - row != 1) {
                        int padding = rowStride - encodedWidthInBytes;
                        buffer.position(buffer.position() + padding);
                    }
                    encodedRowStart += encodedWidthInBytes;
                } else {

                    // On the last row only read the width of the image minus the pixel stride
                    // plus one. Otherwise, this will throw a BufferUnderflowException because the
                    // buffer doesn't include the last padding.
                    if (encodedHeightInPixels - row == 1) {
                        buffer.get(rowData, 0, pixelWidth - pixelStride + 1);
                    } else {
                        buffer.get(rowData, 0, rowStride);
                    }

                    for (int col = 0; col < encodedWidthInPixels; col++) {
                        pixels[encodedRowStart + col] = rowData[col * pixelStride];
                    }
                    //encodedRowStart += encodedWidthInBytes;
                }
            }
        }

        // Finally, create the Mat.
        //Mat mat = new Mat(pixelHeight + pixelHeight / 2, pixelWidth, CvType.CV_8UC1);
        //mat.put(0, 0, pixels);

        return pixels;
    }

    /**
     * Takes an Android Image in the YUV_420_888 format and returns an OpenCV Mat.
     *
     * @param image Image in the YUV_420_888 format.
     * @return OpenCV Mat.
     */
    public static byte[] imageToMat(Image image) {
        ByteBuffer buffer;
        int rowStride;
        int pixelStride;
        int pixelWidth = image.getWidth();
        int pixelHeight = image.getHeight();
        int encodedRowStart = 0;

        Image.Plane[] planes = image.getPlanes();
        int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
        byte[] pixels = new byte[image.getWidth() * image.getHeight() * bytesPerPixel];
        byte[] rowData = new byte[planes[0].getRowStride()];

        for (int i = 0; i < planes.length; i++) {
            buffer = planes[i].getBuffer();
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            int encodedWidthInPixels = (i == 0) ? pixelWidth : pixelWidth / 2;
            int encodedHeightInPixels = (i == 0) ? pixelHeight : pixelHeight / 2;
            for (int row = 0; row < encodedHeightInPixels; row++) {
                if (pixelStride == bytesPerPixel) {
                    int encodedWidthInBytes = encodedWidthInPixels * bytesPerPixel;
                    buffer.get(pixels, encodedRowStart, encodedWidthInBytes);

                    // Advance buffer the remainder of the row stride, unless on the last row.
                    // Otherwise, this will throw an IllegalArgumentException because the buffer
                    // doesn't include the last padding.
                    if (encodedHeightInPixels - row != 1) {
                        int padding = rowStride - encodedWidthInBytes;
                        buffer.position(buffer.position() + padding);
                    }
                    encodedRowStart += encodedWidthInBytes;
                } else {

                    // On the last row only read the width of the image minus the pixel stride
                    // plus one. Otherwise, this will throw a BufferUnderflowException because the
                    // buffer doesn't include the last padding.
                    if (encodedHeightInPixels - row == 1) {
                        buffer.get(rowData, 0, pixelWidth - pixelStride + 1);
                    } else {
                        buffer.get(rowData, 0, rowStride);
                    }

                    for (int col = 0; col < encodedWidthInPixels; col++) {
                        pixels[encodedRowStart++] = rowData[col * pixelStride];
                    }
                }
            }
        }

        // Finally, create the Mat.
        //Mat mat = new Mat(pixelHeight + pixelHeight / 2, pixelWidth, CvType.CV_8UC1);
        //mat.put(0, 0, pixels);

        return pixels;
    }
}