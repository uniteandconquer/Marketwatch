package marketwatch;

import enums.Folders;
import enums.Chart;
import customized.CustomComponentResizedListener;
import customized.CustomComponentMovedListener;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.swing.JPanel;

public class ChartWindow extends javax.swing.JInternalFrame
{
    private MarketPanel marketPanel;
    protected String font = "Dialog";
    protected byte fontSize = 15;    
    protected ChartMaker chartMaker;
    protected Chart type;
    protected Color chartColor = Color.white;
    protected Color plotColor = Color.white;
    protected Color crosshairsColor = Color.black;
    protected Color dialogColor = Color.black;
    protected Color dialogTextColor = Color.white;
    protected Color lineColor = Color.black;
    protected Color textColor = Color.black;
    protected Color gridColor = Color.white;
    protected int sliderValue = 15;
    protected boolean showSnapshots;
    protected boolean showText = true;    
    protected boolean showSubtitle = true;
    protected boolean showDialog = true;
    protected boolean showXcrosshairs = false;
    protected boolean showYcrosshair = true;
    protected boolean drawX;
    protected boolean drawY;
    protected boolean showMovingAverage = true;
    protected boolean showVolume;
    protected int averageBy = 10;
    protected boolean wasRestored;
    protected int arrIndex;
    
    public ChartWindow()
    {
        initComponents();
        initialise();
    }
    
    private void initialise()
    {    
        //Since the windows are resized relative to the desktopPane we only want the current arrangement to be saved if 
        //a chart window was moved or resized directly by the user with the mouse. The move and resize events will also get triggered 
        //when marketpanel resizeChartWindows() gets called, we want to ignore those events. 
        //Saving the arrangement on every event for every chartwindow is unnessecary and will also lead to slippage in
        //the proportianal resizing causing gaps and insets.        
        
        //this listener will only trigger a resize event after x amount of ms
        addComponentListener(new CustomComponentResizedListener(50)
        {
            @Override
            public void resizeTimedOut()
            {          
                //this flag gets set to true when a ChartWindow frame activated event is triggered and is
                //set to false when marketPanel resizeChartWindows is called, which is when the desktopPane's
                //resizeTimedOut() is triggered on its custom resize listener
                if(!marketPanel.chartWindowFocused)
                    return;
                
                //only save arrangement if frame was resized by dragging edges (or de-maximized)
                if(isSelected && !isMaximum)
                    marketPanel.saveCurrentArrangement();
            }
            
            @Override
            public void startedResizing(){}
        });  
        addComponentListener(new CustomComponentMovedListener(50)
        {
            @Override
            public void moveTimedOut()
            {
                if(!marketPanel.chartWindowFocused)
                    return;
                
                //only save arrangement if frame was moved directly by user
                if(isSelected && !isMaximum)
                    marketPanel.saveCurrentArrangement();
            }
            
            @Override
            public void startedMoving(){}
        });
    }   
    
    protected void setStyle(String font,byte fontSize,Color chartColor,Color plotColor,Color crosshairsColor, Color dialogColor,Color dialogTextColor,
            Color lineColor,Color textColor,Color gridColor,int sliderValue, boolean showSnapshots,boolean showSubtitle,boolean showText,
            boolean showDialog,boolean showXcrosshairs,boolean showYcrosshair,boolean drawX,boolean drawY,boolean showMa,boolean showVolume,int averageBy)
    {
        this.font = font;
        this.fontSize = fontSize;
        setNewFont(font,fontSize,textColor);        
        this.chartColor = chartColor;
        setChartColor(chartColor);
        this.plotColor = plotColor;
        setPlotColor(plotColor);        
        this.crosshairsColor = crosshairsColor;
        chartMaker.setCrosshairsColor(crosshairsColor);
        this.dialogColor = dialogColor;
        chartMaker.setDialogColor(dialogColor);
        this.dialogTextColor = dialogTextColor;
        chartMaker.setDialogTextColor(dialogTextColor);
        this.lineColor = lineColor;
        setLineColor(lineColor);
        this.textColor = textColor;
        setTextColor(textColor);
        this.gridColor = gridColor;
        chartMaker.setGridColor(gridColor);
        this.showSnapshots = showSnapshots;
        chartMaker.toggleSnapshots(showSnapshots);
        this.showSubtitle = showSubtitle;
        chartMaker.toggleSubtitle(showSubtitle, textColor);        
        this.showText = showText;
        chartMaker.toggleText(showText, title, textColor);
         this.showDialog = showDialog;
        chartMaker.showDialog = showDialog;
        this.showXcrosshairs = showXcrosshairs;
        chartMaker.toggleXCrosshairs(showXcrosshairs);
        this.showYcrosshair = showYcrosshair;
        chartMaker.toggleYCrosshair(showYcrosshair);
        this.drawX = drawX;
        chartMaker.toggleGridX(drawX);
        this.drawY = drawY;
        chartMaker.toggleGridY(drawY);
        this.sliderValue = sliderValue;
        chartMaker.setStroke((float)sliderValue / 10);
        this.showMovingAverage = showMa;
        chartMaker.movingAverageEnabled = showMovingAverage;
        this.averageBy = averageBy;   
        this.showVolume = showVolume;    
        chartMaker.toggleVolume(showVolume);
    
        peaksCheckbox.setVisible(false);//type.toString().endsWith("TRADE"));
        flipPairButton.setVisible(false);
        showVolumeBox.setVisible(false);
            
    }
    
