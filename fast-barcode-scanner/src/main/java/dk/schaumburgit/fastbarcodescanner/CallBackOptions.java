package dk.schaumburgit.fastbarcodescanner;

import android.os.Handler;

/**
 * Created by Thomas on 05-02-2018.
 */

public class CallBackOptions
{
    public final boolean includeImageInCallback;
    //public BarcodeDetectedListener listener;
    public CallBackOptions(boolean includeImageInCallback)
    {
        this.includeImageInCallback = includeImageInCallback;

    }
    public CallBackOptions()
    {
        this.includeImageInCallback = false;

    }
    //public Handler callbackHandler;
}
