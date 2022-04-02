package marketwatch;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.TitledBorder;

public class ChartItem extends javax.swing.JPanel
{
    private boolean dragging;
    private int xAnchor;
    private int yAnchor;
    protected String title;
    private final ChartItem thisItem;
    protected ImageIcon icon1;
    protected ImageIcon icon2;
    protected boolean isSelected;
    private MarketPanel marketPanel;
    protected Color bgColor;
    protected Color selectedColor;
    protected Color highlightColor;
    protected Color borderColor;
    protected Color borderColorSelected;
    protected Font font;
    
    
    public ChartItem()
    {
        initComponents();
        thisItem = this;
    }
    
    public void initialise(GUI gui,String title,ImageIcon icon1,ImageIcon icon2,boolean isTrade)
    {
        marketPanel = gui.marketPanel;
        this.icon1 = icon1;
        this.icon2 = icon2;
        if(isTrade)
            middleLabel.setIcon(new ImageIcon(getClass().getResource("/Images/trade.png")));
        bgColor = getBackground();
        highlightColor = bgColor.brighter().brighter();
        selectedColor = highlightColor.brighter().brighter();
        
        SwingUtilities.invokeLater(()->
        {
            icon1Label.setText("");
            icon2Label.setText("");
            icon1Label.setIcon(icon1);
            icon2Label.setIcon(icon2);
            CompoundBorder border = (CompoundBorder)getBorder();
            var titleBorder = (TitledBorder) border.getOutsideBorder();
            titleBorder.setTitle(title); 
            this.title = title;
        });
            
        
        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {     
                xAnchor =  e.getX();
                yAnchor =  e.getY();  
                
                SwingUtilities.invokeLater(() ->
                {
                    marketPanel.draggedItem = new DraggedItem(gui, false,thisItem);
                    marketPanel.draggedItem.setLocation(e.getXOnScreen() - xAnchor, e.getYOnScreen() - yAnchor);
                    marketPanel.draggedItem.pack();
                    marketPanel.draggedItem.setVisible(true);
                    dragging = true;
                });
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {   
                //check for drag & drop
                if(desktopContainsItem())
                {
                    Point point = new Point
                    (
                        marketPanel.draggedItem.getLocationOnScreen().x - marketPanel.desktopPane.getLocationOnScreen().x, 
                        marketPanel.draggedItem.getLocationOnScreen().y - marketPanel.desktopPane.getLocationOnScreen().y
                    );

                    if (marketPanel.addNewChart(title,point,"CurrentLayout"))
                    {
                        CompoundBorder border = (CompoundBorder) getBorder();
                        var titleBorder = (TitledBorder) border.getInsideBorder();
                        titleBorder.setTitle("");
                        isSelected = false;
                        setBackground(bgColor);
                        setVisible(false);
                    }                        
                }
                else
                {
                    //check for single click (selection)
                    if(!isSelected)
                    {                    
                        isSelected = true;
                        setBackground(selectedColor);
                        CompoundBorder border = (CompoundBorder)getBorder();
                        var titleBorder = (TitledBorder) border.getInsideBorder();
                        titleBorder.setTitle("Click again or drag & drop  ");     
                        updateItemUI();                      
                    }
                    //check for double click (add chart) (only if 2nd click is within a second from 1st click)
                    else
                    {      
                        if(marketPanel.addNewChart(title,null,"CurrentLayout"))
                        {
                            CompoundBorder border = (CompoundBorder)getBorder();
                            var titleBorder = (TitledBorder) border.getInsideBorder();
                            titleBorder.setTitle(""); 
                            isSelected = false;
                            setBackground(bgColor);
                            setVisible(false);
                        }
                    }   
                }                                
                
                ChartItem item;
                for(Component c : marketPanel.chartsList.getComponents())
                {
                    item = (ChartItem) c;
                    
                    if(item == thisItem)
                        continue;                    
                    
                    if(item.isSelected)
                    {
                        CompoundBorder border = (CompoundBorder)item.getBorder();
                        var titleBorder = (TitledBorder) border.getInsideBorder();
                        titleBorder.setTitle(""); 
                        item.isSelected = false;
                        item.updateItemUI();
                    }
                }
                
                marketPanel.draggedItem.setVisible(false);
                dragging = false;                        
            }            
            @Override
            public void mouseEntered(MouseEvent e)
            {
                if(!isSelected)
                    setBackground(highlightColor);
            }
            @Override
            public void mouseExited(MouseEvent e)
            {
                if(!isSelected)
                    setBackground(bgColor);
            }
        });
        
