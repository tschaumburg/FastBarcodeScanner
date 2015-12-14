package dk.schaumburgit.trackingbarcodescanner;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;

import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;

import java.nio.IntBuffer;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by Thomas on 12-12-2015.
 */
public class LuminanceSourceFactory
{
    static final Map<Integer, Double> SUPPORTED_FORMAT_COSTS;
    static {
        Map<Integer, Double> tmp = new LinkedHashMap<Integer, Double>();

        tmp.put(ImageFormat.NV21, 0.3);
        tmp.put(ImageFormat.NV16, 0.3);
        tmp.put(ImageFormat.YV12, 0.3);
        tmp.put(ImageFormat.YUY2, 0.3);
        tmp.put(ImageFormat.YUV_420_888, 0.3);
        tmp.put(ImageFormat.YUV_422_888, 0.3);
        tmp.put(ImageFormat.YUV_444_888, 0.3);
        tmp.put(ImageFormat.FLEX_RGB_888, 0.3);
        tmp.put(ImageFormat.FLEX_RGBA_8888, 0.3);
        tmp.put(ImageFormat.JPEG, 1.0);
        tmp.put(ImageFormat.RGB_565, 0.3);

        SUPPORTED_FORMAT_COSTS = Collections.unmodifiableMap(tmp);
    }
    public static boolean isFormatSupported(int imageFormat)
    {
        return SUPPORTED_FORMAT_COSTS.containsKey(imageFormat);
    }

    public static LuminanceSource getLuminanceSource(int imageFormat, byte[] data, int width, int height)
    {
        switch (imageFormat) {
            case ImageFormat.UNKNOWN:
                return null;
            case ImageFormat.NV21:
                // Source: http://www.programcreek.com/java-api-examples/index.php?source_dir=Roid-Library-master/src/com/rincliu/library/common/persistence/zxing/camera/CameraManager.java
                // This is the standard Android format which all devices are REQUIRED
                // to support. In theory, it's the only one we should ever care about.
            case ImageFormat.NV16:
                // Source: http://www.programcreek.com/java-api-examples/index.php?source_dir=Roid-Library-master/src/com/rincliu/library/common/persistence/zxing/camera/CameraManager.java
                // This format has never been seen in the wild, but is compatible as we only care
                // about the Y channel, so allow it.
            case ImageFormat.YV12:
                // source: https://github.com/evopark/tiqr-android/blob/master/src/main/java/de/evopark/tiqr/android/processing/ZxingQrScanner.java
            case ImageFormat.YUY2:
                // source: https://github.com/evopark/tiqr-android/blob/master/src/main/java/de/evopark/tiqr/android/processing/ZxingQrScanner.java
            case ImageFormat.YUV_420_888:
                // 420_888 is verified in practice - the other two
            case ImageFormat.YUV_422_888:
                // only varies from yuv_420_888 in chroma-subsampling, which I'm guessing
                // doesn't affect the luminance much
                // (see https://en.wikipedia.org/wiki/Chroma_subsampling)
            case ImageFormat.YUV_444_888:
                // only varies from yuv_420_888 in chroma-subsampling, which I'm guessing
                // doesn't affect the luminance much
                // (see https://en.wikipedia.org/wiki/Chroma_subsampling)
                return new PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false);
            case ImageFormat.FLEX_RGB_888:
            case ImageFormat.FLEX_RGBA_8888:
                return new RGBLuminanceSource(width, height, uncompress(data, width, height));// PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false);
            case ImageFormat.JPEG:
                // Tried and tested myself
                return new RGBLuminanceSource(width, height, uncompress(data, width, height));// PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false);
            case ImageFormat.RGB_565:
                return new RGB565LuminanceSource(width, height, uncompress(data, width, height));// PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false);
            case ImageFormat.RAW_SENSOR:
            case ImageFormat.RAW10:
            case ImageFormat.RAW12:
            case ImageFormat.DEPTH16:
            case ImageFormat.DEPTH_POINT_CLOUD:
                //ImageFormat.Y8:
                //ImageFormat.Y16:
                return null;
            default:
                return null;
        }
    }
    private static int[] uncompress(byte[] data, int width, int height)
    {
        Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length);
        // This is then turned into an uncompressed array-of-ints:
        IntBuffer pixelBuffer = IntBuffer.allocate(width * height);
        bm.copyPixelsToBuffer(pixelBuffer);
        int[] pixelArray = new int[pixelBuffer.rewind().remaining()];
        pixelBuffer.get(pixelArray);
        return pixelArray;
    }
}
