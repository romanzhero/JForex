package jforex.strategies;

import java.io.File;
import java.math.BigDecimal;
import java.util.*;

import com.dukascopy.api.*;
import com.dukascopy.api.indicators.IIndicator;
import com.dukascopy.api.indicators.IndicatorInfo;
import jforex.utils.FXUtils;
import jforex.utils.JForexChart;
import jforex.utils.TradingHours;
import jforex.utils.log.FlexLogEntry;
import jforex.utils.log.Logger;
import jforex.utils.props.ClimberProperties;
import jforex.utils.stats.DailyPnL;
import jforex.utils.stats.RangesStats;
import jforex.utils.stats.RangesStats.InstrumentRangeStats;
import jforex.utils.RollingAverage;
import jforex.events.CandleMomentumEvent;
import jforex.events.ITAEvent;
import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.logging.TradeLog;
import jforex.techanalysis.Trend;
import jforex.techanalysis.source.FlexTASource;
import jforex.trades.*;
import jforex.trades.flat.FlatStrongTradeSetup;
import jforex.trades.flat.FlatTradeSetup;
import jforex.trades.momentum.CandleImpulsSetup;
import jforex.trades.momentum.MomentumReversalSetup;
import jforex.trades.momentum.SmiTradeSetup;
import jforex.trades.trend.SmaCrossTradeSetup;
import jforex.trades.trend.SmaSoloTradeSetup;
import jforex.trades.trend.SmaTradeSetup;
import jforex.trades.trend.TrendSprint;
import jforex.trades.trend.TrendSprintEarly;

public class FlatCascTest implements IStrategy {
	@Configurable(value = "Period", description = "Choose the time frame")
	public Period 
		selectedPeriod = null,
		orderSubmitPeriod = Period.ONE_MIN;

	@Configurable(value = "Filter", description = "Choose the candle filter")
	public Filter selectedFilter = Filter.ALL_FLATS;

	@Configurable(value = "Amount", stepSize = 0.0001, description = "Choose amount (step size 0.0001)")
	public double selectedAmount = 100000;

	protected Instrument selectedInstrument = null;
	protected boolean visualMode = false, showIndicators = false;
	protected String reportDir = null;

	private IEngine engine;
	private IConsole console;
	private IHistory history;
	private IIndicators indicators;
	private IDataService dataService;
	
	private JForexChart chart = null;

	private Map<String, IOrder> orderPerPair = new HashMap<String, IOrder>();
	private long orderCnt = 0;

	private List<ITradeSetup> tradeSetups = new ArrayList<ITradeSetup>();
	private List<ITAEvent> taEvents = new ArrayList<ITAEvent>();
	private ITakeOverRules takeOverRules = new SmiSmaTakeOverRules();
	private ITradeSetup currentSetup = null;

	private FlexTASource taSource = null;
	private Map<String, FlexLogEntry> lastTaValues = null;
	
	private Map<Instrument, InstrumentRangeStats> dayRanges = null;
	private DailyPnL dailyPnL = null;	
	private Map<Instrument, RollingAverage> barRangeAverages = null;
	
	private Logger 
		log = null,
		statsLog = null,
		barStatsLog = null; // for each bar
	private TradeLog tradeLog = null;
	protected boolean barStatsLogHeader = false;

	private boolean 
		orderWaitinginQueue = false,
		queueOrderIsLong;
	private String queueOrderLabel = null;
	private boolean headerPrinted = false;
	private ClimberProperties conf = null;

	private IContext context;

	private String lastTradingEvent = new String();
	private int commentCnt = 0;
//	private SortedSet<Double> commentLevels = new TreeSet<Double>();
//	private class CommentLevelBarsCount {
//		public double commentLevel;
//		public int barCount = 0;
//		public CommentLevelBarsCount(double pCommentLevel, int pBarCount) {
//			commentLevel = pCommentLevel;
//			barCount = pBarCount;
//		}
//	}
	private SortedMap<Double, Integer> commentLevelsCounts = new TreeMap<Double, Integer>();
	private static final double SCREEN_H = 1080;

	public FlatCascTest(Instrument selectedInstrument, boolean visualMode,	boolean showIndicators, String reportDir) {
		super();
		this.selectedInstrument = selectedInstrument;
		this.visualMode = visualMode;
		this.showIndicators = showIndicators;
		this.reportDir = reportDir;
	}
	
	public FlatCascTest(Instrument selectedInstrument, ClimberProperties p) {
		super();
		this.selectedInstrument = selectedInstrument;
		this.visualMode = p.getProperty("visualMode", "no").equalsIgnoreCase("yes"); 
		this.showIndicators = p.getProperty("showIndicators", "no").equalsIgnoreCase("yes");
		this.reportDir = p.getProperty("reportDirectory", ".");
		this.selectedAmount = Double.parseDouble(p.getProperty("tradeAmount", "100000.0"));
		this.selectedPeriod = FXUtils.reverseTimeFrameNamesMap.get(p.getProperty("timeFrame"));
		conf = p;
	}

	public void onStart(IContext context) throws JFException {
		this.engine = context.getEngine();
		this.console = context.getConsole();
		this.history = context.getHistory();
		this.indicators = context.getIndicators();
		this.context = context;
		FXUtils.setProfitLossHelper(context.getAccount().getAccountCurrency(), history);
		dataService = context.getDataService();
		
		calcBarRangeAverages(context);

		taSource = new FlexTASource(indicators, history, selectedFilter);
		
		openLoggers();		

		Set<Instrument> pairs = context.getSubscribedInstruments();
		for (Instrument currI : pairs) {
			orderPerPair.put(currI.name(), null);
		}
		configureSetups(context);
		
		//taEvents.add(new LongCandlesEvent(indicators, history));
		//taEvents.add(new ShortCandlesEvent(indicators, history));
		taEvents.add(new CandleMomentumEvent(indicators, history));
		
		chart = new JForexChart(context, visualMode, selectedInstrument, console, showIndicators, indicators);
		chart.showChart(context);
	}

