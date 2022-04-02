package marketwatch;

import customized.CustomCombinedDomainXYPlot;
import customized.CrosshairOverlay;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Ellipse2D;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.Crosshair;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.time.RegularTimePeriod;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.data.time.Day;
import org.jfree.data.time.Second;
import org.jfree.data.xy.IntervalXYDataset;

public class ChartMaker extends ApplicationFrame implements ChartMouseListener
{
    private Crosshair xCrosshair;
    protected ChartPanel chartPanel;
    public ArrayList<TimeSeriesCollection> datasets;
    public javax.swing.JLabel chartDialogLabel;
    protected javax.swing.JDialog chartDialog;
    private final Point dialogSize = new Point(250,20);  
    public boolean showRangeCrosshairs = true;
    public boolean showDialog = true;
    public boolean interpolateEnabled = true;
    public boolean snapshotsEnabled = false;
    public int averagingPeriod = 5;
    public boolean movingAverageEnabled = true;
    public String chartTitle;
    private XYLineAndShapeRenderer lineRenderer;
    private XYBarRenderer barRenderer;
    private XYPlot barChartPlot;
    private StandardXYItemRenderer maRenderer;
    private TimeSeriesCollection maCollection;
    protected Crosshair maCrosshair;
    private CrosshairOverlay crosshairOverlay;
    private JFreeChart chart;
    private String myOS;
    private MarketPanel marketPanel;

    public ChartMaker(GUI gui)
    {
        super("");
        initChartDialog(gui);
        marketPanel = gui.marketPanel;
    }   

    @Override
    public void chartMouseClicked(ChartMouseEvent event){}  

    @Override
    public void chartMouseMoved(ChartMouseEvent event)
    {
        //We always show the vertical crosshair
        JFreeChart eventChart = event.getChart();
        XYPlot plot = (XYPlot) eventChart.getPlot();
        ValueAxis xAxis = plot.getDomainAxis();
        double x = xAxis.java2DToValue(event.getTrigger().getX(), chartPanel.getScreenDataArea(),
                org.jfree.chart.ui.RectangleEdge.BOTTOM);
        this.xCrosshair.setValue(x);         
        //yCrosshairs values will be set in CrosshairOverlay.paintOverlay

        if(showDialog)
        {            
            setDialogPosition(event.getTrigger().getLocationOnScreen(), event.getTrigger().getPoint(), 
                    event.getTrigger().getX(), event.getTrigger().getY());
        }        
    }    

    private void initChartDialog(GUI gui)
    {
        chartDialog = new javax.swing.JDialog(gui);
        chartDialog.setUndecorated(true);         

        chartDialogLabel = new javax.swing.JLabel();
        chartDialogLabel.setFont(new java.awt.Font("Dialog", 0, 11)); // NOI18N
        chartDialogLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        chartDialogLabel.setForeground(Color.LIGHT_GRAY);
        
        myOS = gui.dbManager.myOS;        
        setDialogColor(new Color (30, 30, 30));

        javax.swing.GroupLayout chartDialogLayout = new javax.swing.GroupLayout(chartDialog.getContentPane());
        chartDialog.getContentPane().setLayout(chartDialogLayout);
        chartDialogLayout.setHorizontalGroup(
                chartDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(chartDialogLabel, javax.swing.GroupLayout.Alignment.TRAILING, 
                                javax.swing.GroupLayout.DEFAULT_SIZE, dialogSize.x, Short.MAX_VALUE)
        );
        chartDialogLayout.setVerticalGroup(
                chartDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(chartDialogLabel, javax.swing.GroupLayout.Alignment.TRAILING, 
                                javax.swing.GroupLayout.DEFAULT_SIZE, dialogSize.y, Short.MAX_VALUE)
        );
        chartDialog.pack();
    }    
    
