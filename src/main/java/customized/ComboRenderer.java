package customized;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicComboBoxRenderer;

/**Used for rendering system fonts in a combo box*/
public class ComboRenderer extends BasicComboBoxRenderer
{
    public JComboBox comboBox;
    final DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();
    private int row;
    public Color bgColor = Color.white;
    public Color fgColor = Color.black;
    Dimension prefferedSize = new Dimension(125, 20);

    public ComboRenderer(JComboBox fontsBox)
    {
        comboBox = fontsBox;
    }

    private void manItemInCombo()
    {
        if (comboBox.getItemCount() > 0)
        {
            final Object comp = comboBox.getUI().getAccessibleChild(comboBox, 0);
//            setHorizontalAlignment(SwingConstants.LEFT);
            if ((comp instanceof JPopupMenu))
            {
                final JList list = new JList(comboBox.getModel());
                final JPopupMenu popup = (JPopupMenu) comp;
                final JScrollPane scrollPane = (JScrollPane) popup.getComponent(0);
                final JViewport viewport = scrollPane.getViewport();
                final Rectangle rect = popup.getVisibleRect();
                final Point pt = viewport.getViewPosition();
                row = list.locationToIndex(pt);
            }
        }
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
    {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (list.getModel().getSize() > 0)
        {
            manItemInCombo();
        }
        final JLabel renderer = (JLabel) defaultRenderer.getListCellRendererComponent(list, value, row, isSelected, cellHasFocus);
        
        //the item in the box has index -1 -> items in the pop-up list have indices 0 and above
        if(index == -1)
        {
            setHorizontalAlignment(SwingConstants.CENTER);
            setPreferredSize(prefferedSize);
            setMaximumSize(prefferedSize);
        }
        else
            setHorizontalAlignment(SwingConstants.LEFT);
        
        setBackground(bgColor);
        setForeground(fgColor);
        
        final Object fntObj = value;
        final String fontFamilyName = (String) fntObj;
        setFont(new Font(fontFamilyName, isSelected ? Font.BOLD : Font.PLAIN, isSelected ? 14 : 12));
        return this;
    }
}
