package dk.schaumburgit.stillsequencecamera;

import android.app.Activity;
import android.media.Image;
import android.os.Handler;
import android.view.TextureView;

import java.io.IOException;
import java.util.Map;

/**
 * Created by Thomas Schaumburg on 21-11-2015.
 */
public interface IStillSequenceCamera {
    Map<Integer,Double> getSupportedImageFormats();
    void setup(int imageFormat);
    void start(OnImageAvailableListener listener, Handler callbackHandler);
    void stop();
    void close();

    public interface OnImageAvailableListener
    {
        void onImageAvailable(int format, byte[] data, int width, int height);
        void onError(Exception error);
    }

    boolean isLockFocus();
    void setLockFocus(boolean lockFocus);
}