    //Cant use mouseEvent or chartMouseEvent, we need this function to handle both types 
    //(reacting to move = chartMouseEvt, reacting to click = mouseEvt)
    private void setDialogPosition(Point mousePos, Point pointOnChart, double x, double y)
    {            
           if (chartPanel.getScreenDataArea().contains(pointOnChart))
        {
            int dialogX;
            int dialogY;

            if (x + chartDialog.getWidth() - 20 > chartPanel.getScreenDataArea().getMaxX())
            {
                dialogX = mousePos.x - dialogSize.x - 15;//dialog to the left of mousepointer
            }
            else            
            {
                dialogX = mousePos.x + 15;//dialog to the right of mousepointer
            }

            if (y - chartDialog.getHeight() - 20 < chartPanel.getScreenDataArea().getMinY())
            {
                dialogY = mousePos.y + 15; //dialog below mousepointer
            }
            else
            {
                dialogY = mousePos.y - chartDialog.getHeight() - 15;//dialog above mousepointer
            }
            chartDialog.setLocation(dialogX, dialogY);

            chartDialog.setVisible(true);
        }
        else
           {
               //Custom color chooser is set to not visible when it loses focus, this is to enable the user to close the
               //color chooser by clicking anywhere outside it. The chart dialog steals focus from the cc and triggers 
               //a focus lost event, we don't want that. If we don't re-request focus for the cc when chart dialog is 
               //no longer painted due to pointer leaving chart, we will not be able to detect if user clicked outside of cc
               if(marketPanel.colorChooser.isVisible())
                   marketPanel.colorChooser.requestFocus();
               
               if(marketPanel.tipDialog.isVisible())
                   marketPanel.tipDialog.requestFocus();
               
               if(marketPanel.tradeInfoDialog.isVisible())
                   marketPanel.tradeInfoDialog.requestFocus();
               
               chartDialog.setVisible(false);
               
           }
    }    

    public void toggleSnapshots(boolean isEnabled)
    {
        lineRenderer.setSeriesShapesVisible(0, isEnabled);
    }

    protected void toggleGridY(boolean isEnabled)
    {
        var cplot = (CustomCombinedDomainXYPlot) chart.getPlot();
         XYPlot linePlot = (XYPlot) cplot.getSubplots().get(0);
         linePlot.setRangeGridlinesVisible(isEnabled);
         
         if(barChartPlot != null)
            barChartPlot.setRangeGridlinesVisible(isEnabled);     
    }

    protected void toggleGridX(boolean isEnabled)
    {
        var cplot = (CustomCombinedDomainXYPlot) chart.getPlot();
        XYPlot linePlot = (XYPlot) cplot.getSubplots().get(0);
        linePlot.setDomainGridlinesVisible(isEnabled);
        
        if (barChartPlot != null)
            barChartPlot.setDomainGridlinesVisible(isEnabled);
    }  

     protected void toggleText(boolean isEnabled,String labelTitle,Color color)
    {
        var cplot = (CustomCombinedDomainXYPlot) chart.getPlot();
        XYPlot linePlot = (XYPlot) cplot.getSubplots().get(0);

        if(isEnabled)
        {
            chart.setTitle(chartTitle);
            chart.getTitle().setPaint(color);
            linePlot.getRangeAxis().setLabel(labelTitle); 
            if(barChartPlot != null)
                barChartPlot.getRangeAxis().setLabel("Volume");             
        }
        else
        {
            chart.setTitle("");
            linePlot.getRangeAxis().setLabel("");
            if(barChartPlot != null)
                barChartPlot.getRangeAxis().setLabel("");
        }
    }
     
    protected void toggleVolume(boolean isEnabled)
    {
            
        if(barChartPlot == null)
            return;
        
        var cplot = (CustomCombinedDomainXYPlot)chart.getPlot();        
        
        if(isEnabled && cplot.getSubplots().size() == 1)
            cplot.add(barChartPlot);
        
        if(!isEnabled && cplot.getSubplots().size() == 2)
            cplot.remove(barChartPlot);
        
    }

    protected void toggleSubtitle(boolean isEnabled,Color color)
    {
        if(isEnabled && chart.getSubtitleCount() == 1)
        {
            chart.addSubtitle(new TextTitle(Main.BUNDLE.getString("chartSubtitle")));
            //if subtitle color was changed while subtitle was disabled
            TextTitle subtitle = (TextTitle) chart.getSubtitle(1);//0 is legendTitle
            subtitle.setPaint(color);  
        }  
        else
        {
            if(chart.getSubtitleCount() == 2)
                chart.removeSubtitle(chart.getSubtitle(1));
        }
    }

    protected void toggleYCrosshair(boolean isEnabled)
    {
        xCrosshair.setVisible(isEnabled);
    }

    protected void toggleXCrosshairs(boolean isEnabled)
    {
        showRangeCrosshairs = isEnabled;

        for(Object c : crosshairOverlay.getRangeCrosshairs())
        {
            Crosshair crosshair = (Crosshair) c;
            crosshair.setVisible(isEnabled);
        }

        if(isEnabled && movingAverageEnabled)
            maCrosshair.setVisible(true);
        else
            maCrosshair.setVisible(false);
    }

    protected void toggleMovingAverage(boolean isEnabled)
    {
        maRenderer.setSeriesVisible(0, isEnabled);
    }

