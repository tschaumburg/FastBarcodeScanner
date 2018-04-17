package dk.schaumburgit.stillsequencecamera.camera2;

import android.media.Image;

import dk.schaumburgit.stillsequencecamera.ISource;

/**
 * Created by Thomas on 12-04-2018.
 */

public class SourceImage implements ISource {
    public SourceImage(Image image) {
    }

    @Override
    public String save() {
        return null;
    }
    
    public void close() {
    }
}
