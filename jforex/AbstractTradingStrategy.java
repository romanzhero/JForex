package jforex;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;

import jforex.utils.FXUtils;
import jforex.utils.log.FlexLogEntry;
import jforex.utils.log.Logger.logTags;
import jforex.utils.stats.TradeStats;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public abstract class AbstractTradingStrategy extends BasicTAStrategy {

	protected IAccount account;

	protected TradeStateController tradeState;
	protected double MAX_RISK = 35;
	protected double BREAK_EVEN_PROFIT_THRESHOLD = 60.0;
	protected double PROTECT_PROFIT_THRESHOLD = 100.0;
	protected double PROTECT_PROFIT_THRESHOLD_OFFSET = 20.0;
	protected double safeZone = 3.0;
	protected double DEFAULT_LEVERAGE = 10.0;
	protected boolean FLEXIBLE_LEVERAGE = false;
	protected boolean trailProfit;
	protected LastStopType lastStopType = LastStopType.NONE;
	private int counter = 0;

	protected double protectiveStop = 0;

	protected String runID;

	protected double trendStDevDefinitionMin = -0.7;

	protected double trendStDevDefinitionMax;

	protected TradeStats tradeStats;

	protected double lastRecordedTick = 0;

	protected boolean protectProfitSet = false;

	protected enum LastStopType {
		NONE, EMA9, BEARISH_TRIGGER_HIGH_IN_CHANNEL, BULLISH_TRIGGER_LOW_IN_CHANNEL, TRAILING, BREAKEVEN, PROFIT_PROTECTION
	}

	public AbstractTradingStrategy(Properties props) {
		super(props);
		MAX_RISK = Double.parseDouble(props.getProperty("MAX_RISK", "35"));
		BREAK_EVEN_PROFIT_THRESHOLD = Double.parseDouble(props.getProperty(
				"BREAK_EVEN_PROFIT_THRESHOLD", "60"));
		PROTECT_PROFIT_THRESHOLD = Double.parseDouble(props.getProperty(
				"PROTECT_PROFIT_THRESHOLD", "100"));
		PROTECT_PROFIT_THRESHOLD_OFFSET = Double.parseDouble(props.getProperty(
				"PROTECT_PROFIT_THRESHOLD_OFFSET", "20"));
		DEFAULT_LEVERAGE = Double.parseDouble(props.getProperty("leverage",
				"10"));
		FLEXIBLE_LEVERAGE = Boolean.parseBoolean(props.getProperty(
				"flexibleLeverage", "false").equalsIgnoreCase("yes") ? "true"
				: "false");

		trailProfit = Boolean.parseBoolean(props.getProperty("trailProfit",
				"false").equalsIgnoreCase("yes") ? "true" : "false");

		safeZone = Double.parseDouble(props.getProperty("safeZone"));

		dbLogging = Boolean.parseBoolean(props.getProperty("DB_LOG", "false")
				.equalsIgnoreCase("yes") ? "true" : "false");

		tradeStats = new TradeStats();
	}

	public void onStartExec(IContext context) throws JFException {
		super.onStartExec(context);
		this.account = context.getAccount();
		this.tradeState = new TradeStateController(this.log, true);

		log.print("Type;Label;Timestamp;Action;Stop;Stop distance from entry;P&L;Commissions");
		dbLogOnStart();
	}

	protected String getLabel(Instrument instrument) {
		String label = instrument.name();
		label = label + FXUtils.if3.format(counter++);
		label = label.toUpperCase();
		return label;
	}

	protected void dbLogOnStart() {
		if (dbLogging) {
			try {
				Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
			} catch (java.lang.ClassNotFoundException e1) {
				log.print("ODBC driver load failure (ClassNotFoundException: "
						+ e1.getMessage() + ")");
				dbLogging = false;
			}
			String dsn = "FX_log";
			String url = "jdbc:odbc:" + dsn;
			try {
				logDB = DriverManager.getConnection(url, "", "");
				Statement stmt = logDB.createStatement();
				String runTimeStamp = FXUtils.getFormatedTimeGMT(new DateTime()
						.getMillis());
				runID = getStrategyName() + "_" + runTimeStamp;
				stmt.executeUpdate("INSERT INTO TRun (RunID, Pair, Strategy, Start, Leverage, FlexLeverage) "
						+ "VALUES ('"
						+ runID
						+ "', '"
						+ getSelectedInstrument().toString()
						+ "', '"
						+ getStrategyName()
						+ "', '"
						+ runTimeStamp
						+ "', "
						+ FXUtils.df1.format(DEFAULT_LEVERAGE)
						+ ", "
						+ (FLEXIBLE_LEVERAGE ? "Yes" : "No") + ")");
			} catch (SQLException ex) {
				log.print("Log database problem: " + ex.getMessage());
				dbLogging = false;
			}
		}
	}

	protected void dbLogRecordEntry(IMessage message, IOrder positionOrder) {
		if (!dbLogging)
			return;

		String dbFieldNamesStr = new String(
				" (RunID, Instrument, OrderID, OrderType, Amount, FillTime, EntryPriceFill)");
		String dbValuesStr = new String(" VALUES (");
		dbValuesStr += "'" + runID + "', '"
				+ positionOrder.getInstrument().toString() + "', '"
				+ positionOrder.getLabel() + "', '"
				+ positionOrder.getOrderCommand().toString() + "', "
				+ FXUtils.df2.format(positionOrder.getAmount()) + ", '"
				+ FXUtils.getFormatedTimeGMT(positionOrder.getFillTime())
				+ "', " + FXUtils.df5.format(positionOrder.getOpenPrice())
				+ ")";
		String statementStr = "INSERT INTO TTrade " + dbFieldNamesStr
				+ dbValuesStr;
		try {
			Statement insert = logDB.createStatement();
			insert.executeUpdate(statementStr);
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr);
			dbLogging = false;
		}
	}

	protected void dbLogRecordExit(IMessage message, IOrder positionOrder,
			logTags exitReason) {
		if (!dbLogging)
			return;

		String dbUpdateFieldsStr = new String();
		String dbWhereStr = new String(" WHERE ");

		dbUpdateFieldsStr += "ExitTime = '"
				+ FXUtils.getFormatedTimeGMT(positionOrder.getCloseTime())
				+ "', "
				+ "WinOrLose = "
				+ (positionOrder.getProfitLossInPips() > 0 ? "Yes" : "No")
				+ ", "
				+ "ExitPriceWanted = "
				+ FXUtils.df5.format(positionOrder.getStopLossPrice())
				+ ", "
				+ "ExitPriceFill = "
				+ FXUtils.df5.format(message.getOrder().getClosePrice())
				+ ", "
				+ "PnLPips = "
				+ FXUtils.df1.format(positionOrder.getProfitLossInPips())
				+ ", "
				+ "PnLPipsAccountCurrency = "
				+ FXUtils.df1.format(positionOrder
						.getProfitLossInAccountCurrency()) + ", "
				+ "ExitReason = '" + exitReason.toString() + "', "
				+ "MaxWin = " + FXUtils.df1.format(tradeStats.getMaxWin())
				+ ", " + "MaxLoss = "
				+ FXUtils.df1.format(tradeStats.getMaxLoss());

		dbWhereStr += "RunID = '" + runID + "'" + " AND Instrument = '"
				+ positionOrder.getInstrument().toString() + "'"
				+ " AND OrderID = '" + positionOrder.getLabel() + "'";

		String statementStr = "UPDATE TTrade SET " + dbUpdateFieldsStr
				+ dbWhereStr;
		try {
			Statement update = logDB.createStatement();
			update.executeUpdate(statementStr);
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr);
			dbLogging = false;
		}
	}

	protected void printStatsValues(logTags direction, Instrument instrument,
			Period period, long time, double entryPrice, long triggerPivotTime,
			double protectiveStop, int lookBack, boolean header)
			throws JFException {
		double[] macds = momentum.getMACDs(instrument, period, OfferSide.BID,
				AppliedPrice.CLOSE, time);
		double[] stochs = momentum.getStochs(instrument, period, OfferSide.BID,
				time);
		double[] adxs = trendDetector.getADXs(instrument, period,
				OfferSide.BID, time);
		List<FlexLogEntry> logLine = new ArrayList<FlexLogEntry>();

		logLine.add(new FlexLogEntry(direction.toString(), direction.toString()
				+ " " + period.toString()));
		logLine.add(new FlexLogEntry("orderID", getPositionOrder().getLabel()));
		logLine.add(new FlexLogEntry("time", FXUtils.getFormatedTimeGMT(time)));
		logLine.add(new FlexLogEntry("CandleTrigger", tradeTrigger
				.getLastBullishTrigger().toString()));
		logLine.add(new FlexLogEntry("MACD", new Double(macds[0]), FXUtils.df5));
		logLine.add(new FlexLogEntry("MACD_Signal", new Double(macds[1]),
				FXUtils.df5));
		logLine.add(new FlexLogEntry("MACD_H", new Double(macds[2]),
				FXUtils.df5));
		logLine.add(new FlexLogEntry("StochFast", new Double(stochs[0]),
				FXUtils.df1));
		logLine.add(new FlexLogEntry("StochSlow", new Double(stochs[1]),
				FXUtils.df1));
		logLine.add(new FlexLogEntry("StocsDiff", new Double(stochs[0]
				- stochs[1]), FXUtils.df1));
		logLine.add(new FlexLogEntry("ADX", new Double(adxs[0]), FXUtils.df1));
		logLine.add(new FlexLogEntry("DI_PLUS", new Double(adxs[1]),
				FXUtils.df1));
		logLine.add(new FlexLogEntry("DI_MINUS", new Double(adxs[2]),
				FXUtils.df1));
		double trendStDevPos = trendDetector.getUptrendMAsMaxDifStDevPos(
				instrument, period, OfferSide.BID, AppliedPrice.CLOSE, time,
				lookBack);
		if (trendStDevPos != -1000 && trendStDevPos > trendStDevDefinitionMin) {
			logLine.add(new FlexLogEntry("Trend", "Up"));
		} else if ((trendStDevPos = trendDetector
				.getDowntrendMAsMaxDifStDevPos(instrument, period,
						OfferSide.BID, AppliedPrice.CLOSE, time, lookBack)) != -1000
				&& trendStDevPos > trendStDevDefinitionMin) {
			logLine.add(new FlexLogEntry("Trend", "Down"));
		} else {
			trendStDevPos = trendDetector.getMAsMaxDiffStDevPos(instrument,
					period, OfferSide.BID, AppliedPrice.CLOSE, time, lookBack);
			logLine.add(new FlexLogEntry("Trend", "Flat"));
		}
		logLine.add(new FlexLogEntry("Strength", new Double(trendStDevPos),
				FXUtils.df2));
		logLine.add(new FlexLogEntry("entryChannelPos", new Double(
				channelPosition.priceChannelPos(instrument, period,
						OfferSide.BID, time, entryPrice)), FXUtils.df1));
		logLine.add(new FlexLogEntry("stopChannelPos", new Double(
				channelPosition.priceChannelPos(instrument, period,
						OfferSide.BID, triggerPivotTime, protectiveStop)),
				FXUtils.df1));
		logLine.add(new FlexLogEntry("ATR14", new Double(vola.getATR(
				instrument, period, OfferSide.BID, time, 14) * 10000),
				FXUtils.df2));
		logLine.add(new FlexLogEntry("ATR28", new Double(vola.getATR(
				instrument, period, OfferSide.BID, time, 28) * 10000),
				FXUtils.df2));
		logLine.add(new FlexLogEntry("barsBelow", new Double(channelPosition
				.consequitiveBarsBelow(instrument, period, OfferSide.BID, time,
						5)), FXUtils.df1));
		logLine.add(new FlexLogEntry("RSI", new Double(indicators.rsi(
				instrument, period, OfferSide.BID, AppliedPrice.CLOSE, 14,
				Filter.WEEKENDS, 1, time, 0)[0]), FXUtils.df1));
		logLine.add(new FlexLogEntry("barsAbove", new Double(channelPosition
				.consequitiveBarsAbove(instrument, period, OfferSide.BID, time,
						5)), FXUtils.df1));

		if (header)
			log.printLabelsFlex(logLine);
		log.printValuesFlex(logLine);
		if (dbLogging)
			dbLogSignalStats(logLine, instrument, period, direction);

	}

	protected void dbLogSignalStats(List<FlexLogEntry> fields, Instrument pair,
			Period p, logTags direction) {
		String dbFieldNamesStr = new String(
				" (RunID, Instrument, TimeFrame, EntryOrExit, OrderID, BarTime"), dbValuesStr = new String(
				" VALUES ('" + runID + "', '" + pair.toString() + "', '"
						+ p.toString() + "', '"
						+ direction.equals(logTags.ENTRY_STATS) + "', '"
						+ fields.get(1).getFormattedValue() + "', '"
						+ fields.get(2).getFormattedValue() + "'");

		for (int i = 3; i < fields.size() && i < 28; i++) {
			FlexLogEntry field = fields.get(i);
			dbFieldNamesStr += ", Field" + (i - 2);
			if (field.isDouble()) {
				dbFieldNamesStr += ", Field" + (i - 2) + "Double";
				dbValuesStr += ", '" + field.getLabel() + "', "
						+ field.getFormattedValue();
			} else {
				dbFieldNamesStr += ", Field" + (i - 2) + "Text";
				dbValuesStr += ", '" + field.getLabel() + "', '"
						+ field.getFormattedValue() + "'";
			}
		}
		// close the brackets
		dbFieldNamesStr += ") ";
		dbValuesStr += ")";
		String statementStr = "INSERT INTO TStatsEntry " + dbFieldNamesStr
				+ dbValuesStr;
		try {
			Statement insert = logDB.createStatement();
			insert.executeUpdate(statementStr);
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr);
			dbLogging = false;
		}
	}

	protected abstract Instrument getSelectedInstrument();

	protected abstract IOrder getPositionOrder();

	protected boolean tradingAllowed(long time) {
		// No trading after Fri 17h, on Christmas and New Years Eve
		DateTime timeStamp = new DateTime(time);
		return !((timeStamp.getDayOfWeek() == DateTimeConstants.FRIDAY && timeStamp
				.getHourOfDay() > 20)
				|| timeStamp.getDayOfWeek() == DateTimeConstants.SATURDAY || (timeStamp
				.getDayOfWeek() == DateTimeConstants.SUNDAY && timeStamp
				.getHourOfDay() < 22))
				&& !(timeStamp.getMonthOfYear() == 12 && timeStamp
						.getDayOfMonth() == 24)
				&& !(timeStamp.getMonthOfYear() == 12 && timeStamp
						.getDayOfMonth() == 31);
	}

}