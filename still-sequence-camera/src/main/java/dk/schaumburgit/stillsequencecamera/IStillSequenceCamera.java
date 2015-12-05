package dk.schaumburgit.stillsequencecamera;

import android.app.Activity;
import android.media.Image;
import android.view.TextureView;

/**
 * Created by Thomas Schaumburg on 21-11-2015.
 */
public interface IStillSequenceCamera {
    void setCameraStateChangeListener(CameraStateChangeListener listener);
    public interface CameraStateChangeListener
    {
        /**
         * Called whenever the auto-focus or auto-exposure states of the camera device change,
         * @param autoFocusState Value returned from CaptureResult.get(CaptureResult.CONTROL_AF_STATE)
         * @param autoExposureState Value returned from CaptureResult.get(CaptureResult.CONTROL_AE_STATE)
         * @param isCapturing True if the camera is capturing
         * @see android.hardware.camera2.CaptureResult#CONTROL_AF_STATE_INACTIVE
         * @see android.hardware.camera2.CaptureResult#CONTROL_AF_STATE_PASSIVE_SCAN
         * @see android.hardware.camera2.CaptureResult#CONTROL_AF_STATE_PASSIVE_FOCUSED
         * @see android.hardware.camera2.CaptureResult#CONTROL_AF_STATE_ACTIVE_SCAN
         * @see android.hardware.camera2.CaptureResult#CONTROL_AF_STATE_FOCUSED_LOCKED
         * @see android.hardware.camera2.CaptureResult#CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
         * @see android.hardware.camera2.CaptureResult#CONTROL_AF_STATE_PASSIVE_UNFOCUSED
         */
        void onCameraStateChanged(Integer autoFocusState, Integer autoExposureState, boolean isCapturing);
    }

    void StartCapture();
    void StopCapture();

    /**
     * Gets the latest capture image (or null if none are available).
     *
     * The caller MUST call close() on the returned Image at the
     * earliest possible time - the camera has a maximum capacity of
     * 2 unclosed images.
     *
     * If more than one image are available, the older images are
     * closed and discarded when this method is called.
     * @return the latest captured image
     */
    Image GetLatestImage();

    void setImageListener(OnImageAvailableListener listener);
    public interface OnImageAvailableListener {
        void onImageAvailable();
    }
}

