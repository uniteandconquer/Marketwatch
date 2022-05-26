package marketwatch;

import enums.Extensions;
import enums.Folders;
import enums.Chart;
import customized.ComboRenderer;
import customized.CustomCombinedDomainXYPlot;
import customized.CustomComponentResizedListener;
import customized.CustomColorChooser;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.beans.PropertyVetoException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.plot.XYPlot;
import org.json.JSONException;
import org.json.JSONObject;

public class MarketPanel extends javax.swing.JPanel
{    
    protected DatabaseManager dbManager;
    protected GUI gui;
    private boolean selectedByChart;
    protected CustomColorChooser colorChooser;
    protected boolean initComplete;
    protected int dividerColor;
    protected int guiBackgroundColor = Color.lightGray.getRGB();
    protected int guiComponentColor = Color.lightGray.getRGB();
    protected int guiFontColor = Color.black.getRGB();
    protected int componentFontColor = Color.black.getRGB();
    protected Map<String,String> typeToQueryMap;
    protected BidirectionalMap typeToTitleMap;
    protected BidirectionalMap titleToIndexMap;
    protected DraggedItem draggedItem;
    private Timer updateTimer;
    protected int currentTick;
    protected final static int UPDATE_INTERVAL = 15;
    protected long lastUpdatedTime;
    protected boolean updateInProgress;
    private Timer crosshairTimer;
    private TimerTask crosshairTask;
    private String orderKey = "asc";
    private ArrayList<ArrangedFrame> currentArrangement;
    protected boolean chartWindowFocused;

    public MarketPanel()
    {
        initComponents();
    }

    protected void initialise(DatabaseManager dbManager, GUI gui)
    {
        this.dbManager = dbManager;
        this.gui = gui;
        toggleStyleButton.setText(toggleStyleButton.isSelected() ? "Hide style menu" : "Show style menu");
        styleMenuScrollpane.setVisible(toggleStyleButton.isSelected());
        layoutMenuScrollpane.setVisible(toggleLayoutButton.isSelected());
        colorChooser = new CustomColorChooser(this);
        crosshairTimer = new Timer();
        populateTypeToQueryMap();
        populateTypeToTitle();
        populateTitleToIndex();

        initListeners(); 
        
        File tradesFile = new File(System.getProperty("user.dir") + "/" + Folders.DB.get() + "/trades.mv.db");
        if(!tradesFile.exists())
        {
            //Should not occur, but in case trades file does not exist disable all tabs except 
            //update status tab and go to that tab
            tabbedPane.setSelectedComponent(updateTab);
            for(int i = 0; i < tabbedPane.getTabCount(); i++)
                tabbedPane.setEnabledAt(i, i == tabbedPane.getTabCount() - 1);
        }
        else
        {
           fillTradesTables();
        }   
        
    }//end initialise()
    
    protected void fillTradesTables()
    {
        try(Connection connection = ConnectionDB.getConnection("trades",Folders.DB.get()))
           {    
               fillTradesTable(connection, ltcTradesTable, "timestamp desc", getTradesTableStrings(ltcTradesTable));
               fillTradesTable(connection, dogeTradesTable, "timestamp desc", getTradesTableStrings(dogeTradesTable));
               fillTradesTable(connection, ravenTradesTable, "timestamp desc", getTradesTableStrings(ravenTradesTable));
               fillTradesTable(connection, digibyteTradesTable, "timestamp desc", getTradesTableStrings(digibyteTradesTable));
               fillTradesTable(connection, btcTradesTable, "timestamp desc", getTradesTableStrings(btcTradesTable));
           }
           catch (Exception e)
           {
               BackgroundService.AppendLog(e);
           }
    }
    
    private void fillTradesTable(Connection connection,JTable tradesTable,String orderString,String[] stringTypes) throws SQLException
    {
        Statement statement;
        ResultSet resultSet;

        DefaultTableModel model = (DefaultTableModel) tradesTable.getModel();
        model.setRowCount(0);

        statement = connection.createStatement();
        resultSet = statement.executeQuery("select * from " + stringTypes[0] + " order by " + orderString );

        while(resultSet.next())
        {        
            //using strings for cell x allignment purposes
            model.addRow(new Object[]
            {
                Utilities.DateFormatShort(resultSet.getLong("timestamp")),
                resultSet.getInt("blockheight"),
                resultSet.getDouble("amount"),
                resultSet.getDouble(stringTypes[1]),//total_foreign
                resultSet.getDouble(stringTypes[2]),//foreign_per_qort
                resultSet.getDouble(stringTypes[3]),//qort_per_foreign
                resultSet.getString("signature")
            });
        }    
    }   
    
    private void setGuiValues(String layoutName,String folder, Extensions extension)
    {
        try(Connection connection = ConnectionDB.getConnection(layoutName, folder, extension))
        {    
            Object fontObject = dbManager.GetFirstItem("gui", "font", connection);
            if(fontObject != null)
            {
                for(int i = 0; i < fontsBox.getItemCount(); i++)
                {
                    if(fontsBox.getItemAt(i).equals(fontObject.toString()))
                    {
                        fontsBox.setSelectedIndex(i);
                        break;
                    }
                }
            }
            Object divColorObject = dbManager.GetFirstItem("gui", "divider_color", connection);
            if(divColorObject != null)
            {
                dividerColor = (int)divColorObject;
                dividerColorLabel.setBackground(new Color(dividerColor));
            }
            
            Object bgcObject = dbManager.GetFirstItem("gui", "background_color", connection);
            if(bgcObject != null)
            {
                guiBackgroundColor = (int)bgcObject;
                guiBackgroundLabel.setBackground(new Color(guiBackgroundColor));
            }
            
            Object cmpObject = dbManager.GetFirstItem("gui", "component_color", connection);
            if(cmpObject != null)
            {
                guiComponentColor = (int)cmpObject;
                guiComponentsLabel.setBackground(new Color(guiComponentColor));
            }
            
            Object fontColorObject = dbManager.GetFirstItem("gui", "gui_font_color", connection);
            if(fontColorObject != null)
            {
                guiFontColor = (int)fontColorObject;
                guiFontColorLabel.setBackground(new Color(guiFontColor));
            }
            
            Object cmpFontObject = dbManager.GetFirstItem("gui", "component_font_color", connection);
            if(cmpFontObject != null)
            {
                componentFontColor = (int)cmpFontObject;
                componentFontLabel.setBackground(new Color(componentFontColor));
            }
            
        }
        catch(Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }
            
    protected void updateStyleMenu(ChartWindow cw)
    {       
        textColorLabel.setBackground(cw.textColor);
        lineColorLabel.setBackground(cw.lineColor);
        gridColorLabel.setBackground(cw.gridColor);
        dialogColorLabel.setBackground(cw.dialogColor);
        dialogTxtColorLabel.setBackground(cw.dialogTextColor);
        crosshairsColorLabel.setBackground(cw.crosshairsColor);
        chartColorLabel.setBackground(cw.chartColor);
        plotColorLabel.setBackground(cw.plotColor);
        strokeSlider.setValue(cw.sliderValue);        
        snapshotsCheckbox.setSelected(cw.showSnapshots);
        drawX_box.setSelected(cw.drawX);
        drawY_box.setSelected(cw.drawY);
        showTextBox.setSelected(cw.showText);
        showSubBox.setSelected(cw.showSubtitle);
        showXcrosshairsBox.setSelected(cw.showXcrosshairs);
        showYcrosshairBox.setSelected(cw.showYcrosshair);
        showDialogBox.setSelected(cw.showDialog);
        tradeVolumeBox.setSelected(cw.showVolume);
        movingAverageBox.setSelected(cw.showMovingAverage);
        averagingSlider.setValue(cw.averageBy);
    }
    
    /**When a chart gets added or removed, the arrangements list needs to be updated to<br>
     show the available number of arrangements for the current number of frames*/
    protected void updateArrangementsList()
    {
        SwingUtilities.invokeLater(()->
        {
            if(desktopPane.getAllFrames().length <= 1)
                saveArrangeButton.setEnabled(false);
            else
                saveArrangeButton.setEnabled(true);
            
            File folder = new File(System.getProperty("user.dir") + "/" + Folders.ARRANGE.get());
            if(!folder.isDirectory())
                folder.mkdir();

           File[] listOfFiles = folder.listFiles();

           var model = (DefaultListModel)arrangementsList.getModel();
           model.clear();

           for (File file : listOfFiles)
           {               
               if(!file.getName().endsWith(".arng"))
                   continue;
               
               String dbName = file.getName().substring(0,file.getName().length() - 5);
               try(Connection connection =
                       ConnectionDB.getConnection(dbName, Folders.ARRANGE.get(),Extensions.ARRANGE))
               {
                   //only show arrangements with the same number of frames as currently on desktop
                   if(dbManager.GetTables(connection).size()  == desktopPane.getAllFrames().length)
                       model.addElement(dbName);                
               }
               catch (Exception e)
               {
                   BackgroundService.AppendLog(e);
               }            
           }  
           
           arrangementsLabel.setText(Utilities.AllignCenterHTML(model.getSize() + " arrangements<br/>for " + 
                   desktopPane.getAllFrames().length + " chart layout"));
           
           if(arrangementsList.getSelectedIndex() == -1)
           {
               deleteArrangeButton.setEnabled(false);
               loadArrangeButton.setEnabled(false);
               randomizeCheckbox.setEnabled(false);               
           }
           
        });        
    }
    
    protected void updateCharts()
    {
        try(Connection connection = ConnectionDB.getConnection("trades", Folders.DB.get()))
        {
            //update all open chart windows
            for(JInternalFrame frame : desktopPane.getAllFrames())
            {
                ((ChartWindow)frame).updateChart(connection);
            }
            //update all chart windows in the trades tabs
            for(int i = 0; i < tabbedPane.getTabCount(); i++)
            {
                String tabTitle = tabbedPane.getTitleAt(i);

                if(tabTitle.endsWith("trades"))
                {   
                    if(tabTitle.equals("Get latest trades"))
                        continue;
                    
                    JSplitPane splitPane = (JSplitPane) tabbedPane.getComponent(i); 
                    var cw = (ChartWindow)splitPane.getTopComponent();
                    cw.updateChart(connection);
                    
                    //re-initialise chartPanel mouse listeners
                    switch (tabTitle)
                    {
                        case "LTC trades":
                            setChartPanelListener(cw, ltcTradesTable, ltcTradesScrollpane);
                            break;
                        case "BTC trades":
                            setChartPanelListener(cw, btcTradesTable, btcTradesScrollpane);
                            break;
                        case "Doge trades":
                            setChartPanelListener(cw, dogeTradesTable, dogeTradesScrollpane);
                            break;
                        case "Ravencoin trades":
                            setChartPanelListener(cw, ravenTradesTable, ravenTradesScrollpane);
                            break;
                        case "Digibyte trades":
                            setChartPanelListener(cw, digibyteTradesTable, digibyteTradesScrollpane);
                            break;
                    }
                }
            }
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }            
    }
    
    /**If there are no frames on the desktop we disable the saveLayoutButton here<br>
     on add/remove frame we only check for button disable/enable
     * @param buttonCheckOnly*/
    protected void updateLayoutsList(boolean buttonCheckOnly)
    {
        SwingUtilities.invokeLater(()->
        {
            if(desktopPane.getAllFrames().length == 0)
                saveLayoutButton.setEnabled(false);
            else
                saveLayoutButton.setEnabled(true);
            
            if(buttonCheckOnly)
                return;
            
            File folder = new File(System.getProperty("user.dir") + "/" + Folders.LAYOUTS.get());
            if(!folder.isDirectory())
                folder.mkdir();

           File[] listOfFiles = folder.listFiles();

           var model = (DefaultListModel)layoutsList.getModel();
           model.clear();

           for (File file : listOfFiles)
           {
               if(file.isDirectory())
                   continue;
               
               if(!file.getName().endsWith(".layout"))
                   continue;
               
               String dbName = file.getName().substring(0,file.getName().length() - 7);
               
               if(!dbName.equals("CurrentLayout"))
                    model.addElement(dbName);             
           }  
           if(layoutsList.getSelectedIndex() == -1)
           {
               deleteLayoutButton.setEnabled(false);
               loadLayoutButton.setEnabled(false);
           }           
        });        
    }
    
      /**If there are no frames on the desktop we disable the saveStylesButton here<br>
     on add/remove frame we only check for button disable/enable
     * @param buttonCheckOnly*/
    protected void updateStylesList(boolean buttonCheckOnly)
    {
        SwingUtilities.invokeLater(()->
        {
            if(desktopPane.getAllFrames().length == 0)
                saveStyleButton.setEnabled(false);
            else
                saveStyleButton.setEnabled(true);
            
            if(buttonCheckOnly)
                return;
            
            File folder = new File(System.getProperty("user.dir") + "/" + Folders.STYLES.get());
            if(!folder.isDirectory())
                folder.mkdir();

           File[] listOfFiles = folder.listFiles();

           var model = (DefaultListModel)stylesList.getModel();
           model.clear();

           for (File file : listOfFiles)
           {
               if(file.isDirectory())
                   continue;
               
               if(!file.getName().endsWith(".style"))
                   continue;
               
               String dbName = file.getName().substring(0,file.getName().length() - 6);
               model.addElement(dbName);             
           }  
           if(stylesList.getSelectedIndex() == -1)
           {
               loadStyleButton.setEnabled(false);
               deleteStyleButton.setEnabled(false);
           }           
        });        
    }
    
    private void saveArrangement(String fileName)
    {
        File file = new File(System.getProperty("user.dir") + "/" + Folders.ARRANGE.get() + "/" + fileName + ".arng");
        
        if(!file.exists())
            ConnectionDB.CreateDatabase(fileName, Folders.ARRANGE.get(),Extensions.ARRANGE);
        
        try(Connection connection = ConnectionDB.getConnection(fileName, Folders.ARRANGE.get(),Extensions.ARRANGE))
        {            
            dbManager.ExecuteUpdate("drop all objects", connection);
            
            int count = 0;
            
            for(JInternalFrame frame : desktopPane.getAllFrames())
            {
                dbManager.CreateTable(new String[]{"chart_" + count,"x","double","y","double","width","double","height","double"}, connection);
                dbManager.InsertIntoDB(new String[]{"chart_" + count,
                    "x",String.valueOf(((double) frame.getX() / desktopPane.getWidth())),
                    "y",String.valueOf(((double) frame.getY()/ desktopPane.getHeight())),
                    "width",String.valueOf(((double) frame.getWidth()/ desktopPane.getWidth())),
                    "height",String.valueOf(((double) frame.getHeight()/ desktopPane.getHeight())) }, connection);
                
                count++;
            }
        }
        catch(Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }
    
    private void loadArrangement(String filename)
    {
        try (Connection connection = ConnectionDB.getConnection(
                    filename, Folders.ARRANGE.get(), Extensions.ARRANGE))
        {
            var tablesList = dbManager.GetTables(connection);
            if (randomizeCheckbox.isSelected())
                Collections.shuffle(tablesList);

            int count = 0;
            for (String table : tablesList)
            {
                JInternalFrame frame = desktopPane.getAllFrames()[count];
                double x = (double) dbManager.GetFirstItem(table, "x", connection);
                x *= desktopPane.getWidth();
                double y = (double) dbManager.GetFirstItem(table, "y", connection);
                y *= desktopPane.getHeight();
                double width = (double) dbManager.GetFirstItem(table, "width", connection);
                width *= desktopPane.getWidth();
                double height = (double) dbManager.GetFirstItem(table, "height", connection);
                height *= desktopPane.getHeight();

                frame.setBounds((int)x, (int)y, (int)width, (int)height);

                count++;
            }

            saveCurrentArrangement();
            //If we keep selecting the closest frame, the arrangement will always (appear to) be  random
            if(desktopPane.getSelectedFrame() == null)
                selectClosestFrame();
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }
    
    protected void saveCurrentArrangement()
    {
        SwingUtilities.invokeLater(() ->
        {
            currentArrangement = new ArrayList<>();

            for (int i = 0; i < desktopPane.getAllFrames().length; i++)
            {
                ChartWindow cw = (ChartWindow) desktopPane.getAllFrames()[i];
                currentArrangement.add(new ArrangedFrame(
                        ((double) cw.getX() / desktopPane.getWidth()),
                        ((double) cw.getY() / desktopPane.getHeight()),
                        ((double) cw.getWidth() / desktopPane.getWidth()),
                        ((double) cw.getHeight() / desktopPane.getHeight())
                ));

                cw.arrIndex = i;
            }
        });
    }
    
    protected boolean addNewChart(String title,Point point,String layoutName)
    {   
        int frameCount = desktopPane.getAllFrames().length;
        if(frameCount == 10)
        {
            draggedItem.setVisible(false);
            JOptionPane.showMessageDialog(this, "Cannot add more than 10 charts");
            return false;
        }
        
        try(Connection connection = ConnectionDB.getConnection("trades", Folders.DB.get()))
        {        
            //having a maximized frame when adding a new frame messes up the layout
            //when that frame is de-maximized later on
            for(JInternalFrame frame : desktopPane.getAllFrames())
                if(frame.isMaximum())
                    frame.setMaximum(false);
            
            int width = desktopPane.getWidth() / 3;
            int height = desktopPane.getHeight() / 3;
            int x = frameCount * (width / 10);
            int y = frameCount * (width / 10);
            
            String type = typeToTitleMap.get(title);
            ChartWindow cw = new ChartWindow();
            Point location = point == null ? new Point(x,y) : point;            
            cw.createChart(Chart.valueOf(type), this, new Dimension(width, height), location, gui, dbManager, connection);    
            
            //set the new chart's style to same as selected chart
            var selectedChart = (ChartWindow) desktopPane.getSelectedFrame();
            //if no chart selected and desktop not empty -> set style to chart with index 0
            selectedChart = selectedChart == null && desktopPane.getAllFrames().length > 0 ?
                    (ChartWindow)desktopPane.getAllFrames()[0] : selectedChart;
            //if sected chart is still null the chart will load with default style
            if(selectedChart != null)
            {
                ChartStyle style = copyChartStyle(selectedChart.type.toString(), layoutName,Folders.LAYOUTS.get(),Extensions.LAYOUT);
                if(style != null)
                    setChartStyle(cw, style);
            }
            
            //For proper UX we want all frames to be in the same layer, but newly added
            //frames will not be visible if not set to layer 1 first before setting layer to 0
            cw.setLayer(1);
            desktopPane.add(cw);
            cw.setLayer(0);
            updateArrangementsList();
            updateLayoutsList(true);
            updateStylesList(true);
            cw.setSelected(true);
            updateStyleMenu(cw);
            saveLayout("CurrentLayout", cw.type, false, false);
            saveCurrentArrangement();
            return true;
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
            return false;
        }
    }
    
    private ChartStyle copyChartStyle(String table,String layoutName,String folder,Extensions extension)
    {    
        try(Connection connection = ConnectionDB.getConnection(layoutName, folder,extension))
        {
            return new ChartStyle
            (
                (String) dbManager.GetFirstItem(table, "font", connection),
                (byte) dbManager.GetFirstItem(table, "font_size", connection),
                new Color((int) dbManager.GetFirstItem(table, "chart_color", connection)),
                new Color((int) dbManager.GetFirstItem(table, "plot_color", connection)),
                new Color((int) dbManager.GetFirstItem(table, "crosshairs_color", connection)),
                new Color((int) dbManager.GetFirstItem(table, "dialog_color", connection)),
                new Color((int) dbManager.GetFirstItem(table, "dialog_txt_color", connection)),
                new Color((int) dbManager.GetFirstItem(table, "line_color", connection)),
                new Color((int) dbManager.GetFirstItem(table, "text_color", connection)),
                new Color((int) dbManager.GetFirstItem(table, "grid_color", connection)),
                (int) dbManager.GetFirstItem(table, "slider_value", connection),
                (boolean) dbManager.GetFirstItem(table, "show_snaps", connection),
                (boolean) dbManager.GetFirstItem(table, "show_sub", connection),
                (boolean) dbManager.GetFirstItem(table, "show_text", connection),
                (boolean) dbManager.GetFirstItem(table, "show_dialog", connection),
                (boolean) dbManager.GetFirstItem(table, "show_x_crosshairs", connection),
                (boolean) dbManager.GetFirstItem(table, "show_y_crosshair", connection),
                (boolean) dbManager.GetFirstItem(table, "draw_x", connection),
                (boolean) dbManager.GetFirstItem(table, "draw_y", connection),
                (boolean) dbManager.GetFirstItem(table, "show_ma", connection),
                (boolean) dbManager.GetFirstItem(table, "show_volume", connection),
                (byte) dbManager.GetFirstItem(table, "average_by", connection)            
            );
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
            return null;
        }        
    } 
    
    private void setChartStyle(ChartWindow cw,ChartStyle style)
    {  
        cw.setStyle(style.font, style.fontSize, style.chartColor, style.plotColor, style.crosshairsColor, 
                    style.dialogColor, style.dialogTextColor, style.lineColor, style.textColor, style.gridColor,
                    style.sliderValue, style.showSnapshots, style.showSubtitle, style.showText, style.showDialog,
                    style.showXcrosshairs, style.showYcrosshair,style.drawX, style.drawY, 
                    style.showMovingAverage,style.showVolume,style.averageBy);      
    }
    
    protected void initUpdateTimer()
    {
        if(!autoUpdateCheckbox.isSelected())
            return;
        
        currentTick = -1; //ensure update runs on init
        updateTimer = new Timer();
        updateTimer.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                currentTick++;   
                
                if(currentTick % UPDATE_INTERVAL == 0)
                {
                    currentTick = 0;
                    
                    if(!updateInProgress)
                       TradeExtractor.extractTrades(MarketPanel.this);                    
                }
                else
                {
                    if(!updateInProgress)
                        updateStatusLabel.setText("Last updated : " + Utilities.DateFormat(lastUpdatedTime) + 
                                " | Next update in " + (UPDATE_INTERVAL - currentTick) +  " minutes");
                }
            }
        }, 0, 60000);
    }
    