    protected void setStyle()
    {
        setNewFont(font,fontSize,textColor);        
        setChartColor(chartColor);
        setPlotColor(plotColor);        
        chartMaker.setCrosshairsColor(crosshairsColor);
        chartMaker.setDialogColor(dialogColor);
        chartMaker.setDialogTextColor(dialogTextColor);
        setLineColor(lineColor);
        setTextColor(textColor);
        chartMaker.setGridColor(gridColor);
        chartMaker.toggleSnapshots(showSnapshots);
        chartMaker.toggleSubtitle(showSubtitle, textColor);     
        chartMaker.toggleText(showText, title, textColor);
        chartMaker.showDialog = showDialog;
        chartMaker.toggleXCrosshairs(showXcrosshairs);
        chartMaker.toggleYCrosshair(showYcrosshair);
        chartMaker.toggleGridX(drawX);
        chartMaker.toggleGridY(drawY);
        chartMaker.setStroke((float)sliderValue / 10);
        chartMaker.movingAverageEnabled = showMovingAverage; 
        chartMaker.toggleVolume(showVolume);            
    }
    
    //needs separate function in order to set toolbar style
    protected void setChartColor(Color color)
    {
        chartMaker.setChartColor(color);
        
        toolbar.setBackground(color);
        allRadio.setBackground(color);
        quarterRadio.setBackground(color);
        monthRadio.setBackground(color);
        weekRadio.setBackground(color);
        peaksCheckbox.setBackground(color);
        showVolumeBox.setBackground(color);
        repaint();
    }
    
    //needs separate function in order to set toolbar style
    protected void setTextColor(Color color)
    {
        chartMaker.setTextColor(color);
        
        allRadio.setForeground(color);
        quarterRadio.setForeground(color);
        monthRadio.setForeground(color);
        weekRadio.setForeground(color);
        peaksCheckbox.setForeground(color);
        showVolumeBox.setForeground(color);     
        repaint();
    }
    
    //needs separate function in order to set toolbar style
    protected void setPlotColor(Color color)
    {
        chartMaker.setPlotColor(color); 
        flipPairButton.setBackground(color);
        repaint();
    }
    
    //needs separate function in order to set toolbar style
    protected void setLineColor(Color color)
    {
        chartMaker.setLineColor(color);         
        flipPairButton.setForeground(color); 
        repaint();
    }
    
    //needs separate function in order to set toolbar style
    protected void setNewFont(String font,int fontSize,Color fontColor)
    {
        chartMaker.setNewFont(font, fontSize, fontColor);       
        
        //toolbar components font colors are set in this.setTextColor()
        flipPairButton.setFont(new Font(font, flipPairButton.getFont().getStyle(), flipPairButton.getFont().getSize()));
        showVolumeBox.setFont(new Font(font, showVolumeBox.getFont().getStyle(), showVolumeBox.getFont().getSize()));    
        peaksCheckbox.setFont(new Font(font, peaksCheckbox.getFont().getStyle(), peaksCheckbox.getFont().getSize()));    
        weekRadio.setFont(new Font(font, weekRadio.getFont().getStyle(), weekRadio.getFont().getSize()));
        monthRadio.setFont(new Font(font, monthRadio.getFont().getStyle(), monthRadio.getFont().getSize()));
        quarterRadio.setFont(new Font(font, quarterRadio.getFont().getStyle(), quarterRadio.getFont().getSize()));
        allRadio.setFont(new Font(font, allRadio.getFont().getStyle(), allRadio.getFont().getSize()));          
    }
    
