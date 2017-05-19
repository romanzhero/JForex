package jforex.explorers;

import java.io.File;
import java.util.*;

import com.dukascopy.api.*;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;
import jforex.utils.Logger;
import jforex.logging.TradeLog;
import jforex.techanalysis.Trend;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.FlexTAValue;

public class SimpleStatsCollector implements IStrategy {
	@Configurable(value = "Period", description = "Choose the time frame")
	public Period selectedPeriod = Period.FIVE_MINS;

	@Configurable(value = "Filter", description = "Choose the candle filter")
	public Filter selectedFilter = Filter.ALL_FLATS;

	protected String reportDir = null;

	private IConsole console;
	private IHistory history;
	private IIndicators indicators;
	private FlexTASource taSource = null;
	private Map<String, FlexTAValue> lastTaValues = null;
	
	private Logger 
		log = null,
		statsLog = null;
	private TradeLog tradeLog = null;

	private boolean headerPrinted = false;
	
	public SimpleStatsCollector(Properties p) {
		super();
		this.reportDir = p.getProperty("reportDirectory", ".");
	}

	public void onStart(IContext context) throws JFException {
		context.getEngine();
		this.console = context.getConsole();
		this.history = context.getHistory();
		this.indicators = context.getIndicators();
		FXUtils.setProfitLossHelper(context.getAccount().getAccountCurrency(), history);

		taSource = new FlexTASource(indicators, history, selectedFilter);

		log = new Logger(reportDir + "//Casc_report_" + FXUtils.getFileTimeStamp(System.currentTimeMillis()) + ".txt");
		statsLog = new Logger(reportDir + "//Casc_stat_report_" + FXUtils.getFileTimeStamp(System.currentTimeMillis()) + ".txt");
	}

	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (!period.equals(selectedPeriod)
			|| (bidBar.getClose() == bidBar.getOpen() && bidBar.getClose() == bidBar.getHigh() && bidBar.getClose() == bidBar.getLow()))
			return;
		
		lastTaValues = taSource.calcTAValues(instrument, period, askBar, bidBar);
		createTradeLog(instrument, period, bidBar, OfferSide.BID, "SimpleStats", true, lastTaValues);		
		if (!headerPrinted) {
			headerPrinted = true;
			statsLog.print(tradeLog.prepareHeader());
		}
		String logLine = tradeLog.prepareExitReport(instrument);
		console.getOut().println(logLine);					
		statsLog.print(logLine);
	}

	private void createTradeLog(Instrument instrument, Period period, IBar bar, OfferSide side, String orderLabel, boolean isLong, Map<String, FlexTAValue> taValues) {
		tradeLog = new TradeLog(orderLabel, isLong, "SimpleStats", bar.getTime(), 0, 0, 0);

		addLatestTAValues(taValues, isLong);
	}

	protected void addLatestTAValues(Map<String, FlexTAValue> taValues, boolean isLong) {
		tradeLog.addLogEntry(new FlexLogEntry("Regime", FXUtils.getRegimeString((Trend.TREND_STATE)taValues.get(FlexTASource.TREND_ID).getTrendStateValue(),
				taValues.get(FlexTASource.MAs_DISTANCE_PERC).getDoubleValue(),
				(Trend.FLAT_REGIME_CAUSE)taValues.get(FlexTASource.FLAT_REGIME).getValue(), 
				taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue(), taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue())));
		for (Map.Entry<String, FlexTAValue> curr : taValues.entrySet()) {
			FlexTAValue taValue = curr.getValue();
			tradeLog.addLogEntry(taValue);
		}
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

}