    private void initListeners()
    {      
        desktopPane.addComponentListener(new CustomComponentResizedListener(50)
        {
            @Override
            public void resizeTimedOut()
            {
                SwingUtilities.invokeLater(()->{resizeChartWindows();});    
//                resizeChartWindows();
            }
            
            @Override
            public void startedResizing(){}
        });
        
       // <editor-fold defaultstate="collapsed" desc="Lists listeners"> 
        arrangementsList.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent evt)
            {
                if(arrangementsList.getSelectedIndex() >= 0)
                {
                    loadArrangeButton.setEnabled(true);
                    randomizeCheckbox.setEnabled(true);
                    saveArrangeButton.setEnabled(true);
                    deleteArrangeButton.setEnabled(true);
                }
                else
                {
                    loadArrangeButton.setEnabled(false);
                    randomizeCheckbox.setEnabled(false);
                    saveArrangeButton.setEnabled(false);
                    deleteArrangeButton.setEnabled(false);
                }
                
                if (evt.getClickCount() == 2)
                {
                    loadArrangeButtonActionPerformed(null);
                }
            }
        });  
        
        layoutsList.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent evt)
            {
                if(layoutsList.getSelectedIndex() >= 0)
                {
                    loadLayoutButton.setEnabled(true);
                    saveLayoutButton.setEnabled(true);
                    deleteLayoutButton.setEnabled(true);
                }
                else
                {
                    loadLayoutButton.setEnabled(false);
                    saveLayoutButton.setEnabled(false);
                    deleteLayoutButton.setEnabled(false);
                }
                
                if (evt.getClickCount() == 2)
                {
                    loadLayoutButtonActionPerformed(null);
                }
            }
        });   
        stylesList.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent evt)
            {
                if(stylesList.getSelectedIndex() >= 0)
                {
                    loadStyleButton.setEnabled(true);
                    saveStyleButton.setEnabled(true);
                    deleteStyleButton.setEnabled(true);
                }
                else
                {
                    loadStyleButton.setEnabled(false);
                    saveStyleButton.setEnabled(false);
                    deleteStyleButton.setEnabled(false);
                }
                
                if (evt.getClickCount() == 2)
                {
                    loadStyleButtonActionPerformed(null);
                }
            }
        });  
        //</editor-fold>
        
    }
    
    protected void initCharts()
    {        
        try(Connection connection = ConnectionDB.getConnection("trades", Folders.DB.get()))
        {   
            ChartItem[] chartItems = new ChartItem[titleToIndexMap.getSize()];
            
            ImageIcon qortIcon = new ImageIcon(getClass().getResource("/Images/qortal_coin.png"));
            ImageIcon btcIcon = new ImageIcon(getClass().getResource("/Images/bitcoin.png"));
            ImageIcon ltcIcon = new ImageIcon(getClass().getResource("/Images/litecoin.png"));
            ImageIcon dogeIcon = new ImageIcon(getClass().getResource("/Images/dogecoin.png"));
            ImageIcon ravenIcon = new ImageIcon(getClass().getResource("/Images/ravencoin.png"));
            ImageIcon digibyteIcon = new ImageIcon(getClass().getResource("/Images/digibyte.png"));
            ImageIcon dollarIcon = new ImageIcon(getClass().getResource("/Images/dollar.png"));
            ImageIcon multiIcon = new ImageIcon(getClass().getResource("/Images/all_foreign.png"));
            
            boolean layoutLoaded = loadLayout("CurrentLayout",null);
            
            int halfWidth = desktopPane.getWidth() / 2;
            int halfHeight = desktopPane.getHeight() / 2;       
            
            for(String table : dbManager.GetTables(connection))
            {
                switch (table)
                {
                    case "LTC_QORT":                        
                        addChartItemToList(Chart.LTC_QORT.toString(), ltcIcon, qortIcon, chartItems,false);
                        addChartItemToList(Chart.QORT_LTC.toString(), qortIcon, ltcIcon, chartItems,false);  
                        
                        //set default charts if no layout available
                        if (!layoutLoaded)
                        {
                            ChartWindow chart = new ChartWindow();
                            chart.createChart(Chart.LTC_QORT, this, new Dimension(halfWidth, halfHeight), new Point(0, 0), gui, dbManager, connection);
                            desktopPane.add(chart);
                            ChartWindow chart2 = new ChartWindow();
                            chart2.createChart(Chart.QORT_LTC, this, new Dimension(halfWidth, halfHeight), new Point(halfWidth, 0), gui, dbManager, connection);
                            desktopPane.add(chart2);
                        }
                        break;
                    case "USD_QORT":
                        addChartItemToList(Chart.USD_QORT.toString(), dollarIcon, qortIcon, chartItems,false);
                        addChartItemToList(Chart.QORT_USD.toString(), qortIcon, dollarIcon, chartItems,false);  
                        
                        if (!layoutLoaded)
                        {
                            ChartWindow chart = new ChartWindow();
                            chart.createChart(Chart.USD_QORT, this, new Dimension(halfWidth * 2, halfHeight), new Point(0, halfHeight), gui, dbManager, connection);
                            desktopPane.add(chart);
                        }
                        break;
                    case "DOGE_QORT":
                        addChartItemToList(Chart.DOGE_QORT.toString(), dogeIcon, qortIcon, chartItems,false);
                        addChartItemToList(Chart.QORT_DOGE.toString(), qortIcon, dogeIcon, chartItems,false);                          
                        break;
                    case "RAVEN_QORT":
                        addChartItemToList(Chart.RAVEN_QORT.toString(), ravenIcon, qortIcon, chartItems,false);
                        addChartItemToList(Chart.QORT_RAVEN.toString(), qortIcon, ravenIcon, chartItems,false);                          
                        break;
                    case "DIGIBYTE_QORT":
                        addChartItemToList(Chart.DIGIBYTE_QORT.toString(), digibyteIcon, qortIcon, chartItems,false);
                        addChartItemToList(Chart.QORT_DIGIBYTE.toString(), qortIcon, digibyteIcon, chartItems,false);                          
                        break;
                    case "BTC_QORT":
                        addChartItemToList(Chart.BTC_QORT.toString(), btcIcon, qortIcon, chartItems,false);
                        addChartItemToList(Chart.QORT_BTC.toString(), qortIcon, btcIcon, chartItems,false);                          
                        break;
                    case "USD_LTC":
                        addChartItemToList(Chart.USD_LTC.toString(), dollarIcon, ltcIcon, chartItems,false);
                        addChartItemToList(Chart.LTC_USD.toString(), ltcIcon, dollarIcon, chartItems,false);                         
                        break;
                    case "USD_BTC":
                        addChartItemToList(Chart.USD_BTC.toString(), dollarIcon, btcIcon, chartItems,false);
                        addChartItemToList(Chart.BTC_USD.toString(), btcIcon, dollarIcon, chartItems,false);                          
                        break;
                    case "USD_DOGE":
                        addChartItemToList(Chart.USD_DOGE.toString(), dollarIcon, dogeIcon, chartItems,false);
                        addChartItemToList(Chart.DOGE_USD.toString(), dogeIcon, dollarIcon, chartItems,false);                          
                        break;
                    case "USD_RAVEN":
                        addChartItemToList(Chart.USD_RAVEN.toString(), dollarIcon, ravenIcon, chartItems,false);
                        addChartItemToList(Chart.RAVEN_USD.toString(), ravenIcon, dollarIcon, chartItems,false);                          
                        break;
                    case "USD_DIGIBYTE":
                        addChartItemToList(Chart.USD_DIGIBYTE.toString(), dollarIcon, digibyteIcon, chartItems,false);
                        addChartItemToList(Chart.DIGIBYTE_USD.toString(), digibyteIcon, dollarIcon, chartItems,false);                          
                        break;
                    case "LTC_TRADES":
                        addChartItemToList(Chart.LTC_QORT_TRADE.toString(), ltcIcon, qortIcon, chartItems,true);
                        addChartItemToList(Chart.QORT_LTC_TRADE.toString(), qortIcon, ltcIcon, chartItems,true);   
                        addChartItemToList(Chart.LTC_QORT_AMOUNT.toString(), ltcIcon, qortIcon, chartItems,true);     
                        addChartItemToList(Chart.QORT_LTC_AMOUNT.toString(), qortIcon, ltcIcon, chartItems,true);     
                        addChartItemToList(Chart.LTC_TRADES_TOTAL.toString(), ltcIcon, qortIcon, chartItems,true);                          
                        break;
                    case "DOGE_TRADES":
                        addChartItemToList(Chart.DOGE_QORT_TRADE.toString(), dogeIcon, qortIcon, chartItems,true);
                        addChartItemToList(Chart.QORT_DOGE_TRADE.toString(), qortIcon, dogeIcon, chartItems,true);   
                        addChartItemToList(Chart.DOGE_QORT_AMOUNT.toString(), dogeIcon, qortIcon, chartItems,true);     
                        addChartItemToList(Chart.QORT_DOGE_AMOUNT.toString(), qortIcon, dogeIcon, chartItems,true);     
                        addChartItemToList(Chart.DOGE_TRADES_TOTAL.toString(), dogeIcon, qortIcon, chartItems,true);                         
                        break;
                    case "RAVEN_TRADES":
                        addChartItemToList(Chart.RAVEN_QORT_TRADE.toString(), ravenIcon, qortIcon, chartItems,true);
                        addChartItemToList(Chart.QORT_RAVEN_TRADE.toString(), qortIcon, ravenIcon, chartItems,true);   
                        addChartItemToList(Chart.RAVEN_QORT_AMOUNT.toString(), ravenIcon, qortIcon, chartItems,true);     
                        addChartItemToList(Chart.QORT_RAVEN_AMOUNT.toString(), qortIcon, ravenIcon, chartItems,true);     
                        addChartItemToList(Chart.RAVEN_TRADES_TOTAL.toString(), ravenIcon, qortIcon, chartItems,true);                         
                        break;
                    case "DIGIBYTE_TRADES":
                        addChartItemToList(Chart.DIGIBYTE_QORT_TRADE.toString(), digibyteIcon, qortIcon, chartItems,true);
                        addChartItemToList(Chart.QORT_DIGIBYTE_TRADE.toString(), qortIcon, digibyteIcon, chartItems,true);   
                        addChartItemToList(Chart.DIGIBYTE_QORT_AMOUNT.toString(), digibyteIcon, qortIcon, chartItems,true);     
                        addChartItemToList(Chart.QORT_DIGIBYTE_AMOUNT.toString(), qortIcon, digibyteIcon, chartItems,true);     
                        addChartItemToList(Chart.DIGIBYTE_TRADES_TOTAL.toString(), digibyteIcon, qortIcon, chartItems,true);                         
                        break;
                    case "BTC_TRADES":
                        addChartItemToList(Chart.BTC_QORT_TRADE.toString(), btcIcon, qortIcon, chartItems,true);
                        addChartItemToList(Chart.QORT_BTC_TRADE.toString(), qortIcon, btcIcon, chartItems,true);    
                        addChartItemToList(Chart.BTC_QORT_AMOUNT.toString(), btcIcon, qortIcon, chartItems,true);     
                        addChartItemToList(Chart.QORT_BTC_AMOUNT.toString(), qortIcon, btcIcon, chartItems,true);  
                        addChartItemToList(Chart.BTC_TRADES_TOTAL.toString(), btcIcon, qortIcon, chartItems,true);                          
                        break;
                    case "ALL_TRADES":
                        addChartItemToList(Chart.TOTAL_TRADES.toString(), qortIcon, multiIcon, chartItems,true);  
                        addChartItemToList(Chart.TOTAL_QORT_TRADED.toString(), qortIcon, multiIcon, chartItems,true);   
                        break;
                    default:
                        System.err.println("FOUND UNIITIALIZED TABLE : " + table);
                }
            }   
            
            SwingUtilities.invokeLater(()->
            {  
                for(ChartItem item : chartItems)
                {
                    chartsList.add(item);
                }                
                
                //remove all listitems for existing charts
                for (JInternalFrame frame : desktopPane.getAllFrames())
                {
                    var cw = (ChartWindow) frame;
                    setChartItemVisible(typeToTitleMap.get(cw.type.toString()), false);
                }
                //without validate, the list would sometimes take a long
                //time to initialise (show the items, occurs when no frames in desktop)
                chartsList.validate();
                chartsList.repaint();                
            });
            
            initTradesTab(Chart.LTC_QORT_TRADE, connection, tradesTab, ltcTradesTable, ltcTradesScrollpane);
            initTradesTab(Chart.DOGE_QORT_TRADE, connection, dogeTradesTab, dogeTradesTable, dogeTradesScrollpane);
            initTradesTab(Chart.RAVEN_QORT_TRADE, connection, ravenTradesTab, ravenTradesTable, ravenTradesScrollpane);
            initTradesTab(Chart.DIGIBYTE_QORT_TRADE, connection, digibyteTradesTab, digibyteTradesTable, digibyteTradesScrollpane);
            initTradesTab(Chart.BTC_QORT_TRADE, connection, btcTradesTab, btcTradesTable, btcTradesScrollpane);      
            
            if(desktopPane.getAllFrames().length > 0) //desktopPane.getSelectedFrame() != null)
                setTradesChartsStyle("CurrentLayout",Folders.LAYOUTS.get(),Extensions.LAYOUT,
                        ((ChartWindow)desktopPane.getAllFrames()[0]).type.toString()); //.getSelectedFrame()).type.toString());
            
            saveCurrentArrangement();    
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }   
        
         //flag to block layout saving due to resizing components
         initComplete = true;
    }
    
    private void initTradesTab(Chart chart,Connection connection,JSplitPane tradesTab,JTable tradesTable,JScrollPane tradesScrollPane)
    {
        //Init trades tab chart
        final ChartWindow tradesWindow = new ChartWindow();
        ((JInternalFrame)tradesWindow).setClosable(false);
        ((JInternalFrame)tradesWindow).setResizable(false);
        ((JInternalFrame)tradesWindow).setMaximizable(false);
        tradesWindow.createChart(chart, this, new Dimension(100,100), new Point(0, 0), gui, dbManager, connection);
        tradesTab.setTopComponent(tradesWindow);
        tradesTab.setDividerLocation(0.5);

        setChartPanelListener(tradesWindow, tradesTable, tradesScrollPane);
        
        //needed for key event
        tradesTable.getSelectionModel().addListSelectionListener((ListSelectionEvent event) ->
        {
            if(event.getValueIsAdjusting())
                return;
            
            tradeTableRowSelected(tradesTable, tradesTab);
        });

        //Use key binding to re-map the 'Enter' functionality. Otherwise the selected row will increment on enter key release
        tradesTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "Enter");
        tradesTable.getActionMap().put("Enter", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                getTradeInfo(tradesTable);
            }
        });
                
        //needed for double click event
        tradesTable.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseReleased(MouseEvent mouseEvent)
            {
                if(tradeInfoDialog.isVisible())
                    tradeInfoDialog.setVisible(false);
                
                //don't need to check for single click, the listSelectionListener will pick up a single click event                
                if (mouseEvent.getClickCount() == 2 && tradesTable.getSelectedRow() != -1)
                {
                    getTradeInfo(tradesTable);
                }
            }
        });
        
        //For sorting table by header
        tradesTable.getTableHeader().addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent evt)
            {
                String[] stringTypes = getTradesTableStrings(tradesTable);
                
                orderKey = orderKey.equals("asc") ? "desc" : "asc";  
                int column = tradesTable.columnAtPoint(evt.getPoint());
                String orderBy = "";
                switch(column)
                {
                    case 0:
                        orderBy = "timestamp";
                        break;
                    case 1:
                        orderBy = "blockheight";
                        break;
                    case 2:
                        orderBy = "amount";
                        break;
                    case 3:
                        orderBy = stringTypes[1]; //total_foreign
                        break;
                    case 4:
                        orderBy = stringTypes[2]; //foreign_per_qort
                        break;
                    case 5:
                        orderBy = stringTypes[3]; //qort_per_foreign
                        break;    
                    case 6:
                        orderBy = "signature";
                        break;
                }        
                
                orderBy += " " + orderKey;

                try (Connection connection = ConnectionDB.getConnection("trades",Folders.DB.get()))
                {
                    fillTradesTable(connection, tradesTable,orderBy,stringTypes);
                }
                catch (Exception ex)
                {
                    BackgroundService.AppendLog(ex);
                }
            }
        });
    }
    
    /**
     *Needs to be set separately from init. When chart gets updated<br>
     *we need to re-initialise the listener that sets the domain crosshair<br>
     *and selects the trade in the trades table
     */
    private void setChartPanelListener(ChartWindow tradesWindow,JTable tradesTable,JScrollPane tradesScrollPane)
    {
        tradesWindow.chartMaker.setDomainCrosshair();
        //init trades tab chart listener (on click select closest trade in trades table)
        tradesWindow.chartMaker.chartPanel.addMouseListener(new MouseAdapter()
        {    
            @Override
            public void mouseReleased(MouseEvent evt)
            {
                //The domain crosshair value gets automatically set in the plot, but only after executing any code 
                //in the (chart)mouseListeners. We need the actual value of the domain crosshair to select the correct row
                //in the tradesTable. The crosshairTask waits for 100 ms before getting the value from the plot.
                crosshairTask = new TimerTask()
                {
                    @Override
                    public void run()
                    {
                        ChartPanel chartPanel = tradesWindow.chartMaker.chartPanel;
                        var cplot = (CustomCombinedDomainXYPlot) chartPanel.getChart().getPlot();
                        XYPlot plot = (XYPlot) cplot.getSubplots().get(0);    
                        long timestamp = ((long)plot.getDomainCrosshairValue());
                        String date = Utilities.DateFormatShort(timestamp);

                        int rowIndex = -1;
                         for(int i = 0; i < tradesTable.getRowCount(); i++)
                        {
                            String rowEntry =  tradesTable.getValueAt(i, 0).toString();                
                            if(rowEntry.toLowerCase().contains(date.toLowerCase()))
                            {
                                rowIndex = i;
                                break;
                            }             
                        }

                         if(rowIndex < 0)
                             return;

                        selectedByChart = true;
                        tradesTable.setRowSelectionInterval(rowIndex, rowIndex);

                        //scroll as close to middle as possible
                        JViewport viewport = tradesScrollPane.getViewport();
                        Dimension extentSize = viewport.getExtentSize();   
                        int visibleRows = extentSize.height/tradesTable.getRowHeight();

                        //first scroll all the way up (scrolling up to found name was not working properly)
                        tradesTable.scrollRectToVisible(new Rectangle(tradesTable.getCellRect(0, 0, true)));   

                        int scrollToRow = rowIndex + (visibleRows / 2);        
                        if(scrollToRow >= tradesTable.getRowCount())
                            scrollToRow = tradesTable.getRowCount() - 1;

                        if(rowIndex <= visibleRows / 2)
                            scrollToRow = 0;

                        tradesTable.scrollRectToVisible(new Rectangle(tradesTable.getCellRect(scrollToRow, 0, true)));   
                    }
                };                
                crosshairTimer.schedule(crosshairTask, 100);                
            }
        }); 
    }
    
    private void tradeTableRowSelected(JTable tradesTable,JSplitPane tradesTab)
    {
         //We don't want to setRangeAround if this event was triggered by user clicking on 
        //chartPanel (which triggers a list selection event)
        if(selectedByChart)
        {
            selectedByChart = false;
            return;                
        }

        if(tradesTable.getSelectedRow() < 0)
            return;

        try(Connection connection = ConnectionDB.getConnection("trades", Folders.DB.get()))
        {
            String signature =  tradesTable.getValueAt(tradesTable.getSelectedRow(), 6).toString();
            String table = getTradesTableStrings(tradesTable)[0];            
            long timestamp = (long) dbManager.GetItemValue(table, "timestamp", "signature", Utilities.SingleQuotedString(signature), connection);            
            timestamp = timestamp - (timestamp % 1000);//snapshots are placed per second, get the second value of the millisec timestamp
            
            ChartWindow cw = (ChartWindow)tradesTab.getTopComponent();
            cw.chartMaker.setRangeAround(timestamp, 1, cw.peaksCheckbox.isSelected());      
            var cplot = (CustomCombinedDomainXYPlot)cw.chartMaker.chartPanel.getChart().getPlot();
            ((XYPlot)cplot.getSubplots().get(0)).setDomainCrosshairValue((double)timestamp);    
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }
    
    private String[] getTradesTableStrings(JTable tradesTable)
    {
        String[] result = new String[4];
        if(tradesTable == ltcTradesTable)
        {
            result[0] = "ltc_trades";
            result[1] = "total_ltc";
            result[2] = "ltc_per_qort";
            result[3] = "qort_per_ltc";
            return result;
        }
        else if(tradesTable == dogeTradesTable)
        {
            result[0] = "doge_trades";
            result[1] = "total_doge";
            result[2] = "doge_per_qort";
            result[3] = "qort_per_doge";
            return result;
        }
        else if(tradesTable == ravenTradesTable)
        {
            result[0] = "raven_trades";
            result[1] = "total_raven";
            result[2] = "raven_per_qort";
            result[3] = "qort_per_raven";
            return result;
        }
        else if(tradesTable == digibyteTradesTable)
        {
            result[0] = "digibyte_trades";
            result[1] = "total_digibyte";
            result[2] = "digibyte_per_qort";
            result[3] = "qort_per_digibyte";
            return result;
        }
        else
        {
            result[0] = "btc_trades";
            result[1] = "total_btc";
            result[2] = "btc_per_qort";
            result[3] = "qort_per_btc";
            return result;
        }
    }
    
    private void getTradeInfo(JTable tradesTable)
    {        
        String signature = tradesTable.getValueAt(tradesTable.getSelectedRow(), 6).toString();      
        
        try(Connection connection = ConnectionDB.getConnection("trades", Folders.DB.get()))
        {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("select at_json from all_trades where signature = " + Utilities.SingleQuotedString(signature));
            
            while(resultSet.next())
            {
                String jsonString = resultSet.getString("at_json");
                JSONObject jso = new JSONObject(jsonString);
//                System.err.println(jso.toString(1));
                
                var model = (DefaultTableModel) tradeInfoTable.getModel();
                model.setRowCount(0);
            
                model.addRow(new Object[]{"Seller", jso.getString("qortalCreator")});
                model.addRow(new Object[]{"Buyer", jso.getString("qortalPartnerReceivingAddress")});
                model.addRow(new Object[]{"Foreign blockchain", jso.getString("foreignBlockchain")});
                model.addRow(new Object[]{"QORT amount",  tradesTable.getValueAt(tradesTable.getSelectedRow(), 2)});
                model.addRow(new Object[]{"Foreign amount", tradesTable.getValueAt(tradesTable.getSelectedRow(), 3)});
                model.addRow(new Object[]{"Price in QORT", tradesTable.getValueAt(tradesTable.getSelectedRow(), 5)});
                model.addRow(new Object[]{"Price in foreign", tradesTable.getValueAt(tradesTable.getSelectedRow(), 4)});
                model.addRow(new Object[]{"Block height", tradesTable.getValueAt(tradesTable.getSelectedRow(), 1)});
                model.addRow(new Object[]{"Listed on", Utilities.DateFormatShort(jso.getLong("creationTimestamp"))});
                model.addRow(new Object[]{"Sold on",tradesTable.getValueAt(tradesTable.getSelectedRow(), 0)});    
                model.addRow(new Object[]{"AT address", jso.getString("qortalAtAddress")});
                model.addRow(new Object[]{"AT acct", jso.getString("acctName")}); 
                model.addRow(new Object[]{"Signature",signature});       
            }
            
            SwingUtilities.invokeLater(()->
            {                
                tradeInfoDialog.pack();
                
                int cellHeight = tradeInfoTable.getCellRect(1, 1, true).height;
                int tableHeight = cellHeight * (tradeInfoTable.getRowCount() + 3) + 2;
                
                tradeInfoDialog.setSize(new Dimension(tradeInfoDialog.getWidth(),tableHeight));
                int x = gui.getX() + ((gui.getWidth() / 2) - (tradeInfoDialog.getWidth() / 2));
                int y = gui.getY() + ((gui.getHeight() / 2) - (tradeInfoDialog.getHeight() / 2));
                tradeInfoDialog.setLocation(x, y);
                tradeInfoDialog.setVisible(true);              
           
                tradeInfoTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);                
                tradeInfoTable.getColumnModel().getColumn(0).setMinWidth(175);
                tradeInfoTable.getColumnModel().getColumn(1).setPreferredWidth(600);
            });            
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }        
    }
    
    private void setTradesChartsStyle(String layoutName, String folder, Extensions extension,String table)
    {       
        ChartStyle style = copyChartStyle(table, layoutName, folder, extension);
        
        JSplitPane[] tradesTabs = {tradesTab,dogeTradesTab,ravenTradesTab,digibyteTradesTab,btcTradesTab};
        
        for(JSplitPane tab : tradesTabs)
        {
            ChartWindow tradesWindow = (ChartWindow) tab.getTopComponent();
            if(style != null)
                setChartStyle(tradesWindow,style);
            tradesWindow.chartMaker.toggleSnapshots(true);
            tradesWindow.chartMaker.toggleText(false, tradesWindow.getTitle(), tradesWindow.textColor);  
            tradesWindow.chartMaker.setRange(0,true);
            tradesWindow.chartMaker.toggleVolume(true);            
        }
            
        enableTradeTabItems();//must be done after setStyle, items get disabled there
    }
    
    private void enableTradeTabItems()
    {
        JSplitPane[] tradesTabs = {tradesTab,dogeTradesTab,ravenTradesTab,digibyteTradesTab,btcTradesTab};
        
        for(JSplitPane tab : tradesTabs)
        {
            ChartWindow tradesWindow = (ChartWindow) tradesTab.getTopComponent();
            tradesWindow.flipPairButton.setVisible(true);
            tradesWindow.peaksCheckbox.setVisible(true);
            tradesWindow.showVolumeBox.setVisible(true);            
        }
    }
    
    protected void hideAllChartDialogs()
    {
        for(JInternalFrame frame : desktopPane.getAllFrames())
        {
            ((ChartWindow)frame).chartMaker.chartDialog.setVisible(false);
        }
        
        //on app init the charts are not added to splitPane yet, initComplete flag returns true though 
        //this is probably due to splitpane action happening on EDT and flag being set before that is completed
        if(dogeTradesTab.getTopComponent() instanceof  ChartWindow == false)
            return;
        
        ((ChartWindow) dogeTradesTab.getTopComponent()).chartMaker.chartDialog.setVisible(false);
        ((ChartWindow) btcTradesTab.getTopComponent()).chartMaker.chartDialog.setVisible(false);
        ((ChartWindow)tradesTab.getTopComponent()).chartMaker.chartDialog.setVisible(false);
    }
    
    private void setChartItemVisible(String title, boolean isVisible)
    {
        for(Component c : chartsList.getComponents())
        {
            ChartItem item = (ChartItem) c;
            
            if(title.equals(item.title))
            {
                item.setVisible(isVisible);
                return;
            }
        }
    }
    
    private void addChartItemToList(String type,ImageIcon icon1,ImageIcon icon2,ChartItem[] itemsList,boolean isTrade)
    {         
        ChartItem item = new ChartItem();
        String title = typeToTitleMap.get(type);
        item.initialise(gui,title,icon1,icon2,isTrade);
        
        int indexInList = Integer.parseInt(titleToIndexMap.get(title));                        
        itemsList[indexInList] = item;
    }
    
    private boolean loadLayout(String layoutName,ChartStyle style)
    {        
        File layoutFile = new File(System.getProperty("user.dir")
                + "/" + Folders.LAYOUTS.get() + "/" + layoutName + ".layout");
        
        if(!layoutFile.exists())
            return false;
        
        //on init or if user chose not to keep current style load the gui style from the layout file supplied as arg
        if(!keepStyleBox.isSelected() || !initComplete)
        {
            setGuiValues(layoutName,Folders.LAYOUTS.get(),Extensions.LAYOUT);  
            updateGuiItems();            
        }
        
        try(Connection layoutConn = 
                ConnectionDB.getConnection(layoutName, Folders.LAYOUTS.get(),Extensions.LAYOUT))
        {       
            //We need the trades connection to get the trades/prices tables for the chart windows
            try(Connection tradesConn = ConnectionDB.getConnection("trades", Folders.DB.get()))
            {      
                for(String table : dbManager.GetTables(layoutConn))
                {
                    if(table.equals("GUI"))
                        continue;
                    
                    double x = (double)dbManager.GetFirstItem(table, "x", layoutConn);
                    x *= desktopPane.getWidth();
                    double y = (double)dbManager.GetFirstItem(table, "y", layoutConn);
                    y *= desktopPane.getHeight();
                    double width = (double)dbManager.GetFirstItem(table, "width", layoutConn);
                    width *= desktopPane.getWidth();
                    double height = (double)dbManager.GetFirstItem(table, "height", layoutConn);
                    height *= desktopPane.getHeight(); 
                                        
                    ChartWindow cw = new ChartWindow();
                    cw.createChart(Chart.valueOf(table), this, new Dimension((int)width,(int)height), new Point((int)x,(int) y), gui, dbManager, tradesConn);                    
                                        
                    if(!initComplete)
                    {
                        style = copyChartStyle(table, layoutName, Folders.LAYOUTS.get(), Extensions.LAYOUT);
                        setChartStyle(cw, style);
                    }   
                    else
                    {
                        //if user chooses to keep current style and a frame is selected (not null) set the style
                        //of all new frames to the style of the selected frame
                        if(keepStyleBox.isSelected())
                        {
                            //if for some reason no reference frame for style is found, the default style for charts will be loaded (i.e. this call will be skipped) 
                            if(style != null)
                                setChartStyle(cw, style);
                        }
                        else
                        {
                            style = copyChartStyle(table, layoutName, Folders.LAYOUTS.get(), Extensions.LAYOUT);
                            if(style != null)
                                setChartStyle(cw,style);
                        }   
                    }
                    
                    desktopPane.add(cw);
                    
                    //Remove corresponding chartItem from items list
                    for(Component c : chartsList.getComponents())
                    {
                        if(((ChartItem)c).title.equals(cw.getTitle()))
                            c.setVisible(false);
                    }
                }                
            }
            catch (Exception e)
            {
                BackgroundService.AppendLog(e);
            }
            
            //If user loaded a layout from the list, set the current layout settings to that layout
            if(!layoutName.equals("CurrentLayout"))
                saveLayout("CurrentLayout", null, true, false);
            
            return true;
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
            return false;
        }
    }
    
     protected void addToChartsList(String title)
    {
        if(!initComplete)
            return;
        
        ChartItem item;
        for(Component c : chartsList.getComponents())
        {
            item = (ChartItem) c;
            
            if(item.title.equals(title))
            {
                item.updateItemUI();
                item.setVisible(true);
                break;
            }
        }      
    }
     
     /** * This function should only be called for the CurrentLayout file, when the user closes a frame<br>
      it should be removed from the current layout file, when the user loads a layout file, all the open<br>
      frames get closed and their windowListener will call this function.We don't want to drop the<br>
      layout tables from the layout table that is about to be loaded
     * @param tableToDrop
     * @param title
     * @param layoutName
     * @return */
     protected boolean dropChartFromLayout(String tableToDrop, String title, String layoutName)
    {
        if(!layoutName.equals("CurrentLayout"))
            return false;
        
        if(!initComplete)
            return false;
        
//        System.out.println("Deleting chart " + tableToDrop + " from " + layoutName + "...");
        
        File layoutFile = new File(System.getProperty("user.dir") 
                + "/" + Folders.LAYOUTS.get() + "/" + layoutName + ".layout");
        if(!layoutFile.exists())
            return false;
        
        try(Connection connection =
                ConnectionDB.getConnection(layoutName, Folders.LAYOUTS.get(),Extensions.LAYOUT))
        {  
            
            if(!dbManager.TableExists(tableToDrop, connection))
                return false;
            else
            {
                dbManager.ExecuteUpdate("drop table " + tableToDrop, connection);   
                selectClosestFrame();
                return true;
            }
        }
        catch(Exception e)
        {
            BackgroundService.AppendLog(e);
            return false;
        }        
    }
    
    protected boolean saveLayout(String layoutName,Chart type,boolean appliedToAll,boolean guiOnly)
    {
        if(!initComplete)
            return false;
        
//        System.err.println("Saving...");
        
        File layoutFile = new File(System.getProperty("user.dir") 
                + "/" + Folders.LAYOUTS.get() + "/" + layoutName + ".layout");
        if(!layoutFile.exists())
            ConnectionDB.CreateDatabase(layoutName, Folders.LAYOUTS.get(),Extensions.LAYOUT);
        
        try(Connection connection = ConnectionDB.getConnection(layoutName, Folders.LAYOUTS.get(),Extensions.LAYOUT))
        {     
            //CAUTION: ANY NEW TABLES MUST BE CREATED AFTER THE DROP ALL OBJECTS COMMAND
            //drop all tables or selected chart's table
            if(!guiOnly)
            {
                if(appliedToAll)
                {
                    dbManager.ExecuteUpdate("drop all objects", connection);
                }
                else if(dbManager.TableExists(type.toString(), connection) && !guiOnly)
                        dbManager.ExecuteUpdate("drop table " + type.toString(), connection);                   
            }            
            
             if(!dbManager.TableExists("gui", connection))
                 createGuiTable(connection);             
            fillGuiTable(connection);            
            if(guiOnly)
                return true;
            
            for(JInternalFrame frame : desktopPane.getAllFrames())
            {
                ChartWindow cw = (ChartWindow) frame;
                
                if(!appliedToAll && type != cw.type)
                    continue;
                
                createLayoutTable(cw.type.toString(), connection);
                fillLayoutTable(cw, connection);
            }
            
            return true;
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
            return false;
        }
    }
    
    private void createLayoutTable(String table,Connection connection)
    {
        dbManager.CreateTable(new String[]{table,
            "id","tinyint",
            "font","varchar(30)",
            "font_size","tinyint",
            "chart_color","int",
            "plot_color","int",
            "crosshairs_color","int",
            "dialog_color","int",
            "dialog_txt_color","int",
            "line_color","int",
            "grid_color","int",
            "text_color","int",
            "slider_value","int",
            "show_snaps","boolean",
            "show_text","boolean",
            "show_sub","boolean",
            "show_dialog","boolean",
            "show_x_crosshairs","boolean", 
            "show_y_crosshair","boolean", 
            "draw_x","boolean",
            "draw_y","boolean",
            "show_ma","boolean",
            "average_by","tinyint",
            "show_volume","boolean",
            "x","double", "y","double","width","double","height","double"}, connection);
    }
    
    private void fillLayoutTable(ChartWindow cw, Connection connection)
    {        
        dbManager.InsertIntoDB(new String[]{cw.type.toString(),
            "id","0",
            "font",Utilities.SingleQuotedString(cw.font),
            "font_size",String.valueOf(cw.fontSize),
            "chart_color", String.valueOf(cw.chartColor.getRGB()),
            "plot_color", String.valueOf(cw.plotColor.getRGB()),
            "crosshairs_color", String.valueOf(cw.crosshairsColor.getRGB()),
            "dialog_color", String.valueOf(cw.dialogColor.getRGB()),
            "dialog_txt_color", String.valueOf(cw.dialogTextColor.getRGB()),
            "line_color", String.valueOf(cw.lineColor.getRGB()),
            "grid_color", String.valueOf(cw.gridColor.getRGB()),
            "text_color", String.valueOf(cw.textColor.getRGB()),
            "slider_value", String.valueOf(cw.sliderValue),
            "show_snaps", String.valueOf(cw.showSnapshots),
            "show_text", String.valueOf(cw.showText),
            "show_sub", String.valueOf(cw.showSubtitle),
            "show_dialog", String.valueOf(cw.showDialog),
            "show_x_crosshairs", String.valueOf(cw.showXcrosshairs),
            "show_y_crosshair", String.valueOf(cw.showYcrosshair),
            "draw_x", String.valueOf(cw.drawX),
            "draw_y", String.valueOf(cw.drawY),
            "show_ma", String.valueOf(cw.showMovingAverage),
            "average_by", String.valueOf(cw.averageBy),
            "show_volume", String.valueOf(cw.showVolume),
            "x",String.valueOf(((double) cw.getX() / desktopPane.getWidth())),
            "y",String.valueOf(((double) cw.getY()/ desktopPane.getHeight())),
            "width",String.valueOf(((double) cw.getWidth()/ desktopPane.getWidth())),
            "height",String.valueOf(((double) cw.getHeight()/ desktopPane.getHeight()))}, connection);    
    }
    
    private void createGuiTable(Connection connection)
    {
        dbManager.CreateTable(new String[]{"gui",
            "divider_color","int",
            "background_color","int",
            "component_color","int",
            "gui_font_color","int",
            "component_font_color","int",
            "font","varchar(30)",
            "look_and_feel","varchar(30)"}, connection);
    }
    
    private void fillGuiTable(Connection connection)
    {      
        dbManager.ExecuteUpdate("delete from gui", connection);      
        
        dbManager.InsertIntoDB(new String[]{"gui",
            "divider_color",String.valueOf(dividerColor),
            "background_color",String.valueOf(guiBackgroundColor),
            "component_color",String.valueOf(guiComponentColor),
            "gui_font_color",String.valueOf(guiFontColor),
            "component_font_color",String.valueOf(componentFontColor),
            "font",String.valueOf(Utilities.SingleQuotedString(fontsBox.getSelectedItem().toString())),
            "look_and_feel",String.valueOf(Utilities.SingleQuotedString(UIManager.getLookAndFeel().getName()))}, connection);     
    }
    
    protected String typeToTable(Chart type)
    {        
        switch(type)
        {
            case LTC_QORT:
            case QORT_LTC:
                return "LTC_QORT";
            case BTC_QORT:
            case QORT_BTC:
                return "BTC_QORT";
            case DOGE_QORT:
            case QORT_DOGE:
                return "DOGE_QORT";
            case RAVEN_QORT:
            case QORT_RAVEN:
                return "RAVEN_QORT";
            case DIGIBYTE_QORT:
            case QORT_DIGIBYTE:
                return "DIGIBYTE_QORT";
            case USD_QORT:
            case QORT_USD:
                return "USD_QORT";
            case USD_LTC:
            case LTC_USD:
                return "USD_LTC";
            case USD_DOGE:
            case DOGE_USD:
                return "USD_DOGE";
            case USD_RAVEN:
            case RAVEN_USD:
                return "USD_RAVEN";
            case USD_DIGIBYTE:
            case DIGIBYTE_USD:
                return "USD_DIGIBYTE";
            case USD_BTC:
            case BTC_USD:
                return "USD_BTC";
            case QORT_LTC_TRADE:
            case LTC_QORT_TRADE:
            case QORT_LTC_AMOUNT:
            case LTC_QORT_AMOUNT:
            case LTC_TRADES_TOTAL:
                return "LTC_TRADES";
            case QORT_BTC_TRADE:
            case BTC_QORT_TRADE:
            case QORT_BTC_AMOUNT:
            case BTC_QORT_AMOUNT:
            case BTC_TRADES_TOTAL:
                return "BTC_TRADES";
            case QORT_DOGE_TRADE:
            case DOGE_QORT_TRADE:
            case QORT_DOGE_AMOUNT:
            case DOGE_QORT_AMOUNT:
            case DOGE_TRADES_TOTAL:
                return "DOGE_TRADES";
            case QORT_RAVEN_TRADE:
            case RAVEN_QORT_TRADE:
            case QORT_RAVEN_AMOUNT:
            case RAVEN_QORT_AMOUNT:
            case RAVEN_TRADES_TOTAL:
                return "RAVEN_TRADES";
            case QORT_DIGIBYTE_TRADE:
            case DIGIBYTE_QORT_TRADE:
            case QORT_DIGIBYTE_AMOUNT:
            case DIGIBYTE_QORT_AMOUNT:
            case DIGIBYTE_TRADES_TOTAL:
                return "DIGIBYTE_TRADES";
            case TOTAL_TRADES:
            case TOTAL_QORT_TRADED:
                return "ALL_TRADES";
            default:
                return null;
        }        
    }
    
    private void populateTypeToTitle()
    {
        ArrayList<String> list1 = new ArrayList<>();
        ArrayList<String> list2 = new ArrayList<>();
        list1.add("LTC_QORT"); list2.add("Litecoin to QORT price");
        list1.add("QORT_LTC"); list2.add("QORT to Litecoin price");
        list1.add("BTC_QORT"); list2.add("Bitcoin to QORT price");
        list1.add("QORT_BTC"); list2.add("QORT to Bitcoin price");
        list1.add("DOGE_QORT"); list2.add("Dogecoin to QORT price");
        list1.add("QORT_DOGE"); list2.add("QORT to Dogecoin price");        
        list1.add("RAVEN_QORT"); list2.add("Ravencoin to QORT price");
        list1.add("QORT_RAVEN"); list2.add("QORT to Ravencoin price");        
        list1.add("DIGIBYTE_QORT"); list2.add("Digibyte to QORT price");
        list1.add("QORT_DIGIBYTE"); list2.add("QORT to Digibyte price");
        
        list1.add("USD_QORT"); list2.add("US Dollar to QORT price");
        list1.add("QORT_USD"); list2.add("QORT to US Dollar price");        
        list1.add("USD_LTC"); list2.add("US Dollar to Litecoin price");
        list1.add("LTC_USD"); list2.add("Litecoin to US Dollar price");
        list1.add("USD_DOGE"); list2.add("US Dollar to Dogecoin price");
        list1.add("DOGE_USD"); list2.add("Dogecoin to US Dollar price");        
        list1.add("USD_RAVEN"); list2.add("US Dollar to Ravencoin price");
        list1.add("RAVEN_USD"); list2.add("Ravencoin to US Dollar price");        
        list1.add("USD_DIGIBYTE"); list2.add("US Dollar to Digibyte price");
        list1.add("DIGIBYTE_USD"); list2.add("Digibyte to US Dollar price");        
        list1.add("USD_BTC"); list2.add("US Dollar to Bitcoin price");
        list1.add("BTC_USD"); list2.add("Bitcoin to US Dollar price");
        
        list1.add("QORT_LTC_TRADE"); list2.add("LTC to QORT trades (in QORT)");
        list1.add("LTC_QORT_TRADE"); list2.add("LTC to QORT trades (in LTC)");
        list1.add("QORT_LTC_AMOUNT"); list2.add("Total QORT to LTC traded");
        list1.add("LTC_QORT_AMOUNT"); list2.add("Total LTC to QORT traded");
        list1.add("LTC_TRADES_TOTAL"); list2.add("LTC to QORT trades (Total)");
        
        list1.add("QORT_BTC_TRADE"); list2.add("BTC to QORT trades (in QORT)");
        list1.add("BTC_QORT_TRADE"); list2.add("BTC to QORT trades (in BTC)");
        list1.add("QORT_BTC_AMOUNT"); list2.add("Total QORT to BTC traded");
        list1.add("BTC_QORT_AMOUNT"); list2.add("Total BTC to QORT traded");
        list1.add("BTC_TRADES_TOTAL"); list2.add("BTC to QORT trades (Total)");
        
        list1.add("QORT_DOGE_TRADE"); list2.add("DOGE to QORT trades (in QORT)");
        list1.add("DOGE_QORT_TRADE"); list2.add("DOGE to QORT trades (in DOGE)");
        list1.add("QORT_DOGE_AMOUNT"); list2.add("Total QORT to DOGE traded");
        list1.add("DOGE_QORT_AMOUNT"); list2.add("Total DOGE to QORT traded");
        list1.add("DOGE_TRADES_TOTAL"); list2.add("DOGE to QORT trades (Total)");
        
        list1.add("QORT_RAVEN_TRADE"); list2.add("RAVEN to QORT trades (in QORT)");
        list1.add("RAVEN_QORT_TRADE"); list2.add("RAVEN to QORT trades (in RAVEN)");
        list1.add("QORT_RAVEN_AMOUNT"); list2.add("Total QORT to RAVEN traded");
        list1.add("RAVEN_QORT_AMOUNT"); list2.add("Total RAVEN to QORT traded");
        list1.add("RAVEN_TRADES_TOTAL"); list2.add("RAVEN to QORT trades (Total)");
        
        list1.add("QORT_DIGIBYTE_TRADE"); list2.add("DIGIBYTE to QORT trades (in QORT)");
        list1.add("DIGIBYTE_QORT_TRADE"); list2.add("DIGIBYTE to QORT trades (in DIGIBYTE)");
        list1.add("QORT_DIGIBYTE_AMOUNT"); list2.add("Total QORT to DIGIBYTE traded");
        list1.add("DIGIBYTE_QORT_AMOUNT"); list2.add("Total DIGIBYTE to QORT traded");
        list1.add("DIGIBYTE_TRADES_TOTAL"); list2.add("DIGIBYTE to QORT trades (Total)");
        
        list1.add("TOTAL_TRADES"); list2.add("All trades (Total)");
        list1.add("TOTAL_QORT_TRADED"); list2.add("Total QORT traded");
        typeToTitleMap = new BidirectionalMap(list1, list2);
    }
    
    /**This list is used to sort the chartsList items in the preferred order.<br>
     Using a bi-di list we can find the index of each item by their title*/
    private void populateTitleToIndex()
    {
        ArrayList<String> indexList = new ArrayList<>();
        ArrayList<String> titlesList = new ArrayList<>();
        indexList.add("0"); titlesList.add("QORT to Litecoin price");
        indexList.add("1"); titlesList.add("Litecoin to QORT price");        
        indexList.add("2"); titlesList.add("QORT to US Dollar price");
        indexList.add("3"); titlesList.add("US Dollar to QORT price");        
        indexList.add("4"); titlesList.add("QORT to Dogecoin price");
        indexList.add("5"); titlesList.add("Dogecoin to QORT price");           
        indexList.add("6"); titlesList.add("QORT to Ravencoin price");
        indexList.add("7"); titlesList.add("Ravencoin to QORT price");     
        indexList.add("8"); titlesList.add("QORT to Digibyte price");
        indexList.add("9"); titlesList.add("Digibyte to QORT price"); 
        indexList.add("10"); titlesList.add("QORT to Bitcoin price");
        indexList.add("11"); titlesList.add("Bitcoin to QORT price");        
        indexList.add("12"); titlesList.add("Litecoin to US Dollar price");
        indexList.add("13"); titlesList.add("US Dollar to Litecoin price");        
        indexList.add("14"); titlesList.add("Dogecoin to US Dollar price");
        indexList.add("15"); titlesList.add("US Dollar to Dogecoin price");           
        indexList.add("16"); titlesList.add("Ravencoin to US Dollar price");
        indexList.add("17"); titlesList.add("US Dollar to Ravencoin price");   
        indexList.add("18"); titlesList.add("Digibyte to US Dollar price");
        indexList.add("19"); titlesList.add("US Dollar to Digibyte price");           
        indexList.add("20"); titlesList.add("Bitcoin to US Dollar price");
        indexList.add("21"); titlesList.add("US Dollar to Bitcoin price");   
        
        indexList.add("22"); titlesList.add("LTC to QORT trades (in LTC)");
        indexList.add("23"); titlesList.add("LTC to QORT trades (in QORT)");     
        indexList.add("24"); titlesList.add("Total QORT to LTC traded");
        indexList.add("25"); titlesList.add("Total LTC to QORT traded");
        indexList.add("26"); titlesList.add("LTC to QORT trades (Total)");  
        
        indexList.add("27"); titlesList.add("DOGE to QORT trades (in DOGE)");
        indexList.add("28"); titlesList.add("DOGE to QORT trades (in QORT)");  
        indexList.add("29"); titlesList.add("Total QORT to DOGE traded");
        indexList.add("30"); titlesList.add("Total DOGE to QORT traded");
        indexList.add("31"); titlesList.add("DOGE to QORT trades (Total)");
        
        indexList.add("32"); titlesList.add("RAVEN to QORT trades (in RAVEN)");
        indexList.add("33"); titlesList.add("RAVEN to QORT trades (in QORT)");  
        indexList.add("34"); titlesList.add("Total QORT to RAVEN traded");
        indexList.add("35"); titlesList.add("Total RAVEN to QORT traded");
        indexList.add("36"); titlesList.add("RAVEN to QORT trades (Total)");
        
        indexList.add("37"); titlesList.add("DIGIBYTE to QORT trades (in DIGIBYTE)");
        indexList.add("38"); titlesList.add("DIGIBYTE to QORT trades (in QORT)");  
        indexList.add("39"); titlesList.add("Total QORT to DIGIBYTE traded");
        indexList.add("40"); titlesList.add("Total DIGIBYTE to QORT traded");
        indexList.add("41"); titlesList.add("DIGIBYTE to QORT trades (Total)");
        
        indexList.add("42"); titlesList.add("BTC to QORT trades (in BTC)");
        indexList.add("43"); titlesList.add("BTC to QORT trades (in QORT)");
        indexList.add("44"); titlesList.add("Total QORT to BTC traded");
        indexList.add("45"); titlesList.add("Total BTC to QORT traded");
        indexList.add("46"); titlesList.add("BTC to QORT trades (Total)");
       
        indexList.add("47"); titlesList.add( "All trades (Total)");
        indexList.add("48"); titlesList.add("Total QORT traded");
        titleToIndexMap = new BidirectionalMap(indexList, titlesList);
    }
    
    private void populateTypeToQueryMap()
    {
        typeToQueryMap = new HashMap<>();
        
        typeToQueryMap.put("LTC_QORT", "select timestamp,ltc_per_qort,amount from ltc_qort");
        typeToQueryMap.put("QORT_LTC", "select timestamp,qort_per_ltc,amount from ltc_qort");
        typeToQueryMap.put("BTC_QORT", "select timestamp,btc_per_qort,amount from btc_qort");
        typeToQueryMap.put("QORT_BTC", "select timestamp,qort_per_btc,amount from btc_qort");
        typeToQueryMap.put("DOGE_QORT", "select timestamp,doge_per_qort,amount from doge_qort");
        typeToQueryMap.put("QORT_DOGE", "select timestamp,qort_per_doge,amount from doge_qort");        
        typeToQueryMap.put("RAVEN_QORT", "select timestamp,raven_per_qort,amount from raven_qort");
        typeToQueryMap.put("QORT_RAVEN", "select timestamp,qort_per_raven,amount from raven_qort");        
        typeToQueryMap.put("DIGIBYTE_QORT", "select timestamp,digibyte_per_qort,amount from digibyte_qort");
        typeToQueryMap.put("QORT_DIGIBYTE", "select timestamp,qort_per_digibyte,amount from digibyte_qort");
        
        typeToQueryMap.put("USD_QORT", "select timestamp,usd_per_qort from usd_qort");
        typeToQueryMap.put("QORT_USD", "select timestamp,qort_per_usd from usd_qort");
        typeToQueryMap.put("USD_LTC", "select timestamp,usd_per_ltc from usd_ltc");
        typeToQueryMap.put("LTC_USD", "select timestamp,ltc_per_usd from usd_ltc");
        typeToQueryMap.put("USD_DOGE", "select timestamp,usd_per_doge from usd_doge");
        typeToQueryMap.put("DOGE_USD", "select timestamp,doge_per_usd from usd_doge");        
        typeToQueryMap.put("USD_RAVEN", "select timestamp,usd_per_raven from usd_raven");
        typeToQueryMap.put("RAVEN_USD", "select timestamp,raven_per_usd from usd_raven");        
        typeToQueryMap.put("USD_DIGIBYTE", "select timestamp,usd_per_digibyte from usd_digibyte");
        typeToQueryMap.put("DIGIBYTE_USD", "select timestamp,digibyte_per_usd from usd_digibyte");        
        typeToQueryMap.put("USD_BTC", "select timestamp,usd_per_btc from usd_btc");
        typeToQueryMap.put("BTC_USD", "select timestamp,btc_per_usd from usd_btc");
        
        typeToQueryMap.put("QORT_LTC_TRADE", "select timestamp,qort_per_ltc,amount from ltc_trades");
        typeToQueryMap.put("LTC_QORT_TRADE", "select timestamp,ltc_per_qort,amount from ltc_trades");
        typeToQueryMap.put("QORT_LTC_AMOUNT", "select timestamp,amount from ltc_trades");
        typeToQueryMap.put("LTC_QORT_AMOUNT", "select timestamp,total_ltc from ltc_trades");
        typeToQueryMap.put("LTC_TRADES_TOTAL", "select timestamp from ltc_trades");     
        
        typeToQueryMap.put("QORT_BTC_TRADE", "select timestamp,qort_per_btc,amount from btc_trades");
        typeToQueryMap.put("BTC_QORT_TRADE", "select timestamp,btc_per_qort,amount from btc_trades");
        typeToQueryMap.put("QORT_BTC_AMOUNT", "select timestamp,amount from btc_trades");
        typeToQueryMap.put("BTC_QORT_AMOUNT", "select timestamp,total_btc from btc_trades");
        typeToQueryMap.put("BTC_TRADES_TOTAL", "select timestamp from btc_trades");     
        
        typeToQueryMap.put("QORT_DOGE_TRADE", "select timestamp,qort_per_doge,amount from doge_trades");
        typeToQueryMap.put("DOGE_QORT_TRADE", "select timestamp,doge_per_qort,amount from doge_trades");   
        typeToQueryMap.put("QORT_DOGE_AMOUNT", "select timestamp,amount from doge_trades");
        typeToQueryMap.put("DOGE_QORT_AMOUNT", "select timestamp,total_doge from doge_trades");
        typeToQueryMap.put("DOGE_TRADES_TOTAL", "select timestamp from doge_trades"); 
        
        typeToQueryMap.put("QORT_RAVEN_TRADE", "select timestamp,qort_per_raven,amount from raven_trades");
        typeToQueryMap.put("RAVEN_QORT_TRADE", "select timestamp,raven_per_qort,amount from raven_trades");   
        typeToQueryMap.put("QORT_RAVEN_AMOUNT", "select timestamp,amount from raven_trades");
        typeToQueryMap.put("RAVEN_QORT_AMOUNT", "select timestamp,total_raven from raven_trades");
        typeToQueryMap.put("RAVEN_TRADES_TOTAL", "select timestamp from raven_trades"); 
        
        typeToQueryMap.put("QORT_DIGIBYTE_TRADE", "select timestamp,qort_per_digibyte,amount from digibyte_trades");
        typeToQueryMap.put("DIGIBYTE_QORT_TRADE", "select timestamp,digibyte_per_qort,amount from digibyte_trades");   
        typeToQueryMap.put("QORT_DIGIBYTE_AMOUNT", "select timestamp,amount from digibyte_trades");
        typeToQueryMap.put("DIGIBYTE_QORT_AMOUNT", "select timestamp,total_digibyte from digibyte_trades");
        typeToQueryMap.put("DIGIBYTE_TRADES_TOTAL", "select timestamp from digibyte_trades"); 
        
        typeToQueryMap.put("TOTAL_TRADES","select timestamp from all_trades");
        typeToQueryMap.put("TOTAL_QORT_TRADED","select timestamp,amount from all_trades");
    }
    
    protected void showStyleMenu()
    {
        tabbedPane.setSelectedIndex(0);
        styleMenuScrollpane.setVisible(true);
        toggleStyleButton.setSelected(true);
        chartsMenu.setVisible(true);
    }
     
     private void showColorChooser(JLabel colorBoxLabel)
     {
         int x,y;
         int width,height;
         
        //only set location and size if chooser was closed more than 2.5 seconds ago
        if(System.currentTimeMillis() - colorChooser.lastClosedTime > 2500)
        {
            if(isGuiLabel(colorBoxLabel))
            {
               x = desktopPane.getLocationOnScreen().x;
               y = desktopPane.getLocationOnScreen().y;
               width = desktopPane.getWidth();
               height = desktopPane.getHeight();
            }
            else
            {             
                x = chartsTab.getLocationOnScreen().x - 5;
                y = chartsTab.getLocationOnScreen().y;
                int addWidth = layoutMenuScrollpane.isVisible() ? layoutMenu.getWidth() : 0;
                width = chartsMenu.getWidth() + styleMenu.getWidth() + addWidth + 25;
                height = chartsTab.getHeight() + 5;
            }
            colorChooser.setLocation(x,y);
            colorChooser.setPreferredSize(new Dimension(width,height));
            colorChooser.pack();
        }   
        
        //show dialog for selected frame if user is changing dialog or dialog text color
        if(colorBoxLabel == dialogColorLabel || colorBoxLabel == dialogTxtColorLabel)
        {
            if(desktopPane.getSelectedFrame() != null )
            {
                var chartWindow = (ChartWindow)desktopPane.getSelectedFrame();
                var dialog = chartWindow.chartMaker.chartDialog;
                x = chartWindow.getLocationOnScreen().x +(chartWindow.getWidth() / 2) - (dialog.getWidth() / 2);
                y = chartWindow.getLocationOnScreen().y + (chartWindow.getHeight() / 2) - (dialog.getHeight() / 2);
                dialog.setLocation(x, y);
                dialog.setVisible(true);
            }
        }
        
        colorChooser.setColorBoxLabel(colorBoxLabel);
        colorChooser.setTitle(colorBoxLabel.getToolTipText());
        colorChooser.setVisible(true);
     }
     
     public void changeColor(JLabel label,Color color)
     {
         if(isGuiLabel(label))
         {
             //cannot use button.getBackground() -> might not 
             //be set yet at the moment of db entry
             dividerColor = dividerColorLabel.getBackground().getRGB(); // color.getRGB();
             guiBackgroundColor = guiBackgroundLabel.getBackground().getRGB();
             guiComponentColor = guiComponentsLabel.getBackground().getRGB();
             guiFontColor = guiFontColorLabel.getBackground().getRGB();
             componentFontColor = componentFontLabel.getBackground().getRGB(); 
             updateGuiItems();
             //apply to all to simplify
            saveLayout("CurrentLayout", null, true,true);
             return;
         }
         
         boolean applyToSelectedOnly = desktopPane.getSelectedFrame() != null && !applyToAllChartsBox.isSelected();
        
        if(applyToSelectedOnly)
        {
            ChartWindow chartWindow = (ChartWindow) desktopPane.getSelectedFrame();
            applyColor(label, chartWindow, color);  
            saveLayout("CurrentLayout", chartWindow.type, false,false);
        }
        else if(applyToAllChartsBox.isSelected())
        {    
            for(JInternalFrame frame : desktopPane.getAllFrames())
            {
                ChartWindow chartWindow = (ChartWindow) frame;
                applyColor(label, chartWindow, color);
            }
            saveLayout("CurrentLayout", null, true,false);
        }             
     }
     
     private boolean isGuiLabel(JLabel label)
     {
         if(label == dividerColorLabel)
             return true;
         if(label == guiBackgroundLabel)
             return true;
         if(label == guiFontColorLabel)
             return true;
         if(label == componentFontLabel)
             return true;
         
        return label == guiComponentsLabel;
     }
     
     protected void updateGuiItems()
     {  
         //this function gets called by listeners (fontsBox) before GUI is done initialising
         if(!initComplete)
             return;
         
         Color bgColor = new Color(guiBackgroundColor);
         Color cmpColor = new Color(guiComponentColor);
         Color fontColor = new Color(guiFontColor);
         Color cmpFontColor = new Color(componentFontColor);         
         
         // <editor-fold defaultstate="collapsed" desc="Update local components">  
         
         for(Component c : chartsList.getComponents())
         {
             var item = (ChartItem) c;
             setChartItemUI(item, fontColor, bgColor, cmpColor,cmpFontColor);
         }        
         
        dividerButton.setBackground(new Color(dividerColor));
        divider.setBackground(bgColor);      
         
        var fontBoxRenderer = (ComboRenderer) fontsBox.getRenderer();
        fontsBox.setBackground(cmpColor);
        fontsBox.setForeground(cmpFontColor);
        fontBoxRenderer.bgColor = cmpColor;
        fontBoxRenderer.fgColor = cmpFontColor;
         
         Font listFont = new Font(fontsBox.getSelectedItem().toString(), Font.PLAIN, stylesList.getFont().getSize());
         
         //These lists will not show up as components of menu's 
         //they are children of scrollPanes
         stylesList.setBackground(cmpColor);
         stylesList.setFont(listFont);
         stylesList.setForeground(cmpFontColor);         
         
         layoutsList.setBackground(cmpColor);
         layoutsList.setFont(listFont);
         layoutsList.setForeground(cmpFontColor);
         
         arrangementsList.setBackground(cmpColor);
         arrangementsList.setFont(listFont);
         arrangementsList.setForeground(cmpFontColor);
         
         tipDialogPanel.setBackground(bgColor);
         tipDialogLabel.setBackground(bgColor);
         tipDialogLabel.setOpaque(true);
         tipDialogLabel.setForeground(fontColor);
         tipDialogLabel.setFont(new Font(fontsBox.getSelectedItem().toString(), 
                 tipDialogLabel.getFont().getStyle(), tipDialogLabel.getFont().getSize()));
         
         chartsMenu.setBackground(bgColor);
         for(Component c : chartsMenu.getComponents())
         {             
             if(c instanceof JToggleButton)
             {
                 c.setBackground(cmpColor);
                 c.setFont(new Font(fontsBox.getSelectedItem().toString(), Font.PLAIN, c.getFont().getSize()));
                 c.setForeground(cmpFontColor);
             }
             if(c instanceof JScrollPane)
                 c.setBackground(bgColor);
         }
         
         updateCollapsableMenu(styleMenu, bgColor, cmpColor, cmpFontColor, fontColor);
         updateCollapsableMenu(layoutMenu, bgColor, cmpColor, cmpFontColor, fontColor);         
         
         //does not work for all L&F (Nimbus / windows)
         tabbedPane.setBackground(bgColor);
         tabbedPane.setFont(new Font(fontsBox.getSelectedItem().toString(), Font.PLAIN, tabbedPane.getFont().getSize()));
         tabbedPane.setForeground(fontColor);
         
         for(int i = 0; i < tabbedPane.getTabCount(); i++)
         {
            //does not work for all L&F (Nimbus / windows)
             tabbedPane.setBackgroundAt(i, bgColor);
             
             String tabTitle = tabbedPane.getTitleAt(i);
             
             if(tabTitle.endsWith("trades") || tabTitle.equals("Get latest trades"))
             {                 
                 JSplitPane splitPane = (JSplitPane) tabbedPane.getComponent(i);    
                 JViewport viewport = (JViewport) ((JScrollPane)splitPane.getBottomComponent()).getComponent(0);
                 JTable table = (JTable) viewport.getComponent(0);
                 table.setBackground(bgColor);
                 table.setFont(new Font(fontsBox.getSelectedItem().toString(), table.getFont().getStyle(), table.getFont().getSize()));
                 table.setForeground(fontColor);
                 table.setSelectionBackground(cmpColor);
                 table.setSelectionForeground(cmpFontColor);
                 table.setShowGrid(true);
                 table.setGridColor(fontColor);
                 
                 //does not work for all L&F (Nimbus / windows)
                 table.getTableHeader().setBackground(cmpColor);
                 table.getTableHeader().setForeground(cmpFontColor);
                 table.getTableHeader().setFont(table.getFont());
                 
                 if(tabTitle.equals("Get latest trades"))
                 {
                     JPanel panel = (JPanel)splitPane.getTopComponent();
                     panel.setBackground(bgColor);
                     for(Component c : panel.getComponents())
                     {
                         if (c instanceof JButton)
                         {
                             c.setBackground(cmpColor);
                             c.setFont(new Font(fontsBox.getSelectedItem().toString(), c.getFont().getStyle(), c.getFont().getSize()));
                             c.setForeground(cmpFontColor);
                         }
                         if (c instanceof JLabel)
                         {
                             c.setFont(new Font(fontsBox.getSelectedItem().toString(), c.getFont().getStyle(), c.getFont().getSize()));
                             c.setForeground(fontColor);
                         }   
                         if (c instanceof JCheckBox)
                         {
                             c.setBackground(bgColor);
                             c.setFont(new Font(fontsBox.getSelectedItem().toString(), c.getFont().getStyle(), c.getFont().getSize()));
                             c.setForeground(fontColor);
                         }
                     }
                 }
             }
         }   
         
         tradeInfoTable.setBackground(bgColor);
         tradeInfoTable.setFont(new Font(fontsBox.getSelectedItem().toString(), tradeInfoTable.getFont().getStyle(), tradeInfoTable.getFont().getSize()));
         tradeInfoTable.setForeground(fontColor);
         tradeInfoTable.setSelectionBackground(cmpColor);
         tradeInfoTable.setSelectionForeground(cmpFontColor);
         tradeInfoTable.setShowGrid(true);
         tradeInfoTable.setGridColor(fontColor);
                  
         //</editor-fold>
         
         gui.updateStyle(bgColor, cmpColor, fontColor, cmpFontColor, fontsBox.getSelectedItem().toString());
         
         updateGuiStyleLabels();    
         updatePopOutMenusDimension();
     }
     
     private void updateGuiStyleLabels()
     {
        guiBackgroundLabel.setBackground(new Color(guiBackgroundColor));
        guiComponentsLabel.setBackground(new Color(guiComponentColor));
        guiFontColorLabel.setBackground(new Color(guiFontColor));
        componentFontLabel.setBackground(new Color(componentFontColor));
        dividerColorLabel.setBackground(new Color(dividerColor));
     }
     
     private void updateCollapsableMenu(JPanel menu, Color bgColor, Color cmpColor, Color cmpFontColor,Color fontColor)
     {
         menu.setBackground(bgColor);
         for(Component c : menu.getComponents())
         {
             if(c instanceof JButton)
             {
                 c.setBackground(cmpColor);
                 c.setFont(new Font(fontsBox.getSelectedItem().toString(), c.getFont().getStyle(), c.getFont().getSize()));
                 c.setForeground(cmpFontColor);
             }
             if(c instanceof JScrollPane)
                 c.setBackground(bgColor);
             if(c instanceof JLabel)
             {                 
                 c.setFont(new Font(fontsBox.getSelectedItem().toString(), c.getFont().getStyle(), c.getFont().getSize()));
                 c.setForeground(fontColor);
             }
             if(c instanceof JCheckBox)
             {
                 c.setBackground(bgColor);
                 c.setFont(new Font(fontsBox.getSelectedItem().toString(), c.getFont().getStyle(), c.getFont().getSize()));
                 c.setForeground(fontColor);
             }
             if(c instanceof JSlider)
                 c.setBackground(bgColor);
         }
     }    
     
     private void updatePopOutMenusDimension()
     {
         SwingUtilities.invokeLater(()->
         {
            //When font is changed, we need to re-calculate the width and height of the layout and style menu
            //Get the height of all the components in the style/layout menu's by the grid bag manager and
            //set the height of the panel by their cumulative height + 20  (insets)
            int totalHeight = 0;
            GridBagLayout gbl = (GridBagLayout) styleMenu.getLayout();
            for (int height : gbl.getLayoutDimensions()[1])
            {
                totalHeight += height;
            }
            styleMenu.setMinimumSize(new Dimension(styleMenu.getWidth(), totalHeight + 20));
            styleMenu.setPreferredSize(new Dimension(styleMenu.getWidth(), totalHeight + 20));
            styleMenu.revalidate();

            totalHeight = 0;
            gbl = (GridBagLayout) layoutMenu.getLayout();
            for (int height : gbl.getLayoutDimensions()[1])
            {
                totalHeight += height;
            }
            layoutMenu.setMinimumSize(new Dimension(layoutMenu.getWidth(), totalHeight + 20));
            layoutMenu.setPreferredSize(new Dimension(layoutMenu.getWidth(), totalHeight + 20));
            layoutMenu.revalidate();

            //make sure the width of the style menu scroll pane is big enough to fit the style menu
            styleMenuScrollpane.setPreferredSize(new Dimension(
                    styleMenu.getWidth() + styleMenuScrollpane.getVerticalScrollBar().getWidth(), styleMenu.getHeight()));
         });
     }
     
     protected void setTradeTabDividers()
     {
         SwingUtilities.invokeLater(()->
         {
             tradesTab.setDividerLocation(0.5);
             btcTradesTab.setDividerLocation(0.5);
             dogeTradesTab.setDividerLocation(0.5);         
         });
     }
     
     private void setChartItemUI(ChartItem item,Color fontColor,Color bgColor,Color cmpColor,Color cmpFontColor)
     {         
         item.bgColor = bgColor;
         item.highlightColor = bgColor.brighter().brighter();
         item.selectedColor = cmpColor;
         item.borderColor = fontColor;
         item.borderColorSelected = cmpFontColor;
         item.updateItemUI();
     }
     
     private void applyColor(JLabel label, ChartWindow chartWindow, Color color)
     {         
             if(label == chartColorLabel)
             {
                 chartWindow.setChartColor(color);
                 chartWindow.chartColor = color;
             }
             else if(label == plotColorLabel)
             {
                 chartWindow.setPlotColor(color);
                 chartWindow.plotColor = color;
             }
             else if(label == lineColorLabel)
             {
                 chartWindow.setLineColor(color);
                 chartWindow.lineColor = color;
             }
             else if(label == textColorLabel)
             {
                 chartWindow.setTextColor(color);
                 chartWindow.textColor = color;
             }
             else if(label == gridColorLabel)
             {
                 chartWindow.chartMaker.setGridColor(color);
                 chartWindow.gridColor = color;
             }
             else if(label == dialogColorLabel)
             {                 
                 chartWindow.chartMaker.setDialogColor(color);
                 chartWindow.dialogColor = color;
             }
             else if(label == dialogTxtColorLabel)
             {
                 chartWindow.chartMaker.setDialogTextColor(color);
                 chartWindow.dialogTextColor = color;
             }
             else if(label == crosshairsColorLabel)
             {
                 chartWindow.chartMaker.setCrosshairsColor(color);
                 chartWindow.crosshairsColor = color;
             }
     }
     
     private void applyFont()
     {
         if(!isChangeApplicable())
             return;
         
         updateGuiItems();
         
         boolean applyToSelectedOnly = desktopPane.getSelectedFrame() != null && !applyToAllChartsBox.isSelected();
        
        if(applyToSelectedOnly)
        {
            ChartWindow chartWindow = (ChartWindow) desktopPane.getSelectedFrame();
            chartWindow.font = fontsBox.getSelectedItem().toString();
            chartWindow.fontSize = (byte) fontSlider.getValue();
            chartWindow.setNewFont(
                    fontsBox.getSelectedItem().toString(),fontSlider.getValue(),chartWindow.textColor);
            saveLayout("CurrentLayout", chartWindow.type, false,false);
        }
        else if(applyToAllChartsBox.isSelected())
        {             
            for(JInternalFrame frame : desktopPane.getAllFrames())
            {
                ChartWindow chartWindow = (ChartWindow) frame;
                chartWindow.font = fontsBox.getSelectedItem().toString();
                chartWindow.fontSize = (byte) fontSlider.getValue();
                chartWindow.setNewFont(
                        fontsBox.getSelectedItem().toString(), fontSlider.getValue(),chartWindow.textColor);
            }
            saveLayout("CurrentLayout", null, true,false);
        }             
     }
     
     /**In case user has no frame selected and apply to all checkbox is not selected we want<br>
      *to automatically select the frame that is closest to the top left corner of the desktop*/
     private boolean isChangeApplicable()
     {
         if(applyToAllChartsBox.isSelected())
             return true;
         
         //don't select frame closest to top right corner if any frame is selected
         if(desktopPane.getSelectedFrame() != null)
             return true;
         else
            return selectClosestFrame();           
     }
     
     /**Selects the frame closest to the top right corner of the desktop
     * @return */
     protected boolean selectClosestFrame()
     {
         if(desktopPane.getAllFrames().length == 0)
             return false;
        
        int lowest = Integer.MAX_VALUE;
        int closestIndex = 0;
        
        //In case user has no frame selected and de-selects apply to all checkbox, we want to automatically 
        //select the frame that is closest to the top left corner of the destop
        for (int i = 0; i < desktopPane.getAllFrames().length; i++)
        {
            JInternalFrame frame = desktopPane.getAllFrames()[i];
            
            if(frame.getX() + frame.getY() < lowest)
            {
                lowest = frame.getX() + frame.getY();
                closestIndex = i;
            }
        }
        
        JInternalFrame selectedFrame = desktopPane.getAllFrames()[closestIndex];
        
         try
         {
             selectedFrame.setSelected(true);
             updateStyleMenu((ChartWindow)selectedFrame);
             chartWindowFocused = true;
             return true;
         }
         catch (PropertyVetoException e)
         {
            //if setSelected fails, inform the desktop that this is the selected frame (without visual cue)
            desktopPane.setSelectedFrame(selectedFrame);
            updateStyleMenu((ChartWindow)selectedFrame);
            return true;
         }   
         
     }
     
     private void toggleCheckbox(JCheckBox checkBox, boolean isSelected)
     {
         if(!isChangeApplicable())
             return;
         
         boolean applyToSelectedOnly = desktopPane.getSelectedFrame() != null && !applyToAllChartsBox.isSelected();
        
        if(applyToSelectedOnly)
        {
            ChartWindow chartWindow = (ChartWindow) desktopPane.getSelectedFrame();
            applyCheckboxToChart(chartWindow, checkBox, isSelected);
            saveLayout("CurrentLayout", chartWindow.type, false,false);
        }
        else if(applyToAllChartsBox.isSelected())
        {             
            for(JInternalFrame frame : desktopPane.getAllFrames())
            {
                ChartWindow chartWindow = (ChartWindow) frame;
                    applyCheckboxToChart(chartWindow, checkBox, isSelected);
            }
            saveLayout("CurrentLayout", null, true,false);
        }
     }
     
     private void applyCheckboxToChart(ChartWindow chartWindow,JCheckBox checkBox,boolean isSelected)
     {
            if(checkBox == snapshotsCheckbox)
            {
                chartWindow.chartMaker.toggleSnapshots(isSelected);
                chartWindow.showSnapshots = isSelected;
            }
            else if(checkBox == drawX_box)
            {
                chartWindow.chartMaker.toggleGridX(isSelected);
                chartWindow.drawX = isSelected;                
            }
            else if(checkBox == drawY_box)
            {
                chartWindow.chartMaker.toggleGridY(isSelected);
                chartWindow.drawY = isSelected;
            } 
            else if(checkBox == showTextBox)
            {
                chartWindow.chartMaker.toggleText(isSelected,typeToTitleMap.get(chartWindow.type.toString()),chartWindow.textColor);
                chartWindow.showText = isSelected;
            }   
            else if(checkBox == showSubBox)
            {
                chartWindow.chartMaker.toggleSubtitle(isSelected,chartWindow.textColor);
                chartWindow.showSubtitle = isSelected;
            }        
            else if(checkBox == showXcrosshairsBox)
            {
                chartWindow.chartMaker.toggleXCrosshairs(isSelected);
                chartWindow.showXcrosshairs = isSelected;
            }      
            else if(checkBox == showYcrosshairBox)
            {
                chartWindow.chartMaker.toggleYCrosshair(isSelected);
                chartWindow.showYcrosshair = isSelected;
            }       
            else if(checkBox == showDialogBox)
            {
                chartWindow.chartMaker.showDialog = isSelected;
                chartWindow.showDialog = isSelected;
            }         
            else if(checkBox == movingAverageBox)
            {
                chartWindow.chartMaker.toggleMovingAverage(isSelected);
                chartWindow.showMovingAverage = isSelected;
            }      
            else if(checkBox == tradeVolumeBox)
            {
                chartWindow.chartMaker.toggleVolume(isSelected);
                chartWindow.showVolume = isSelected;
            }   
     }
     
     protected void disableChartDialogs(ChartWindow active)
     {
         for(JInternalFrame frame : desktopPane.getAllFrames())
         {
             ChartWindow chartWindow = (ChartWindow) frame;
             if(chartWindow == active)
                 continue;
             
             chartWindow.chartMaker.chartDialog.setVisible(false);
         }
     }   

     //It's very important that the minimum size of the UI components are not too large (especially for height)
     //this will cause the resize listener for the desktop pane to not get triggered when the JFrame gets resized 
     //and result in the charts not getting resized properly. It also causes the JTables in the trades tabs to 
     //get rendered improperly
     protected void resizeChartWindows()
     {
         if(currentArrangement == null || currentArrangement.isEmpty())
             return;      
         
         chartWindowFocused = false;   
        
         //We save the normalized bounds of each chart window relative to the desktop when we save an arrangement
         //or a layout. When resizing the chart to current desktop size we only need to multiply the normalized bounds
         //of each chart with the current desktop dimension
         for(int i = 0; i < desktopPane.getAllFrames().length; i++)
         {
             JInternalFrame frame = desktopPane.getAllFrames()[i];
             int arrIndex = ((ChartWindow)frame).arrIndex;
             
             if(frame.isMaximum())
             {
                 frame.setNormalBounds(new Rectangle(
                     (int)(currentArrangement.get(arrIndex).x * desktopPane.getWidth()), 
                     (int)(currentArrangement.get(arrIndex).y * desktopPane.getHeight()), 
                     (int)(currentArrangement.get(arrIndex).width * desktopPane.getWidth()), 
                     (int)(currentArrangement.get(arrIndex).height * desktopPane.getHeight()))); 
             }
             else
             {
                 frame.setBounds(
                     (int)(currentArrangement.get(arrIndex).x * desktopPane.getWidth()),
                     (int)(currentArrangement.get(arrIndex).y * desktopPane.getHeight()), 
                     (int)(currentArrangement.get(arrIndex).width * desktopPane.getWidth()), 
                     (int)(currentArrangement.get(arrIndex).height * desktopPane.getHeight())); 
             }
         }
     }
     
    /**
     *The size of the dialog needs to be adjusted to the font type.<br>
     *We get the width of the entire string and divide that by the width of the label<br>
     *to find out how many lines the string will have. Then we add the line breaks in<br>
     *the string to find the actual lines. We multiply the number of actual lines by the<br>
     *string's actual height and add some margin to get the dialog height*/ 
     private void setInfoDialogBounds(int x, int y, int lineBreaks,boolean belowPointer)
     {
        FontRenderContext frc = new FontRenderContext(new AffineTransform(), true, true);
        int textwidth = (int)(tipDialogLabel.getFont().getStringBounds(tipDialogLabel.getText(), frc).getWidth());
        int textheight = (int)(tipDialogLabel.getFont().getStringBounds(tipDialogLabel.getText(), frc).getHeight());        
        int lines = textwidth / (400 - 60);//width dialog - label left/right insets
        lines += lineBreaks;        
        //Dialog will always be 400 pixels wide, the height will depend on content and font type
        tipDialog.setPreferredSize(new Dimension(400, (textheight * lines) + 150));
        tipDialog.pack();
        
        if(belowPointer)
            tipDialog.setLocation(x - (tipDialog.getWidth()/ 2), y + 25);
        else
            tipDialog.setLocation(x - (tipDialog.getWidth()/ 2), y - (tipDialog.getHeight() + 15));
        
        tipDialog.setVisible(true);
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

        tipDialog = new javax.swing.JDialog();
        tipDialogLabel = new javax.swing.JLabel();
        tipDialogPanel = new javax.swing.JPanel();
        tradeInfoDialog = new javax.swing.JDialog();
        tradeInfoScrollpane = new javax.swing.JScrollPane();
        tradeInfoTable = new javax.swing.JTable();
        tabbedPane = new javax.swing.JTabbedPane();
        chartsTab = new javax.swing.JPanel();
        chartsMenu = new javax.swing.JPanel();
        toggleStyleButton = new javax.swing.JToggleButton();
        chartsListScrollPane = new javax.swing.JScrollPane();
        chartsListScrollPane.getVerticalScrollBar().setUnitIncrement(10);
        chartsList = new javax.swing.JPanel();
        toggleLayoutButton = new javax.swing.JToggleButton();
        styleMenuScrollpane = new javax.swing.JScrollPane();
        styleMenuScrollpane.getVerticalScrollBar().setUnitIncrement(10);
        styleMenu = new javax.swing.JPanel();
        applyToAllChartsBox = new javax.swing.JCheckBox();
        jLabel1 = new javax.swing.JLabel();
        plotColorLabel = new javax.swing.JLabel();
        plotColorLabel.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255))));
        jLabel3 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        chartColorLabel = new javax.swing.JLabel();
        chartColorLabel.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255))));
        lineColorLabel = new javax.swing.JLabel();
        lineColorLabel.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255))));
        strokeSlider = new javax.swing.JSlider();
        snapshotsCheckbox = new javax.swing.JCheckBox();
        strokeLabel = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        textColorLabel = new javax.swing.JLabel();
        textColorLabel.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255))));
        jLabel7 = new javax.swing.JLabel();
        gridColorLabel = new javax.swing.JLabel();
        gridColorLabel.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255))));
        drawY_box = new javax.swing.JCheckBox();
        drawX_box = new javax.swing.JCheckBox();
        showSubBox = new javax.swing.JCheckBox();
        tradeVolumeBox = new javax.swing.JCheckBox();
        movingAverageBox = new javax.swing.JCheckBox();
        averagingLabel = new javax.swing.JLabel();
        averagingSlider = new javax.swing.JSlider();
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] fontFamilyNames = ge.getAvailableFontFamilyNames();
        fontsBox = new JComboBox(fontFamilyNames);
        fontsBox.setSelectedItem(0);
        fontsBox.setRenderer(new ComboRenderer(fontsBox));
        fontsBox.addItemListener(new ItemListener()
        {

            @Override
            public void itemStateChanged(ItemEvent e)
            {
                if (e.getStateChange() == ItemEvent.SELECTED)
                {
                    final String fontName = fontsBox.getSelectedItem().toString();
                    fontsBox.setFont(new Font(fontName, Font.PLAIN, 12));
                }
            }
        });
        fontsBox.setSelectedItem(0);
        fontsBox.getEditor().selectAll();
        fontSlider = new javax.swing.JSlider();
        fontLabel = new javax.swing.JLabel();
        showTextBox = new javax.swing.JCheckBox();
        showDialogBox = new javax.swing.JCheckBox();
        showXcrosshairsBox = new javax.swing.JCheckBox();
        jLabel8 = new javax.swing.JLabel();
        dialogColorLabel = new javax.swing.JLabel();
        dialogColorLabel.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255))));
        jLabel9 = new javax.swing.JLabel();
        crosshairsColorLabel = new javax.swing.JLabel();
        crosshairsColorLabel.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255))));
        jLabel10 = new javax.swing.JLabel();
        dialogTxtColorLabel = new javax.swing.JLabel();
        dialogTxtColorLabel.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255))));
        jLabel2 = new javax.swing.JLabel();
        dividerColorLabel = new javax.swing.JLabel();
        dividerColorLabel.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255))));
        showYcrosshairBox = new javax.swing.JCheckBox();
        jSeparator3 = new javax.swing.JSeparator();
        saveStyleButton = new javax.swing.JButton();
        loadStyleButton = new javax.swing.JButton();
        sylesListScrollpane = new javax.swing.JScrollPane();
        stylesList = new javax.swing.JList(new DefaultListModel());
        deleteStyleButton = new javax.swing.JButton();
        guiBackgroundLabel = new javax.swing.JLabel();
        guiBackgroundLabel.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255))));
        jLabel12 = new javax.swing.JLabel();
        jSeparator4 = new javax.swing.JSeparator();
        jLabel13 = new javax.swing.JLabel();
        guiComponentsLabel = new javax.swing.JLabel();
        guiComponentsLabel.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255))));
        jLabel14 = new javax.swing.JLabel();
        guiFontColorLabel = new javax.swing.JLabel();
        guiFontColorLabel.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255))));
        jLabel15 = new javax.swing.JLabel();
        componentFontLabel = new javax.swing.JLabel();
        componentFontLabel.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)), javax.swing.BorderFactory.createLineBorder(new java.awt.Color(255, 255, 255))));
        chartsLabel = new javax.swing.JLabel();
        fontStyleLabel = new javax.swing.JLabel();
        jSeparator5 = new javax.swing.JSeparator();
        uiLabel1 = new javax.swing.JLabel();
        fontStyleLabel1 = new javax.swing.JLabel();
        stylesLinkLabel = new javax.swing.JLabel();
        applyToAllButton = new javax.swing.JButton();
        applyLinkLabel = new javax.swing.JLabel();
        applyStyleToAllBox = new javax.swing.JCheckBox();
        loadUiStyleBox = new javax.swing.JCheckBox();
        layoutMenuScrollpane = new javax.swing.JScrollPane();
        layoutMenuScrollpane.getVerticalScrollBar().setUnitIncrement(10);
        layoutMenu = new javax.swing.JPanel();
        saveArrangeButton = new javax.swing.JButton();
        arrangementsListScrollpane = new javax.swing.JScrollPane();
        arrangementsList = new javax.swing.JList(new DefaultListModel());
        loadArrangeButton = new javax.swing.JButton();
        arrangementsLabel = new javax.swing.JLabel();
        randomizeCheckbox = new javax.swing.JCheckBox();
        jSeparator1 = new javax.swing.JSeparator();
        saveLayoutButton = new javax.swing.JButton();
        loadLayoutButton = new javax.swing.JButton();
        arrangementsListScrollpane1 = new javax.swing.JScrollPane();
        layoutsList = new javax.swing.JList(new DefaultListModel());
        layoutsLabel = new javax.swing.JLabel();
        arrangeLabel = new javax.swing.JLabel();
        deleteLayoutButton = new javax.swing.JButton();
        deleteArrangeButton = new javax.swing.JButton();
        arrangeLinkLabel = new javax.swing.JLabel();
        layoutsLinkLabel = new javax.swing.JLabel();
        keepStyleBox = new javax.swing.JCheckBox();
        divider = new javax.swing.JPanel();
        dividerButton = new javax.swing.JButton();
        desktopPane = new javax.swing.JDesktopPane();
        tradesTab = new javax.swing.JSplitPane();
        ltcTradesScrollpane = new javax.swing.JScrollPane();
        ltcTradesTable = new JTable()
        {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        ltcTradesTable.getTableHeader().setReorderingAllowed(false);
        ltcTradesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ltcChartPlaceholder = new javax.swing.JPanel();
        btcTradesTab = new javax.swing.JSplitPane();
        btcChartPlaceholder = new javax.swing.JPanel();
        btcTradesScrollpane = new javax.swing.JScrollPane();
        btcTradesTable = new JTable()
        {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        btcTradesTable.getTableHeader().setReorderingAllowed(false);
        btcTradesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dogeTradesTab = new javax.swing.JSplitPane();
        dogeChartPlaceholder = new javax.swing.JPanel();
        dogeTradesScrollpane = new javax.swing.JScrollPane();
        dogeTradesTable = new JTable()
        {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        dogeTradesTable.getTableHeader().setReorderingAllowed(false);
        dogeTradesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ravenTradesTab = new javax.swing.JSplitPane();
        ravenChartPlaceholder = new javax.swing.JPanel();
        ravenTradesScrollpane = new javax.swing.JScrollPane();
        ravenTradesTable = new JTable()
        {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        ravenTradesTable.getTableHeader().setReorderingAllowed(false);
        ravenTradesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        digibyteTradesTab = new javax.swing.JSplitPane();
        digibyteChartPlaceholder = new javax.swing.JPanel();
        digibyteTradesScrollpane = new javax.swing.JScrollPane();
        digibyteTradesTable = new JTable()
        {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        digibyteTradesTable.getTableHeader().setReorderingAllowed(false);
        digibyteTradesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        updateTab = new javax.swing.JSplitPane();
        allTxMenuPanel = new javax.swing.JPanel();
        updateButton = new javax.swing.JButton();
        progressBar = new javax.swing.JProgressBar();
        progressBar.setVisible(false);
        stopButton = new javax.swing.JButton();
        updateStatusLabel = new javax.swing.JLabel();
        lookupStatusLabel = new javax.swing.JLabel();
        autoUpdateCheckbox = new javax.swing.JCheckBox();
        jScrollPane1 = new javax.swing.JScrollPane();
        txTable = new javax.swing.JTable();

        tipDialog.setAlwaysOnTop(true);
        tipDialog.setType(java.awt.Window.Type.UTILITY);
        tipDialog.addFocusListener(new java.awt.event.FocusAdapter()
        {
            public void focusLost(java.awt.event.FocusEvent evt)
            {
                tipDialogFocusLost(evt);
            }
        });
        tipDialog.getContentPane().setLayout(new java.awt.GridBagLayout());

        tipDialogLabel.setFont(new java.awt.Font("Segoe UI", 0, 13)); // NOI18N
        tipDialogLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        tipDialogLabel.setText("jLabel4");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(5, 30, 5, 30);
        tipDialog.getContentPane().add(tipDialogLabel, gridBagConstraints);

        tipDialogPanel.setPreferredSize(new java.awt.Dimension(252, 170));

        javax.swing.GroupLayout tipDialogPanelLayout = new javax.swing.GroupLayout(tipDialogPanel);
        tipDialogPanel.setLayout(tipDialogPanelLayout);
        tipDialogPanelLayout.setHorizontalGroup(
            tipDialogPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 458, Short.MAX_VALUE)
        );
        tipDialogPanelLayout.setVerticalGroup(
            tipDialogPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 312, Short.MAX_VALUE)
        );

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        tipDialog.getContentPane().add(tipDialogPanel, gridBagConstraints);

        tradeInfoDialog.setTitle("Trade Info");
        tradeInfoDialog.setAlwaysOnTop(true);
        tradeInfoDialog.setType(java.awt.Window.Type.UTILITY);
        tradeInfoDialog.addFocusListener(new java.awt.event.FocusAdapter()
        {
            public void focusLost(java.awt.event.FocusEvent evt)
            {
                tradeInfoDialogFocusLost(evt);
            }
        });

        tradeInfoTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {

            },
            new String []
            {
                "", ""
            }
        )
        {
            boolean[] canEdit = new boolean []
            {
                false, true
            };

            public boolean isCellEditable(int rowIndex, int columnIndex)
            {
                return canEdit [columnIndex];
            }
        });
        tradeInfoTable.getTableHeader().setReorderingAllowed(false);
        tradeInfoScrollpane.setViewportView(tradeInfoTable);

        javax.swing.GroupLayout tradeInfoDialogLayout = new javax.swing.GroupLayout(tradeInfoDialog.getContentPane());
        tradeInfoDialog.getContentPane().setLayout(tradeInfoDialogLayout);
        tradeInfoDialogLayout.setHorizontalGroup(
            tradeInfoDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tradeInfoScrollpane, javax.swing.GroupLayout.DEFAULT_SIZE, 575, Short.MAX_VALUE)
        );
        tradeInfoDialogLayout.setVerticalGroup(
            tradeInfoDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tradeInfoScrollpane)
        );

        setBackground(new java.awt.Color(102, 255, 102));
        setOpaque(false);

        tabbedPane.setMinimumSize(new java.awt.Dimension(200, 200));
        tabbedPane.setOpaque(true);

        chartsTab.setMinimumSize(new java.awt.Dimension(200, 200));
        chartsTab.addComponentListener(new java.awt.event.ComponentAdapter()
        {
            public void componentHidden(java.awt.event.ComponentEvent evt)
            {
                chartsTabComponentHidden(evt);
            }
        });
        chartsTab.setLayout(new java.awt.GridBagLayout());

        chartsMenu.setMaximumSize(new java.awt.Dimension(250, 2147483647));
        chartsMenu.setMinimumSize(new java.awt.Dimension(250, 77));
        chartsMenu.setPreferredSize(new java.awt.Dimension(250, 224));
        chartsMenu.setLayout(new java.awt.GridBagLayout());

        toggleStyleButton.setText("Show style menu");
        toggleStyleButton.setMinimumSize(new java.awt.Dimension(150, 25));
        toggleStyleButton.setPreferredSize(new java.awt.Dimension(150, 25));
        toggleStyleButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                toggleStyleButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 5, 0);
        chartsMenu.add(toggleStyleButton, gridBagConstraints);

        chartsList.setLayout(new javax.swing.BoxLayout(chartsList, javax.swing.BoxLayout.Y_AXIS));
        chartsListScrollPane.setViewportView(chartsList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        chartsMenu.add(chartsListScrollPane, gridBagConstraints);

        toggleLayoutButton.setText("Show layout menu");
        toggleLayoutButton.setMinimumSize(new java.awt.Dimension(150, 25));
        toggleLayoutButton.setPreferredSize(new java.awt.Dimension(150, 25));
        toggleLayoutButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                toggleLayoutButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 0);
        chartsMenu.add(toggleLayoutButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weighty = 0.1;
        chartsTab.add(chartsMenu, gridBagConstraints);

        styleMenuScrollpane.setMaximumSize(new java.awt.Dimension(225, 32767));
        styleMenuScrollpane.setMinimumSize(new java.awt.Dimension(225, 16));
        styleMenuScrollpane.setPreferredSize(new java.awt.Dimension(225, 700));

        styleMenu.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        styleMenu.setDoubleBuffered(false);
        styleMenu.setFocusTraversalPolicyProvider(true);
        styleMenu.setMaximumSize(new java.awt.Dimension(200, 6000));
        styleMenu.setMinimumSize(new java.awt.Dimension(200, 400));
        styleMenu.setPreferredSize(new java.awt.Dimension(200, 1160));
        styleMenu.setVerifyInputWhenFocusTarget(false);
        styleMenu.setLayout(new java.awt.GridBagLayout());

        applyToAllChartsBox.setText("Auto apply to all");
        applyToAllChartsBox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                applyToAllChartsBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 21;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(applyToAllChartsBox, gridBagConstraints);

        jLabel1.setText("Plot background");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 32;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(jLabel1, gridBagConstraints);

        plotColorLabel.setText("pick color");
        plotColorLabel.setToolTipText("Pick a color for the plot's background");
        plotColorLabel.setOpaque(true);
        plotColorLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                colorLabelMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 32;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 3, 2, 0);
        styleMenu.add(plotColorLabel, gridBagConstraints);
        plotColorLabel.setText("     ");

        jLabel3.setText("Chart background");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 31;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(jLabel3, gridBagConstraints);

        jLabel5.setText("Line color");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 26;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(jLabel5, gridBagConstraints);

        chartColorLabel.setText("pick color");
        chartColorLabel.setToolTipText("Pick a color for the chart's background");
        chartColorLabel.setOpaque(true);
        chartColorLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                colorLabelMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 31;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 3, 2, 0);
        styleMenu.add(chartColorLabel, gridBagConstraints);
        chartColorLabel.setText("     ");

        lineColorLabel.setText("pick color");
        lineColorLabel.setToolTipText("Pick a color for the chart's line");
        lineColorLabel.setOpaque(true);
        lineColorLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                colorLabelMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 26;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 3, 2, 0);
        styleMenu.add(lineColorLabel, gridBagConstraints);
        lineColorLabel.setText("     ");

        strokeSlider.setMaximum(50);
        strokeSlider.setMinimum(10);
        strokeSlider.setValue(20);
        strokeSlider.setMaximumSize(new java.awt.Dimension(120, 20));
        strokeSlider.setPreferredSize(new java.awt.Dimension(120, 20));
        strokeSlider.addChangeListener(new javax.swing.event.ChangeListener()
        {
            public void stateChanged(javax.swing.event.ChangeEvent evt)
            {
                strokeSliderStateChanged(evt);
            }
        });
        strokeSlider.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                strokeSliderMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 34;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 15, 5, 15);
        styleMenu.add(strokeSlider, gridBagConstraints);

        snapshotsCheckbox.setText("Show snapshots");
        snapshotsCheckbox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                snapshotsCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 35;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(snapshotsCheckbox, gridBagConstraints);

        strokeLabel.setText("Line radius : 2.0");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 33;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        styleMenu.add(strokeLabel, gridBagConstraints);

        jLabel6.setText("Text color");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 25;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(jLabel6, gridBagConstraints);

        textColorLabel.setText("pick color");
        textColorLabel.setToolTipText("Pick a color for the text");
        textColorLabel.setOpaque(true);
        textColorLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                colorLabelMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 25;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 3, 2, 0);
        styleMenu.add(textColorLabel, gridBagConstraints);
        textColorLabel.setText("     ");

        jLabel7.setText("Grid color");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 27;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(jLabel7, gridBagConstraints);

        gridColorLabel.setText("pick color");
        gridColorLabel.setToolTipText("Pick a color for the grid");
        gridColorLabel.setOpaque(true);
        gridColorLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                colorLabelMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 27;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 3, 2, 0);
        styleMenu.add(gridColorLabel, gridBagConstraints);
        gridColorLabel.setText("     ");

        drawY_box.setSelected(true);
        drawY_box.setText("Draw grid Y");
        drawY_box.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                drawY_boxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 37;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(drawY_box, gridBagConstraints);

        drawX_box.setSelected(true);
        drawX_box.setText("Draw grid X");
        drawX_box.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                drawX_boxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 36;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(drawX_box, gridBagConstraints);

        showSubBox.setSelected(true);
        showSubBox.setText("Show subtitle");
        showSubBox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                showSubBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 39;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(showSubBox, gridBagConstraints);

        tradeVolumeBox.setText("Show trade volume");
        tradeVolumeBox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                tradeVolumeBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 43;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(tradeVolumeBox, gridBagConstraints);

        movingAverageBox.setSelected(true);
        movingAverageBox.setText("Show moving average");
        movingAverageBox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                movingAverageBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 44;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(movingAverageBox, gridBagConstraints);

        averagingLabel.setText("Average by 10 trades");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 45;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 2, 0);
        styleMenu.add(averagingLabel, gridBagConstraints);

        averagingSlider.setMaximum(50);
        averagingSlider.setMinimum(5);
        averagingSlider.setMinorTickSpacing(1);
        averagingSlider.setValue(10);
        averagingSlider.setMaximumSize(new java.awt.Dimension(120, 20));
        averagingSlider.setPreferredSize(new java.awt.Dimension(120, 20));
        averagingSlider.addChangeListener(new javax.swing.event.ChangeListener()
        {
            public void stateChanged(javax.swing.event.ChangeEvent evt)
            {
                averagingSliderStateChanged(evt);
            }
        });
        averagingSlider.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                averagingSliderMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 46;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 30, 25, 30);
        styleMenu.add(averagingSlider, gridBagConstraints);

        fontsBox.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        fontsBox.setMaximumSize(new java.awt.Dimension(150, 30));
        fontsBox.setPreferredSize(new java.awt.Dimension(150, 30));
        fontsBox.addItemListener(new java.awt.event.ItemListener()
        {
            public void itemStateChanged(java.awt.event.ItemEvent evt)
            {
                fontsBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        styleMenu.add(fontsBox, gridBagConstraints);

        fontSlider.setMaximum(30);
        fontSlider.setMinimum(10);
        fontSlider.setValue(20);
        fontSlider.setMaximumSize(new java.awt.Dimension(120, 20));
        fontSlider.setPreferredSize(new java.awt.Dimension(120, 20));
        fontSlider.addChangeListener(new javax.swing.event.ChangeListener()
        {
            public void stateChanged(javax.swing.event.ChangeEvent evt)
            {
                fontSliderStateChanged(evt);
            }
        });
        fontSlider.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                fontSliderMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 24;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(2, 15, 2, 15);
        styleMenu.add(fontSlider, gridBagConstraints);

        fontLabel.setText("Font size : 20");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 23;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        styleMenu.add(fontLabel, gridBagConstraints);

        showTextBox.setSelected(true);
        showTextBox.setText("Show text");
        showTextBox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                showTextBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 38;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(showTextBox, gridBagConstraints);

        showDialogBox.setSelected(true);
        showDialogBox.setText("Show chart dialog");
        showDialogBox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                showDialogBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 42;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(showDialogBox, gridBagConstraints);

        showXcrosshairsBox.setText("Show X crosshairs");
        showXcrosshairsBox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                showXcrosshairsBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 40;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(showXcrosshairsBox, gridBagConstraints);

        jLabel8.setText("Dialog color");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 28;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(jLabel8, gridBagConstraints);

        dialogColorLabel.setText("pick color");
        dialogColorLabel.setToolTipText("Pick a color for the dialog and crosshair labels");
        dialogColorLabel.setOpaque(true);
        dialogColorLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                colorLabelMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 28;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 3, 2, 0);
        styleMenu.add(dialogColorLabel, gridBagConstraints);
        dialogColorLabel.setText("     ");

        jLabel9.setText("Crosshairs color");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 30;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(jLabel9, gridBagConstraints);

        crosshairsColorLabel.setText("pick color");
        crosshairsColorLabel.setToolTipText("Pick a color for the crosshairs");
        crosshairsColorLabel.setOpaque(true);
        crosshairsColorLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                colorLabelMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 30;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 3, 2, 0);
        styleMenu.add(crosshairsColorLabel, gridBagConstraints);
        crosshairsColorLabel.setText("     ");

        jLabel10.setText("Dialog text color");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 29;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(jLabel10, gridBagConstraints);

        dialogTxtColorLabel.setText("pick color");
        dialogTxtColorLabel.setToolTipText("Pick a color for the dialog and crosshair labels text");
        dialogTxtColorLabel.setOpaque(true);
        dialogTxtColorLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                colorLabelMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 29;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 3, 2, 0);
        styleMenu.add(dialogTxtColorLabel, gridBagConstraints);
        dialogTxtColorLabel.setText("     ");

        jLabel2.setText("Divider color");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 17;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(jLabel2, gridBagConstraints);

        dividerColorLabel.setText("pick color");
        dividerColorLabel.setToolTipText("Pick a color for the desktop divider");
        dividerColorLabel.setOpaque(true);
        dividerColorLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                colorLabelMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 17;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 3, 2, 0);
        styleMenu.add(dividerColorLabel, gridBagConstraints);
        dividerColorLabel.setText("     ");

        showYcrosshairBox.setSelected(true);
        showYcrosshairBox.setText("Show Y crosshair");
        showYcrosshairBox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                showYcrosshairBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 41;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(showYcrosshairBox, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 18;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        styleMenu.add(jSeparator3, gridBagConstraints);

        saveStyleButton.setText("Save style");
        saveStyleButton.setMinimumSize(new java.awt.Dimension(150, 25));
        saveStyleButton.setPreferredSize(new java.awt.Dimension(150, 25));
        saveStyleButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                saveStyleButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 3, 0);
        styleMenu.add(saveStyleButton, gridBagConstraints);

        loadStyleButton.setText("Load style");
        loadStyleButton.setMinimumSize(new java.awt.Dimension(150, 25));
        loadStyleButton.setPreferredSize(new java.awt.Dimension(150, 25));
        loadStyleButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                loadStyleButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 3, 0);
        styleMenu.add(loadStyleButton, gridBagConstraints);

        sylesListScrollpane.setMaximumSize(new java.awt.Dimension(32767, 250));
        sylesListScrollpane.setMinimumSize(new java.awt.Dimension(0, 150));
        sylesListScrollpane.setPreferredSize(new java.awt.Dimension(268, 150));
        sylesListScrollpane.setRequestFocusEnabled(false);

        stylesList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        sylesListScrollpane.setViewportView(stylesList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipady = 50;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 5, 0);
        styleMenu.add(sylesListScrollpane, gridBagConstraints);

        deleteStyleButton.setText("Delete style");
        deleteStyleButton.setMinimumSize(new java.awt.Dimension(150, 25));
        deleteStyleButton.setPreferredSize(new java.awt.Dimension(150, 25));
        deleteStyleButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                deleteStyleButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 3, 0);
        styleMenu.add(deleteStyleButton, gridBagConstraints);

        guiBackgroundLabel.setText("pick color");
        guiBackgroundLabel.setToolTipText("Pick a background color for the UI");
        guiBackgroundLabel.setOpaque(true);
        guiBackgroundLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                colorLabelMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 3, 2, 0);
        styleMenu.add(guiBackgroundLabel, gridBagConstraints);
        guiBackgroundLabel.setText("     ");

        jLabel12.setText("UI background");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(jLabel12, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        styleMenu.add(jSeparator4, gridBagConstraints);

        jLabel13.setText("UI elements");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(jLabel13, gridBagConstraints);

        guiComponentsLabel.setText("pick color");
        guiComponentsLabel.setToolTipText("Pick a color for the UI elements (buttons, lists, etc)");
        guiComponentsLabel.setOpaque(true);
        guiComponentsLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                colorLabelMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 3, 2, 0);
        styleMenu.add(guiComponentsLabel, gridBagConstraints);
        guiComponentsLabel.setText("     ");

        jLabel14.setText("UI font");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 15;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(jLabel14, gridBagConstraints);

        guiFontColorLabel.setText("pick color");
        guiFontColorLabel.setToolTipText("Pick a font color for the UI");
        guiFontColorLabel.setOpaque(true);
        guiFontColorLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                colorLabelMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 15;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 3, 2, 0);
        styleMenu.add(guiFontColorLabel, gridBagConstraints);
        guiFontColorLabel.setText("     ");

        jLabel15.setText("Element font");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 16;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(jLabel15, gridBagConstraints);

        componentFontLabel.setText("pick color");
        componentFontLabel.setToolTipText("Pick a font color for the UI elements (buttons, lists, etc)");
        componentFontLabel.setOpaque(true);
        componentFontLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseReleased(java.awt.event.MouseEvent evt)
            {
                colorLabelMouseReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 16;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(2, 3, 2, 0);
        styleMenu.add(componentFontLabel, gridBagConstraints);
        componentFontLabel.setText("     ");

        chartsLabel.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        chartsLabel.setText("Charts");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 19;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 5, 0);
        styleMenu.add(chartsLabel, gridBagConstraints);

        fontStyleLabel.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        fontStyleLabel.setText("Styles");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 2, 0);
        styleMenu.add(fontStyleLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        styleMenu.add(jSeparator5, gridBagConstraints);

        uiLabel1.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        uiLabel1.setText("UI Colors");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        styleMenu.add(uiLabel1, gridBagConstraints);

        fontStyleLabel1.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        fontStyleLabel1.setText("Font");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        styleMenu.add(fontStyleLabel1, gridBagConstraints);

        stylesLinkLabel.setFont(new java.awt.Font("Segoe UI", 1, 10)); // NOI18N
        stylesLinkLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        stylesLinkLabel.setText("<html><u>what are styles?</u></html>");
        stylesLinkLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseClicked(java.awt.event.MouseEvent evt)
            {
                stylesLinkLabelMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        styleMenu.add(stylesLinkLabel, gridBagConstraints);

        applyToAllButton.setText("Apply to all");
        applyToAllButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                applyToAllButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 20;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        styleMenu.add(applyToAllButton, gridBagConstraints);

        applyLinkLabel.setFont(new java.awt.Font("Segoe UI", 1, 10)); // NOI18N
        applyLinkLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        applyLinkLabel.setText("<html><u>what is this?</u></html>");
        applyLinkLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseClicked(java.awt.event.MouseEvent evt)
            {
                applyLinkLabelMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 22;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 8, 0);
        styleMenu.add(applyLinkLabel, gridBagConstraints);

        applyStyleToAllBox.setSelected(true);
        applyStyleToAllBox.setText("Apply to all charts");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(applyStyleToAllBox, gridBagConstraints);

        loadUiStyleBox.setSelected(true);
        loadUiStyleBox.setText("Load UI style");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(2, 25, 2, 0);
        styleMenu.add(loadUiStyleBox, gridBagConstraints);

        styleMenuScrollpane.setViewportView(styleMenu);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weighty = 0.1;
        chartsTab.add(styleMenuScrollpane, gridBagConstraints);

        layoutMenuScrollpane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        layoutMenuScrollpane.setMaximumSize(new java.awt.Dimension(225, 32767));
        layoutMenuScrollpane.setMinimumSize(new java.awt.Dimension(225, 16));
        layoutMenuScrollpane.setName(""); // NOI18N
        layoutMenuScrollpane.setPreferredSize(new java.awt.Dimension(225, 700));

        layoutMenu.setMaximumSize(new java.awt.Dimension(225, 700));
        layoutMenu.setMinimumSize(new java.awt.Dimension(225, 700));
        layoutMenu.setName(""); // NOI18N
        layoutMenu.setPreferredSize(new java.awt.Dimension(225, 700));
        layoutMenu.setRequestFocusEnabled(false);
        layoutMenu.setLayout(new java.awt.GridBagLayout());

        saveArrangeButton.setText("Save arrangement");
        saveArrangeButton.setMinimumSize(new java.awt.Dimension(150, 25));
        saveArrangeButton.setPreferredSize(new java.awt.Dimension(150, 25));
        saveArrangeButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                saveArrangeButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 3, 0);
        layoutMenu.add(saveArrangeButton, gridBagConstraints);

        arrangementsListScrollpane.setMaximumSize(new java.awt.Dimension(32767, 250));
        arrangementsListScrollpane.setMinimumSize(new java.awt.Dimension(0, 150));
        arrangementsListScrollpane.setPreferredSize(new java.awt.Dimension(268, 150));
        arrangementsListScrollpane.setRequestFocusEnabled(false);

        arrangementsList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        arrangementsListScrollpane.setViewportView(arrangementsList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 15;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 5, 0);
        layoutMenu.add(arrangementsListScrollpane, gridBagConstraints);

        loadArrangeButton.setText("Load arrangement");
        loadArrangeButton.setMinimumSize(new java.awt.Dimension(150, 25));
        loadArrangeButton.setPreferredSize(new java.awt.Dimension(150, 25));
        loadArrangeButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                loadArrangeButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 3, 0);
        layoutMenu.add(loadArrangeButton, gridBagConstraints);

        arrangementsLabel.setFont(new java.awt.Font("Segoe UI", 0, 10)); // NOI18N
        arrangementsLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        arrangementsLabel.setText("Arrangements info");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 3, 0);
        layoutMenu.add(arrangementsLabel, gridBagConstraints);

        randomizeCheckbox.setSelected(true);
        randomizeCheckbox.setText("Randomize chart order");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 3, 0);
        layoutMenu.add(randomizeCheckbox, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(8, 0, 8, 0);
        layoutMenu.add(jSeparator1, gridBagConstraints);

        saveLayoutButton.setText("Save layout");
        saveLayoutButton.setMinimumSize(new java.awt.Dimension(150, 25));
        saveLayoutButton.setPreferredSize(new java.awt.Dimension(150, 25));
        saveLayoutButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                saveLayoutButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 2, 0);
        layoutMenu.add(saveLayoutButton, gridBagConstraints);

        loadLayoutButton.setText("Load layout");
        loadLayoutButton.setMinimumSize(new java.awt.Dimension(150, 25));
        loadLayoutButton.setPreferredSize(new java.awt.Dimension(150, 25));
        loadLayoutButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                loadLayoutButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 3, 0);
        layoutMenu.add(loadLayoutButton, gridBagConstraints);

        arrangementsListScrollpane1.setMaximumSize(new java.awt.Dimension(32767, 250));
        arrangementsListScrollpane1.setMinimumSize(new java.awt.Dimension(0, 150));
        arrangementsListScrollpane1.setPreferredSize(new java.awt.Dimension(268, 150));
        arrangementsListScrollpane1.setRequestFocusEnabled(false);

        layoutsList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        arrangementsListScrollpane1.setViewportView(layoutsList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipady = 50;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 3, 0);
        layoutMenu.add(arrangementsListScrollpane1, gridBagConstraints);

        layoutsLabel.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        layoutsLabel.setText("Layouts");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 2, 0);
        layoutMenu.add(layoutsLabel, gridBagConstraints);

        arrangeLabel.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        arrangeLabel.setText("Chart arrangements");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 2, 0);
        layoutMenu.add(arrangeLabel, gridBagConstraints);

        deleteLayoutButton.setText("Delete layout");
        deleteLayoutButton.setMinimumSize(new java.awt.Dimension(150, 25));
        deleteLayoutButton.setPreferredSize(new java.awt.Dimension(150, 25));
        deleteLayoutButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                deleteLayoutButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 3, 0);
        layoutMenu.add(deleteLayoutButton, gridBagConstraints);

        deleteArrangeButton.setText("Delete arrangement");
        deleteArrangeButton.setMinimumSize(new java.awt.Dimension(150, 25));
        deleteArrangeButton.setPreferredSize(new java.awt.Dimension(150, 25));
        deleteArrangeButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                deleteArrangeButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 16;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 3, 0);
        layoutMenu.add(deleteArrangeButton, gridBagConstraints);

        arrangeLinkLabel.setFont(new java.awt.Font("Segoe UI", 1, 10)); // NOI18N
        arrangeLinkLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        arrangeLinkLabel.setText("<html><u>what are arrangements?</u></html>");
        arrangeLinkLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseClicked(java.awt.event.MouseEvent evt)
            {
                arrangeLinkLabelMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        layoutMenu.add(arrangeLinkLabel, gridBagConstraints);

        layoutsLinkLabel.setFont(new java.awt.Font("Segoe UI", 1, 10)); // NOI18N
        layoutsLinkLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        layoutsLinkLabel.setText("<html><u>what are layouts?</u></html>");
        layoutsLinkLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseClicked(java.awt.event.MouseEvent evt)
            {
                layoutsLinkLabelMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        layoutMenu.add(layoutsLinkLabel, gridBagConstraints);

        keepStyleBox.setSelected(true);
        keepStyleBox.setText("Keep current style");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 3, 0);
        layoutMenu.add(keepStyleBox, gridBagConstraints);

        layoutMenuScrollpane.setViewportView(layoutMenu);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weighty = 0.1;
        chartsTab.add(layoutMenuScrollpane, gridBagConstraints);

        divider.setBorder(javax.swing.BorderFactory.createCompoundBorder(new javax.swing.border.LineBorder(new java.awt.Color(51, 51, 51), 2, true), javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED)));
        divider.setMaximumSize(new java.awt.Dimension(45, 458));
        divider.setMinimumSize(new java.awt.Dimension(45, 200));
        divider.setPreferredSize(new java.awt.Dimension(45, 0));
        divider.setLayout(new java.awt.GridLayout(1, 0));

        dividerButton.setBackground(new java.awt.Color(16, 16, 16));
        dividerButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/divider.png"))); // NOI18N
        dividerButton.setToolTipText("Toggle hide/show all menus, only applies to visible menus");
        dividerButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                dividerButtonActionPerformed(evt);
            }
        });
        divider.add(dividerButton);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weighty = 0.1;
        chartsTab.add(divider, gridBagConstraints);

        desktopPane.setPreferredSize(new java.awt.Dimension(500, 0));

        javax.swing.GroupLayout desktopPaneLayout = new javax.swing.GroupLayout(desktopPane);
        desktopPane.setLayout(desktopPaneLayout);
        desktopPaneLayout.setHorizontalGroup(
            desktopPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        desktopPaneLayout.setVerticalGroup(
            desktopPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        chartsTab.add(desktopPane, gridBagConstraints);

        tabbedPane.addTab("Charts", chartsTab);

        tradesTab.setDividerLocation(200);
        tradesTab.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        tradesTab.addComponentListener(new java.awt.event.ComponentAdapter()
        {
            public void componentHidden(java.awt.event.ComponentEvent evt)
            {
                tradesTabComponentHidden(evt);
            }
            public void componentShown(java.awt.event.ComponentEvent evt)
            {
                tradesTabComponentShown(evt);
            }
        });

        ltcTradesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {

            },
            new String []
            {
                "Trade time", "Block height", "Amount", "Total LTC", "LTC per QORT", "QORT per LTC", "Signature"
            }
        )
        {
            Class[] types = new Class []
            {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean []
            {
                false, false, false, false, false, false, false
            };

            public Class getColumnClass(int columnIndex)
            {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex)
            {
                return canEdit [columnIndex];
            }
        });
        ltcTradesTable.setFillsViewportHeight(true);
        ltcTradesScrollpane.setViewportView(ltcTradesTable);

        tradesTab.setRightComponent(ltcTradesScrollpane);

        javax.swing.GroupLayout ltcChartPlaceholderLayout = new javax.swing.GroupLayout(ltcChartPlaceholder);
        ltcChartPlaceholder.setLayout(ltcChartPlaceholderLayout);
        ltcChartPlaceholderLayout.setHorizontalGroup(
            ltcChartPlaceholderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 1245, Short.MAX_VALUE)
        );
        ltcChartPlaceholderLayout.setVerticalGroup(
            ltcChartPlaceholderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );

        tradesTab.setLeftComponent(ltcChartPlaceholder);

        tabbedPane.addTab("LTC trades", tradesTab);

        btcTradesTab.setDividerLocation(200);
        btcTradesTab.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        btcTradesTab.addComponentListener(new java.awt.event.ComponentAdapter()
        {
            public void componentHidden(java.awt.event.ComponentEvent evt)
            {
                btcTradesTabComponentHidden(evt);
            }
            public void componentShown(java.awt.event.ComponentEvent evt)
            {
                btcTradesTabComponentShown(evt);
            }
        });

        javax.swing.GroupLayout btcChartPlaceholderLayout = new javax.swing.GroupLayout(btcChartPlaceholder);
        btcChartPlaceholder.setLayout(btcChartPlaceholderLayout);
        btcChartPlaceholderLayout.setHorizontalGroup(
            btcChartPlaceholderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 1245, Short.MAX_VALUE)
        );
        btcChartPlaceholderLayout.setVerticalGroup(
            btcChartPlaceholderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );

        btcTradesTab.setLeftComponent(btcChartPlaceholder);

        btcTradesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {

            },
            new String []
            {
                "Trade time", "Block height", "Amount", "Total BTC", "BTC per QORT", "QORT per BTC", "Signature"
            }
        )
        {
            Class[] types = new Class []
            {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean []
            {
                false, false, false, false, false, false, false
            };

            public Class getColumnClass(int columnIndex)
            {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex)
            {
                return canEdit [columnIndex];
            }
        });
        btcTradesTable.setFillsViewportHeight(true);
        btcTradesScrollpane.setViewportView(btcTradesTable);

        btcTradesTab.setRightComponent(btcTradesScrollpane);

        tabbedPane.addTab("BTC trades", btcTradesTab);

        dogeTradesTab.setDividerLocation(200);
        dogeTradesTab.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        dogeTradesTab.addComponentListener(new java.awt.event.ComponentAdapter()
        {
            public void componentHidden(java.awt.event.ComponentEvent evt)
            {
                dogeTradesTabComponentHidden(evt);
            }
            public void componentShown(java.awt.event.ComponentEvent evt)
            {
                dogeTradesTabComponentShown(evt);
            }
        });

        javax.swing.GroupLayout dogeChartPlaceholderLayout = new javax.swing.GroupLayout(dogeChartPlaceholder);
        dogeChartPlaceholder.setLayout(dogeChartPlaceholderLayout);
        dogeChartPlaceholderLayout.setHorizontalGroup(
            dogeChartPlaceholderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 1245, Short.MAX_VALUE)
        );
        dogeChartPlaceholderLayout.setVerticalGroup(
            dogeChartPlaceholderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );

        dogeTradesTab.setLeftComponent(dogeChartPlaceholder);

        dogeTradesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {

            },
            new String []
            {
                "Trade time", "Block height", "Amount", "Total DOGE", "DOGE per QORT", "QORT per DOGE", "Signature"
            }
        )
        {
            Class[] types = new Class []
            {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean []
            {
                false, false, false, false, false, false, false
            };

            public Class getColumnClass(int columnIndex)
            {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex)
            {
                return canEdit [columnIndex];
            }
        });
        dogeTradesTable.setFillsViewportHeight(true);
        dogeTradesScrollpane.setViewportView(dogeTradesTable);

        dogeTradesTab.setRightComponent(dogeTradesScrollpane);

        tabbedPane.addTab("Doge trades", dogeTradesTab);

        ravenTradesTab.setDividerLocation(200);
        ravenTradesTab.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        ravenTradesTab.addComponentListener(new java.awt.event.ComponentAdapter()
        {
            public void componentHidden(java.awt.event.ComponentEvent evt)
            {
                ravenTradesTabComponentHidden(evt);
            }
            public void componentShown(java.awt.event.ComponentEvent evt)
            {
                ravenTradesTabComponentShown(evt);
            }
        });

        javax.swing.GroupLayout ravenChartPlaceholderLayout = new javax.swing.GroupLayout(ravenChartPlaceholder);
        ravenChartPlaceholder.setLayout(ravenChartPlaceholderLayout);
        ravenChartPlaceholderLayout.setHorizontalGroup(
            ravenChartPlaceholderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 1245, Short.MAX_VALUE)
        );
        ravenChartPlaceholderLayout.setVerticalGroup(
            ravenChartPlaceholderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 200, Short.MAX_VALUE)
        );

        ravenTradesTab.setLeftComponent(ravenChartPlaceholder);

        ravenTradesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {

            },
            new String []
            {
                "Trade time", "Block height", "Amount", "Total RAVEN", "RAVEN per QORT", "QORT per RAVEN", "Signature"
            }
        )
        {
            Class[] types = new Class []
            {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean []
            {
                false, false, false, false, false, false, false
            };

            public Class getColumnClass(int columnIndex)
            {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex)
            {
                return canEdit [columnIndex];
            }
        });
        ravenTradesTable.setFillsViewportHeight(true);
        ravenTradesScrollpane.setViewportView(ravenTradesTable);

        ravenTradesTab.setRightComponent(ravenTradesScrollpane);

        tabbedPane.addTab("Ravencoin trades", ravenTradesTab);

        digibyteTradesTab.setDividerLocation(200);
        digibyteTradesTab.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        digibyteTradesTab.addComponentListener(new java.awt.event.ComponentAdapter()
        {
            public void componentHidden(java.awt.event.ComponentEvent evt)
            {
                digibyteTradesTabComponentHidden(evt);
            }
            public void componentShown(java.awt.event.ComponentEvent evt)
            {
                digibyteTradesTabComponentShown(evt);
            }
        });

        javax.swing.GroupLayout digibyteChartPlaceholderLayout = new javax.swing.GroupLayout(digibyteChartPlaceholder);
        digibyteChartPlaceholder.setLayout(digibyteChartPlaceholderLayout);
        digibyteChartPlaceholderLayout.setHorizontalGroup(
            digibyteChartPlaceholderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 1245, Short.MAX_VALUE)
        );
        digibyteChartPlaceholderLayout.setVerticalGroup(
            digibyteChartPlaceholderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 200, Short.MAX_VALUE)
        );

        digibyteTradesTab.setLeftComponent(digibyteChartPlaceholder);

        digibyteTradesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {

            },
            new String []
            {
                "Trade time", "Block height", "Amount", "Total DGB", "DGB per QORT", "QORT per DGB", "Signature"
            }
        )
        {
            Class[] types = new Class []
            {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean []
            {
                false, false, false, false, false, false, false
            };

            public Class getColumnClass(int columnIndex)
            {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex)
            {
                return canEdit [columnIndex];
            }
        });
        digibyteTradesTable.setFillsViewportHeight(true);
        digibyteTradesScrollpane.setViewportView(digibyteTradesTable);

        digibyteTradesTab.setRightComponent(digibyteTradesScrollpane);

        tabbedPane.addTab("Digibyte trades", digibyteTradesTab);

        updateTab.setDividerLocation(300);
        updateTab.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        allTxMenuPanel.setLayout(new java.awt.GridBagLayout());

        updateButton.setText("Update now");
        updateButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                updateButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 35, 0);
        allTxMenuPanel.add(updateButton, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipady = 15;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.PAGE_END;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        allTxMenuPanel.add(progressBar, gridBagConstraints);

        stopButton.setText("Stop");
        stopButton.setEnabled(false);
        stopButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                stopButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(30, 0, 0, 0);
        allTxMenuPanel.add(stopButton, gridBagConstraints);

        updateStatusLabel.setFont(new java.awt.Font("Ebrima", 1, 12)); // NOI18N
        updateStatusLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        updateStatusLabel.setText("Fetch the latest trades and prices by clicking 'Update now'");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.PAGE_END;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        allTxMenuPanel.add(updateStatusLabel, gridBagConstraints);

        lookupStatusLabel.setFont(new java.awt.Font("Ebrima", 1, 12)); // NOI18N
        lookupStatusLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lookupStatusLabel.setText("Fetch the latest trades and prices by clicking 'Update now'");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.PAGE_END;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        allTxMenuPanel.add(lookupStatusLabel, gridBagConstraints);

        autoUpdateCheckbox.setSelected(true);
        autoUpdateCheckbox.setText("Auto update trades");
        autoUpdateCheckbox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                autoUpdateCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        allTxMenuPanel.add(autoUpdateCheckbox, gridBagConstraints);

        updateTab.setTopComponent(allTxMenuPanel);

        jScrollPane1.setViewportView(txTable);

        updateTab.setRightComponent(jScrollPane1);

        tabbedPane.addTab("Get latest trades", updateTab);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void tipDialogFocusLost(java.awt.event.FocusEvent evt)//GEN-FIRST:event_tipDialogFocusLost
    {//GEN-HEADEREND:event_tipDialogFocusLost
      if(evt.getOppositeComponent() instanceof  JDialog)
            return;
        
        tipDialog.setVisible(false);
    }//GEN-LAST:event_tipDialogFocusLost

    private void tradeInfoDialogFocusLost(java.awt.event.FocusEvent evt)//GEN-FIRST:event_tradeInfoDialogFocusLost
    {//GEN-HEADEREND:event_tradeInfoDialogFocusLost
        if(evt.getOppositeComponent() instanceof  JDialog)
            return;
        //When chart dialog steals focus, then tradesInfoTable gains focus the dialog
        //will be set to not-visible if we don't return in that case
        if(evt.getOppositeComponent() == tradeInfoTable)
            return;
        
        tradeInfoDialog.setVisible(false);
    }//GEN-LAST:event_tradeInfoDialogFocusLost

    private void autoUpdateCheckboxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_autoUpdateCheckboxActionPerformed
    {//GEN-HEADEREND:event_autoUpdateCheckboxActionPerformed
        File settingsFile = new File(System.getProperty("user.dir") + "/bin/settings.json");
        if(settingsFile.exists())
        {
            try
            {
                String jsonString = Files.readString(settingsFile.toPath());
                if(jsonString != null)
                {
                    JSONObject jsonObject = new JSONObject(jsonString);
                    jsonObject.put("autoUpdate", String.valueOf(autoUpdateCheckbox.isSelected()));

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
        if(autoUpdateCheckbox.isSelected())
        {
            //Enable timer
            initUpdateTimer();
        }
        else
        {
            //Disable timer
            try
            {
                updateTimer.cancel();
                if(lastUpdatedTime > 0)
                updateStatusLabel.setText("Last updated : " + Utilities.DateFormatShort(lastUpdatedTime));
            }
            catch (Exception e)
            {
                BackgroundService.AppendLog(e);
            }
        }
    }//GEN-LAST:event_autoUpdateCheckboxActionPerformed

    private void stopButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_stopButtonActionPerformed
    {//GEN-HEADEREND:event_stopButtonActionPerformed
        TradeExtractor.LOOKUP_HALTED = true;
        stopButton.setEnabled(false);
        progressBar.setString("Terminating transations lookup  |  Please wait...");
    }//GEN-LAST:event_stopButtonActionPerformed

    private void updateButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_updateButtonActionPerformed
    {//GEN-HEADEREND:event_updateButtonActionPerformed
        currentTick = 0;
        TradeExtractor.extractTrades(this);
    }//GEN-LAST:event_updateButtonActionPerformed

    private void dogeTradesTabComponentShown(java.awt.event.ComponentEvent evt)//GEN-FIRST:event_dogeTradesTabComponentShown
    {//GEN-HEADEREND:event_dogeTradesTabComponentShown
        try{((ChartWindow) dogeTradesTab.getTopComponent()).setSelected(true);}
        catch (PropertyVetoException e){}
        chartWindowFocused = false;
    }//GEN-LAST:event_dogeTradesTabComponentShown

    private void dogeTradesTabComponentHidden(java.awt.event.ComponentEvent evt)//GEN-FIRST:event_dogeTradesTabComponentHidden
    {//GEN-HEADEREND:event_dogeTradesTabComponentHidden
        if(dogeTradesTab.getTopComponent() instanceof ChartWindow == false)
            return;

        ((ChartWindow)dogeTradesTab.getTopComponent()).chartMaker.chartDialog.setVisible(false);
    }//GEN-LAST:event_dogeTradesTabComponentHidden

    private void btcTradesTabComponentShown(java.awt.event.ComponentEvent evt)//GEN-FIRST:event_btcTradesTabComponentShown
    {//GEN-HEADEREND:event_btcTradesTabComponentShown
        try{((ChartWindow) btcTradesTab.getTopComponent()).setSelected(true);}
        catch (PropertyVetoException e){}
        chartWindowFocused = false;
    }//GEN-LAST:event_btcTradesTabComponentShown

    private void btcTradesTabComponentHidden(java.awt.event.ComponentEvent evt)//GEN-FIRST:event_btcTradesTabComponentHidden
    {//GEN-HEADEREND:event_btcTradesTabComponentHidden
        if(btcTradesTab.getTopComponent() instanceof ChartWindow == false)
            return;

        ((ChartWindow)btcTradesTab.getTopComponent()).chartMaker.chartDialog.setVisible(false);
    }//GEN-LAST:event_btcTradesTabComponentHidden

    private void tradesTabComponentShown(java.awt.event.ComponentEvent evt)//GEN-FIRST:event_tradesTabComponentShown
    {//GEN-HEADEREND:event_tradesTabComponentShown
        //Selecting the ltc trades chart window during init didn't work (selected frame is more estethic)
        try{((ChartWindow) tradesTab.getTopComponent()).setSelected(true);}
        catch (PropertyVetoException e){}
        //ensures saveCurrentArrangement doesn't get called when the ltc trades chartwindow is resized
        chartWindowFocused = false;
    }//GEN-LAST:event_tradesTabComponentShown

    private void tradesTabComponentHidden(java.awt.event.ComponentEvent evt)//GEN-FIRST:event_tradesTabComponentHidden
    {//GEN-HEADEREND:event_tradesTabComponentHidden
        if(tradesTab.getTopComponent() instanceof ChartWindow == false)
            return;

        ((ChartWindow)tradesTab.getTopComponent()).chartMaker.chartDialog.setVisible(false);
    }//GEN-LAST:event_tradesTabComponentHidden

    private void chartsTabComponentHidden(java.awt.event.ComponentEvent evt)//GEN-FIRST:event_chartsTabComponentHidden
    {//GEN-HEADEREND:event_chartsTabComponentHidden
        hideAllChartDialogs();
    }//GEN-LAST:event_chartsTabComponentHidden

    private void dividerButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_dividerButtonActionPerformed
    {//GEN-HEADEREND:event_dividerButtonActionPerformed
        if(chartsMenu.isVisible())
        {
            chartsMenu.setVisible(false);
            styleMenuScrollpane.setVisible(false);
            layoutMenuScrollpane.setVisible(false);
        }
        else
        {
            chartsMenu.setVisible(true);
            if(toggleStyleButton.isSelected())
            {
                styleMenuScrollpane.setVisible(true);
            }
            if(toggleLayoutButton.isSelected())
            {
                layoutMenuScrollpane.setVisible(true);
            }
        }
    }//GEN-LAST:event_dividerButtonActionPerformed

    private void layoutsLinkLabelMouseClicked(java.awt.event.MouseEvent evt)//GEN-FIRST:event_layoutsLinkLabelMouseClicked
    {//GEN-HEADEREND:event_layoutsLinkLabelMouseClicked
        tipDialogLabel.setText(Utilities.AllignHTML(
            "Layouts are a combination of chart types, chart arrangements and the style that is applied to them.<br/><br/>"
            + "Any changes to your current layout are automatically saved and loaded when you restart the program.<br/><br/>"
            + "You can create and save as many layouts as you like for different purposes. You could create a layout for "
            + "every different cross-chain price pair, or separate layouts for trades and prices for example.<br/><br/>"
            + "Tip: You can double click on a layout to load it.","justify"));

    int x = (int)evt.getLocationOnScreen().getX();
    int y = (int)evt.getLocationOnScreen().getY();

    boolean belowPointer = evt.getYOnScreen() < (java.awt.Toolkit.getDefaultToolkit().getScreenSize().height / 2);

    setInfoDialogBounds(x, y, 6,belowPointer);
    }//GEN-LAST:event_layoutsLinkLabelMouseClicked

    private void arrangeLinkLabelMouseClicked(java.awt.event.MouseEvent evt)//GEN-FIRST:event_arrangeLinkLabelMouseClicked
    {//GEN-HEADEREND:event_arrangeLinkLabelMouseClicked
        tipDialogLabel.setText(Utilities.AllignHTML(
            "There are numerous ways in which you can arrange your charts on the desktop. When adding or removing "
            + "charts from the desktop the arrangements list will show the available arrangements for the current "
            + "number of visible charts.<br/><br/>"
            + "Enabling chart order randomization will randomly place the current charts in different positions of "
            + "the selected arrangement. Keep re-loading the arrangement until you get one that's suitable for your "
            + "purposes.<br/><br/>"
            + "You can create and save your own arrangements and apply them to any selection of chart types "
            + "that are currently on your desktop.<br/><br/>"
            + "Tip: You can double click on an arrangement to load it.","justify"));

    int x = (int)evt.getLocationOnScreen().getX();
    int y = (int)evt.getLocationOnScreen().getY();

    boolean belowPointer = evt.getYOnScreen() < (java.awt.Toolkit.getDefaultToolkit().getScreenSize().height / 2);

    setInfoDialogBounds(x,y,6,belowPointer);
    }//GEN-LAST:event_arrangeLinkLabelMouseClicked

    private void deleteArrangeButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deleteArrangeButtonActionPerformed
    {//GEN-HEADEREND:event_deleteArrangeButtonActionPerformed
        if(arrangementsList.getSelectedIndex() == -1)
            return;

        if(JOptionPane.showConfirmDialog(arrangementsList, "Delete " + arrangementsList.getSelectedValue() + "?",
            "Please confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
            return;

        File file = new File(System.getProperty("user.dir") +
            "/" + Folders.ARRANGE.get() + "/" + arrangementsList.getSelectedValue() + ".arng");
        file.delete();

        //using index to remove causes weird behaviour, use model
        var model = (DefaultListModel)arrangementsList.getModel();
        model.removeElement(arrangementsList.getSelectedValue());
        updateArrangementsList();
    }//GEN-LAST:event_deleteArrangeButtonActionPerformed

    private void deleteLayoutButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deleteLayoutButtonActionPerformed
    {//GEN-HEADEREND:event_deleteLayoutButtonActionPerformed
        if(layoutsList.getSelectedIndex() == -1)
            return;

        if(JOptionPane.showConfirmDialog(deleteLayoutButton, "Delete " + layoutsList.getSelectedValue() + "?",
            "Please confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
            return;

        File file = new File(System.getProperty("user.dir") +
            "/" + Folders.LAYOUTS.get() + "/" + layoutsList.getSelectedValue() + ".layout");
        file.delete();

        //using index to remove causes weird behaviour, use model
        var model = (DefaultListModel)layoutsList.getModel();
        model.removeElement(layoutsList.getSelectedValue());
        updateLayoutsList(false);
    }//GEN-LAST:event_deleteLayoutButtonActionPerformed

    private void loadLayoutButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_loadLayoutButtonActionPerformed
    {//GEN-HEADEREND:event_loadLayoutButtonActionPerformed
        if(layoutsList.getSelectedIndex() == -1)
            return;

        if (desktopPane.getSelectedFrame() == null)
            selectClosestFrame();

        ChartStyle style = null;
        if(desktopPane.getSelectedFrame() != null)
        {
            ChartWindow selected = (ChartWindow) desktopPane.getSelectedFrame();
            style = copyChartStyle(selected.type.toString(), "CurrentLayout", Folders.LAYOUTS.get(), Extensions.LAYOUT);
        }
        
        for(JInternalFrame frame : desktopPane.getAllFrames())
        {
            var cw = (ChartWindow) frame;
            addToChartsList(cw.getTitle());
            //fail safe, for some reason the chart dialog for disposed frames would not disappear
            cw.chartMaker.chartDialog.setVisible(false);
            frame.dispose();
        }

        loadLayout(layoutsList.getSelectedValue(),style);
        updateArrangementsList();

        //at this point the selected frame from above was disposed in loadLayout
        if(selectClosestFrame() && !keepStyleBox.isSelected())
            setTradesChartsStyle("CurrentLayout", Folders.LAYOUTS.get(), Extensions.LAYOUT,
                ((ChartWindow)desktopPane.getSelectedFrame()).type.toString());

    }//GEN-LAST:event_loadLayoutButtonActionPerformed

    private void saveLayoutButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_saveLayoutButtonActionPerformed
    {//GEN-HEADEREND:event_saveLayoutButtonActionPerformed
        if(desktopPane.getAllFrames().length == 0)
            return;

        var model = (DefaultListModel) layoutsList.getModel();

        String fileName = null;

        if(layoutsList.getSelectedIndex() >= 0)
        {
            do
            {
                int choice = JOptionPane.showConfirmDialog(saveLayoutButton,
                    Utilities.AllignCenterHTML("Overwrite '" + layoutsList.getSelectedValue() + "'?<br/>Choose 'No' to create a new file"),
                    "Overwrite?",
                    JOptionPane.YES_NO_OPTION);

                if(choice == JOptionPane.CANCEL_OPTION)
                    return;
                
                if(choice == JOptionPane.YES_OPTION)
                {
                    fileName = layoutsList.getSelectedValue();
                    break;
                }
                if(choice == JOptionPane.NO_OPTION)
                    fileName = JOptionPane.showInputDialog(saveLayoutButton, "Creating a new layout. Please give it a name");
            }
            while(fileName != null && Utilities.containsIgnoreCase(model.toArray(), fileName));

        }
        else
        {
            fileName = JOptionPane.showInputDialog(saveLayoutButton, "Creating a new layout. Please give it a name");
        }

        if(fileName == null)
            return;

        File file = new File(System.getProperty("user.dir") + "/" + Folders.LAYOUTS.get() + "/" + fileName + ".layout");

        if(!file.exists())
            ConnectionDB.CreateDatabase(fileName, Folders.LAYOUTS.get(),Extensions.LAYOUT);

        //We save each chart window's style separately, the user may have set a different style for each chart
        for(JInternalFrame frame : desktopPane.getAllFrames())
        {
            var cw = (ChartWindow) frame;
            saveLayout(fileName, cw.type, false,false);
        }
        updateLayoutsList(false);
    }//GEN-LAST:event_saveLayoutButtonActionPerformed

    private void loadArrangeButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_loadArrangeButtonActionPerformed
    {//GEN-HEADEREND:event_loadArrangeButtonActionPerformed
        if(arrangementsList.getSelectedIndex() == -1)
            return;

        Thread thread = new Thread(() ->
        {
            //<editor-fold defaultstate="collapsed" desc="Check for maximized frame">
            //Loading an arrangement when a frame is maximized causes an incorrect layout. De-maximizing a frame
            //before setting the new arrangement also causes an incorrect layout. If a frame is maximized, we de-maximize
            //it, then wait for 1,5 seconds, then continue the operation.
            boolean maximized = false;

            for (JInternalFrame frame : desktopPane.getAllFrames())
            {
                if (frame.isMaximum())
                {
                    try
                    {
                        frame.setMaximum(false);
                        maximized = true;
                        break;//only 1 maximized frame possible
                    }
                    catch (PropertyVetoException e)
                    {
                        BackgroundService.AppendLog(e);
                    }
                }
            }
            if (maximized)
            {
                try
                {
                    loadArrangeButton.setEnabled(false);
                    saveArrangeButton.setEnabled(false);
                    Thread.sleep(500);
                    loadArrangeButton.setEnabled(true);
                    saveArrangeButton.setEnabled(true);
                }
                catch (InterruptedException e)
                {
                    BackgroundService.AppendLog(e);
                }
            }
            //</editor-fold>

            loadArrangement(arrangementsList.getSelectedValue());

        });

        thread.start();
    }//GEN-LAST:event_loadArrangeButtonActionPerformed

    private void saveArrangeButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_saveArrangeButtonActionPerformed
    {//GEN-HEADEREND:event_saveArrangeButtonActionPerformed
        if(desktopPane.getAllFrames().length <= 1)
            return;

        var model = (DefaultListModel) arrangementsList.getModel();

        String fileName = null;

        if(arrangementsList.getSelectedIndex() >= 0)
        {
            do
            {
                int choice = JOptionPane.showConfirmDialog(saveArrangeButton,
                    Utilities.AllignCenterHTML("Overwrite '" + arrangementsList.getSelectedValue() + "'?<br/>Choose 'No' to create a new file"),
                    "Overwrite?",
                    JOptionPane.YES_NO_OPTION);

                if(choice == JOptionPane.CANCEL_OPTION)
                    return;
                
                if(choice == JOptionPane.YES_OPTION)
                {
                    fileName = arrangementsList.getSelectedValue();
                    break;
                }
                if(choice == JOptionPane.NO_OPTION)
                    fileName = JOptionPane.showInputDialog(saveArrangeButton, "Creating a new arrangement. Please give it a name");
            }
            while(fileName != null && Utilities.containsIgnoreCase(model.toArray(), fileName));

        }
        else
        {
            fileName = JOptionPane.showInputDialog(saveArrangeButton, "Creating a new arrangement. Please give it a name");
        }

        if(fileName == null)
            return;

        saveArrangement(fileName);

        if(!model.contains(fileName))
            model.addElement(fileName);

        arrangementsLabel.setText(Utilities.AllignCenterHTML(model.getSize() + " arrangements<br/>for " +
            desktopPane.getAllFrames().length + " chart layout"));
    }//GEN-LAST:event_saveArrangeButtonActionPerformed

    private void applyLinkLabelMouseClicked(java.awt.event.MouseEvent evt)//GEN-FIRST:event_applyLinkLabelMouseClicked
    {//GEN-HEADEREND:event_applyLinkLabelMouseClicked
        tipDialogLabel.setText(Utilities.AllignHTML(
            "Enable auto apply to all if you want to automatically apply any changes that you make to the "
            + "style of the selected chart to all charts in real time.<br/><br/>"
            + "Keep in mind that applying changes to many charts simultaneously can diminish the programs "
            + "responsiveness and cause a bad user experience.<br/><br/>"
            + "If your system cannot handle real-time auto update it's best to apply the style to the selected "
            + "chart first and then click the 'Appy to all' button.","justify"));

        int x = (int)evt.getLocationOnScreen().getX();
        int y = (int)evt.getLocationOnScreen().getY();

        boolean belowPointer = evt.getYOnScreen() < (java.awt.Toolkit.getDefaultToolkit().getScreenSize().height / 2);

        setInfoDialogBounds(x,y,4,belowPointer);
    }//GEN-LAST:event_applyLinkLabelMouseClicked

    private void applyToAllButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_applyToAllButtonActionPerformed
    {//GEN-HEADEREND:event_applyToAllButtonActionPerformed
        if(desktopPane.getSelectedFrame() == null)
            JOptionPane.showMessageDialog(applyToAllButton, "Please select a chart from which to apply its style to all others");

        Chart selectedType = ((ChartWindow)desktopPane.getSelectedFrame()).type;

        for(JInternalFrame frame : desktopPane.getAllFrames())
        {
            if(frame == desktopPane.getSelectedFrame())
                continue;

            ChartWindow cw = (ChartWindow)frame;
            ChartStyle style = copyChartStyle(selectedType.toString(), "CurrentLayout", Folders.LAYOUTS.get(), Extensions.LAYOUT);
            if(style != null)
                setChartStyle(cw,style);
        }
        saveLayout("CurrentLayout", null, true, false);
    }//GEN-LAST:event_applyToAllButtonActionPerformed

    private void stylesLinkLabelMouseClicked(java.awt.event.MouseEvent evt)//GEN-FIRST:event_stylesLinkLabelMouseClicked
    {//GEN-HEADEREND:event_stylesLinkLabelMouseClicked
        tipDialogLabel.setText(Utilities.AllignHTML(
            "Styles are visual themes that you can apply to your user interface environment.<br/><br/>"
            + "You can custimize the way your UI and charts look by setting different colors, fonts "
            + "and chart properties.<br/><br/>"
            + "Applying a different style to a layout will not affect the rest of your layout settings, like "
            + "the chart arrangement or the chart types.<br/><br/>"
            + "Tip: You can double click on a style to load it.","justify"));

        int x = (int)evt.getLocationOnScreen().getX();
        int y = (int)evt.getLocationOnScreen().getY();

        boolean belowPointer = evt.getYOnScreen() < (java.awt.Toolkit.getDefaultToolkit().getScreenSize().height / 2);

        setInfoDialogBounds(x, y, 6,belowPointer);
    }//GEN-LAST:event_stylesLinkLabelMouseClicked

    private void colorLabelMouseReleased(java.awt.event.MouseEvent evt)//GEN-FIRST:event_colorLabelMouseReleased
    {//GEN-HEADEREND:event_colorLabelMouseReleased
        if(isChangeApplicable())
            showColorChooser((JLabel) evt.getSource());
    }//GEN-LAST:event_colorLabelMouseReleased

    private void deleteStyleButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deleteStyleButtonActionPerformed
    {//GEN-HEADEREND:event_deleteStyleButtonActionPerformed
        if(stylesList.getSelectedIndex() == -1)
            return;

        if(JOptionPane.showConfirmDialog(deleteStyleButton, "Delete " + stylesList.getSelectedValue() + "?",
            "Please confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
            return;

        File file = new File(System.getProperty("user.dir") + "/" +
        Folders.STYLES.get() + "/" + stylesList.getSelectedValue() + ".style");
        file.delete();

        //using index to remove causes weird behaviour, use model
        var model = (DefaultListModel)stylesList.getModel();
        model.removeElement(stylesList.getSelectedValue());
        updateStylesList(false);
    }//GEN-LAST:event_deleteStyleButtonActionPerformed

    private void loadStyleButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_loadStyleButtonActionPerformed
    {//GEN-HEADEREND:event_loadStyleButtonActionPerformed
        if(stylesList.getSelectedIndex() == -1)
            return;

        if(loadUiStyleBox.isSelected())
        {
            setGuiValues(stylesList.getSelectedValue(), Folders.STYLES.get(), Extensions.STYLE);
            updateGuiItems();
        }

        if(desktopPane.getSelectedFrame() == null)
            selectClosestFrame();

        var selectedChart = (ChartWindow)desktopPane.getSelectedFrame();

        for(JInternalFrame frame : desktopPane.getAllFrames())
        {
            var currentChart = (ChartWindow)frame;

            if(!applyStyleToAllBox.isSelected() && selectedChart != currentChart)
                continue;

            ChartStyle style = copyChartStyle("style",stylesList.getSelectedValue(), Folders.STYLES.get(),Extensions.STYLE);
            if(style != null)
                setChartStyle(currentChart, style);
        }

        //        selectClosestFrame();
        setTradesChartsStyle(stylesList.getSelectedValue(), Folders.STYLES.get(), Extensions.STYLE,"style");

        saveLayout("CurrentLayout", null, true, false);
    }//GEN-LAST:event_loadStyleButtonActionPerformed

    private void saveStyleButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_saveStyleButtonActionPerformed
    {//GEN-HEADEREND:event_saveStyleButtonActionPerformed
        if(desktopPane.getAllFrames().length == 0)
            return;

        var model = (DefaultListModel) stylesList.getModel();

        String dbFileName = null;

        if(stylesList.getSelectedIndex() >= 0)
        {
            do
            {
                int choice = JOptionPane.showConfirmDialog(saveStyleButton,
                    Utilities.AllignCenterHTML("Overwrite '" + stylesList.getSelectedValue() + "'?<br/>Choose 'No' to create a new file"),
                    "Overwrite?",
                    JOptionPane.YES_NO_OPTION);

                if(choice == JOptionPane.CANCEL_OPTION)
                    return;
                
                if(choice == JOptionPane.YES_OPTION)
                {
                    dbFileName = stylesList.getSelectedValue();
                    break;
                }
                if(choice == JOptionPane.NO_OPTION)
                    dbFileName = JOptionPane.showInputDialog(saveStyleButton, "Creating a new style. Please give it a name");
            }
            while(dbFileName != null && Utilities.containsIgnoreCase(model.toArray(), dbFileName));

        }
        else
        {
            dbFileName = JOptionPane.showInputDialog(saveStyleButton, "Creating a new style. Please give it a name");
        }

        if(dbFileName == null)
            return;

        File dbFile = new File(System.getProperty("user.dir") + "/" + Folders.STYLES.get() + "/" + dbFileName + ".style");

        if(!dbFile.exists())
            ConnectionDB.CreateDatabase(dbFileName, Folders.STYLES.get(),Extensions.STYLE);

        var cw = (ChartWindow) desktopPane.getSelectedFrame();

        //copy the selected table and gui table to the styles database created above
        try(Connection source = ConnectionDB.getConnection(
            "CurrentLayout", Folders.LAYOUTS.get(),Extensions.LAYOUT))
        {
            try(Connection target = ConnectionDB.getConnection(
                dbFileName, Folders.STYLES.get(),Extensions.STYLE))
            {
                boolean styleSaved = dbManager.copyTable(source, target, cw.type.toString(), "style");
                boolean guiSaved = dbManager.copyTable(source, target, "gui", "gui");

                if(!styleSaved || !guiSaved)
                    JOptionPane.showMessageDialog(saveStyleButton, "Saving style failed");
            }
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
            JOptionPane.showMessageDialog(saveStyleButton, "Saving style failed\n" + e.toString());
        }

        updateStylesList(false);

    }//GEN-LAST:event_saveStyleButtonActionPerformed

    private void showYcrosshairBoxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_showYcrosshairBoxActionPerformed
    {//GEN-HEADEREND:event_showYcrosshairBoxActionPerformed
        toggleCheckbox(showYcrosshairBox, showYcrosshairBox.isSelected());
    }//GEN-LAST:event_showYcrosshairBoxActionPerformed

    private void showXcrosshairsBoxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_showXcrosshairsBoxActionPerformed
    {//GEN-HEADEREND:event_showXcrosshairsBoxActionPerformed
        toggleCheckbox(showXcrosshairsBox, showXcrosshairsBox.isSelected());
    }//GEN-LAST:event_showXcrosshairsBoxActionPerformed

    private void showDialogBoxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_showDialogBoxActionPerformed
    {//GEN-HEADEREND:event_showDialogBoxActionPerformed
        toggleCheckbox(showDialogBox, showDialogBox.isSelected());
    }//GEN-LAST:event_showDialogBoxActionPerformed

    private void showTextBoxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_showTextBoxActionPerformed
    {//GEN-HEADEREND:event_showTextBoxActionPerformed
        toggleCheckbox(showTextBox, showTextBox.isSelected());
    }//GEN-LAST:event_showTextBoxActionPerformed

    private void fontSliderMouseReleased(java.awt.event.MouseEvent evt)//GEN-FIRST:event_fontSliderMouseReleased
    {//GEN-HEADEREND:event_fontSliderMouseReleased
        applyFont();
    }//GEN-LAST:event_fontSliderMouseReleased

    private void fontSliderStateChanged(javax.swing.event.ChangeEvent evt)//GEN-FIRST:event_fontSliderStateChanged
    {//GEN-HEADEREND:event_fontSliderStateChanged
        fontLabel.setText("Font size : " + fontSlider.getValue());
    }//GEN-LAST:event_fontSliderStateChanged

    private void fontsBoxItemStateChanged(java.awt.event.ItemEvent evt)//GEN-FIRST:event_fontsBoxItemStateChanged
    {//GEN-HEADEREND:event_fontsBoxItemStateChanged
        if(initComplete)
            applyFont();
    }//GEN-LAST:event_fontsBoxItemStateChanged

    private void averagingSliderMouseReleased(java.awt.event.MouseEvent evt)//GEN-FIRST:event_averagingSliderMouseReleased
    {//GEN-HEADEREND:event_averagingSliderMouseReleased
        if(!isChangeApplicable())
            return;

        boolean applyToSelectedOnly = desktopPane.getSelectedFrame() != null && !applyToAllChartsBox.isSelected();

        if(applyToSelectedOnly)
        {
            ChartWindow chartWindow = (ChartWindow) desktopPane.getSelectedFrame();
            chartWindow.averageBy = averagingSlider.getValue();
            chartWindow.chartMaker.setMovingAveragePeriod(chartWindow.averageBy);

            saveLayout("CurrentLayout", chartWindow.type, false,false);
        }
        else if(applyToAllChartsBox.isSelected())
        {
            for(JInternalFrame frame : desktopPane.getAllFrames())
            {
                ChartWindow chartWindow = (ChartWindow) frame;
                chartWindow.averageBy = averagingSlider.getValue();
                chartWindow.chartMaker.setMovingAveragePeriod(chartWindow.averageBy);
            }
            saveLayout("CurrentLayout", null, true,false);
        }
    }//GEN-LAST:event_averagingSliderMouseReleased

    private void averagingSliderStateChanged(javax.swing.event.ChangeEvent evt)//GEN-FIRST:event_averagingSliderStateChanged
    {//GEN-HEADEREND:event_averagingSliderStateChanged
        averagingLabel.setText("Average by " + averagingSlider.getValue() + " trades");
    }//GEN-LAST:event_averagingSliderStateChanged

    private void movingAverageBoxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_movingAverageBoxActionPerformed
    {//GEN-HEADEREND:event_movingAverageBoxActionPerformed
        toggleCheckbox(movingAverageBox, movingAverageBox.isSelected());
    }//GEN-LAST:event_movingAverageBoxActionPerformed

    private void tradeVolumeBoxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_tradeVolumeBoxActionPerformed
    {//GEN-HEADEREND:event_tradeVolumeBoxActionPerformed
        toggleCheckbox(tradeVolumeBox, tradeVolumeBox.isSelected());
    }//GEN-LAST:event_tradeVolumeBoxActionPerformed

    private void showSubBoxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_showSubBoxActionPerformed
    {//GEN-HEADEREND:event_showSubBoxActionPerformed
        toggleCheckbox(showSubBox, showSubBox.isSelected());
    }//GEN-LAST:event_showSubBoxActionPerformed

    private void drawX_boxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_drawX_boxActionPerformed
    {//GEN-HEADEREND:event_drawX_boxActionPerformed
        toggleCheckbox(drawX_box, drawX_box.isSelected());
    }//GEN-LAST:event_drawX_boxActionPerformed

    private void drawY_boxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_drawY_boxActionPerformed
    {//GEN-HEADEREND:event_drawY_boxActionPerformed
        toggleCheckbox(drawY_box, drawY_box.isSelected());
    }//GEN-LAST:event_drawY_boxActionPerformed

    private void snapshotsCheckboxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_snapshotsCheckboxActionPerformed
    {//GEN-HEADEREND:event_snapshotsCheckboxActionPerformed
        toggleCheckbox(snapshotsCheckbox, snapshotsCheckbox.isSelected());
    }//GEN-LAST:event_snapshotsCheckboxActionPerformed

    private void strokeSliderMouseReleased(java.awt.event.MouseEvent evt)//GEN-FIRST:event_strokeSliderMouseReleased
    {//GEN-HEADEREND:event_strokeSliderMouseReleased
        float radius = ((float) strokeSlider.getValue() / 10) ;
        strokeLabel.setText("Line radius : " + radius);

        if(!isChangeApplicable())
            return;

        boolean applyToSelectedOnly = desktopPane.getSelectedFrame() != null && !applyToAllChartsBox.isSelected();

        if(applyToSelectedOnly)
        {
            ChartWindow chartWindow = (ChartWindow) desktopPane.getSelectedFrame();
            chartWindow.chartMaker.setStroke(radius);
            chartWindow.sliderValue = strokeSlider.getValue();
            saveLayout("CurrentLayout", chartWindow.type, false,false);
        }
        else if(applyToAllChartsBox.isSelected())
        {
            for(JInternalFrame frame : desktopPane.getAllFrames())
            {
                ChartWindow chartWindow = (ChartWindow) frame;
                chartWindow.chartMaker.setStroke(radius);
                chartWindow.sliderValue = strokeSlider.getValue();
            }
            saveLayout("CurrentLayout", null, true,false);
        }
    }//GEN-LAST:event_strokeSliderMouseReleased

    private void strokeSliderStateChanged(javax.swing.event.ChangeEvent evt)//GEN-FIRST:event_strokeSliderStateChanged
    {//GEN-HEADEREND:event_strokeSliderStateChanged
        float radius = ((float) strokeSlider.getValue() / 10) ;
        strokeLabel.setText("Line radius : " + radius);
    }//GEN-LAST:event_strokeSliderStateChanged

    private void applyToAllChartsBoxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_applyToAllChartsBoxActionPerformed
    {//GEN-HEADEREND:event_applyToAllChartsBoxActionPerformed
        isChangeApplicable();
    }//GEN-LAST:event_applyToAllChartsBoxActionPerformed

    private void toggleLayoutButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_toggleLayoutButtonActionPerformed
    {//GEN-HEADEREND:event_toggleLayoutButtonActionPerformed
        layoutMenuScrollpane.setVisible(toggleLayoutButton.isSelected());
        toggleLayoutButton.setText(toggleLayoutButton.isSelected() ? "Hide layout menu" : "Show layout menu");
    }//GEN-LAST:event_toggleLayoutButtonActionPerformed

    private void toggleStyleButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_toggleStyleButtonActionPerformed
    {//GEN-HEADEREND:event_toggleStyleButtonActionPerformed
        styleMenuScrollpane.setVisible(toggleStyleButton.isSelected());
        toggleStyleButton.setText(toggleStyleButton.isSelected() ? "Hide style menu" : "Show style menu");
    }//GEN-LAST:event_toggleStyleButtonActionPerformed

    private void ravenTradesTabComponentHidden(java.awt.event.ComponentEvent evt)//GEN-FIRST:event_ravenTradesTabComponentHidden
    {//GEN-HEADEREND:event_ravenTradesTabComponentHidden
        if(ravenTradesTab.getTopComponent() instanceof ChartWindow == false)
            return;

        ((ChartWindow)ravenTradesTab.getTopComponent()).chartMaker.chartDialog.setVisible(false);      
    }//GEN-LAST:event_ravenTradesTabComponentHidden

    private void ravenTradesTabComponentShown(java.awt.event.ComponentEvent evt)//GEN-FIRST:event_ravenTradesTabComponentShown
    {//GEN-HEADEREND:event_ravenTradesTabComponentShown
        try{((ChartWindow) ravenTradesTab.getTopComponent()).setSelected(true);}
        catch (PropertyVetoException e){}
        chartWindowFocused = false;
    }//GEN-LAST:event_ravenTradesTabComponentShown

    private void digibyteTradesTabComponentHidden(java.awt.event.ComponentEvent evt)//GEN-FIRST:event_digibyteTradesTabComponentHidden
    {//GEN-HEADEREND:event_digibyteTradesTabComponentHidden
        if(digibyteTradesTab.getTopComponent() instanceof ChartWindow == false)
            return;

        ((ChartWindow)digibyteTradesTab.getTopComponent()).chartMaker.chartDialog.setVisible(false);
    }//GEN-LAST:event_digibyteTradesTabComponentHidden

    private void digibyteTradesTabComponentShown(java.awt.event.ComponentEvent evt)//GEN-FIRST:event_digibyteTradesTabComponentShown
    {//GEN-HEADEREND:event_digibyteTradesTabComponentShown
        try{((ChartWindow) digibyteTradesTab.getTopComponent()).setSelected(true);}
        catch (PropertyVetoException e){}
        chartWindowFocused = false;
    }//GEN-LAST:event_digibyteTradesTabComponentShown


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel allTxMenuPanel;
    private javax.swing.JLabel applyLinkLabel;
    private javax.swing.JCheckBox applyStyleToAllBox;
    private javax.swing.JButton applyToAllButton;
    private javax.swing.JCheckBox applyToAllChartsBox;
    private javax.swing.JLabel arrangeLabel;
    private javax.swing.JLabel arrangeLinkLabel;
    private javax.swing.JLabel arrangementsLabel;
    private javax.swing.JList<String> arrangementsList;
    private javax.swing.JScrollPane arrangementsListScrollpane;
    private javax.swing.JScrollPane arrangementsListScrollpane1;
    protected javax.swing.JCheckBox autoUpdateCheckbox;
    private javax.swing.JLabel averagingLabel;
    protected javax.swing.JSlider averagingSlider;
    private javax.swing.JPanel btcChartPlaceholder;
    private javax.swing.JScrollPane btcTradesScrollpane;
    private javax.swing.JSplitPane btcTradesTab;
    protected javax.swing.JTable btcTradesTable;
    protected javax.swing.JLabel chartColorLabel;
    private javax.swing.JLabel chartsLabel;
    protected javax.swing.JPanel chartsList;
    private javax.swing.JScrollPane chartsListScrollPane;
    private javax.swing.JPanel chartsMenu;
    private javax.swing.JPanel chartsTab;
    protected javax.swing.JLabel componentFontLabel;
    protected javax.swing.JLabel crosshairsColorLabel;
    private javax.swing.JButton deleteArrangeButton;
    private javax.swing.JButton deleteLayoutButton;
    private javax.swing.JButton deleteStyleButton;
    protected javax.swing.JDesktopPane desktopPane;
    protected javax.swing.JLabel dialogColorLabel;
    protected javax.swing.JLabel dialogTxtColorLabel;
    private javax.swing.JPanel digibyteChartPlaceholder;
    private javax.swing.JScrollPane digibyteTradesScrollpane;
    private javax.swing.JSplitPane digibyteTradesTab;
    protected javax.swing.JTable digibyteTradesTable;
    private javax.swing.JPanel divider;
    private javax.swing.JButton dividerButton;
    protected javax.swing.JLabel dividerColorLabel;
    private javax.swing.JPanel dogeChartPlaceholder;
    private javax.swing.JScrollPane dogeTradesScrollpane;
    private javax.swing.JSplitPane dogeTradesTab;
    protected javax.swing.JTable dogeTradesTable;
    protected javax.swing.JCheckBox drawX_box;
    protected javax.swing.JCheckBox drawY_box;
    private javax.swing.JLabel fontLabel;
    protected javax.swing.JSlider fontSlider;
    private javax.swing.JLabel fontStyleLabel;
    private javax.swing.JLabel fontStyleLabel1;
    protected javax.swing.JComboBox<String> fontsBox;
    protected javax.swing.JLabel gridColorLabel;
    protected javax.swing.JLabel guiBackgroundLabel;
    protected javax.swing.JLabel guiComponentsLabel;
    protected javax.swing.JLabel guiFontColorLabel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JSeparator jSeparator5;
    private javax.swing.JCheckBox keepStyleBox;
    private javax.swing.JPanel layoutMenu;
    private javax.swing.JScrollPane layoutMenuScrollpane;
    private javax.swing.JLabel layoutsLabel;
    private javax.swing.JLabel layoutsLinkLabel;
    private javax.swing.JList<String> layoutsList;
    protected javax.swing.JLabel lineColorLabel;
    private javax.swing.JButton loadArrangeButton;
    private javax.swing.JButton loadLayoutButton;
    private javax.swing.JButton loadStyleButton;
    private javax.swing.JCheckBox loadUiStyleBox;
    protected javax.swing.JLabel lookupStatusLabel;
    private javax.swing.JPanel ltcChartPlaceholder;
    private javax.swing.JScrollPane ltcTradesScrollpane;
    protected javax.swing.JTable ltcTradesTable;
    protected javax.swing.JCheckBox movingAverageBox;
    protected javax.swing.JLabel plotColorLabel;
    protected javax.swing.JProgressBar progressBar;
    private javax.swing.JCheckBox randomizeCheckbox;
    private javax.swing.JPanel ravenChartPlaceholder;
    private javax.swing.JScrollPane ravenTradesScrollpane;
    private javax.swing.JSplitPane ravenTradesTab;
    protected javax.swing.JTable ravenTradesTable;
    private javax.swing.JButton saveArrangeButton;
    private javax.swing.JButton saveLayoutButton;
    private javax.swing.JButton saveStyleButton;
    protected javax.swing.JCheckBox showDialogBox;
    protected javax.swing.JCheckBox showSubBox;
    protected javax.swing.JCheckBox showTextBox;
    protected javax.swing.JCheckBox showXcrosshairsBox;
    protected javax.swing.JCheckBox showYcrosshairBox;
    protected javax.swing.JCheckBox snapshotsCheckbox;
    protected javax.swing.JButton stopButton;
    private javax.swing.JLabel strokeLabel;
    protected javax.swing.JSlider strokeSlider;
    private javax.swing.JPanel styleMenu;
    private javax.swing.JScrollPane styleMenuScrollpane;
    private javax.swing.JLabel stylesLinkLabel;
    private javax.swing.JList<String> stylesList;
    private javax.swing.JScrollPane sylesListScrollpane;
    protected javax.swing.JTabbedPane tabbedPane;
    protected javax.swing.JLabel textColorLabel;
    protected javax.swing.JDialog tipDialog;
    private javax.swing.JLabel tipDialogLabel;
    private javax.swing.JPanel tipDialogPanel;
    private javax.swing.JToggleButton toggleLayoutButton;
    private javax.swing.JToggleButton toggleStyleButton;
    protected javax.swing.JDialog tradeInfoDialog;
    private javax.swing.JScrollPane tradeInfoScrollpane;
    private javax.swing.JTable tradeInfoTable;
    protected javax.swing.JCheckBox tradeVolumeBox;
    private javax.swing.JSplitPane tradesTab;
    protected javax.swing.JTable txTable;
    private javax.swing.JLabel uiLabel1;
    protected javax.swing.JButton updateButton;
    protected javax.swing.JLabel updateStatusLabel;
    protected javax.swing.JSplitPane updateTab;
    // End of variables declaration//GEN-END:variables
}
