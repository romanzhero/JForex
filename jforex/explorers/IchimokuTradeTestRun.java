package jforex.explorers;

import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.SimpleTimeZone;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.Filter;
import com.dukascopy.api.util.DateUtils;
import com.dukascopy.api.IIndicators.AppliedPrice;

import java.io.File;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;

import jforex.BasicTAStrategy;
import jforex.logging.TradeLog;
import jforex.techanalysis.TradeTrigger.TriggerDesc;
import jforex.techanalysis.Trend.IchiDesc;
import jforex.utils.FXUtils;
import jforex.utils.Logger;
import jforex.utils.MultiDDLog;

// 1 full ATR from cloud as SL
public class IchimokuTradeTestRun extends BasicTAStrategy implements IStrategy {

	public static final int YEAR_WORTH_OF_4H_BARS = 1560;

	public static final DecimalFormat df5 = new DecimalFormat("0.00000");
	public static final DecimalFormat df4 = new DecimalFormat("0.0000");
	public static final DecimalFormat df2 = new DecimalFormat("0.00");
	public static final DecimalFormat df1 = new DecimalFormat("0.0");

	private class IchiTradeLog extends TradeLog {
		public double ratioCloudATR, PnLAtLastKijun, currPnLWhenRSIExtreme, // only
																			// working
																			// variable
				PnLAtFirstRSIStayExtreme, // only for first block of 3 or more
											// bars in extreme
				MaxPnLWhenRSIExtreme, // biggest of all except the first block
										// of 3 or more bars in extreme
				cloudDistStDev; // difference between High/Low and closest cloud
								// border

		public String exitReason = null, entryBorderTopDirection,
				entryBorderBottomDirection;
		public boolean entryTenkanBullish, entryIsBullishCloud;
		public int currRSIExtremeBars, FirstRSIStayExtremeBars, // only first
																// entry into 3+
																// bars in
																// extreme will
																// be recorded
				RSIExtremeEntries; // needed for correct counting and final
									// report

		public IchiTradeLog(String pOrderLabel, boolean pIsLong,
				long pSignalTime, double pEntryPrice, double pSL,
				double pInitialRisk, double pRatioCloudATR,
				boolean pEntryTenkanBullish, String pEntryBorderTopDirection,
				String pEntryBorderBottomDirection, boolean pEntryIsBullishCloud) {
			super(pOrderLabel, pIsLong, "Ichi", pSignalTime, pEntryPrice, pSL, pInitialRisk);
			ratioCloudATR = pRatioCloudATR;
			entryTenkanBullish = pEntryTenkanBullish;
			entryBorderTopDirection = pEntryBorderTopDirection;
			entryBorderBottomDirection = pEntryBorderBottomDirection;
			entryIsBullishCloud = pEntryIsBullishCloud;

			PnLAtLastKijun = 0.0;

			currRSIExtremeBars = 0;
			RSIExtremeEntries = 0;
			FirstRSIStayExtremeBars = 0;

			currPnLWhenRSIExtreme = 0.0;
			PnLAtFirstRSIStayExtreme = 0.0;
			MaxPnLWhenRSIExtreme = 0.0;

			cloudDistStDev = 0.0;
		}

		public double missedProfit(Instrument instrument) {
			// PnL taken from IOrder and already in pips
			return maxProfit * Math.pow(10, instrument.getPipScale()) - PnL;
		}

		public double missedProfitPerc(Instrument instrument) {
			// PnL taken from IOrder and already in pips
			return PnL > 0.0 ? 100 * missedProfit(instrument)
					/ (maxProfit * Math.pow(10, instrument.getPipScale()))
					: 0.0;
		}

		public void updateMaxRisk(double newSL) {
			if (isLong) {
				if (fillPrice - newSL > maxRisk)
					maxRisk = fillPrice - newSL;
			} else {
				if (newSL - fillPrice > maxRisk)
					maxRisk = newSL - fillPrice;
			}
		}

		public void updateMaxLoss(IBar bidBar, double ATR) {
			if (isLong) {
				if (bidBar.getLow() < fillPrice
						&& bidBar.getLow() - fillPrice < maxLoss) {
					maxLoss = bidBar.getLow() - fillPrice;
					maxLossATR = maxLoss / ATR;
				}
			} else {
				if (bidBar.getHigh() > fillPrice
						&& fillPrice - bidBar.getHigh() < maxLoss) {
					maxLoss = fillPrice - bidBar.getHigh();
					maxLossATR = maxLoss / ATR;
				}
			}
		}

		public void updateMaxDD(IBar bidBar, double slowLine, double ATR) {
			// updateMaxProfit(bidBar);
			// avoid low of the maxProfit bar
			if (bidBar.getTime() <= maxProfitTime)
				return;

			if (isLong) {
				if (bidBar.getClose() > slowLine
						&& bidBar.getLow() >= fillPrice
						&& maxProfitPrice - bidBar.getLow() > maxDD) {
					maxDD = maxProfitPrice - bidBar.getLow();
					maxDDTime = bidBar.getTime();
					maxDDATR = maxDD / ATR;
				}
			} else {
				if (bidBar.getClose() < slowLine
						&& bidBar.getHigh() <= fillPrice
						&& bidBar.getHigh() - maxProfitPrice > maxDD) {
					maxDD = bidBar.getHigh() - maxProfitPrice;
					maxDDTime = bidBar.getTime();
					maxDDATR = maxDD / ATR;
				}
			}
		}

		public void updateMaxProfit(IBar bidBar) {
			if (isLong) {
				if (bidBar.getHigh() > fillPrice
						&& bidBar.getHigh() - fillPrice > maxProfit) {
					maxProfit = bidBar.getHigh() - fillPrice;
					maxProfitPrice = bidBar.getHigh();
					maxProfitTime = bidBar.getTime();
				}
			} else {
				if (bidBar.getLow() < fillPrice
						&& fillPrice - bidBar.getLow() > maxProfit) {
					maxProfit = fillPrice - bidBar.getLow();
					maxProfitPrice = bidBar.getLow();
					maxProfitTime = bidBar.getTime();
				}
			}
		}

		public void updatePnLAtLastKijun(IBar currBar, IBar prevBar,
				double kijun, double prevKijun) {
			if (isLong) {
				if (prevBar.getLow() >= prevKijun && currBar.getLow() < kijun)
					PnLAtLastKijun = kijun - fillPrice;
			} else {
				if (prevBar.getHigh() <= prevKijun && currBar.getHigh() > kijun)
					PnLAtLastKijun = fillPrice - kijun;
			}
		}

