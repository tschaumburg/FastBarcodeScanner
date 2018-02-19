package dk.schaumburgit.fastbarcodescanner;

/**
 * Created by Thomas on 06-02-2018.
 */

public class FilterOptions
{
    public int emptyDeglitchLevel = 5;
    public int errorDeglitchLevel = 0;

    public enum ResultVerbosity { None, First, Changes, Allways}
    public ResultVerbosity resultVerbosity = ResultVerbosity.Changes;

    public enum BlankVerbosity { None, First, Allways}
    public BlankVerbosity blankVerbosity = BlankVerbosity.None;

    public enum ErrorVerbosity { None, First, Allways}
    public ErrorVerbosity errorVerbosity = ErrorVerbosity.First;
}