    protected void setMovingAveragePeriod(int period)
    {        
        datasets.remove(maCollection);        
        averagingPeriod = period;   

        var cplot = (CustomCombinedDomainXYPlot) chart.getPlot();
        XYPlot plot = (XYPlot) cplot.getSubplots().get(0); 
        maCollection = (TimeSeriesCollection) CreateAverageDataset(plot.getDataset());        
        datasets.add(maCollection);     
        plot.setDataset(1, maCollection);
    }

    protected void setTradesDataset(ResultSet resultSet, int rangePeriod,boolean filterPeaks)
    {
        var cplot = (CustomCombinedDomainXYPlot) chart.getPlot();
        XYPlot plot = (XYPlot) cplot.getSubplots().get(0); 
        XYDataset oldDataset = plot.getDataset();
        datasets.remove((TimeSeriesCollection)oldDataset);             
        XYDataset newDataset = createLineChartDataset(resultSet);
        plot.setDataset(newDataset);        
        //re-creates moving average dataset
        setMovingAveragePeriod(averagingPeriod);        
        setRange(rangePeriod, filterPeaks);
    }
    
    protected void setVolumeChartRange()
    {
        var cplot = (CustomCombinedDomainXYPlot) chart.getPlot();
        if(cplot.getSubplots().size() < 2)
            return;
        
        XYPlot plot = (XYPlot) cplot.getSubplots().get(1); 
        ValueAxis domain = plot.getDomainAxis();
        XYDataset dataset = plot.getDataset();
        
       double begin =  domain.getLowerBound();
        double end = domain.getUpperBound();

        ValueAxis range = plot.getRangeAxis();

        double highest = Double.MIN_VALUE;

        for(int i = dataset.getItemCount(0) - 1; i >= 0; i--)
        {
            double currentX = ((long)dataset.getX(0, i).doubleValue());
            if(currentX > end)
                continue;

            if(currentX < begin)
                break;

            double currentY = (double)dataset.getY(0, i);          

            if(currentY > highest)
                highest = currentY; 
        }

        //add 5% insets to upper margins of the y-axis
        double insets = highest * 0.05; 
        //ensure positive length for range     
        range.setRange(0, highest + insets);    
    }

    protected void setRange(int days, boolean filterPeaks)
    {
        var cplot = (CustomCombinedDomainXYPlot) chart.getPlot();
        XYPlot plot = (XYPlot) cplot.getSubplots().get(0); 
        XYDataset dataset = plot.getDataset();

        ValueAxis domain = plot.getDomainAxis();        
        double firstEntry = dataset.getXValue(0, 0);
        double lastEntry = dataset.getXValue(0, dataset.getItemCount(0) -1);
        double begin = days == 0 ? firstEntry - 86400000L : lastEntry - (days * 86400000L);
        domain.setRange( begin,  lastEntry + 86400000L);        

        ValueAxis range = plot.getRangeAxis();

        double lowest = Double.MAX_VALUE;
        double highest = Double.MIN_VALUE;

        int lowestIndex = 0;

        for(int i = dataset.getItemCount(0) - 1; i >= 0; i--)
        {
            if(((long)dataset.getX(0, i).doubleValue()) < begin)
            {
                lowestIndex = i;
                break;
            }

            double current = (double)dataset.getY(0, i);

            if(current < lowest)
                lowest = current;            

            if(current > highest)
                highest = current; 
        }

        if(filterPeaks)
        {
            double[] filtered = getFilteredRange(dataset,lowestIndex,dataset.getItemCount(0) - 1);
            lowest = filtered[0];
            highest = filtered[1];
        }

        //add 5% insets to upper and lower margins of the y-axis
        double insets = (highest - lowest) * 0.05;            
        range.setRange(lowest - insets, highest + insets);    

        if(filterPeaks)
            range.zoomRange(-3, 3);
        
        setVolumeChartRange();
    }

