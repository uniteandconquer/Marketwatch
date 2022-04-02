package marketwatch;

import java.awt.Color;
import java.net.URL;

public class NodeInfo
{
    public String nodeName;
    public URL iconURL;
    public Color labelColor;
    public Color bgColor;
    
    public NodeInfo(String nodeName, String filename)
    {
        this.nodeName = nodeName;
        iconURL = GUI.class.getClassLoader().getResource("Images/" + filename);
        if (iconURL == null)
        {
            BackgroundService.AppendLog("Couldn't find file: " + filename + " @ public NodeInfo(String,String)");
        }
    }

    public NodeInfo(String nodeName, String filename, Color labelColor)
    {
        this.nodeName = nodeName;
        this.labelColor = labelColor;
        iconURL = GUI.class.getClassLoader().getResource("Images/" + filename);
        if (iconURL == null)
        {
            BackgroundService.AppendLog("Couldn't find file: " + filename + " @ public NodeInfo(String,String,Color)");
        }
    }

    public String GetNodeName()
    {
        return nodeName;
    }

    public URL GetIconUrl()
    {
        return iconURL;
    }

    public void SetIconName(String filename)
    {
        iconURL = GUI.class.getClassLoader().getResource("Images/" + filename);
        if (iconURL == null)
        {
            BackgroundService.AppendLog("Couldn't find file: " + filename + " @ public NodeInfo(String)");
        }
    } 
    
    public void setForeground(Color color)
    {
        labelColor = color;
    }
    
    public void setBackground(Color color)
    {
        bgColor = color;
    }

    @Override
    public String toString()
    {
        return nodeName;
    }
}//end class NodeInfo  