	protected void calcBarRangeAverages(IContext context) throws JFException {
		barRangeAverages = new HashMap<Instrument, RollingAverage>();
		long testIntervalStart = history.getPreviousBarStart(selectedPeriod, conf.getTestIntervalStart().getMillis());
		for (Instrument i : context.getSubscribedInstruments()) {
			long firstCandle = testIntervalStart;
			List<IBar> bars = history.getBars(i, selectedPeriod, OfferSide.BID, selectedFilter, FXUtils.MONTH_WORTH_OF_1h_BARS * 12 * 2, firstCandle, 0);
			double[] barRanges = new double[bars.size()];
			int barIndex = 0;
			for (IBar currBar : bars) {
				barRanges[barIndex++] = currBar.getHigh() - currBar.getLow();
			}
			barRangeAverages.put(i, new RollingAverage(barRanges));
		}
	}

	protected void configureSetups(IContext context) {
		if (conf.getProperty("MomentumReversalSetup", "no").equals("yes"))
			tradeSetups.add(new MomentumReversalSetup(conf.getProperty("useEntryFilters", "no").equals("yes"), engine, context));
		if (conf.getProperty("CandleImpulsSetup", "no").equals("yes"))
			tradeSetups.add(new CandleImpulsSetup(conf.getProperty("useEntryFilters", "no").equals("yes"), engine, context, barRangeAverages));
		if (conf.getProperty("FlatStrongSetup", "no").equals("yes"))
			tradeSetups.add(new FlatStrongTradeSetup(engine, context, history, true, conf.getProperty("useEntryFilters", "no").equals("yes")));
		if (conf.getProperty("FlatSetup", "no").equals("yes"))
			tradeSetups.add(new FlatTradeSetup(engine, context, true, conf.getProperty("useEntryFilters", "no").equals("yes")));
		//tradeSetups.add(new PUPBSetup(indicators, history, engine));
		if (conf.getProperty("SMISetup", "no").equals("yes"))
			tradeSetups.add(new SmiTradeSetup(engine, context, false, 30.0, 30.0, conf.getProperty("useEntryFilters", "no").equals("yes")));
		
		if (conf.getProperty("TrendIDFollowCrossSetup", "no").equals("yes"))
			tradeSetups.add(new SmaCrossTradeSetup(engine, context, context.getSubscribedInstruments(), true, false, conf.getProperty("useEntryFilters", "no").equals("yes"), 30.0, 25.0, false));
		if (conf.getProperty("TrendIDFollowSetup", "no").equals("yes"))
			tradeSetups.add(new SmaTradeSetup(indicators, context, history, engine, context.getSubscribedInstruments(), true, false, 
					conf.getProperty("useEntryFilters", "no").equals("yes"), 30.0, 30.0, false));
		else if (conf.getProperty("TrendIDFollowSoloSetup", "no").equals("yes"))
			tradeSetups.add(new SmaSoloTradeSetup(engine, context, context.getSubscribedInstruments(), true, false, 30.0, 30.0, false, true));
		
		if (conf.getProperty("TrendSprintEarly", "no").equals("yes"))
			tradeSetups.add(new TrendSprintEarly(engine, context, context.getSubscribedInstruments(), true, false, conf.getProperty("useEntryFilters", "no").equals("yes"), 
					30.0, 30.0, true));
		if (conf.getProperty("TrendSprint", "no").equals("yes"))
			tradeSetups.add(new TrendSprint(engine, context, context.getSubscribedInstruments(), true, false, conf.getProperty("useEntryFilters", "no").equals("yes"), 
					30.0, 30.0, true));
	}

	protected void openLoggers() {
		String fileSuffix = FXUtils.getLogFileSuffix(this.conf, this.selectedInstrument, this.selectedPeriod);
		log = new Logger(reportDir + "//Casc_report_" + FXUtils.getFileTimeStamp(System.currentTimeMillis()) + "_" + fileSuffix + ".txt");
		statsLog = new Logger(reportDir + "//Casc_stat_report_" + FXUtils.getFileTimeStamp(System.currentTimeMillis()) + "_" + fileSuffix  + ".csv");
		statsLog.createXLS(reportDir + "//Casc_stat_report_" + FXUtils.getFileTimeStamp(System.currentTimeMillis()) + "_" + fileSuffix + ".xls");
		if (conf.getProperty("writeBarStats", "no").equals("yes"))
			barStatsLog = new Logger(reportDir + "//Casc_bar_report_" + FXUtils.getFileTimeStamp(System.currentTimeMillis()) + "_" + fileSuffix + ".csv");
	}


	private void printIndicatorInfos(IIndicator ind) {
		IndicatorInfo info = ind.getIndicatorInfo();
		print(String.format(
				"%s: input count=%s, optional input count=%s, output count=%s",
				info.getTitle(), info.getNumberOfInputs(),
				info.getNumberOfOptionalInputs(), info.getNumberOfOutputs()));
		for (int i = 0; i < ind.getIndicatorInfo().getNumberOfInputs(); i++) {
			print(String.format("Input %s: %s - %s", i, ind
					.getInputParameterInfo(i).getName(), ind
					.getInputParameterInfo(i).getType()));
		}
		for (int i = 0; i < ind.getIndicatorInfo().getNumberOfOptionalInputs(); i++) {
			print(String.format("Opt Input %s: %s - %s", i, ind
					.getOptInputParameterInfo(i).getName(), ind
					.getOptInputParameterInfo(i).getType()));
		}
		for (int i = 0; i < ind.getIndicatorInfo().getNumberOfOutputs(); i++) {
			print(String.format("Output %s: %s - %s", i, ind
					.getOutputParameterInfo(i).getName(), ind
					.getOutputParameterInfo(i).getType()));
		}
	}

