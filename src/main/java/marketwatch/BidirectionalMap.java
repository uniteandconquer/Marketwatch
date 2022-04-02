package marketwatch;

import java.util.ArrayList;

/**A simple class that allows the lookup of 2 key/value pairs bi-directionally.<br>
 Using duplicate key or value entries may result in inaccurate look-ups*/
public class BidirectionalMap
{
    private final ArrayList<String> list1;
    private final ArrayList<String> list2;
    
    public BidirectionalMap(ArrayList<String> list1, ArrayList<String> list2)
    {
        this.list1 = list1;
        this.list2 = list2;
    }
    
    /**Gets the corresponding value for this pair
     * @param key
     * @return the value corresponding to this key*/
     public String get(String key)
    {
        if(list1.contains(key))
            return list2.get(list1.indexOf(key));
        else
            return list1.get(list2.indexOf(key));
    }
     
     public int getSize()
     {
         return list1.size();
     }
}