    protected void setRangeAround(long timestamp, int days, boolean filterPeaks)
    {
        var cplot = (CustomCombinedDomainXYPlot) chart.getPlot();
        XYPlot plot = (XYPlot) cplot.getSubplots().get(0);
        XYDataset dataset = plot.getDataset();

        ValueAxis domain = plot.getDomainAxis();      

        domain.setRangeAboutValue(((double)timestamp), ((double)days * 86400000L));

//        plot.setDomainCrosshairValue((double)timestamp,true);

        double begin =  domain.getLowerBound();
        double end = domain.getUpperBound();

        ValueAxis range = plot.getRangeAxis();

        double lowest = Double.MAX_VALUE;
        double highest = Double.MIN_VALUE;

        //USED FOR FILTER IMPLEMENTATION BELOW
//        int lowestIndex = 0;
//        int highestIndex = dataset.getItemCount(0);

        for(int i = dataset.getItemCount(0) - 1; i >= 0; i--)
        {
            double currentX = ((long)dataset.getX(0, i).doubleValue());
            if(currentX > end)
            {
//                highestIndex = i;
                continue;
            }
//            if(currentX == end)
//                highestIndex = i;

            if(currentX < begin)
            {
//                lowestIndex = i;
                break;
            }

            double currentY = (double)dataset.getY(0, i);

            if(currentY < lowest)
                lowest = currentY;            

            if(currentY > highest)
                highest = currentY; 
        }

        //CURRENT FILTER IMPLEMENTATION DOES NOT WORK AS INTENDED ON SMALL PERIODS
//        if(filterPeaks)
//        {
//            double[] filtered = getFilteredRange(dataset,lowestIndex,highestIndex);
//            
//            //this may not be the case if the x-axis range was very small, but is a pre-condition
//            //for Range.setRange. Ignore filtered result if upper < lower
//            if(filtered[1] > filtered[0])
//            {
//                lowest = filtered[0];
//                highest = filtered[1];                
//            }
//        }

        //add 5% insets to upper and lower margins of the y-axis (Math.abs to ensure positive inset value)
        double insets = Math.abs(highest - lowest) * 0.05; 
        //ensure positive length for range
        highest = highest - lowest < 1 ? highest + 1 : highest;        
        range.setRange(lowest - insets, highest + insets);    

//        if(filterPeaks)
            range.zoomRange(-3, 3);
            
        setVolumeChartRange();
    }

    private double[] getFilteredRange(XYDataset dataset,int lowestIndex, int highestIndex)
    {
        ArrayList<Double> averages = new ArrayList<>();

        int averageBy = (int) ((double) (dataset.getItemCount(0) - lowestIndex) * 0.5);         

        for(int i = highestIndex; i > lowestIndex; i--)
        {            
            if(dataset.getY(0, i) == null)
                continue;

            if(i - averageBy >= 0)
            {                 
                    double sum = 0;
                    for(int y = 0; y < averageBy; y++)
                    {
                        sum += (double)dataset.getY(0, i - y);
                    }     
                    double average = sum / averageBy;
                    averages.add(average);                
            }
        }
        double lowest = Double.MAX_VALUE;
        double highest = Double.MIN_VALUE;

        for(double current : averages)
        {
            if(current < lowest)
                lowest = current;
            if(current > highest)
                highest = current;
        }
        return new double[]{lowest,highest};        
    }

    //Cannot use setFont() as function name as it is a method of the super, but that doesn't work for this purpose
    protected void setNewFont(String fontName, int fontSize,Color color)
    {
        var cplot = (CustomCombinedDomainXYPlot) chart.getPlot();
        XYPlot linePlot = (XYPlot) cplot.getSubplots().get(0);
        
        Font titleFont = new Font(fontName,Font.BOLD,fontSize);        
        chart.setTitle(new TextTitle(chart.getTitle().getText(), titleFont));
        chart.getTitle().setPaint(color);//this somehow gets reset to black when changing font

        int smallerSize = (int) ((double) fontSize * 0.75);
        fontSize = smallerSize > 15 ? 15 : smallerSize;
        Font smallerFont = new Font(fontName,Font.BOLD,fontSize);  
        linePlot.getDomainAxis().setLabelFont(smallerFont);
        linePlot.getRangeAxis().setLabelFont(smallerFont);
        if(barChartPlot != null)
            barChartPlot.getRangeAxis().setLabelFont(smallerFont);       
        
        if(chart.getSubtitleCount() > 1)
        {
            TextTitle subtitle = (TextTitle) chart.getSubtitle(1);//0 is legendTitle
            subtitle.setFont(smallerFont);
        }
        chart.getLegend().setItemFont(smallerFont);

        fontSize = (int) ((double) fontSize * 0.75);
        Font smallestFont = new Font(fontName, Font.PLAIN, fontSize);
        linePlot.getRangeAxis().setTickLabelFont(smallestFont);
        linePlot.getDomainAxis().setTickLabelFont(smallestFont);        
        if(barChartPlot != null)
            barChartPlot.getRangeAxis().setTickLabelFont(smallestFont);

        //setting any font for the crosshair labels and chart dialog is too unpredictable and can
        //cause the text to get too large and out of bounds
//        Font dialogFont = new Font(fontName, Font.PLAIN, 14);        
//        chartDialogLabel.setFont(dialogFont);
//        xCrosshair.setLabelFont(dialogFont);
//        maCrosshair.setLabelFont(dialogFont);
//        Crosshair yCrosshair = (Crosshair) crosshairOverlay.getRangeCrosshairs().get(0);
//        yCrosshair.setLabelFont(dialogFont);
    }

