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
    public Facing facing = Facing.Back;
    public int minPixels = 1024*768;
    public StillSequenceCameraOptions(SurfaceView preview)
    {
        this.preview = preview;
    }
    public StillSequenceCameraOptions(SurfaceView preview, int minPixels)
    {
        this.preview = preview;
        this.minPixels = minPixels;
    }
}
