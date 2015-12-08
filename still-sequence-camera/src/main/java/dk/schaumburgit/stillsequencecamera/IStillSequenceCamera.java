package dk.schaumburgit.stillsequencecamera;

import android.app.Activity;
import android.media.Image;
import android.view.TextureView;

/**
 * Created by Thomas Schaumburg on 21-11-2015.
 */
public interface IStillSequenceCamera {
    void setup();
    void start(OnImageAvailableListener listener);
    void stop();
    void close();

    public interface OnImageAvailableListener
    {
        void onImageAvailable(int format, byte[] data, int width, int height);
    }
}

