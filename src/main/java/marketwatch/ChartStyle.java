package marketwatch;

import java.awt.Color;

public class ChartStyle
{
    protected String font = "Dialog";
    protected byte fontSize = 15;    
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
    
    public  ChartStyle(String font,byte fontSize,Color chartColor,Color plotColor,Color crosshairsColor, Color dialogColor,Color dialogTextColor,
            Color lineColor,Color textColor,Color gridColor,int sliderValue, boolean showSnapshots,boolean showSubtitle,boolean showText,
            boolean showDialog,boolean showXcrosshairs,boolean showYcrosshair,boolean drawX,boolean drawY,boolean showMa,boolean showVolume,int averageBy)
    {
        this.font = font;
        this.fontSize = fontSize;     
        this.chartColor = chartColor;
        this.plotColor = plotColor;
        this.crosshairsColor = crosshairsColor;
        this.dialogColor = dialogColor;
        this.dialogTextColor = dialogTextColor;
        this.lineColor = lineColor;
        this.textColor = textColor;
        this.gridColor = gridColor;
        this.showSnapshots = showSnapshots;
        this.showSubtitle = showSubtitle;      
        this.showText = showText;
         this.showDialog = showDialog;
        this.showXcrosshairs = showXcrosshairs;
        this.showYcrosshair = showYcrosshair;
        this.drawX = drawX;
        this.drawY = drawY;
        this.sliderValue = sliderValue;
        this.showMovingAverage = showMa;
        this.averageBy = averageBy;   
        this.showVolume = showVolume;                
    }   
}
