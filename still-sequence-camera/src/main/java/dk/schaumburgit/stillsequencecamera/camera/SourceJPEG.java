package dk.schaumburgit.stillsequencecamera.camera;

import dk.schaumburgit.stillsequencecamera.ISource;

/**
 * Created by Thomas on 12-04-2018.
 */

public class SourceJPEG implements ISource {
    public SourceJPEG(byte[] jpegData, int width, int height) {
    }

    @Override
    public String save() {
        return null;
    }

    @Override
    public void close() {

    }
}
