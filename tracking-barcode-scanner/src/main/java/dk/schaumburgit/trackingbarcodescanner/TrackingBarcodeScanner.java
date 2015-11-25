package dk.schaumburgit.trackingbarcodescanner;

/**
 * Created by Thomas on 21-11-2015.
 */

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.nio.IntBuffer;
import java.util.Date;
import java.util.EnumSet;
import java.util.Hashtable;

public class TrackingBarcodeScanner {
    private double mRelativeTrackingMargin = 1.0;
    private int mNoHitsBeforeTrackingLoss = 5;
    private EnumSet<BarcodeFormat> mPossibleFormats = EnumSet.of(BarcodeFormat.QR_CODE);
    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "BarcodeFinder";
    private static final int[] mPreferredFormats = {ImageFormat.YUV_420_888, ImageFormat.JPEG};
    public int[] GetPreferredFormats()
    {
        return mPreferredFormats;
    }

    private QRCodeReader mReader = new QRCodeReader();
    private Hashtable<DecodeHintType, Object> mDecodeHints;
    public TrackingBarcodeScanner()
    {
        mDecodeHints = new Hashtable<DecodeHintType, Object>();
        mDecodeHints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        mDecodeHints.put(DecodeHintType.POSSIBLE_FORMATS, mPossibleFormats);
    }

    public Date a;
    public Date b;
    public Date c;
    public Date d;
    public Date f;
    public Date g;
    public String find(int imageFormat, int w, int h, byte[] bytes)
    {
        a = new Date();
        // First we'll convert into a BinaryBitmap:
        BinaryBitmap bitmap = null;
        try {
            switch (imageFormat) {
                case ImageFormat.JPEG:
                    // from JPEG
                    // =========
                    // ZXing doesn't accept a JPEG-encoded byte array, so we let Java decode intoa Bitmap:
                    Bitmap bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    b = new Date();
                    int width = bm.getWidth();
                    int height = bm.getHeight();
                    int length = bytes.length;

                    // This is then turned into an uncompressed array-of-ints:
                    IntBuffer pixelBuffer = IntBuffer.allocate(width * height);
                    bm.copyPixelsToBuffer(pixelBuffer);
                    int[] pixelArray = new int[pixelBuffer.rewind().remaining()];
                    pixelBuffer.get(pixelArray);
                    c = new Date();

                    // ...and THAT (finally), ZXing is happy about:
                    LuminanceSource lumSource = new RGBLuminanceSource(width, height, pixelArray);// PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false);
                    d = new Date();
                    bitmap = new BinaryBitmap(new HybridBinarizer(lumSource));
                    f = new Date();
                    break;
                case ImageFormat.YUV_420_888:
                    LuminanceSource lumSourceYUV = new PlanarYUVLuminanceSource(bytes, w, h, 0, 0, w, h , false);
                    b = new Date();
                    bitmap = new BinaryBitmap(new HybridBinarizer(lumSourceYUV));
                    c = new Date();
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "failed reading captured image", e);
            return null;
        }

        try {
            Result r = null;

            // First try where we found the barcode before (much quicker that way):
            if (mLatestMatch != null) {
                Geometry.Rectangle crop = mLatestMatch.normalize(0, 0, bitmap.getWidth(), bitmap.getHeight());
                //Log.d(TAG, "CROP: looking in (" + crop.x + ", " + crop.y + ", " + crop.width + ", " + crop.height + ")");
                int left = crop.x;
                int top = crop.y;
                r = doFind(bitmap.crop(left, top, crop.width, crop.height));
            }
            d = new Date();

            // If that didn't work, look at the entire picture:
            if (r==null)
            {
                //Log.d(TAG, "CROP: Failed - looking in full bitmap");
                r = doFind(bitmap);
                f = new Date();

                // if that worked (i.e. the barcode is in a new place),
                // we'll update the mLatestMatch rectangle:
                if (r != null)
                    rememberMatch(r);
            } else
            {
                f = new Date();
                //Log.d(TAG, "CROP: Succeded - found barcode " + r.getText());
            }

            g = new Date();
            if (r == null)
            {
                if (mConsecutiveNoHits++ > mNoHitsBeforeTrackingLoss)
                    mLatestMatch = null;

                return null;
            }

            mConsecutiveNoHits = 0;
            return r.getText();
        } catch (Exception e) {
            Log.e(TAG, "FAILED DECODING", e);
            return null;
        }
    }

    private Result doFind(BinaryBitmap bitmap)
    {
        Result r = null;
        try {
            // First try where we found the barcode before:
            r = mReader.decode(bitmap, mDecodeHints);
        } catch (com.google.zxing.NotFoundException e) {
            // not an error - we just didn't find a barcode
        } catch (com.google.zxing.ChecksumException e) {
            // not an error - the barcode just wasn't completely read
        } catch (com.google.zxing.FormatException e) {
            // not an error - the barcode just wasn't completely read
        }

        return r;
    }

    private int mConsecutiveNoHits = 0;
    private Geometry.Rectangle mLatestMatch = null;

    private void rememberMatch(Result r)
    {
        if (r != null && r.getResultPoints() != null && r.getResultPoints().length > 2)
        {
            ResultPoint first = r.getResultPoints()[0];
            Geometry.Rectangle match = new Geometry.Rectangle((int)first.getX(), (int)first.getY());
            //Log.d(TAG, "CROP a: (" + first.getX() + ", " + first.getY() + ") => (" + match.x + ", " + match.y + ", " + match.width + ", " + match.height + ")");

            for (ResultPoint p : r.getResultPoints())
            {
                match = match.expandToInclude((int)p.getX(), (int)p.getY());
                //Log.d(TAG, "CROP b: (" + p.getX() + ", " + p.getY() + ") => (" + match.x + ", " + match.y + ", " + match.width + ", " + match.height + ")");
            }

            match = match.addRelativeMargin(mRelativeTrackingMargin);

            if (match.width <= 0 || match.height <= 0)
                match = null;

            mLatestMatch = match;

            //Log.d(TAG, "CROP: (" + match.x + ", " + match.y + ", " + match.width + ", " + match.height + ")");
        }
    }
}

