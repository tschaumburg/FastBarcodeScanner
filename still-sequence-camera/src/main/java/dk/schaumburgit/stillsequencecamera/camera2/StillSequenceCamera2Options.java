package dk.schaumburgit.stillsequencecamera.camera2;

import android.view.TextureView;

/**
 * Created by Thomas on 06-02-2018.
 */

public class StillSequenceCamera2Options
{
    public enum Facing
    {
        Back,
        Front,
        External
    }

    public TextureView preview;
    public Facing facing = Facing.Back;
    public int minPixels = 1024*768;
    public StillSequenceCamera2Options(TextureView preview, int minPixels, Facing facing)
    {
        this.preview = preview;
        this.minPixels = minPixels;
        this.facing = facing;
    }
    public StillSequenceCamera2Options(TextureView preview)
    {
        this.preview = preview;
    }
}