    protected void createChart(Chart type,MarketPanel mp,Dimension size,Point location, GUI gui, DatabaseManager dbManager, Connection connection)
    {   
        this.type = type;
        marketPanel = mp;
        String dbTable = mp.typeToTable(type);
        String query = mp.typeToQueryMap.get(type.toString());
        setTitle(mp.typeToTitleMap.get(type.toString()));
        
        try
        {
            if (dbManager.TableExists(dbTable, connection))
            {
                Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                ResultSet resultSet = statement.executeQuery(query);
                
                chartMaker = new ChartMaker(gui);
                
                JPanel chartPanel =chartMaker.createCombinedChartPanel(title, resultSet);                  
                
                placeHolder.add(chartPanel);
                setPreferredSize(size);
                setLocation(location);
                setVisible(true);
                pack();      
            }            
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }     
    }   
    
    protected void updateChart(Connection connection)
    {
        String dbTable = marketPanel.typeToTable(type);
        String query = marketPanel.typeToQueryMap.get(type.toString());
        
        try
        {
            if (marketPanel.dbManager.TableExists(dbTable, connection))
            {
                Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                ResultSet resultSet = statement.executeQuery(query);               
                
                JPanel chartPanel = chartMaker.createCombinedChartPanel(title, resultSet);                  
                
                if(placeHolder.getComponentCount() > 0)
                {
                    for(int i = placeHolder.getComponentCount() - 1; i >= 0; i--)
                        placeHolder.remove(i);
                }
                placeHolder.add(chartPanel);   
                setStyle();
            }            
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }     
    }
    
    private void flipType()
    {
        if(null != type)
            switch (type)
        {
            case LTC_QORT_TRADE:
                type = Chart.QORT_LTC_TRADE;
                break;
            case QORT_LTC_TRADE:
                type = Chart.LTC_QORT_TRADE;
                break;
            case DOGE_QORT_TRADE:
                type = Chart.QORT_DOGE_TRADE;
                break;
            case QORT_DOGE_TRADE:
                type = Chart.DOGE_QORT_TRADE;
                break;
            case RAVEN_QORT_TRADE:
                type = Chart.QORT_RAVEN_TRADE;
                break;
            case QORT_RAVEN_TRADE:
                type = Chart.RAVEN_QORT_TRADE;
                break;
            case DIGIBYTE_QORT_TRADE:
                type = Chart.QORT_DIGIBYTE_TRADE;
                break;
            case QORT_DIGIBYTE_TRADE:
                type = Chart.DIGIBYTE_QORT_TRADE;
                break;
            case BTC_QORT_TRADE:
                type = Chart.QORT_BTC_TRADE;
                break;
            case QORT_BTC_TRADE:
                type = Chart.BTC_QORT_TRADE;
                break;
            default:
                break;
        }                  
    }
    
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        buttonGroup = new javax.swing.ButtonGroup();
        placeHolder = new javax.swing.JPanel();
        toolbar = new javax.swing.JPanel();
        flipPairButton = new javax.swing.JButton();
        showVolumeBox = new javax.swing.JCheckBox();
        peaksCheckbox = new javax.swing.JCheckBox();
        allRadio = new javax.swing.JRadioButton();
        quarterRadio = new javax.swing.JRadioButton();
        monthRadio = new javax.swing.JRadioButton();
        weekRadio = new javax.swing.JRadioButton();
        buttonGroup.add(allRadio);
        buttonGroup.add(quarterRadio);
        buttonGroup.add(monthRadio);
        buttonGroup.add(weekRadio);

