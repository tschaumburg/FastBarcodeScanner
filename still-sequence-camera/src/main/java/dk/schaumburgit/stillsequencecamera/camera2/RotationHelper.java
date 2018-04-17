package dk.schaumburgit.stillsequencecamera.camera2;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Log;
import android.view.Surface;

/**
 * Created by Thomas on 02-03-2018.
 */

class RotationHelper
{
    private static final String TAG = "RotationHelper";
    static int getNeededImageRotationDegrees(Activity activity, String cameraId)
    {
        int deviceRotationDegrees = getDeviceRotation(activity);
        int cameraOrientationDegrees = getSensorOrientation(activity, cameraId);

        int result;
        if (isFrontFacing(activity, cameraId))
        {
            result = (cameraOrientationDegrees + deviceRotationDegrees) % 360;
            result = (360 - result) % 360;
        }
        else
        {
            result = (cameraOrientationDegrees - deviceRotationDegrees + 270) % 360;
        }

        //Log.v(TAG, "ROTATION: (cameraOrientationDegrees: " + cameraOrientationDegrees + ", deviceRotationDegrees: " + deviceRotationDegrees + ") => " + result);

        return result;
    }

    private static int getSensorOrientation(Activity activity, String cameraId)
    {
        try {
            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            return characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        } catch (android.hardware.camera2.CameraAccessException e) {
            Log.e(TAG, "CameraAccessException");
            throw new UnsupportedOperationException("CameraAccessException");
        }
    }

    private static int getDeviceRotation(Activity activity) {
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

        switch (rotation) {
            case Surface.ROTATION_0:
                return 0;
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            default:
                return 0;
        }
    }

    private static boolean isFrontFacing(Activity activity, String cameraId)
    {
        try {
            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            return facing == CameraCharacteristics.LENS_FACING_FRONT;
        } catch (android.hardware.camera2.CameraAccessException e) {
            Log.e(TAG, "CameraAccessException");
            throw new UnsupportedOperationException("CameraAccessException");
        }
    }
}
