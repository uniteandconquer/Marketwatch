package customized;

import java.awt.Component;
import java.net.URL;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import marketwatch.NodeInfo;

public class NodeTreeCellRenderer extends DefaultTreeCellRenderer implements TreeCellRenderer
{
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
            boolean leaf, int row, boolean hasFocus)
    {
        try
        {
            JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            Object o = ((DefaultMutableTreeNode) value).getUserObject();
            if (o instanceof NodeInfo)
            {
                NodeInfo nodeInfo = (NodeInfo) o;
                URL imageUrl = nodeInfo.GetIconUrl();
                if (imageUrl != null)
                {
                    label.setIcon(new ImageIcon(imageUrl));
                }
                label.setText(nodeInfo.GetNodeName());
                if (nodeInfo.labelColor != null)
                {
                    label.setOpaque(true);
                    
                    if(selected)
                    {
                        label.setForeground(nodeInfo.bgColor);
                        label.setBackground(nodeInfo.labelColor);
                    }
                    else
                    {
                        label.setForeground(nodeInfo.labelColor);
                        label.setBackground(nodeInfo.bgColor);                        
                    }                        
                }
            }
            else
            {
                label.setIcon(null);
                label.setText(value.toString());
            }
            return label;
        }
        catch (Exception e)
        {
//            BackgroundService.AppendLog("Error creating label for " + value.toString());
            return new JLabel(value.toString());
        }
    }

}//end class NodeTreeCellRenderer    