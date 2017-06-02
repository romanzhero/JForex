package jforex.emailflex.elements.trend;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.AppliedPrice;

import jforex.emailflex.BaseFlexElement;
import jforex.emailflex.IFlexEmailElement;
import jforex.emailflex.SignalUtils;
import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.utils.FXUtils;
import jforex.utils.log.FlexLogEntry;

/**
 * Writes signals into DB instead of preparing HTML email elements If there is
 * cross betweem simple MA20 and MA50 it calculates all necessary entry data and
 * writes it into tsignal table
 * 
 * @author Sascha
 * 
 */
public class SMACrossElementSlow extends BaseFlexElement implements
		IFlexEmailElement {

	protected boolean signalFound = false;

	@Override
	public boolean isGeneric() {
		return false;
	}

	@Override
	public IFlexEmailElement cloneIt() {
		return new SMACrossElementSlow();
	}

	@Override
	public IFlexEmailElement cloneIt(Properties conf) {
		return cloneIt();
	}

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			IHistory history, IIndicators indicators, Trend trendDetector,
			Channel channelPosition, Momentum momentum, Volatility vola,
			TradeTrigger tradeTrigger, Properties conf,
			List<FlexLogEntry> logLine, Connection logDB) throws JFException {
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}

		String color = getWhite(), value = new String("SMA Cross"), sqlStr = new String(
				SignalUtils.signalInsertStart);
		double atr = mailValuesMap.get(
				"ATR" + FXUtils.timeFrameNamesMap.get(pPeriod.toString()))
				.getDoubleValue(), low = mailValuesMap.get(
				"barLow" + FXUtils.timeFrameNamesMap.get(pPeriod.toString()))
				.getDoubleValue(), high = mailValuesMap.get(
				"barHigh" + FXUtils.timeFrameNamesMap.get(pPeriod.toString()))
				.getDoubleValue();
		DecimalFormat df = instrument.getPipScale() == 2 ? FXUtils.df2
				: FXUtils.df5;
		if (isBullishSMACross(mailStringsMap, mailValuesMap, instrument,
				pPeriod, history, indicators, bidBar)) {
			value = instrument.toString()
					+ ": Bulish SMA cross (MA50 / MA100) trend following signal ! BUY STP: "
					+ df.format(high)
					+ ", ATR "
					+ FXUtils.df1.format(atr)
					+ ", SL "
					+ df.format(FXUtils.roundToPip(
							high - atr / Math.pow(10, instrument.getPipScale())
									* 1.5, instrument));
			color = getGreen();
			signalFound = true;

			sqlStr += ", ValueName1, ValueD1) "
					+ SignalUtils.getSignalInsertValues(
							SignalUtils.SMACrossSlowID, FXUtils
									.dbGetInstrumentID(logDB,
											instrument.toString()), "BUY",
							FXUtils.timeFrameNamesMap.get(pPeriod.toString()),
							FXUtils.getMySQLTimeStamp(SignalUtils
									.getBarEndTime(bidBar.getTime(), pPeriod)),
							FXUtils.getMySQLTimeStamp(bidBar.getTime()), high,
							(high + low) / 2, FXUtils.roundToPip(high - atr
									/ Math.pow(10, instrument.getPipScale())
									* 1.5, instrument), atr);
			sqlStr += ", 'ATR', " + FXUtils.df1.format(atr) + ")";

			try {
				ResultSet signalsDone = FXUtils.dbReadQuery(
						logDB,
						"SELECT count(*) as signal_exists FROM "
								+ FXUtils.getDbToUse()
								+ ".tsignal WHERE option_id = "
								+ SignalUtils.SMACrossSlowID
								+ " AND instrument_id = "
								+ FXUtils.dbGetInstrumentID(logDB,
										instrument.toString())
								+ " AND direction = 'BUY'"
								+ " AND TimeFrame = '"
								+ FXUtils.timeFrameNamesMap.get(pPeriod
										.toString())
								+ "'"
								+ " AND signalTime = '"
								+ FXUtils.getMySQLTimeStamp(SignalUtils
										.getBarEndTime(bidBar.getTime(),
												pPeriod)) + "'"
								+ " AND barTime = '"
								+ FXUtils.getMySQLTimeStamp(bidBar.getTime())
								+ "'");
				int existing_signals = 0;
				if (signalsDone.next()) {
					existing_signals = signalsDone.getInt("signal_exists");
				}

				if (existing_signals == 0) {
					Statement qry = logDB.createStatement();
					qry.executeUpdate(sqlStr);
				}
			} catch (SQLException ex) {
				System.out.print("Log database problem: " + ex.getMessage());
				System.out.print(sqlStr);
				System.exit(1);
			}
		} else if (isBearishSMACross(mailStringsMap, mailValuesMap, instrument,
				pPeriod, history, indicators, bidBar)) {
			value = "Bearish SMA cross (MA20 / MA50) trend following signal ! SELL STP: "
					+ df.format(low)
					+ ", ATR "
					+ FXUtils.df1.format(atr)
					+ ", SL "
					+ df.format(FXUtils.roundToPip(
							high + atr / Math.pow(10, instrument.getPipScale())
									* 1.5, instrument));
			color = getRed();
			signalFound = true;

			sqlStr += ", ValueName1, ValueD1) "
					+ SignalUtils.getSignalInsertValues(
							SignalUtils.SMACrossSlowID, FXUtils
									.dbGetInstrumentID(logDB,
											instrument.toString()), "SELL",
							FXUtils.timeFrameNamesMap.get(pPeriod.toString()),
							FXUtils.getMySQLTimeStamp(SignalUtils
									.getBarEndTime(bidBar.getTime(), pPeriod)),
							FXUtils.getMySQLTimeStamp(bidBar.getTime()), low,
							(high + low) / 2, FXUtils.roundToPip(low + atr
									/ Math.pow(10, instrument.getPipScale())
									* 1.4, instrument), atr);
			sqlStr += ", 'ATR', " + FXUtils.df1.format(atr) + ")";

			try {
				ResultSet signalsDone = FXUtils.dbReadQuery(
						logDB,
						"SELECT count(*) as signal_exists FROM "
								+ FXUtils.getDbToUse()
								+ ".tsignal WHERE option_id = "
								+ SignalUtils.SMACrossSlowID
								+ " AND instrument_id = "
								+ FXUtils.dbGetInstrumentID(logDB,
										instrument.toString())
								+ " AND direction = 'SELL'"
								+ " AND TimeFrame = '"
								+ FXUtils.timeFrameNamesMap.get(pPeriod
										.toString())
								+ "'"
								+ " AND signalTime = '"
								+ FXUtils.getMySQLTimeStamp(SignalUtils
										.getBarEndTime(bidBar.getTime(),
												pPeriod)) + "'"
								+ " AND barTime = '"
								+ FXUtils.getMySQLTimeStamp(bidBar.getTime())
								+ "'");
				int existing_signals = 0;
				if (signalsDone.next()) {
					existing_signals = signalsDone.getInt("signal_exists");
				}

				if (existing_signals == 0) {
					Statement qry = logDB.createStatement();
					qry.executeUpdate(sqlStr);
				}
			} catch (SQLException ex) {
				System.out.print("Log database problem: " + ex.getMessage());
				System.out.print(sqlStr);
				System.exit(1);
			}
		}

		return new String("<tr><td><span style=\"background-color:" + color
				+ "; display:block; margin:0 1px; color:#fff;\">" + value
				+ "</span></td></tr>");
	}

	private boolean isBearishSMACross(Map<String, String> mailStringsMap,
			Map<String, FlexLogEntry> mailValuesMap, Instrument instrument,
			Period pPeriod, IHistory history, IIndicators indicators,
			IBar bidBar) throws JFException {
		double[] sma50 = indicators
				.sma(instrument, pPeriod, OfferSide.ASK, AppliedPrice.CLOSE,
						50, Filter.WEEKENDS, 2, bidBar.getTime(), 0), sma100 = indicators
				.sma(instrument, pPeriod, OfferSide.ASK, AppliedPrice.CLOSE,
						100, Filter.WEEKENDS, 2, bidBar.getTime(), 0);
		double prevSMA50 = sma50[0], currSMA50 = sma50[1], prevSMA100 = sma100[0], currSMA100 = sma100[1];

		return prevSMA50 >= prevSMA100 && currSMA50 < currSMA100;
	}

	private boolean isBullishSMACross(Map<String, String> mailStringsMap,
			Map<String, FlexLogEntry> mailValuesMap, Instrument instrument,
			Period pPeriod, IHistory history, IIndicators indicators,
			IBar askBar) throws JFException {
		double[] sma50 = indicators
				.sma(instrument, pPeriod, OfferSide.ASK, AppliedPrice.CLOSE,
						50, Filter.WEEKENDS, 2, askBar.getTime(), 0), sma100 = indicators
				.sma(instrument, pPeriod, OfferSide.ASK, AppliedPrice.CLOSE,
						100, Filter.WEEKENDS, 2, askBar.getTime(), 0);
		double prevSMA50 = sma50[0], currSMA50 = sma50[1], prevSMA100 = sma100[0], currSMA100 = sma100[1];

		return prevSMA50 <= prevSMA100 && currSMA50 > currSMA100;
	}

	@Override
	public boolean isSignal() {
		return signalFound;
	}

}