    protected void setLineColor(Color color)
    {
        lineRenderer.setSeriesPaint(0, color);
        if(barRenderer != null)
            barRenderer.setSeriesPaint(0, color);
    }

    protected void setTextColor(Color color)
    {
        chart.getTitle().setPaint(color);
        chart.getLegend().setItemPaint(color);
        if (chart.getSubtitleCount() > 1)
        {
            TextTitle subtitle = (TextTitle) chart.getSubtitle(1);//0 is legendTitle
            subtitle.setPaint(color);
        }
        
        var cplot = (CustomCombinedDomainXYPlot) chart.getPlot();
        XYPlot linePlot = (XYPlot) cplot.getSubplots().get(0);
        linePlot.getRangeAxis().setLabelPaint(color);
        linePlot.getRangeAxis().setTickLabelPaint(color);
        linePlot.getDomainAxis().setLabelPaint(color);
        linePlot.getDomainAxis().setTickLabelPaint(color); 
        
        if(barChartPlot != null)
        {
            barChartPlot.getRangeAxis().setLabelPaint(color);
            barChartPlot.getRangeAxis().setTickLabelPaint(color);
        }    
    }

    protected void setGridColor(Color color)
    {
        var cplot = (CustomCombinedDomainXYPlot) chart.getPlot();
        XYPlot linePlot = (XYPlot) cplot.getSubplots().get(0);
        linePlot.setDomainGridlinePaint(color);
        linePlot.setRangeGridlinePaint(color);
        
        if(barChartPlot != null)
        {
            barChartPlot.setDomainGridlinePaint(color);
            barChartPlot.setRangeGridlinePaint(color);  
        }            
    }

    protected void setChartColor(Color color)
    {
        chart.setBackgroundPaint(color);
        chart.getLegend().setBackgroundPaint(color);
    }

    protected void setPlotColor(Color color)
    {
        var cplot = (CustomCombinedDomainXYPlot) chart.getPlot();
        XYPlot linePlot = (XYPlot) cplot.getSubplots().get(0);
        linePlot.setBackgroundPaint(color);
        
        if(barChartPlot != null)
            barChartPlot.setBackgroundPaint(color);
    }

    protected void setDomainCrosshair()
    {
        var cplot = (CustomCombinedDomainXYPlot) chart.getPlot();
        XYPlot linePlot = (XYPlot) cplot.getSubplots().get(0);
        if(!linePlot.isDomainCrosshairVisible())
        {
            linePlot.setDomainCrosshairPaint(Color.RED);
            linePlot.setDomainCrosshairStroke(new BasicStroke(1.25f));
            linePlot.setDomainCrosshairVisible(true);
            linePlot.setDomainCrosshairValue(linePlot.getDataset().getXValue(0, 0));            
        }
    }

    protected void setCrosshairsColor(Color color)
    {
        xCrosshair.setPaint(color);
        Crosshair yCrosshair = (Crosshair) crosshairOverlay.getRangeCrosshairs().get(0);
        yCrosshair.setPaint(color); 
    }

    protected void setDialogColor(Color color)
    {         
        SwingUtilities.invokeLater(()->
        {
            Color endColor;
             //only enable chartDialog transparancy for non linux systems
            //Linux rendering on transparent background is blurry to the point of unreadable
            if(myOS.contains("nix") || myOS.contains("nux"))
            {
                chartDialog.getRootPane ().setOpaque (true);
                endColor = color;
                chartDialog.getContentPane ().setBackground (endColor);
            }
            else
            {
                chartDialog.getRootPane ().setOpaque (false);
                chartDialog.getContentPane ().setBackground (new Color (0, 0, 0, 0));
                endColor = new Color (color.getRed(), color.getGreen(), color.getBlue(), 180);
            } 
            chartDialog.setBackground (endColor); 

            //if called from constructor
            if(xCrosshair == null)
                return;
            
            xCrosshair.setLabelBackgroundPaint(endColor);
            Crosshair yCrosshair = (Crosshair) crosshairOverlay.getRangeCrosshairs().get(0);
            yCrosshair.setLabelBackgroundPaint(endColor);   
            maCrosshair.setLabelBackgroundPaint(endColor);  
        });       
    }

     protected void setDialogTextColor(Color color)
    {  
        chartDialogLabel.setForeground(color);
        xCrosshair.setLabelPaint(color);
        Crosshair yCrosshair = (Crosshair) crosshairOverlay.getRangeCrosshairs().get(0);
        yCrosshair.setLabelPaint(color);   
        maCrosshair.setLabelPaint(color);
    }

