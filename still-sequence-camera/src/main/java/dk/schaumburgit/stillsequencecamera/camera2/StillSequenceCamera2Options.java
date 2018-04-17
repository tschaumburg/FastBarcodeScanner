package dk.schaumburgit.stillsequencecamera.camera2;

import android.view.TextureView;

/**
 * Created by Thomas on 06-02-2018.
 */

public class StillSequenceCamera2Options
{
    public enum Facing
    {
        Back(0),
        Front(1),
        External(2)
        ;
        private final int intValue;

        private Facing(int intValue) {
            this.intValue = intValue;
        }
    }

    public final TextureView preview;
    public final Facing facing;
    public final int minPixels;
    public StillSequenceCamera2Options(TextureView preview, int minPixels, Facing facing)
    {
        this.preview = preview;
        this.minPixels = minPixels;
        this.facing = facing;
    }
    public StillSequenceCamera2Options(TextureView preview)
    {
        this(preview, 1024*768, Facing.Back);
    }
    public StillSequenceCamera2Options clone(int minPixels)
    {
        return new StillSequenceCamera2Options(this.preview, minPixels, this.facing);
    }
    public StillSequenceCamera2Options clone(TextureView preview)
    {
        return new StillSequenceCamera2Options(preview, this.minPixels, this.facing);
    }
    public StillSequenceCamera2Options clone(Facing facing)
    {
        return new StillSequenceCamera2Options(this.preview, this.minPixels, facing);
    }
}
