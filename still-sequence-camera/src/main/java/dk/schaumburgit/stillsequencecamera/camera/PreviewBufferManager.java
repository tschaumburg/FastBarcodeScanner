package dk.schaumburgit.stillsequencecamera.camera;

import android.graphics.ImageFormat;
import android.hardware.Camera;

/**
 * Created by Thomas on 17-04-2018.
 */

class PreviewBufferManager {
    private Object _lock = new Object();
    private final Camera mCamera;
    private int mPreviewFormat;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private int mPreviewBufferSize;
    public PreviewBufferManager(Camera camera)
    {
        mCamera = camera;

    }

    public void setup(int previewFormat, int previewWidth, int previewHeight) {
        mPreviewFormat = previewFormat;
        mPreviewWidth = previewWidth;
        mPreviewHeight = previewHeight;
        int bitsPerPixel = ImageFormat.getBitsPerPixel(mPreviewFormat);
        mPreviewBufferSize = (mPreviewWidth * mPreviewHeight * bitsPerPixel) / 8;
    }

    private int buffersCurrentlyAllocated = 0;
    private int buffersCurrentlyBorrowed = 0;
    public void start(int nBuffers) {
        int nBufs = 3;
        for (int n = 0; n < nBuffers; n++) {
            allocateBuffer();
        }
    }

    private void allocateBuffer()
    {
        mCamera.addCallbackBuffer(new byte[mPreviewBufferSize]);
        synchronized (_lock) {
            buffersCurrentlyAllocated++;
        }
    }

    public PreviewBuffer borrow(byte[] buffer)
    {
        synchronized (_lock) {
            buffersCurrentlyBorrowed++;
        }
        return new PreviewBuffer(this, buffer);
    }

    void _return(byte[] buffer) {
        mCamera.addCallbackBuffer(buffer);
        synchronized (_lock) {
            buffersCurrentlyBorrowed--;
        }
    }
}
