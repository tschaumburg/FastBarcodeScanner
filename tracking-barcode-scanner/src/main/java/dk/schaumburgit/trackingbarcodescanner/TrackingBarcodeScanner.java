package dk.schaumburgit.trackingbarcodescanner;

/**
 * Created by Thomas on 21-11-2015.
 */

import android.graphics.ImageFormat;
import android.graphics.Point;
import android.media.Image;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;
import com.google.zxing.qrcode.QRCodeReader;

import java.nio.ByteBuffer;
import java.util.ArrayList;
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

    /*private static final int[] mAllImageFormats =
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
            };*/
    private EnumSet<BarcodeFormat> mPossibleBarcodeFormats = EnumSet.of(BarcodeFormat.QR_CODE);

    private QRCodeReader mReader = new QRCodeReader();
    private QRCodeMultiReader mMultiReader = new QRCodeMultiReader(); //new GenericMultipleBarcodeReader(new MultiFormatReader());
    private Hashtable<DecodeHintType, Object> mDecodeHints;
    private final TrackingOptions mTrackingOptions;
    private final ScanOptions mScanOptions;

    /*public TrackingBarcodeScanner()
    {
        this(new ScanOptions(), new TrackingOptions());
    }*/

    public TrackingBarcodeScanner(ScanOptions scanOptions, TrackingOptions trackingOptions)
    {
        this.mTrackingOptions = trackingOptions;
        this.mScanOptions = scanOptions;
        mDecodeHints = new Hashtable<DecodeHintType, Object>();
        mDecodeHints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        mDecodeHints.put(DecodeHintType.POSSIBLE_FORMATS, mPossibleBarcodeFormats);
    }

    public BinaryBitmap DecodeImage(byte[] jpegData, int width, int height) {
        try {
            LuminanceSource lumSource = LuminanceSourceFactory.getLuminanceSource(ImageFormat.JPEG, jpegData, width, height);
            return new BinaryBitmap(new HybridBinarizer(lumSource));
        } catch (Exception e) {
            Log.e(TAG, "failed reading captured image", e);
            return null;
        }
    }

    public BinaryBitmap DecodeImage(Image source)
    {
        BinaryBitmap bitmap = null;

        byte[] bytes = null;
        int imageFormat = source.getFormat();
        int w = source.getWidth();
        int h = source.getHeight();
        Image.Plane plane = source.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        if (LuminanceSourceFactory.isFormatSupported(imageFormat) == false)
            throw new UnsupportedOperationException("ZXing cannot process images of type " + imageFormat);

        // First we'll convert into a BinaryBitmap:
        try {
            LuminanceSource lumSource = LuminanceSourceFactory.getLuminanceSource(imageFormat, bytes, w, h);
            bitmap = new BinaryBitmap(new HybridBinarizer(lumSource));
        } catch (Exception e) {
            Log.e(TAG, "failed reading captured image", e);
            return null;
        }

        return bitmap;
    }

    public Barcode findSingle(BinaryBitmap bitmap)
    {
        if (bitmap == null)
            return null;

        try {
            Result r = null;
            int left = 0;
            int top = 0;

            if (mTrackingOptions != null && mTrackingOptions.trackingPatience > 0) {
                // First try where we found the barcode before (much quicker that way):
                if (mLatestMatch != null) {
                    Geometry.Rectangle crop = mLatestMatch.normalize(0, 0, bitmap.getWidth(), bitmap.getHeight());
                    //Log.d(TAG, "CROP: looking in (" + crop.x + ", " + crop.y + ", " + crop.width + ", " + crop.height + ")");
                    left = crop.x;
                    top = crop.y;
                    r = doFind(bitmap.crop(left, top, crop.width, crop.height), mScanOptions.beginsWith);
                }
            }

            // If that didn't work, look at the entire picture:
            if (r==null)
            {
                //Log.d(TAG, "CROP: Failed - looking in full bitmap");
                left = 0;
                top = 0;
                r = doFind(bitmap, mScanOptions.beginsWith);

                // if that worked (i.e. the barcode is in a new place),
                // we'll update the mLatestMatch rectangle:
                if (r != null)
                    rememberMatch(r);
            } else
            {
                //Log.d(TAG, "CROP: Succeded - found barcode " + r.getText());
            }

            if (r == null)
            {
                if (mConsecutiveNoHits++ > mTrackingOptions.trackingPatience)
                    mLatestMatch = null;

                return null;
            }

            mConsecutiveNoHits = 0;
            return new Barcode(r.getText(), r.getBarcodeFormat(), _convert(r.getResultPoints(), left, top));
        } catch (Exception e) {
            Log.e(TAG, "FAILED DECODING", e);
            return null;
        }
    }

    private Point[] _convert(ResultPoint[] points, int addLeft, int addTop) {
        Point[] res = new Point[points.length];
        for (int n = 0; n< points.length; n++)
            res[n] = new Point((int)points[n].getX() + addLeft, (int)points[n].getY() + addTop);
        return res;
    }

    public Barcode[] findMultiple(BinaryBitmap bitmap)
    {
        if (bitmap == null)
            return null;

        try {
            Result[] rs = mMultiReader.decodeMultiple(bitmap, mDecodeHints);
            return _convert(rs, this.mScanOptions.beginsWith);
        } catch (com.google.zxing.NotFoundException e) {
            // not an error - we just didn't find a barcode
        } catch (Exception e) {
            Log.e(TAG, "FAILED DECODING", e);
        }

        return null;
    }

    private Barcode[] _convert(Result[] rs, String beginsWith)
    {
        if (rs == null)
            return null;

        //Barcode[] res = new Barcode[rs.length];
        ArrayList<Barcode> res2 = new ArrayList<Barcode>(rs.length);

        for (int n=0; n< rs.length; n++) {
            //res[n] = new Barcode(rs[n].getText(), rs[n].getBarcodeFormat(), _convert(rs[n].getResultPoints(), 0, 0));
            if (beginsWith == null || rs[n].getText().startsWith(beginsWith))
                res2.add(new Barcode(rs[n].getText(), rs[n].getBarcodeFormat(), _convert(rs[n].getResultPoints(), 0, 0)));
        }

        //return res;
        return res2.toArray(new Barcode[res2.size()]);
    }

    private Result doFind(BinaryBitmap bitmap, String beginsWith) {
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

        if (r == null)
            return r;

        if (beginsWith == null || beginsWith.length() == 0)
            return r;

        if (r.getText() == null)
            return null;

        if (r.getText().startsWith(beginsWith))
            return r;

        return null;
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

            match = match.addRelativeMargin(mTrackingOptions.trackingMargin);

            if (match.width <= 0 || match.height <= 0)
                match = null;

            mLatestMatch = match;

            //Log.d(TAG, "CROP: (" + match.x + ", " + match.y + ", " + match.width + ", " + match.height + ")");
        }
    }

    public EnumSet<BarcodeFormat> getPossibleBarcodeFormats() {
        return mPossibleBarcodeFormats;
    }

    public void setPossibleBarcodeFormats(EnumSet<BarcodeFormat> mPossibleFormats) {
        this.mPossibleBarcodeFormats = mPossibleFormats;
        mDecodeHints.put(DecodeHintType.POSSIBLE_FORMATS, mPossibleBarcodeFormats);
    }

    public Map<Integer, Double> getPreferredImageFormats()
    {
        return LuminanceSourceFactory.SUPPORTED_FORMAT_COSTS;
    }
}

