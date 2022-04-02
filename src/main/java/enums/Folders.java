package enums;

public enum Folders 
{
    DB("databases"),
    LAYOUTS("UI/layouts"),
    ARRANGE("UI/arrangements"),
    STYLES("UI/styles");    
 
    private String get;
 
    Folders(String folder) 
    {
        this.get = folder;
    }
 
    public String get()
    {
        return get;
    }
}