        addMouseMotionListener(new MouseAdapter()
        {
            @Override
            public void mouseDragged(MouseEvent e)
            {
                if (dragging)
                {
                    //When user drags the item, we want to de-select it. On mouse release it will be
                    //set to selected again, but this way if the user does not drop the item inside the desktop
                    //a chart will not be created if the item was already set to selected before.
                    isSelected = false;
                    
                    marketPanel.draggedItem.setLocation(e.getXOnScreen() - xAnchor, e.getYOnScreen() - yAnchor);
                }                
            }
        });
    }
    
    protected void updateItemUI()
     {            
         var cBorder = (CompoundBorder) getBorder();
         var outsideBorder = (TitledBorder) cBorder.getOutsideBorder();

         //Setting the font on select/deselect of a ChartItem is not neccesary, but will probably not
         //happen very often during runtime. Not so much as to warrant instructions to skip in those cases
         Font newFont = new Font(marketPanel.fontsBox.getSelectedItem().toString(),
                 Font.PLAIN, outsideBorder.getTitleFont().getSize());

         font = newFont;
         outsideBorder.setTitleFont(font);

         var matteBorder = (MatteBorder) outsideBorder.getBorder();
         var insideBorder = (TitledBorder) cBorder.getInsideBorder();

         insideBorder.setTitleFont(font);

         outsideBorder.setTitleColor(isSelected ? borderColorSelected : borderColor);
         outsideBorder.setBorder(new MatteBorder(matteBorder.getBorderInsets(), isSelected ? borderColorSelected : borderColor));
         insideBorder.setTitleColor(isSelected ? borderColorSelected : borderColor);
         setBackground(isSelected ? selectedColor : bgColor);
         repaint();
     }
    
    private boolean desktopContainsItem()
    {
        try
        {
            Dimension desktop = marketPanel.desktopPane.getSize();
            Point desktopPoint = marketPanel.desktopPane.getLocationOnScreen();
            Point itemPoint = marketPanel.draggedItem.getLocationOnScreen();

            boolean containsX = desktopPoint.x <= itemPoint.x && desktopPoint.x + desktop.width >= itemPoint.x;
            boolean containsY = desktopPoint.y <= itemPoint.y && desktopPoint.y + desktop.height >= itemPoint.y;

            return containsX && containsY;            
        }
        catch (IllegalStateException e)
        {
            //in some rare cases the location on screen getter throws an illegal state exception
            //we must catch it and set the dragged item to not visible
            BackgroundService.AppendLog(e);
            marketPanel.draggedItem.setVisible(false);
            return false;
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {

        icon1Label = new javax.swing.JLabel();
        middleLabel = new javax.swing.JLabel();
        icon2Label = new javax.swing.JLabel();

        setBackground(new java.awt.Color(3, 32, 12));
        setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createMatteBorder(2, 4, 2, 4, new java.awt.Color(204, 204, 0)), "", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.TOP, new java.awt.Font("Bahnschrift", 1, 12), new java.awt.Color(203, 203, 1)), javax.swing.BorderFactory.createTitledBorder(null, "", javax.swing.border.TitledBorder.CENTER, javax.swing.border.TitledBorder.BOTTOM, new java.awt.Font("Bahnschrift", 0, 12), new java.awt.Color(203, 203, 1)))); // NOI18N
        setLayout(new java.awt.GridLayout(1, 3));

        icon1Label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        icon1Label.setMaximumSize(new java.awt.Dimension(37, 50));
        icon1Label.setMinimumSize(new java.awt.Dimension(37, 50));
        icon1Label.setPreferredSize(new java.awt.Dimension(37, 50));
        add(icon1Label);

        middleLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        middleLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/item_arrow.png"))); // NOI18N
        add(middleLabel);

        icon2Label.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        add(icon2Label);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    protected javax.swing.JLabel icon1Label;
    private javax.swing.JLabel icon2Label;
    private javax.swing.JLabel middleLabel;
    // End of variables declaration//GEN-END:variables
}