	private void print(Object o) {
		console.getOut().println(o);
	}

	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (instrument.equals(selectedInstrument) 
			&& period.equals(orderSubmitPeriod)
			&& conf.getProperty("noWeekendPositions", "no").equals("yes")
			&& TradingHours.tradingAllowed(dataService, bidBar.getTime(), period) 
			&& !(bidBar.getClose() == bidBar.getOpen() && bidBar.getClose() == bidBar.getHigh() && bidBar.getClose() == bidBar.getLow())) {
			submitOrderFromQueue(selectedInstrument, bidBar, askBar);
			return;
		}
		
		if (instrument.equals(selectedInstrument)
			&& (period.equals(Period.WEEKLY) || dayRanges == null))
			checkDayRanges(instrument, askBar, bidBar);

		if (!instrument.equals(selectedInstrument)
			|| !period.equals(selectedPeriod)
			|| (bidBar.getClose() == bidBar.getOpen() && bidBar.getClose() == bidBar.getHigh() && bidBar.getClose() == bidBar.getLow()))
			return;

		IOrder order = orderPerPair.get(instrument.name());
		if (instrument.equals(selectedInstrument)
			&& period.equals(selectedPeriod)
			// no more trading 4 hours before close on Fridays, but only if explicitly configured !
			&& conf.getProperty("noWeekendPositions", "no").equals("yes")
			&& !TradingHours.barProcessingAllowed(dataService, bidBar.getTime(), 4 * 3600 * 1000)) {
			// and also close any open positions - no open positions left over weekend !
			if (order != null) {
				currentSetup.setLastTradingEvent("Trade closed due to Friday end of trading");
				order.close();
				order.waitForUpdate(null);
			}
			return;
		}
		
		incCommentLevelsCount();
		removeOldCommentLevel(15);
		lastTaValues = taSource.calcTAValues(instrument, period, askBar, bidBar);
		
		List<FlexLogEntry> barReport = new ArrayList<FlexLogEntry>();
		barReport.add(new FlexLogEntry("barTime", FXUtils.getFormatedTimeGMT(bidBar.getTime())));
		TradeLog.addTAData(lastTaValues, barReport);
		
		// report this for further analysis
		if (!barStatsLogHeader) {
			barStatsLogHeader = true;
			if (barStatsLog != null)
				barStatsLog.printLabelsFlex(barReport);
		}
		if (barStatsLog != null)
			barStatsLog.printValuesFlex(barReport);
		
		for (ITradeSetup ts : tradeSetups) {
			ts.updateOnBar(instrument, period, askBar, bidBar);
		}
		
