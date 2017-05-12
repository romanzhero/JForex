package jforex.strategies;

import java.io.File;
import java.math.BigDecimal;
import java.util.*;

import com.dukascopy.api.*;
import jforex.utils.DailyPnL;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;
import jforex.utils.Logger;
import jforex.utils.RangesStats;
import jforex.utils.TradingHours;
import jforex.utils.FXUtils.PreviousTrade;
import jforex.utils.RangesStats.InstrumentRangeStats;
import jforex.events.CandleMomentumEvent;
import jforex.events.ITAEvent;
import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.logging.TradeLog;
import jforex.techanalysis.Trend;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.FlexTAValue;
import jforex.trades.*;
import jforex.trades.flat.FlatStrongTradeSetup;
import jforex.trades.flat.FlatTradeSetup;
import jforex.trades.momentum.SmiTradeSetup;
import jforex.trades.trend.SmaCrossTradeSetup;
import jforex.trades.trend.SmaSoloTradeSetup;
import jforex.trades.trend.SmaTradeSetup;

public class MultiPairCasc implements IStrategy {
	@Configurable(value = "Period", description = "Choose the time frame")
	public Period 
		selectedPeriod = Period.FIVE_MINS,
		orderSubmitPeriod = Period.ONE_MIN;

	@Configurable(value = "Filter", description = "Choose the candle filter")
	public Filter selectedFilter = Filter.ALL_FLATS;

	@Configurable(value = "Amount", stepSize = 0.0001, description = "Choose amount (step size 0.0001)")
	public double selectedAmount = 100000;

	protected String reportDir = null;

	private IEngine engine;
	private IConsole console;
	private IHistory history;
	private IIndicators indicators;
	private IDataService dataService;

	private List<ITradeSetup> tradeSetups = new ArrayList<ITradeSetup>();
	private List<ITAEvent> taEvents = new ArrayList<ITAEvent>();
	private ITakeOverRules takeOverRules = new SmiSmaTakeOverRules();

	private FlexTASource taSource = null;
	private Map<String, FlexTAValue> lastTaValues = null;
	
	private Map<Instrument, InstrumentRangeStats> dayRanges = null;
	private DailyPnL dailyPnL = null;	
	
	protected Map<String, PairTradeData> pairsTradeData = new HashMap<String, PairTradeData>();
	protected Set<Instrument> 
		instrumentsToTrade = new HashSet<Instrument>(),
		instrumentsToIgnore = new HashSet<Instrument>();
	private Logger 
		log = null,
		statsLog = null;

	private boolean 
		orderWaitinginQueue = false,
		queueOrderIsLong;
	private String queueOrderLabel = null;
	private boolean headerPrinted = false;
	private Properties conf = null;

	private IContext context;
	
	protected class PairTradeData {
		public Instrument pair;
		public TradeLog tradeLog = null;
		public IOrder positionOrder = null;
		public ITradeSetup currentSetup = null;
		public boolean 
			waitingOrder = false, 
			openPosition = false;
		public int 
			orderCounter = 0, 
			noOfBarsInTrade = 0;
		PreviousTrade prevTrade = PreviousTrade.NONE;

		public PairTradeData(Instrument pair) {
			super();
			this.pair = pair;
		}

		protected void resetVars() {
			waitingOrder = false;
			tradeLog = null;
			noOfBarsInTrade = 0;
			currentSetup = null;
			positionOrder = null;
		}
	}
	

	public MultiPairCasc(String reportDir) {
		super();
		this.reportDir = reportDir;
	}
	
	public MultiPairCasc(Properties p) {
		super();
		this.reportDir = p.getProperty("reportDirectory", ".");
		conf = p;
	}

