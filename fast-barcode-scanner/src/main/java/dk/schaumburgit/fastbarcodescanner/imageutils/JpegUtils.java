package dk.schaumburgit.fastbarcodescanner.imageutils;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;

import java.io.ByteArrayOutputStream;

/**
 * Created by Thomas on 16-12-2015.
 */
public class JpegUtils
{
    public static byte[] ToJpeg(byte[] imageData, int imageFormat, int width, int height)
    {
        if (imageData == null)
            return null;

        switch (imageFormat)
        {
            case ImageFormat.NV21:
            case ImageFormat.YUY2:
                YuvImage img = new YuvImage(imageData, imageFormat, width, height, null);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int quality = 20; //set quality
                img.compressToJpeg(new Rect(0, 0, width, height), quality, baos);//this line decreases the image quality
                return baos.toByteArray();
            case ImageFormat.YUV_420_888:
                return JpegFromYuv420888(imageData, imageFormat, width, height);


            case ImageFormat.UNKNOWN:
                return null;
            case ImageFormat.NV16:
                // Source: http://www.programcreek.com/java-api-examples/index.php?source_dir=Roid-Library-master/src/com/rincliu/library/common/persistence/zxing/camera/CameraManager.java
                // This format has never been seen in the wild, but is compatible as we only care
                // about the Y channel, so allow it.
            case ImageFormat.YV12:
                // source: https://github.com/evopark/tiqr-android/blob/master/src/main/java/de/evopark/tiqr/android/processing/ZxingQrScanner.java
            case ImageFormat.YUV_422_888:
                // only varies from yuv_420_888 in chroma-subsampling, which I'm guessing
                // doesn't affect the luminance much
                // (see https://en.wikipedia.org/wiki/Chroma_subsampling)
            case ImageFormat.YUV_444_888:
                // only varies from yuv_420_888 in chroma-subsampling, which I'm guessing
                // doesn't affect the luminance much
                // (see https://en.wikipedia.org/wiki/Chroma_subsampling)
                return null;//new PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false);
            case ImageFormat.FLEX_RGB_888:
            case ImageFormat.FLEX_RGBA_8888:
                return null;//new RGBLuminanceSource(width, height, uncompress(data, width, height));// PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false);
            case ImageFormat.JPEG:
                // Tried and tested myself
                return null;//new RGBLuminanceSource(width, height, uncompress(data, width, height));// PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false);
            case ImageFormat.RGB_565:
                return null;//new RGB565(width, height, uncompress(data, width, height));// PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false);
            case ImageFormat.RAW_SENSOR:
            case ImageFormat.RAW10:
            case ImageFormat.RAW12:
            case ImageFormat.DEPTH16:
            case ImageFormat.DEPTH_POINT_CLOUD:
                //ImageFormat.Y8:
                //ImageFormat.Y16:
                return null;
            default:
                throw new IllegalArgumentException("No support for image format " + imageFormat);
        }
    }

    private static byte[] JpegFromYuv420888(byte[] imageData, int imageFormat, int width, int height)
    {
        return null;
    }
}
