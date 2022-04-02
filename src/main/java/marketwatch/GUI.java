package marketwatch;

import enums.Extensions;
import enums.Folders;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.beans.PropertyVetoException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.sql.Connection;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.border.BevelBorder;
import javax.swing.border.LineBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.json.JSONException;
import org.json.JSONObject;

public class GUI extends javax.swing.JFrame
{
    protected final BackgroundService backgroundService;
    protected final DatabaseManager dbManager;    
    private String currentCard = "mintingMonitor"; 
    private String lastLNF;
    
    public GUI(BackgroundService bgs)
    {              
        backgroundService = bgs;
        dbManager = bgs.dbManager;
        setLastLookAndFeel();//do this before initComponents() & after setting dbManager
        initComponents();
        nodeMonitorPanel.CreateMonitorTree();
        InitTaskbar();  
        initFrame();    
        System.gc();  
        
    }//end constructor
    
    private void initFrame()
    {
        //put the frame at middle of the screen,add icon and set visible
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        URL imageURL = GUI.class.getClassLoader().getResource("Images/icon.png");
        Image icon = Toolkit.getDefaultToolkit().getImage(imageURL);
        setTitle(BackgroundService.BUILDVERSION);
        setIconImage(icon);        
        setLocation(dim.width / 2 - getSize().width / 2, dim.height / 2 - getSize().height / 2);
        marketPanel.initCharts();
        marketPanel.updateArrangementsList();
        marketPanel.updateLayoutsList(false);
        marketPanel.updateStylesList(false);
        marketPanel.updateGuiItems();
        setVisible(true);
        BackgroundService.SPLASH.setVisible(false);  
        //after enabling gui, seems internal frame will not be selected if jframe is not visible
        //invoke later to ensure chartWindowFocused will be set to true (is set to false in resizeChartWindows 
        //which is also invoked later (will override the selectClosestFrame setting of that flag)
        SwingUtilities.invokeLater(()->{marketPanel.selectClosestFrame();});
        //MUST be called before initUpdateTimer
        getSettings();    
        marketPanel.initUpdateTimer();
    }
    
    private void InitTaskbar()
    {           
        for (LookAndFeelInfo LFI : UIManager.getInstalledLookAndFeels())
        {
            JRadioButtonMenuItem radioButtonMenuItem = new JRadioButtonMenuItem(LFI.getName());
            if(LFI.getName().equals(lastLNF))
                radioButtonMenuItem.setSelected(true); 
                
            radioButtonMenuItem.addActionListener((ActionEvent e) ->
            {     
                appearanceMenu.setVisible(false);
                setLookAndFeel(e.getActionCommand());
            });
            appearanceGroup.add(radioButtonMenuItem);
            appearanceMenu.add(radioButtonMenuItem);
        }
    }
    