	public void onStart(IContext context) throws JFException {
		this.engine = context.getEngine();
		this.console = context.getConsole();
		this.history = context.getHistory();
		this.indicators = context.getIndicators();
		this.context = context;
		
		FXUtils.parseInstrumentsWithTimeFrames(conf.getProperty("pairsToCheck"), instrumentsToTrade);
		Set<Instrument> pairs = context.getSubscribedInstruments();
		for (Instrument i : pairs) {
			if (instrumentsToTrade.contains(i))
				pairsTradeData.put(i.toString(), new PairTradeData(i));
			else
				instrumentsToIgnore.add(i);
		}
		
		FXUtils.setProfitLossHelper(context.getAccount().getAccountCurrency(), history);

		dataService = context.getDataService();

		taSource = new FlexTASource(indicators, history, selectedFilter);

		log = new Logger(reportDir + "//Casc_report_" + FXUtils.getFileTimeStamp(System.currentTimeMillis()) + ".txt");
		statsLog = new Logger(reportDir + "//Casc_stat_report_" + FXUtils.getFileTimeStamp(System.currentTimeMillis()) + ".txt");

		if (conf.getProperty("FlatStrongSetup", "no").equals("yes"))
			tradeSetups.add(new FlatStrongTradeSetup(engine, context, history, true));
		if (conf.getProperty("FlatSetup", "no").equals("yes"))
			tradeSetups.add(new FlatTradeSetup(engine, context, true));
		//tradeSetups.add(new PUPBSetup(indicators, history, engine));
		if (conf.getProperty("SMISetup", "no").equals("yes"))
			tradeSetups.add(new SmiTradeSetup(engine, context, false, 30.0, 30.0));
		if (conf.getProperty("TrendIDFollowCrossSetup", "no").equals("yes"))
			tradeSetups.add(new SmaCrossTradeSetup(engine, context, instrumentsToTrade, true, false, 30.0, 25.0, false));
		if (conf.getProperty("TrendIDFollowSetup", "no").equals("yes"))
			tradeSetups.add(new SmaTradeSetup(indicators, context, history, engine, instrumentsToTrade, true, false, 30.0, 30.0, false));
		else if (conf.getProperty("TrendIDFollowSoloSetup", "no").equals("yes"))
			tradeSetups.add(new SmaSoloTradeSetup(engine, context, instrumentsToTrade, true, false, 30.0, 30.0, false));
		
		//taEvents.add(new LongCandlesEvent(indicators, history));
		//taEvents.add(new ShortCandlesEvent(indicators, history));
		taEvents.add(new CandleMomentumEvent(indicators, history));
		
	}

	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (instrumentsToIgnore.contains(instrument))
			return;

		if (period.equals(orderSubmitPeriod)
			&& tradingAllowed(bidBar.getTime(), period)
			&& !(bidBar.getClose() == bidBar.getOpen() && bidBar.getClose() == bidBar.getHigh() && bidBar.getClose() == bidBar.getLow())) {
			submitOrderFromQueue(instrument, bidBar, askBar);
			return;
		}
		
		if (!period.equals(selectedPeriod)
			|| (bidBar.getClose() == bidBar.getOpen() && bidBar.getClose() == bidBar.getHigh() && bidBar.getClose() == bidBar.getLow()))
			return;
		
		PairTradeData currPairData = pairsTradeData.get(instrument.toString());
		IOrder order = currPairData.positionOrder;		
	
		checkDayRanges(instrument, askBar, bidBar);
		
		lastTaValues = taSource.calcTAValues(instrument, period, askBar, bidBar);
		
