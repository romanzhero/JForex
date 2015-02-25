package jforex;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import jforex.techanalysis.PriceZone;
import jforex.techanalysis.SRLevel;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.TrendLineSnapshot;
import jforex.techanalysis.Trendline;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IOrder.State;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public abstract class AdvancedMailCreator extends BasicTAStrategy {

	protected boolean filterMail = false;
	protected boolean sendFilteredMail = false;
	protected Map<String, String> nextSetups = new HashMap<String, String>();

	public AdvancedMailCreator(Properties props) {
		super(props);
		filterMail = props.getProperty("filterMail", "no").equals("yes");
		String nextSetupsStr = props.getProperty("nextSetups");
		if (nextSetupsStr != null) {
			// format should be: nextSetups=<pair>:<setup description>;<pair>:<setup description>;...;<pair>:<setup description>
			StringTokenizer st = new StringTokenizer(nextSetupsStr, ";");
			while (st.hasMoreTokens()) {
				String nextSetup = st.nextToken();
				StringTokenizer internal = new StringTokenizer(nextSetup, ":");
				nextSetups.put(internal.nextToken(), internal.nextToken().replace("<br>", "\n"));
			}
		}
	}

	protected PriceZone checkEntryZones(Instrument pair, double pivotPrice, boolean isLong) {
		if (priceZones == null || priceZones.size() == 0)
			return null;
		String direction = new String(isLong ? "L" : "S");
		for (PriceZone pz : priceZones) {
			if (pz.getDirection().equals(direction) && pz.hit(pivotPrice, 10.0 / Math.pow(10, pair.getPipScale())))
				return pz;
		}
		return null;
	}

	protected String showSortedTrendlineValues(Instrument instrument, IBar bidBar) {
		List<TrendLineSnapshot> currentTrendlineValues = new ArrayList<TrendLineSnapshot>();
		for (Trendline trendLineToCheck : trendlinesToCheck) {
			currentTrendlineValues.add(new TrendLineSnapshot(trendLineToCheck.getName(), trendLineToCheck.getCurrentValue(bidBar), trendLineToCheck.getSlope30min()));				
		}
		String result = new String();
		Collections.sort(currentTrendlineValues);
	
		TrendLineSnapshot current = currentTrendlineValues.get(0);
		if (bidBar.getClose() > current.getCurrentValue())
			result += "Current trendline values in descending order:\nCurrent price (" + FXUtils.df5.format(bidBar.getClose()) + ") is ABOVE all trendlines.\n1. ";
		else
			result += "Current trendline values in descending order:\n1. ";
		result += current.getName() + ": " + FXUtils.df5.format(current.getCurrentValue()) + "\n"
			+ FXUtils.df1.format((current.getCurrentValue() - bidBar.getClose()) * Math.pow(10, instrument.getPipScale())) + " pips from last price";
		for (int i = 1; i < currentTrendlineValues.size(); i++) {
			current = currentTrendlineValues.get(i);
			TrendLineSnapshot previous = currentTrendlineValues.get(i-1);
			if (bidBar.getClose() <= previous.getCurrentValue() && bidBar.getClose() >= current.getCurrentValue())
				result += "\n--------------------------------------------\nCurrent price (" 
					+ FXUtils.df5.format(bidBar.getClose()) + ") position !\n--------------------------------------------"; 
	
			double diffToClose = (current.getCurrentValue() - bidBar.getClose()) * Math.pow(10, instrument.getPipScale());
			result += "\n" + (i + 1) + ". trendline " + current.getName() + ": " 
				+ "\ncurrent value: " + FXUtils.df5.format(current.getCurrentValue())
				+ ", next value: " + FXUtils.df5.format(current.getCurrentValue() + current.getSlope30min()) + "\n"
				+ FXUtils.df1.format(diffToClose) + " pips from last price";			
		}
		if (bidBar.getClose() < current.getCurrentValue())
			result += "\nCurrent price (" + FXUtils.df5.format(bidBar.getClose()) + ") is BELOW all trendlines !\n";
		return result;
	}

	protected String showSortedSRLevels(Instrument instrument, List<SRLevel> sortedSRlevels, IBar bidBar) {
		String result = new String();
		Collections.sort(sortedSRlevels);
		SRLevel current = sortedSRlevels.get(0);
		if (bidBar.getClose() > current.getValue())
			result += "support/resistance levels in descending order:\nCurrent price (" + FXUtils.df5.format(bidBar.getClose()) + ") is ABOVE all S/R levels !\n1. ";
		else
			result += "support/resistance levels in descending order:\n1. ";
		result += current.getName() + ": " + FXUtils.df5.format(current.getValue()) + "\n"
			+ FXUtils.df1.format((current.getValue() - bidBar.getClose()) * Math.pow(10, instrument.getPipScale())) + " pips from last price";
		for (int i = 1; i < sortedSRlevels.size(); i++) {
				current = sortedSRlevels.get(i);
				SRLevel previous = sortedSRlevels.get(i-1);
			double 
				maSdiff = (previous.getValue() - current.getValue()) * Math.pow(10, instrument.getPipScale()),
				diffToClose = (current.getValue() - bidBar.getClose()) * Math.pow(10, instrument.getPipScale());
			if (bidBar.getClose() <= previous.getValue() && bidBar.getClose() >= current.getValue())
				result += "\n--------------------------------------------\nCurrent price (" 
					+ FXUtils.df5.format(bidBar.getClose()) + ") position !\n--------------------------------------------"; 
			result += "\n" + (i + 1) + ". " + current.getName() + ": " 
				+ FXUtils.df5.format(current.getValue()) + " (" + FXUtils.df1.format(maSdiff) + " pips below)\n " 
				+ FXUtils.df1.format(diffToClose) + " pips from last price";
		}
		if (bidBar.getClose() < current.getValue())
			result += "\nCurrent price (" + FXUtils.df5.format(bidBar.getClose()) + ") is BELOW all S/R levels !\n";
		return result;
	}

	protected String showSortedMAsList(Instrument instrument, List<SRLevel> mas, IBar bidBar) {
		String result = new String();
		Collections.sort(mas);
		SRLevel current = mas.get(0);
		if (bidBar.getClose() > current.getValue())
			result += "moving averages in descending order:\nCurrent price (" + FXUtils.df5.format(bidBar.getClose()) + ") is ABOVE all moving averages !\n1. ";
		else
			result += "moving averages in descending order:\n1. ";
		result += current.getName() + ": " + FXUtils.df5.format(current.getValue()) + "\n"
			+ FXUtils.df1.format((current.getValue() - bidBar.getClose()) * Math.pow(10, instrument.getPipScale())) + " pips from last price";
		for (int i = 1; i < mas.size(); i++) {
				current = mas.get(i);
				SRLevel previous = mas.get(i-1);
			double 
				maSdiff = (previous.getValue() - current.getValue()) * Math.pow(10, instrument.getPipScale()),
				diffToClose = (current.getValue() - bidBar.getClose()) * Math.pow(10, instrument.getPipScale());
			if (bidBar.getClose() <= previous.getValue() && bidBar.getClose() >= current.getValue())
				result += "\n--------------------------------------------\nCurrent price (" 
					+ FXUtils.df5.format(bidBar.getClose()) + ") position !\n--------------------------------------------"; 
			result += "\n" + (i + 1) + ". " + current.getName() + ": " 
				+ FXUtils.df5.format(current.getValue()) + " (" + FXUtils.df1.format(maSdiff) + " pips below)\n " 
				+ FXUtils.df1.format(diffToClose) + " pips from last price";
		}
		if (bidBar.getClose() < current.getValue())
			result += "\nCurrent price (" + FXUtils.df5.format(bidBar.getClose()) + ") is BELOW all moving averages !\n";
		return result;
	}

	protected String checkNoGoZones(Map<String, String> mailStringsMap, Map<String, FlexLogEntry> mailValuesMap) {
		String result = new String("");
		// 4h momentum no-go zones
		Double stochFast4h = (Double)mailValuesMap.get("StochFast4h").getValue();
		Double stochSlow4h = (Double)mailValuesMap.get("StochSlow4h").getValue();
		if (mailStringsMap.get("MACDState4h").contains("FALLING")
			&& (mailStringsMap.get("MACDHState4h").contains("FALLING") || mailStringsMap.get("MACDHState4h").contains("TICKED_DOWN")) 
			&& stochFast4h.doubleValue() < stochSlow4h.doubleValue()) {
			result += "<span style=\"background-color:" + getRed() + "; display:block; margin:0 1px;\">WARNING: 4h momentum LONG no-go zone - all three FALLING !<br /></span>";
			sendFilteredMail = true;
		}
		if (mailStringsMap.get("MACDState4h").contains("RAISING")
				&& (mailStringsMap.get("MACDHState4h").contains("RAISING") || mailStringsMap.get("MACDHState4h").contains("TICKED_UP")) 
				&& stochFast4h.doubleValue() > stochSlow4h.doubleValue()) {
			result += "<span style=\"background-color:" + getGreen() + "; display:block; margin:0 1px;\">4h momentum SHORT no-go zone - all three RAISING !<br /></span>";
			sendFilteredMail = true;
		}
	
		// 30min momentum no-go zones
		Double stochFast30min = (Double)mailValuesMap.get("StochFast30min").getValue();
		Double stochSlow30min = (Double)mailValuesMap.get("StochSlow30min").getValue();
		if (mailStringsMap.get("MACDState30min").contains("FALLING")
			&& (mailStringsMap.get("MACDHState30min").contains("FALLING") || mailStringsMap.get("MACDHState30min").contains("TICKED_DOWN")) 
			&& stochFast30min.doubleValue() < stochSlow30min.doubleValue()) {
			result += "<span style=\"background-color:" + getLightRed() + "; display:block; margin:0 1px;\">30min momentum LONG no-go zone - all three FALLING !<br /></span>";
		}
		if (mailStringsMap.get("MACDState30min").contains("RAISING")
				&& (mailStringsMap.get("MACDHState30min").contains("RAISING") || mailStringsMap.get("MACDHState30min").contains("TICKED_UP")) 
				&& stochFast30min.doubleValue() > stochSlow30min.doubleValue()) {
			result += "<span style=\"background-color:" + getLightGreen() + "; display:block; margin:0 1px;\">30min momentum SHORT no-go zone - all three RAISING !<br /></span>";
		}
		
		// too late for short / long due to massive OS / OB
		Double rsi30min = (Double)mailValuesMap.get("RSI30min").getValue();
		Double rsi4h = (Double)mailValuesMap.get("RSI4h").getValue();
		if (rsi30min.doubleValue() > 68 && rsi4h.doubleValue() > 68) {
			result += "<span style=\"background-color:" + getDarkGreen() + "; display:block; margin:0 1px;\">WARNING: both RSIs over 68 !<br /></span>";
			sendFilteredMail = true;
		}
		if (rsi30min.doubleValue() < 32 && rsi4h.doubleValue() < 32) {
			result += "<span style=\"background-color:" + getDarkRed() + "; display:block; margin:0 1px;\">WARNING: both RSIs below 32 !<br /></span>";
			sendFilteredMail = true;
		}
		
		Integer barsAboveChannel4h = (Integer)mailValuesMap.get("barsAboveChannel4h").getValue();
		if (barsAboveChannel4h > 1) {
			result += "<span style=\"background-color:" + getDarkGreen() + "; display:block; margin:0 1px;\">WARNING: " + barsAboveChannel4h + " 4h bars highs ABOVE channel !<br /></span>";
			sendFilteredMail = true;			
		}
		Integer barsBelowChannel4h = (Integer)mailValuesMap.get("barsBelowChannel4h").getValue();
		if (barsBelowChannel4h > 1) {
			result += "<span style=\"background-color:" + getDarkRed() + "; display:block; margin:0 1px;\">WARNING: " + barsBelowChannel4h + " 4h bars lows BELOW channel !<br /></span>";
			sendFilteredMail = true;			
		}
	
		Integer barsAboveChannel30min = (Integer)mailValuesMap.get("barsAboveChannel30min").getValue();
		if (barsAboveChannel30min > 2) {
			result += "<span style=\"background-color:" + getLightGreen() + "; display:block; margin:0 1px;\">WARNING: " + barsAboveChannel30min + " 30min bars highs ABOVE channel !<br /></span>";
			sendFilteredMail = true;			
		}
		Integer barsBelowChannel30min = (Integer)mailValuesMap.get("barsBelowChannel30min").getValue();
		if (barsBelowChannel30min > 2) {
			result += "<span style=\"background-color:" + getLightRed() + "; display:block; margin:0 1px;\">WARNING: " + barsBelowChannel30min + " 30min bars lows BELOW channel !<br /></span>";
			sendFilteredMail = true;			
		}
		
	
		return result;
	}

	protected String checkTALevelsHit(Instrument instrument, Period pPeriod, IBar bidBar,
			String candleSignals, boolean checkHighOrLow) throws JFException {
				String 
					taLevelsHit = new String(),
					srLevelsHit = new String(),
					maSHit = new String(),
					trendlinesHit = new String();
				IBar barToCheck = bidBar;
				List<SRLevel> MAsToCheck = getMAsValues(instrument, bidBar);
				// check for srLevels crossed
				if (candleSignals.contains("_2_BARS") || candleSignals.contains("_3_BARS")) {
					// get previous bar
					barToCheck = history.getBar(instrument, pPeriod, OfferSide.BID, 2);
				}
				for (SRLevel levelsToCheck : srLevels) {
					if (levelsToCheck.hit(barToCheck, checkHighOrLow, 5 / Math.pow(10, instrument.getPipScale()))) {
						sendFilteredMail = true;
						if (srLevelsHit.length() == 0)
							srLevelsHit += "\nsupport/resistance level(s) HIT:\n" + levelsToCheck + "\n";
						else
							srLevelsHit += levelsToCheck + "\n";
					}
				}
				taLevelsHit += srLevelsHit;
				
				for (SRLevel levelsToCheck : MAsToCheck) {
					if (levelsToCheck.hit(barToCheck, checkHighOrLow, 5 / Math.pow(10, instrument.getPipScale()))) {
						sendFilteredMail = true;
						if (maSHit.length() == 0)
							maSHit += "\nmoving average(s) HIT:\n" + levelsToCheck + "\n";
						else
							maSHit += levelsToCheck + "\n";
					}
				}
				taLevelsHit += maSHit;
				
				for (Trendline trendLineToCheck : trendlinesToCheck) {
					if (trendLineToCheck.hit(barToCheck, 10 / Math.pow(10, instrument.getPipScale()))) {
						sendFilteredMail = true;
						if (trendlinesHit.length() == 0)
							trendlinesHit += "\ntrendline(s) HIT:\n" + trendLineToCheck.getName() + "\n";
						else
							trendlinesHit += trendLineToCheck.getName() + "\n";
					}				
				}
				taLevelsHit += trendlinesHit;
				
				return taLevelsHit;
			}

	protected String checkNearTALevels(Instrument instrument, Period pPeriod, IBar bidBar,
			Map<String, FlexLogEntry> mailValuesMap, double fromPrice, double distance, boolean isBullish)
			throws JFException {
				String 
					taLevelsHit = new String(),
					channelsHit = new String(),
					srLevelsHit = new String(),
					maSHit = new String(),
					trendlinesHit = new String();
				List<SRLevel> 
					MAsToCheck = getMAsValues(instrument, bidBar),
					channelBordersToCheck = new ArrayList<SRLevel>();
				
				channelBordersToCheck.add(new SRLevel("4h channel top", mailValuesMap.get("bBandsTop4h").getDoubleValue()));
				channelBordersToCheck.add(new SRLevel("4h channel bottom", mailValuesMap.get("bBandsBottom4h").getDoubleValue()));
				channelBordersToCheck.add(new SRLevel("1d channel top", mailValuesMap.get("bBandsTop1d").getDoubleValue()));
				channelBordersToCheck.add(new SRLevel("1d channel bottom", mailValuesMap.get("bBandsBottom1d").getDoubleValue()));
			
				for (SRLevel levelsToCheck : srLevels) {
					if (levelsToCheck.isNear(fromPrice, distance / Math.pow(10, instrument.getPipScale()))) {
						if (srLevelsHit.length() == 0)
							srLevelsHit += "\nsupport/resistance level(s) near " + (isBullish ? "bullish pivot low" : "bearish pivot high") + ":\n"; 
						srLevelsHit += levelsToCheck + ", " 
						+ FXUtils.df1.format(Math.abs((levelsToCheck.getValue() - fromPrice) * Math.pow(10, instrument.getPipScale()))) + " pips " 
						+ (levelsToCheck.getValue() - fromPrice > 0 ? "above\n" : "below\n");
					}
				}
				taLevelsHit += srLevelsHit;
				
				for (SRLevel levelsToCheck : MAsToCheck) {
					if (levelsToCheck.isNear(fromPrice, distance / Math.pow(10, instrument.getPipScale()))) {
						if (maSHit.length() == 0)
							maSHit += "\nmoving average(s) near " + (isBullish ? "bullish pivot low" : "bearish pivot high") + ":\n";
						maSHit += levelsToCheck + ", " 
						+ FXUtils.df1.format(Math.abs((levelsToCheck.getValue() - fromPrice) * Math.pow(10, instrument.getPipScale()))) + " pips "
						+ (levelsToCheck.getValue() - fromPrice > 0 ? "above\n" : "below\n");
					}
				}
				taLevelsHit += maSHit;
				
				for (Trendline trendLineToCheck : trendlinesToCheck) {
					if (trendLineToCheck.isNear(bidBar, fromPrice, distance / Math.pow(10, instrument.getPipScale()))) {
						double currValue = trendLineToCheck.getCurrentValue(bidBar);
						if (trendlinesHit.length() == 0)
							trendlinesHit += "\ntrendline(s) near " + (isBullish ? "bullish pivot low" : "bearish pivot high") + ":\n";
						trendlinesHit += trendLineToCheck.getName() + " (" + FXUtils.df4.format(currValue) + "), " 
						+ FXUtils.df1.format(Math.abs((currValue - fromPrice) * Math.pow(10, instrument.getPipScale()))) + " pips "
						+ (currValue - fromPrice > 0 ? "above\n" : "below\n");
					}				
				}
				taLevelsHit += trendlinesHit;
					
				for (SRLevel levelsToCheck : channelBordersToCheck) {
					if (levelsToCheck.isNear(fromPrice, distance / Math.pow(10, instrument.getPipScale()))) {
						if (channelsHit.length() == 0)
							channelsHit += "\nchannel borders(s) near " + (isBullish ? "bullish pivot low" : "bearish pivot high") + ":\n";
						channelsHit += levelsToCheck + ", " 
						+ FXUtils.df1.format(Math.abs((levelsToCheck.getValue() - fromPrice) * Math.pow(10, instrument.getPipScale()))) + " pips "
						+ (levelsToCheck.getValue() - fromPrice > 0 ? "above\n" : "below\n");
					}
				}
				taLevelsHit += channelsHit;
			
				return taLevelsHit;
			}

	protected List<SRLevel> getMAsValues(Instrument instrument, IBar bidBar) throws JFException {
			List<SRLevel> result = new ArrayList<SRLevel>();
	
			result.add(new SRLevel("MA20 30min", indicators.sma(instrument, Period.THIRTY_MINS, OfferSide.BID, AppliedPrice.CLOSE, 20, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));
			result.add(new SRLevel("MA50 30min", indicators.sma(instrument, Period.THIRTY_MINS, OfferSide.BID, AppliedPrice.CLOSE, 50, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));
			result.add(new SRLevel("MA100 30min", indicators.sma(instrument, Period.THIRTY_MINS, OfferSide.BID, AppliedPrice.CLOSE, 100, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));
			result.add(new SRLevel("MA200 30min", indicators.sma(instrument, Period.THIRTY_MINS, OfferSide.BID, AppliedPrice.CLOSE, 200, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));
	
			result.add(new SRLevel("MA20 4h", indicators.sma(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, 20, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));
			result.add(new SRLevel("MA50 4h", indicators.sma(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, 50, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));
			result.add(new SRLevel("MA100 4h", indicators.sma(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, 100, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));
			result.add(new SRLevel("MA200 4h", indicators.sma(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, 200, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0]));
	
			result.add(new SRLevel("MA20 1d", indicators.sma(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, 20, 1)));
			result.add(new SRLevel("MA50 1d", indicators.sma(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, 50, 1)));
			result.add(new SRLevel("MA100 1d", indicators.sma(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, 100, 1)));
			result.add(new SRLevel("MA200 1d", indicators.sma(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, AppliedPrice.CLOSE, 200, 1)));
	
			//TODO: make sure this works also not only for last bar in time ! I.e. for historical tester !
			DateTime 
				endInterval = FXUtils.calcTimeOfLastNYClose30minBar(bidBar.getTime()),
				startInterval = new DateTime(endInterval.minusMinutes(30));
			double 
				sum20 = 0.0,
				sum50 = 0.0,
				sum100 = 0.0,
				sum200 = 0.0;
			int count = 0;		
			while (count < 200) {
				// returns two bars, last one is the correct
				if (endInterval.getDayOfWeek() != DateTimeConstants.SATURDAY && endInterval.getDayOfWeek() != DateTimeConstants.SUNDAY) {
					List<IBar> currBar = history.getBars(instrument, Period.THIRTY_MINS, OfferSide.BID, Filter.NO_FILTER, startInterval.getMillis(), endInterval.getMillis());
					if (count < 20)
						sum20 += currBar.get(1).getClose();
					if (count < 50)
						sum50 += currBar.get(1).getClose();
					if (count < 100)
						sum100 += currBar.get(1).getClose();
					if (count < 200)
						sum200 += currBar.get(1).getClose();
					count++;
				}
				endInterval = new DateTime(endInterval.minusDays(1));
				startInterval = new DateTime(endInterval.minusMinutes(30));
			}
			result.add(new SRLevel("MA20 1d by NY close", sum20 / 20));
			result.add(new SRLevel("MA50 1d by NY close", sum50 / 50));
			result.add(new SRLevel("MA100 1d by NY close", sum100 / 100));
			result.add(new SRLevel("MA200 1d by NY close", sum200 / 200));
			
	//		result.add(new SRLevel("MA20 1d", indicators.sma(instrument, Period.DAILY_SKIP_SUNDAY, OfferSide.BID, AppliedPrice.CLOSE, 20, Filter.WEEKENDS, 1, previousEODTime, 0)[0]));
	//		result.add(new SRLevel("MA50 1d", indicators.sma(instrument, Period.DAILY_SKIP_SUNDAY, OfferSide.BID, AppliedPrice.CLOSE, 50, Filter.WEEKENDS, 1, previousEODTime, 0)[0]));
	//		result.add(new SRLevel("MA100 1d", indicators.sma(instrument, Period.DAILY_SKIP_SUNDAY, OfferSide.BID, AppliedPrice.CLOSE, 100, Filter.WEEKENDS, 1, previousEODTime, 0)[0]));
	//		result.add(new SRLevel("MA200 1d", indicators.sma(instrument, Period.DAILY_SKIP_SUNDAY, OfferSide.BID, AppliedPrice.CLOSE, 200, Filter.WEEKENDS, 1, previousEODTime, 0)[0]));
	
			return result;
		}

	protected String createMailBody4h(Instrument instrument, Period pPeriod, IBar bidBar,
			List<FlexLogEntry> logLine) throws JFException {
				
				sendFilteredMail = true;
				
				Map<String, String> mailStringsMap = new HashMap<String, String>();
				Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
				for (FlexLogEntry e : logLine) {
					mailStringsMap.put(e.getLabel(), e.getFormattedValue());
					mailValuesMap.put(e.getLabel(), e);
				}
				String mailBody = new String();
				
				mailBody = "Report for " 
					+ instrument.toString() + ", " 
					+ FXUtils.getFormatedTimeCET(bidBar.getTime()) 
					+ " CET (time frame: " + pPeriod.toString() 
					+ ")\n\n";
				
				//TODO: this makes sense only when running live ! Add attribute to indicate it !
				//mailBody += printCurrentPnL(instrument);
			
				// Warn on good 4h signals on both 4h and 1d channel extremes
				mailBody += eventsSource.checkStrong4hFirstEntrySignals(instrument, pPeriod, bidBar, logLine);
				
				double roc1 = indicators.roc(instrument, Period.FOUR_HOURS, OfferSide.BID, AppliedPrice.CLOSE, 1, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0];
				mailBody += "\n4h price change: " + FXUtils.df1.format(roc1 * 100) + " pips\n";
			
				IBar prevBar4h = history.getBar(instrument, pPeriod, OfferSide.BID, 2);
				String 
						lastTwo4hBarsStats = new String("Last bar candle stats: upper handle = "  
						+ FXUtils.df1.format(tradeTrigger.barsUpperHandlePerc(bidBar)) + "% / " + 
						(bidBar.getClose() > bidBar.getOpen() ? "BULLISH" : "BEARISH") + " body = "  
						+ FXUtils.df1.format(tradeTrigger.barsBodyPerc(bidBar)) + "% / lower handle = "			
						+ FXUtils.df1.format(tradeTrigger.barsLowerHandlePerc(bidBar)) + "%\n"
						+ "Previous bar candle stats: upper handle = " 
						+ FXUtils.df1.format(tradeTrigger.barsUpperHandlePerc(prevBar4h)) + "% / " + 
						(prevBar4h.getClose() > prevBar4h.getOpen() ? "BULLISH" : "BEARISH") + " body = "  
						+ FXUtils.df1.format(tradeTrigger.barsBodyPerc(prevBar4h)) + "% / lower handle = "			
						+ FXUtils.df1.format(tradeTrigger.barsLowerHandlePerc(prevBar4h)) + "%\n"),			
					candles = new String(),
					valueToShow = mailStringsMap.get("CandleTrigger4h"),
					bullishCandles = null,
					bearishCandles = null;
				boolean twoCandleSignals = false;
				
				if (valueToShow != null && !valueToShow.toUpperCase().equals("NONE") && !valueToShow.toLowerCase().equals("n/a")) {
					int positionOfAnd = valueToShow.indexOf(" AND ");
					if (positionOfAnd > 0) {
						twoCandleSignals = true;
						String firstCandle = valueToShow.substring(0, positionOfAnd);
						String secondCandle = valueToShow.substring(positionOfAnd + 5);
						if (firstCandle.contains("BULLISH")) {
							bullishCandles = new String(firstCandle);
							bearishCandles = new String(secondCandle);					
						}					
						else {
							bullishCandles = new String(secondCandle);
							bearishCandles = new String(firstCandle);					
						}					
					}
					else {
						if (valueToShow.contains("BULLISH")) 
							bullishCandles = new String(valueToShow);
						else
							bearishCandles = new String(valueToShow);
					}
			
					if (twoCandleSignals)
						candles = valueToShow + "\n";
			
					if (valueToShow.contains("BULLISH")) {
						//TODO: check for any of explicit patterns found by JForex API
						String candleDesc = tradeTrigger.bullishCandleDescription(instrument, pPeriod, OfferSide.BID, bidBar.getTime());
						if (twoCandleSignals)
							candles += "\n" + bullishCandles + (candleDesc != null && candleDesc.length() > 0 ? " (" + candleDesc + ")" : "");
						else 
							candles += "\n" + valueToShow + (candleDesc != null && candleDesc.length() > 0 ? " (" + candleDesc + ")" : "");
						candles += " (bar StDev size: " + mailStringsMap.get("barStat4h");
						if (valueToShow.contains("BULLISH_1_BAR"))
							candles += ")\n";
						else
							candles += 
									", combined signal StDev size: " + mailStringsMap.get("bullishTriggerCombinedSizeStat4h")
									+ ", lower handle : " + mailStringsMap.get("bullishTriggerCombinedLHPerc4h") + "%"
									+ ", real body : " + mailStringsMap.get("bullishTriggerCombinedBodyPerc4h") + "% ("
									+ mailStringsMap.get("bullishTriggerCombinedBodyDirection4h")
									+ "), upper handle : " + mailStringsMap.get("bullishTriggerCombinedUHPerc4h") + "%"
									+ ")\n";
						candles += "pivot bar low 4h channel position: " + mailStringsMap.get("bullishCandleTriggerChannelPos4h") + "%\n"
							+ "Keltner channel position (pivotBar): " + mailStringsMap.get("bullishCandleTriggerKChannelPos4h") + "%\n";
						long prev1dBarTime = history.getPreviousBarStart(Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
						double
							pivotLevel4h = mailValuesMap.get("bullishPivotLevel4h").getDoubleValue(),
							channelPos1d = tradeTrigger.priceChannelPos(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prev1dBarTime, pivotLevel4h, 0),
							keltnerPos1d = tradeTrigger.priceKeltnerChannelPos(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prev1dBarTime, pivotLevel4h, 0);
						candles += "1d BBands channel pivot position: " + FXUtils.df1.format(channelPos1d) + "% / 1d Keltner channel position: "
							+ FXUtils.df1.format(keltnerPos1d)
							+ "%\nlast 30min bar low 1d channel position: " + mailStringsMap.get("barLowChannelPos1d") + "%\n";
						candles += lastTwo4hBarsStats;
			
						double  
							barHigh = bidBar.getHigh(),
							barLow = bidBar.getLow(),
							barHalf = barLow + (barHigh - barLow) / 2;
			
						Double 
							atr4hPlus20Perc = (Double)mailValuesMap.get("ATR30min + 20%").getValue(),
							bullishPivotLevel = (Double)mailValuesMap.get("bullishPivotLevel4h").getValue(),
							aggressiveSL = new Double(bullishPivotLevel.doubleValue() - atr4hPlus20Perc.doubleValue() / Math.pow(10, instrument.getPipScale())),
							riskStandard = new Double((barHigh - bullishPivotLevel) * Math.pow(10, instrument.getPipScale())),
							riskAggressive = new Double((barHigh - aggressiveSL) * Math.pow(10, instrument.getPipScale())),
							riskStandard2 = new Double((barHalf - bullishPivotLevel) * Math.pow(10, instrument.getPipScale())),
							riskAggressive2 = new Double((barHalf - aggressiveSL) * Math.pow(10, instrument.getPipScale()));
						candles += "\nBUY STP: " + FXUtils.df5.format(barHigh) 
							+ " (risks " + FXUtils.df1.format(riskStandard.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive.doubleValue()) 
							+ ")\nBUY LMT: " + FXUtils.df5.format(barHalf)  
							+ " (risks " + FXUtils.df1.format(riskStandard2.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive2.doubleValue()) 
							+ ")\nSL: " + mailStringsMap.get("bullishPivotLevel4h")
							+ " (aggressive: " + FXUtils.df5.format(aggressiveSL.doubleValue()) + ")";
					}
					if (valueToShow.contains(" AND "))
						candles += "\n\n";
					if (valueToShow.contains("BEARISH")) {
						if (twoCandleSignals)
							candles += "\n" + bearishCandles;
						else 
							candles += "\n" + valueToShow;
						candles += " (bar StDev size: " + mailStringsMap.get("barStat4h");
						if (valueToShow.contains("BEARISH_1_BAR"))
							candles += ")\n";
						else
							candles +=  ", combined signal StDev size: " + mailStringsMap.get("bearishTriggerCombinedSizeStat4h")
							+ ", lower handle : " + mailStringsMap.get("bearishTriggerCombinedLHPerc4h") + "%"
							+ ", real body : " + mailStringsMap.get("bearishTriggerCombinedBodyPerc4h") + "%, ("
							+ mailStringsMap.get("bearishTriggerCombinedBodyDirection4h")
							+ "), upper handle : " + mailStringsMap.get("bearishTriggerCombinedUHPerc4h") + "%"
							+ ")\n";
						candles += "pivot bar high BBands channel position: " 
							+ mailStringsMap.get("bearishCandleTriggerChannelPos4h") + "%\n"
							+ "Keltner channel position (pivotBar): " + mailStringsMap.get("bearishCandleTriggerKChannelPos4h") + "%\n";
						long prev1dBarTime = history.getPreviousBarStart(Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
						double
							pivotLevel4h = mailValuesMap.get("bearishPivotLevel4h").getDoubleValue(),
							channelPos1d = tradeTrigger.priceChannelPos(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prev1dBarTime, pivotLevel4h, 0),
							keltnerPos1d = tradeTrigger.priceKeltnerChannelPos(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, prev1dBarTime, pivotLevel4h, 0);
						candles += "1d BBands channel pivot position: " + FXUtils.df1.format(channelPos1d) + "% / 1d Keltner channel position: "
							+ FXUtils.df1.format(keltnerPos1d)
							+ "%\nlast 30min bar high 1d channel position: " + mailStringsMap.get("barHighChannelPos1d") + "%\n";
						candles += lastTwo4hBarsStats;
			
						double  
							barHigh = bidBar.getHigh(),
							barLow = bidBar.getLow(),
							barHalf = barLow + (barHigh - barLow) / 2;
			
						Double 
							atr30minPlus20Perc = (Double)mailValuesMap.get("ATR30min + 20%").getValue(),
							bearishPivotLevel = (Double)mailValuesMap.get("bearishPivotLevel4h").getValue(),
							aggressiveSL = new Double(bearishPivotLevel.doubleValue() + atr30minPlus20Perc.doubleValue() / Math.pow(10, instrument.getPipScale())),
							riskStandard = new Double((bearishPivotLevel - barLow) * Math.pow(10, instrument.getPipScale())),
							riskAggressive = new Double((aggressiveSL - barLow) * Math.pow(10, instrument.getPipScale())),
							riskStandard2 = new Double((bearishPivotLevel - barHalf) * Math.pow(10, instrument.getPipScale())),
							riskAggressive2 = new Double((aggressiveSL - barHalf) * Math.pow(10, instrument.getPipScale()));
						candles += "\nSELL STP: " + FXUtils.df5.format(barLow)
							+ " (risks " + FXUtils.df1.format(riskStandard.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive.doubleValue()) 
							+ ")\nSELL LMT: " + FXUtils.df5.format(barLow + (barHigh - barLow) / 2)  
							+ " (risks " + FXUtils.df1.format(riskStandard2.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive2.doubleValue()) 
							+ ")\nSL: " + mailStringsMap.get("bearishPivotLevel4h") 
							+ " (aggressive: " + FXUtils.df5.format(aggressiveSL.doubleValue()) + ")";
					}
					Double trendStrength4h = (Double)mailValuesMap.get("MAsDistance4h").getValue();
					if (trendStrength4h.doubleValue() < -0.7) 
						candles += "\n\n4h trend: FLAT (Strength: " + mailStringsMap.get("MAsDistance4h") + "); TrendID: " + mailStringsMap.get("TrendId4h");  			
					else
						candles += "\n\n4h trend: " + mailStringsMap.get("TrendId4h") + " (" + mailStringsMap.get("MAsDistance4h") + ")";
			
					candles += "\n4h momentum: MACDState " + mailStringsMap.get("MACDState4h")  
							+  " / MACDHState " + mailStringsMap.get("MACDHState4h")  
							+ " / StochState " + mailStringsMap.get("StochState4h");
					if (mailStringsMap.get("StochState4h").contains("OVER")) {
						Double stochFast4h = (Double)mailValuesMap.get("StochFast4h").getValue();
						Double stochSlow4h = (Double)mailValuesMap.get("StochSlow4h").getValue();
						String stochsToShow = FXUtils.df1.format(stochFast4h) + "/" + FXUtils.df1.format(stochSlow4h);
						if (stochFast4h > stochSlow4h)
							candles  += " and RAISING (" + stochsToShow + " --> difference " + FXUtils.df1.format(stochFast4h - stochSlow4h) + ")";
						else 
							candles += " and FALLING (" + stochsToShow + " --> difference " + FXUtils.df1.format(stochFast4h - stochSlow4h) + ")";
					}
					
					candles += "\nvolatility4h (BBands squeeze): " + mailStringsMap.get("volatility4h") + "%\n";
					candles += "volatility30min (BBands squeeze): " + mailStringsMap.get("volatility30min") + "%\n";
				}
			
				if (candles.length() > 0) 
					mailBody += "\nCandles:\n" + candles;
				
				// other COMBINED signals
				if (mailStringsMap.get("MACDHState4h").equals("TICKED_UP_BELOW_ZERO") && ((Double)mailValuesMap.get("barLowChannelPos4h").getValue()).doubleValue() < 50.0)
				{
					mailBody += "\nAdditional signals: MACDHistogram 4h " + mailStringsMap.get("MACDHState4h") + " and bar low 4h channelPos " + mailStringsMap.get("barLowChannelPos4h") + "%\n";
					IBar prevBar = history.getBar(instrument, pPeriod, OfferSide.BID, 2);
			
					double  
						barHigh = bidBar.getHigh(),
						barLow = bidBar.getLow(),
						barHalf = barLow + (barHigh - barLow) / 2;
			
					Double 
						atr30minPlus20Perc = (Double)mailValuesMap.get("ATR30min + 20%").getValue(),
						bullishPivotLevel = new Double(barLow < prevBar.getLow() ? barLow : prevBar.getLow()),
						aggressiveSL = new Double(bullishPivotLevel.doubleValue() - atr30minPlus20Perc.doubleValue() / Math.pow(10, instrument.getPipScale())),
						riskStandard = new Double((barHigh - bullishPivotLevel) * Math.pow(10, instrument.getPipScale())),
						riskAggressive = new Double((barHigh - aggressiveSL) * Math.pow(10, instrument.getPipScale())),
						riskStandard2 = new Double((barHalf - bullishPivotLevel) * Math.pow(10, instrument.getPipScale())),
						riskAggressive2 = new Double((barHalf - aggressiveSL) * Math.pow(10, instrument.getPipScale()));
					mailBody += "\nBUY STP: " + FXUtils.df5.format(barHigh)
						+ " (risks " + FXUtils.df1.format(riskStandard.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive.doubleValue()) 
						+ ")\nBUY LMT: " + FXUtils.df5.format(barHalf)  
						+ " (risks " + FXUtils.df1.format(riskStandard2.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive2.doubleValue()) 
						+ ")\nSL: " + FXUtils.df5.format(bullishPivotLevel)
						+ " (aggressive: " + FXUtils.df5.format(aggressiveSL.doubleValue()) + ")\n";
				}
				else if (mailStringsMap.get("MACDHState4h").equals("TICKED_DOWN_ABOVE_ZERO") && ((Double)mailValuesMap.get("barHighChannelPos4h").getValue()).doubleValue() > 50.0)
				{
					mailBody += "\nAdditional signals: MACDHistogram 4h " + mailStringsMap.get("MACDHState4h") + " and bar high 4h channelPos " + mailStringsMap.get("barHighChannelPos4h")  + "%\n";
					IBar prevBar = history.getBar(instrument, pPeriod, OfferSide.BID, 2);
					double  
						barHigh = bidBar.getHigh(),
						barLow = bidBar.getLow(),
						barHalf = barLow + (barHigh - barLow) / 2;
					Double
						atr30minPlus20Perc = (Double)mailValuesMap.get("ATR30min + 20%").getValue(),
						bearishPivotLevel = new Double(barHigh > prevBar.getHigh() ? barHigh : prevBar.getHigh()),
						aggressiveSL = new Double(bearishPivotLevel.doubleValue() + atr30minPlus20Perc.doubleValue() / Math.pow(10, instrument.getPipScale())),
						riskStandard = new Double((bearishPivotLevel - barLow) * Math.pow(10, instrument.getPipScale())),
						riskAggressive = new Double((aggressiveSL - barLow) * Math.pow(10, instrument.getPipScale())),
						riskStandard2 = new Double((bearishPivotLevel - barHalf) * Math.pow(10, instrument.getPipScale())),
						riskAggressive2 = new Double((aggressiveSL - barHalf) * Math.pow(10, instrument.getPipScale()));
					mailBody += "\nSELL STP: " + FXUtils.df5.format(barLow) 
						+ " (risks " + FXUtils.df1.format(riskStandard.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive.doubleValue()) 
						+ ")\nSELL LMT: " + FXUtils.df5.format(barLow + (barHigh - barLow) / 2)  
						+ " (risks " + FXUtils.df1.format(riskStandard2.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive2.doubleValue()) 
						+ ")\nSL: " + FXUtils.df5.format(bearishPivotLevel) 
						+ " (aggressive: " + FXUtils.df5.format(aggressiveSL.doubleValue()) + ")\n";
				}
				
				if (!mailStringsMap.get("MACDCross4h").equals("NONE")) {
					mailBody += "\nMACD4h cross ! " + mailStringsMap.get("MACDCross4h");
					mailBody += "\n\n";
				}
			
				Double rsi4h = (Double)mailValuesMap.get("RSI4h").getValue();
				Double rsi1d = (Double)mailValuesMap.get("RSI1d").getValue();
				Double cci4h = (Double)mailValuesMap.get("CCI4h").getValue();
				Double cci1d = (Double)mailValuesMap.get("CCI1d").getValue();
				Double barHighPos4h = (Double)mailValuesMap.get("barHighChannelPos4h").getValue(); 
				Double barLowPos4h = (Double)mailValuesMap.get("barLowChannelPos4h").getValue();  
				Double barHighPos1d = (Double)mailValuesMap.get("barHighChannelPos1d").getValue(); 
				Double barLowPos1d = (Double)mailValuesMap.get("barLowChannelPos1d").getValue();
				Double flatRegime4h = (Double)mailValuesMap.get("MAsDistance4h").getValue();		
				Double flatRegime1d= (Double)mailValuesMap.get("MAsDistance1d").getValue();		
				Double adx1d = (Double)mailValuesMap.get("ADX1d").getValue();
				Double adx4h = (Double)mailValuesMap.get("ADX4h").getValue();
				Double lowVolatility = (Double)mailValuesMap.get("volatility4h").getValue();
				Integer barsAboveChannel4h = (Integer)mailValuesMap.get("barsAboveChannel4h").getValue();
				Integer barsBelowChannel4h = (Integer)mailValuesMap.get("barsBelowChannel4h").getValue();
						
				if ((rsi4h.doubleValue() > 68.0 || rsi4h.doubleValue() < 32.0)
					|| (rsi1d.doubleValue() > 68.0 || rsi1d.doubleValue() < 32.0)
					|| (cci4h.doubleValue() > 190.0 || cci4h.doubleValue() < -190.0)
					|| (cci1d.doubleValue() > 190.0 || cci1d.doubleValue() < -190.0)
					|| barHighPos4h.doubleValue() > 95.0
					|| barHighPos4h.doubleValue() < 0.0
					|| barLowPos4h.doubleValue() < 5.0
					|| barLowPos4h.doubleValue() > 100.0
					|| barHighPos1d.doubleValue() > 95.0
					|| barLowPos1d.doubleValue() < 5.0
					|| mailStringsMap.get("StochState4h").contains("OVER")
					|| mailStringsMap.get("StochState1d").contains("OVER")
					|| flatRegime4h < -0.7
					|| flatRegime1d < -0.7
					|| adx1d > 40.0
					|| adx4h > 40.0
					|| (lowVolatility < 70.0 || lowVolatility > 170.0)
					|| barsAboveChannel4h > 1
					|| barsBelowChannel4h > 1) {
					mailBody += "\n\nExtreme values:";
					if (flatRegime4h < -0.7)
						mailBody += "\nflat regime 4h: " + mailStringsMap.get("MAsDistance4h");
					if (flatRegime1d < -0.7)
						mailBody += "\nflat regime 1d: " + mailStringsMap.get("MAsDistance1d");
					if (rsi4h.doubleValue() > 68 || rsi4h.doubleValue() < 32)
						mailBody += "\nRSI 4h: " + mailStringsMap.get("RSI4h");
					if (rsi1d.doubleValue() > 68 || rsi1d.doubleValue() < 32)
						mailBody += "\nRSI 1d: " + mailStringsMap.get("RSI1d");
					if (cci4h.doubleValue() > 190.0 || cci4h.doubleValue() < -190.0)
						mailBody += "\nCCI 4h: " + mailStringsMap.get("CCI4h") + " (" + mailStringsMap.get("CCIState4h") + ")";
					if (cci1d.doubleValue() > 190.0 || cci1d.doubleValue() < -190.00)
						mailBody += "\nCCI 1d: " + mailStringsMap.get("CCI1d");
					if (barHighPos4h.doubleValue() > 95.0)
						mailBody += "\nBar high in 4h channel: " + mailStringsMap.get("barHighChannelPos4h") + " %";
					if (barHighPos4h.doubleValue() < 0.0)
						mailBody += "\nBar high BELOW 4h channel: " + mailStringsMap.get("barHighChannelPos4h") + " %";
					if (barLowPos4h.doubleValue() < 5)
						mailBody += "\nBar low in 4h channel: " + mailStringsMap.get("barLowChannelPos4h") + " %";
					if (barLowPos4h.doubleValue() > 100)
						mailBody += "\nBar low ABOVE 4h channel: " + mailStringsMap.get("barLowChannelPos4h") + " %";
					if (barHighPos1d.doubleValue() > 95.0)
						mailBody += "\nBar high in 1d channel: " + mailStringsMap.get("barHighChannelPos1d") + " %";
					if (barLowPos1d.doubleValue() < 5)
						mailBody += "\nBar low in 1d channel: " + mailStringsMap.get("barLowChannelPos1d") + " %";
					if (mailStringsMap.get("StochState4h").contains("OVER"))
						mailBody += "\nStoch 4h: " + mailStringsMap.get("StochState4h");
					if (mailStringsMap.get("StochState1d").contains("OVER"))
						mailBody += "\nStoch 1d: " + mailStringsMap.get("StochState1d");
					if (adx1d > 40.0)
						mailBody += "\nADX 1d: " + mailStringsMap.get("ADX1d") + " (DI+: " + mailStringsMap.get("DI_PLUS1d") + " / DI-: " + mailStringsMap.get("DI_MINUS1d") + ")"; 
					if (adx4h > 40.0)
						mailBody += "\nADX 4h: " + mailStringsMap.get("ADX4h") + " (DI+: " + mailStringsMap.get("DI_PLUS4h") + " / DI-: " + mailStringsMap.get("DI_MINUS4h") + ")";
					if (lowVolatility < 70.0)
						mailBody += "\nlow 4h volatility (BBands squeeze): " + mailStringsMap.get("volatility4h") + "%";
					if (lowVolatility > 170.0)
						mailBody += "\nhigh 4h volatility (BBands squeeze): " + mailStringsMap.get("volatility4h") + "%";
					if (barsAboveChannel4h > 1)
						mailBody += "\n" + barsAboveChannel4h + " 4h bars ABOVE channel top !";  	
					if (barsBelowChannel4h > 1)
						mailBody += "\n" + barsBelowChannel4h + " 4h bars BELOW channel bottom !";  	
			
					mailBody += "\n\n";
				}
				
				if (nextSetups.containsKey(instrument.toString())) {
					mailBody += "Next recommended setup: " + nextSetups.get(instrument.toString()) + "\n\n";
				}
				
				// 1d regime
				mailBody += "\n\n-----------------------------------------------\nDAILY TIMEFRAME REGIME (previous finished day):\n-----------------------------------------------\n\nTrend direction (strength):\n";
				Double trendStrength1d = (Double)mailValuesMap.get("MAsDistance1d").getValue();
				if (trendStrength1d.doubleValue() < -0.7) 
					mailBody += "FLAT (Strength: " + mailStringsMap.get("MAsDistance1d") + "); TrendID: " + mailStringsMap.get("TrendId1d");  			
				else
					mailBody += "TrendId" + ": " + mailStringsMap.get("TrendId1d") + " (" + mailStringsMap.get("MAsDistance1d") + ")";  
				
				mailBody += "\n\nMomentum:\nMACDState: " + mailStringsMap.get("MACDState1d") + " (StDevPos: " + mailStringsMap.get("MACDStDevPos1d") + "), ";  
				mailBody += "MACDHState: " + mailStringsMap.get("MACDHState1d") + "\nStochState: " + mailStringsMap.get("StochState1d"); 
				if (mailStringsMap.get("StochState1d").contains("OVER")) {
					Double stochFast1d = (Double)mailValuesMap.get("StochFast1d").getValue();
					Double stochSlow1d = (Double)mailValuesMap.get("StochSlow1d").getValue();
					if (stochFast1d > stochSlow1d)
						mailBody += " and RAISING (difference " + FXUtils.df1.format(stochFast1d - stochSlow1d) + ")";
					else 
						mailBody += " and FALLING (difference " + FXUtils.df1.format(stochFast1d - stochSlow1d) + ")";
				}
			
				mailBody +=  "\n\nOversold / Overbought:\n";		
				if (mailStringsMap.get("StochState1d").contains("OVER"))
					mailBody += "StochState: " + mailStringsMap.get("StochState1d") + ", ";  
				mailBody += "Stochs: " + mailStringsMap.get("StochFast1d") + "/" + mailStringsMap.get("StochSlow1d") + ", ";
				mailBody += "RSI1d: " + mailStringsMap.get("RSI1d");  
			
				mailBody += "\n\nChannel position:\n";  		
				mailBody += "last 30min bar high/low: " + mailStringsMap.get("barHighChannelPos1d") + "%/"+ mailStringsMap.get("barLowChannelPos1d") + "%\n";
				if (!mailStringsMap.get("barsAboveChannel1d").equals("0"))
					mailBody += "barsAboveChannelTop for " + mailStringsMap.get("barsAboveChannel1d") + " day(s)) !\n";
				else if (!mailStringsMap.get("barsBelowChannel1d").equals("0"))
					mailBody += "barsBelowChannelBottom for " + mailStringsMap.get("barsBelowChannel1d") + " day(s))!\n";
				
			    long timeLast1d = history.getPreviousBarStart(Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
			    TradeTrigger.TriggerDesc  
			    bullishCandleTriggerDesc1d = tradeTrigger.bullishReversalCandlePatternDesc(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, timeLast1d), 
			    bearishCandleTriggerDesc1d = tradeTrigger.bearishReversalCandlePatternDesc(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, timeLast1d);
			    if (bullishCandleTriggerDesc1d != null) { 
			    	mailBody += "Bullish reversal candle: " + bullishCandleTriggerDesc1d.type.toString() + "\n";
			    }
			    if (bearishCandleTriggerDesc1d != null) {
			    	mailBody += "Bearish reversal candle: " + bearishCandleTriggerDesc1d.type.toString() + "\n";        	
			    }
				
				
				// 4h regime
				mailBody += "\n\n-------------------------\n4h TIMEFRAME REGIME:\n-------------------------\n\nTrend direction (strength):\n";
				Double trendStrength4h = (Double)mailValuesMap.get("MAsDistance4h").getValue();
				if (trendStrength4h.doubleValue() < -0.7) 
					mailBody += "FLAT (Strength: " + mailStringsMap.get("MAsDistance4h") + "); TrendID: " + mailStringsMap.get("TrendId4h");  			
				else
					mailBody += "TrendId" + ": " + mailStringsMap.get("TrendId4h") + " (" + mailStringsMap.get("MAsDistance4h") + ")";
				mailBody += "\nADX: " + mailStringsMap.get("ADX4h") + " / DI+: " + mailStringsMap.get("DI_PLUS4h") + " / DI-: " + mailStringsMap.get("DI_MINUS4h"); 
				
				mailBody += "\n\nMomentum:\nMACDState: " + mailStringsMap.get("MACDState4h") + " (StDevPos: " + mailStringsMap.get("MACDStDevPos4h") + "), ";  
				mailBody += "MACDHState: " + mailStringsMap.get("MACDHState4h")  + " (StDevPos: " + mailStringsMap.get("MACD_HStDevPos4h") 
					+ ")\nStochState: " + mailStringsMap.get("StochState4h"); 
				if (mailStringsMap.get("StochState4h").contains("OVER")) {
					Double stochFast4h = (Double)mailValuesMap.get("StochFast4h").getValue();
					Double stochSlow4h = (Double)mailValuesMap.get("StochSlow4h").getValue();
					if (stochFast4h > stochSlow4h)
						mailBody += " and RAISING (difference " + FXUtils.df1.format(stochFast4h - stochSlow4h) + ")";
					else 
						mailBody += " and FALLING (difference " + FXUtils.df1.format(stochFast4h - stochSlow4h) + ")";
				}
			
				mailBody +=  "\n\nOversold / Overbought:\n";				
				if (mailStringsMap.get("StochState4h").contains("OVER"))
					mailBody += "StochState: " + mailStringsMap.get("StochState4h") + ", ";  
				mailBody += "Stochs: " + mailStringsMap.get("StochFast4h") + "/" + mailStringsMap.get("StochSlow4h") + ", ";
				mailBody += "\nRSI4h: " + mailStringsMap.get("RSI4h") + " (" + mailStringsMap.get("RSIState4h") + ")"; 
				mailBody += "\nCCI4h: " + mailStringsMap.get("CCI4h") + " (" + mailStringsMap.get("CCIState4h") + ")";
			
				mailBody += "\n\nChannel position:\n";  		
				mailBody += "30min bar high/low: " + mailStringsMap.get("barHighChannelPos4h") + "%/"+ mailStringsMap.get("barLowChannelPos4h") + "%\n";
				if (!mailStringsMap.get("barsAboveChannel4h").equals("0"))
					mailBody += "barsAboveChannelTop ! (" + mailStringsMap.get("barsAboveChannel4h") + " bar(s))\n";
				else if (!mailStringsMap.get("barsBelowChannel4h").equals("0"))
					mailBody += "barsBelowChannel ! (" + mailStringsMap.get("barsBelowChannel4h") + " bar(s))\n";
				mailBody += "Channel width: " + mailStringsMap.get("bBandsWidth4h") + " pips\n";
				mailBody += "volatility4h (BBands squeeze): " + mailStringsMap.get("volatility4h") + "%\n";
			
				mailBody += "Volatility (all TFs)\nATR30min: " + mailStringsMap.get("ATR30min") + ", + 20%: " + mailStringsMap.get("ATR30min + 20%") + ", ";  
				mailBody += "ATR4h: " + mailStringsMap.get("ATR4h") + ", ATR1d: " + mailStringsMap.get("ATR1d") + "\n\n";  
				
				return mailBody;
			}

	protected String checkTakeProfitLevels(Instrument instrument, Period pPeriod,
			IBar bidBar) throws JFException {
				String taLevelsHit = new String();
				IBar barToCheck = bidBar;
				for (SRLevel levelsToCheck : takeProfitLevels) {
					if (alreadyHit(levelsToCheck, barToCheck))
						continue;
			
					if (levelsToCheck.hit(barToCheck, 10 / Math.pow(10, instrument.getPipScale()))) {
						if (taLevelsHit.length() == 0)
							taLevelsHit += "Take profit target " + levelsToCheck + " hit !";
						else
							taLevelsHit += ", Take profit target " + levelsToCheck + " hit !";
						try {
							Writer output = new BufferedWriter(new FileWriter("TakeProfitChecks.txt"));
							output.write("TP level:" + levelsToCheck + " hit on " + FXUtils.getFormatedTimeCET(bidBar.getTime()));
							output.close();
					    } catch (IOException e) {
							log.print("Exception " + e.getMessage() + " while writing TP levels..");
						} 
					}
				}
				return taLevelsHit;
			}

	protected boolean alreadyHit(SRLevel levelsToCheck, IBar barToCheck) {
		try {		    
			BufferedReader in = new BufferedReader(new FileReader("TakeProfitChecks.txt"));
		    String currentLine = null;
		    while ((currentLine = in.readLine()) != null) {		    	
		        if (currentLine.contains("TP level:") && currentLine.contains(levelsToCheck.getName()) && currentLine.contains(" hit on ")) {
		        	String dateStr = currentLine.substring(currentLine.indexOf(" hit on ") + 8);
		        	DateTimeFormatter fmt = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm");
		        	try {
						DateTime 
							dateHit = fmt.parseDateTime(dateStr),
							today = new DateTime();
						if (dateHit.getYear() == today.getYear()
							&& dateHit.getMonthOfYear() == today.getMonthOfYear()
							&& dateHit.getDayOfMonth() == today.getDayOfMonth()) {
						    in.close();
				        	return true;
						}
					} catch (IllegalArgumentException e2) {
		                log.print("Date format wrong: " + dateStr + ", exception " + e2.getMessage());
					} catch (UnsupportedOperationException e1) {
		                log.print("Date format wrong: " + dateStr + ", exception " + e1.getMessage());
					}
		        }
		    }
		    in.close();
		} catch (IOException e) {
			log.print("Exception " + e.getMessage() + " while writing TP levels..");
		}
		return false;
	}

	protected String printCurrentPnL(Instrument instrument) throws JFException {
		String result = new String();
		List<IOrder> dukaOrders = context.getEngine().getOrders(instrument);
		int openDukaOrders = 0;
		for (IOrder currOrder : dukaOrders) {
			if (currOrder.getState().equals(State.FILLED)) {
				openDukaOrders++;
				result += "Order " + openDukaOrders + " (" + currOrder.getId() + " - " + currOrder.getLabel() + "): PnL " 
					+ FXUtils.df1.format(currOrder.getProfitLossInPips()) + " / " 
					+ FXUtils.df1.format(currOrder.getProfitLossInUSD()) + " USD\n";
			}
		}
		if (openDukaOrders > 0) {
			result = "Open Dukascopy position(s):\n" + result; 
		}
		return result;
	}

	protected String getBullishSignalColor(Map<String, FlexLogEntry> mailValuesMap) {
		double
			totSize = mailValuesMap.get("bullishTriggerCombinedSizeStat30min").getDoubleValue(),
			lowerHandle = mailValuesMap.get("bullishTriggerCombinedLHPerc30min").getDoubleValue(),
			bodyPerc = mailValuesMap.get("bullishTriggerCombinedBodyPerc30min").getDoubleValue(),
			bodySign = (mailValuesMap.get("bullishTriggerCombinedBodyDirection30min").getFormattedValue().toLowerCase().equals("bullish") ? 1.0 : -1.0);
		bodyPerc *= bodySign;
		if (totSize > 2.0 && (lowerHandle > 66.0 || bodyPerc > 66.0))
			return new String("#090");
		else if (totSize > 1.0 && (lowerHandle > 60.0 || lowerHandle + bodyPerc > 60.0))
			return new String("#0C3");
		else if (totSize > 0.0 && (lowerHandle > 60.0 || lowerHandle + bodyPerc > 60.0))
			return new String("#0F6");
		return new String("#FFF");
	}
	
	protected String getBearishSignalColor(Map<String, FlexLogEntry> mailValuesMap) {
		double
			totSize = mailValuesMap.get("bearishTriggerCombinedSizeStat30min").getDoubleValue(),
			upperHandle = mailValuesMap.get("bearishTriggerCombinedUHPerc30min").getDoubleValue(),
			bodyPerc = mailValuesMap.get("bearishTriggerCombinedBodyPerc30min").getDoubleValue(),
			bodySign = (mailValuesMap.get("bearishTriggerCombinedBodyDirection30min").getFormattedValue().toLowerCase().equals("bearish") ? 1.0 : -1.0);
		bodyPerc *= bodySign;
		if (totSize > 2.0 && (upperHandle > 66.0 || bodyPerc > 66.0))
			return new String("#C00");
		else if (totSize > 1.0 && (upperHandle > 60.0 || upperHandle + bodyPerc > 60.0))
			return new String("#F00");
		else if (totSize > 0.0 && (upperHandle > 60.0 || upperHandle + bodyPerc > 60.0))
			return new String("#F66");
		return new String("#FFF");
	}
	
	protected String getBullishChannelPosColor(Map<String, FlexLogEntry> mailValuesMap) {
		double
			bBandsPos = mailValuesMap.get("bullishCandleTriggerChannelPos30min").getDoubleValue(),
			kChannelPos = mailValuesMap.get("bullishCandleTriggerKChannelPos30min").getDoubleValue(),
			vola = mailValuesMap.get("volatility30min").getDoubleValue();
		if (vola < 70) {
			if (kChannelPos < 5.0)
				return new String("#090");
			else if (kChannelPos < 10.0)
				return new String("#0C3");
			else if (kChannelPos < 15.0)
				return new String("#0F6");
		} else {
			if (bBandsPos < 5.0)
				return new String("#090");
			else if (bBandsPos < 20.0)
				return new String("#0C3");
			else if (bBandsPos <= 50.0)
				return new String("#0F6");
			
		}
		return new String("#FFF");
	}
	
	protected String getBearishChannelPosColor(Map<String, FlexLogEntry> mailValuesMap) {
		double
			bBandsPos = mailValuesMap.get("bearishCandleTriggerChannelPos30min").getDoubleValue(),
			kChannelPos = mailValuesMap.get("bearishCandleTriggerKChannelPos30min").getDoubleValue(),
			vola = mailValuesMap.get("volatility30min").getDoubleValue();
		if (vola < 70) {
			if (kChannelPos > 95.0)
				return new String("#C00");
			else if (kChannelPos > 90.0)
				return new String("#F00");
			else if (kChannelPos > 85.0)
				return new String("#F66");
		} else {
			if (bBandsPos > 95.0)
				return new String("#C00");
			else if (bBandsPos > 80.0)
				return new String("#F00");
			else if (bBandsPos >= 50.0)
				return new String("#F66");
			
		}
		return new String("#FFF");
	}	
	protected String getLightBlue() { return new String("#3CF"); }

	protected String getDarkBlue() { return new String("#06C");	}

	protected String getDarkGreen() { return new String("#090"); }

	protected String getGreen() { return new String("#0C3"); }

	protected String getLightGreen() { return new String("#0F6"); }

	protected String getLightRed() { return new String("#F66"); }

	protected String getRed() {	return new String("#F00"); }

	protected String getDarkRed() { return new String("#C00");	}

	protected String getWhite() { return new String("#FFF"); }

	protected String printSimpleTableHeader() {
		String res = new String();
		res += "<table width=\"780\" border=\"0\" cellspacing=\"2\" cellpadding=\"0\" style=\"text-align:left; vertical-align:middle; font-size:12px; line-height:20px; font-family:Arial, Helvetica, sans-serif; border:1px solid #f4f4f4;\">";		
		return res;
	}
}