		double dailyPnLvsRange = dailyPnL.ratioPnLAvgRange(instrument);
		if (order != null) {
			// there is an open order, might be pending (waiting) or filled !
/*			if (order.getState().equals(IOrder.State.OPENED)
				&& dailyPnLvsRange > 0.5) {
				// cancel pending order if daily profit OK and skip further trading
				order.close();
				order.waitForUpdate(null);
				order = null;
				orderPerPair.put(instrument.name(), null);	
				dailyPnL.resetInstrumentDailyPnL(instrument, bidBar.getTime());
				return;
			}*/
/*			long tradingHoursEnd = TradingHours.tradingHoursEnd(instrument, bidBar.getTime());
			if (tradingHoursEnd != -1 && bidBar.getTime() + 3600 * 1000 > tradingHoursEnd) {
				order.close();
				order.waitForUpdate(null);
				order = null;
				orderPerPair.put(instrument.name(), null);	
				dailyPnL.resetInstrumentDailyPnL(instrument, bidBar.getTime());
				return;				
			}*/
				
			order = openOrderProcessing(instrument, period, askBar, bidBar, order);
		}
		long tradingHoursEnd = TradingHours.tradingHoursEnd(instrument, bidBar.getTime());
		if (tradingHoursEnd != -1 && bidBar.getTime() + 3600 * 1000 > tradingHoursEnd) {
/*			if (order != null) {
				order.close();
				order.waitForUpdate(null);
			}*/
			dailyPnL.resetInstrumentDailyPnL(instrument, bidBar.getTime());
			return;
		}
		// no more entries if daily profit is OK or less then 1 hour before close
/*		if (dailyPnLvsRange > 0.5) {			
			lastTradingEvent = "Daily profit more then 50% of daily range - no trading until the end of the day";
			return;
		}	*/	
		// enable re-entry on the same bar !
		if (order == null) {
			newOrderProcessing(instrument, period, askBar, bidBar);
		}
	}

	protected void newOrderProcessing(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		IOrder order;
		// no open position
		TAEventDesc signal = null;
		for (ITradeSetup setup : tradeSetups) {
			if (setup.isTakeOverOnly())
				continue;
			
			signal = setup.checkEntry(instrument, period, askBar, bidBar, selectedFilter, lastTaValues);
			if (signal != null) {
				// must be before so also Mkt orders can have a chance to
				// write to non-null tradeLog !
				currentSetup = setup;
				String orderLabel = getOrderLabel(instrument, bidBar.getTime(),	signal.isLong ? "BUY" : "SELL");
				createTradeLog(instrument, period, askBar, OfferSide.ASK, orderLabel, signal.isLong, lastTaValues);
				if (TradingHours.tradingAllowed(dataService, bidBar.getTime(), period)) {
					double inTargetCurrency = FXUtils.convertByBar(BigDecimal.valueOf(selectedAmount), context.getAccount().getAccountCurrency(), selectedInstrument.getSecondaryJFCurrency(), selectedPeriod, OfferSide.BID, bidBar.getTime()).doubleValue();
					double amountToTrade = Math.round(inTargetCurrency / bidBar.getClose()) / (instrument.toString().contains(".CMD") ? 1e8 : 1e6); 
					order = currentSetup.submitOrder(orderLabel, instrument, signal.isLong, amountToTrade, bidBar, askBar);
					order.waitForUpdate(null);

					orderPerPair.put(instrument.name(), order);
					String logLine = new String(FXUtils.getFormatedBarTime(bidBar) + ": "
									+ (signal.isLong ? "long" : "short") + " entry with "
									+ currentSetup.getName() + ", order " + order.getLabel());
					console.getOut().println(logLine);
					log.print(logLine);

					double level = 0;
					if (signal.isLong)
						level = calcNextLongLevel(bidBar, instrument, chart.getMinPrice(), chart.getMaxPrice());
					else
						level = calcNextShortLevel(askBar, instrument, chart.getMinPrice(), chart.getMaxPrice());
					String textToShow = new String(orderCnt + "." + commentCnt++ + ": " + (signal.isLong ? "Long" : "Short") + " entry signal with " + currentSetup.getName());
					chart.showTradingEventOnGUI(textToShow, instrument, bidBar.getTime(), level);
				} else if (conf.getProperty("noWeekendPositions", "no").equals("yes")) {
					// put in the order in the "queue" to be submitted on Sunday
					orderWaitinginQueue = true;
					queueOrderLabel = orderLabel;
					queueOrderIsLong = signal.isLong;
					
					double level = 0;
					if (signal.isLong)
						level = calcNextLongLevel(bidBar, instrument, chart.getMinPrice(), chart.getMaxPrice());
					else
						level = calcNextShortLevel(askBar, instrument, chart.getMinPrice(), chart.getMaxPrice());
					String textToShow = new String(orderCnt + "." + commentCnt++ + ": " + (signal.isLong ? "Long" : "Short") + " entry signal with " + currentSetup.getName());
					chart.showTradingEventOnGUI(textToShow, instrument, bidBar.getTime(), level);
				}
				break;
			}
		}
	}

	protected void checkDayRanges(Instrument instrument, IBar askBar, IBar bidBar) throws JFException {
		dayRanges = new RangesStats(context.getSubscribedInstruments(), history).init(askBar, bidBar);
		dailyPnL = new DailyPnL(dayRanges, bidBar.getTime());
		
		for (ITradeSetup ts : tradeSetups)
			ts.addDayRanges(dayRanges);
		
		for (Instrument currI : dayRanges.keySet()) {
			InstrumentRangeStats currStats =  dayRanges.get(currI);
			if (currStats == null)
				continue;
			
			log.print("Day ranges data");
			log.print(currI.name()
					+ " - avg: " + FXUtils.df2.format(currStats.avgRange)
					+ "% / median: " + FXUtils.df2.format(currStats.medianRange)
					+ "% / max: " + FXUtils.df2.format(currStats.maxRange)
					+ "% / min: " + FXUtils.df2.format(currStats.minRange)
					+ "% / avg + 1 StDev: " + FXUtils.df2.format(currStats.avgRange + currStats.rangeStDev)
					+ "% (StDev = " + FXUtils.df2.format(currStats.rangeStDev)
					+ ", StDev vs. avg = " + FXUtils.df2.format(currStats.rangeStDev / currStats.avgRange) + ")",
					true);
		}
	}

	protected IOrder openOrderProcessing(Instrument instrument, Period period, IBar askBar, IBar bidBar, IOrder order) throws JFException {
		// go through all the other (non-current) setups and check whether
		// they should take over (their conditions fullfilled)
		ITradeSetup.EntryDirection signal = ITradeSetup.EntryDirection.NONE;
		boolean takeOver = false;
		if (!currentSetup.isTradeLocked(instrument, period, askBar, bidBar,	selectedFilter, order, lastTaValues)) {
			for (ITradeSetup setup : tradeSetups) {
				if (!setup.getName().equals(currentSetup.getName())) {
					signal = setup.checkTakeOver(instrument, period, askBar, bidBar, selectedFilter, lastTaValues);
					takeOver = (order.isLong() && signal.equals(ITradeSetup.EntryDirection.LONG) && takeOverRules.canTakeOver(currentSetup.getName(), setup.getName()))
							|| (!order.isLong()	&& signal.equals(ITradeSetup.EntryDirection.SHORT) && takeOverRules.canTakeOver(currentSetup.getName(),	setup.getName()));
					if (takeOver) {
						currentSetup = setup;
						String logLine = new String(FXUtils.getFormatedBarTime(bidBar) + ": " + signal.toString()
										+ " takeover by " + currentSetup.getName() + ", order " + order.getLabel());
						console.getOut().println(logLine);
						log.print(logLine);
						setup.takeTradingOver(order);
						
						double level = 0;
						if (order.isLong())
							level = calcNextLongLevel(bidBar, instrument, chart.getMinPrice(), chart.getMaxPrice());
						else
							level = calcNextShortLevel(askBar, instrument, chart.getMinPrice(), chart.getMaxPrice());
						String textToShow = new String(orderCnt + "." + commentCnt++ + ": " + "Trade takeover by " + currentSetup.getName());
						chart.showTradingEventOnGUI(textToShow, instrument, bidBar.getTime(), level);
						break;
					}
				}
			}
		}
		if (!takeOver) {
			if (order.getStopLossPrice() != 0.0)
				tradeLog.updateMaxRisk(order.getStopLossPrice());
			tradeLog.updateMaxLoss(bidBar, lastTaValues.get(FlexTASource.ATR).getDoubleValue());
			tradeLog.updateMaxProfit(bidBar);
			
			// check market situation by testing all available entry signals and TA events and send them to current setup for processing / reaction
			List<TAEventDesc> marketEvents = checkMarketEvents(instrument, period, askBar, bidBar, selectedFilter, lastTaValues);
			
			TAEventDesc maxTradeProfit = new TAEventDesc(TAEventType.MAX_TRADE_PROFIT_IN_PERC, TAEventDesc.MAX_TRADE_PROFIT_IN_PERC, instrument, false, askBar, bidBar, period);
			maxTradeProfit.pnlDayRangeRatio = dailyPnL.ratioPnLAvgRange(instrument);
			maxTradeProfit.avgPnLRange = dailyPnL.getInstrumentData(instrument).rangeStats.avgRange;
			maxTradeProfit.tradeProfitInPerc = tradeLog.calcMaxProfitInPerc();
			marketEvents.add(maxTradeProfit);
			
			currentSetup.inTradeProcessing(instrument, period, askBar, bidBar, selectedFilter, order, lastTaValues, marketEvents);
			if (currentSetup != null // trade can be closed in the previous method !
				&& !currentSetup.getLastTradingEvent().equals(lastTradingEvent)
				&& !currentSetup.getLastTradingEvent().equals("none")) {
				lastTradingEvent = currentSetup.getLastTradingEvent();
				double level = 0;
				if (order.isLong())
					level = calcNextLongLevel(bidBar, instrument, chart.getMinPrice(), chart.getMaxPrice());
				else
					level = calcNextShortLevel(askBar, instrument, chart.getMinPrice(), chart.getMaxPrice());
				String textToShow = new String(orderCnt + "." + commentCnt++ + ": " + currentSetup.getLastTradingEvent() + " with " + currentSetup.getName());
				chart.showTradingEventOnGUI(textToShow, instrument, bidBar.getTime(), level);
			}
			
			// inTradeProcessing might CLOSE the order, onMessage will set to null !
			order = orderPerPair.get(instrument.name());
			if (order != null) {
				ITradeSetup.EntryDirection exitSignal = currentSetup.checkExit(instrument, period, askBar, bidBar, selectedFilter, order, lastTaValues);
				if ((order.isLong() && exitSignal.equals(ITradeSetup.EntryDirection.LONG))
					|| (!order.isLong() && exitSignal.equals(ITradeSetup.EntryDirection.SHORT))) {
					double level = 0;
					if (exitSignal.equals(ITradeSetup.EntryDirection.LONG))
						level = calcNextLongLevel(bidBar, instrument, chart.getMinPrice(), chart.getMaxPrice());
					else
						level = calcNextShortLevel(askBar, instrument, chart.getMinPrice(), chart.getMaxPrice());
					String textToShow = new String(orderCnt + "." + commentCnt++ + ": " + (order.isLong() ? "Long " : "Short ") + "exit signal with " + currentSetup.getName());
					chart.showTradingEventOnGUI(textToShow, instrument, bidBar.getTime(), level);
	
					if (tradeLog != null)
						tradeLog.exitReason = new String("exit criteria");
					String logLine = new String(FXUtils.getFormatedBarTime(bidBar) + ": " + exitSignal.toString()
									+ "  exit signal by " + currentSetup.getName() + " setup, order " + order.getLabel());
					console.getOut().println(logLine);
					log.print(logLine);

					currentSetup.setLastTradingEvent("Closed due to exist signal");
					order.close();
					order.waitForUpdate(null);
					order = null;
					orderPerPair.put(instrument.name(), null);
				}
			}
		}
		return order;
	}

	private void incCommentLevelsCount() {
		for (Double commentLevel : commentLevelsCounts.keySet()) {
			Integer currCnt = commentLevelsCounts.get(commentLevel);
			commentLevelsCounts.put(commentLevel, new Integer(++currCnt));
		}
	}

	private void removeOldCommentLevel(int defineOld) {
		SortedMap<Double, Integer> filtered = new TreeMap<Double, Integer>();
		for (Double commentLevel : commentLevelsCounts.keySet()) {
			Integer currCnt = commentLevelsCounts.get(commentLevel);
			if (currCnt < defineOld)
				filtered.put(commentLevel, currCnt);
		}
		commentLevelsCounts = filtered;
	}
	
	private double calcNextShortLevel(IBar askBar, Instrument instrument, double minPrice, double maxPrice) {
		Double levels[] = new Double[commentLevelsCounts.size()];
		levels = commentLevelsCounts.keySet().toArray(levels);
		// COMMENT_OFFSET_PERC / Math.pow(10, instrument.getPipScale());
		double
			bBands[][] = lastTaValues.get(FlexTASource.BBANDS).getDa2DimValue(),
			offset = 15 / (SCREEN_H / (maxPrice- minPrice)),
			levelToCheck = (askBar.getHigh() > bBands[0][0] ? askBar.getHigh() : bBands[0][0]) + offset;
		boolean overLapFound = false;
		for (int i = 0; i < levels.length && !overLapFound; i++) {
			double currLevel = levels[i].doubleValue();
			if ((levelToCheck + offset >= currLevel 
				&& levelToCheck + offset <= currLevel + offset)
				|| 
				(levelToCheck >= currLevel 
				&& levelToCheck <= currLevel + offset)) {
				// overlap found. Now traverse the array backwards to find first free slot in levels
				overLapFound = true;
				boolean freeLevelFound = false;
				// in case it never enters the loop
				levelToCheck = levels[i].doubleValue() + offset;
				for (int j = i; j < levels.length - 1 && !freeLevelFound; j++) {
					double curr2ndLevel = levels[j + 1];
					levelToCheck = levels[j].doubleValue() + offset;
					freeLevelFound = 
							!((levelToCheck + offset >= curr2ndLevel 
								&& levelToCheck + offset <= curr2ndLevel + offset)
							|| (levelToCheck >= curr2ndLevel 
								&& levelToCheck <= curr2ndLevel + offset)); 
 
				}
			}
		}
		commentLevelsCounts.put(new Double(levelToCheck), new Integer(1));
		return levelToCheck;
	}

	private double calcNextLongLevel(IBar bidBar, Instrument instrument, double minPrice, double maxPrice) {	
		Double levels[] = new Double[commentLevelsCounts.size()];
		levels = commentLevelsCounts.keySet().toArray(levels);
		double
			bBands[][] = lastTaValues.get(FlexTASource.BBANDS).getDa2DimValue(),
			offset = 15 / (SCREEN_H / (maxPrice - minPrice)),
			levelToCheck = (bidBar.getLow() < bBands[2][0] ? bidBar.getLow() : bBands[2][0]) - offset;
		boolean overLapFound = false;
		for (int i = 0; i < levels.length && !overLapFound; i++) {
			double currLevel = levels[i].doubleValue();
			if ((levelToCheck + offset >= currLevel 
				&& levelToCheck + offset <= currLevel + offset)
				|| 
				(levelToCheck >= currLevel 
				&& levelToCheck <= currLevel + offset)) {
				// overlap found. Now traverse the array backwards to find first free slot in levels
				overLapFound = true;
				boolean freeLevelFound = false;
				// in case it never enters the loop
				levelToCheck = levels[i].doubleValue() - offset;
				for (int j = i; j > 0 && !freeLevelFound; j--) {
					double curr2ndLevel = levels[j - 1];
					levelToCheck = levels[j].doubleValue() - offset;
					freeLevelFound = 
							!((levelToCheck + offset >= curr2ndLevel 
								&& levelToCheck + offset <= curr2ndLevel + offset)
							|| (levelToCheck >= curr2ndLevel 
								&& levelToCheck <= curr2ndLevel + offset)); 
 
				}
			}
		}
		commentLevelsCounts.put(new Double(levelToCheck), new Integer(1));
		return levelToCheck;
	}

	private void createTradeLog(Instrument instrument, Period period, IBar bar, OfferSide side, String orderLabel, boolean isLong, Map<String, FlexLogEntry> taValues) {
		tradeLog = new TradeLog(orderLabel, isLong, currentSetup.getName(), bar.getTime(), 0, 0, 0);

		//Trend.TREND_STATE entryTrendID = trend.getTrendState(instrument, period, side, IIndicators.AppliedPrice.CLOSE, bar.getTime());
		addLatestTAValues(taValues, isLong);
		/*
		tradeLog.addLogEntry(new FlexLogEntry("TrendID", new String(
				entryTrendID.toString())));
		tradeLog.addLogEntry(new FlexLogEntry("ma200Highest", new String(
				ma200Highest ? "ma200Highest" : "no")));
		tradeLog.addLogEntry(new FlexLogEntry("ma200Lowest", new String(
				ma200Lowest ? "ma200Lowest" : "no")));
		tradeLog.addLogEntry(new FlexLogEntry("generalTrendStrength",
				new Double(trend.getMAsMaxDiffStDevPos(instrument, period,
						side, IIndicators.AppliedPrice.CLOSE, bar.getTime(),
						FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df2));
		tradeLog.addLogEntry(new FlexLogEntry("generalTrendStrengthPerc",
				new Double(trend.getMAsMaxDiffPercentile(instrument, period,
						side, IIndicators.AppliedPrice.CLOSE, bar.getTime(),
						FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df1));
		if (entryTrendID.equals(Trend.TREND_STATE.UP_STRONG)) {
			tradeLog.addLogEntry(new FlexLogEntry("trendingTrendStrength",
					new Double(trend.getUptrendMAsMaxDifStDevPos(instrument,
							period, side, IIndicators.AppliedPrice.CLOSE,
							bar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS)),
					FXUtils.df2));
			tradeLog.addLogEntry(new FlexLogEntry("trendingTrendStrengthPerc",
					new Double(trend.getUptrendMAsMaxDiffPercentile(instrument,
							period, side, IIndicators.AppliedPrice.CLOSE,
							bar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS)),
					FXUtils.df1));
		} else if (entryTrendID.equals(Trend.TREND_STATE.DOWN_STRONG)) {
			tradeLog.addLogEntry(new FlexLogEntry("trendingTrendStrength",
					new Double(trend.getDowntrendMAsMaxDifStDevPos(instrument,
							period, side, IIndicators.AppliedPrice.CLOSE,
							bar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS)),
					FXUtils.df2));
			tradeLog.addLogEntry(new FlexLogEntry("trendingTrendStrengthPerc",
					new Double(trend.getDowntrendMAsMaxDiffPercentile(
							instrument, period, side,
							IIndicators.AppliedPrice.CLOSE, bar.getTime(),
							FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df1));
		} else {
			tradeLog.addLogEntry(new FlexLogEntry("trendingTrendStrength",
					new Double(0.0), FXUtils.df2));
			tradeLog.addLogEntry(new FlexLogEntry("trendingTrendStrengthPerc",
					new Double(0.0), FXUtils.df1));
		}
		tradeLog.addLogEntry(new FlexLogEntry("bBandsSqueeze",
				new Double(vola.getBBandsSqueeze(instrument, period, side,
						bar.getTime(), 20)), FXUtils.df1));
		tradeLog.addLogEntry(new FlexLogEntry("bBandsSqueezePerc", new Double(
				vola.getBBandsSqueezePercentile(instrument, period, side,
						IIndicators.AppliedPrice.CLOSE, Filter.WEEKENDS,
						bar.getTime(), 20, FXUtils.YEAR_WORTH_OF_4H_BARS)),
				FXUtils.df1));
		tradeLog.addLogEntry(new FlexLogEntry("highPos", new Double(channel
				.priceChannelPos(instrument, period, side, bar.getTime(),
						bar.getHigh())), FXUtils.df1));
		tradeLog.addLogEntry(new FlexLogEntry("lowPos", new Double(channel
				.priceChannelPos(instrument, period, side, bar.getTime(),
						bar.getLow())), FXUtils.df1));
		tradeLog.addLogEntry(new FlexLogEntry("closePos", new Double(channel
				.priceChannelPos(instrument, period, side, bar.getTime(),
						bar.getClose())), FXUtils.df1));
		tradeLog.addLogEntry(new FlexLogEntry("barSizeStat", new Double(candles
				.barLengthStatPos(instrument, period, side, bar,
						FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df2));
		tradeLog.addLogEntry(new FlexLogEntry("bodySizePerc", new Double(
				candles.barsBodyPerc(bar)), FXUtils.df1));
		tradeLog.addLogEntry(new FlexLogEntry("fastSMI", new Double(fastSMI),
				FXUtils.df1));
		tradeLog.addLogEntry(new FlexLogEntry("slowSMI", new Double(slowSMI),
				FXUtils.df1));
		tradeLog.addLogEntry(new FlexLogEntry("SMIcross", new String(
				fastSMI > slowSMI ? "fast" : "slow")));
			*/
	}

	protected void addLatestTAValues(Map<String, FlexLogEntry> taValues, boolean isLong) {
		tradeLog.addLogEntry(new FlexLogEntry("Regime", FXUtils.getRegimeString((Trend.TREND_STATE)taValues.get(FlexTASource.TREND_ID).getTrendStateValue(), 
				taValues.get(FlexTASource.MAs_DISTANCE_PERC).getDoubleValue(),
				(Trend.FLAT_REGIME_CAUSE)taValues.get(FlexTASource.FLAT_REGIME).getValue(), 
				taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue(), taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue())));
		if (currentSetup != null && currentSetup.getName().equals("Flat")) {
			if (isLong) {
				if (taValues.get(FlexTASource.BULLISH_CANDLES) != null)
					taValues.replace(FlexTASource.BULLISH_CANDLES, new FlexLogEntry(FlexTASource.BULLISH_CANDLES, ((FlatTradeSetup) currentSetup).getLastLongSignal()));
				else
					taValues.put(FlexTASource.BULLISH_CANDLES, new FlexLogEntry(FlexTASource.BULLISH_CANDLES, ((FlatTradeSetup) currentSetup).getLastLongSignal()));
			} else {
				if (taValues.get(FlexTASource.BEARISH_CANDLES) != null)
					taValues.replace(FlexTASource.BEARISH_CANDLES, new FlexLogEntry(FlexTASource.BEARISH_CANDLES, ((FlatTradeSetup) currentSetup).getLastShortSignal()));
				else 
					taValues.put(FlexTASource.BEARISH_CANDLES, new FlexLogEntry(FlexTASource.BEARISH_CANDLES, ((FlatTradeSetup) currentSetup).getLastShortSignal()));
			}
		}
		tradeLog.addTAData(taValues);
	}

	private void submitOrderFromQueue(Instrument instrument, IBar bidBar, IBar askBar) throws JFException {
		if (orderWaitinginQueue) {
			IOrder order = currentSetup.submitOrder(queueOrderLabel, instrument, queueOrderIsLong, selectedAmount, bidBar, askBar);
			order.waitForUpdate(null);
	
			orderPerPair.put(instrument.name(), order);
			String logLine = new String(FXUtils.getFormatedBarTime(bidBar) + ": "
							+ (queueOrderIsLong ? "long" : "short") + " entry with "
							+ currentSetup.getName() + ", order " + order.getLabel());
			console.getOut().println(logLine);
			log.print(logLine);	
			
			queueOrderLabel = null;
			orderWaitinginQueue = false;
		}
	}

	private List<TAEventDesc> checkMarketEvents(Instrument instrument,	Period period, IBar askBar, IBar bidBar, Filter filter, Map<String, FlexLogEntry> taValues) throws JFException {
		List<TAEventDesc> result = new ArrayList<TAEventDesc>();
		for (ITradeSetup setup : tradeSetups) {
			TAEventDesc signal = setup.checkEntry(instrument, period, askBar, bidBar, filter, taValues);
			if (signal != null) {
				result.add(signal);
			}
		}
		for (ITAEvent taEvent : taEvents) {
			TAEventDesc event = taEvent.checkEvent(instrument, period, askBar, bidBar, filter);
			if (event != null) {
				result.add(event);
			}
		}
		TAEventDesc pnlRangeRatio = new TAEventDesc(TAEventType.DAILY_PNL_INFO, "PnLDayRangeRatio", instrument, false, askBar, bidBar, null);
		pnlRangeRatio.pnlDayRangeRatio = dailyPnL.ratioPnLAvgRange(instrument);
		pnlRangeRatio.avgPnLRange = dailyPnL.getInstrumentData(instrument).rangeStats.avgRange;
		result.add(pnlRangeRatio);
		return result;
	}

	public void onMessage(IMessage message) throws JFException {
		if (message.getType().equals(IMessage.Type.CONNECTION_STATUS)
			|| message.getType().equals(IMessage.Type.NOTIFICATION)
			|| message.getType().equals(IMessage.Type.INSTRUMENT_STATUS)
			|| message.getType().equals(IMessage.Type.ORDER_CHANGED_REJECTED)
			|| message.getType().equals(IMessage.Type.ORDER_CLOSE_REJECTED)
			|| message.getType().equals(IMessage.Type.ORDER_FILL_REJECTED)
			|| message.getType().equals(IMessage.Type.ORDER_SUBMIT_REJECTED)
			|| message.getType().equals(IMessage.Type.ORDERS_MERGE_REJECTED)) {
			Set<IMessage.Reason> reasons = message.getReasons();
			String reasonsStr = new String();
			for (IMessage.Reason r : reasons) {
				reasonsStr += r.toString();
			}
			log.print("There is a problem at "
					+ FXUtils.getFormatedTimeGMT(message.getCreationTime())
					+ " ! Message: " + message.getType().toString() 
					+ ", content: "+ message.getContent()
					+ ", reasons: " + reasonsStr);
			log.close();
			statsLog.close();
			if (barStatsLog != null)
				barStatsLog.close();
			System.exit(2);
		}
		if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {
			boolean addLastTradingEvent = false;
			if (currentSetup != null // trade can be closed in the previous method !
				&& !currentSetup.getLastTradingEvent().equals(lastTradingEvent)
				&& !currentSetup.getLastTradingEvent().equals("none")) {
				lastTradingEvent = currentSetup.getLastTradingEvent();
				addLastTradingEvent = true;
			}
			String guiLabel = new String();
			if (message.getOrder().getState().equals(IOrder.State.CANCELED))
				guiLabel = (message.getOrder().isLong() ? "Long " : "Short ") + currentSetup.getName() + " order canceled" + (addLastTradingEvent ? " (" + lastTradingEvent + ")" : "");
			else {
				// update total daily profit in %
				dailyPnL.updateInstrumentPnL(message.getOrder().getInstrument(), message.getOrder());
				double dailyPnLRangeRatio = dailyPnL.ratioPnLAvgRange(message.getOrder().getInstrument());
				if (dailyPnLRangeRatio > 0.75) {
					log.print("Total daily PnL > 0.75 x avg. daily range, no more trading ! (" 
							+ FXUtils.df2.format(dailyPnLRangeRatio) + ")");
				}

				guiLabel = (message.getOrder().isLong() ? "Long " : "Short ") + currentSetup.getName() 
					+ " trade closed" 
					+ (addLastTradingEvent ? " (" + lastTradingEvent + ")" : "")
					+ (dailyPnL.ratioPnLAvgRange(message.getOrder().getInstrument()) > 1 ? " Daily profit goal reached - NO more trading !" : "");
			}
			
			double level = 0;
			if (message.getOrder().isLong())
				level = calcNextLongLevel(history.getBar(message.getOrder().getInstrument(), selectedPeriod, OfferSide.BID, 1), message.getOrder().getInstrument(), chart.getMinPrice(), chart.getMaxPrice());
			else
				level = calcNextShortLevel(history.getBar(message.getOrder().getInstrument(), selectedPeriod, OfferSide.ASK, 1), message.getOrder().getInstrument(), chart.getMinPrice(), chart.getMaxPrice());
			String textToShow = new String(orderCnt + "." + commentCnt++ + ": " + guiLabel + " with " + currentSetup.getName());
			chart.showTradingEventOnGUI(textToShow, message.getOrder().getInstrument(), history.getBar(message.getOrder().getInstrument(), selectedPeriod, OfferSide.BID, 1).getTime(), level);
			
			// might be cancel of unfilled order or SL close
			Set<IMessage.Reason> reasons = message.getReasons();
			String reasonsStr = new String();
			for (IMessage.Reason r : reasons) {
				reasonsStr += r.toString();
			}

			if (tradeLog != null) {
				tradeLog.exitTime = message.getCreationTime();
				if (tradeLog.exitReason == null)
					tradeLog.exitReason = reasonsStr;
				tradeLog.PnL = message.getOrder().getProfitLossInPips();
				
				tradeLog.addLogEntry(new FlexLogEntry("exitValues", "EXIT"));
				tradeLog.addLogEntry(new FlexLogEntry("exitSetup", currentSetup.getName()));
				tradeLog.addLogEntry(new FlexLogEntry("lastTradingAction", currentSetup.getLastTradingEvent()));
				addLatestTAValues(lastTaValues, message.getOrder().isLong());
				
				if (!headerPrinted) {
					headerPrinted = true;
					String header = tradeLog.prepareHeader(); 
					statsLog.print(header);
					statsLog.printXlsCSVLine(header);
				}
				String logLine = tradeLog.prepareExitReport(message.getOrder().getInstrument());
				console.getOut().println(logLine);					
				statsLog.print(logLine);
				//statsLog.printXlsCSVLine(logLine);
				statsLog.printXlsValuesFlex(tradeLog.prepareExitReportAsList(message.getOrder().getInstrument()));
			}
			List<String> tradeHistory = currentSetup.getTradeHistory();
			log.print("Trade: " + message.getOrder().getLabel() + " action history:");
			for (String tradeHistoryEntry : tradeHistory) {
				log.print(tradeHistoryEntry);
			}

			orderPerPair.put(message.getOrder().getInstrument().name(), null);
			for (ITradeSetup setup : tradeSetups)
				setup.afterTradeReset(message.getOrder().getInstrument());
			currentSetup = null;
			commentCnt = 1;
		}
		if (message.getType().equals(IMessage.Type.ORDER_FILL_OK)) {
			tradeLog.setOrder(message.getOrder()); // needed for Mkt orders, tradeLog was created without order being created
			tradeLog.fillTime = message.getCreationTime();
			tradeLog.fillPrice = message.getOrder().getOpenPrice();
			currentSetup.addTradeHistoryEntry(new String("Order filled at " + FXUtils.getFormatedTimeGMT(tradeLog.fillTime)
					+ (message.getOrder().getStopLossPrice() != 0.0 ? "; stop loss set at " + FXUtils.df5.format(message.getOrder().getStopLossPrice()) 
					: "; no stop loss set")));
		}
	}

	public void onStop() throws JFException {
		log.close();
		statsLog.close();
		if (barStatsLog != null)
			barStatsLog.close();
		File tradeTestRunningSignal = new File("strategyTestRunning.bin");
		if (tradeTestRunningSignal.exists())
			tradeTestRunningSignal.delete();
	}

	public void onAccount(IAccount account) throws JFException {
	}

	public void onTick(Instrument instrument, ITick tick) throws JFException {
	}

	String getOrderLabel(Instrument instrument, long time, String direction) {
		return new String("FlatCascTest_" + instrument.name() + "_"
				+ FXUtils.getFormatedTimeGMTforID(time) + "_" + ++orderCnt
				+ "_" + direction);
	}

}
