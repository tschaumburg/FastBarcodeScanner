package dk.schaumburgit.fastbarcodescanner;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.nfc.FormatException;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import dalvik.system.PathClassLoader;

/**
 * Created by Thomas on 18-12-2015.
 */
public class ImageDecoder
{
    //public static Bitmap ToBitmap(Image image)
    //{
    //    byte[] nv21Bytes = Serialize(image);
    //
    //    return ToBitmap(nv21Bytes, image.getFormat(), image.getWidth(), image.getHeight());
    //}

    public static Bitmap ToBitmap(byte[] imageBytes, int format, int width, int height)
    {
        switch (format) {
            case ImageFormat.NV21:
            case ImageFormat.YUV_420_888:
                return NV21ToBitmap(imageBytes, width, height);
        }

        return null;
    }

    private static Bitmap NV21ToBitmap(byte[] nv21Bytes, int width, int height)
    {
        YuvImage yuv = new YuvImage(nv21Bytes, ImageFormat.NV21, width, height, null);

        // pWidth and pHeight define the size of the preview Frame
        ByteArrayOutputStream jpegStream = new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0, 0, width, height), 50, jpegStream);
        byte[] jpegBytes = jpegStream.toByteArray();

        Bitmap bitmap= BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);

        return bitmap;
    }

    public static byte[] ToNV21(Image image)
    {
        if (image.getPlanes().length != 3)
            throw new RuntimeException("Expected 3 planes for planar YUV");

        byte[] nv21Bytes = new byte[getNV21Size(image)];
        getNV21(image, nv21Bytes);

        return nv21Bytes;
    }

    public static byte[] Serialize(Image image)
    {
        if (image==null)
            return null;

        Image.Plane[] planes = image.getPlanes();

        // NV21 expects planes in order YVU, not YUV:
        if (image.getFormat() == ImageFormat.YUV_420_888)
            planes = new Image.Plane[] {planes[0], planes[2], planes[1]};

        byte[] serializeBytes = new byte[getSerializedSize(image)];
        int nextFree = 0;

        for (Image.Plane plane: planes)
        {
            ByteBuffer buffer = plane.getBuffer();
            buffer.position(0);
            int nBytes = buffer.remaining();
            plane.getBuffer().get(serializeBytes, nextFree, nBytes);
            nextFree += nBytes;
        }

        return serializeBytes;
    }

    private static int getSerializedSize(Image image) {
        int size = 0;

        for (Image.Plane plane: image.getPlanes())
        {
            size += plane.getBuffer().capacity();
        }

        return size;
    }

    private static int getNV21Size(Image src)
    {
        //return (int)(src.getHeight() * src.getWidth() * 1.5);
        return (int)(src.getHeight() * src.getWidth() * 2);
    }

    private static void getNV21(Image src, byte[] dest)
    {
        // Check nPlanes etc.
        Image.Plane yPlane = src.getPlanes()[0];
        Image.Plane uPlane = src.getPlanes()[1];
        Image.Plane vPlane = src.getPlanes()[2];

        int ySize = yPlane.getBuffer().capacity();
        int uSize = uPlane.getBuffer().capacity();
        int vSize = vPlane.getBuffer().capacity();

        if (ySize != src.getWidth() * src.getHeight())
            throw new RuntimeException("Y-plane in planar YUV_420_888 is expected to be width*height bytes");

        if (ySize != 2 * (uSize + 1))
            throw new RuntimeException("U-plane in planar YUV_420_888 is expected to be (width*height/2 - 1) bytes");

        if (ySize != 2 * (vSize + 1))
            throw new RuntimeException("V-plane in planar YUV_420_888 is expected to be (width*height/2 - 1) bytes");

        //int nextFree = getNonInterleaved(yPlane.getBuffer(), dest, 0);
        //getInterleaved(vPlane.getBuffer(), 2, dest, nextFree, 2);
        //getInterleaved(uPlane.getBuffer(), 2, dest, nextFree + 1, 2);
        int nextFree = 0;
        nextFree += getNonInterleaved(yPlane.getBuffer(), dest, nextFree);
        nextFree += getNonInterleaved(vPlane.getBuffer(), dest, nextFree);
        nextFree += getNonInterleaved(uPlane.getBuffer(), dest, nextFree);
    }

    private static void getInterleaved(ByteBuffer srcBuffer, int srcStride, byte[] dest, int destStart, int destStride)
    {
        byte[] srcBytes;
        if (srcBuffer.hasArray())
            srcBytes = srcBuffer.array();
        else {
            srcBuffer.position(0);
            int len = srcBuffer.remaining();
            srcBytes = new byte[len];
            srcBuffer.get(srcBytes, 0, len);
        }

        int srcLength = srcBuffer.capacity();
        for (int srcIndex = 0, destIndex = destStart; srcIndex < srcLength; srcIndex += srcStride, destIndex += destStride)
        {
            dest[destIndex] = srcBytes[srcIndex];// srcBuffer.get(srcIndex);// srcBytes[n];
        }
    }

    private static int getNonInterleaved(ByteBuffer srcBuffer, byte[] dest, int destStart) {
        srcBuffer.position(0);
        int srcLength = srcBuffer.remaining();
        srcBuffer.get(dest, destStart, srcLength);
        return srcLength;
    }
}
