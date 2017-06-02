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
import com.dukascopy.api.drawings.ITextChartObject;
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
				chart.add(indicators.getIndicator("BBands"), new Object[] { 20,
						2.0, 2.0, MaType.SMA.ordinal() }, new Color[] {
						Color.MAGENTA, Color.RED, Color.MAGENTA }, null, null);
				chart.add(indicators.getIndicator("STOCH"), new Object[] { 14,
						3, MaType.SMA.ordinal(), 3, MaType.SMA.ordinal() },
						new Color[] { Color.RED, Color.BLUE },
						new OutputParameterInfo.DrawingStyle[] {
								OutputParameterInfo.DrawingStyle.LINE,
								OutputParameterInfo.DrawingStyle.LINE }, null);
				chart.add(indicators.getIndicator("SMA"), new Object[] { 50 },
						new Color[] { Color.BLUE }, null, null);
				chart.add(indicators.getIndicator("SMA"), new Object[] { 100 },
						new Color[] { Color.GREEN }, null, null);
				chart.add(indicators.getIndicator("SMA"), new Object[] { 200 },
						new Color[] { Color.YELLOW }, null, null);
				chart.add(indicators.getIndicator("SMI"), new Object[] { 50,
						15, 5, 3 }, new Color[] { Color.CYAN, Color.BLACK },
						new OutputParameterInfo.DrawingStyle[] {
								OutputParameterInfo.DrawingStyle.LINE,
								OutputParameterInfo.DrawingStyle.NONE }, null);

				List<IIndicatorPanel> panels = chart.getIndicatorPanels();
				for (IIndicatorPanel currPanel : panels) {
					List<IIndicator> panelIndicators = currPanel.getIndicators();
					for (IIndicator currIndicator : panelIndicators) {
						if (currIndicator.toString().contains("SMIIndicator")) {
							currPanel.add(indicators.getIndicator("SMI"),
											new Object[] { 10, 3, 5, 3 },
											new Color[] { Color.RED,Color.BLACK },
											new OutputParameterInfo.DrawingStyle[] {
													OutputParameterInfo.DrawingStyle.LINE,
													OutputParameterInfo.DrawingStyle.NONE },
											null);
							IHorizontalLineChartObject lplus60 = chart.getChartObjectFactory().createHorizontalLine();
							lplus60.setPrice(0, 60);
							lplus60.setText("60");
							lplus60.setColor(Color.BLACK);
							lplus60.setLineStyle(LineStyle.DASH);
							currPanel.add(lplus60);

							IHorizontalLineChartObject lminus60 = chart
									.getChartObjectFactory().createHorizontalLine();
							lminus60.setPrice(0, -60);
							lminus60.setText("-60");
							lminus60.setColor(Color.BLACK);
							lminus60.setLineStyle(LineStyle.DASH);
							currPanel.add(lminus60);
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

	public double getMaxPrice() {
		return chart.getMaxPrice();
	}

	public double getMinPrice() {
		return chart.getMinPrice();
	}
}


