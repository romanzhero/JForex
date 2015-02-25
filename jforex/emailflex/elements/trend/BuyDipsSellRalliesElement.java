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

import com.dukascopy.api.IBar;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.Period;

import jforex.emailflex.BaseFlexElement;
import jforex.emailflex.IFlexEmailElement;
import jforex.emailflex.SignalUtils;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;

/**
 * Writes signals into DB instead of preparing HTML email elements
 * If there is simple trend following signal of buy dip / sell rallies it calculates all necessary entry data 
 * and writes it into tsignal table
 * @author Sascha
 *
 */
public class BuyDipsSellRalliesElement extends BaseFlexElement implements IFlexEmailElement {
	
	protected boolean signalFound = false;

	@Override
	public IFlexEmailElement cloneIt() {
		return new BuyDipsSellRalliesElement();
	}

	@Override
	public IFlexEmailElement cloneIt(Properties conf) {
		return cloneIt();
	}
	
	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine, Connection logDB) {
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}
		
		String
			color = getWhite(),
			value = mailStringsMap.get("BuyDipsSellRallies" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())),
			sqlStr = new String(SignalUtils.signalInsertStart);		
		double 
			atr = mailValuesMap.get("ATR" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue(),
			low = mailValuesMap.get("barLow" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue(),
			high = mailValuesMap.get("barHigh" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue();
		DecimalFormat df = instrument.getPipScale() == 2 ? FXUtils.df2 : FXUtils.df5;
		if (isUptrendDip(mailStringsMap, mailValuesMap, pPeriod)) {
			value = instrument.toString() + ": Buy dip bullish trend following signal ! BUY STP: " + df.format(high) 
					+ ", ATR " + FXUtils.df1.format(atr)
					+ ", SL " + df.format(FXUtils.roundToPip(high - atr / Math.pow(10, instrument.getPipScale()) * 1.5, instrument));
			color = getGreen();
			signalFound = true;
			
			sqlStr += ", ValueName1, ValueD1) "
					+ SignalUtils.getSignalInsertValues(SignalUtils.BuyDipsSellRalliesID, 
					FXUtils.dbGetInstrumentID(logDB, instrument.toString()), "BUY", 
					FXUtils.timeFrameNamesMap.get(pPeriod.toString()), 
					FXUtils.getMySQLTimeStamp(SignalUtils.getBarEndTime(bidBar.getTime(), pPeriod)), 
					FXUtils.getMySQLTimeStamp(bidBar.getTime()),
					high, (high + low) / 2, 
					FXUtils.roundToPip(high - atr / Math.pow(10, instrument.getPipScale()) * 1.5, instrument),
					atr);
			sqlStr += ", 'ATR', " + FXUtils.df1.format(atr) + ")"; 
			
			try {
				ResultSet signalsDone = FXUtils.dbReadQuery(logDB, "SELECT count(*) as signal_exists FROM " 
						+ FXUtils.getDbToUse() + ".tsignal WHERE option_id = " + SignalUtils.BuyDipsSellRalliesID
						+ " AND instrument_id = " + FXUtils.dbGetInstrumentID(logDB, instrument.toString()) 
						+ " AND direction = 'BUY'"
						+ " AND TimeFrame = '" + FXUtils.timeFrameNamesMap.get(pPeriod.toString()) + "'"
						+ " AND signalTime = '" + FXUtils.getMySQLTimeStamp(SignalUtils.getBarEndTime(bidBar.getTime(), pPeriod)) + "'"
						+ " AND barTime = '" + FXUtils.getMySQLTimeStamp(bidBar.getTime()) + "'");
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
		else if (isDownTrendRally(mailStringsMap, mailValuesMap, pPeriod)) {
			value = "Sell rally bearish trend following signal ! SELL STP: " + df.format(low) 
					+ ", ATR " + FXUtils.df1.format(atr)
					+ ", SL " + df.format(FXUtils.roundToPip(high + atr / Math.pow(10, instrument.getPipScale()) * 1.5, instrument));
			color = getRed();
			signalFound = true;
			
			sqlStr += ", ValueName1, ValueD1) "
					+ SignalUtils.getSignalInsertValues(SignalUtils.BuyDipsSellRalliesID, 
					FXUtils.dbGetInstrumentID(logDB, instrument.toString()), "SELL", 
					FXUtils.timeFrameNamesMap.get(pPeriod.toString()), 
					FXUtils.getMySQLTimeStamp(SignalUtils.getBarEndTime(bidBar.getTime(), pPeriod)), 
					FXUtils.getMySQLTimeStamp(bidBar.getTime()),
					low, (high + low) / 2, 
					FXUtils.roundToPip(low + atr / Math.pow(10, instrument.getPipScale()) * 1.4, instrument),
					atr);
			sqlStr += ", 'ATR', " + FXUtils.df1.format(atr) + ")"; 
			
			try {
				ResultSet signalsDone = FXUtils.dbReadQuery(logDB, "SELECT count(*) as signal_exists FROM " 
						+ FXUtils.getDbToUse() + ".tsignal WHERE option_id = " + SignalUtils.BuyDipsSellRalliesID
						+ " AND instrument_id = " + FXUtils.dbGetInstrumentID(logDB, instrument.toString()) 
						+ " AND direction = 'SELL'"
						+ " AND TimeFrame = '" + FXUtils.timeFrameNamesMap.get(pPeriod.toString()) + "'"
						+ " AND signalTime = '" + FXUtils.getMySQLTimeStamp(SignalUtils.getBarEndTime(bidBar.getTime(), pPeriod)) + "'"
						+ " AND barTime = '" + FXUtils.getMySQLTimeStamp(bidBar.getTime()) + "'");
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
		
		return new String("<tr><td><span style=\"background-color:" + color + "; display:block; margin:0 1px; color:#fff;\">" 
				+ value + "</span></td></tr>"); 
	}
	
	@Override
	public SignalResult detectSignal(Instrument instrument, Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine, Connection logDB) {
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}
		
		SignalResult result = new SignalResult();
		String
			color = getWhite(),
			value = mailStringsMap.get("BuyDipsSellRallies" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())),
			sqlStr = new String(SignalUtils.signalInsertStart);		
		double 
			atr = mailValuesMap.get("ATR" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue(),
			low = mailValuesMap.get("barLow" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue(),
			high = mailValuesMap.get("barHigh" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue();
		DecimalFormat df = instrument.getPipScale() == 2 ? FXUtils.df2 : FXUtils.df5;
		if (isUptrendDip(mailStringsMap, mailValuesMap, pPeriod)) {
			value = instrument.toString() + ": Buy dip bullish trend following signal ! BUY STP: " + df.format(high) 
					+ ", ATR " + FXUtils.df1.format(atr)
					+ ", SL " + df.format(FXUtils.roundToPip(high - atr / Math.pow(10, instrument.getPipScale()) * 1.5, instrument));
			color = getGreen();
			signalFound = true;
			
			sqlStr += ", ValueName1, ValueD1) "
					+ SignalUtils.getSignalInsertValues(SignalUtils.BuyDipsSellRalliesID, 
					FXUtils.dbGetInstrumentID(logDB, instrument.toString()), "BUY", 
					FXUtils.timeFrameNamesMap.get(pPeriod.toString()), 
					FXUtils.getMySQLTimeStamp(SignalUtils.getBarEndTime(bidBar.getTime(), pPeriod)), 
					FXUtils.getMySQLTimeStamp(bidBar.getTime()),
					high, (high + low) / 2, 
					FXUtils.roundToPip(high - atr / Math.pow(10, instrument.getPipScale()) * 1.5, instrument),
					atr);
			sqlStr += ", 'ATR', " + FXUtils.df1.format(atr) + ")"; 
			result.insertSQL = sqlStr;
		}
		else if (isDownTrendRally(mailStringsMap, mailValuesMap, pPeriod)) {
			value = "Sell rally bearish trend following signal ! SELL STP: " + df.format(low) 
					+ ", ATR " + FXUtils.df1.format(atr)
					+ ", SL " + df.format(FXUtils.roundToPip(high + atr / Math.pow(10, instrument.getPipScale()) * 1.5, instrument));
			color = getRed();
			signalFound = true;
			
			sqlStr += ", ValueName1, ValueD1) "
					+ SignalUtils.getSignalInsertValues(SignalUtils.BuyDipsSellRalliesID, 
					FXUtils.dbGetInstrumentID(logDB, instrument.toString()), "SELL", 
					FXUtils.timeFrameNamesMap.get(pPeriod.toString()), 
					FXUtils.getMySQLTimeStamp(SignalUtils.getBarEndTime(bidBar.getTime(), pPeriod)), 
					FXUtils.getMySQLTimeStamp(bidBar.getTime()),
					low, (high + low) / 2, 
					FXUtils.roundToPip(low + atr / Math.pow(10, instrument.getPipScale()) * 1.4, instrument),
					atr);
			sqlStr += ", 'ATR', " + FXUtils.df1.format(atr) + ")"; 
			result.insertSQL = sqlStr;
		}
		result.mailBody = new String("<tr><td><span style=\"background-color:" + color + "; display:block; margin:0 1px; color:#fff;\">" 
				+ value + "</span></td></tr>");
		return result; 
	}
	

	private boolean isDownTrendRally(Map<String, String> mailStringsMap, Map<String, FlexLogEntry> mailValuesMap, Period pPeriod) {
		String trendState = mailStringsMap.get("TrendId" + FXUtils.timeFrameNamesMap.get(pPeriod.toString()));
		double trendStrength = ((Double)mailValuesMap.get("MAsDistance" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getValue()).doubleValue();
		if (!((trendState.equals("DOWN_STRONG") || trendState.equals("DOWN_MILD")) && trendStrength > -0.7))
			return false;
		String candleTrigger = isBearishCandle(mailStringsMap, mailValuesMap, pPeriod);
		if (candleTrigger == null || candleTrigger.contains("3"))
			return false;
		
		Double
			triggerHighChannelPos = (Double)mailValuesMap.get("bearishCandleTriggerChannelPos" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getValue(),
			volatility = (Double)mailValuesMap.get("volatility" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getValue(),
			triggerHighKChannelPos = (Double)mailValuesMap.get("bearishCandleTriggerKChannelPos" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getValue();
	
		return (volatility > 70 && triggerHighChannelPos > 90.0) 
				|| (volatility <= 70.0 && triggerHighChannelPos > 105.0 && triggerHighKChannelPos > 85.0);
	}

	private boolean isUptrendDip(Map<String, String> mailStringsMap, Map<String, FlexLogEntry> mailValuesMap, Period pPeriod) {
		String trendState = mailStringsMap.get("TrendId" + FXUtils.timeFrameNamesMap.get(pPeriod.toString()));
		double trendStrength = ((Double)mailValuesMap.get("MAsDistance" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getValue()).doubleValue();
		if (!((trendState.equals("UP_STRONG") || trendState.equals("UP_MILD")) && trendStrength > -0.7))
			return false;
		String candleTrigger = isBullishCandle(mailStringsMap, mailValuesMap, pPeriod);
		if (candleTrigger == null || candleTrigger.contains("3"))
			return false;
		
		Double
			triggerLowChannelPos = (Double)mailValuesMap.get("bullishCandleTriggerChannelPos" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getValue(),
			volatility = (Double)mailValuesMap.get("volatility" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getValue(),
			triggerLowKChannelPos = (Double)mailValuesMap.get("bullishCandleTriggerKChannelPos" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getValue();
	
		return (volatility > 70 && triggerLowChannelPos < 10.0) 
				|| (volatility <= 70.0 && triggerLowChannelPos < -5.0 && triggerLowKChannelPos < 15.0);
	}
	
	private String isBullishCandle(Map<String, String> mailStringsMap, Map<String, FlexLogEntry> mailValuesMap, Period pPeriod) {
		String 
			candles = mailStringsMap.get("CandleTrigger" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())),
			bullishCandles = null;
		if (candles != null && !candles.toUpperCase().equals("NONE")) {
			int positionOfAnd = candles.indexOf(" AND ");
			if (positionOfAnd > 0) {
				String firstCandle = candles.substring(0, positionOfAnd);
				String secondCandle = candles.substring(positionOfAnd + 5);
				if (firstCandle.contains("BULLISH")) {
					bullishCandles = new String(firstCandle);					
				}					
				else {
					bullishCandles = new String(secondCandle);					
				}					
			} else {
				if (candles.contains("BULLISH")) 
					bullishCandles = new String(candles);
			}		
			return bullishCandles;
		} else
			return null;
	}
	
	private String isBearishCandle(Map<String, String> mailStringsMap, Map<String, FlexLogEntry> mailValuesMap, Period pPeriod) {
		String 
			candles = mailStringsMap.get("CandleTrigger" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())),
			bearishCandles = null;
		if (candles != null && !candles.toUpperCase().equals("NONE")) {
			int positionOfAnd = candles.indexOf(" AND ");
			if (positionOfAnd > 0) {
				String firstCandle = candles.substring(0, positionOfAnd);
				String secondCandle = candles.substring(positionOfAnd + 5);
				if (firstCandle.contains("BEARISH")) {
					bearishCandles = new String(firstCandle);					
				}					
				else {
					bearishCandles = new String(secondCandle);					
				}					
			} else {
				if (candles.contains("BEARISH")) {
					bearishCandles = new String(candles);
				} 					
			}		
			return bearishCandles;
		} else
			return null;
	}
	
	@Override
	public boolean isSignal() {
		return signalFound;
	}	

}
