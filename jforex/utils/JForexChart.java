package jforex.utils;

import java.awt.Color;
import java.awt.Font;
import java.util.List;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IChart;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IIndicatorPanel;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.LineStyle;
import com.dukascopy.api.IIndicators.MaType;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.drawings.IHorizontalLineChartObject;
import com.dukascopy.api.drawings.ISignalDownChartObject;
import com.dukascopy.api.drawings.ISignalUpChartObject;
import com.dukascopy.api.drawings.ITextChartObject;
import com.dukascopy.api.drawings.ITimeMarkerChartObject;
import com.dukascopy.api.indicators.IIndicator;
import com.dukascopy.api.indicators.OutputParameterInfo;

public class JForexChart {

	private IContext context = null;
	private IChart chart = null;
	private boolean visualMode;
	private Instrument selectedInstrument;
	private IConsole console;
	private boolean showIndicators;
	private IIndicators indicators;
	
	public JForexChart(IContext context, boolean visualMode, Instrument selectedInstrument, IConsole console,
			boolean showIndicators, IIndicators indicators) {
		super();
		this.context = context;
		this.chart = context.getChart(selectedInstrument);
		this.visualMode = visualMode;
		this.selectedInstrument = selectedInstrument;
		this.console = console;
		this.showIndicators = showIndicators;
		this.indicators = indicators;
	}

	public void showChart(IContext context) {
		if (visualMode) {
			chart = context.getChart(selectedInstrument);
			if (chart == null) {
				// chart is not opened, we can't plot an object
				console.getOut().println("Can't open the chart for " + selectedInstrument.toString() + ", stop !");
				context.stop();
			}
			if (showIndicators) {
				chart.add(indicators.getIndicator("BBands"), new Object[] { 20,	2.0, 2.0, MaType.SMA.ordinal() }, 
						new Color[] {Color.MAGENTA, Color.RED, Color.MAGENTA }, null, null);
				chart.add(indicators.getIndicator("STOCH"), new Object[] { 14, 3, MaType.SMA.ordinal(), 3, MaType.SMA.ordinal() },
						new Color[] { Color.RED, Color.BLUE },
						new OutputParameterInfo.DrawingStyle[] {OutputParameterInfo.DrawingStyle.LINE, OutputParameterInfo.DrawingStyle.LINE }, null);
				chart.add(indicators.getIndicator("SMA"), new Object[] { 50 }, new Color[] { Color.BLUE }, null, null);
				chart.add(indicators.getIndicator("SMA"), new Object[] { 100 },	new Color[] { Color.GREEN }, null, null);
				chart.add(indicators.getIndicator("SMA"), new Object[] { 200 }, new Color[] { Color.YELLOW }, null, null);
				chart.add(indicators.getIndicator("SMI"), new Object[] { 50, 15, 5, 3 }, new Color[] { Color.CYAN, Color.BLACK },
						new OutputParameterInfo.DrawingStyle[] {OutputParameterInfo.DrawingStyle.LINE, OutputParameterInfo.DrawingStyle.NONE }, null);

				List<IIndicatorPanel> panels = chart.getIndicatorPanels();
				for (IIndicatorPanel currPanel : panels) {
					List<IIndicator> panelIndicators = currPanel.getIndicators();
					for (IIndicator currIndicator : panelIndicators) {
						if (currIndicator.toString().contains("SMIIndicator")) {
							currPanel.add(indicators.getIndicator("SMI"),
										new Object[] { 10, 3, 5, 3 },
										new Color[] { Color.RED,Color.BLACK },
										new OutputParameterInfo.DrawingStyle[] { OutputParameterInfo.DrawingStyle.LINE,	OutputParameterInfo.DrawingStyle.NONE }, null);

						}
					}
				}
				// printIndicatorInfos(indicators.getIndicator("SMI"));
				// printIndicatorInfos(indicators.getIndicator("SMA"));
			}
		}
	}
	
//	private void showTradingEventOnGUI(long tradeID, String textToShow, boolean direction, IBar bidBar, IBar askBar, Instrument instrument) {
//		if (visualMode) {
//			//chart = context.getChart(instrument);
//			if (chart == null) {
//				// chart is not opened, we can't plot an object
//				console.getOut().println("Can't open the chart for " + instrument.toString() + ", stop !");
//				context.stop();
//			}
//			ITextChartObject txt = chart.getChartObjectFactory().createText();
//			txt.setMenuEnabled(true);
//			txt.setText(tradeID + "." + commentCnt++ + ": " + textToShow, new Font("Helvetica", Font.BOLD, 14));
//			txt.setTime(0, bidBar.getTime());
//			double level = 0;
//			if (direction)
//				level = calcNextLongLevel(bidBar, instrument, chart);
//			else
//				level = calcNextShortLevel(askBar, instrument, chart);
//			txt.setPrice(0, level);
//			chart.add(txt);
//		}
//	}

	public void showTradingEventOnGUI(String textToShow, Instrument instrument, long time, double level) {
		if (visualMode) {
			chart = context.getChart(instrument);
			if (chart == null) {
				// chart is not opened, we can't plot an object
				console.getOut().println("Can't open the chart for " + instrument.toString() + ", stop !");
				context.stop();
			}
			ITextChartObject txt = chart.getChartObjectFactory().createText();
			txt.setMenuEnabled(true);
			txt.setText(textToShow, new Font("Helvetica", Font.BOLD, 14));
			txt.setTime(0, time);
			txt.setPrice(0, level);
			chart.add(txt);
		}
	}
	
	public void showArrowOnGUI(boolean isLong, Instrument instrument, long time, double level) {
		if (visualMode) {
			chart = context.getChart(instrument);
			if (chart == null) {
				// chart is not opened, we can't plot an object
				console.getOut().println("Can't open the chart for " + instrument.toString() + ", stop !");
				context.stop();
			}
			if (isLong) {
				ISignalUpChartObject up = chart.getChartObjectFactory().createSignalUp();
				up.setMenuEnabled(true);
				up.setTime(0, time);
				up.setPrice(0, level);
				chart.add(up);
				
			} else {
				ISignalDownChartObject down = chart.getChartObjectFactory().createSignalDown();
				down.setMenuEnabled(true);
				down.setTime(0, time);
				down.setPrice(0, level);
				chart.add(down);
			}
		}
	}
	
	public void showVerticalLineOnGUI(Instrument instrument, long time, Color color) {
		if (visualMode) {
			chart = context.getChart(instrument);
			if (chart == null) {
				// chart is not opened, we can't plot an object
				console.getOut().println("Can't open the chart for " + instrument.toString() + ", stop !");
				context.stop();
			}
		}
	    ITimeMarkerChartObject vLine = chart.getChartObjectFactory().createTimeMarker();              
	    vLine.setTime(0, time);
	    vLine.setColor(color);
	    vLine.setLineWidth(2);
	    chart.add(vLine);       
	}

    void drawCandleMomentumSignal(Instrument instrument, boolean isLong, long time, double level) {
         if (isLong) {
            ISignalUpChartObject signalUp = chart.getChartObjectFactory().createSignalUp();
            signalUp.setTime(0, time);
            signalUp.setPrice(0, level);
            chart.add(signalUp);
        } else {    
            ISignalDownChartObject signalDown = chart.getChartObjectFactory().createSignalDown();
            signalDown.setTime(0, time);
            signalDown.setPrice(0, level);
            chart.add(signalDown);            
        }        
    }	

	public double getMaxPrice() {
		return chart.getMaxPrice();
	}

	public double getMinPrice() {
		return chart.getMinPrice();
	}
}


