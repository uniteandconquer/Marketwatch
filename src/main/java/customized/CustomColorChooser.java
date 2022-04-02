package customized;

import java.awt.BorderLayout;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import marketwatch.MarketPanel;

public class CustomColorChooser extends JDialog
{
    private JColorChooser jcc = null;
    protected JLabel colorBoxLabel;
    protected MarketPanel marketPanel;
    public long lastClosedTime;

    public CustomColorChooser(MarketPanel marketPanel)
    {
        initializeUI();
        this.marketPanel = marketPanel;
    }

    private void initializeUI()
    {
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setLayout(new BorderLayout());
        setAlwaysOnTop(true);
        
        jcc = new JColorChooser();
        jcc.getSelectionModel().addChangeListener(new ColorSelection());
        getContentPane().add(jcc, BorderLayout.PAGE_START);
        this.pack();
        
        addWindowFocusListener(new WindowFocusListener()
        {
            @Override
            public void windowGainedFocus(WindowEvent e){}

            @Override
            public void windowLostFocus(WindowEvent e)
            {
                //don't close dialog if triggered by JDialog (i.e. chartDialog in ChartWindow.chartmaker)
                //see Chartmaker.setDialogPosition() for related behavior implementation
                if(e.getOppositeWindow() instanceof JDialog)
                    return;
                
                lastClosedTime = System.currentTimeMillis();
                setVisible(false);
            }
        });
    }
    
    public void setColorBoxLabel(JLabel label)
    {
        colorBoxLabel = label;
    }

    /**
     * A ChangeListener implementation for listening the color selection of the
     * JColorChooser component.
     */
    class ColorSelection implements ChangeListener
    {
        @Override
        public void stateChanged(ChangeEvent e)
        {
            colorBoxLabel.setBackground(jcc.getColor());
            marketPanel.changeColor(colorBoxLabel, jcc.getColor());
        }
    }
}
