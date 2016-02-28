package jforex.strategies;

import java.awt.Color;
import java.io.File;
import java.util.*;

import com.dukascopy.api.*;
import com.dukascopy.api.IIndicators.MaType;
import com.dukascopy.api.drawings.IHorizontalLineChartObject;
import com.dukascopy.api.indicators.IIndicator;
import com.dukascopy.api.indicators.IndicatorInfo;
import com.dukascopy.api.indicators.OutputParameterInfo;

import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;
import jforex.utils.Logger;
import jforex.events.CandleMomentumEvent;
import jforex.events.ITAEvent;
import jforex.events.LongCandlesEvent;
import jforex.events.ShortCandlesEvent;
import jforex.events.TAEventDesc;
import jforex.logging.TradeLog;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Trend.FLAT_REGIME_CAUSE;
import jforex.techanalysis.Volatility;
import jforex.techanalysis.Channel;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.FlexTAValue;
import jforex.trades.*;

public class FlatCascTest implements IStrategy {
	@Configurable(value = "Period", description = "Choose the time frame")
	public Period 
		selectedPeriod = Period.FOUR_HOURS,
		orderSubmitPeriod = Period.TEN_MINS;

	@Configurable(value = "Filter", description = "Choose the candle filter")
	public Filter selectedFilter = Filter.WEEKENDS;

	@Configurable(value = "Amount", stepSize = 0.0001, description = "Choose amount (step size 0.0001)")
	public double selectedAmount = 0.1;

	protected Instrument selectedInstrument = null;
	protected boolean visualMode = false, showIndicators = false;
	protected String reportDir = null;

	private IEngine engine;
	private IConsole console;
	private IHistory history;
	private IIndicators indicators;
	private IDataService dataService;

	private IChart chart = null;
	private Map<String, IOrder> orderPerPair = new HashMap<String, IOrder>();
	private long orderCnt = 1;

	private List<ITradeSetup> tradeSetups = new ArrayList<ITradeSetup>();
	private List<ITAEvent> taEvents = new ArrayList<ITAEvent>();
	private ITakeOverRules takeOverRules = new SmiSmaTakeOverRules();
	private ITradeSetup currentSetup = null;

	private FlexTASource taSource = null;
	private Trend trend = null;
	private Volatility vola = null;
	private Channel channel = null;
	private TradeTrigger candles = null;

	private Logger 
		log = null,
		statsLog = null;
	private TradeLog tradeLog = null;

	private boolean 
		orderWaitinginQueue = false,
		queueOrderIsLong;
	private String queueOrderLabel = null;
	private boolean headerPrinted = false;
	private Properties conf = null;

	public FlatCascTest(Instrument selectedInstrument, boolean visualMode,	boolean showIndicators, String reportDir) {
		super();
		this.selectedInstrument = selectedInstrument;
		this.visualMode = visualMode;
		this.showIndicators = showIndicators;
		this.reportDir = reportDir;
	}
	
	public FlatCascTest(Instrument selectedInstrument, Properties p) {
		super();
		this.selectedInstrument = selectedInstrument;
		this.visualMode = p.getProperty("visualMode", "no").equalsIgnoreCase("yes"); 
		this.showIndicators = p.getProperty("showIndicators", "no").equalsIgnoreCase("yes");
		this.reportDir = p.getProperty("reportDirectory", ".");
		conf = p;
	}

