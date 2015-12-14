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
import java.util.Map;

/**
 * The TrackingBarcodeScanner class looks for barcodes in the images supplied by the called.
 *
 * What distinguishes it from other barcode scanners is the tracking algorithm used. The tracking
 * algorithm is optimized for use with sequential images (like the frames in a video recording).
 * Once it has found a barcode in one area, it will look in the same area first in the next frame
 * - only if that fails will it look in the entire image.
 *
 * This optimization yields speed-ups of 2-5 times (i.e. finding a barcode falls from 100ms to
 * 20ms) in optimum situations (meaning where the barcode moves relatively little between frames).
 *
 * In situations where the images are completely unrelated, the tracking may actually cause a
 * slowdown, due to the time wasted on the initial scan.
 *
 * The tracking algorithm may be switched on and off dynamically, so you can experiment with it's
 * applicability for you scenario (see UseTracking bellow).
 *
 * The tracking algorithm is affected by the following properties:
 *
 * UseTracking (boolean): switches the entire tracking on or off (default: true)
 *
 * RelativeTrackingMargin (double): Specifies the relative margin to around the previous hit when
 * running the initial tracking scan. Large values allow relatively large movements between frames,
 * at the cost of lowered performance. Smaller values are faster, but allow less movement before
 * tracking is lost (default 1.0)
 *
 * NoHitsBeforeTrackingLoss (int): Due to bad frames (e.g. motion blur), no-hit scans may
 * occasionally occur. This parameter specifies how many consecutive bad frames will cause
 * a tracking loss (default 5).
 *
 * PreferredImageFormats (readonly, int[]): Specifies the image formats supported by
 * TrackingBarcodeScanner - using values from the ImageFormats enum - in order of preference
 * (default {YUV_420_888, JPEG})
 */
public class TrackingBarcodeScanner {

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "BarcodeFinder";

    private static final int[] mAllImageFormats =
            {
                    ImageFormat.UNKNOWN,
                    ImageFormat.RGB_565,
                    ImageFormat.YV12,
                    //ImageFormat.Y8,
                    //ImageFormat.Y16,
                    ImageFormat.NV16,
                    ImageFormat.NV21,
                    //ImageFormat.YUY2,
                    ImageFormat.JPEG,
                    ImageFormat.YUV_420_888,
                    ImageFormat.YUV_422_888,
                    ImageFormat.YUV_444_888,
                    ImageFormat.FLEX_RGB_888,
                    ImageFormat.FLEX_RGBA_8888,
                    ImageFormat.RAW_SENSOR,
                    ImageFormat.RAW10,
                    ImageFormat.RAW12,
                    ImageFormat.DEPTH16,
                    ImageFormat.DEPTH_POINT_CLOUD,
            };

    private static final int[] mPreferredImageFormats =
            {
                    ImageFormat.YUV_420_888,
                    ImageFormat.JPEG
            };
    private boolean mUseTracking = true;
    private double mRelativeTrackingMargin = 1.0;
    private int mNoHitsBeforeTrackingLoss = 5;
    private EnumSet<BarcodeFormat> mPossibleBarcodeFormats = EnumSet.of(BarcodeFormat.QR_CODE);

    private QRCodeReader mReader = new QRCodeReader();
    private Hashtable<DecodeHintType, Object> mDecodeHints;
    public TrackingBarcodeScanner()
    {
        mDecodeHints = new Hashtable<DecodeHintType, Object>();
        mDecodeHints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        mDecodeHints.put(DecodeHintType.POSSIBLE_FORMATS, mPossibleBarcodeFormats);
    }

    public Date a;
    public Date b;
    public Date c;
    public Date d;
    public Date f;
    public Date g;
    public String find(int imageFormat, int w, int h, byte[] bytes)
    {
        if (LuminanceSourceFactory.isFormatSupported(imageFormat) == false)
            throw new UnsupportedOperationException("ZXing cannot process images of type " + imageFormat);

        a = new Date();
        // First we'll convert into a BinaryBitmap:
        BinaryBitmap bitmap = null;
        try {
            c = new Date();
            LuminanceSource lumSource = LuminanceSourceFactory.getLuminanceSource(imageFormat, bytes, w, h);
            d = new Date();
            bitmap = new BinaryBitmap(new HybridBinarizer(lumSource));
            f = new Date();
        } catch (Exception e) {
            Log.e(TAG, "failed reading captured image", e);
            return null;
        }

        try {
            Result r = null;

            if (mUseTracking) {
                // First try where we found the barcode before (much quicker that way):
                if (mLatestMatch != null) {
                    Geometry.Rectangle crop = mLatestMatch.normalize(0, 0, bitmap.getWidth(), bitmap.getHeight());
                    //Log.d(TAG, "CROP: looking in (" + crop.x + ", " + crop.y + ", " + crop.width + ", " + crop.height + ")");
                    int left = crop.x;
                    int top = crop.y;
                    r = doFind(bitmap.crop(left, top, crop.width, crop.height));
                }
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

    public double getRelativeTrackingMargin() {
        return mRelativeTrackingMargin;
    }

    public void setRelativeTrackingMargin(double mRelativeTrackingMargin) {
        this.mRelativeTrackingMargin = mRelativeTrackingMargin;
    }

    public int getNoHitsBeforeTrackingLoss() {
        return mNoHitsBeforeTrackingLoss;
    }

    public void setNoHitsBeforeTrackingLoss(int mNoHitsBeforeTrackingLoss) {
        this.mNoHitsBeforeTrackingLoss = mNoHitsBeforeTrackingLoss;
    }

    public EnumSet<BarcodeFormat> getPossibleBarcodeFormats() {
        return mPossibleBarcodeFormats;
    }

    public void setPossibleBarcodeFormats(EnumSet<BarcodeFormat> mPossibleFormats) {
        this.mPossibleBarcodeFormats = mPossibleFormats;
        mDecodeHints.put(DecodeHintType.POSSIBLE_FORMATS, mPossibleBarcodeFormats);
    }

    public boolean isUseTracking() {
        return mUseTracking;
    }

    public void setUseTracking(boolean useTracking) {
        this.mUseTracking = useTracking;
    }

    public Map<Integer, Double> getPreferredImageFormats()
    {
        return LuminanceSourceFactory.SUPPORTED_FORMAT_COSTS;
    }
}

