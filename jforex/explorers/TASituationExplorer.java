/**
 * 
 */
package jforex.explorers;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.dukascopy.api.Filter;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IDataService;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

import jforex.logging.TradeLog;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.FlexTAValue;
import jforex.techanalysis.source.TechnicalSituation;
import jforex.techanalysis.source.TechnicalSituation.OverallTASituation;
import jforex.techanalysis.source.TechnicalSituation.TASituationReason;
import jforex.utils.FXUtils;
import jforex.utils.JForexChart;
import jforex.utils.TradingHours;
import jforex.utils.log.Logger;
import jforex.utils.props.ClimberProperties;

/**
 * Pokazuje graficki trajanje SVAKE faze TA rezima. Pored toga skuplja i informacije o njihovom trajanju:
 * - duzina u svecicama
 * - vremensko trajanje
 * - maksimalni profit 
 * Na kraju vremenskog perioda izracunava i ukupne vrednosti:
 * - % svecica u kojima su vladale pojedine faze
 * 
 */
public class TASituationExplorer implements IStrategy {
	
	protected class TAPhasePeriodData {
		OverallTASituation taSituation;
		long
			startTime, endTime,
			lengthInBars, duration;
		
		public TAPhasePeriodData(long time, OverallTASituation taSituation) {
			super();
			this.startTime = time;
			this.taSituation = taSituation;
			duration = 0;
		}

		public void updateOnBar(IBar bidBar) {
			duration++;
			endTime = bidBar.getTime();
		}		
	}
	
	protected TAPhasePeriodData currData = null;
	protected List<TAPhasePeriodData> stats = new ArrayList<TAPhasePeriodData>();
	protected JForexChart chart = null;
	private Instrument selectedInstrument;
	private boolean 
		visualMode, showIndicators;
	private String reportDir;
	
	public Period selectedPeriod = Period.THIRTY_MINS;
	public Filter selectedFilter = Filter.ALL_FLATS;	
	
	private IConsole console;
	private IHistory history;
	private IIndicators indicators;
	private IDataService dataService;
		
	private FlexTASource taSource = null;
	private Map<String, FlexTAValue> 
		lastTaValues = null;
	private OverallTASituation taSituation = OverallTASituation.DUMMY;
	private TASituationReason taReason = TASituationReason.NONE;

	
	private int orderCnt = 0;
	private Logger 
		log = null,
		statsLog = null;
	private TradeLog tradeLog = null;
	private long totalTestBars = 0;
	
	private boolean headerPrinted = false;
	private ClimberProperties conf = null;

	public TASituationExplorer(Instrument selectedInstrument, ClimberProperties p) {
		super();
		this.selectedInstrument = selectedInstrument;
		this.visualMode = p.getProperty("visualMode", "no").equalsIgnoreCase("yes"); 
		this.showIndicators = p.getProperty("showIndicators", "no").equalsIgnoreCase("yes");
		this.reportDir = p.getProperty("reportDirectory", ".");
		conf = p;
	}


