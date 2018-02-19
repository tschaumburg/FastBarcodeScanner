package dk.schaumburgit.fastbarcodescanner;

/**
 * Created by Thomas on 18-02-2018.
 */


public interface ErrorDetectedListener {
    void onError(Exception error);
}
