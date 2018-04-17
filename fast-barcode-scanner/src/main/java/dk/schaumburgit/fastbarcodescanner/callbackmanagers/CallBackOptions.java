package dk.schaumburgit.fastbarcodescanner.callbackmanagers;

/**
 * Created by Thomas on 05-02-2018.
 */

public class CallBackOptions
{
    public final boolean includeImage;

    public final EventConflation conflateHits;
    public final EventConflation conflateBlanks;
    public final EventConflation conflateErrors;

    public final int debounceBlanks;
    public final int debounceErrors;

    public CallBackOptions(
            boolean includeImageInCallback,
            EventConflation conflateHits,
            int debounceBlanks,
            EventConflation conflateBlanks,
            int debounceErrors,
            EventConflation conflateErrors
    )
    {
        this.includeImage = includeImageInCallback;
        this.debounceBlanks = debounceBlanks;
        this.debounceErrors = debounceErrors;
        this.conflateHits = conflateHits;
        this.conflateBlanks = conflateBlanks;
        this.conflateErrors = conflateErrors;

    }
    public CallBackOptions()
    {
        this.includeImage = true;//false;
        this.conflateHits = EventConflation.Changes;
        this.debounceBlanks = 5;
        this.conflateBlanks = EventConflation.None;
        this.debounceErrors = 0;
        this.conflateErrors = EventConflation.First;
    }
    public CallBackOptions clone(boolean includeImage)
    {
        return new CallBackOptions(includeImage, this.conflateHits, this.debounceBlanks, this.conflateBlanks, this.debounceErrors, this.conflateErrors);
    }
    public CallBackOptions clone(int debounceBlanks, int debounceErrors)
    {
        if (debounceBlanks < 0)
            debounceBlanks = this.debounceBlanks;

        if (debounceErrors < 0)
            debounceErrors = this.debounceErrors;

        return new CallBackOptions(this.includeImage, this.conflateHits, debounceBlanks, this.conflateBlanks, debounceErrors, this.conflateErrors);
    }
    public CallBackOptions clone(EventConflation conflateHits, EventConflation conflateBlanks, EventConflation conflateErrors)
    {
        if (conflateHits == null)
            conflateHits = this.conflateHits;

        if (conflateBlanks == null)
            conflateBlanks = this.conflateBlanks;

        if (conflateErrors == null)
            conflateErrors = this.conflateErrors;

        return new CallBackOptions(this.includeImage, conflateHits, this.debounceBlanks, conflateBlanks, this.debounceErrors, conflateErrors);
    }
}