		// only interested in first stay with 3 or more bars in extreme. The
		// next ones shall be ignored.
		public void updateRSI(double[] RSIs, IBar currBar) {
			double prevRSI = RSIs[0], currRSI = RSIs[1];
			if (isLong) {
				if (currRSI > 70.0) {
					currRSIExtremeBars++;
					if (currBar.getClose() - fillPrice > currPnLWhenRSIExtreme)
						currPnLWhenRSIExtreme = currBar.getClose() - fillPrice;
					// log.print(";RSI;" + DateUtils.format(currBar.getTime()) +
					// ";entry;prevRSI: " + df1.format(prevRSI) + ";currRSI: " +
					// df1.format(currRSI) + "; count: " + (RSIExtremeEntries +
					// 1) + "; currExtBars: " + currRSIExtremeBars + ";PnL :" +
					// df1.format((currBar.getClose() - fillPrice) * 10000));
					if (prevRSI >= 70.0) {
						// update profit track only in case of 3 and more
						if (currRSIExtremeBars >= 3
								&& FirstRSIStayExtremeBars < 3)
							PnLAtFirstRSIStayExtreme = currBar.getClose()
									- fillPrice;
						// log.print(";RSI;" +
						// DateUtils.format(currBar.getTime()) +
						// ";next bar;prevRSI: " + df1.format(prevRSI) +
						// ";currRSI: " + df1.format(currRSI) + "; count: " +
						// currRSIExtremeBars + ";PnL :" +
						// df1.format((currBar.getClose() - fillPrice) *
						// 10000));
					}
					return;
				} else {
					// cancel count if less then 3 bars above 70
					if (prevRSI > 70.0) {
						RSIExtremeEntries++;
						if (currRSIExtremeBars < 3) {
							if (currPnLWhenRSIExtreme > MaxPnLWhenRSIExtreme)
								MaxPnLWhenRSIExtreme = currPnLWhenRSIExtreme;
							// log.print(";RSI;" +
							// DateUtils.format(currBar.getTime()) +
							// ";cancel long;prevRSI: " + df1.format(prevRSI) +
							// ";currRSI: " + df1.format(currRSI) + "; count: "
							// + RSIExtremeEntries + ";PnL :" +
							// df1.format((currBar.getClose() - fillPrice) *
							// 10000));
						} else {
							if (FirstRSIStayExtremeBars < 3)
								FirstRSIStayExtremeBars = currRSIExtremeBars;
							else if (currPnLWhenRSIExtreme > MaxPnLWhenRSIExtreme)
								MaxPnLWhenRSIExtreme = currPnLWhenRSIExtreme;
						}
						currRSIExtremeBars = 0;
						currPnLWhenRSIExtreme = 0.0;
					}
					return;
				}
			} else {
				if (currRSI < 31.0) {
					currRSIExtremeBars++;
					if (fillPrice - currBar.getClose() > currPnLWhenRSIExtreme)
						currPnLWhenRSIExtreme = fillPrice - currBar.getClose();
					// log.print(";RSI;" + DateUtils.format(currBar.getTime()) +
					// ";entry;prevRSI: " + df1.format(prevRSI) + ";currRSI: " +
					// df1.format(currRSI) + "; count: " + (RSIExtremeEntries +
					// 1) + "; currExtBars: " + currRSIExtremeBars + ";PnL :" +
					// df1.format((fillPrice - currBar.getClose()) * 10000));
					if (prevRSI < 31.0) {
						// update profit track only in case of 3 and more
						if (currRSIExtremeBars >= 3
								&& FirstRSIStayExtremeBars < 3)
							PnLAtFirstRSIStayExtreme = fillPrice
									- currBar.getClose();
						// log.print(";RSI;" +
						// DateUtils.format(currBar.getTime()) +
						// ";next bar;prevRSI: " + df1.format(prevRSI) +
						// ";currRSI: " + df1.format(currRSI) + "; count: " +
						// currRSIExtremeBars + ";PnL :" + df1.format((fillPrice
						// - currBar.getClose()) * 10000));
					}
					return;
				} else {
					// cancel count if less then 3 bars above 70
					if (prevRSI < 31.0) {
						RSIExtremeEntries++;
						if (currRSIExtremeBars < 3) {
							if (currPnLWhenRSIExtreme > MaxPnLWhenRSIExtreme)
								MaxPnLWhenRSIExtreme = currPnLWhenRSIExtreme;
							// log.print(";RSI;" +
							// DateUtils.format(currBar.getTime()) +
							// ";cancel short;prevRSI: " + df1.format(prevRSI) +
							// ";currRSI: " + df1.format(currRSI) + "; count: "
							// + FirstRSIStayExtremeBars + ";PnL :" +
							// df1.format((fillPrice - currBar.getClose()) *
							// 10000));
						} else {
							if (FirstRSIStayExtremeBars < 3)
								FirstRSIStayExtremeBars = currRSIExtremeBars;
							else if (currPnLWhenRSIExtreme > MaxPnLWhenRSIExtreme)
								MaxPnLWhenRSIExtreme = currPnLWhenRSIExtreme;
						}
						currRSIExtremeBars = 0;
						currPnLWhenRSIExtreme = 0.0;
					}
					return;
				}
			}
		}

		public void exitReport(Instrument instrument, Logger log) {
			log.print(super.prepareExitReport(instrument)
					+ ";"
					+ (entryTenkanBullish ? "BULLISH " : "BEARISH")
					+ ";"
					+ entryBorderTopDirection
					+ ";"
					+ entryBorderBottomDirection
					+ ";"
					+ (entryIsBullishCloud ? "BULLISH " : "BEARISH")
					+ ";"
					+ IchimokuTradeTestRun.df1.format(PnLAtLastKijun
							* Math.pow(10, instrument.getPipScale()))
					+ ";"
					+ IchimokuTradeTestRun.df1.format(ratioCloudATR)
					+ ";"
					+ RSIExtremeEntries
					+ ";"
					+ FirstRSIStayExtremeBars
					+ ";"
					+ IchimokuTradeTestRun.df1.format(PnLAtFirstRSIStayExtreme
							* Math.pow(10, instrument.getPipScale()))
					+ ";"
					+ IchimokuTradeTestRun.df1.format(MaxPnLWhenRSIExtreme
							* Math.pow(10, instrument.getPipScale())));

		}
	}

	private IchiTradeLog tradeLog = null;
	private MultiDDLog mlLog = null;

	private int TENKAN = 0, KIJUN = 1, SENOKU_A = 3, SENOKU_B = 4;

	private Instrument chosenInstrument = null;

	@Configurable("Period ")
	public Period selectedPeriod = Period.FOUR_HOURS;
	@Configurable("Offer side ")
	public OfferSide selectedOfferSide = OfferSide.ASK;

	@Configurable("Tenkan sen")
	public int tenkan = 9;
	@Configurable("Kijun sen")
	public int kijun = 26;
	@Configurable("Senko span")
	public int senkou = 52;

	@Configurable("Slippage")
	public double slippage = 3;
	@Configurable("Amount")
	public double amount = 0.5;

	private IOrder order = null;

	private boolean
	// these two steer special situation when bar closes opposite of adverse
	// lines, but cross was
	// not over adverse lines
			wereLinesAdverse = false,
			wasSlowLineViolated = false,
			isLongTrade = false,
			entryDone = false, lastSignalWasBullish = false;

	private long
	// how many bars trade took so far
			tradeBars = 0,
			tradeEndTime = 0;
	private String dbLabel = null;

	// for signals happening on Friday close
	private boolean filled;

	private double sellStop, sellSL, buySTP, buySL;

