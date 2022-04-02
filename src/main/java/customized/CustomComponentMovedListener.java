package customized;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.Timer;

public abstract class CustomComponentMovedListener
        extends ComponentAdapter
        implements ActionListener
{
    private final Timer timer;
    private boolean startedMoving;

    public CustomComponentMovedListener()
    {
        this(200);
    }

    public CustomComponentMovedListener(int delayMS)
    {
        timer = new Timer(delayMS, this);
        timer.setRepeats(false);
        timer.setCoalesce(false);
    }
    
    @Override
    public void componentMoved(ComponentEvent e)
    {
        if(!startedMoving)
        {
            startedMoving = true;
        }
        timer.restart();
    }

    @Override
    public void actionPerformed(ActionEvent e)
    {
        if(startedMoving)
            moveTimedOut();
        
        startedMoving = false;
    }

    public abstract void moveTimedOut();    
    public abstract void startedMoving();
}
