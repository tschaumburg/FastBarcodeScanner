package dk.schaumburgit.stillsequencecamera;

import android.app.Activity;
import android.media.Image;
import android.view.TextureView;

/**
 * Created by Thomas Schaumburg on 21-11-2015.
 */
public interface IStillSequenceCamera {
    //void Init(Activity activity, TextureView textureView, int[] supportedImageFormatss);

    void StartFocus();
    boolean IsFocused();
    void setStateChangeListener(StateChangeListener listener);
    public interface StateChangeListener {
        void onStateChanged();
    }

    void StartCapture();

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
    void StopCapture();
    void setImageListener(OnImageAvailableListener listener);
    public interface OnImageAvailableListener {
        void onImageAvailable();
    }
}

