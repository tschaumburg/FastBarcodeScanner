package dk.schaumburgit.fastbarcodescanner.callbackmanagers;

/**
 * Created by Thomas on 05-02-2018.
 */

public class CallBackOptions
{
    public final boolean includeImage;

    public enum ResultVerbosity { None, First, Changes, Allways}
    public final ResultVerbosity resultVerbosity;

    public final int blankReluctance;
    public enum BlankVerbosity { None, First, Allways}
    public final BlankVerbosity blankVerbosity;

    public final int errorReluctance;
    public enum ErrorVerbosity { None, First, Allways}
    public final ErrorVerbosity errorVerbosity;

    public CallBackOptions(
            boolean includeImageInCallback,
            ResultVerbosity resultVerbosity,
            int blankReluctance,
            BlankVerbosity blankVerbosity,
            int errorReluctance,
            ErrorVerbosity errorVerbosity
    )
    {
        this.includeImage = includeImageInCallback;
        this.resultVerbosity = resultVerbosity;
        this.blankReluctance = blankReluctance;
        this.blankVerbosity = blankVerbosity;
        this.errorReluctance= errorReluctance;
        this.errorVerbosity = errorVerbosity;

    }
    public CallBackOptions()
    {
        this.includeImage = false;
        this.resultVerbosity = ResultVerbosity.Changes;
        this.blankReluctance = 5;
        this.blankVerbosity = BlankVerbosity.None;
        this.errorReluctance = 0;
        this.errorVerbosity = ErrorVerbosity.First;
    }
    public CallBackOptions clone(boolean includeImage)
    {
        return new CallBackOptions(includeImage, this.resultVerbosity, this.blankReluctance, this.blankVerbosity, this.errorReluctance, this.errorVerbosity);
    }
    public CallBackOptions clone(int blankReluctance, int errorReluctance)
    {
        if (blankReluctance < 0)
            blankReluctance = this.blankReluctance;

        if (errorReluctance < 0)
            blankReluctance = this.errorReluctance;

        return new CallBackOptions(this.includeImage, this.resultVerbosity, blankReluctance, blankVerbosity, this.errorReluctance, this.errorVerbosity);
    }
    public CallBackOptions clone(ResultVerbosity resultVerbosity)
    {
        return new CallBackOptions(this.includeImage, resultVerbosity, this.blankReluctance, this.blankVerbosity, this.errorReluctance, this.errorVerbosity);
    }
    public CallBackOptions clone(BlankVerbosity blankVerbosity)
    {
        return new CallBackOptions(this.includeImage, this.resultVerbosity, this.blankReluctance, blankVerbosity, this.errorReluctance, this.errorVerbosity);
    }
    public CallBackOptions clone(ErrorVerbosity errorVerbosity)
    {
        return new CallBackOptions(this.includeImage, this.resultVerbosity, this.blankReluctance, this.blankVerbosity, this.errorReluctance, errorVerbosity);
    }
}
