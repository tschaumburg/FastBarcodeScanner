package dk.schaumburgit.fastbarcodescanner;

/**
 * Created by Thomas on 05-02-2018.
 */

public class CallBackOptions
{
    public final boolean includeImage;

    public enum ResultVerbosity { None, First, Changes, Allways}
    public ResultVerbosity resultVerbosity = ResultVerbosity.Changes;

    public int blankReluctance = 5;
    public enum BlankVerbosity { None, First, Allways}
    public BlankVerbosity blankVerbosity = BlankVerbosity.None;

    public int errorReluctance = 0;
    public enum ErrorVerbosity { None, First, Allways}
    public ErrorVerbosity errorVerbosity = ErrorVerbosity.First;

    public CallBackOptions(boolean includeImageInCallback)
    {
        this.includeImage = includeImageInCallback;

    }
    public CallBackOptions()
    {
        this.includeImage = false;

    }
}
