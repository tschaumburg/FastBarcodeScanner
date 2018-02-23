package dk.schaumburgit.stillsequencecamera.camera;

import android.view.SurfaceView;

/**
 * Created by Thomas on 06-02-2018.
 */

public class StillSequenceCameraOptions
{
    public enum Facing
    {
        Back,
        Front,
        External
    }

    public final SurfaceView preview;
    public final Facing facing = Facing.Back;
    public final int minPixels;// = 1024*768;
    public StillSequenceCameraOptions(SurfaceView preview, int minPixels)
    {
        this.preview = preview;
        this.minPixels = minPixels;
    }
    public StillSequenceCameraOptions clone(int minPixels)
    {
        return new StillSequenceCameraOptions(this.preview, minPixels);
    }
    public StillSequenceCameraOptions clone(SurfaceView preview)
    {
        return new StillSequenceCameraOptions(preview, this.minPixels);
    }
}
