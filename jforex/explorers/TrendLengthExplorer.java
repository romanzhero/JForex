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
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

import jforex.events.TAEventDesc;
import jforex.logging.TradeLog;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.FlexTAValue;
import jforex.trades.trend.SmaCrossAdvancedTradeSetup;
import jforex.utils.FXUtils;
import jforex.utils.JForexChart;
import jforex.utils.TradingHours;
import jforex.utils.log.FlexLogEntry;
import jforex.utils.log.Logger;
import jforex.utils.props.ClimberProperties;

/**
 * Pokazuje graficki trajanje cistih trendova. Pored toga skuplja i informacije o njihovom trajanju:
 * - duzina u svecicama
 * - vremensko trajanje
 * - maksimalni profit 
 * Na kraju vremenskog perioda izracunava i ukupne vrednosti:
 * - % svecica u kojima je vladao trend
 * - maximalni, prosecni, median profit
 * Kriterijumi za POCETAK long perioda (isto kao kasnije za ulazak u trejd):
 * Grupa 1: trend ili flat
1. TREND_ID									6 (plus na prethodnoj svecici nije bio, tj. CROSS !!!)
2. MAs_DISTANCE_PERC						veca od nekog minimuma, verovatno iznad 20/25 percentile
3. MA200_HIGHEST							ne sme
4. MA200_LOWEST							
5. MA200_IN_CHANNEL							sme samo ako je MAs_DISTANCE_PERC dovoljno veliki
6. MA200MA100_TREND_DISTANCE_PERC			(za ulaz nevazno, interesatno za izlaz)
7. FLAT_REGIME polozaj MAs u kanalu			ne sme ako su sva CETIRI u kanalu (?), mozda sme ako je MA200 najnizi i MAs_DISTANCE_PERC OK ??? Prouciti !
8. ICHI										mozda zgodno proveriti da li je svecica van oblaka ?

Grupa 2: momentum i OS/OB
9. Stanje STOCH linija						ne sme obe OB zajedno sa oba SMI OB (prekasno), ne sme oba FALLING_IN_MIDDLE
10. Stanje SMI linija						ne sme oba OB (zavisno ili nezavisno od Stoch ??), makar brzi mora da raste
11. Stanje RSI linije ?
12. Stanje CCI linije ?
13. CHANNEL_POS (close)

Grupa 3: volatilitet (uzak / sirok kanal)
14. BBANDS_SQUEEZE_PERC						veca od nekog minimuma, verovatno iznad 20/25 percentile. Mozda nevazno ako je MAs_DISTANCE_PERC dovoljno velik i jos eventualno MA200 najnizi ??

Grupa 4: price action / candlestick paterns
15. BULLISH_CANDLES / BEARISH_CANDLES		eventualno bullish sa jasno bullish ukupnim telom
16. poslednja svecica						mora biti ili cisto bulish ili 1-bar bullish reversal 

	Kraj perioda prosto kada svecica zatvori ispod MA100 (dinamicko upravljanje trejdom ostaje za strategiju)
 *
 */
public class TrendLengthExplorer implements IStrategy {
	
	protected class TrendPeriodData {
		boolean isLong;
		long
			startTime, endTime,
			lengthInBars, duration;
		double
			startPrice,
			maxProfit; // in %
		public TrendPeriodData(long startTime, boolean isLong, double startPrice) {
			super();
			this.startTime = startTime;
			this.isLong = isLong;
			this.startPrice = startPrice;
			duration = 0;
			maxProfit = 0.0;
		}
		
		public void updateOnBar(IBar bidBar) {
			duration++;
			endTime = bidBar.getTime();
			double currProfit = 0.0;
			if (isLong) 
				currProfit = (bidBar.getClose() - startPrice) / startPrice;
			else
				currProfit = (startPrice - bidBar.getClose()) / startPrice;
			if (currProfit > maxProfit)
				maxProfit = currProfit;
		}		
	}
	
	protected TrendPeriodData currData = null;
	protected List<TrendPeriodData> stats = new ArrayList<TrendPeriodData>();
	protected JForexChart chart = null;
	private Instrument selectedInstrument;
	private boolean 
		visualMode, showIndicators,
		trendOnGoing = false;
	private String reportDir;
	
	public Period selectedPeriod = Period.THIRTY_MINS;
	public Filter selectedFilter = Filter.ALL_FLATS;	
	
	private IEngine engine;
	private IConsole console;
	private IHistory history;
	private IIndicators indicators;
	private IDataService dataService;
		
	private FlexTASource taSource = null;
	private Map<String, FlexTAValue> 
		lastTaValues = null,
		prevTaValues = null;
	private SmaCrossAdvancedTradeSetup setup = null;
		
	private int orderCnt = 0;
	private Logger 
		log = null,
		statsLog = null;
	private TradeLog tradeLog = null;
	private long totalTestBars = 0;
	
	private boolean headerPrinted = false;
	private ClimberProperties conf = null;

	public TrendLengthExplorer(Instrument selectedInstrument, ClimberProperties p) {
		super();
		this.selectedInstrument = selectedInstrument;
		this.visualMode = p.getProperty("visualMode", "no").equalsIgnoreCase("yes"); 
		this.showIndicators = p.getProperty("showIndicators", "no").equalsIgnoreCase("yes");
		this.reportDir = p.getProperty("reportDirectory", ".");
		conf = p;
	}


