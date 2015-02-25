package jforex.emailflex.elements.candles;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Properties;import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

import jforex.emailflex.BaseFlexElement;
import jforex.emailflex.IFlexEmailElement;
import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;

public class CandleTriggerElement extends BaseFlexElement implements IFlexEmailElement {

@Override
 public String print(Instrument instrument, Period pPeriod, IBar bidBar,
   IHistory history, IIndicators indicators, Trend trendDetector,Channel channelPosition, Momentum momentum, Volatility vola,
   TradeTrigger tradeTrigger, Properties conf, List<FlexLogEntry> logLine, Connection logDB) throws JFException {
return new String();
 }


 @Override
 public String print(Instrument instrument, Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine, Connection logDB) {
	 ResultSet elementData = dbGetElementData(instrument, pPeriod, bidBar, logDB, 
			 "CandleTrigger, barStat, bullishCandleDescription, bullishTriggerCombinedSizeStat, bullishTriggerCombinedLHPerc, "
			 + "bullishTriggerCombinedBodyPerc, bullishTriggerCombinedBodyDirection, bullishTriggerCombinedUHPerc, bullishCandleTriggerChannelPos, "
			 + "bullishCandleTriggerKChannelPos, volatility, bullishPivotLevelHigherTFChannelPos, barHighChannelPos, avgHandleLength, barLow, barHigh, "
			 + "bullishPivotLevel, StochFast, StochSlow, MACDHState, RSIState, bearishPivotLevelHigherTFChannelPos, barLowChannelPos, bearishPivotLevel, "
			 + "bearishCandleTriggerChannelPos, bearishCandleTriggerKChannelPos, ", true);
	 if (elementData != null) {
		String candlesText = new String();
		DecimalFormat df = instrument.getSecondaryCurrency().equals("JPY") ? FXUtils.df1 : FXUtils.df5;
		try {
			String
				valueToShow = elementData.getString("CandleTrigger"),
				bullishCandles = null,
				bearishCandles = null,
				lowRiskHighQualitySignals = new String();
			boolean twoCandleSignals = false;
			
			if (valueToShow != null && !valueToShow.toUpperCase().equals("NONE")) {
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
					candlesText = valueToShow + "\n";
	
				if (valueToShow.contains("BULLISH")) {
					//TODO: check for any of explicit patterns found by JForex API
					String candleDesc = elementData.getString("bullishCandleDescription");
					if (twoCandleSignals)
						candlesText += "\n" + bullishCandles + (candleDesc != null && candleDesc.length() > 0 ? " (" + candleDesc + ")" : "");
					else 
						candlesText += "\n" + valueToShow + (candleDesc != null && candleDesc.length() > 0 ? " (" + candleDesc + ")" : "");
					candlesText += " (bar StDev size: " + FXUtils.df1.format(elementData.getString("barStat"));
					if (valueToShow.contains("BULLISH_1_BAR"))
						candlesText += ")\n";
					else
						candlesText += 
								", combined signal StDev size: " + FXUtils.df1.format(elementData.getDouble("bullishTriggerCombinedSizeStat"))
								+ ", lower handle : " + FXUtils.df1.format(elementData.getDouble("bullishTriggerCombinedLHPerc")) + "%"
								+ ", real body : " + FXUtils.df1.format(elementData.getDouble("bullishTriggerCombinedBodyPerc")) + "% ("
								+ elementData.getString("bullishTriggerCombinedBodyDirection")
								+ "), upper handle : " + FXUtils.df1.format(elementData.getDouble("bullishTriggerCombinedUHPerc")) + "%"
								+ ")\n";
					candlesText += "pivot bar low " + FXUtils.timeFrameNamesMap.get(pPeriod.toString()) + " BBands channel position: " 
						+ FXUtils.df1.format(elementData.getDouble("bullishCandleTriggerChannelPos")) + "%"
						+ "\nKeltner channel position (pivot bar): " + FXUtils.df1.format(elementData.getDouble("bullishCandleTriggerKChannelPos")) + "%"
						+ "\n(volatility (BBands squeeze): " + FXUtils.df1.format(elementData.getDouble("volatility")) + "%)";
					candlesText += "\npivot bar low " + FXUtils.timeFrameNamesMap.get(FXUtils.timeFramesHigherTFMap.get(pPeriod.toString())) + " channel position: " 
							+ FXUtils.df1.format(elementData.getDouble("bullishPivotLevelHigherTFChannelPos")) + "%\n";
					double barHighPerc = elementData.getDouble("barHighChannelPos");  				
					if (barHighPerc > 100)
						candlesText += "WARNING ! Possible breakout, careful with going long ! Bar high channel position " + FXUtils.df1.format(elementData.getDouble("barHighChannelPos30min")) + "%\n";  				 
					//TODO: candlesText += "\n" + checkTALevelsHit(instrument, pPeriod, bidBar, bullishCandles, false);
	
					double 
						avgHandleSize = elementData.getDouble("avgHandleLength"),
						barHalf = (elementData.getDouble("barLow") + (elementData.getDouble("barHigh") - elementData.getDouble("barLow")) / 2),
						bullishPivotLevel = elementData.getDouble("bullishPivotLevel"),
						aggressiveSL = bullishPivotLevel - avgHandleSize,
						riskStandardSTP = (elementData.getDouble("barHigh") - bullishPivotLevel) * Math.pow(10, instrument.getPipScale()),
						riskAggressiveSTP = (elementData.getDouble("barHigh") - aggressiveSL) * Math.pow(10, instrument.getPipScale()),
						riskStandardLMT = (barHalf - bullishPivotLevel) * Math.pow(10, instrument.getPipScale()),
						riskAggressiveLMT = (barHalf - aggressiveSL) * Math.pow(10, instrument.getPipScale());
					candlesText += "\nBUY STP: " + df.format(elementData.getDouble("barHigh")) 
						+ " (risks " + FXUtils.df1.format(riskStandardSTP) + "/" + FXUtils.df1.format(riskAggressiveSTP) 
						+ ")\nBUY LMT: " + df.format(barHalf)  
						+ " (risks " + FXUtils.df1.format(riskStandardLMT) + "/" + FXUtils.df1.format(riskAggressiveLMT) 
						+ ")\nSL: " + df.format(elementData.getDouble("bullishPivotLevel"))
						+ " (aggressive: " + df.format(aggressiveSL) + ")\n";
	
					//TODO: any technical levels in avg bar size distance from current pivot low
//					double avgBarSize = mailValuesMap.get("bar30minAvgSize").getDoubleValue();
//					String vicinity = checkNearTALevels(instrument, basicTimeFrame, bidBar, mailValuesMap, bullishPivotLevel.doubleValue(), avgBarSize, true);
//					if (vicinity != null && vicinity.length() > 0)
//						candlesText += vicinity + "\n";
					
	
					// now detect low risk high quality BULLISH entries !
					double
						triggerLowChannelPos30min = elementData.getDouble("bullishCandleTriggerChannelPos"),
						volatility30min = elementData.getDouble("volatility"),
						barLowChannelPos4h = elementData.getDouble("bullishPivotLevelHigherTFChannelPos"),
						triggerLowKChannelPos30min = elementData.getDouble("bullishCandleTriggerKChannelPos"),
						stochFast30min = elementData.getDouble("StochFast"),
						stochSlow30min = elementData.getDouble("StochSlow");
					
//					TODO: what about mail filtering ??
//					if ((volatility30min > 70 && triggerLowChannelPos30min <= 50.0) || (volatility30min <= 70.0 && triggerLowChannelPos30min < -5.0 && triggerLowKChannelPos30min < 15.0))
//						sendFilteredMail = true;
					
					if ((riskAggressiveSTP <= 25.0 || riskAggressiveLMT <= 25.0)
						&& ((volatility30min > 70 && triggerLowChannelPos30min < 10.0) || (volatility30min <= 70.0 && triggerLowChannelPos30min < -5.0 && triggerLowKChannelPos30min < 15.0))
						&& barLowChannelPos4h < 55.0
						&& (elementData.getString("MACDHState").contains("TICKED_UP") || elementData.getString("MACDHState30min").contains("RAISING")
								|| elementData.getString("RSIState").contains("TICKED_UP_FROM_OVERSOLD")
								|| (stochFast30min > stochSlow30min & (stochFast30min <= 20.0 || stochSlow30min <= 20.0)))) {
						lowRiskHighQualitySignals += "\nATTENTION: low risk and good quality BULLISH entry signal ! Risk STP " 
							+ FXUtils.df1.format(riskAggressiveSTP) + ", risk LMT " + FXUtils.df1.format(riskAggressiveLMT) + " !\n";
						//sendFilteredMail = true;
					}
					
					//TODO: now detect trigger pivot in entry zone !
//					PriceZone hit = null;
//					if ((hit = checkEntryZones(instrument, bullishPivotLevel.doubleValue(), true)) != null) {
//						lowRiskHighQualitySignals += "\nATTENTION: bullish candle trigger in long entry zone ! " + hit.getHitText() + " !\n"; 										
//						sendFilteredMail = true;
//					}				
				}
				if (valueToShow.contains(" AND "))
					candlesText += "\n";
				if (valueToShow.contains("BEARISH")) {
					if (twoCandleSignals)
						candlesText += "\n" + bearishCandles;
					else 
						candlesText += "\n" + valueToShow;
					candlesText += " (bar StDev size: " + elementData.getString("barStat");
					if (valueToShow.contains("BEARISH_1_BAR"))
						candlesText += ")\n";
					else
						candlesText +=  ", combined signal StDev size: " + FXUtils.df1.format(elementData.getDouble("bearishTriggerCombinedSizeStat"))
						+ ", lower handle : " + FXUtils.df1.format(elementData.getDouble("bearishTriggerCombinedLHPerc")) + "%"
						+ ", real body : " + FXUtils.df1.format(elementData.getDouble("bearishTriggerCombinedBodyPerc")) + "%, ("
						+ elementData.getString("bearishTriggerCombinedBodyDirection30min")
						+ "), upper handle : " + FXUtils.df1.format(elementData.getDouble("bearishTriggerCombinedUHPerc")) + "%"
						+ ")\n";
					candlesText += "pivot bar high BBands channel position: " 
						+ FXUtils.df1.format(elementData.getDouble("bearishCandleTriggerChannelPos")) + "%\n"
						+ "Keltner channel position (pivotBar): " + FXUtils.df1.format(elementData.getDouble("bearishCandleTriggerKChannelPos")) + "%\n"
						+ "(volatility (BBands squeeze): " + FXUtils.df1.format(elementData.getDouble("volatility")) + "%)";
					candlesText += "\nlast bar high " + FXUtils.timeFrameNamesMap.get(FXUtils.timeFramesHigherTFMap.get(pPeriod.toString())) + " channel position: " 
						+ FXUtils.df1.format(elementData.getDouble("bearishPivotLevelHigherTFChannelPos")) + "%\n";
					double barLowPerc = elementData.getDouble("barLowChannelPos");  				
					if (barLowPerc < 0)
						candlesText += "WARNING ! Possible breakout, careful with going short ! Bar low channel position " + FXUtils.df1.format(elementData.getDouble("barLowChannelPos")) + "%\n";  				 
					//TODO: candlesText += "\n" + checkTALevelsHit(instrument, pPeriod, bidBar, bearishCandles, true);
	
					double 
						avgHandleSize = elementData.getDouble("avgHandleLength"),
						barHalf = (elementData.getDouble("barLow") + (elementData.getDouble("barHigh") - elementData.getDouble("barLow")) / 2),
						bearishPivotLevel = elementData.getDouble("bearishPivotLevel"),
						aggressiveSL = bearishPivotLevel + avgHandleSize,
						riskStandardSTP = (bearishPivotLevel - elementData.getDouble("barLow")) * Math.pow(10, instrument.getPipScale()),
						riskAggressiveSTP = (aggressiveSL - elementData.getDouble("barLow")) * Math.pow(10, instrument.getPipScale()),
						riskStandardLMT = (bearishPivotLevel - barHalf) * Math.pow(10, instrument.getPipScale()),
						riskAggressiveLMT = (aggressiveSL - barHalf) * Math.pow(10, instrument.getPipScale());
					candlesText += "\nSELL STP: " + df.format(elementData.getDouble("barLow")) 
						+ " (risks " + FXUtils.df1.format(riskStandardSTP) + "/" + FXUtils.df1.format(riskAggressiveSTP) 
						+ ")\nSELL LMT: " + df.format(elementData.getDouble("barLow") + (elementData.getDouble("barHigh") - elementData.getDouble("barLow")) / 2)  
						+ " (risks " + FXUtils.df1.format(riskStandardLMT) + "/" + FXUtils.df1.format(riskAggressiveLMT) 
						+ ")\nSL: " + df.format(bearishPivotLevel) 
						+ " (aggressive: " + FXUtils.df5.format(aggressiveSL) + ")\n";

					//TODO: any technical levels in avg bar size distance from current pivot high
//					double avgBarSize = mailValuesMap.get("bar30minAvgSize").getDoubleValue();
//					String vicinity = checkNearTALevels(instrument, basicTimeFrame, bidBar, mailValuesMap, bearishPivotLevel.doubleValue(), avgBarSize, false);
//					if (vicinity != null && vicinity.length() > 0)
//						candlesText += vicinity + "\n";
					
					// now detect low risk high quality BEARISH entries !
					double
						triggerHighChannelPos30min = elementData.getDouble("bearishCandleTriggerChannelPos"),
						volatility30min = elementData.getDouble("volatility"),
						barHighChannelPos4h = elementData.getDouble("bearishPivotLevelHigherTFChannelPos"),
						triggerHighKChannelPos30min = elementData.getDouble("bearishCandleTriggerKChannelPos"),
						stochFast30min = elementData.getDouble("StochFast30min"),
						stochSlow30min = elementData.getDouble("StochSlow30min");
					
//					if ((volatility30min > 70 && triggerHighChannelPos30min >= 50.0) || (volatility30min <= 70.0 && triggerHighChannelPos30min > 105.0 && triggerHighKChannelPos30min > 85.0))
//							sendFilteredMail = true;
					
					if ((riskAggressiveSTP <= 25.0 || riskAggressiveLMT <= 25.0)
						&& ((volatility30min > 70 && triggerHighChannelPos30min > 90.0) || (volatility30min <= 70.0 && triggerHighChannelPos30min > 105.0 && triggerHighKChannelPos30min > 85.0))
						&& barHighChannelPos4h > 45.0
						&& (elementData.getString("MACDHState").contains("TICKED_DOWN") || elementData.getString("MACDHState").contains("FALLING")
							|| elementData.getString("RSIState").contains("TICKED_DOWN_FROM_OVERBOUGHT")
							|| (stochFast30min < stochSlow30min && (stochFast30min >= 80.0 || stochSlow30min >= 80.0)))) {
						lowRiskHighQualitySignals += "\nATTENTION: low risk and good quality BEARISH entry signal ! Risk STP " 
							+ FXUtils.df1.format(riskAggressiveSTP) + ", risk LMT " + FXUtils.df1.format(riskAggressiveLMT) + " !\n";
						//sendFilteredMail = true;
					}
	
					//TODO: now detect trigger pivot in entry zone !
//					PriceZone hit = null;
//					if ((hit = checkEntryZones(instrument, bearishPivotLevel.doubleValue(), false)) != null) {
//						lowRiskHighQualitySignals += "\nATTENTION: bearish candle trigger in short entry zone ! " + hit.getHitText() + " !\n"; 										
//						sendFilteredMail = true;
//					}
					
					
				}
			}
			return candlesText;				
			} catch (SQLException e) {
				   System.out.print("Log database problem in CandleTriggerElement: " + e.getMessage());
		           System.exit(1);
			}  
			return candlesText;
	}
	return null;
 }

 @Override
 public IFlexEmailElement cloneIt(Properties conf) {
  return cloneIt();
 }

 @Override
 public IFlexEmailElement cloneIt() {
  return new CandleTriggerElement();
 }

}