	public IchimokuTradeTestRun(int runID, Instrument instrumentToTest,
			String label, boolean tradeDirection, long endTime, Properties props) {
		super(props);
		chosenInstrument = instrumentToTest;
		isLongTrade = tradeDirection;
		tradeEndTime = endTime;
		dbLabel = label;
		wereLinesAdverse = false;
		wasSlowLineViolated = false;
	}

	public void onStart(IContext context) throws JFException {
		super.onStartExec(context);
		wereLinesAdverse = false;
		wasSlowLineViolated = false;
		dbLogOnStart();
		dbDeleteTestRunData();

		// need only one header for exit reports
		// log.print(
		// "ER;" // for "Exit Report"
		// + "orderLabel;"
		// + "direction;"
		// + "signalTime;"
		// + "fillTime;"
		// + "exitTime;"
		// + "exitReason;"
		// + "entryPrice;"
		// + "fillPrice;"
		// + "SL;"
		// + "initialRisk;"
		// + "maxRisk;"
		// + "maxLoss;"
		// + "maxLossATR;"
		// + "maxDD;"
		// + "maxDDATR;"
		// + "maxDDTime;"
		// + "maxProfit;"
		// + "maxProfitPrice;"
		// + "PnL;"
		// + "missedProfit;"
		// + "missedProfitPerc;"
		// + "entryTenkan;"
		// + "entryBorderTopDirection;"
		// + "entryBorderBottomDirection;"
		// + "entryCloud;"
		// + "PnLAtLastKijun;"
		// + "RatioCloudATR;"
		// + "RSIExtremeEntries;"
		// + "FirstRSIStayExtremeBars;"
		// + "PnLAtFirstRSIStayExtreme;"
		// + "MaxPnLWhenRSIExtreme");
	}

	public void onAccount(IAccount account) throws JFException {
	}

	public void onMessage(IMessage message) throws JFException {
		if (message.getType() == IMessage.Type.ORDER_CLOSE_OK) {
			Set<IMessage.Reason> reasons = message.getReasons();
			String reasonsStr = new String();
			for (IMessage.Reason r : reasons) {
				reasonsStr += r.toString();
			}
			log.print("In onMessage; time: "
					+ DateUtils.format(message.getCreationTime()) + ", order: "
					+ (message.getOrder() != null ? order.getLabel() : "null")
					+ " closed; message: " + message.getContent()
					+ "; reasons: " + reasonsStr);

			tradeLog.exitTime = message.getCreationTime();
			if (tradeLog.exitReason == null)
				tradeLog.exitReason = reasonsStr;
			tradeLog.PnL = order.getProfitLossInPips();
			tradeLog.exitReport(message.getOrder().getInstrument(), log);

			dbRecordMLDrawDowns(order.getLabel(), order.getInstrument(), mlLog);

			dbMarkTestRunDone();

			order = null;
			tradeLog = null;
		} else {
			if (message.getType() == IMessage.Type.ORDER_FILL_OK) {
				tradeLog.fillTime = message.getCreationTime();
				tradeLog.fillPrice = message.getOrder().getOpenPrice();
				log.print("In onMessage, order filled; Type: "
						+ message.getType()
						+ "; order "
						+ (message.getOrder() != null ? message.getOrder()
								.getLabel() : "order is null") + "; message: "
						+ message.getContent());
			}
			// log.print("In onMessage, all other; Type: " + message.getType() +
			// "; order " + (message.getOrder() != null ?
			// message.getOrder().getLabel() : "order is null") + "; message: "
			// + message.getContent());
		}
	}

