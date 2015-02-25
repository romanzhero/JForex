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
import jforex.techanalysis.Trend;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;

/**
 * First class to write signals into DB instead of preparing HTML email elements
 * If there is Ichi cloud breakout it calculates all necessary entry data 
 * and writes it into " + FXUtils.getDbToUse() + ".tsignal table
 * @author Sascha
 *
 */
public class IchiCloudBreakoutElement extends BaseFlexElement implements IFlexEmailElement {
	
	protected boolean signalFound = false;

	@Override
	public IFlexEmailElement cloneIt() {
		return new IchiCloudBreakoutElement();
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
			value = mailStringsMap.get("IchiCloudCross" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())),
			sqlStr = new String(SignalUtils.signalInsertStart);		
		double 
			atr = mailValuesMap.get("ATR" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue(),
			low = mailValuesMap.get("barLow" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue(),
			high = mailValuesMap.get("barHigh" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue();
		DecimalFormat df = instrument.getPipScale() == 2 ? FXUtils.df2 : FXUtils.df5;
		if (value.equals(Trend.ICHI_CLOUD_CROSS.BULLISH.toString())) {
			value = instrument.toString() + ": Bullish breakout from Ichimoku cloud (signal) ! BUY STP: " + df.format(high) 
					+ ", ATR " + FXUtils.df1.format(atr)
					+ ", SL " + df.format(FXUtils.roundToPip(high - atr / Math.pow(10, instrument.getPipScale()) * 1.5, instrument));
			color = getGreen();
			signalFound = true;
			
			sqlStr += ", ValueName1, ValueD1) "
					+ SignalUtils.getSignalInsertValues(SignalUtils.IchiCloudBreakoutID, 
					FXUtils.dbGetInstrumentID(logDB, instrument.toString()), "BUY", 
					FXUtils.timeFrameNamesMap.get(pPeriod.toString()), 
					FXUtils.getMySQLTimeStamp(SignalUtils.getBarEndTime(bidBar.getTime(), pPeriod)), 
					FXUtils.getMySQLTimeStamp(bidBar.getTime()),
					high, (high + low) / 2, 
					FXUtils.roundToPip(high - atr / Math.pow(10, instrument.getPipScale()) * 1.5, instrument),
					Double.parseDouble(FXUtils.df1.format(atr)));
			sqlStr += ", 'ATR', " + FXUtils.df1.format(atr) + ")"; 
			
			try {
				ResultSet signalsDone = FXUtils.dbReadQuery(logDB, "SELECT count(*) as signal_exists FROM " + FXUtils.getDbToUse() + ".tsignal WHERE option_id = " + SignalUtils.IchiCloudBreakoutID
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
		else if (value.equals(Trend.ICHI_CLOUD_CROSS.BEARISH.toString())) {
			value = "Bearish breakout from Ichimoku cloud (signal) ! SELL STP: " + df.format(low) 
					+ ", ATR " + FXUtils.df1.format(atr)
					+ ", SL " + df.format(FXUtils.roundToPip(high + atr / Math.pow(10, instrument.getPipScale()) * 1.5, instrument));
			color = getRed();
			signalFound = true;
			
			sqlStr += ", ValueName1, ValueD1) "
					+ SignalUtils.getSignalInsertValues(SignalUtils.IchiCloudBreakoutID, 
					FXUtils.dbGetInstrumentID(logDB, instrument.toString()), "SELL", 
					FXUtils.timeFrameNamesMap.get(pPeriod.toString()), 
					FXUtils.getMySQLTimeStamp(SignalUtils.getBarEndTime(bidBar.getTime(), pPeriod)), 
					FXUtils.getMySQLTimeStamp(bidBar.getTime()),
					low, (high + low) / 2, 
					FXUtils.roundToPip(low + atr / Math.pow(10, instrument.getPipScale()) * 1.4, instrument),
					Double.parseDouble(FXUtils.df1.format(atr)));
			sqlStr += ", 'ATR', " + FXUtils.df1.format(atr) + ")"; 
			
			try {
				ResultSet signalsDone = FXUtils.dbReadQuery(logDB, "SELECT count(*) as signal_exists FROM " + FXUtils.getDbToUse() + ".tsignal WHERE option_id = " + SignalUtils.IchiCloudBreakoutID
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
			value = mailStringsMap.get("IchiCloudCross" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())),
			sqlStr = new String(SignalUtils.signalInsertStart);		
		double 
			atr = mailValuesMap.get("ATR" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue(),
			low = mailValuesMap.get("barLow" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue(),
			high = mailValuesMap.get("barHigh" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue();
		DecimalFormat df = instrument.getPipScale() == 2 ? FXUtils.df2 : FXUtils.df5;
		if (value.equals(Trend.ICHI_CLOUD_CROSS.BULLISH.toString())) {
			value = instrument.toString() + ": Bullish breakout from Ichimoku cloud (signal) ! BUY STP: " + df.format(high) 
					+ ", ATR " + FXUtils.df1.format(atr)
					+ ", SL " + df.format(FXUtils.roundToPip(high - atr / Math.pow(10, instrument.getPipScale()) * 1.5, instrument));
			color = getGreen();
			signalFound = true;
			
			sqlStr += ", ValueName1, ValueD1) "
					+ SignalUtils.getSignalInsertValues(SignalUtils.IchiCloudBreakoutID, 
					FXUtils.dbGetInstrumentID(logDB, instrument.toString()), "BUY", 
					FXUtils.timeFrameNamesMap.get(pPeriod.toString()), 
					FXUtils.getMySQLTimeStamp(SignalUtils.getBarEndTime(bidBar.getTime(), pPeriod)), 
					FXUtils.getMySQLTimeStamp(bidBar.getTime()),
					high, (high + low) / 2, 
					FXUtils.roundToPip(high - atr / Math.pow(10, instrument.getPipScale()) * 1.5, instrument),
					Double.parseDouble(FXUtils.df1.format(atr)));
			sqlStr += ", 'ATR', " + FXUtils.df1.format(atr) + ")";
			result.insertSQL = sqlStr;
		}
		else if (value.equals(Trend.ICHI_CLOUD_CROSS.BEARISH.toString())) {
			value = "Bearish breakout from Ichimoku cloud (signal) ! SELL STP: " + df.format(low) 
					+ ", ATR " + FXUtils.df1.format(atr)
					+ ", SL " + df.format(FXUtils.roundToPip(high + atr / Math.pow(10, instrument.getPipScale()) * 1.5, instrument));
			color = getRed();
			signalFound = true;
			
			sqlStr += ", ValueName1, ValueD1) "
					+ SignalUtils.getSignalInsertValues(SignalUtils.IchiCloudBreakoutID, 
					FXUtils.dbGetInstrumentID(logDB, instrument.toString()), "SELL", 
					FXUtils.timeFrameNamesMap.get(pPeriod.toString()), 
					FXUtils.getMySQLTimeStamp(SignalUtils.getBarEndTime(bidBar.getTime(), pPeriod)), 
					FXUtils.getMySQLTimeStamp(bidBar.getTime()),
					low, (high + low) / 2, 
					FXUtils.roundToPip(low + atr / Math.pow(10, instrument.getPipScale()) * 1.4, instrument),
					Double.parseDouble(FXUtils.df1.format(atr)));
			sqlStr += ", 'ATR', " + FXUtils.df1.format(atr) + ")"; 
			result.insertSQL = sqlStr;
			
		}
		result.mailBody = new String("<tr><td><span style=\"background-color:" + color + "; display:block; margin:0 1px; color:#fff;\">" 
				+ value + "</span></td></tr>");
		return result; 
	}
	
	@Override
	public boolean isSignal() {
		return signalFound;
	}	

}
