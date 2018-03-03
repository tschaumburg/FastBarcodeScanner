package dk.schaumburgit.fastbarcodescannerdemo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import dk.schaumburgit.fastbarcodescanner.BarcodeScannerFactory;
import dk.schaumburgit.fastbarcodescanner.EventConflation;
import dk.schaumburgit.fastbarcodescanner.IBarcodeScanner;
import dk.schaumburgit.fastbarcodescanner.IBarcodeScanner.BarcodeDetectedListener;
import dk.schaumburgit.fastbarcodescanner.IBarcodeScanner.BarcodeInfo;
import dk.schaumburgit.fastbarcodescanner.imageutils.ImageDecoder;
import dk.schaumburgit.fastbarcodescanner.IBarcodeScanner.MultipleBarcodesDetectedListener;

public class MainActivity extends AppCompatActivity
        implements BarcodeDetectedListener, MultipleBarcodesDetectedListener//, BarcodeScanner.ScanningStateListener
{
    private static final String TAG = "FastBarcodeScannerDemo";
    private SurfaceView mSurfaceView;
    private TextureView mTextureView;
    private ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Button startButton = (Button)findViewById(R.id.start);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startScan();
            }
        });

        Button stopButton = (Button)findViewById(R.id.button3);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopScan();
            }
        });

        Button pauseResumeButton = (Button)findViewById(R.id.pauseresume);
        pauseResumeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pauseResume();
            }
        });

        //mSurfaceView = (SurfaceView)findViewById(R.id.preview);
        mTextureView = (TextureView)findViewById(R.id.preview2);
        mImageView = (ImageView)findViewById(R.id.imageview);
    }


    @Override
    protected void onDestroy() {
        mScanner.Close();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void pauseResume() {
        // like pause:
        mScanner.StopScan();
        // like resume:
        mScanner.StartScan(this, null);
    }

    IBarcodeScanner mScanner = null;
    private void startScan() {
        requestCameraPermission();

        if (mScanner == null) {
            nCallbacks = 0;
            //mScanner = new BarcodeScanner(this, (TextureView)null, 4*1024*768);
            //mScanner = new BarcodeScanner(this, mSurfaceView);
            //mScanner.setScanningStateListener(this);
            //mScanner = new BarcodeScanner(this, mTextureView, 4*1024*768);
            /*mScanner =
                    BarcodeScanner
                            .BackCamera(this, mTextureView) // (TextureView)null)
                            .Capture(4*1024*768)
                            .scanQR()
                            //.beginningWith("sdp:card")
                            .enableTracking(1.0, 3, "")
                            .deglitch(3)
                            //.verbose()
                            .build();*/

            mScanner =
                    BarcodeScannerFactory
                            .builder(mTextureView)
                            .resolution(4*1024*768)
                            //.findQR()
                            .debounceBlanks(3)
                            .debounceErrors(3)
                            .track(1.0, 3)
                            .build(this);

        }

        Button startButton = (Button)findViewById(R.id.start);
        Button stopButton = (Button)findViewById(R.id.button3);
        Button pauseResumeButton = (Button)findViewById(R.id.pauseresume);

        startButton.setEnabled(false);
        mScanner.setLockFocus(true);
        //mScanner.setIncludeImagesInCallback(true);
        //mScanner.StartMultiScan(true, 4, "pfx:scorecard:", this, null);
        mScanner.StartScan(this, null);
        stopButton.setEnabled(true);
        pauseResumeButton.setEnabled(true);
    }

    private void rotate() {
        int h = mTextureView.getLayoutParams().height;
        int w = mTextureView.getLayoutParams().width;
        //mTextureView.getLayoutParams().height = w;
        //mTextureView.getLayoutParams().width = h;

        //ViewParent vp = mTextureView.getParent();
        //vp.requestLayout();
        //vp.invalidate();
        //vp.recomputeViewAttributes(mTextureView);
        mTextureView.setLayoutParams(new RelativeLayout.LayoutParams(h, w));
    }

    private void stopScan() {
        Button startButton = (Button)findViewById(R.id.start);
        Button stopButton = (Button)findViewById(R.id.button3);
        Button pauseResumeButton = (Button)findViewById(R.id.pauseresume);

        stopButton.setEnabled(false);
        pauseResumeButton.setEnabled(false);
        mScanner.StopScan();
        startButton.setEnabled(true);
    }

    @Override
    public void OnHits(BarcodeInfo[] barcodes, Image image) {
        String barcodesText = null;

        if (barcodes != null && barcodes.length > 0)
        {
            barcodesText = "";
            for (int n = 0; n < barcodes.length; n++)
            {
                barcodesText = barcodesText + ", " + barcodes[n].barcode;
            }
            barcodesText = "" + barcodes.length + ": " + barcodesText.substring(2);
        }

        final String latestBarcode = (barcodesText == null) ? "none" : barcodesText;
        final TextView resView = (TextView) findViewById(R.id.textView2);

        Log.v(TAG, "Start decode");
        final Bitmap bm = image2bitmap(image);
        Log.v(TAG, "End decode");

        this.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        resView.setText(latestBarcode);
                        if (bm != null)
                            mImageView.setImageBitmap(bm);
                    }
                }
        );

        if (barcodesText != null) {
            if (!"1: pfx:calibrator".equalsIgnoreCase(barcodesText)) {
                Vibrator v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
                // Vibrate for 100 milliseconds
                v.vibrate(100);
            }
        }
    }

    private Bitmap image2bitmap(Image source)
    {
        if (source==null)
            return null;

        final byte[] serialized = ImageDecoder.Serialize(source);
        final int width = source.getWidth();
        final int height = source.getHeight();
        final int format = source.getFormat();

        return ImageDecoder.ToBitmap(serialized, format, width, height);
    }

    private int nCallbacks = 9;
    @Override
    public void OnHit(BarcodeInfo barcodeInfo, Image image)
    {
        nCallbacks++;
        String barcode = null;
        if (barcodeInfo != null)
            barcode = barcodeInfo.barcode;

        final int count = nCallbacks;
        final String latestBarcode = (barcode == null) ? "none" : barcode;
        final TextView resView = (TextView) findViewById(R.id.textView2);

        Log.v(TAG, "Start decode");
        final Bitmap bm = image2bitmap(image);
        Log.v(TAG, "End decode");

        this.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        resView.setText(latestBarcode + "  (" + count + ")");
                        if (bm != null)
                            mImageView.setImageBitmap(bm);
                    }
                }
        );

        if (barcode != null) {
            if (!"pfx:calibrator".equalsIgnoreCase(barcode)) {
                Vibrator v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
                // Vibrate for 100 milliseconds
                v.vibrate(100);
            }
        }
    }

    @Override
    public void OnBlank() {

    }

    private String formatFormat(int imageFormat)
    {
        switch (imageFormat)
        {
            case ImageFormat.UNKNOWN:
                return "UNKNOWN";
            case ImageFormat.NV21:
                return "NV21";
            case ImageFormat.NV16:
                return "NV16";
            case ImageFormat.YV12:
                return "YV12";
            case ImageFormat.YUY2:
                return "YUY2";
            case ImageFormat.YUV_420_888:
                return "YUV_420_888";
            case ImageFormat.YUV_422_888:
                return "YUV_422_888";
            case ImageFormat.YUV_444_888:
                return "YUV_444_888";
            case ImageFormat.FLEX_RGB_888:
                return "FLEX_RGB_888";
            case ImageFormat.FLEX_RGBA_8888:
                return "FLEX_RGBA_8888";
            case ImageFormat.JPEG:
                return "JPEG";
            case ImageFormat.RGB_565:
                return "RGB_565";
            case ImageFormat.RAW_SENSOR:
                return "RAW_SENSOR";
            case ImageFormat.RAW10:
                return "RAW10";
            case ImageFormat.RAW12:
                return "RAW12";
            case ImageFormat.DEPTH16:
                return "DEPTH16";
            case ImageFormat.DEPTH_POINT_CLOUD:
                return "DEPTH_POINT_CLOUD";
            //case ImageFormat.Y8:
            //case ImageFormat.Y16:

        }

        return "" + imageFormat;
    }

    @Override
    public void OnError(Exception error) {

    }


    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    private void requestCameraPermission() {
        //if (this.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
        //    new ConfirmationDialog().show(this.getFragmentManager(), FRAGMENT_DIALOG);
        //} else {
            this.requestPermissions(new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        //}
        //Log.e(TAG, "DOESNT HAVE CAMERA PERMISSION");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(this.getFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }
    }
    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent,
                                    new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }

}