	@Override
	public void onStart(IContext context) throws JFException {
		context.getEngine();
		this.console = context.getConsole();
		this.history = context.getHistory();
		this.indicators = context.getIndicators();
		FXUtils.setProfitLossHelper(context.getAccount().getAccountCurrency(), history);
		dataService = context.getDataService();
		
		taSource = new FlexTASource(indicators, history, selectedFilter);

		log = new Logger(reportDir + "//TASituation_Length_report_" + FXUtils.getFileTimeStamp(System.currentTimeMillis()) + ".txt");
		statsLog = new Logger(reportDir + "//TASituation_Length_stat_report_" + FXUtils.getFileTimeStamp(System.currentTimeMillis()) + ".txt");
		
		chart = new JForexChart(context, visualMode, selectedInstrument, console, showIndicators, indicators);
		chart.showChart(context);
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {	
		if (!instrument.equals(selectedInstrument)
			|| !period.equals(selectedPeriod)
			|| !TradingHours.barProcessingAllowed(dataService, bidBar.getTime())
			|| (bidBar.getClose() == bidBar.getOpen() && bidBar.getClose() == bidBar.getHigh() && bidBar.getClose() == bidBar.getLow()))
			return;
		
		lastTaValues = taSource.calcTAValues(instrument, period, askBar, bidBar);
		TechnicalSituation currTA = lastTaValues.get(FlexTASource.TA_SITUATION).getTehnicalSituationValue();
		totalTestBars++;
		
		if (!currTA.taSituation.equals(taSituation) || !currTA.taReason.equals(taReason)) {
			// new TA phase started !
			currData = new TAPhasePeriodData(bidBar.getTime(), currTA.taSituation);
			stats.add(currData);
			// record PREVIOUS period stats
			if (tradeLog != null) {
				if (!headerPrinted) {
					headerPrinted = true;
					statsLog.print(tradeLog.prepareHeader());
				}
				String logLine = tradeLog.prepareExitReport(instrument);
				console.getOut().println(logLine);					
				statsLog.print(logLine);
			}
			
			tradeLog = new TradeLog(FXUtils.getOrderLabel(instrument, "TrendLength", bidBar.getTime(), currTA.taSituation, orderCnt++), 
					currTA.taSituation.equals(OverallTASituation.BULLISH), "TrendLength", bidBar.getTime(), bidBar.getClose(), 0, 0);
			tradeLog.addTAData(lastTaValues);

			if (!currTA.taSituation.equals(taSituation)) {
				Color markerColor = Color.CYAN;
				double level = bidBar.getLow();
				if (currTA.taSituation.equals(OverallTASituation.BULLISH))
					markerColor = Color.GREEN;
				else if (currTA.taSituation.equals(OverallTASituation.BEARISH)) {
					markerColor = Color.RED;
					level = askBar.getHigh();
				}
				chart.showVerticalLineOnGUI(instrument, bidBar.getTime(), markerColor);
				chart.showTradingEventOnGUI("Start " + currTA.taSituation.toString() + "|" + currTA.taReason.toString()
					+ "; " + currTA.txtSummary, instrument, bidBar.getTime(), level);
			} else {
				chart.showTradingEventOnGUI(currTA.taReason.toString(), instrument, bidBar.getTime(), bidBar.getLow());
				if (currTA.taSituation.equals(OverallTASituation.BULLISH))
					chart.showArrowOnGUI(true, instrument, bidBar.getTime(), bidBar.getLow());
				else if (currTA.taSituation.equals(OverallTASituation.BEARISH)) 				
					chart.showArrowOnGUI(false, instrument, bidBar.getTime(), askBar.getHigh());
			}
			
		} else {
			currData.updateOnBar(bidBar);
			tradeLog.updateMaxProfit(bidBar);
		}
		taSituation = currTA.taSituation;
		taReason = currTA.taReason;
	}


	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {	}

	@Override
	public void onMessage(IMessage message) throws JFException {	}

	@Override
	public void onAccount(IAccount account) throws JFException {	}

	@Override
	public void onStop() throws JFException {
		// Final total stats report
		long 
			maxDuration = 0,
			maxDurationTime = 0,
			totalLongDuration = 0,
			totalFlatDuration = 0,
			totalShortDuration = 0;
		double[] durationValues = new double[stats.size()];
		int i = 0;
		for (TAPhasePeriodData curr : stats) {
			durationValues[i++] = curr.duration;
			if (curr.taSituation.equals(TechnicalSituation.OverallTASituation.BULLISH))
				totalLongDuration += curr.duration;
			else if (curr.taSituation.equals(TechnicalSituation.OverallTASituation.BEARISH))
				totalShortDuration += curr.duration;
			else
				totalFlatDuration += curr.duration;
			if (curr.duration > maxDuration) {
				maxDuration = curr.duration;
				maxDurationTime = curr.startTime;
			}
		}
		log.print("TA phase length stats report for " + selectedInstrument.toString() 
			+ ", time frame: " + selectedPeriod.toString()
			+ ", test period start: " + conf.getProperty("testIntervalStart", "no start set")
			+ ", end: " + conf.getProperty("testIntervalEnd", "no end set"));
		log.print("Time in uptrend (%): " + FXUtils.df1.format((double)totalLongDuration / (double)totalTestBars * 100.0));
		log.print("Time in downtrend (%): " + FXUtils.df1.format((double)totalShortDuration / (double)totalTestBars * 100.0));
		log.print("Time in flat (%): " + FXUtils.df1.format((double)totalFlatDuration / (double)totalTestBars * 100.0));
		log.print("Max duration: " + maxDuration
				+ " (started: " + FXUtils.getFormatedTimeGMT(maxDurationTime) + "), "
				+ "avg. duration: " + FXUtils.df1.format(FXUtils.average(durationValues)), true);
		
		log.close();
		statsLog.close();
	}

}
