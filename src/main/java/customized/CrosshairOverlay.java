package customized;

import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import marketwatch.ChartMaker;
import marketwatch.Utilities;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.event.OverlayChangeEvent;
import org.jfree.chart.panel.AbstractOverlay;
import org.jfree.chart.panel.Overlay;
import org.jfree.chart.plot.Crosshair;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.text.TextUtils;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.chart.util.ObjectUtils;
import org.jfree.chart.util.Args;
import org.jfree.chart.util.PublicCloneable;
import org.jfree.data.general.DatasetUtils;
import org.jfree.data.xy.XYDataset;

public class CrosshairOverlay extends AbstractOverlay implements Overlay,
        PropertyChangeListener, PublicCloneable, Cloneable, Serializable
{
    private List xCrosshairs;
    protected List yCrosshairs;
    private final ChartMaker chartMaker;
    private String dialogString; 
    
    public CrosshairOverlay(ChartMaker chartMaker)
    {
        super();
        this.xCrosshairs = new java.util.ArrayList();
        this.yCrosshairs = new java.util.ArrayList();
        this.chartMaker = chartMaker;
    }

    /**
     * Adds a crosshair against the domain axis and sends an
     * {@link OverlayChangeEvent} to all registered listeners.
     * @param crosshair
     */
    public void addDomainCrosshair(Crosshair crosshair)
    {
        Args.nullNotPermitted(crosshair, "crosshair");
        this.xCrosshairs.add(crosshair);
        crosshair.addPropertyChangeListener(this);
        fireOverlayChanged();
    }

    /**
     * Removes a domain axis crosshair and sends an {@link OverlayChangeEvent}
     * to all registered listeners.
     *
     * @param crosshair the crosshair ({@code null} not permitted).
     *
     * @see #addDomainCrosshair(org.jfree.chart.plot.Crosshair)
     */
    public void removeDomainCrosshair(Crosshair crosshair)
    {
        Args.nullNotPermitted(crosshair, "crosshair");
        if (this.xCrosshairs.remove(crosshair))
        {
            crosshair.removePropertyChangeListener(this);
            fireOverlayChanged();
        }
    }

    /**
     * Clears all the domain crosshairs from the overlay and sends an
     * {@link OverlayChangeEvent} to all registered listeners.
     */
    public void clearDomainCrosshairs()
    {
        if (this.xCrosshairs.isEmpty())
        {
            return;  // nothing to do
        }
        List crosshairs = getDomainCrosshairs();
        for (int i = 0; i < crosshairs.size(); i++)
        {
            Crosshair c = (Crosshair) crosshairs.get(i);
            this.xCrosshairs.remove(c);
            c.removePropertyChangeListener(this);
        }
        fireOverlayChanged();
    }

    /**
     * Returns a new list containing the domain crosshairs for this overlay.
     *
     * @return A list of crosshairs.
     */
    public List getDomainCrosshairs()
    {
        return new ArrayList(this.xCrosshairs);
    }

    /**
     * Adds a crosshair against the range axis and sends an
     * {@link OverlayChangeEvent} to all registered listeners.
     *
     * @param crosshair the crosshair ({@code null} not permitted).
     */
    public void addRangeCrosshair(Crosshair crosshair)
    {
        Args.nullNotPermitted(crosshair, "crosshair");
        this.yCrosshairs.add(crosshair);
        crosshair.addPropertyChangeListener(this);
        fireOverlayChanged();
    }

    /**
     * Removes a range axis crosshair and sends an {@link OverlayChangeEvent} to
     * all registered listeners.
     *
     * @param crosshair the crosshair ({@code null} not permitted).
     *
     * @see #addRangeCrosshair(org.jfree.chart.plot.Crosshair)
     */
    public void removeRangeCrosshair(Crosshair crosshair)
    {
        Args.nullNotPermitted(crosshair, "crosshair");
        if (this.yCrosshairs.remove(crosshair))
        {
            crosshair.removePropertyChangeListener(this);
            fireOverlayChanged();
        }
    }

    /**
     * Clears all the range crosshairs from the overlay and sends an
     * {@link OverlayChangeEvent} to all registered listeners.
     */
    public void clearRangeCrosshairs()
    {
        if (this.yCrosshairs.isEmpty())
        {
            return;  // nothing to do
        }
        List crosshairs = getRangeCrosshairs();
        for (int i = 0; i < crosshairs.size(); i++)
        {
            Crosshair c = (Crosshair) crosshairs.get(i);
            this.yCrosshairs.remove(c);
            c.removePropertyChangeListener(this);
        }
        fireOverlayChanged();
    }

    /**
     * Returns a new list containing the range crosshairs for this overlay.
     *
     * @return A list of crosshairs.
     */
    public List getRangeCrosshairs()
    {
        return new ArrayList(this.yCrosshairs);
    }

    /**
     * Receives a property change event (typically a change in one of the
     * crosshairs).
     *
     * @param e the event.
     */
    @Override
    public void propertyChange(PropertyChangeEvent e)
    {
        fireOverlayChanged();
    }

    /**
     * Paints the crosshairs in the layer.
     *
     * @param g2 the graphics target.
     * @param chartPanel the chart panel.
     */
    @Override
    public void paintOverlay(Graphics2D g2, ChartPanel chartPanel)
    {  
        //Draw vertical crosshair
        Shape savedClip = g2.getClip();
        Rectangle2D dataArea = chartPanel.getScreenDataArea();
        g2.clip(dataArea);
        JFreeChart chart = chartPanel.getChart();
        
        var cplot = (CustomCombinedDomainXYPlot) chart.getPlot();
        XYPlot plot = (XYPlot) cplot.getSubplots().get(0);
        
        Crosshair crosshairX = (Crosshair) xCrosshairs.get(0);
        ValueAxis xAxis = plot.getDomainAxis();            
        RectangleEdge xAxisEdge = plot.getDomainAxisEdge();
        double x = crosshairX.getValue();
        double xx = xAxis.valueToJava2D(x, dataArea, xAxisEdge);
        if (plot.getOrientation() == PlotOrientation.VERTICAL)
        {
            if(crosshairX.isVisible())
                drawVerticalCrosshair(g2, dataArea, xx, crosshairX);
        }
        else
        {
            if(crosshairX.isVisible())
                drawHorizontalCrosshair(g2, dataArea, xx, crosshairX);
        }   

        dialogString = Utilities.DateFormatShort(x) + "<br/>";

        for(int i = 0; i < plot.getDatasetCount(); i++)
        {            
            boolean isMovingAverage;
            ValueAxis yAxis = plot.getRangeAxis(); //
            
            if(i == 1)
            {
                isMovingAverage = true;                
                if(!chartMaker.movingAverageEnabled)
                    continue;
            }
            else
                isMovingAverage = false;
            
            dataArea = chartPanel.getScreenDataArea();
            g2.clip(dataArea);
            XYDataset dataset = plot.getDataset(i);
            
            double y =   DatasetUtils.findYValue(dataset, 0, x);            

            if(chartMaker.showRangeCrosshairs)
            {
                //for the range crosshairs on a combined plot, we need to set the datarea to the rectangle of the
                //linechart plot, otherwise the crosshairs will use the data area of the entire combined plot and not work properly
                dataArea = chartPanel.getChartRenderingInfo().getPlotInfo().getSubplotInfo(0).getDataArea();                
                
                Crosshair ch = (Crosshair) yCrosshairs.get(i);
                
                if(ch.isVisible())
                {
                    RectangleEdge yAxisEdge = plot.getRangeAxisEdge(i);
                    ch.setValue(y);

                    double yy = yAxis.valueToJava2D(y, dataArea, yAxisEdge);
                    if (plot.getOrientation() == PlotOrientation.VERTICAL)
                    {
                        drawHorizontalCrosshair(g2, dataArea, yy, ch);
                    }
                    else
                    {
                        drawVerticalCrosshair(g2, dataArea, yy, ch);
                    }
                }
            }
            
            if(chartMaker.showDialog)
            {
                if(isMovingAverage)
                {
                    if(chartMaker.chartTitle.endsWith("(Total)"))
                        dialogString += String.format("%,d moving average<br/>", (int)y);
                    else
                        dialogString += String.format("%,.5f moving average<br/>", (double)y);                        
                }
                else
                {
                    //can't use rangeAxis label as label due to possible empty string when user disables text on chart
                    String label = chartMaker.chartTitle; 

                    switch(label)
                    {
                        case "Litecoin to QORT price":
                            dialogString += String.format("%,.5f Litecoin for 1 QORT<br/>",(double)y);
                            break;    
                        case "QORT to Litecoin price":
                            dialogString += String.format("%,.5f QORT for 1 Litecoin<br/>",(double)y);
                            break;    
                        case "Bitcoin to QORT price":     
                            dialogString += String.format("%,.5f Bitcoin for 1 QORT<br/>",(double)y);
                            break;    
                        case "QORT to Bitcoin price":
                            dialogString += String.format("%,.5f QORT for 1 Bitcoin<br/>",(double)y);
                            break;  
                        case "Dogecoin to QORT price":
                            dialogString += String.format("%,.5f Dogecoin for 1 QORT<br/>",(double)y);
                            break;  
                        case "QORT to Dogecoin price":  
                            dialogString += String.format("%,.5f QORT for 1 Dogecoin<br/>",(double)y);
                            break;  
                        case "US Dollar to QORT price":  
                            dialogString += String.format("%,.5f US Dollar for 1 QORT<br/>",(double)y);
                            break;  
                        case "QORT to US Dollar price":   
                            dialogString += String.format("%,.5f QORT for 1 US Dollar<br/>",(double)y);
                            break;  
                        case "US Dollar to Litecoin price":
                            dialogString += String.format("%,.5f US Dollar for 1 Litecoin<br/>",(double)y);
                            break; 
                        case "Litecoin to US Dollar price":
                            dialogString += String.format("%,.5f Litecoin for 1 US Dollar<br/>",(double)y);
                            break;  
                        case "US Dollar to Dogecoin price":
                            dialogString += String.format("%,.5f US Dollar for 1 Dogecoin<br/>",(double)y);
                            break; 
                        case "Dogecoin to US Dollar price":    
                            dialogString += String.format("%,.5f Dogecoin for 1 US Dollar<br/>",(double)y);
                            break;  
                        case "US Dollar to Bitcoin price":
                            dialogString += String.format("%,.5f US Dollar for 1 Bitcoin<br/>",(double)y);
                            break; 
                        case "Bitcoin to US Dollar price": 
                            dialogString += String.format("%,.5f Bitcoin for 1 US Dollar<br/>",(double)y);
                            break; 
                        case "LTC to QORT trades (in QORT)":
                            dialogString += String.format("%,.5f QORT per Litecoin paid<br/>",(double)y);
                            break; 
                        case "LTC to QORT trades (in LTC)":
                            dialogString += String.format("%,.5f Litecoin per QORT paid<br/>",(double)y);
                            break; 
                        case "BTC to QORT trades (in QORT)":  
                            dialogString += String.format("%,.5f QORT per Bitcoin paid<br/>",(double)y);
                            break; 
                        case "BTC to QORT trades (in BTC)":
                            dialogString += String.format("%,.5f Bitcoin per QORT paid<br/>",(double)y);
                            break; 
                        case "DOGE to QORT trades (in QORT)":
                            dialogString += String.format("%,.5f QORT per Dogecoin paid<br/>",(double)y);
                            break; 
                        case "DOGE to QORT trades (in DOGE)":
                            dialogString += String.format("%,.5f Dogecoin per QORT paid<br/>", (double) y);
                            break;
                        case "BTC to QORT trades (Total)":
                        case "LTC to QORT trades (Total)":
                        case "DOGE to QORT trades (Total)":
                        case "All trades (Total)":
                            dialogString += String.format("%,d trades<br/>",  (int) y);
                            break;
                        case "Total QORT to LTC traded":
                        case "Total QORT to DOGE traded":
                        case "Total QORT to BTC traded":
                        case "Total QORT traded":      
                            dialogString += String.format("%,.2f QORT traded<br/>", (double) y);
                            break;
                        case "Total LTC to QORT traded":
                            dialogString += String.format("%,.2f LTC traded<br/>", (double) y);
                            break;
                        case "Total BTC to QORT traded":
                            dialogString += String.format("%,.2f BTC traded<br/>", (double) y);
                            break;
                        case "Total DOGE to QORT traded":
                            dialogString += String.format("%,.2f DOGE traded<br/>", (double) y);
                            break;
                    }
                }                   
            }               
        }     
        
        chartMaker.chartDialogLabel.setText(Utilities.AllignCenterHTML(dialogString));
        g2.setClip(savedClip);       
    }

    /**
     * Draws a crosshair horizontally across the plot.
     *
     * @param g2 the graphics target.
     * @param dataArea the data area.
     * @param y the y-value in Java2D space.
     * @param crosshair the crosshair.
     */
    protected void drawHorizontalCrosshair(Graphics2D g2, Rectangle2D dataArea,
            double y, Crosshair crosshair)
    {

        if (y >= dataArea.getMinY() && y <= dataArea.getMaxY())
        {
            Line2D line = new Line2D.Double(dataArea.getMinX(), y,
                    dataArea.getMaxX(), y);
            Paint savedPaint = g2.getPaint();
            Stroke savedStroke = g2.getStroke();
            g2.setPaint(crosshair.getPaint());
            g2.setStroke(crosshair.getStroke());
            g2.draw(line);
            if (crosshair.isLabelVisible())
            {
                String label = crosshair.getLabelGenerator().generateLabel(
                        crosshair);
                RectangleAnchor anchor = crosshair.getLabelAnchor();
                Point2D pt = calculateLabelPoint(line, anchor, 5, 5);
                float xx = (float) pt.getX();
                float yy = (float) pt.getY();
                TextAnchor alignPt = textAlignPtForLabelAnchorH(anchor);
                Shape hotspot = TextUtils.calculateRotatedStringBounds(
                        label, g2, xx, yy, alignPt, 0.0, TextAnchor.CENTER);   
                if(hotspot != null)
                {                    
                    if (!dataArea.contains(hotspot.getBounds2D()))
                    {
                        anchor = flipAnchorV(anchor);
                        pt = calculateLabelPoint(line, anchor, 5, 5);
                        xx = (float) pt.getX();
                        yy = (float) pt.getY();
                        alignPt = textAlignPtForLabelAnchorH(anchor);
                        hotspot = TextUtils.calculateRotatedStringBounds(
                                label, g2, xx, yy, alignPt, 0.0, TextAnchor.CENTER);
                    }

                    g2.setPaint(crosshair.getLabelBackgroundPaint());
                    g2.fill(hotspot);
                    g2.setPaint(crosshair.getLabelOutlinePaint());
                    g2.setStroke(crosshair.getLabelOutlineStroke());
                    g2.draw(hotspot);
                    g2.setPaint(crosshair.getLabelPaint());
                    TextUtils.drawAlignedString(label, g2, xx, yy, alignPt);       
                }
            }
            g2.setPaint(savedPaint);
            g2.setStroke(savedStroke);
        }
    }

    /**
     * Draws a crosshair vertically on the plot.
     *
     * @param g2 the graphics target.
     * @param dataArea the data area.
     * @param x the x-value in Java2D space.
     * @param crosshair the crosshair.
     */
    protected void drawVerticalCrosshair(Graphics2D g2, Rectangle2D dataArea,
            double x, Crosshair crosshair)
    {
        if(!crosshair.isVisible())
            return;
        
        if (x >= dataArea.getMinX() && x <= dataArea.getMaxX())
        {
            Line2D line = new Line2D.Double(x, dataArea.getMinY(), x,
                    dataArea.getMaxY());
            Paint savedPaint = g2.getPaint();
            Stroke savedStroke = g2.getStroke();            
            g2.setPaint(crosshair.getPaint());
            g2.setStroke(crosshair.getStroke());
            g2.draw(line);
            if (crosshair.isLabelVisible())
            {
                String label = crosshair.getLabelGenerator().generateLabel(
                        crosshair);
                RectangleAnchor anchor = crosshair.getLabelAnchor();
                Point2D pt = calculateLabelPoint(line, anchor, 5, 5);
                float xx = (float) pt.getX();
                float yy = (float) pt.getY();
                TextAnchor alignPt = textAlignPtForLabelAnchorV(anchor);
                Shape hotspot = TextUtils.calculateRotatedStringBounds(
                        label, g2, xx, yy, alignPt, 0.0, TextAnchor.CENTER);
                if (!dataArea.contains(hotspot.getBounds2D()))
                {
                    anchor = flipAnchorH(anchor);
                    pt = calculateLabelPoint(line, anchor, 5, 5);
                    xx = (float) pt.getX();
                    yy = (float) pt.getY();
                    alignPt = textAlignPtForLabelAnchorV(anchor);
                    hotspot = TextUtils.calculateRotatedStringBounds(
                            label, g2, xx, yy, alignPt, 0.0, TextAnchor.CENTER);
                }
                g2.setPaint(crosshair.getLabelBackgroundPaint());
                g2.fill(hotspot);
                g2.setPaint(crosshair.getLabelOutlinePaint());
                g2.setStroke(crosshair.getLabelOutlineStroke());
                g2.draw(hotspot);
                g2.setPaint(crosshair.getLabelPaint());
//                g2.setFont(crosshair.getLabelFont());                
                TextUtils.drawAlignedString(label, g2, xx, yy, alignPt);
            }
            g2.setPaint(savedPaint);
            g2.setStroke(savedStroke);
        }
    }

    /**
     * Calculates the anchor point for a label.
     *
     * @param line the line for the crosshair.
     * @param anchor the anchor point.
     * @param deltaX the x-offset.
     * @param deltaY the y-offset.
     *
     * @return The anchor point.
     */
    private Point2D calculateLabelPoint(Line2D line, RectangleAnchor anchor,
            double deltaX, double deltaY)
    {
        double x, y;
        boolean left = (anchor == RectangleAnchor.BOTTOM_LEFT
                || anchor == RectangleAnchor.LEFT
                || anchor == RectangleAnchor.TOP_LEFT);
        boolean right = (anchor == RectangleAnchor.BOTTOM_RIGHT
                || anchor == RectangleAnchor.RIGHT
                || anchor == RectangleAnchor.TOP_RIGHT);
        boolean top = (anchor == RectangleAnchor.TOP_LEFT
                || anchor == RectangleAnchor.TOP
                || anchor == RectangleAnchor.TOP_RIGHT);
        boolean bottom = (anchor == RectangleAnchor.BOTTOM_LEFT
                || anchor == RectangleAnchor.BOTTOM
                || anchor == RectangleAnchor.BOTTOM_RIGHT);
        Rectangle rect = line.getBounds();

        // we expect the line to be vertical or horizontal
        if (line.getX1() == line.getX2())
        {  // vertical
            x = line.getX1();
            y = (line.getY1() + line.getY2()) / 2.0;
            if (left)
            {
                x = x - deltaX;
            }
            if (right)
            {
                x = x + deltaX;
            }
            if (top)
            {
                y = Math.min(line.getY1(), line.getY2()) + deltaY;
            }
            if (bottom)
            {
                y = Math.max(line.getY1(), line.getY2()) - deltaY;
            }
        }
        else
        {  // horizontal
            x = (line.getX1() + line.getX2()) / 2.0;
            y = line.getY1();
            if (left)
            {
                x = Math.min(line.getX1(), line.getX2()) + deltaX;
            }
            if (right)
            {
                x = Math.max(line.getX1(), line.getX2()) - deltaX;
            }
            if (top)
            {
                y = y - deltaY;
            }
            if (bottom)
            {
                y = y + deltaY;
            }
        }
        return new Point2D.Double(x, y);
    }

    /**
     * Returns the text anchor that is used to align a label to its anchor
     * point.
     *
     * @param anchor the anchor.
     *
     * @return The text alignment point.
     */
    private TextAnchor textAlignPtForLabelAnchorV(RectangleAnchor anchor)
    {
        TextAnchor result = TextAnchor.CENTER;
        switch (anchor)
        {
            case TOP_LEFT:
                result = TextAnchor.TOP_RIGHT;
                break;
            case TOP:
                result = TextAnchor.TOP_CENTER;
                break;
            case TOP_RIGHT:
                result = TextAnchor.TOP_LEFT;
                break;
            case LEFT:
                result = TextAnchor.HALF_ASCENT_RIGHT;
                break;
            case RIGHT:
                result = TextAnchor.HALF_ASCENT_LEFT;
                break;
            case BOTTOM_LEFT:
                result = TextAnchor.BOTTOM_RIGHT;
                break;
            case BOTTOM:
                result = TextAnchor.BOTTOM_CENTER;
                break;
            case BOTTOM_RIGHT:
                result = TextAnchor.BOTTOM_LEFT;
                break;
            default:
                break;
        }
        return result;
    }

    /**
     * Returns the text anchor that is used to align a label to its anchor
     * point.
     *
     * @param anchor the anchor.
     *
     * @return The text alignment point.
     */
    private TextAnchor textAlignPtForLabelAnchorH(RectangleAnchor anchor)
    {
        TextAnchor result = TextAnchor.CENTER;
        switch (anchor)
        {
            case TOP_LEFT:
                result = TextAnchor.BOTTOM_LEFT;
                break;
            case TOP:
                result = TextAnchor.BOTTOM_CENTER;
                break;
            case TOP_RIGHT:
                result = TextAnchor.BOTTOM_RIGHT;
                break;
            case LEFT:
                result = TextAnchor.HALF_ASCENT_LEFT;
                break;
            case RIGHT:
                result = TextAnchor.HALF_ASCENT_RIGHT;
                break;
            case BOTTOM_LEFT:
                result = TextAnchor.TOP_LEFT;
                break;
            case BOTTOM:
                result = TextAnchor.TOP_CENTER;
                break;
            case BOTTOM_RIGHT:
                result = TextAnchor.TOP_RIGHT;
                break;
            default:
                break;
        }
        return result;
    }

    private RectangleAnchor flipAnchorH(RectangleAnchor anchor)
    {
        RectangleAnchor result = anchor;
        switch (anchor)
        {
            case TOP_LEFT:
                result = RectangleAnchor.TOP_RIGHT;
                break;
            case TOP_RIGHT:
                result = RectangleAnchor.TOP_LEFT;
                break;
            case LEFT:
                result = RectangleAnchor.RIGHT;
                break;
            case RIGHT:
                result = RectangleAnchor.LEFT;
                break;
            case BOTTOM_LEFT:
                result = RectangleAnchor.BOTTOM_RIGHT;
                break;
            case BOTTOM_RIGHT:
                result = RectangleAnchor.BOTTOM_LEFT;
                break;
            default:
                break;
        }
        return result;
    }

    private RectangleAnchor flipAnchorV(RectangleAnchor anchor)
    {
        RectangleAnchor result = anchor;
        switch (anchor)
        {
            case TOP_LEFT:
                result = RectangleAnchor.BOTTOM_LEFT;
                break;
            case TOP_RIGHT:
                result = RectangleAnchor.BOTTOM_RIGHT;
                break;
            case TOP:
                result = RectangleAnchor.BOTTOM;
                break;
            case BOTTOM:
                result = RectangleAnchor.TOP;
                break;
            case BOTTOM_LEFT:
                result = RectangleAnchor.TOP_LEFT;
                break;
            case BOTTOM_RIGHT:
                result = RectangleAnchor.TOP_RIGHT;
                break;
            default:
                break;
        }
        return result;
    }

    /**
     * Tests this overlay for equality with an arbitrary object.
     *
     * @param obj the object ({@code null} permitted).
     *
     * @return A boolean.
     */
    @Override
    public boolean equals(Object obj)
    {
        if (obj == this)
        {
            return true;
        }
        if (!(obj instanceof CrosshairOverlay))
        {
            return false;
        }
        CrosshairOverlay that = (CrosshairOverlay) obj;
        if (!this.xCrosshairs.equals(that.xCrosshairs))
        {
            return false;
        }
        return this.yCrosshairs.equals(that.yCrosshairs);
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.xCrosshairs);
        hash = 41 * hash + Objects.hashCode(this.yCrosshairs);
        return hash;
    }

    /**
     * Returns a clone of this instance.
     *
     * @return A clone of this instance.
     *
     * @throws java.lang.CloneNotSupportedException if there is some problem
     * with the cloning.
     */
    @Override
    public Object clone() throws CloneNotSupportedException
    {
        CrosshairOverlay clone = (CrosshairOverlay) super.clone();
        clone.xCrosshairs = (List) ObjectUtils.deepClone(this.xCrosshairs);
        clone.yCrosshairs = (List) ObjectUtils.deepClone(this.yCrosshairs);
        return clone;
    }

}