	@Override
	public void onStart(IContext context) throws JFException {
		this.engine = context.getEngine();
		this.console = context.getConsole();
		this.history = context.getHistory();
		this.indicators = context.getIndicators();
		FXUtils.setProfitLossHelper(context.getAccount().getAccountCurrency(), history);
		dataService = context.getDataService();
		
		taSource = new FlexTASource(indicators, history, selectedFilter);

		log = new Logger(reportDir + "//Trend_Length_report_" + FXUtils.getFileTimeStamp(System.currentTimeMillis()) + ".txt");
		statsLog = new Logger(reportDir + "//Trend_Length_stat_report_" + FXUtils.getFileTimeStamp(System.currentTimeMillis()) + ".txt");
		
		setup = new SmaCrossAdvancedTradeSetup(engine, context, context.getSubscribedInstruments(), true, true, 20, 20, false);
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
		totalTestBars++;
		if (prevTaValues == null) {
			prevTaValues = lastTaValues;
			return;
		}	
		if (!trendOnGoing) {
			TAEventDesc signal = setup.checkEntry(instrument, period, askBar, bidBar, selectedFilter, lastTaValues);
			if (signal != null && signal.eventType.equals(TAEventDesc.TAEventType.ENTRY_SIGNAL)) {
				trendOnGoing = true;
				tradeLog = new TradeLog(FXUtils.getOrderLabel(instrument, "TrendLength", bidBar.getTime(), signal.isLong, orderCnt ++), 
						signal.isLong, "TrendLength", bidBar.getTime(), bidBar.getClose(), 0, 0);
				tradeLog.addTAData(lastTaValues);

				chart.showVerticalLineOnGUI(instrument, bidBar.getTime(), signal.isLong ? Color.GREEN : Color.RED);
				chart.showTradingEventOnGUI("Start " + (signal.isLong ? "long" : "short"), instrument, bidBar.getTime(), bidBar.getClose());
			} else {
				prevTaValues = lastTaValues;
				setup.inTradeProcessing(instrument, period, askBar, bidBar, selectedFilter, null, lastTaValues, null);
				return;
			}
			// trend started !
			currData = new TrendPeriodData(bidBar.getTime(), signal.isLong, bidBar.getClose());
		} else if (trendOnGoing) {
			currData.updateOnBar(bidBar);
			tradeLog.updateMaxProfit(bidBar);
			setup.inTradeProcessing(instrument, period, askBar, bidBar, selectedFilter, null, lastTaValues, null);
			double ma100 = lastTaValues.get(FlexTASource.MAs).getDa2DimValue()[1][2];
			boolean trendEnd = (currData.isLong && bidBar.getClose() < ma100)
								|| (!currData.isLong && bidBar.getClose() > ma100);
			if  (trendEnd) {
				trendOnGoing = false;
				setup.afterTradeReset(instrument);
				stats.add(currData);
				if (tradeLog != null) {
					tradeLog.exitTime = bidBar.getTime();
					tradeLog.exitReason = "MA100 crossed";
					tradeLog.setPnLInPips(bidBar.getClose(), instrument);
					tradeLog.setPnLInPerc(bidBar.getClose());
					
					tradeLog.addLogEntry(new FlexLogEntry("exitValues", "EXIT"));
					tradeLog.addLogEntry(new FlexLogEntry("exitSetup", "TrendLength"));
					tradeLog.addLogEntry(new FlexLogEntry("lastTradingAction", "MA100 crossed"));
					tradeLog.addTAData(lastTaValues);
					
					if (!headerPrinted) {
						headerPrinted = true;
						statsLog.print(tradeLog.prepareHeader());
					}
					String logLine = tradeLog.prepareExitReport(instrument);
					console.getOut().println(logLine);					
					statsLog.print(logLine);					
				}
				
				chart.showVerticalLineOnGUI(instrument, bidBar.getTime(), Color.BLACK);
				chart.showTradingEventOnGUI("End " + (currData.isLong ? "long" : "short"), instrument, bidBar.getTime(), bidBar.getClose());
			} 
		}
		prevTaValues = lastTaValues;
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
		double maxProfit = 0.0;
		long 
			maxProfitTime = 0, 
			maxDuration = 0,
			maxDurationTime = 0,
			totalDuration = 0;
		double[] profitValues = new double[stats.size()];
		double[] durationValues = new double[stats.size()];
		int i = 0;
		for (TrendPeriodData curr : stats) {
			profitValues[i] = curr.maxProfit;
			durationValues[i++] = curr.duration;
			totalDuration += curr.duration;
			if (curr.maxProfit > maxProfit) {
				maxProfit = curr.maxProfit;
				maxProfitTime = curr.startTime;
			}
			if (curr.duration > maxDuration) {
				maxDuration = curr.duration;
				maxDurationTime = curr.startTime;
			}
		}
		log.print("Trend Length stats report for " + selectedInstrument.toString() 
			+ ", time frame: " + selectedPeriod.toString()
			+ ", test period start: " + conf.getProperty("testIntervalStart", "no start set")
			+ ", end: " + conf.getProperty("testIntervalEnd", "no end set"));
		log.print("Max profit: " + FXUtils.df2.format(maxProfit * 100) 
			+ " (started: " + FXUtils.getFormatedTimeGMT(maxProfitTime) + "), "
			+ "avg. max. profit: " + FXUtils.df2.format(FXUtils.average(profitValues) * 100));
		log.print("Time in trend (%): " + FXUtils.df1.format((double)totalDuration / (double)totalTestBars * 100.0) + ", "
				+ "Max duration: " + maxDuration
				+ " (started: " + FXUtils.getFormatedTimeGMT(maxDurationTime) + "), "
				+ "avg. duration: " + FXUtils.df1.format(FXUtils.average(durationValues)), true);
		
		log.close();
		statsLog.close();
	}

}