	private void dbRecordMLDrawDowns(String tradeLabel, Instrument i,
			MultiDDLog pMLLog) {
		String statementStr1 = "INSERT INTO "
				+ FXUtils.getDbToUse()
				+ ".`tintradedata` (`RunLabel`, `TimeFrame`, `barTime`, `ValueName1`, `ValueD1`, `ValueName2`, `ValueD2`, `ValueName3`, `ValueD3`, `ValueName4`, `ValueD4`) VALUES (", statementStr2 = "'"
				+ tradeLabel
				+ "', '4 Hours', '"
				+ FXUtils.getMySQLTimeStamp(pMLLog.getLevel(0).DDtime)
				+ "', 'maxDD_L1', "
				+ FXUtils.df1.format(pMLLog.getLevel(0).maxDD
						* Math.pow(10, i.getPipScale()))
				+ ", 'maxDD_atr_L1', "
				+ FXUtils.df1.format(pMLLog.getLevel(0).atr
						* Math.pow(10, i.getPipScale()))
				+ ", 'maxDD_atrEMA_L1', "
				+ FXUtils.df1.format(pMLLog.getLevel(0).atrEMA
						* Math.pow(10, i.getPipScale()))
				+ ", 'maxDD_atrSMA_L1', "
				+ FXUtils.df1.format(pMLLog.getLevel(0).atrSMA
						* Math.pow(10, i.getPipScale())) + ")";
		try {
			Statement qry = logDB.createStatement();
			qry.executeUpdate(statementStr1 + statementStr2);

			statementStr2 = "'"
					+ tradeLabel
					+ "', '4 Hours', '"
					+ FXUtils.getMySQLTimeStamp(pMLLog.getLevel(1).DDtime)
					+ "', 'maxDD_L2', "
					+ FXUtils.df1.format(pMLLog.getLevel(1).maxDD
							* Math.pow(10, i.getPipScale()))
					+ ", 'maxDD_atr_L2', "
					+ FXUtils.df1.format(pMLLog.getLevel(1).atr
							* Math.pow(10, i.getPipScale()))
					+ ", 'maxDD_atrEMA_L2', "
					+ FXUtils.df1.format(pMLLog.getLevel(1).atrEMA
							* Math.pow(10, i.getPipScale()))
					+ ", 'maxDD_atrSMA_L2', "
					+ FXUtils.df1.format(pMLLog.getLevel(1).atrSMA
							* Math.pow(10, i.getPipScale())) + ")";
			qry.executeUpdate(statementStr1 + statementStr2);

			statementStr2 = "'"
					+ tradeLabel
					+ "', '4 Hours', '"
					+ FXUtils.getMySQLTimeStamp(pMLLog.getLevel(2).DDtime)
					+ "', 'maxDD_L3', "
					+ FXUtils.df1.format(pMLLog.getLevel(2).maxDD
							* Math.pow(10, i.getPipScale()))
					+ ", 'maxDD_atr_L3', "
					+ FXUtils.df1.format(pMLLog.getLevel(2).atr
							* Math.pow(10, i.getPipScale()))
					+ ", 'maxDD_atrEMA_L3', "
					+ FXUtils.df1.format(pMLLog.getLevel(2).atrEMA
							* Math.pow(10, i.getPipScale()))
					+ ", 'maxDD_atrSMA_L3', "
					+ FXUtils.df1.format(pMLLog.getLevel(2).atrSMA
							* Math.pow(10, i.getPipScale())) + ")";
			qry.executeUpdate(statementStr1 + statementStr2);

		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr1);
			log.close();
			System.exit(1);
		}
	}

	protected void dbLogOnStart() {
		try {
			Class.forName(conf.getProperty("sqlDriver",
					"sun.jdbc.odbc.JdbcOdbcDriver"));
		} catch (java.lang.ClassNotFoundException e1) {
			log.print("ODBC driver load failure (ClassNotFoundException: "
					+ e1.getMessage() + ")");
			log.close();
			System.exit(1);
		}
		String url = conf.getProperty("odbcURL");
		try {
			logDB = DriverManager.getConnection(url,
					conf.getProperty("dbUserName", ""),
					conf.getProperty("dbPassword", ""));
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.close();
			System.exit(1);
		}
	}

	private void dbMarkTestRunDone() {
		String dbWhere = new String("WHERE Label = '" + dbLabel + "'"), statementStr1 = "update "
				+ FXUtils.getDbToUse()
				+ ".ttesttrades set testRunDone = 'Y' "
				+ dbWhere, statementStr2 = "update " + FXUtils.getDbToUse()
				+ ".ttesttrades_full set testRunDone = 'Y' " + dbWhere;
		try {
			Statement qry = logDB.createStatement();
			qry.executeUpdate(statementStr1);
			qry.executeUpdate(statementStr2);
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr1);
			log.close();
			System.exit(1);
		}
	}

	private void dbDeleteTestRunData() {
		String statementStr1 = "delete from " + FXUtils.getDbToUse()
				+ ".`tintradedata` WHERE RunLabel = '" + dbLabel + "'";
		try {
			Statement qry = logDB.createStatement();
			qry.executeUpdate(statementStr1);
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr1);
			log.close();
			System.exit(1);
		}
	}

	public void onStop() throws JFException {
		log.close();

		File testRunningSignal = new File("tradeTestRunning.bin");
		if (testRunningSignal.exists())
			testRunningSignal.delete();
	}

	public void onTick(Instrument instrument, ITick tick) throws JFException {

	}

	public void onBar(Instrument instrument, Period period, IBar askBar,
			IBar bidBar) throws JFException {
		if (!instrument.equals(chosenInstrument) || period != selectedPeriod)
			return;

		Calendar calendar = new GregorianCalendar(new SimpleTimeZone(0, "GMT"));
		calendar.setTime(new Date(bidBar.getTime()));
		if ((calendar.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY && calendar
				.get(Calendar.HOUR_OF_DAY) > 20)
				|| calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
				|| (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && calendar
						.get(Calendar.HOUR_OF_DAY) < 20))
			return;

		IchiDesc currIchiSituation = trendDetector.getIchi(history, instrument,
				selectedPeriod, OfferSide.BID, bidBar.getTime());

		double[][]
		// used for cloud borders, therefore moved kijun periods in past ! Cloud
		// is drawn in future !
		i_sh = indicators.ichimoku(instrument, selectedPeriod,
				selectedOfferSide, tenkan, kijun, senkou, Filter.WEEKENDS,
				1 + kijun, bidBar.getTime(), 0),
		// lines are normally drawn...
		lines = indicators.ichimoku(instrument, selectedPeriod,
				selectedOfferSide, tenkan, kijun, senkou, Filter.WEEKENDS, 2,
				bidBar.getTime(), 0);

		double atr_stop = Math.round((indicators.atr(instrument,
				selectedPeriod, selectedOfferSide, 14, Filter.WEEKENDS, 1,
				bidBar.getTime(), 0)[0] / 4)
				* Math.pow(10, instrument.getPipScale()))
				/ Math.pow(10, instrument.getPipScale());

		long prevDayTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		double[] RSIs = indicators.rsi(instrument, selectedPeriod,
				selectedOfferSide, AppliedPrice.CLOSE, 14, Filter.WEEKENDS, 2,
				bidBar.getTime(), 0);
		double RSI1d = indicators.rsi(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, selectedOfferSide,
				AppliedPrice.CLOSE, 14, Filter.WEEKENDS, 1, prevDayTime, 0)[0];

		double
		// must be rounded to 0.1 pips since used as SL !
		i_cloudTop = Math.round(Math.max(i_sh[SENOKU_A][0], i_sh[SENOKU_B][0])
				* Math.pow(10, instrument.getPipScale())), i_cloudBottom = Math
				.round(Math.min(i_sh[SENOKU_A][0], i_sh[SENOKU_B][0])
						* Math.pow(10, instrument.getPipScale()));

		double cloudTop = i_cloudTop / Math.pow(10, instrument.getPipScale()), cloudBottom = i_cloudBottom
				/ Math.pow(10, instrument.getPipScale()), cloudWidth = cloudTop
				- cloudBottom, ratioCloudATR = cloudWidth / (atr_stop * 4), tenkan = lines[TENKAN][1], kijun = lines[KIJUN][1], prevKijun = lines[KIJUN][0];

		List<IBar> last2bars = history.getBars(instrument, selectedPeriod,
				selectedOfferSide, Filter.WEEKENDS, 2, bidBar.getTime(), 0);
		IBar bar = last2bars.get(1);
		IBar bar_1 = last2bars.get(0);

		if (order != null) {
			// log.print("Checking order " + order.getLabel() + (order.isLong()
			// ? " (Long)" : " (Short)") + "; state " + order.getState());
			if (order.getState() == IOrder.State.FILLED) {
				tradeBars++;

				// adjust stop loss
				if (order.isLong()
						&& order.getStopLossPrice() != cloudTop - atr_stop) {
					double newSLPrice = cloudTop - atr_stop * 4;
					if (tradeEndTime - bidBar.getTime() < 48 * 3600 * 1000)
						// force exit for close cloud approaches
						newSLPrice = cloudTop + 2 * atr_stop;
					order.setStopLossPrice(newSLPrice);
					// log.print("Bar: " + DateUtils.format(bidBar.getTime()) +
					// " order " + order.getLabel() + "; Long SL changed to: " +
					// newSLPrice);
					tradeLog.updateMaxRisk(newSLPrice);
				} else if (!order.isLong()
						&& order.getStopLossPrice() != cloudBottom + atr_stop) {
					double newSLPrice = cloudBottom + atr_stop * 4;
					if (tradeEndTime - bidBar.getTime() < 48 * 3600 * 1000)
						// force exit for close cloud approaches
						newSLPrice = cloudBottom - 2 * atr_stop;
					order.setStopLossPrice(newSLPrice);
					// log.print("Bar: " + DateUtils.format(bidBar.getTime()) +
					// " order " + order.getLabel() + "; Short SL changed to: "
					// + newSLPrice);
					tradeLog.updateMaxRisk(newSLPrice);
				}

				// update trade logs too
				tradeLog.updateMaxLoss(bidBar, atr_stop * 4);
				tradeLog.updateMaxProfit(bidBar);
				tradeLog.updateMaxDD(bidBar, kijun, atr_stop * 4);
				tradeLog.updatePnLAtLastKijun(bidBar, bar_1, kijun, prevKijun);
				tradeLog.updateRSI(RSIs, askBar);

				double[] cloudDist = cloudDistCalc(instrument, bidBar);
				double currProfit = 0.0;
				if (tradeLog.isLong) {
					currProfit = bidBar.getHigh() - tradeLog.fillPrice;
				} else {
					currProfit = tradeLog.fillPrice - bidBar.getLow();
				}
				// log.print("CloudDist;" + order.getLabel() + ";" +
				// DateUtils.format(bidBar.getTime()) + ";" +
				// df1.format(currProfit * Math.pow(10,
				// instrument.getPipScale())) + ";" + df2.format(cloudDist[0]) +
				// ";" + df2.format(cloudDist[1]));
				dbRecordCloudDist(
						order.getLabel(),
						FXUtils.getMySQLTimeStamp(bidBar.getTime()),
						df1.format(currProfit
								* Math.pow(10, instrument.getPipScale())),
						df2.format(cloudDist[0]), df2.format(cloudDist[1]));

				dbRecordRSI(order.getLabel(),
						FXUtils.getMySQLTimeStamp(bidBar.getTime()),
						df1.format(RSIs[1]), df1.format(RSI1d));

				double[] avgPriceChanges = calcPriceChangeAvgs(instrument,
						bidBar), atrAvgs = calcATRAvgs(instrument, bidBar);
				double avgATR = atrAvgs[0], avgATREMA = atrAvgs[1];
				dbRecordProfits(order.getLabel(),
						FXUtils.getMySQLTimeStamp(bidBar.getTime()),
						FXUtils.if1.format(tradeBars), df1.format(FXUtils
								.toPips(currProfit, instrument)),
						df1.format(FXUtils.toPips(atr_stop * 4, instrument)),
						df1.format(FXUtils.toPips(avgATR, instrument)),
						df1.format(FXUtils.toPips(avgATREMA, instrument)),
						df1.format(FXUtils.toPips(avgPriceChanges[0],
								instrument)), df1.format(FXUtils.toPips(
								avgPriceChanges[1], instrument)));

				if (order.isLong()) {
					if (bidBar.getClose() > kijun
							&& bidBar.getLow() >= tradeLog.fillPrice) {
						mlLog.update(bidBar, tradeLog.fillPrice,
								tradeLog.maxProfitPrice,
								tradeLog.maxProfitTime, atr_stop * 4,
								avgATREMA, avgATR);
					}

					TriggerDesc adverseCandles = tradeTrigger
							.bearishReversalCandlePatternDesc(instrument,
									selectedPeriod, OfferSide.BID,
									bidBar.getTime());
					if (adverseCandles != null) {
						double combinedCandleSize = tradeTrigger
								.barLengthStatPos(instrument, selectedPeriod,
										OfferSide.BID, bidBar,
										adverseCandles.getCombinedHigh(),
										adverseCandles.getCombinedLow(),
										FXUtils.QUARTER_WORTH_OF_4h_BARS);
						if (combinedCandleSize >= 2.0) {
							double bodyDirection = 1.0;
							if (!adverseCandles.combinedRealBodyDirection)
								bodyDirection = -1.0;
							dbRecordAdverseCandles(
									order.getLabel(),
									FXUtils.getMySQLTimeStamp(bidBar.getTime()),
									adverseCandles.type.toString(),
									df2.format(combinedCandleSize),
									df1.format(adverseCandles.combinedUpperHandlePerc),
									df1.format(bodyDirection
											* adverseCandles.combinedRealBodyPerc));
						}
					}

					// interested in bearish crosses over slow line when BOTH in
					// adverse position i.e. bearish
					if (currIchiSituation.isBearishSlowLineCross
							&& currIchiSituation.linesState().equals("BEARISH"))
						dbRecordIchiLines(
								order.getLabel(),
								FXUtils.getMySQLTimeStamp(bidBar.getTime()),
								"bearish Ichi line cross over adverse lines",
								df1.format((currProfit - Math.abs(bidBar
										.getHigh() - bidBar.getLow()))
										* Math.pow(10, instrument.getPipScale())),
								df2.format(cloudDist[0]), df2
										.format(cloudDist[1]));
					else if (currIchiSituation.isBearishSlowLineCross
							&& currIchiSituation.linesState().equals("FLAT"))
						dbRecordIchiLines(
								order.getLabel(),
								FXUtils.getMySQLTimeStamp(bidBar.getTime()),
								"bearish Ichi line cross over flat lines",
								df1.format((currProfit - Math.abs(bidBar
										.getHigh() - bidBar.getLow()))
										* Math.pow(10, instrument.getPipScale())),
								df2.format(cloudDist[0]), df2
										.format(cloudDist[1]));
					else if (!wereLinesAdverse
							&& currIchiSituation.linesState().equals("BEARISH")
							&& !currIchiSituation.isCloseAboveSlowLine) {
						dbRecordIchiLines(
								order.getLabel(),
								FXUtils.getMySQLTimeStamp(bidBar.getTime()),
								"bearish Ichi line close over adverse lines",
								df1.format((currProfit - Math.abs(bidBar
										.getHigh() - bidBar.getLow()))
										* Math.pow(10, instrument.getPipScale())),
								df2.format(cloudDist[0]), df2
										.format(cloudDist[1]));
					}
					// update states for next bar
					wereLinesAdverse = currIchiSituation.linesState().equals(
							"BEARISH");
					this.wasSlowLineViolated = !currIchiSituation.isCloseAboveSlowLine;

				} else {

					if (bidBar.getClose() < kijun
							&& bidBar.getHigh() <= tradeLog.fillPrice)
						mlLog.update(bidBar, tradeLog.fillPrice,
								tradeLog.maxProfitPrice,
								tradeLog.maxProfitTime, atr_stop * 4,
								avgATREMA, avgATR);

					TriggerDesc adverseCandles = tradeTrigger
							.bullishReversalCandlePatternDesc(instrument,
									selectedPeriod, OfferSide.BID,
									bidBar.getTime());
					if (adverseCandles != null) {
						double combinedCandleSize = tradeTrigger
								.barLengthStatPos(instrument, selectedPeriod,
										OfferSide.BID, bidBar,
										adverseCandles.getCombinedHigh(),
										adverseCandles.getCombinedLow(),
										FXUtils.QUARTER_WORTH_OF_4h_BARS);
						if (combinedCandleSize >= 2.0) {
							double bodyDirection = 1.0;
							if (!adverseCandles.combinedRealBodyDirection)
								bodyDirection = -1.0;
							dbRecordAdverseCandles(
									order.getLabel(),
									FXUtils.getMySQLTimeStamp(bidBar.getTime()),
									adverseCandles.type.toString(),
									df2.format(combinedCandleSize),
									df1.format(adverseCandles.combinedLowerHandlePerc),
									df1.format(bodyDirection
											* adverseCandles.combinedRealBodyPerc));
						}
					}

					// interested in bullish crosses over slow line when BOTH in
					// adverse position i.e. bullish
					if (currIchiSituation.isBullishSlowLineCross
							&& currIchiSituation.linesState().equals("BULLISH"))
						dbRecordIchiLines(
								order.getLabel(),
								FXUtils.getMySQLTimeStamp(bidBar.getTime()),
								"bullish Ichi line cross over adverse lines",
								df1.format((currProfit - Math.abs(bidBar
										.getHigh() - bidBar.getLow()))
										* Math.pow(10, instrument.getPipScale())),
								df2.format(cloudDist[0]), df2
										.format(cloudDist[1]));
					else if (currIchiSituation.isBullishSlowLineCross
							&& currIchiSituation.linesState().equals("FLAT"))
						dbRecordIchiLines(
								order.getLabel(),
								FXUtils.getMySQLTimeStamp(bidBar.getTime()),
								"bullish Ichi line cross over flat lines",
								df1.format((currProfit - Math.abs(bidBar
										.getHigh() - bidBar.getLow()))
										* Math.pow(10, instrument.getPipScale())),
								df2.format(cloudDist[0]), df2
										.format(cloudDist[1]));
					else if (!wereLinesAdverse
							&& currIchiSituation.linesState().equals("BULLISH")
							&& currIchiSituation.isCloseAboveSlowLine) {
						dbRecordIchiLines(
								order.getLabel(),
								FXUtils.getMySQLTimeStamp(bidBar.getTime()),
								"bullish Ichi line close over adverse lines",
								df1.format((currProfit - Math.abs(bidBar
										.getHigh() - bidBar.getLow()))
										* Math.pow(10, instrument.getPipScale())),
								df2.format(cloudDist[0]), df2
										.format(cloudDist[1]));
					}

					// update states for next bar
					wereLinesAdverse = currIchiSituation.linesState().equals(
							"BULLISH");
					this.wasSlowLineViolated = currIchiSituation.isCloseAboveSlowLine;
				}

				return;
			} else {
				if (order.isLong()) {
					if (bar.getClose() < cloudTop) {
						log.print("Bar: " + DateUtils.format(bidBar.getTime())
								+ " cancelling BUY order " + order.getLabel());
						tradeLog.exitReason = new String("cancelled");
						order.close();
						order.waitForUpdate();
					} else {
						// need to adjust SL even if order still not filled
						// since cloud might move
						if (order.getStopLossPrice() != cloudTop - atr_stop) {
							// order.setStopLossPrice(cloudTop - atr_stop);
							// log.print("Bar: " +
							// DateUtils.format(bidBar.getTime()) + " order " +
							// order.getLabel() + "; Long SL changed to: " +
							// (cloudTop - 4 * atr_stop));
							// tradeLog.updateMaxRisk(cloudTop - 4 * atr_stop);
						}
					}
					// this will call onMessage, which sets order to null
				} else if (!order.isLong()) {
					if (bar.getClose() > cloudBottom) {
						log.print("Bar: " + DateUtils.format(bidBar.getTime())
								+ " cancelling SELL order " + order.getLabel());
						tradeLog.exitReason = new String("cancelled");
						order.close();
						order.waitForUpdate();
						// this will call onMessage, which, sets order to null
					} else if (order.getStopLossPrice() != cloudBottom
							+ atr_stop) {
						// need to adjust SL even if order still not filled
						// since cloud might move
						// order.setStopLossPrice(cloudBottom + atr_stop);
						// log.print("Bar: " +
						// DateUtils.format(bidBar.getTime()) + " order " +
						// order.getLabel() + "; Short SL changed to: " +
						// (cloudBottom + 4 * atr_stop));
						// tradeLog.updateMaxRisk(cloudBottom + 4 * atr_stop);
					}
				}
				// but onBar should continue in order to handle situation where
				// on the same bar there is both cancel and open of new position
				// in opposite order !!
			}
		}

		// EXTREMELY IMPORTANT !!!
		// Key to this strategy is that first bar send is defined as TRADE ENTRY
		// bar ! Entry stop should be set immediately !
		if (!entryDone || !filled) {
			boolean orderHaveBinOpened = false;
			if (!isLongTrade) {
				boolean bigGapDown = bar_1.getClose() > bar.getOpen()
						&& bar_1.getClose() - bar.getOpen() > 3 * atr_stop;
				// log.print("Before submitting SELL order: order variable is "
				// + (order != null ? order.getLabel() + "; (" + (order.isLong()
				// ? "long)" : "short)") : "null") + "; gap down: " +
				// (bar.getClose()- askBar.getOpen()) + "; 2xATR stop: " + 2 *
				// atr_stop);
				// log.print("Prev. bar " + DateUtils.format(bar_1.getTime()) +
				// "; close: " + bar_1.getClose() + "; curr. bar " +
				// DateUtils.format(bar.getTime()) + "; open: " + bar.getOpen()
				// + "; gap down: " + (bar_1.getClose()- bar.getOpen()) +
				// "; 2xATR stop: " + 2 * atr_stop);
				if (!entryDone) {
					sellStop = bar.getLow() - atr_stop;
					sellSL = cloudBottom + 4 * atr_stop;
				}
				order = submitOrder(instrument, OrderCommand.SELLSTOP,
						sellStop, sellSL);
				entryDone = true;
				log.print("Bar: "
						+ DateUtils.format(bidBar.getTime())
						+ " SELL order "
						+ order.getLabel()
						+ " submitted, state "
						+ order.getState()
						+ "; SELL STP: "
						+ order.getOpenPrice()
						+ "; SL: "
						+ order.getStopLossPrice()
						+ "; risk: "
						+ df1.format((order.getStopLossPrice() - order
								.getOpenPrice())
								* Math.pow(10, instrument.getPipScale()))
						+ "; tenkan (fast line): " + tenkan
						+ "; kijun (slow line): " + kijun);
				if (order.getState() == IOrder.State.CANCELED) {
					log.print("Order "
							+ order.getLabel()
							+ " got canceled after submit ! Setting it to null !");
					order = null;
					filled = false;
				} else {
					filled = true;
					tradeLog = new IchiTradeLog(order.getLabel(),
							order.isLong(), bidBar.getTime(), bar.getLow()
									- atr_stop, cloudBottom + 4 * atr_stop,
							order.getStopLossPrice() - order.getOpenPrice(),
							ratioCloudATR,
							currIchiSituation.isBullishTenkanLine,
							currIchiSituation.topBorderDirection,
							currIchiSituation.bottomBorderDirection,
							currIchiSituation.isBullishCloud);
					mlLog = new MultiDDLog(3, 2, false);
					orderHaveBinOpened = true;
				}
				lastSignalWasBullish = false;
			}

			if (!orderHaveBinOpened && isLongTrade) {
				boolean bigGapUp = bar_1.getClose() < bar.getOpen()
						&& bar.getOpen() - bar.getClose() > 3 * atr_stop;
				// log.print("Before submitting BUY order: order variable is " +
				// (order != null ? order.getLabel() + "; (" + (order.isLong() ?
				// "long)" : "short)") : "null") + "; gap up: " +
				// (askBar.getOpen() - bar.getClose()) + "; 2xATR stop: " + 2 *
				// atr_stop);
				// log.print("Prev. bar close: " + bar.getClose() +
				// "; curr. bar open: " + askBar.getOpen() + "; gap up: " +
				// (askBar.getOpen() - bar.getClose()) + "; 2xATR stop: " + 2 *
				// atr_stop);
				if (!entryDone) {
					buySTP = bar.getHigh() + atr_stop;
					buySL = cloudTop - 4 * atr_stop;
				}
				order = submitOrder(instrument, OrderCommand.BUYSTOP, buySTP,
						buySL);
				entryDone = true;
				log.print("Bar: "
						+ DateUtils.format(bidBar.getTime())
						+ " BUY order "
						+ order.getLabel()
						+ " submitted, state "
						+ order.getState()
						+ "; BUY STP: "
						+ order.getOpenPrice()
						+ "; SL: "
						+ order.getStopLossPrice()
						+ "; risk: "
						+ df1.format((order.getOpenPrice() - order
								.getStopLossPrice())
								* Math.pow(10, instrument.getPipScale()))
						+ "; tenkan (fast line): " + tenkan
						+ "; kijun (slow line): " + kijun);
				if (order.getState() == IOrder.State.CANCELED) {
					log.print("Order "
							+ order.getLabel()
							+ " got canceled after submit ! Setting it to null !");
					order = null;
					filled = false;
				} else {
					tradeLog = new IchiTradeLog(order.getLabel(),
							order.isLong(), bidBar.getTime(), bar.getHigh()
									+ atr_stop, cloudTop - 4 * atr_stop,
							order.getOpenPrice() - order.getStopLossPrice(),
							ratioCloudATR,
							currIchiSituation.isBullishTenkanLine,
							currIchiSituation.topBorderDirection,
							currIchiSituation.bottomBorderDirection,
							currIchiSituation.isBullishCloud);
					filled = true;
					mlLog = new MultiDDLog(3, 2, true);
				}
				lastSignalWasBullish = true;
			}
		}
	}

	private void dbRecordAdverseCandles(String tradeLabel, String BarTime,
			String candleType, String candleSize, String adverseHandlePerc,
			String bodyPerc) {
		String statementStr1 = "INSERT INTO "
				+ FXUtils.getDbToUse()
				+ ".`tintradedata` (`RunLabel`, `TimeFrame`, `barTime`, `ValueName1`, `ValueS1`, `ValueD1`, `ValueName2`, `ValueD2`, `ValueName3`, `ValueD3`) VALUES (", statementStr2 = "'"
				+ tradeLabel
				+ "', '4 Hours', '"
				+ BarTime
				+ "', 'bigAdverseCandle', '"
				+ candleType
				+ "', "
				+ candleSize
				+ ", 'adverseHandlePerc', "
				+ adverseHandlePerc
				+ ", 'bodyPerc', " + bodyPerc + ")";
		try {
			Statement qry = logDB.createStatement();
			qry.executeUpdate(statementStr1 + statementStr2);
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr1);
			log.close();
			System.exit(1);
		}
	}

	private void dbRecordProfits(String tradeLabel, String BarTime,
			String barNumber, String currProfit, String currATR,
			String currAvgATR, String currEMAATR, String currAvgPriceChange,
			String currAvgAbsPriceChange) {
		String statementStr1 = "INSERT INTO "
				+ FXUtils.getDbToUse()
				+ ".`tintradedata` (`RunLabel`, `TimeFrame`, `barTime`, `ValueName1`, `ValueD1`, `ValueName2`, `ValueD2`, `ValueName3`, `ValueD3`, `ValueName4`, `ValueD4`, `ValueName5`, `ValueD5`, `ValueName6`, `ValueD6`, `ValueName7`, `ValueD7`) VALUES (", statementStr2 = "'"
				+ tradeLabel
				+ "', '4 Hours', '"
				+ BarTime
				+ "', 'barNo', "
				+ barNumber
				+ ", 'currProfit', "
				+ currProfit
				+ ", 'ATR', "
				+ currATR
				+ ", 'avgATR', "
				+ currAvgATR
				+ ", 'emaATR', "
				+ currEMAATR
				+ ", 'avgPriceChange', "
				+ currAvgPriceChange
				+ ", 'avgAbsPriceChange', " + currAvgAbsPriceChange + ")";
		try {
			Statement qry = logDB.createStatement();
			qry.executeUpdate(statementStr1 + statementStr2);
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr1);
			log.close();
			System.exit(1);
		}
	}

	private void dbRecordIchiLines(String tradeLabel, String mySQLTimeStamp,
			String message, String PnL, String cloudDistATR, String cloudDistAbs) {
		String statementStr1 = "INSERT INTO "
				+ FXUtils.getDbToUse()
				+ ".`tintradedata` (`RunLabel`, `TimeFrame`, `barTime`, `ValueName1`, `ValueD1`, `ValueS1`, `ValueName2`, `ValueD2`, `ValueName3`, `ValueD3`) VALUES (", statementStr2 = "'"
				+ tradeLabel
				+ "', '4 Hours', '"
				+ mySQLTimeStamp
				+ "', 'PnL', "
				+ PnL
				+ ", '"
				+ message
				+ "' "
				+ ", 'CloudDistATR', "
				+ cloudDistATR
				+ ", 'CloudDistAbs', "
				+ cloudDistAbs + ")";
		try {
			Statement qry = logDB.createStatement();
			qry.executeUpdate(statementStr1 + statementStr2);
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr1);
			log.close();
			System.exit(1);
		}
	}

	private void dbRecordRSI(String tradeLabel, String BarTime, String rsi4h,
			String rsi1d) {
		String statementStr1 = "INSERT INTO "
				+ FXUtils.getDbToUse()
				+ ".`tintradedata` (`RunLabel`, `TimeFrame`, `barTime`, `ValueName1`, `ValueD1`, `ValueName2`, `ValueD2`) VALUES (", statementStr2 = "'"
				+ tradeLabel
				+ "', '4 Hours', '"
				+ BarTime
				+ "', 'RSI4h', "
				+ rsi4h + ", 'RSI1d', " + rsi1d + ")";
		try {
			Statement qry = logDB.createStatement();
			qry.executeUpdate(statementStr1 + statementStr2);
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr1);
			log.close();
			System.exit(1);
		}
	}

	private void dbRecordCloudDist(String tradeLabel, String BarTime,
			String PnL, String CloudDistATR, String CloudDistAbs) {
		String statementStr1 = "INSERT INTO "
				+ FXUtils.getDbToUse()
				+ ".`tintradedata` (`RunLabel`, `TimeFrame`, `barTime`, `ValueName1`, `ValueD1`, `ValueName2`, `ValueD2`, `ValueName3`, `ValueD3`) VALUES (", statementStr2 = "'"
				+ tradeLabel
				+ "', '4 Hours', '"
				+ BarTime
				+ "', 'PnL', "
				+ PnL
				+ ", 'CloudDistATR', "
				+ CloudDistATR
				+ ", 'CloudDistAbs', "
				+ CloudDistAbs + ")";
		try {
			Statement qry = logDB.createStatement();
			qry.executeUpdate(statementStr1 + statementStr2);
		} catch (SQLException ex) {
			log.print("Log database problem: " + ex.getMessage());
			log.print(statementStr1);
			log.close();
			System.exit(1);
		}
	}

	private String getLabel(Instrument instrument) {
		return dbLabel;
	}

	private IOrder submitOrder(Instrument instrument, OrderCommand orderCmd,
			double price, double stopLossPrice) throws JFException {
		String label = getLabel(instrument);

		return engine.submitOrder(label, instrument, orderCmd, amount, price,
				slippage, stopLossPrice, 0);
	}

	private double[] calcPriceChangeAvgs(Instrument instrument, IBar bar)
			throws JFException {
		List<IBar> bars = history.getBars(instrument, selectedPeriod,
				selectedOfferSide, Filter.WEEKENDS,
				FXUtils.QUARTER_WORTH_OF_4h_BARS, bar.getTime(), 0);
		double[] res = new double[2], priceChanges = new double[bars.size() - 1], absPriceChanges = new double[bars
				.size() - 1];
		for (int i = 0; i < bars.size() - 1; i++) {
			IBar currBar = bars.get(i + 1), prevBar = bars.get(i);
			priceChanges[i] = currBar.getClose() - prevBar.getClose();
			absPriceChanges[i] = Math.abs(currBar.getClose()
					- prevBar.getClose());
		}
		res[0] = FXUtils.average(priceChanges);
		res[1] = FXUtils.average(absPriceChanges);
		return res;
	}

	// first is sma and other ema
	private double[] calcATRAvgs(Instrument instrument, IBar bar)
			throws JFException {
		double[] atr_hist = indicators.atr(instrument, selectedPeriod,
				selectedOfferSide, 14, Filter.WEEKENDS, YEAR_WORTH_OF_4H_BARS,
				bar.getTime(), 0), res = new double[2];

		res[0] = FXUtils.average(atr_hist);
		res[1] = FXUtils.ema(atr_hist);
		return res;
	}

	private double[] cloudDistCalc(Instrument instrument, IBar bar)
			throws JFException {
		double[][]
		// used for cloud borders, therefore moved kijun periods in past ! Cloud
		// is drawn in future !
		i_sh = indicators.ichimoku(instrument, selectedPeriod,
				selectedOfferSide, tenkan, kijun, senkou, Filter.WEEKENDS,
				YEAR_WORTH_OF_4H_BARS + kijun, bar.getTime(), 0);
		double[] atr_hist = indicators.atr(instrument, selectedPeriod,
				selectedOfferSide, 14, Filter.WEEKENDS, YEAR_WORTH_OF_4H_BARS,
				bar.getTime(), 0);

		List<IBar> bars = history.getBars(instrument, selectedPeriod,
				selectedOfferSide, Filter.WEEKENDS, YEAR_WORTH_OF_4H_BARS,
				bar.getTime(), 0);
		double[] distances = new double[YEAR_WORTH_OF_4H_BARS];
		int relevantBars = 0;

		for (int i = 0; i < bars.size(); i++) {

			IBar currBar = bars.get(i);
			double i_cloudTop = Math.max(i_sh[SENOKU_A][i], i_sh[SENOKU_B][i]), i_cloudBottom = Math
					.min(i_sh[SENOKU_A][i], i_sh[SENOKU_B][i]);
			// relevant are only bars with at least one extreme outside cloud
			if (currBar.getLow() > i_cloudTop
					|| (currBar.getHigh() > i_cloudTop
							&& currBar.getLow() < i_cloudTop && currBar
							.getLow() >= i_cloudBottom)
					|| (currBar.getHigh() > i_cloudTop
							&& currBar.getLow() < i_cloudBottom && currBar
							.getClose() > i_cloudTop)) {
				distances[relevantBars++] = (currBar.getHigh() - i_cloudTop)
						/ atr_hist[i];
			} else if (currBar.getHigh() < i_cloudBottom
					|| (currBar.getLow() < i_cloudBottom
							&& currBar.getHigh() > i_cloudBottom && currBar
							.getHigh() <= i_cloudTop)
					|| (currBar.getLow() < i_cloudBottom
							&& currBar.getHigh() > i_cloudTop && currBar
							.getClose() < i_cloudBottom)) {
				distances[relevantBars++] = (i_cloudBottom - currBar.getLow())
						/ atr_hist[i];
			}
		}

		double[][]
		// used for cloud borders, therefore moved kijun periods in past ! Cloud
		// is drawn in future !
		i_sh_now = indicators.ichimoku(instrument, selectedPeriod,
				selectedOfferSide, tenkan, kijun, senkou, Filter.WEEKENDS,
				1 + kijun, bar.getTime(), 0);
		double atr = indicators.atr(instrument, selectedPeriod,
				selectedOfferSide, 14, Filter.WEEKENDS, 1, bar.getTime(), 0)[0], currDistance = 0.0, currDistanceAbs = 0.0, i_cloudTop_now = Math
				.max(i_sh_now[SENOKU_A][0], i_sh_now[SENOKU_B][0]), i_cloudBottom_now = Math
				.min(i_sh_now[SENOKU_A][0], i_sh_now[SENOKU_B][0]);
		if (bar.getLow() > i_cloudTop_now
				|| (bar.getHigh() > i_cloudTop_now
						&& bar.getLow() < i_cloudTop_now && bar.getLow() >= i_cloudBottom_now)
				|| (bar.getHigh() > i_cloudTop_now
						&& bar.getLow() < i_cloudBottom_now && bar.getClose() > i_cloudTop_now)) {
			currDistanceAbs = bar.getHigh() - i_cloudTop_now;
			currDistance = currDistanceAbs / atr;
		} else if (bar.getHigh() < i_cloudBottom_now
				|| (bar.getLow() < i_cloudBottom_now
						&& bar.getHigh() > i_cloudBottom_now && bar.getHigh() <= i_cloudTop_now)
				|| (bar.getLow() < i_cloudBottom_now
						&& bar.getHigh() > i_cloudTop_now && bar.getClose() < i_cloudBottom_now)) {
			currDistanceAbs = i_cloudBottom_now - bar.getLow();
			currDistance = currDistanceAbs / atr;
		}

		double[] stDevRes = sdFast(distances, relevantBars);
		double[] res = new double[3];

		res[0] = (currDistance - stDevRes[0]) / stDevRes[1];
		res[1] = currDistance;
		res[2] = currDistanceAbs * Math.pow(10, instrument.getPipScale());

		return res;
	}

	public static double[] sdFast(double[] data, int lastIndex) {
		// sd is sqrt of sum of (values-mean) squared divided by n - 1
		// Calculate the mean
		double mean = 0;
		final int n = lastIndex;
		double[] res = new double[2];
		if (n < 2) {
			res[0] = res[1] = Double.NaN;
			return res;
		}
		for (int i = 0; i < n; i++) {
			mean += data[i];
		}
		mean /= n;
		// calculate the sum of squares
		double sum = 0;
		for (int i = 0; i < n; i++) {
			final double v = data[i] - mean;
			sum += v * v;
		}
		// Change to ( n - 1 ) to n if you have complete data instead of a
		// sample.
		res[0] = mean;
		res[1] = Math.sqrt(sum / n);
		return res;
	}

	@Override
	protected String getStrategyName() {
		return "Ichi4h";
	}

	@Override
	protected String getReportFileName() {
		return getStrategyName() + "_";
	}

}