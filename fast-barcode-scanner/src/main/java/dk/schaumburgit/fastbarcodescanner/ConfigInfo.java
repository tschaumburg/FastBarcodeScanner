package dk.schaumburgit.fastbarcodescanner;

/**
 * Created by Thomas on 12-04-2018.
 */

public class ConfigInfo {
    public final int imageFormat;
    public final int imageWidth;
    public final int imageHeight;
    public ConfigInfo(int imageFormat, int imageWidth, int imageHeight)
    {
        this.imageFormat = imageFormat;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }
}
