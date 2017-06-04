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
	private IContext context;
	private IConsole console;
	private IHistory history;
	private IIndicators indicators;
	private IDataService dataService;
		
	private FlexTASource taSource = null;
	private Map<String, FlexTAValue> 
		lastTaValues = null,
		prevTaValues = null;
	private SmaCrossAdvancedTradeSetup setup = null;
		
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
		this.context = context;
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
				chart.showVerticalLineOnGUI("Start " + (signal.isLong ? "long" : "short"), instrument, bidBar.getTime(), signal.isLong ? Color.GREEN : Color.RED);
			} else {
				prevTaValues = lastTaValues;
				setup.inTradeProcessing(instrument, period, askBar, bidBar, selectedFilter, null, lastTaValues, null);
				return;
			}
			// trend started !
			currData = new TrendPeriodData(bidBar.getTime(), signal.isLong, bidBar.getClose());
		} else if (trendOnGoing) {
			currData.updateOnBar(bidBar);
			setup.inTradeProcessing(instrument, period, askBar, bidBar, selectedFilter, null, lastTaValues, null);
			double ma100 = lastTaValues.get(FlexTASource.MAs).getDa2DimValue()[1][2];
			boolean trendEnd = (currData.isLong && bidBar.getClose() < ma100)
								|| (!currData.isLong && bidBar.getClose() > ma100);
			if  (trendEnd) {
				trendOnGoing = false;
				setup.afterTradeReset(instrument);
				stats.add(currData);
				chart.showVerticalLineOnGUI("End " + (currData.isLong ? "long" : "short"), instrument, bidBar.getTime(), Color.BLACK);
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
		// TODO Auto-generated method stub

	}

}
