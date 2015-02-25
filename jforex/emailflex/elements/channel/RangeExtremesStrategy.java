package jforex.emailflex.elements.channel;

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
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;
import jforex.emailflex.BaseFlexElement;
import jforex.emailflex.IFlexEmailElement;
import jforex.emailflex.SignalUtils;
import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;

/**
 * Writes signals into DB instead of preparing HTML email elements
 * Conditions:
 * 1. channel squeeze (BBands within KChannel)
 * 2. but price closed above / below upper / lower BBand
 * @author Sascha
 *
 */
public class RangeExtremesStrategy extends BaseFlexElement implements IFlexEmailElement {
	
	protected boolean signalFound = false;

	@Override
	public boolean isGeneric() {
		return false;
	}

	@Override
	public IFlexEmailElement cloneIt() {
		return new RangeExtremesStrategy();
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
		
		String
			color = getWhite(),
			value = new String("Trading range extreme"),
			sqlStr = new String(SignalUtils.signalInsertStart);		
		double 
			atr = mailValuesMap.get("ATR" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue(),
			low = mailValuesMap.get("barLow" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue(),
			high = mailValuesMap.get("barHigh" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue();
		DecimalFormat df = instrument.getPipScale() == 2 ? FXUtils.df2 : FXUtils.df5;
		if (isBullishRangeExtreme(mailStringsMap, mailValuesMap, instrument, pPeriod, history, indicators, vola, bidBar)) {
			value = instrument.toString() + ": Bulish volatility drop breakout signal ! BUY STP: " + df.format(high) 
					+ ", ATR " + FXUtils.df1.format(atr)
					+ ", SL " + df.format(FXUtils.roundToPip(high - atr / Math.pow(10, instrument.getPipScale()) * 1.5, instrument));
			color = getGreen();
			signalFound = true;
			
			sqlStr += ", ValueName1, ValueD1) "
					+ SignalUtils.getSignalInsertValues(SignalUtils.RangeExtremeID, 
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
						+ FXUtils.getDbToUse() + ".tsignal WHERE option_id = " + SignalUtils.RangeExtremeID
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
		else if (isBearishRangeExtreme(mailStringsMap, mailValuesMap, instrument, pPeriod, history, indicators, vola, bidBar)) {
			value = "Bearish volatility drop breakout signal ! SELL STP: " + df.format(low) 
					+ ", ATR " + FXUtils.df1.format(atr)
					+ ", SL " + df.format(FXUtils.roundToPip(high + atr / Math.pow(10, instrument.getPipScale()) * 1.5, instrument));
			color = getRed();
			signalFound = true;
			
			sqlStr += ", ValueName1, ValueD1) "
					+ SignalUtils.getSignalInsertValues(SignalUtils.RangeExtremeID, 
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
						+ FXUtils.getDbToUse() + ".tsignal WHERE option_id = " + SignalUtils.RangeExtremeID
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
	public SignalResult detectSignal(Instrument instrument, Period pPeriod, IBar bidBar,
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
		
		SignalResult result = new SignalResult();
		String
			color = getWhite(),
			value = new String("Trading range extreme"),
			sqlStr = new String(SignalUtils.signalInsertStart);		
		double 
			atr = mailValuesMap.get("ATR" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue(),
			low = mailValuesMap.get("barLow" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue(),
			high = mailValuesMap.get("barHigh" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue();
		DecimalFormat df = instrument.getPipScale() == 2 ? FXUtils.df2 : FXUtils.df5;
		if (isBullishRangeExtreme(mailStringsMap, mailValuesMap, instrument, pPeriod, history, indicators, vola, bidBar)) {
			value = instrument.toString() + ": Bulish range extreme signal ! BUY STP: " + df.format(high) 
					+ ", ATR " + FXUtils.df1.format(atr)
					+ ", SL " + df.format(FXUtils.roundToPip(high - atr / Math.pow(10, instrument.getPipScale()) * 1.5, instrument));
			color = getGreen();
			signalFound = true;
			
			sqlStr += ", ValueName1, ValueD1) "
					+ SignalUtils.getSignalInsertValues(SignalUtils.RangeExtremeID, 
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
		else if (isBearishRangeExtreme(mailStringsMap, mailValuesMap, instrument, pPeriod, history, indicators, vola, bidBar)) {
			value = "Bearish range extreme signal ! SELL STP: " + df.format(low) 
					+ ", ATR " + FXUtils.df1.format(atr)
					+ ", SL " + df.format(FXUtils.roundToPip(high + atr / Math.pow(10, instrument.getPipScale()) * 1.5, instrument));
			color = getRed();
			signalFound = true;
			
			sqlStr += ", ValueName1, ValueD1) "
					+ SignalUtils.getSignalInsertValues(SignalUtils.RangeExtremeID, 
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

	/**
	 * Signal criteria:
	 * - flat trend regime
	 * - not a volatility squeeze
	 * - bullish signal: bullish candles on the bottom of the channel
	 * - bearish signal: bearish candles on the top of the channel
	 */
	private boolean isBearishRangeExtreme(Map<String, String> mailStringsMap, Map<String, FlexLogEntry> mailValuesMap, Instrument instrument, Period pPeriod, IHistory history, IIndicators indicators, Volatility vola, IBar bidBar) throws JFException {
    	double 
    		squeeze = mailValuesMap.get("volatility" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue();
    	if (squeeze <= 70.0)
    		return false;
    	
    	double flatRegime = mailValuesMap.get("MAsDistance" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue();
    	if (flatRegime > -0.7) // not a flat regime, at least some trend
    		return false;
    	
    	String candles = mailStringsMap.get("CandleTrigger" + FXUtils.timeFrameNamesMap.get(pPeriod.toString()));
    	if (candles == null || candles.length() == 0 || !candles.contains("BEARISH"))
    		return false;
    	
		double
			triggerHighChannelPos = mailValuesMap.get("bearishCandleTriggerChannelPos" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue(),
			triggerHighKChannelPos = mailValuesMap.get("bearishCandleTriggerKChannelPos" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue();
		if (triggerHighChannelPos < 95.0 || triggerHighKChannelPos < 95.0)
			return false;
    	
    	return true;
	}

	private boolean isBullishRangeExtreme(Map<String, String> mailStringsMap, Map<String, FlexLogEntry> mailValuesMap, Instrument instrument, Period pPeriod, IHistory history, IIndicators indicators, Volatility vola, IBar askBar) throws JFException {
    	double 
    		squeeze = mailValuesMap.get("volatility" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue();
    	if (squeeze <= 70.0)
    		return false;
    	
    	double flatRegime = mailValuesMap.get("MAsDistance" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue();
    	if (flatRegime > -0.7) // not a flat regime, at least some trend
    		return false;
    	
    	String candles = mailStringsMap.get("CandleTrigger" + FXUtils.timeFrameNamesMap.get(pPeriod.toString()));
    	if (candles == null || candles.length() == 0 || !candles.contains("BULLISH"))
    		return false;
    	
		double
			triggerLowChannelPos = mailValuesMap.get("bullishCandleTriggerChannelPos" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue(),
			triggerLowKChannelPos = mailValuesMap.get("bullishCandleTriggerKChannelPos" + FXUtils.timeFrameNamesMap.get(pPeriod.toString())).getDoubleValue();  	
		if (triggerLowChannelPos > 5.0 || triggerLowKChannelPos > 5.0)
			return false;
		
    	return true;
	}
	
	@Override
	public boolean isSignal() {
		return signalFound;
	}	

}
