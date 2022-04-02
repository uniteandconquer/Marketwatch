package customized;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.Timer;

public abstract class CustomComponentResizedListener
        extends ComponentAdapter
        implements ActionListener
{
    private final Timer timer;
    private boolean startedResizing;

    public CustomComponentResizedListener()
    {
        this(200);
    }

    public CustomComponentResizedListener(int delayMS)
    {
        timer = new Timer(delayMS, this);
        timer.setRepeats(false);
        timer.setCoalesce(false);
    }

    @Override
    public void componentResized(ComponentEvent e)
    {
        if(!startedResizing)
        {
            startedResizing = true;
            startedResizing();
        }
        timer.restart();
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {        
        if(startedResizing)
            resizeTimedOut();
        
        startedResizing = false;
    }

    public abstract void resizeTimedOut();
    public abstract void startedResizing();
}