    private void getSettings()
    {        
        File settingsFile = new File(System.getProperty("user.dir") + "/bin/settings.json");
        if(!settingsFile.exists())
            createSettingsFile(settingsFile);
        
        try
        {
            String jsonString = Files.readString(settingsFile.toPath());
            if(jsonString != null)
            {
                int newLoginCount;

                JSONObject jsonObject = new JSONObject(jsonString);

                String dismissed = jsonObject.optString("donateDismissed");
                if(dismissed.isBlank())
                    jsonObject.put("donateDismissed", "false");  

                String loginCount = jsonObject.optString("loginCount");
                if(loginCount.isBlank())
                {
                    jsonObject.put("loginCount", "1");
                    newLoginCount = 1;
                }
                else
                {
                    newLoginCount = 1 + Integer.parseInt(loginCount);
                    jsonObject.put("loginCount", String.valueOf(newLoginCount));                      
                }   
                
                String autoUpdate = jsonObject.optString("autoUpdate");
                if(autoUpdate.isBlank())
                {
                    jsonObject.put("autoUpdate", "true");
                    marketPanel.autoUpdateCheckbox.setSelected(true);
                }  
                else
                {
                    boolean isEnabled = Boolean.parseBoolean(autoUpdate);
                    marketPanel.autoUpdateCheckbox.setSelected(isEnabled);
                }

                //MUST write to json before opening (modal) dialog, otherwise it will overwrite
                //the user's dismiss donate pref after clicking dismissButton
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(settingsFile)))
                {
                    writer.write(jsonObject.toString(1));
                    writer.close();
                }                          
                if(dismissed.equals("false") && newLoginCount % 20 == 0)
                {
                    donateDialog.pack();
                    int x = getX() + ((getWidth() / 2) - (donateDialog.getWidth() / 2));
                    int y = getY() + ((getHeight() / 2) - (donateDialog.getHeight() / 2));
                    donateDialog.setLocation(x, y);
                    donateDialog.setVisible(true);   
                }  
            }                
            }
            catch (IOException | JSONException e)
            {
                BackgroundService.AppendLog(e);
            }
    }
    
    private void createSettingsFile(File settingsFile)
    {
        try
        {                    
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("donateDismissed", "false");  
            jsonObject.put("loginCount", "0"); 
            jsonObject.put(("autoUpdate"), "true");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(settingsFile)))
            {
                writer.write(jsonObject.toString(1));
                writer.close();
            }                   
        }
        catch (IOException | JSONException e)
        {
            BackgroundService.AppendLog(e);
        }
    }
     
    protected void ShowLoadScreen()
    {      
        //setting the label to visible will make the logo jump up. Label extractTransactions text is 3 line breaks.
        statusLabel.setText(Utilities.AllignCenterHTML(Main.BUNDLE.getString("loginSuccess")));
        CardLayout card = (CardLayout) mainPanel.getLayout();
        card.show(mainPanel, "splashPanel");
    }
    
    protected void showDonateDialog()
    {
        SwingUtilities.invokeLater(()->
        {
            donateDialog.pack();
            int x = getX() + ((getWidth() / 2) - (donateDialog.getWidth() / 2));
            int y = getY() + ((getHeight() / 2) - (donateDialog.getHeight() / 2));
            donateDialog.setLocation(x, y);
            donateDialog.setVisible(true);   
        });
    }
    
    protected void updateStyle(Color bgColor, Color cmpColor, Color fontColor, Color cmpFontColor, String fontType)
    {
        toolbar.setBackground(bgColor);

        for (Component c : toolbar.getComponents())
        {
            if (c instanceof JButton)
            {
                c.setBackground(cmpColor);
                c.setFont(new Font(fontType, c.getFont().getStyle(), c.getFont().getSize()));
                c.setForeground(cmpFontColor);
            }
        }

        JPanel monitorPanel = (JPanel) nodeMonitorPanel.getComponent(0);
        monitorPanel.setBackground(bgColor);
        for (Component c : monitorPanel.getComponents())
        {
            if (c instanceof JScrollPane)
            {
                //Jtree is child of viewport
                JViewport viewPort = (JViewport) ((JScrollPane) c).getComponent(0);
                JTree tree = (JTree) viewPort.getComponent(0);
                tree.setBackground(bgColor);
                tree.setFont(new Font(fontType, c.getFont().getStyle(), c.getFont().getSize()));
                
                //We have to set the foreground color for the label of every node's NodeInfo (userObject)
                var treeModel = (DefaultTreeModel) tree.getModel();
                var node = (DefaultMutableTreeNode) treeModel.getRoot();
                var nodeInfo = (NodeInfo) node.getUserObject();
                nodeInfo.setForeground(fontColor);
                for (int currentChild = 0; currentChild < node.getChildCount(); currentChild++)
                {
                    var child = (DefaultMutableTreeNode) node.getChildAt(currentChild);
                    nodeInfo = (NodeInfo) child.getUserObject();
                    nodeInfo.setForeground(fontColor);

                    for (int currentGrandChild = 0; currentGrandChild < child.getChildCount(); currentGrandChild++)
                    {
                        var grandChild = (DefaultMutableTreeNode) child.getChildAt(currentGrandChild);
                        nodeInfo = (NodeInfo) grandChild.getUserObject();
                        nodeInfo.setForeground(fontColor);
                        nodeInfo.setBackground(bgColor);
                    }
                }
            }

            if (c instanceof JLabel)
            {
                c.setFont(new Font(fontType, c.getFont().getStyle(), c.getFont().getSize()));
                c.setForeground(fontColor);
            }
            if (c instanceof JButton)
            {
                c.setBackground(cmpColor);
                c.setFont(new Font(fontType, c.getFont().getStyle(), c.getFont().getSize()));
                c.setForeground(cmpFontColor);
            }
        }

        tipJarPanel.setBackground(bgColor);
        for (Component c : tipJarPanel.getComponents())
        {
            if (c instanceof JLabel)
            {
                c.setFont(new Font(fontType, c.getFont().getStyle(), c.getFont().getSize()));
                c.setForeground(fontColor);
            }
            if (c instanceof JTextField)
            {
                c.setBackground(cmpColor);
                c.setFont(new Font(fontType, c.getFont().getStyle(), c.getFont().getSize()));
                c.setForeground(cmpFontColor);
            }
        }

        //setting popupmenu bg color doesn't work, using a lineborder removes the insets with default color
        appearanceMenu.setBorder(BorderFactory.createLineBorder(bgColor));
        appearanceMenu.setBackground(bgColor);
        for (Component c : appearanceMenu.getComponents())
        {

            if (c instanceof JRadioButtonMenuItem)
            {
                c.setBackground(cmpColor);
                ((JRadioButtonMenuItem) c).setOpaque(true);
                c.setFont(new Font(fontType, c.getFont().getStyle(), c.getFont().getSize()));
                c.setForeground(cmpFontColor);
            }
        }
        
        donatePanel.setBackground(bgColor);
        LineBorder lineBorder =  new LineBorder(cmpColor, 5, true); 
        donatePanel.setBorder(BorderFactory.createCompoundBorder(
                lineBorder, BorderFactory.createBevelBorder(BevelBorder.LOWERED)));
        walletsButton.setBackground(cmpColor);
        walletsButton.setForeground(cmpFontColor);
        remindLaterButton.setBackground(cmpColor);
        remindLaterButton.setForeground(cmpFontColor);
        dismissButton.setBackground(cmpColor);
        dismissButton.setForeground(cmpFontColor);
        donateLabel.setForeground(fontColor);        
        
    }
    
    protected void ExpandTree(JTree tree, int nodeLevel)
    {
        var currentNode = (DefaultMutableTreeNode) tree.getModel().getRoot();        
        
        do
        {    
            if (currentNode.getLevel() == nodeLevel) 
            {
                tree.expandPath(new TreePath(currentNode.getPath()));
            }
            
            currentNode = currentNode.getNextNode();
        } 
        while (currentNode != null);
    }
    
    protected void ExpandNode(JTree tree, DefaultMutableTreeNode currentNode,int nodeLevel)
    {        
        DefaultMutableTreeNode original = currentNode;
        do
        {
            if (currentNode.getLevel() == nodeLevel) 
                tree.expandPath(new TreePath(currentNode.getPath()));
            
            currentNode = currentNode.getNextNode().isNodeAncestor(original) ? currentNode.getNextNode() : null;            
        } 
        while (currentNode != null);
    }
    
    private void setLastLookAndFeel()
    {
        try(Connection connection = ConnectionDB.getConnection("CurrentLayout", Folders.LAYOUTS.get(), Extensions.LAYOUT))
        {
            if(dbManager.TableExists("gui", connection))
            {
                Object lastSetting = dbManager.GetFirstItem("gui", "look_and_feel", connection);
                if(lastSetting != null)
                {
                    lastLNF = (String) lastSetting;
                    setLookAndFeel((String)lastSetting);
                }
            }
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }

    private void setLookAndFeel(String styleString)
    {
        try
        {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels())
            {
                if (styleString.equals(info.getName()))
                {
                    //in case nimbus dark mode button text is not visible
//                    if(styleString.equals("Nimbus"))
//                        UIManager.getLookAndFeelDefaults().put("Button.textForeground", Color.BLACK);  
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    SwingUtilities.updateComponentTreeUI(this);
                    if(appearanceMenu != null)
                        SwingUtilities.updateComponentTreeUI(appearanceMenu);
                    if(marketPanel != null)
                    {
                        SwingUtilities.updateComponentTreeUI(marketPanel.colorChooser);
                        marketPanel.updateGuiItems();//style and layout menu size needs update for diff L&F
                        marketPanel.saveLayout("CurrentLayout", null, false, true);
                    }
                    break;
                }
            }
        }
        catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex)
        {
            java.util.logging.Logger.getLogger(GUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
    }
    
    protected void exitInitiated(boolean isWindowEvent)
    {                 
        if(isWindowEvent)
        {
            backgroundService.SetGUIEnabled(false);
        }
        else
        {
            if (BackgroundService.ISMAPPING)
            {
                if (JOptionPane.showConfirmDialog(
                        this,
                        Utilities.AllignCenterHTML(Main.BUNDLE.getString("exitConfirm")),
                        Main.BUNDLE.getString("exitConfirmTitle"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.PLAIN_MESSAGE) == JOptionPane.YES_OPTION)
                {
                    //Reserved in case run in background gets implemented later
                }
            }
            else
                exitConfirmed();
        }
    }
    
    protected void exitConfirmed()
    {
        setVisible(false);
        
        //We want to de-maximize any frames before exiting, otherwise the layout will be 
        //incorect on the next start-up.  
        Thread thread = new Thread(()->
        {
            try
            {
                boolean isMaximized = false;
                for (JInternalFrame frame : marketPanel.desktopPane.getAllFrames())
                {
                    if (frame.isMaximum())
                    {
                        isMaximized = true;
                        SwingUtilities.invokeLater(()->
                        {
                            try
                            {
                                frame.setMaximum(false);                                
                            }
                            catch (PropertyVetoException e)
                            {
                                BackgroundService.AppendLog(e);
                            }                            
                        });
                        break;
                    }
                }
                if (isMaximized)
                    Thread.sleep(1000);
                
                //If an update is in progress we abort it and for 30 seconds check every second whether
                //the update was finished, it takes some time to abort the update after lookup_halted is set to true
                if(marketPanel.updateInProgress)
                {
                    TradeExtractor.LOOKUP_HALTED = true;                    
                    
                    for(int i = 0; i < 30; i++)
                    {
                        if(!marketPanel.updateInProgress)
                            break;
                        
                        Thread.sleep(1000);
                    }
                }
                
                //Untill this point all changes to arrangement were saved in memory. 
                //On exit we want to save the current arrangement and layout
                marketPanel.saveLayout("CurrentLayout", null, true, false);
                
                //Extra buffer to make sure db stays open during saveLayout
                Thread.sleep(1000);
                
                System.exit(0);
            }
            catch (InterruptedException e)
            {
                BackgroundService.AppendLog(e);
            }            
        });
        
        thread.start();              
    }
    
    private void pasteToLabel(String coin)
    {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable t = clipboard.getContents(this);
        if (t == null)
            return;
        try
        {
            clipboardLabel.setText(coin + " address copied to clipboard: " + (String) t.getTransferData(DataFlavor.stringFlavor));
        }
        catch (UnsupportedFlavorException | IOException e)
        {
            BackgroundService.AppendLog(e);
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
        java.awt.GridBagConstraints gridBagConstraints;

        appearanceGroup = new javax.swing.ButtonGroup();
        appearanceMenu = new javax.swing.JPopupMenu();
        trayPopup = new javax.swing.JDialog();
        popUpLabel = new javax.swing.JLabel();
        donateDialog = new javax.swing.JDialog();
        donatePanel = new javax.swing.JPanel();
        donateLabel = new javax.swing.JLabel();
        walletsButton = new javax.swing.JButton();
        remindLaterButton = new javax.swing.JButton();
        dismissButton = new javax.swing.JButton();
        mainPanel = new javax.swing.JPanel();
        marketPanel = new marketwatch.MarketPanel();
        marketPanel.initialise(dbManager,this);
        splashPanel = new javax.swing.JPanel();
        statusLabel = new javax.swing.JLabel();
        nodeMonitorPanel = new marketwatch.MonitorPanel();
        nodeMonitorPanel.Initialise(this);
        tipJarScrollPane = new javax.swing.JScrollPane();
        tipJarScrollPane.getVerticalScrollBar().setUnitIncrement(10);
        tipJarPanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        btcField = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        dogeField = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        ltcField = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        qortField = new javax.swing.JTextField();
        jSeparator1 = new javax.swing.JSeparator();
        jSeparator2 = new javax.swing.JSeparator();
        jSeparator3 = new javax.swing.JSeparator();
        clipboardLabel = new javax.swing.JLabel();
        toolbar = new javax.swing.JPanel();
        marketButton = new javax.swing.JButton();
        nodeMonitorButton = new javax.swing.JButton();
        appearanceButton = new javax.swing.JButton();
        donateButton = new javax.swing.JButton();
        exitButton = new javax.swing.JButton();

        appearanceMenu.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseExited(java.awt.event.MouseEvent evt)
            {
                appearanceMenuMouseExited(evt);
            }
        });

        trayPopup.setUndecorated(true);

        popUpLabel.setBackground(new java.awt.Color(204, 202, 202));
        popUpLabel.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        popUpLabel.setForeground(new java.awt.Color(0, 0, 0));
        popUpLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        popUpLabel.setText("<html><div style='text-align: center;'>Mapping session in progress<br/>MintMeister is running in the background<br/><br/> Double click on the system tray icon to open the UI<br/><br/> To exit the program, click 'Exit' in the menu bar<br/> You can also right click the system tray icon and click 'Exit'</div><html>");
        popUpLabel.setBorder(javax.swing.BorderFactory.createCompoundBorder(new javax.swing.border.LineBorder(new java.awt.Color(49, 0, 0), 4, true), new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.LOWERED, new java.awt.Color(54, 56, 72), new java.awt.Color(84, 55, 55), new java.awt.Color(58, 77, 96), new java.awt.Color(72, 50, 50))));
        popUpLabel.setOpaque(true);
        popUpLabel.setPreferredSize(new java.awt.Dimension(380, 120));
        popUpLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseClicked(java.awt.event.MouseEvent evt)
            {
                popUpLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout trayPopupLayout = new javax.swing.GroupLayout(trayPopup.getContentPane());
        trayPopup.getContentPane().setLayout(trayPopupLayout);
        trayPopupLayout.setHorizontalGroup(
            trayPopupLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(popUpLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 417, Short.MAX_VALUE)
        );
        trayPopupLayout.setVerticalGroup(
            trayPopupLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(popUpLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 177, Short.MAX_VALUE)
        );

        donateDialog.setModal(true);
        donateDialog.setUndecorated(true);
        donateDialog.setResizable(false);

        donatePanel.setBorder(javax.swing.BorderFactory.createCompoundBorder(new javax.swing.border.LineBorder(new java.awt.Color(22, 162, 22), 5, true), javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED)));
        java.awt.GridBagLayout donatePanelLayout = new java.awt.GridBagLayout();
        donatePanelLayout.columnWidths = new int[] {0};
        donatePanelLayout.rowHeights = new int[] {0, 12, 0, 12, 0, 12, 0, 12, 0, 12, 0, 12, 0};
        donatePanel.setLayout(donatePanelLayout);

        donateLabel.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
        donateLabel.setText("<html><div style='text-align: center;'>Enjoying Marketwatch?<br/><br/>\n\nPlease consider supporting the creation and maintenance of<br/>\nmore Qortal apps by sending a tip to one of my Qortal wallets.<br/><br/>\n\nYou can find the wallet addresses on the wallets page.</div><html>");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        donatePanel.add(donateLabel, gridBagConstraints);

        walletsButton.setFont(new java.awt.Font("Bahnschrift", 0, 12)); // NOI18N
        walletsButton.setText("Go to wallets page");
        walletsButton.setPreferredSize(new java.awt.Dimension(150, 45));
        walletsButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                walletsButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        donatePanel.add(walletsButton, gridBagConstraints);

        remindLaterButton.setFont(new java.awt.Font("Bahnschrift", 0, 12)); // NOI18N
        remindLaterButton.setText("Remind me later");
        remindLaterButton.setMinimumSize(new java.awt.Dimension(122, 22));
        remindLaterButton.setPreferredSize(new java.awt.Dimension(150, 45));
        remindLaterButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                remindLaterButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        donatePanel.add(remindLaterButton, gridBagConstraints);

        dismissButton.setFont(new java.awt.Font("Bahnschrift", 0, 12)); // NOI18N
        dismissButton.setText("<html><div style='text-align: center;'>No thanks<br/>Don't show again</div><html>");
        dismissButton.setActionCommand("<html><div style='text-align: center;'>No thanks<br/>Don't show again</div><html>");
        dismissButton.setPreferredSize(new java.awt.Dimension(150, 45));
        dismissButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                dismissButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        donatePanel.add(dismissButton, gridBagConstraints);

        javax.swing.GroupLayout donateDialogLayout = new javax.swing.GroupLayout(donateDialog.getContentPane());
        donateDialog.getContentPane().setLayout(donateDialogLayout);
        donateDialogLayout.setHorizontalGroup(
            donateDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 478, Short.MAX_VALUE)
            .addGroup(donateDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(donatePanel, javax.swing.GroupLayout.DEFAULT_SIZE, 478, Short.MAX_VALUE))
        );
        donateDialogLayout.setVerticalGroup(
            donateDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 448, Short.MAX_VALUE)
            .addGroup(donateDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(donatePanel, javax.swing.GroupLayout.DEFAULT_SIZE, 448, Short.MAX_VALUE))
        );

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new java.awt.Dimension(500, 600));
        setPreferredSize(new java.awt.Dimension(900, 650));
        addWindowStateListener(new java.awt.event.WindowStateListener()
        {
            public void windowStateChanged(java.awt.event.WindowEvent evt)
            {
                formWindowStateChanged(evt);
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(java.awt.event.WindowEvent evt)
            {
                windowHandler(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        mainPanel.setLayout(new java.awt.CardLayout());
        mainPanel.add(marketPanel, "marketPanel");

        splashPanel.setBackground(new java.awt.Color(51, 51, 51));
        splashPanel.setLayout(new java.awt.GridBagLayout());

        statusLabel.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        statusLabel.setForeground(new java.awt.Color(166, 166, 166));
        statusLabel.setText("<html><div style='text-align: center;'<br/><br/><br/></div><html>");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        splashPanel.add(statusLabel, gridBagConstraints);

        mainPanel.add(splashPanel, "splashPanel");
        mainPanel.add(nodeMonitorPanel, "monitorPanel");

        tipJarPanel.setLayout(new java.awt.GridBagLayout());

        jLabel1.setFont(new java.awt.Font("Bahnschrift", 1, 18)); // NOI18N
        jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel1.setText("Leave a tip for the developer");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 20, 0);
        tipJarPanel.add(jLabel1, gridBagConstraints);

        jLabel2.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
        jLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel2.setText("Bitcoin");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 14;
        tipJarPanel.add(jLabel2, gridBagConstraints);

        btcField.setEditable(false);
        btcField.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
        btcField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        btcField.setText("17vBcudET2y2YL9KVEK8M2ex3NHtFrHBwZ");
        btcField.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                btcFieldMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 15;
        gridBagConstraints.ipadx = 150;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
        tipJarPanel.add(btcField, gridBagConstraints);

        jLabel3.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
        jLabel3.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel3.setText("Dogecoin");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        tipJarPanel.add(jLabel3, gridBagConstraints);

        dogeField.setEditable(false);
        dogeField.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
        dogeField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        dogeField.setText("D5rY86s64phnAdBW4DbYfLdDT2EHJUy2Wz");
        dogeField.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                dogeFieldMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.ipadx = 150;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
        tipJarPanel.add(dogeField, gridBagConstraints);

        jLabel4.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
        jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel4.setText("Litecoin");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        tipJarPanel.add(jLabel4, gridBagConstraints);

        ltcField.setEditable(false);
        ltcField.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
        ltcField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        ltcField.setText("LT6XGEurUeRRS3N4BJdP2FB5GHP7Ug3tet");
        ltcField.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                ltcFieldMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.ipadx = 150;
        gridBagConstraints.insets = new java.awt.Insets(11, 0, 11, 0);
        tipJarPanel.add(ltcField, gridBagConstraints);

        jLabel5.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
        jLabel5.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel5.setText("QORT");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        tipJarPanel.add(jLabel5, gridBagConstraints);

        qortField.setEditable(false);
        qortField.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
        qortField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        qortField.setText("Qirh1ZpBufX3PFzX2bmaaQmXTtGqAMtZG2");
        qortField.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                qortFieldMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.ipadx = 150;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
        tipJarPanel.add(qortField, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(11, 0, 10, 0);
        tipJarPanel.add(jSeparator1, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
        tipJarPanel.add(jSeparator2, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
        tipJarPanel.add(jSeparator3, gridBagConstraints);

        clipboardLabel.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
        clipboardLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        clipboardLabel.setText("Click on an address to copy it to your clipboard");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 20, 0);
        tipJarPanel.add(clipboardLabel, gridBagConstraints);

        tipJarScrollPane.setViewportView(tipJarPanel);

        mainPanel.add(tipJarScrollPane, "tipJarPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        getContentPane().add(mainPanel, gridBagConstraints);

        toolbar.setMaximumSize(new java.awt.Dimension(485, 55));
        toolbar.setMinimumSize(new java.awt.Dimension(485, 55));
        toolbar.setPreferredSize(new java.awt.Dimension(485, 55));
        toolbar.setLayout(new javax.swing.BoxLayout(toolbar, javax.swing.BoxLayout.LINE_AXIS));

        marketButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/market.png"))); // NOI18N
        marketButton.setText("Market");
        marketButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        marketButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        marketButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                marketButtonActionPerformed(evt);
            }
        });
        toolbar.add(marketButton);

        nodeMonitorButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/monitor.png"))); // NOI18N
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n/Language"); // NOI18N
        nodeMonitorButton.setText(bundle.getString("nodeMonitorButton")); // NOI18N
        nodeMonitorButton.setToolTipText("Current info on you node's status");
        nodeMonitorButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        nodeMonitorButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        nodeMonitorButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                nodeMonitorButtonActionPerformed(evt);
            }
        });
        toolbar.add(nodeMonitorButton);

        appearanceButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/Appearance.png"))); // NOI18N
        appearanceButton.setText(bundle.getString("appearanceButton")); // NOI18N
        appearanceButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        appearanceButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        appearanceButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                appearanceButtonActionPerformed(evt);
            }
        });
        toolbar.add(appearanceButton);

        donateButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/donate.png"))); // NOI18N
        donateButton.setText(bundle.getString("donateButton")); // NOI18N
        donateButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        donateButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        donateButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                donateButtonActionPerformed(evt);
            }
        });
        toolbar.add(donateButton);

        exitButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/exit.png"))); // NOI18N
        exitButton.setText(bundle.getString("exitButton")); // NOI18N
        exitButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        exitButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        exitButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                exitButtonActionPerformed(evt);
            }
        });
        toolbar.add(exitButton);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        getContentPane().add(toolbar, gridBagConstraints);

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void nodeMonitorButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_nodeMonitorButtonActionPerformed
    {//GEN-HEADEREND:event_nodeMonitorButtonActionPerformed
        nodeMonitorPanel.isSynced = true; //first ping this flag must be true to activate time approximation
        CardLayout card = (CardLayout) mainPanel.getLayout();
        //We only need to run the GUI timer if monitorPanel is selected/in focus
        if (!currentCard.equals("monitorPanel"))
            nodeMonitorPanel.RestartTimer();
        
        currentCard = "monitorPanel";
        card.show(mainPanel, currentCard);
        if(nodeMonitorPanel.startTime == 0)
            nodeMonitorPanel.startTime = System.currentTimeMillis();
        
        //on slow systems, the dialog may not have been triggered to invisible on leaving the chart
        marketPanel.hideAllChartDialogs();
    }//GEN-LAST:event_nodeMonitorButtonActionPerformed

    private void windowHandler(java.awt.event.WindowEvent evt)//GEN-FIRST:event_windowHandler
    {//GEN-HEADEREND:event_windowHandler
        exitInitiated(true);        
    }//GEN-LAST:event_windowHandler

    private void appearanceMenuMouseExited(java.awt.event.MouseEvent evt)//GEN-FIRST:event_appearanceMenuMouseExited
    {//GEN-HEADEREND:event_appearanceMenuMouseExited
        appearanceMenu.setVisible(false);
    }//GEN-LAST:event_appearanceMenuMouseExited

    private void appearanceButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_appearanceButtonActionPerformed
    {//GEN-HEADEREND:event_appearanceButtonActionPerformed
        //Menu bar did can not listen for mouse click on menu, only for menu items.This is a problem for the other buttons.
        //Using a custom pop up menu for setting look and feel. Tried many listeners (focus, mouseEntered and Exited etc.) show() works best
        //Using setVisible creates problems getting rid of the popup. Using the buttons location in show() would place the menu with an offset
        appearanceMenu.setLocation(appearanceButton.getLocationOnScreen().x,appearanceButton.getLocationOnScreen().y);
        appearanceMenu.show(appearanceButton, appearanceMenu.getX(),appearanceMenu.getY());
                
        //on slow systems, the dialog may not have been triggered to invisible on leaving the chart
        marketPanel.hideAllChartDialogs();
    }//GEN-LAST:event_appearanceButtonActionPerformed

    private void donateButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_donateButtonActionPerformed
    {//GEN-HEADEREND:event_donateButtonActionPerformed
        clipboardLabel.setText("Click on an address to copy it to your clipboard");
        
        CardLayout card = (CardLayout) mainPanel.getLayout();
        currentCard = "tipJarPanel";
        card.show(mainPanel, currentCard);
        if (nodeMonitorPanel.timer != null)
            nodeMonitorPanel.timer.cancel();     
        
        //on slow systems, the dialog may not have been triggered to invisible on leaving the chart
        marketPanel.hideAllChartDialogs();
    }//GEN-LAST:event_donateButtonActionPerformed

    private void popUpLabelMouseClicked(java.awt.event.MouseEvent evt)//GEN-FIRST:event_popUpLabelMouseClicked
    {//GEN-HEADEREND:event_popUpLabelMouseClicked
        backgroundService.SetGUIEnabled(true);
    }//GEN-LAST:event_popUpLabelMouseClicked

    private void qortFieldMouseReleased(java.awt.event.MouseEvent evt)//GEN-FIRST:event_qortFieldMouseReleased
    {//GEN-HEADEREND:event_qortFieldMouseReleased
         Utilities.copyToClipboard(qortField.getText());
        pasteToLabel("QORT");
    }//GEN-LAST:event_qortFieldMouseReleased

    private void ltcFieldMouseReleased(java.awt.event.MouseEvent evt)//GEN-FIRST:event_ltcFieldMouseReleased
    {//GEN-HEADEREND:event_ltcFieldMouseReleased
        Utilities.copyToClipboard(ltcField.getText());
        pasteToLabel("Litecoin");
    }//GEN-LAST:event_ltcFieldMouseReleased

    private void dogeFieldMouseReleased(java.awt.event.MouseEvent evt)//GEN-FIRST:event_dogeFieldMouseReleased
    {//GEN-HEADEREND:event_dogeFieldMouseReleased
        Utilities.copyToClipboard(dogeField.getText());
        pasteToLabel("Dogecoin");
    }//GEN-LAST:event_dogeFieldMouseReleased

    private void btcFieldMouseReleased(java.awt.event.MouseEvent evt)//GEN-FIRST:event_btcFieldMouseReleased
    {//GEN-HEADEREND:event_btcFieldMouseReleased
        Utilities.copyToClipboard(btcField.getText());
        pasteToLabel("Bitcoin");
    }//GEN-LAST:event_btcFieldMouseReleased

    private void walletsButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_walletsButtonActionPerformed
    {//GEN-HEADEREND:event_walletsButtonActionPerformed
        donateDialog.setVisible(false);
        CardLayout card = (CardLayout) mainPanel.getLayout();
        currentCard = "tipJarPanel";
        card.show(mainPanel, currentCard);
    }//GEN-LAST:event_walletsButtonActionPerformed

    private void remindLaterButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_remindLaterButtonActionPerformed
    {//GEN-HEADEREND:event_remindLaterButtonActionPerformed
        donateDialog.setVisible(false);
    }//GEN-LAST:event_remindLaterButtonActionPerformed

    private void dismissButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_dismissButtonActionPerformed
    {//GEN-HEADEREND:event_dismissButtonActionPerformed
        donateDialog.setVisible(false);
        File settingsFile = new File(System.getProperty("user.dir") + "/bin/settings.json");
        //Faiil safe: exists check should be redundant, this function is called from a dialog that is only shown if json file exists
        if(settingsFile.exists())
        {
            try
            {
                String jsonString = Files.readString(settingsFile.toPath());
                if(jsonString != null)
                {
                    JSONObject jsonObject = new JSONObject(jsonString);
                    jsonObject.put("donateDismissed", "true");   
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(settingsFile)))
                    {
                        writer.write(jsonObject.toString(1));
                        writer.close();
                    }
                }                
            }
            catch (IOException | JSONException e)
            {
                BackgroundService.AppendLog(e);
            }
        }            
    }//GEN-LAST:event_dismissButtonActionPerformed

    private void marketButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_marketButtonActionPerformed
    {//GEN-HEADEREND:event_marketButtonActionPerformed
        CardLayout card = (CardLayout) mainPanel.getLayout();
        currentCard = "marketPanel";
        card.show(mainPanel, currentCard);
        if (nodeMonitorPanel.timer != null)
            nodeMonitorPanel.timer.cancel();
        
        //on slow systems, the dialog may not have been triggered to invisible on leaving the chart
        marketPanel.hideAllChartDialogs();
    }//GEN-LAST:event_marketButtonActionPerformed

    private void formWindowStateChanged(java.awt.event.WindowEvent evt)//GEN-FIRST:event_formWindowStateChanged
    {//GEN-HEADEREND:event_formWindowStateChanged
        if(evt.getNewState() == Frame.MAXIMIZED_BOTH || 
                evt.getNewState() == Frame.MAXIMIZED_VERT || 
                evt.getOldState()== Frame.MAXIMIZED_BOTH ||
                evt.getOldState() == Frame.MAXIMIZED_VERT)
            marketPanel.setTradeTabDividers();
    }//GEN-LAST:event_formWindowStateChanged

    private void exitButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_exitButtonActionPerformed
    {//GEN-HEADEREND:event_exitButtonActionPerformed
        exitInitiated(false);
    }//GEN-LAST:event_exitButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton appearanceButton;
    private javax.swing.ButtonGroup appearanceGroup;
    protected javax.swing.JPopupMenu appearanceMenu;
    private javax.swing.JTextField btcField;
    private javax.swing.JLabel clipboardLabel;
    private javax.swing.JButton dismissButton;
    private javax.swing.JTextField dogeField;
    private javax.swing.JButton donateButton;
    private javax.swing.JDialog donateDialog;
    private javax.swing.JLabel donateLabel;
    private javax.swing.JPanel donatePanel;
    private javax.swing.JButton exitButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JTextField ltcField;
    protected javax.swing.JPanel mainPanel;
    private javax.swing.JButton marketButton;
    protected marketwatch.MarketPanel marketPanel;
    private javax.swing.JButton nodeMonitorButton;
    protected marketwatch.MonitorPanel nodeMonitorPanel;
    private javax.swing.JLabel popUpLabel;
    private javax.swing.JTextField qortField;
    private javax.swing.JButton remindLaterButton;
    private javax.swing.JPanel splashPanel;
    protected javax.swing.JLabel statusLabel;
    protected javax.swing.JPanel tipJarPanel;
    private javax.swing.JScrollPane tipJarScrollPane;
    protected javax.swing.JPanel toolbar;
    public javax.swing.JDialog trayPopup;
    private javax.swing.JButton walletsButton;
    // End of variables declaration//GEN-END:variables

        
}//end class GUI