		double dailyPnLvsRange = dailyPnL.ratioPnLAvgRange(instrument);
		if (order != null) {
			// there is an open order, might be pending (waiting) or filled !
			if (order.getState().equals(IOrder.State.OPENED)
				&& dailyPnLvsRange > 0.75) {
				// cancel pending order if daily profit OK and skip further trading
				order.close();
				order.waitForUpdate(null);
				order = null;
				currPairData.positionOrder = null;	
				dailyPnL.resetInstrumentDailyPnL(instrument, bidBar.getTime());
				return;
			}
			long tradingHoursEnd = TradingHours.tradingHoursEnd(instrument, bidBar.getTime());
			if (tradingHoursEnd != -1 && bidBar.getTime() + 3600 * 1000 > tradingHoursEnd) {
				order.close();
				order.waitForUpdate(null);
				order = null;
				currPairData.positionOrder = null;	
				dailyPnL.resetInstrumentDailyPnL(instrument, bidBar.getTime());
				return;				
			}
				
			order = openOrderProcessing(instrument, period, askBar, bidBar, order);
		}
		// no more entries if daily profit is OK or less then 1 hour before close
		if (dailyPnLvsRange > 0.75) {			
			return;
		}
		long tradingHoursEnd = TradingHours.tradingHoursEnd(instrument, bidBar.getTime());
		if (tradingHoursEnd != -1 && bidBar.getTime() + 3600 * 1000 > tradingHoursEnd){
			dailyPnL.resetInstrumentDailyPnL(instrument, bidBar.getTime());
			return;
		}
		
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
			signal = setup.checkEntry(instrument, period, askBar, bidBar, selectedFilter, lastTaValues);
			if (signal != null) {
				// must be before so also Mkt orders can have a chance to
				// write to non-null tradeLog !
				PairTradeData currPairData = pairsTradeData.get(instrument.toString());
				currPairData.currentSetup = setup;
				String orderLabel = getOrderLabel(instrument, bidBar.getTime(),	signal.isLong ? "BUY" : "SELL");
				createTradeLog(instrument, period, askBar, OfferSide.ASK, orderLabel, signal.isLong, lastTaValues);
				if (tradingAllowed(bidBar.getTime(), period)) {
					double inTargetCurrency = FXUtils.convertByBar(BigDecimal.valueOf(selectedAmount), context.getAccount().getAccountCurrency(), instrument.getSecondaryJFCurrency(), selectedPeriod, OfferSide.BID, bidBar.getTime()).doubleValue();
					double amountToTrade = Math.round(inTargetCurrency / bidBar.getClose()) / (instrument.toString().contains(".CMD") ? 1e8 : 1e6); 
					order = currPairData.currentSetup.submitOrder(orderLabel, instrument, signal.isLong, amountToTrade, bidBar, askBar);
					order.waitForUpdate(null);

					currPairData.positionOrder = order;
					String logLine = new String(FXUtils.getFormatedBarTime(bidBar) + ": "
									+ (signal.isLong ? "long" : "short") + " entry with "
									+ currPairData.currentSetup.getName() + ", order " + order.getLabel());
					console.getOut().println(logLine);
					log.print(logLine);
					
				} else {
					// put in the order in the "queue" to be submitted on Sunday
					orderWaitinginQueue = true;
					queueOrderLabel = orderLabel;
					queueOrderIsLong = signal.isLong;
					
				}
				break;
			}
		}
	}

	protected void checkDayRanges(Instrument instrument, IBar askBar, IBar bidBar) throws JFException {
		if (dayRanges == null) {
			dayRanges = new RangesStats(context.getSubscribedInstruments(), history).init(askBar, bidBar);
			dailyPnL = new DailyPnL(dayRanges, bidBar.getTime());
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
	}

	protected IOrder openOrderProcessing(Instrument instrument, Period period, IBar askBar, IBar bidBar, IOrder order) throws JFException {
		// go through all the other (non-current) setups and check whether
		// they should take over (their conditions fullfilled)
		PairTradeData currPairData = pairsTradeData.get(instrument.toString());

		ITradeSetup.EntryDirection signal = ITradeSetup.EntryDirection.NONE;
		boolean takeOver = false;
		if (!currPairData.currentSetup.isTradeLocked(instrument, period, askBar, bidBar, selectedFilter, order, lastTaValues)) {
			for (ITradeSetup setup : tradeSetups) {
				if (!setup.getName().equals(currPairData.currentSetup.getName())) {
					signal = setup.checkTakeOver(instrument, period, askBar, bidBar, selectedFilter, lastTaValues);
					takeOver = (order.isLong() && signal.equals(ITradeSetup.EntryDirection.LONG) && takeOverRules.canTakeOver(currPairData.currentSetup.getName(), setup.getName()))
							|| (!order.isLong()	&& signal.equals(ITradeSetup.EntryDirection.SHORT) && takeOverRules.canTakeOver(currPairData.currentSetup.getName(),	setup.getName()));
					if (takeOver) {
						currPairData.currentSetup = setup;
						String logLine = new String(FXUtils.getFormatedBarTime(bidBar) + ": " + signal.toString()
										+ " takeover by " + currPairData.currentSetup.getName() + ", order " + order.getLabel());
						console.getOut().println(logLine);
						log.print(logLine);
						setup.takeTradingOver(order);
						break;
					}
				}
			}
		}
		if (!takeOver) {
			// check market situation by testing all available entry signals and TA events and send them to current setup for processing / reaction
			List<TAEventDesc> marketEvents = checkMarketEvents(instrument, period, askBar, bidBar, selectedFilter, lastTaValues);
			currPairData.currentSetup.inTradeProcessing(instrument, period, askBar, bidBar, selectedFilter, order, lastTaValues, marketEvents);
			
			// inTradeProcessing might CLOSE the order, onMessage will set to null !	
			if (currPairData.positionOrder != null) {
				if (order.getStopLossPrice() != 0.0)
					currPairData.tradeLog.updateMaxRisk(order.getStopLossPrice());
				currPairData.tradeLog.updateMaxLoss(bidBar, lastTaValues.get(FlexTASource.ATR).getDoubleValue());
				currPairData.tradeLog.updateMaxProfit(bidBar);
				ITradeSetup.EntryDirection exitSignal = currPairData.currentSetup.checkExit(instrument, period, askBar, bidBar, selectedFilter, order, lastTaValues);
				if ((order.isLong() && exitSignal.equals(ITradeSetup.EntryDirection.LONG))
					|| (!order.isLong() && exitSignal.equals(ITradeSetup.EntryDirection.SHORT))) {
					
					if (currPairData.tradeLog != null)
						currPairData.tradeLog.exitReason = new String("exit criteria");
					String logLine = new String(FXUtils.getFormatedBarTime(bidBar) + ": " + exitSignal.toString()
									+ "  exit signal by " + currPairData.currentSetup.getName() + " setup, order " + order.getLabel());
					console.getOut().println(logLine);
					log.print(logLine);

					order.close();
					order.waitForUpdate(null);
					order = null;
					currPairData.positionOrder = null;
				}
			}
		}
		return order;
	}

	private void createTradeLog(Instrument instrument, Period period, IBar bar, OfferSide side, String orderLabel, boolean isLong, Map<String, FlexTAValue> taValues) {
		PairTradeData currPairData = pairsTradeData.get(instrument.toString());
		currPairData.tradeLog = new TradeLog(orderLabel, isLong, currPairData.currentSetup.getName(), bar.getTime(), 0, 0, 0);

		//Trend.TREND_STATE entryTrendID = trend.getTrendState(instrument, period, side, IIndicators.AppliedPrice.CLOSE, bar.getTime());
		addLatestTAValues(instrument, taValues, isLong);
	}

	protected void addLatestTAValues(Instrument instrument, Map<String, FlexTAValue> taValues, boolean isLong) {
		PairTradeData currPairData = pairsTradeData.get(instrument.toString());
	
		currPairData.tradeLog.addLogEntry(new FlexLogEntry("Regime", FXUtils.getRegimeString((Trend.TREND_STATE)taValues.get(FlexTASource.TREND_ID).getTrendStateValue(), (Trend.FLAT_REGIME_CAUSE)taValues.get(FlexTASource.FLAT_REGIME).getValue(), 
				taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue(), taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue())));
		if (currPairData.currentSetup != null && currPairData.currentSetup.getName().equals("Flat")) {
			if (isLong) {
				if (taValues.get(FlexTASource.BULLISH_CANDLES) != null)
					taValues.replace(FlexTASource.BULLISH_CANDLES, new FlexTAValue(FlexTASource.BULLISH_CANDLES, ((FlatTradeSetup)currPairData.currentSetup).getLastLongSignal()));
				else
					taValues.put(FlexTASource.BULLISH_CANDLES, new FlexTAValue(FlexTASource.BULLISH_CANDLES, ((FlatTradeSetup)currPairData.currentSetup).getLastLongSignal()));
			} else {
				if (taValues.get(FlexTASource.BEARISH_CANDLES) != null)
					taValues.replace(FlexTASource.BEARISH_CANDLES, new FlexTAValue(FlexTASource.BEARISH_CANDLES, ((FlatTradeSetup)currPairData.currentSetup).getLastShortSignal()));
				else 
					taValues.put(FlexTASource.BEARISH_CANDLES, new FlexTAValue(FlexTASource.BEARISH_CANDLES, ((FlatTradeSetup)currPairData.currentSetup).getLastShortSignal()));
			}
		}
		for (Map.Entry<String, FlexTAValue> curr : taValues.entrySet()) {
			FlexTAValue taValue = curr.getValue();
			currPairData.tradeLog.addLogEntry(taValue);
		}
	}

	private void submitOrderFromQueue(Instrument instrument, IBar bidBar, IBar askBar) throws JFException {
		if (orderWaitinginQueue) {
			PairTradeData currPairData = pairsTradeData.get(instrument.toString());
			IOrder order = currPairData.currentSetup.submitOrder(queueOrderLabel, instrument, queueOrderIsLong, selectedAmount, bidBar, askBar);
			order.waitForUpdate(null);
			
			currPairData.positionOrder = order;
			String logLine = new String(FXUtils.getFormatedBarTime(bidBar) + ": "
							+ (queueOrderIsLong ? "long" : "short") + " entry with "
							+ currPairData.currentSetup.getName() + ", order " + order.getLabel());
			console.getOut().println(logLine);
			log.print(logLine);	
			
			queueOrderLabel = null;
			orderWaitinginQueue = false;
		}
	}

	private List<TAEventDesc> checkMarketEvents(Instrument instrument,	Period period, IBar askBar, IBar bidBar, Filter filter, Map<String, FlexTAValue> taValues) throws JFException {
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
		TAEventDesc pnlRangeRatio = new TAEventDesc(TAEventType.PNL_INFO, "PnLDayRangeRatio", instrument, false, askBar, bidBar, null);
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
			System.exit(2);
		}
		if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {

			if (!message.getOrder().getState().equals(IOrder.State.CANCELED)) {
				// update total daily profit in %
				dailyPnL.updateInstrumentPnL(message.getOrder().getInstrument(), message.getOrder());
				double dailyPnLRangeRatio = dailyPnL.ratioPnLAvgRange(message.getOrder().getInstrument());
				if (dailyPnLRangeRatio > 0.75) 
					log.print("Total daily PnL > 0.75 x avg. daily range, no more trading ! (" + FXUtils.df2.format(dailyPnLRangeRatio) + ")");
			}
			// might be cancel of unfilled order or SL close
			Set<IMessage.Reason> reasons = message.getReasons();
			String reasonsStr = new String();
			for (IMessage.Reason r : reasons) {
				reasonsStr += r.toString();
			}
			
			PairTradeData currPairData = pairsTradeData.get(message.getOrder().getInstrument().toString());

			if (currPairData.tradeLog != null) {
				currPairData.tradeLog.exitTime = message.getCreationTime();
				if (currPairData.tradeLog.exitReason == null)
					currPairData.tradeLog.exitReason = reasonsStr;
				currPairData.tradeLog.PnL = message.getOrder().getProfitLossInPips();
				
				currPairData.tradeLog.addLogEntry(new FlexLogEntry("exitValues", "EXIT"));
				currPairData.tradeLog.addLogEntry(new FlexLogEntry("exitSetup", currPairData.currentSetup.getName()));
				currPairData.tradeLog.addLogEntry(new FlexLogEntry("lastTradingAction", currPairData.currentSetup.getLastTradingEvent()));
				addLatestTAValues(message.getOrder().getInstrument(), lastTaValues, message.getOrder().isLong());
				
				if (!headerPrinted) {
					headerPrinted = true;
					statsLog.print(currPairData.tradeLog.prepareHeader());
				}
				String logLine = currPairData.tradeLog.prepareExitReport(message.getOrder().getInstrument());
				console.getOut().println(logLine);					
				statsLog.print(logLine);	
			}

			for (ITradeSetup setup : tradeSetups)
				setup.afterTradeReset(message.getOrder().getInstrument());
			currPairData.currentSetup = null;
			currPairData.positionOrder = null;
			currPairData.resetVars();
		}
		if (message.getType().equals(IMessage.Type.ORDER_FILL_OK)) {
			PairTradeData currPairData = pairsTradeData.get(message.getOrder().getInstrument().toString());
			currPairData.tradeLog.setOrder(message.getOrder()); // needed for Mkt orders, tradeLog was created without order being created
			currPairData.tradeLog.fillTime = message.getCreationTime();
			currPairData.tradeLog.fillPrice = message.getOrder().getOpenPrice();
		}
	}

	public void onStop() throws JFException {
		log.close();
		statsLog.close();
		File tradeTestRunningSignal = new File("strategyTestRunning.bin");
		if (tradeTestRunningSignal.exists())
			tradeTestRunningSignal.delete();
	}

	public void onAccount(IAccount account) throws JFException {
	}

	public void onTick(Instrument instrument, ITick tick) throws JFException {
	}

	String getOrderLabel(Instrument instrument, long time, String direction) {
		PairTradeData currPairData = pairsTradeData.get(instrument.toString());
		return new String("MultiPairCasc_" + instrument.name() + "_"
				+ FXUtils.getFormatedTimeGMTforID(time) + "_" + currPairData.orderCounter++
				+ "_" + direction);
	}

	boolean barProcessingAllowed(long time) throws JFException {
		ITimeDomain timeDomain = dataService.getOfflineTimeDomain();

		//return time + selectedPeriod.getInterval() < timeDomain.getStart();
		// so entry signals on last Friday bar can be taken
		return time < timeDomain.getStart();
	}

	boolean tradingAllowed(long time, Period period) throws JFException {
		ITimeDomain timeDomain = dataService.getOfflineTimeDomain();

		return time + period.getInterval() < timeDomain.getStart();
	}

}
