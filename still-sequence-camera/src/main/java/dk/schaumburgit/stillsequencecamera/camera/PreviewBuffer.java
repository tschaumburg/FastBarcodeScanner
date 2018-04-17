package dk.schaumburgit.stillsequencecamera.camera;

import android.hardware.Camera;

import dk.schaumburgit.stillsequencecamera.ISource;

/**
 * Created by Thomas on 17-04-2018.
 */

class PreviewBuffer implements ISource {
    private final Object mLock = new Object();
    private final PreviewBufferManager mManager;
    private byte[] mBuffer;
    public PreviewBuffer(PreviewBufferManager mgr, byte[] buffer)
    {
        mManager = mgr;
        mBuffer = buffer;
    }

    @Override
    public String save() {
        return null;
    }

    @Override
    public void close() {
        byte[] buffer = null;
        synchronized (mLock) {
            if (mBuffer != null) {
                buffer = mBuffer;
                mBuffer = null;
            }
        }

        if (buffer != null)
            mManager._return(buffer);
    }
}