    public void SetMovingAverage(boolean isEnabled)
    {
        var cplot = (CustomCombinedDomainXYPlot) chart.getPlot();
        XYPlot linePlot = (XYPlot) cplot.getSubplots().get(0);
        //if more than one rangeaxis exist, there's no moving average
        if(linePlot.getRangeAxisCount() != 1)
            return;

        if(isEnabled)
        {
            linePlot.setDataset(1,maCollection);
//            maCrosshair.setVisible(true);
        }
        else
        {
            linePlot.setDataset(1,null);
            maCrosshair.setVisible(false);            
        }
    }

    protected void setStroke(float radius)
    {
        lineRenderer.setSeriesStroke(0, new BasicStroke(radius));
        float a = -2f * (radius / 2.0f);
        float b = 4f * (radius / 2.0f);        
        lineRenderer.setSeriesShape(0, new Ellipse2D.Double(a,a,b,b));
        maRenderer.setSeriesStroke(0, new BasicStroke(radius * .75f));
    }

    private void CreateMovingAverage(XYDataset sourceDataset, XYPlot plot)
    {
        maCollection = (TimeSeriesCollection) CreateAverageDataset(sourceDataset);        
        datasets.add(maCollection);        

        plot.setDataset(1, maCollection);
        plot.mapDatasetToRangeAxis(1, 0); 

        maRenderer = new StandardXYItemRenderer();
        maRenderer.setSeriesStroke(0, new BasicStroke(1.5f));
        maRenderer.setSeriesPaint(0, Color.RED);
        plot.setRenderer(1, maRenderer);        
    }

    private XYDataset CreateAverageDataset(XYDataset sourceDataset)
    {
        TimeSeries series = new TimeSeries(Main.BUNDLE.getString("movingAverage"));
        RegularTimePeriod time;

        for(int i = sourceDataset.getItemCount(0) - 1; i >= 0; i--)
        {
            if(sourceDataset.getY(0, i) == null)
                continue;

            time = new Second(new Date((long)sourceDataset.getX(0, i)));

            if(i - averagingPeriod >= 0)
            {
                if(sourceDataset.getY(0, i) instanceof  Double)
                {                   
                    double sum = 0;
                    for(int y = 0; y < averagingPeriod; y++)
                    {
                        sum += (double)sourceDataset.getY(0, i - y);
                    }     
                    double aveage = sum / averagingPeriod;
                    series.addOrUpdate(time,aveage);
                }
                else if(sourceDataset.getY(0, i) instanceof  Byte)
                {                 
                    int sum = 0;
                    for(int y = 0; y < averagingPeriod; y++)
                    {
                        sum += (byte)sourceDataset.getY(0, i - y);
                    } 
                    int average = sum / averagingPeriod;
                    series.addOrUpdate(time,average);
                }
                else if(sourceDataset.getY(0, i) instanceof  Integer)
                {                 
                    int sum = 0;
                    for(int y = 0; y < averagingPeriod; y++)
                    {
                        sum += (int)sourceDataset.getY(0, i - y);
                    } 
                    int average = sum / averagingPeriod;
                    series.addOrUpdate(time,average);
                }
                else if(sourceDataset.getY(0, i) instanceof  Long)
                {                 
                    long sum = 0;
                    for(int y = 0; y < averagingPeriod; y++)
                    {
                        sum += (long)sourceDataset.getY(0, i - y);
                    } 
                    long average = sum / averagingPeriod;
                    series.addOrUpdate(time,average);
                }
            }
        }   

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(series);
        return dataset;
    }

