package dk.schaumburgit.stillsequencecamera;

import android.os.Handler;

import com.google.zxing.BinaryBitmap;

import java.util.List;

/**
 * Created by Thomas Schaumburg on 21-11-2015.
 */
public interface IStillSequenceCamera {
    double sourceAspectRatio();
    List<CaptureFormatInfo> getSupportedImageFormats(double relativeDevicePerformance);
    void setup(int imageFormat, int imageWidth, int imageHeight);
    void start(OnImageAvailableListener listener, Handler callbackHandler);
    void stop();
    void close();

    public interface OnImageAvailableListener
    {
        void onImageAvailable(ISource source, BinaryBitmap bitmap);
        void onError(Exception error);
    }

    boolean isLockFocus();
    void setLockFocus(boolean lockFocus);
}

