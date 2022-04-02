package enums;

public enum Extensions 
{
    LAYOUT("layout"),
    STYLE("style"),
    ARRANGE("arng");
 
    private String get;
 
    Extensions(String extension)
    {
        this.get = extension;
    }
 
    public String get() 
    {
        return get;
    }
}