        setClosable(true);
        setMaximizable(true);
        setResizable(true);
        addInternalFrameListener(new javax.swing.event.InternalFrameListener()
        {
            public void internalFrameActivated(javax.swing.event.InternalFrameEvent evt)
            {
                formInternalFrameActivated(evt);
            }
            public void internalFrameClosed(javax.swing.event.InternalFrameEvent evt)
            {
                formInternalFrameClosed(evt);
            }
            public void internalFrameClosing(javax.swing.event.InternalFrameEvent evt)
            {
            }
            public void internalFrameDeactivated(javax.swing.event.InternalFrameEvent evt)
            {
            }
            public void internalFrameDeiconified(javax.swing.event.InternalFrameEvent evt)
            {
            }
            public void internalFrameIconified(javax.swing.event.InternalFrameEvent evt)
            {
            }
            public void internalFrameOpened(javax.swing.event.InternalFrameEvent evt)
            {
            }
        });
        addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseEntered(java.awt.event.MouseEvent evt)
            {
                formMouseEntered(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        placeHolder.setLayout(new javax.swing.BoxLayout(placeHolder, javax.swing.BoxLayout.LINE_AXIS));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        getContentPane().add(placeHolder, gridBagConstraints);

        toolbar.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 2, 4));

        flipPairButton.setFont(new java.awt.Font("Segoe UI", 1, 9)); // NOI18N
        flipPairButton.setText("Flip pair");
        flipPairButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                flipPairButtonActionPerformed(evt);
            }
        });
        toolbar.add(flipPairButton);

        showVolumeBox.setFont(new java.awt.Font("Segoe UI", 0, 9)); // NOI18N
        showVolumeBox.setSelected(true);
        showVolumeBox.setText("Show volume");
        showVolumeBox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                showVolumeBoxActionPerformed(evt);
            }
        });
        toolbar.add(showVolumeBox);

        peaksCheckbox.setFont(new java.awt.Font("Segoe UI", 0, 9)); // NOI18N
        peaksCheckbox.setSelected(true);
        peaksCheckbox.setText("Filter peaks");
        peaksCheckbox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                peaksCheckboxActionPerformed(evt);
            }
        });
        toolbar.add(peaksCheckbox);

        allRadio.setFont(new java.awt.Font("Segoe UI", 0, 9)); // NOI18N
        allRadio.setSelected(true);
        allRadio.setText("All");
        allRadio.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                allRadioActionPerformed(evt);
            }
        });
        toolbar.add(allRadio);

        quarterRadio.setFont(new java.awt.Font("Segoe UI", 0, 9)); // NOI18N
        quarterRadio.setText("Quarter");
        quarterRadio.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                quarterRadioActionPerformed(evt);
            }
        });
        toolbar.add(quarterRadio);

        monthRadio.setFont(new java.awt.Font("Segoe UI", 0, 9)); // NOI18N
        monthRadio.setText("Month");
        monthRadio.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                monthRadioActionPerformed(evt);
            }
        });
        toolbar.add(monthRadio);

        weekRadio.setFont(new java.awt.Font("Segoe UI", 0, 9)); // NOI18N
        weekRadio.setText("Week");
        weekRadio.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                weekRadioActionPerformed(evt);
            }
        });
        toolbar.add(weekRadio);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        getContentPane().add(toolbar, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formMouseEntered(java.awt.event.MouseEvent evt)//GEN-FIRST:event_formMouseEntered
    {//GEN-HEADEREND:event_formMouseEntered
        marketPanel.disableChartDialogs(this);
    }//GEN-LAST:event_formMouseEntered

    private void formInternalFrameActivated(javax.swing.event.InternalFrameEvent evt)//GEN-FIRST:event_formInternalFrameActivated
    {//GEN-HEADEREND:event_formInternalFrameActivated
        marketPanel.chartWindowFocused = true;
        marketPanel.updateStyleMenu(this);
    }//GEN-LAST:event_formInternalFrameActivated

    private void formInternalFrameClosed(javax.swing.event.InternalFrameEvent evt)//GEN-FIRST:event_formInternalFrameClosed
    {//GEN-HEADEREND:event_formInternalFrameClosed
        marketPanel.addToChartsList(title);
        marketPanel.dropChartFromLayout(type.toString(),title, "CurrentLayout");
        marketPanel.updateArrangementsList();
        marketPanel.updateLayoutsList(true);
        marketPanel.updateStylesList(true);
        marketPanel.saveLayout("CurrentLayout", null, true, false);
        marketPanel.saveCurrentArrangement();
    }//GEN-LAST:event_formInternalFrameClosed

    private void weekRadioActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_weekRadioActionPerformed
    {//GEN-HEADEREND:event_weekRadioActionPerformed
        boolean filterPeaks = peaksCheckbox.isVisible() && peaksCheckbox.isSelected();
        chartMaker.setRange(7,filterPeaks);
    }//GEN-LAST:event_weekRadioActionPerformed

    private void monthRadioActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_monthRadioActionPerformed
    {//GEN-HEADEREND:event_monthRadioActionPerformed
        boolean filterPeaks = peaksCheckbox.isVisible() && peaksCheckbox.isSelected();
        chartMaker.setRange(30,filterPeaks);
    }//GEN-LAST:event_monthRadioActionPerformed

    private void allRadioActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_allRadioActionPerformed
    {//GEN-HEADEREND:event_allRadioActionPerformed
        boolean filterPeaks = peaksCheckbox.isVisible() && peaksCheckbox.isSelected();
        chartMaker.setRange(0,filterPeaks);
    }//GEN-LAST:event_allRadioActionPerformed

    private void peaksCheckboxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_peaksCheckboxActionPerformed
    {//GEN-HEADEREND:event_peaksCheckboxActionPerformed
        if(weekRadio.isSelected())
            chartMaker.setRange(7, peaksCheckbox.isSelected());
        else if(monthRadio.isSelected())
            chartMaker.setRange(30, peaksCheckbox.isSelected());
        else if(quarterRadio.isSelected())
            chartMaker.setRange(90, peaksCheckbox.isSelected());
        else if(allRadio.isSelected())
            chartMaker.setRange(0, peaksCheckbox.isSelected());
    }//GEN-LAST:event_peaksCheckboxActionPerformed

    private void quarterRadioActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_quarterRadioActionPerformed
    {//GEN-HEADEREND:event_quarterRadioActionPerformed
        boolean filterPeaks = peaksCheckbox.isVisible() && peaksCheckbox.isSelected();
        chartMaker.setRange(90,filterPeaks);
    }//GEN-LAST:event_quarterRadioActionPerformed

    private void flipPairButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_flipPairButtonActionPerformed
    {//GEN-HEADEREND:event_flipPairButtonActionPerformed
        flipType();
        
        String dbTable = marketPanel.typeToTable(type);
        String query = marketPanel.typeToQueryMap.get(type.toString());
        setTitle(marketPanel.typeToTitleMap.get(type.toString()));
        chartMaker.chartTitle = getTitle();//for crosshair label and chart dialog 
        
        try(Connection connection = ConnectionDB.getConnection("trades",Folders.DB.get()))
        {
            if (marketPanel.dbManager.TableExists(dbTable, connection))
            {
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(query);
                int rangePeriod = 0;
                if(quarterRadio.isSelected())
                    rangePeriod = 90;
                if(monthRadio.isSelected())
                    rangePeriod = 30;
                if(weekRadio.isSelected())
                    rangePeriod = 7;
                
                chartMaker.setTradesDataset(resultSet,rangePeriod,peaksCheckbox.isSelected());
            }
        }
        catch(Exception e)
        {
            BackgroundService.AppendLog(e);            
        }
    }//GEN-LAST:event_flipPairButtonActionPerformed

    private void showVolumeBoxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_showVolumeBoxActionPerformed
    {//GEN-HEADEREND:event_showVolumeBoxActionPerformed
        chartMaker.toggleVolume(showVolumeBox.isSelected());
    }//GEN-LAST:event_showVolumeBoxActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton allRadio;
    private javax.swing.ButtonGroup buttonGroup;
    protected javax.swing.JButton flipPairButton;
    private javax.swing.JRadioButton monthRadio;
    protected javax.swing.JCheckBox peaksCheckbox;
    private javax.swing.JPanel placeHolder;
    private javax.swing.JRadioButton quarterRadio;
    protected javax.swing.JCheckBox showVolumeBox;
    private javax.swing.JPanel toolbar;
    private javax.swing.JRadioButton weekRadio;
    // End of variables declaration//GEN-END:variables
}