	public void onStart(IContext context) throws JFException {
		this.engine = context.getEngine();
		this.console = context.getConsole();
		this.history = context.getHistory();
		this.indicators = context.getIndicators();

		dataService = context.getDataService();

		taSource = new FlexTASource(indicators, history, selectedFilter);
		trend = new Trend(indicators);
		vola = new Volatility(indicators);
		channel = new Channel(context.getHistory(), indicators);
		candles = new TradeTrigger(indicators, context.getHistory(), null);

		log = new Logger(reportDir + "//Casc_report_" + FXUtils.getFileTimeStamp(System.currentTimeMillis()) + ".txt");
		statsLog = new Logger(reportDir + "//Casc_stat_report_" + FXUtils.getFileTimeStamp(System.currentTimeMillis()) + ".txt");

		Set<Instrument> pairs = context.getSubscribedInstruments();
		for (Instrument currI : pairs) {
			orderPerPair.put(currI.name(), null);
		}
		if (conf.getProperty("FlatSetup", "no").equals("yes"))
			tradeSetups.add(new FlatTradeSetup(engine, true));
		//tradeSetups.add(new PUPBSetup(indicators, history, engine));
		if (conf.getProperty("SMISetup", "no").equals("yes"))
			tradeSetups.add(new SmiTradeSetup(engine, false, 30.0, 30.0));
		if (conf.getProperty("TrendIDFollowSetup", "no").equals("yes"))
			tradeSetups.add(new SmaTradeSetup(indicators, history, engine, context.getSubscribedInstruments(), true, false, 30.0, 30.0));
		else if (conf.getProperty("TrendIDFollowSoloSetup", "no").equals("yes"))
			tradeSetups.add(new SmaSoloTradeSetup(engine, context.getSubscribedInstruments(), true, false, 30.0, 30.0));
		
		taEvents.add(new LongCandlesEvent(indicators, history));
		taEvents.add(new ShortCandlesEvent(indicators, history));
		taEvents.add(new CandleMomentumEvent(indicators, history));

		if (visualMode) {
			chart = context.getChart(selectedInstrument);
			if (chart == null) {
				// chart is not opened, we can't plot an object
				console.getOut().println(
						"Can't open the chart for "
								+ selectedInstrument.toString() + ", stop !");
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
					List<IIndicator> panelIndicators = currPanel
							.getIndicators();
					for (IIndicator currIndicator : panelIndicators) {
						if (currIndicator.toString().contains("SMIIndicator")) {
							currPanel
									.add(indicators.getIndicator("SMI"),
											new Object[] { 10, 3, 5, 3 },
											new Color[] { Color.RED,
													Color.BLACK },
											new OutputParameterInfo.DrawingStyle[] {
													OutputParameterInfo.DrawingStyle.LINE,
													OutputParameterInfo.DrawingStyle.NONE },
											null);
							IHorizontalLineChartObject lplus60 = chart
									.getChartObjectFactory()
									.createHorizontalLine();
							lplus60.setPrice(0, 60);
							lplus60.setText("60");
							lplus60.setColor(Color.BLACK);
							lplus60.setLineStyle(LineStyle.DASH);
							currPanel.add(lplus60);

							IHorizontalLineChartObject lminus60 = chart
									.getChartObjectFactory()
									.createHorizontalLine();
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
			&& tradingAllowed(bidBar.getTime(), period)
			&& !(bidBar.getClose() == bidBar.getOpen() && bidBar.getClose() == bidBar.getHigh() && bidBar.getClose() == bidBar.getLow())) {
			submitOrderFromQueue(selectedInstrument, bidBar, askBar);
			return;
		}
		
		if (!instrument.equals(selectedInstrument)
			|| !period.equals(selectedPeriod)
			|| !barProcessingAllowed(bidBar.getTime())
			|| (bidBar.getClose() == bidBar.getOpen() && bidBar.getClose() == bidBar.getHigh() && bidBar.getClose() == bidBar.getLow()))
			return;
		
		Map<String, FlexTAValue> taValues = taSource.calcTAValues(instrument, period, askBar, bidBar);

		double[][] slowSMI = indicators.smi(instrument, period, OfferSide.BID,
				50, 15, 5, 3, selectedFilter, 2, bidBar.getTime(), 0), fastSMI = indicators
				.smi(instrument, period, OfferSide.BID, 10, 3, 5, 3,
						selectedFilter, 2, bidBar.getTime(), 0);
		double atr = indicators.atr(instrument, period, OfferSide.BID, 14,
				selectedFilter, 1, bidBar.getTime(), 0)[0], ma20 = indicators
				.sma(instrument, period, OfferSide.BID,
						IIndicators.AppliedPrice.CLOSE, 20, selectedFilter, 1,
						bidBar.getTime(), 0)[0], ma50 = indicators.sma(
				instrument, period, OfferSide.BID,
				IIndicators.AppliedPrice.CLOSE, 50, selectedFilter, 1,
				bidBar.getTime(), 0)[0], ma100 = indicators.sma(instrument,
				period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100,
				selectedFilter, 1, bidBar.getTime(), 0)[0], ma200 = indicators
				.sma(instrument, period, OfferSide.BID,
						IIndicators.AppliedPrice.CLOSE, 200, selectedFilter, 1,
						bidBar.getTime(), 0)[0];
		FLAT_REGIME_CAUSE isFlat = trend.isFlatRegime(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, selectedFilter, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS, 30);

		IOrder order = orderPerPair.get(instrument.name());
		if (order != null) {
			// there is an open order, might be pending (waiting) or filled !
			// go through all the other (non-current) setups and check whether
			// they should take over (their conditions fullfilled)
			ITradeSetup.EntryDirection signal = ITradeSetup.EntryDirection.NONE;
			boolean takeOver = false;
			if (!currentSetup.isTradeLocked(instrument, period, askBar, bidBar,	selectedFilter, order, taValues)) {
				for (ITradeSetup setup : tradeSetups) {
					if (!setup.getName().equals(currentSetup.getName())) {
						signal = setup.checkTakeOver(instrument, period, askBar, bidBar, selectedFilter, taValues);
						takeOver = (order.isLong() && signal.equals(ITradeSetup.EntryDirection.LONG) && takeOverRules.canTakeOver(currentSetup.getName(), setup.getName()))
								|| (!order.isLong()	&& signal.equals(ITradeSetup.EntryDirection.SHORT) && takeOverRules.canTakeOver(currentSetup.getName(),	setup.getName()));
						if (takeOver) {
							currentSetup = setup;
							String logLine = new String(FXUtils.getFormatedBarTime(bidBar) + ": " + signal.toString()
											+ " takeover by " + currentSetup.getName() + ", order " + order.getLabel());
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
				List<TAEventDesc> marketEvents = checkMarketEvents(instrument, period, askBar, bidBar, selectedFilter, taValues);
				currentSetup.inTradeProcessing(instrument, period, askBar, bidBar, selectedFilter, order, taValues, marketEvents);
				// inTradeProcessing might CLOSE the order, onMessage will set to null !
				order = orderPerPair.get(instrument.name());
				if (order != null) {
					if (order.getStopLossPrice() != 0.0)
						tradeLog.updateMaxRisk(order.getStopLossPrice());
					tradeLog.updateMaxLoss(bidBar, atr);
					tradeLog.updateMaxProfit(bidBar);
					ITradeSetup.EntryDirection exitSignal = currentSetup.checkExit(instrument, period, askBar, bidBar, selectedFilter, order, taValues);
					if ((order.isLong() && exitSignal.equals(ITradeSetup.EntryDirection.LONG))
							|| (!order.isLong() && exitSignal.equals(ITradeSetup.EntryDirection.SHORT))) {
						if (tradeLog != null)
							tradeLog.exitReason = new String("exit criteria");
						String logLine = new String(FXUtils.getFormatedBarTime(bidBar) + ": " + exitSignal.toString()
										+ "  exit signal by " + currentSetup.getName() + " setup, order " + order.getLabel());
						console.getOut().println(logLine);
						log.print(logLine);

						order.close();
						order.waitForUpdate(null);
						order = null;
						orderPerPair.put(instrument.name(), null);
					}
				}
			}
		}
		// enable re-entry on the same bar !
		if (order == null) {
			// no open position
			TAEventDesc signal = null;
			for (ITradeSetup setup : tradeSetups) {
				signal = setup.checkEntry(instrument, period, askBar, bidBar, selectedFilter, taValues);
				if (signal != null) {
					// must be before so also Mkt orders can have a chance to
					// write to non-null tradeLog !
					currentSetup = setup;
					String orderLabel = getOrderLabel(instrument, bidBar.getTime(),	signal.isLong ? "BUY" : "SELL");
					createTradeLog(instrument, period, askBar, OfferSide.ASK, orderLabel, signal.isLong,
							slowSMI[0][1], fastSMI[0][1], ma200 > ma100	&& ma200 > ma50 && ma200 > ma20,
							ma200 < ma100 && ma200 < ma50 && ma200 < ma20, isFlat);
					if (tradingAllowed(bidBar.getTime(), period)) {
						order = currentSetup.submitOrder(orderLabel, instrument, signal.isLong, selectedAmount, bidBar, askBar);
						order.waitForUpdate(null);
	
						orderPerPair.put(instrument.name(), order);
						String logLine = new String(FXUtils.getFormatedBarTime(bidBar) + ": "
										+ (signal.isLong ? "long" : "short") + " entry with "
										+ currentSetup.getName() + ", order " + order.getLabel());
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
				
				if (!headerPrinted) {
					headerPrinted = true;
					statsLog.print(tradeLog.prepareHeader());
				}
				String logLine = tradeLog.prepareExitReport(message.getOrder().getInstrument());
				console.getOut().println(logLine);					
				statsLog.print(logLine);
			}

			orderPerPair.put(message.getOrder().getInstrument().name(), null);
			for (ITradeSetup setup : tradeSetups)
				setup.afterTradeReset(message.getOrder().getInstrument());
			currentSetup = null;
		}
		if (message.getType().equals(IMessage.Type.ORDER_FILL_OK)) {
			tradeLog.setOrder(message.getOrder()); // needed for Mkt orders,
													// tradeLog was created
													// without order being
													// created
			tradeLog.fillTime = message.getCreationTime();
			tradeLog.fillPrice = message.getOrder().getOpenPrice();
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
		return new String("FlatCascTest_" + instrument.name() + "_"
				+ FXUtils.getFormatedTimeGMTforID(time) + "_" + orderCnt++
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
	
	void createTradeLog(Instrument instrument, Period period, IBar bar,	OfferSide side, String orderLabel, boolean isLong, 
			double slowSMI,	double fastSMI, boolean ma200Highest, boolean ma200Lowest, FLAT_REGIME_CAUSE isFlat)	throws JFException {

		tradeLog = new TradeLog(orderLabel, isLong, currentSetup.getName(), bar.getTime(), 0, 0, 0);

		Trend.TREND_STATE entryTrendID = trend.getTrendState(instrument, period, side,
				IIndicators.AppliedPrice.CLOSE, bar.getTime());
		tradeLog.addLogEntry(new FlexLogEntry("Regime", FXUtils.getRegimeString(entryTrendID, isFlat, ma200Highest, ma200Lowest)));
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
	}

}
