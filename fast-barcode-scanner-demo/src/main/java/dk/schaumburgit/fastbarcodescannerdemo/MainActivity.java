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
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v13.app.FragmentCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import dk.schaumburgit.fastbarcodescanner.FastBarcodeScanner;
import dk.schaumburgit.stillsequencecamera.StillSequenceCamera2;

public class MainActivity extends AppCompatActivity
        implements FastBarcodeScanner.BarcodeDetectedListener, FastBarcodeScanner.ScanningStateListener
{
    private static final String TAG = "FastBarcodeScannerDemo";

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

    FastBarcodeScanner mScanner = null;
    private void startScan() {
        requestCameraPermission();

        if (mScanner == null) {
            mScanner = new FastBarcodeScanner(this, null);
            mScanner.setScanningStateListener(this);
            mScanner.setBarcodeListener(this);
        }

        Button startButton = (Button)findViewById(R.id.start);
        Button stopButton = (Button)findViewById(R.id.button3);

        startButton.setEnabled(false);
        mScanner.StartScan();
        stopButton.setEnabled(true);
    }

    private void stopScan() {
        Button startButton = (Button)findViewById(R.id.start);
        Button stopButton = (Button)findViewById(R.id.button3);

        stopButton.setEnabled(false);
        mScanner.StopScan();
        startButton.setEnabled(true);
    }

    private void showSpinner()
    {
        ProgressBar spinner;
        spinner = (ProgressBar)findViewById(R.id.progressBar1);
        spinner.setVisibility(View.VISIBLE);
    }

    private void hideSpinner()
    {
        ProgressBar spinner;
        spinner = (ProgressBar)findViewById(R.id.progressBar1);
        spinner.setVisibility(View.INVISIBLE);
    }

    private String mLatestBarcode;
    @Override
    public void onBarcodeAvailable(final String barcode) {
        mLatestBarcode = (barcode == null) ? "none" : barcode;
        final TextView resView = (TextView) findViewById(R.id.textView);

        Log.d(TAG, "DETECTED BARCODE " + barcode);

        this.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        resView.setText(mLatestBarcode);
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
    public void onFocusStateChanged(int focusState)
    {
        final TextView focusView = (TextView) findViewById(R.id.textView2);
        String focus = "unknown";
        switch (focusState)
        {
            case FastBarcodeScanner.ScanningStateListener.FOCUS_IDLE:
                focus = "idle";
                break;
            case FastBarcodeScanner.ScanningStateListener.FOCUS_FOCUSING:
                focus = "focusing...";
                break;
            case FastBarcodeScanner.ScanningStateListener.FOCUS_FOCUSED:
                focus = "focus";
                break;
            case FastBarcodeScanner.ScanningStateListener.FOCUS_UNFOCUSED:
                focus = "no focus";
                break;
            case FastBarcodeScanner.ScanningStateListener.FOCUS_LOCKED:
                focus = "locked!";
                break;
            case FastBarcodeScanner.ScanningStateListener.FOCUS_FAILED:
                focus = "failed!";
                break;
            default:
                focus = "unknown (" + focus + ")";
                break;
        }
        final String finalFocus = focus;
        this.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        focusView.setText(finalFocus);
                    }
                }
        );
    }

    @Override
    public void onScanSpeedChanged(int fps)
    {

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
                StillSequenceCamera2.ErrorDialog.newInstance(getString(R.string.request_permission))
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