    protected JPanel createCombinedChartPanel(String title,ResultSet resultSet)
    {                
        chartTitle = title;
        chart = createCombinedChart(title,resultSet);
        chartPanel = new ChartPanel(chart);

        chartPanel.addChartMouseListener(this);
        crosshairOverlay = new CrosshairOverlay(this);
        xCrosshair = new Crosshair(Double.NaN, Color.DARK_GRAY, new BasicStroke(.5f));
        xCrosshair.setLabelVisible(true);
        xCrosshair.setLabelGenerator((Crosshair crshr) ->
        {
            return Utilities.DateFormatShort(crshr.getValue());
        });
        crosshairOverlay.addDomainCrosshair(xCrosshair);    

        CustomCombinedDomainXYPlot cplot = (CustomCombinedDomainXYPlot)chart.getPlot();
        XYPlot plot = (XYPlot) cplot.getSubplots().get(0);
        

        Crosshair crosshair = new Crosshair(Double.NaN,Color.black, new BasicStroke(.5f));
        crosshair.setLabelVisible(true);     

        String label = plot.getRangeAxis().getLabel();

        crosshair.setLabelGenerator((Crosshair crshr) ->
        {
            return Utilities.GenerateLabelString(label, crshr.getValue());
        });
        crosshairOverlay.addRangeCrosshair(crosshair);

        //Range (horizontal) crosshairs are disabled by default 
        crosshair.setVisible(true);
        showRangeCrosshairs = true;
        
        //setup moving average crosshair
        maCrosshair = new Crosshair(Double.NaN, Color.RED, new BasicStroke(.5f));
        maCrosshair.setLabelVisible(true);
        maCrosshair.setLabelAnchor(RectangleAnchor.BOTTOM_RIGHT);
        
        final String labelKey = title.endsWith("(Total)") ? "integer average" : "moving average";

        maCrosshair.setLabelGenerator((Crosshair crshr) ->
        {
            return Utilities.GenerateLabelString(labelKey, crshr.getValue());
        });
        crosshairOverlay.addRangeCrosshair(maCrosshair);
        //Range (horizontal) crosshairs are disabled by default 
        maCrosshair.setVisible(true);

        chart.setBackgroundPaint(Color.WHITE);
        chartPanel.addOverlay(crosshairOverlay);
        chartPanel.setMouseWheelEnabled(true);
        chartPanel.setZoomAroundAnchor(true);
        chartPanel.addMouseListener(new MouseListener()
        {
            @Override
            public void mousePressed(MouseEvent me)
            {
                if(showDialog)
                    chartDialog.setVisible(false);
            }
            @Override
            public void mouseReleased(MouseEvent me)
            {
                if (chartPanel.getScreenDataArea().contains(me.getPoint()))
                {
                    if(showDialog)
                    {
                        setDialogPosition(me.getLocationOnScreen(), me.getPoint(), me.getX(), me.getY());
                        chartDialog.setVisible(true);
                    }
                }
                setVolumeChartRange();
            }
            @Override public void mouseEntered(MouseEvent me){}
            @Override public void mouseExited(MouseEvent me){}
            @Override public void mouseClicked(MouseEvent me){}
        });
        chartDialog.setSize(dialogSize.x, dialogSize.y * (datasets.size() + 1) + 10);

        //must be done after chart is initialized,         
        SetMovingAverage(movingAverageEnabled);
        
        return chartPanel;
    }        

    private JFreeChart createCombinedChart(String title,ResultSet resultSet)
    {
        datasets = new ArrayList<>();    
        XYDataset dataset1;
        
        boolean isIntegerAxis = false;
        
        if(title.endsWith("(Total)"))
        {
            dataset1 = createTotalTradesDataset(resultSet);
            isIntegerAxis = true;
        }
        else if(title.endsWith("traded"))
        {
            dataset1 = createAmountDataset(resultSet);
        }
        else
            dataset1 = createLineChartDataset(resultSet);     

        title = dataset1 == null ? Main.BUNDLE.getString("notEnoughData") : title;
        
        lineRenderer = new XYLineAndShapeRenderer(true, false); 
        lineRenderer.setSeriesStroke(0, new BasicStroke(2.5f));
        lineRenderer.setSeriesPaint(0, Color.black);        
        lineRenderer.setSeriesShape(0, new Ellipse2D.Double(-2.0, -2.0, 4.0, 4.0));
        lineRenderer.setSeriesShapesVisible(0, snapshotsEnabled);

        DateAxis domainAxis = new DateAxis("");
        domainAxis.setLowerMargin(0.0);
        domainAxis.setUpperMargin(0.02);
        domainAxis.setTickLabelPaint(Color.black);

        NumberAxis rangeAxis = new NumberAxis(title);
        
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setTickLabelPaint(Color.black);
        if(isIntegerAxis)
            rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        XYPlot lineChartPlot = new XYPlot(dataset1, null, rangeAxis, lineRenderer);
        CreateMovingAverage(dataset1, lineChartPlot);
        lineChartPlot.setBackgroundPaint(Color.white);
        lineChartPlot.setOrientation(PlotOrientation.VERTICAL);
        lineChartPlot.setDomainPannable(true);
        lineChartPlot.setRangePannable(true);         

        CustomCombinedDomainXYPlot cplot = new CustomCombinedDomainXYPlot(domainAxis);
        cplot.add(lineChartPlot, 3);   
        cplot.setDomainPannable(true);
        cplot.setRangePannable(true);

        try
        {
            //not all resultsets will have a volume/amount column (USD tables)
            ResultSetMetaData metaData = resultSet.getMetaData();
            if(metaData.getColumnCount() == 3)
            {
                IntervalXYDataset volumeDataset = createVolumeDataset(resultSet);
                barRenderer = new XYBarRenderer();
                barRenderer.setSeriesPaint(0, Color.blue);
                barRenderer.setDrawBarOutline(false);
                barRenderer.setShadowVisible(false);
                StandardXYToolTipGenerator generator = new StandardXYToolTipGenerator(
                        "{1}  ||  {2} QORT", new SimpleDateFormat("MMM dd yyyy 'at' HH:mm"), NumberFormat.getIntegerInstance());
                barRenderer.setDefaultToolTipGenerator(generator);

                barChartPlot = new XYPlot(volumeDataset, null, new NumberAxis("Volume"), barRenderer);
                barChartPlot.setBackgroundPaint(Color.white);
                barChartPlot.setDomainPannable(true);
                barChartPlot.setRangePannable(true); 

                cplot.add(barChartPlot, 1);
                cplot.setGap(6.0);
            }
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }           

        // return a new chart containing the overlaid plot...
        chart = new JFreeChart(title,JFreeChart.DEFAULT_TITLE_FONT, cplot, true);
        chart.setBackgroundPaint(Color.white);
        chart.addSubtitle(new TextTitle(Main.BUNDLE.getString("chartSubtitle")));

        return chart;
    } 

    private XYDataset createLineChartDataset(ResultSet resultSet)
    {     
        try
        {       
            resultSet.beforeFirst();
            String column = resultSet.getMetaData().getColumnName(2);
            TimeSeries series = new TimeSeries(column);
            RegularTimePeriod time;
            Number value;  
            while(resultSet.next())
            {   
                //using second to make sure addOrUpdate will insert if time interval is smaller than 1 minute
                time = new Second(new Date(resultSet.getLong("timestamp")));
                value = (Number) resultSet.getObject(column);       
                if(value == null)
                    continue;

                series.addOrUpdate(time, value);
            }
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);
            datasets.add(dataset);      

            return dataset;            
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
        return null;        
    } 
    
    private XYDataset createTotalTradesDataset(ResultSet resultSet)
    {
        try
        {       
            resultSet.beforeFirst();
            resultSet.next();
            TimeSeries series = new TimeSeries("trades");
            RegularTimePeriod lastDay = new Day(new Date(resultSet.getLong("timestamp")));
            RegularTimePeriod currentDay = lastDay;
            series.addOrUpdate(lastDay, 0);
            int totalTrades = 1;
            
            while(resultSet.next())
            {   
                totalTrades++;
                currentDay = new Day(new Date(resultSet.getLong("timestamp")));
                
                if(lastDay.equals(currentDay))
                    continue;
                
                series.addOrUpdate(currentDay, totalTrades);
                lastDay = currentDay;
            }
            
            //last week could have been skipped in while loop
            series.addOrUpdate(currentDay, totalTrades);
            
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);
            datasets.add(dataset);      
            
            return dataset;            
        }
        catch (SQLException e)
        {
            mintmeister.BackgroundService.AppendLog(e);
        }
        return null;  
    }
    
    private XYDataset createAmountDataset(ResultSet resultSet)
    {
          try
        {       
            resultSet.beforeFirst();
            String column = resultSet.getMetaData().getColumnName(2);
            TimeSeries series = new TimeSeries(column);
            RegularTimePeriod time;
            Number value;  
            double totalAmount = 0;
            while(resultSet.next())
            {   
                //using second to make sure addOrUpdate will insert if time interval is smaller than 1 minute
                time = new Second(new Date(resultSet.getLong("timestamp")));
                value = (Number) resultSet.getObject(column);      
                if(value == null)
                    continue;

                totalAmount += (double)value;
                series.addOrUpdate(time, totalAmount);
            }
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);
            datasets.add(dataset);      

            return dataset;            
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
        return null;        
    }
    
    private IntervalXYDataset createVolumeDataset(ResultSet resultSet)
    {
        try
        {
            resultSet.beforeFirst();
            TimeSeries series = new TimeSeries("Volume");
            RegularTimePeriod time;
            Number value;  
            while(resultSet.next())
            {   
                //using second to make sure addOrUpdate will insert if time interval is smaller than 1 minute
                time = new Second(new Date(resultSet.getLong("timestamp")));
                value = (Number) resultSet.getObject("amount");       
                if(value == null)
                    continue;

                series.addOrUpdate(time, value);
            }
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(series);  

            return dataset;            

        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }

        return null;

    }

}