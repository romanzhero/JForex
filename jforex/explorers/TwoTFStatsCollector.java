package jforex.explorers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.StringTokenizer;

import jforex.AdvancedMailCreator;
import jforex.techanalysis.PriceZone;
import jforex.techanalysis.SRLevel;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;
import jforex.ib.IBBridge;
import com.dukascopy.api.Configurable;
import com.dukascopy.api.Filter;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.AppliedPrice;

public class TwoTFStatsCollector extends AdvancedMailCreator implements IStrategy {
	
	protected boolean 
		headerPrinted = false;
	
	protected static final String
		TREND_ID_30MIN = "TREND_ID_30MIN",
		TREND_ID_4H = "TREND_ID_4H",
		TREND_STRENGTH_30MIN = "TREND_STRENGTH_30MIN",
		TREND_STRENGTH_4H = "TREND_STRENGTH_4H";
	
	@Configurable("Period")
	public Period basicTimeFrame = Period.THIRTY_MINS;	
	
	protected IBBridge ib = null;

    public TwoTFStatsCollector(Properties p) {
		super(p);
	}

	@Override
	public void onStart(IContext context) throws JFException {
		super.onStartExec(context);
		if (conf.getProperty("mailToDBOnly", "no").equals("yes") || conf.getProperty("barStatsToDB", "no").equals("yes")) {
			dbLogOnStart();
		}
//		ib = new IBBridge();
//		ib.connect("", 4001, 0);
//		log.print(ib.getLastMessage());
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException { }

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException 
	{	
		if (skipPairs.contains(instrument))
			return;
		
		//log.print("Entered onBar before filtering for timeframe " + period.toString() + " and bidBar time of " + FXUtils.getFormatedBarTimeWithSecs(bidBar));
		if (!tradingAllowed(bidBar.getTime())) {
			//log.print("onBar - trading not allowed; timeframe " + period.toString() + " and bidBar time of " + FXUtils.getFormatedBarTimeWithSecs(bidBar));
			return;
		}
		
        // for all subscribed instruments i.e. no filtering there
/*		if (period.equals(Period.FIVE_MINS)) {
			//log.print("onBar - for 5mins; timeframe " + period.toString() + " and bidBar time of " + FXUtils.getFormatedBarTimeWithSecs(bidBar));
			sendReportMail(instrument, period, bidBar, null);
			return;
		}*/		
		if (!(period.equals(basicTimeFrame) || period.equals(Period.FOUR_HOURS))) {
			//log.print("onBar - irrelevant timeframe; timeframe " + period.toString() + " and bidBar time of " + FXUtils.getFormatedBarTimeWithSecs(bidBar));
            return;
        }
		
		// avoid timeframes below lowest set
		String lowestTF = pairsTimeFrames.get(instrument);
		if (period.equals(basicTimeFrame) && (lowestTF == null || !lowestTF.equals("30min")))
			return;
		
		log.print("Entered onBar AFTER filtering for timeframe " + period.toString() + " and bidBar time of " + FXUtils.getFormatedBarTimeWithSecs(bidBar));
        
        // Attention: 4h indicators are all calculated for 7 out 8 30min candles as INCOMPLETE bars ! On purpose - immediate reaction wanted, not up to 3,5 hours later !         

		List<FlexLogEntry> logLine = new ArrayList<FlexLogEntry>();
        
		eventsSource.collectAllStats(instrument, period, bidBar, logLine);
		
		if (conf.getProperty("mailToDBOnly", "no").equals("yes")) {
			dbLogSignalStats(instrument, period, logLine.get(2).getFormattedValue(), 
					period.equals(Period.THIRTY_MINS) ? createMailBody30min(instrument, period, bidBar, logLine) : createMailBody4h(instrument, period, bidBar, logLine));

		}
		else {
			// each record for a separate TF. Note NOT to use this for 30min TF otherwise too many records in the database !
			if (conf.getProperty("barStatsToDB", "no").equals("yes")) {			
				dbLogBarStats(instrument, period, logLine.get(2).getFormattedValue(), logLine);
				if (period.equals(basicTimeFrame))
					dbLogBarStats(instrument, Period.FOUR_HOURS, logLine.get(2).getFormattedValue(), logLine);
				else if (period.equals(Period.FOUR_HOURS))
					dbLogBarStats(instrument, Period.DAILY_SUNDAY_IN_MONDAY, logLine.get(2).getFormattedValue(), logLine);
			}
			
			if (!headerPrinted) {
				headerPrinted = true;
				log.printLabelsFlex(logLine);
			}
			log.printValuesFlex(logLine);
			
			sendReportMail(instrument, period, bidBar, logLine);
		}
		//if (period.equals(Period.THIRTY_MINS)) {

		String tfName = new String();
		if (period.equals(Period.FOUR_HOURS))
			tfName = "4h";
		else if (period.equals(Period.THIRTY_MINS))
			tfName = "30min";
		if (lowestTF == null || lowestTF.equals(tfName)) {
			File requestedCall = new File("requestedCall.bin");
			if (requestedCall.exists())
				requestedCall.delete();
		}
	}

	private void sendReportMail(Instrument instrument, Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		String 
			mailBody = null,
			mailSubject = null;  
		if (pPeriod.equals(Period.FIVE_MINS)) {
			mailBody = createMailBody5min(instrument, pPeriod, bidBar);
			mailSubject = new String("Take profit target hit for " + instrument.toString() + " at bar starting " + FXUtils.getFormatedTimeCET(bidBar.getTime()));
		}
		else if (pPeriod.equals(Period.FOUR_HOURS)) {
			mailBody = createMailBody4h(instrument, pPeriod, bidBar, logLine);
			mailSubject = new String("FX pair report for " + pPeriod.toString() + " timeframe: " + instrument.toString() + " at " + FXUtils.getFormatedTimeCET(bidBar.getTime()));
		}
		else if (pPeriod.equals(Period.THIRTY_MINS)) { 
				mailBody = createMailBody30min(instrument, pPeriod, bidBar, logLine);
				mailSubject = new String("FX pair report for " + pPeriod.toString() + " timeframe: " + instrument.toString() + " at " + FXUtils.getFormatedTimeCET(bidBar.getTime()));
		}
		else
			return;
		
		StringTokenizer t = new StringTokenizer(conf.getProperty("mail_recipients"), ";");
		int noOfRecepients = t.countTokens();
		if (noOfRecepients == 0)
			return;
		
		String[] recepients = new String[noOfRecepients];
		int i = 0;
		while (t.hasMoreTokens()) {
			recepients[i++] = t.nextToken();
		}
		
		if (conf.getProperty("sendMail", "no").equals("yes")
			&& (!filterMail
				//|| inPosition(instrument)
				|| mailBody.contains("time frame: 4 Hours")
				|| (pPeriod.equals(Period.FIVE_MINS) && mailBody.length() > 0)
				|| sendFilteredMail))
			sendMail("romanzhero@gmail.com", recepients, 
				mailSubject, 
				mailBody);
		
		if (conf.getProperty("logMail", "no").equals("yes"))
			log.print(mailBody);
	}

	private boolean inPosition(Instrument instrument) throws JFException {
		int openDukaOrders = 0;
		try {
			Scanner scanner = new Scanner(new FileInputStream(instrument.toString().replace("/", "") + "_open_orders.txt"));
			while (scanner.hasNextLine()){
				StringTokenizer orderRecord = new StringTokenizer(scanner.nextLine(), ":");
				// format is <ticker>:<direction (LONG/SHORT)>:<PnL in pips>:<open price>:<SL or 0 if now set>:<TP or 0 if not set>
				String 
					ticker = null,
					direction = null,
					openPrice = null,
					PnLinPips = null,
					stopLoss = null,
					takeProfit = null;
				for (int tokenPosition = 0; tokenPosition < 6 && orderRecord.hasMoreTokens(); tokenPosition++) {					
					String nextToken = orderRecord.nextToken();
					switch (tokenPosition) {
						case 0: ticker = new String(nextToken); break;
						case 1: direction = new String(nextToken); break;
						case 2: PnLinPips = new String(nextToken); break;
						case 3: openPrice = new String(nextToken); break;
						case 4: stopLoss = new String(nextToken); break;
						case 5: takeProfit = new String(nextToken); break;
						default: break;
					}
				}
				if (ticker.equals(instrument.toString()) && (direction.equals("LONG") || direction.equals("SHORT"))) {
					openDukaOrders++;
				}
			}
		    scanner.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return openDukaOrders > 0;
//		String ibName = instrument.toString().replace("/", ".");
//		ib.requestAllOpenOrdersFiltered(ibName);
//		return ib.getOpenOrders().size() > 0;
	}

	protected String createMailBody30min(Instrument instrument, Period pPeriod, IBar bidBar, List<FlexLogEntry> logLine) throws JFException {
		
		sendFilteredMail = false;
		
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}
		
		//TODO: mail body became quite complex. Needs to be split into easier to handle parts which are then composed into end result
		String 
			mailBody = new String(),
			candlesText = new String(),
			noGoZonesText = new String(),
			lowRiskHighQualitySignals = new String();
		
		mailBody = "Report for " 
			+ instrument.toString() + ", " 
			+ FXUtils.getFormatedTimeCET(bidBar.getTime()) 
			+ " CET (time frame: " + pPeriod.toString() 
			+ ")\n\n";

		mailBody += printCurrentPnL(instrument);
		
		double roc1 = indicators.roc(instrument, basicTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, 1, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0];
		mailBody += "30min price change: " + FXUtils.df1.format(roc1 * 100) + " pips";
		mailBody += " (current price: " + FXUtils.df5.format(bidBar.getClose()) + ", ";  
		mailBody += "last bar low: " + mailStringsMap.get("barLow30min") + " / ";  
		mailBody += "high: " + mailStringsMap.get("barHigh30min") + " / ";  		
		Double barHigh = (Double)mailValuesMap.get("barHigh30min").getValue();
		Double barLow = (Double)mailValuesMap.get("barLow30min").getValue();
		mailBody += "middle: " + FXUtils.df5.format(barLow.doubleValue() + (barHigh.doubleValue() - barLow.doubleValue()) / 2) + ")\n";  	
				
		IBar prevBar30min = history.getBar(instrument, pPeriod, OfferSide.BID, 2);
		String 
			lastTwo30MinBarsStats = new String("Last bar candle stats: upper handle = "  
				+ FXUtils.df1.format(tradeTrigger.barsUpperHandlePerc(bidBar)) + "% / " + 
				(bidBar.getClose() > bidBar.getOpen() ? "BULLISH" : "BEARISH") + " body = "  
				+ FXUtils.df1.format(tradeTrigger.barsBodyPerc(bidBar)) + "% / lower handle = "			
				+ FXUtils.df1.format(tradeTrigger.barsLowerHandlePerc(bidBar)) + "%\n"
				+ "Previous bar candle stats: upper handle = " 
				+ FXUtils.df1.format(tradeTrigger.barsUpperHandlePerc(prevBar30min)) + "% / " + 
				(prevBar30min.getClose() > prevBar30min.getOpen() ? "BULLISH" : "BEARISH") + " body = "  
				+ FXUtils.df1.format(tradeTrigger.barsBodyPerc(prevBar30min)) + "% / lower handle = "			
				+ FXUtils.df1.format(tradeTrigger.barsLowerHandlePerc(prevBar30min)) + "%\n"),
			valueToShow = mailStringsMap.get("CandleTrigger30min"),
			bullishCandles = null,
			bearishCandles = null;
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
				String candleDesc = tradeTrigger.bullishCandleDescription(instrument, pPeriod, OfferSide.BID, bidBar.getTime());
				if (twoCandleSignals)
					candlesText += "\n" + bullishCandles + (candleDesc != null && candleDesc.length() > 0 ? " (" + candleDesc + ")" : "");
				else 
					candlesText += "\n" + valueToShow + (candleDesc != null && candleDesc.length() > 0 ? " (" + candleDesc + ")" : "");
				candlesText += " (bar StDev size: " + mailStringsMap.get("barStat30min");
				if (valueToShow.contains("BULLISH_1_BAR"))
					candlesText += ")\n";
				else
					candlesText += 
							", combined signal StDev size: " + mailStringsMap.get("bullishTriggerCombinedSizeStat30min")
							+ ", lower handle : " + mailStringsMap.get("bullishTriggerCombinedLHPerc30min") + "%"
							+ ", real body : " + mailStringsMap.get("bullishTriggerCombinedBodyPerc30min") + "% ("
							+ mailStringsMap.get("bullishTriggerCombinedBodyDirection30min")
							+ "), upper handle : " + mailStringsMap.get("bullishTriggerCombinedUHPerc30min") + "%"
							+ ")\n";
				candlesText += "pivot bar low 30min channel positions: BBands " 
					+ mailStringsMap.get("bullishCandleTriggerChannelPos30min") 
					+ "% / Keltner channel position (pivot bar): " + mailStringsMap.get("bullishCandleTriggerKChannelPos30min") + "%"
					+ "\n(volatility30min (BBands squeeze): " + mailStringsMap.get("volatility30min") + "%)";
				candlesText += "\nlast bar low 4h channel position: " + mailStringsMap.get("barLowChannelPos4h") + "%\n";
				candlesText += lastTwo30MinBarsStats;
				double barHighPerc = (Double)mailValuesMap.get("barHighChannelPos30min").getValue();  				
				if (barHighPerc > 100)
					candlesText += "WARNING ! Possible breakout, careful with going long ! Bar high channel position " + mailStringsMap.get("barHighChannelPos30min") + "%\n";  				 

				double 
					avgHandleSize = tradeTrigger.avgHandleLength(instrument, basicTimeFrame, OfferSide.BID, bidBar, FXUtils.MONTH_WORTH_OF_30min_BARS);
				Double 
					barHalf = new Double(barLow.doubleValue() + (barHigh.doubleValue() - barLow.doubleValue()) / 2),
					bullishPivotLevel = (Double)mailValuesMap.get("bullishPivotLevel30min").getValue(),
					aggressiveSL = new Double(bullishPivotLevel.doubleValue() - avgHandleSize),
					riskStandardSTP = new Double((barHigh - bullishPivotLevel) * Math.pow(10, instrument.getPipScale())),
					riskAggressiveSTP = new Double((barHigh - aggressiveSL) * Math.pow(10, instrument.getPipScale())),
					riskStandardLMT = new Double((barHalf - bullishPivotLevel) * Math.pow(10, instrument.getPipScale())),
					riskAggressiveLMT = new Double((barHalf - aggressiveSL) * Math.pow(10, instrument.getPipScale()));
				candlesText += "\nBUY STP: " + mailStringsMap.get("barHigh30min") 
					+ " (risks " + FXUtils.df1.format(riskStandardSTP.doubleValue()) + "/" + FXUtils.df1.format(riskAggressiveSTP.doubleValue()) 
					+ ")\nBUY LMT: " + FXUtils.df5.format(barHalf.doubleValue())  
					+ " (risks " + FXUtils.df1.format(riskStandardLMT.doubleValue()) + "/" + FXUtils.df1.format(riskAggressiveLMT.doubleValue()) 
					+ ")\nSL: " + mailStringsMap.get("bullishPivotLevel30min")
					+ " (aggressive: " + FXUtils.df5.format(aggressiveSL.doubleValue()) + ")\n";

				// any technical levels in avg bar size distance from current pivot low
				double avgBarSize = mailValuesMap.get("bar30minAvgSize").getDoubleValue();
				String vicinity = checkNearTALevels(instrument, basicTimeFrame, bidBar, mailValuesMap, bullishPivotLevel.doubleValue(), avgBarSize, true);
				if (vicinity != null && vicinity.length() > 0)
					candlesText += vicinity + "\n";
				

				// now detect low risk high quality BULLISH entries !
				Double
					triggerLowChannelPos30min = (Double)mailValuesMap.get("bullishCandleTriggerChannelPos30min").getValue(),
					volatility30min = (Double)mailValuesMap.get("volatility30min").getValue(),
					barLowChannelPos4h = (Double)mailValuesMap.get("barLowChannelPos4h").getValue(),
					triggerLowKChannelPos30min = (Double)mailValuesMap.get("bullishCandleTriggerKChannelPos30min").getValue(),
					stochFast30min = (Double)mailValuesMap.get("StochFast30min").getValue(),
					stochSlow30min = (Double)mailValuesMap.get("StochSlow30min").getValue();
				
				if ((volatility30min > 70 && triggerLowChannelPos30min <= 50.0) || (volatility30min <= 70.0 && triggerLowChannelPos30min < -5.0 && triggerLowKChannelPos30min < 15.0))
					sendFilteredMail = true;
				
				if ((riskAggressiveSTP <= 25.0 || riskAggressiveLMT <= 25.0)
					&& ((volatility30min > 70 && triggerLowChannelPos30min < 10.0) || (volatility30min <= 70.0 && triggerLowChannelPos30min < -5.0 && triggerLowKChannelPos30min < 15.0))
					&& barLowChannelPos4h < 55.0
					&& (mailStringsMap.get("MACDHState30min").contains("TICKED_UP") || mailStringsMap.get("MACDHState30min").contains("RAISING")
							|| momentum.getRSIState(instrument, pPeriod, OfferSide.BID, bidBar.getTime()).toString().contains("TICKED_UP_FROM_OVERSOLD")
							|| (stochFast30min > stochSlow30min & (stochFast30min <= 20.0 || stochSlow30min <= 20.0)))) {
					lowRiskHighQualitySignals += "\nATTENTION: low risk and good quality BULLISH entry signal ! Risk STP " 
						+ FXUtils.df1.format(riskAggressiveSTP) + ", risk LMT " + FXUtils.df1.format(riskAggressiveLMT) + " !\n";
					sendFilteredMail = true;
				}
				
				// now detect trigger pivot in entry zone !
				PriceZone hit = null;
				if ((hit = checkEntryZones(instrument, bullishPivotLevel.doubleValue(), true)) != null) {
					lowRiskHighQualitySignals += "\nATTENTION: bullish candle trigger in long entry zone ! " + hit.getHitText() + " !\n"; 										
					sendFilteredMail = true;
				}				
			}
			if (valueToShow.contains(" AND "))
				candlesText += "\n";
			if (valueToShow.contains("BEARISH")) {
				if (twoCandleSignals)
					candlesText += "\n" + bearishCandles;
				else 
					candlesText += "\n" + valueToShow;
				candlesText += " (bar StDev size: " + mailStringsMap.get("barStat30min");
				if (valueToShow.contains("BEARISH_1_BAR"))
					candlesText += ")\n";
				else
					candlesText +=  ", combined signal StDev size: " + mailStringsMap.get("bearishTriggerCombinedSizeStat30min")
					+ ", lower handle : " + mailStringsMap.get("bearishTriggerCombinedLHPerc30min") + "%"
					+ ", real body : " + mailStringsMap.get("bearishTriggerCombinedBodyPerc30min") + "%, ("
					+ mailStringsMap.get("bearishTriggerCombinedBodyDirection30min")
					+ "), upper handle : " + mailStringsMap.get("bearishTriggerCombinedUHPerc30min") + "%"
					+ ")\n";
				candlesText += "pivot bar high channels position: BBands " 
					+ mailStringsMap.get("bearishCandleTriggerChannelPos30min") 
					+ "% / Keltner channel position (pivotBar): " + mailStringsMap.get("bearishCandleTriggerKChannelPos30min") + "%\n"
					+ "(volatility30min (BBands squeeze): " + mailStringsMap.get("volatility30min") + "%)";
				candlesText += "\nlast bar high 4h channel position: " + mailStringsMap.get("barHighChannelPos4h") + "%\n";
				candlesText += lastTwo30MinBarsStats;
				double barLowPerc = (Double)mailValuesMap.get("barLowChannelPos30min").getValue();  				
				if (barLowPerc < 0)
					candlesText += "WARNING ! Possible breakout, careful with going short ! Bar low channel position " + mailStringsMap.get("barLowChannelPos30min") + "%\n";  				 

				double 
					avgHandleSize = tradeTrigger.avgHandleLength(instrument, basicTimeFrame, OfferSide.BID, bidBar, FXUtils.MONTH_WORTH_OF_30min_BARS);
				Double 
					barHalf = new Double(barLow.doubleValue() + (barHigh.doubleValue() - barLow.doubleValue()) / 2),
					bearishPivotLevel = (Double)mailValuesMap.get("bearishPivotLevel30min").getValue(),
					aggressiveSL = new Double(bearishPivotLevel.doubleValue() + avgHandleSize),
					riskStandardSTP = new Double((bearishPivotLevel - barLow) * Math.pow(10, instrument.getPipScale())),
					riskAggressiveSTP = new Double((aggressiveSL - barLow) * Math.pow(10, instrument.getPipScale())),
					riskStandardLMT = new Double((bearishPivotLevel - barHalf) * Math.pow(10, instrument.getPipScale())),
					riskAggressiveLMT = new Double((aggressiveSL - barHalf) * Math.pow(10, instrument.getPipScale()));
				candlesText += "\nSELL STP: " + mailStringsMap.get("barLow30min") 
					+ " (risks " + FXUtils.df1.format(riskStandardSTP.doubleValue()) + "/" + FXUtils.df1.format(riskAggressiveSTP.doubleValue()) 
					+ ")\nSELL LMT: " + FXUtils.df5.format(barLow.doubleValue() + (barHigh.doubleValue() - barLow.doubleValue()) / 2)  
					+ " (risks " + FXUtils.df1.format(riskStandardLMT.doubleValue()) + "/" + FXUtils.df1.format(riskAggressiveLMT.doubleValue()) 
					+ ")\nSL: " + mailStringsMap.get("bearishPivotLevel30min") 
					+ " (aggressive: " + FXUtils.df5.format(aggressiveSL.doubleValue()) + ")\n";
				// any technical levels in avg bar size distance from current pivot high
				double avgBarSize = mailValuesMap.get("bar30minAvgSize").getDoubleValue();
				String vicinity = checkNearTALevels(instrument, basicTimeFrame, bidBar, mailValuesMap, bearishPivotLevel.doubleValue(), avgBarSize, false);
				if (vicinity != null && vicinity.length() > 0)
					candlesText += vicinity + "\n";
				
				// now detect low risk high quality BEARISH entries !
				Double
					triggerHighChannelPos30min = (Double)mailValuesMap.get("bearishCandleTriggerChannelPos30min").getValue(),
					volatility30min = (Double)mailValuesMap.get("volatility30min").getValue(),
					barHighChannelPos4h = (Double)mailValuesMap.get("barHighChannelPos4h").getValue(),
					triggerHighKChannelPos30min = (Double)mailValuesMap.get("bearishCandleTriggerKChannelPos30min").getValue(),
					stochFast30min = (Double)mailValuesMap.get("StochFast30min").getValue(),
					stochSlow30min = (Double)mailValuesMap.get("StochSlow30min").getValue();
				
				if ((volatility30min > 70 && triggerHighChannelPos30min >= 50.0) || (volatility30min <= 70.0 && triggerHighChannelPos30min > 105.0 && triggerHighKChannelPos30min > 85.0))
						sendFilteredMail = true;
				
				if ((riskAggressiveSTP <= 25.0 || riskAggressiveLMT <= 25.0)
					&& ((volatility30min > 70 && triggerHighChannelPos30min > 90.0) || (volatility30min <= 70.0 && triggerHighChannelPos30min > 105.0 && triggerHighKChannelPos30min > 85.0))
					&& barHighChannelPos4h > 45.0
					&& (mailStringsMap.get("MACDHState30min").contains("TICKED_DOWN") || mailStringsMap.get("MACDHState30min").contains("FALLING")
						|| momentum.getRSIState(instrument, pPeriod, OfferSide.BID, bidBar.getTime()).toString().contains("TICKED_DOWN_FROM_OVERBOUGHT")
						|| (stochFast30min < stochSlow30min && (stochFast30min >= 80.0 || stochSlow30min >= 80.0)))) {
					lowRiskHighQualitySignals += "\nATTENTION: low risk and good quality BEARISH entry signal ! Risk STP " 
						+ FXUtils.df1.format(riskAggressiveSTP) + ", risk LMT " + FXUtils.df1.format(riskAggressiveLMT) + " !\n";
					sendFilteredMail = true;
				}

				// now detect trigger pivot in entry zone !
				PriceZone hit = null;
				if ((hit = checkEntryZones(instrument, bearishPivotLevel.doubleValue(), false)) != null) {
					lowRiskHighQualitySignals += "\nATTENTION: bearish candle trigger in short entry zone ! " + hit.getHitText() + " !\n"; 										
					sendFilteredMail = true;
				}	
			}
			
			Double trendStrength4h = (Double)mailValuesMap.get("MAsDistance4h").getValue();
			if (trendStrength4h.doubleValue() < -0.7) 
				candlesText += "\n\n4h trend: FLAT (Strength: " + mailStringsMap.get("MAsDistance4h") + "); TrendID: " + mailStringsMap.get("TrendId4h");  			
			else
				candlesText += "\n\n4h trend: " + mailStringsMap.get("TrendId4h") + " (" + mailStringsMap.get("MAsDistance4h") + ")";

			Double stochFast4h = (Double)mailValuesMap.get("StochFast4h").getValue();
			Double stochSlow4h = (Double)mailValuesMap.get("StochSlow4h").getValue();
			String stochsToShow = FXUtils.df1.format(stochFast4h) + "/" + FXUtils.df1.format(stochSlow4h);
			candlesText += "\n4h momentum: MACDState " + mailStringsMap.get("MACDState4h")  
					+  " / MACDHState " + mailStringsMap.get("MACDHState4h")  
					+ " / StochState " + mailStringsMap.get("StochState4h");
			if (mailStringsMap.get("StochState4h").contains("OVER")) {
				if (stochFast4h > stochSlow4h)
					candlesText  += " and RAISING (" + stochsToShow + " --> difference " + FXUtils.df1.format(stochFast4h - stochSlow4h) + ")";
				else 
					candlesText += " and FALLING (" + stochsToShow + " --> difference " + FXUtils.df1.format(stochFast4h - stochSlow4h) + ")";
			}
			else
				candlesText += " (" + stochsToShow + " --> difference " + FXUtils.df1.format(stochFast4h - stochSlow4h) + ")";
			
			Double trendStrength30min = (Double)mailValuesMap.get("MAsDistance30min").getValue();
			if (trendStrength30min.doubleValue() < -0.7) 
				candlesText += "\n\n30min trend: FLAT (Strength: " + mailStringsMap.get("MAsDistance30min") + "); TrendID: " + mailStringsMap.get("TrendId30min");  			
			else
				candlesText += "\n\n30min trend: " + mailStringsMap.get("TrendId30min") + " (" + mailStringsMap.get("MAsDistance30min") + ")";  

			Double stochFast30min = (Double)mailValuesMap.get("StochFast30min").getValue();
			Double stochSlow30min = (Double)mailValuesMap.get("StochSlow30min").getValue();
			stochsToShow = FXUtils.df1.format(stochFast30min) + "/" + FXUtils.df1.format(stochSlow30min);
			candlesText += "\n30min momentum: MACDState " + mailStringsMap.get("MACDState30min")  
				+  " / MACDHState " + mailStringsMap.get("MACDHState30min")  
				+ " / StochState " + mailStringsMap.get("StochState30min"); 
			if (mailStringsMap.get("StochState30min").contains("OVER")) {
				if (stochFast30min > stochSlow30min)
					candlesText += " and RAISING (" + stochsToShow + " --> difference " + FXUtils.df1.format(stochFast30min - stochSlow30min) + ")";
				else 
					candlesText += " and FALLING (" + stochsToShow + " --> difference " + FXUtils.df1.format(stochFast30min - stochSlow30min) + ")";
			}
			else
				candlesText += " (" + stochsToShow + " --> difference " + FXUtils.df1.format(stochFast30min - stochSlow30min) + ")";
		}
		valueToShow = mailStringsMap.get("CandleTrigger4h");
		if (valueToShow != null && !valueToShow.toUpperCase().equals("NONE") && !valueToShow.toLowerCase().equals("n/a"))
			candlesText += (candlesText.length() > 0 ? ", 4h: " : "4h: ") + valueToShow;

		
		// check no-go zones
		noGoZonesText += checkNoGoZones(mailStringsMap, mailValuesMap);
		if (candlesText.length() > 0) 
			mailBody += "\n" + lowRiskHighQualitySignals + "\n" + noGoZonesText + "\nCandles: " + candlesText + "\n";
		else
			mailBody += noGoZonesText + "\n";

		mailBody += get3TFOverview(instrument, pPeriod, bidBar, mailStringsMap, mailValuesMap);		
		
		// other COMBINED signals
		if (((Double)mailValuesMap.get("barLowChannelPos30min").getValue()).doubleValue() < 50.0)
		{
			boolean additionalSignal = false;
			if (mailStringsMap.get("MACDHState30min").equals("TICKED_UP_BELOW_ZERO")) {
				mailBody += "\nAdditional signals: MACDHistogram 30min " + mailStringsMap.get("MACDHState30min");
				additionalSignal = true;
			}
			if (momentum.getStochCross(instrument, pPeriod, OfferSide.BID, bidBar.getTime()).toString().equals("BULLISH_CROSS_FROM_OVERSOLD")) {
				mailBody += "\nAdditional signals: Stoch 30min BULLISH CROSS from oversold";
				additionalSignal = true;				
			}
			if (momentum.getRSIState(instrument, pPeriod, OfferSide.BID, bidBar.getTime()).toString().contains("TICKED_UP_FROM_OVERSOLD")) {
				mailBody += "\nAdditional signals: RSI 30min TICKED UP from oversold";
				additionalSignal = true;				
			}
			if (additionalSignal) {
				mailBody += "\nand bar low 30min channelPos " + mailStringsMap.get("barLowChannelPos30min") + "% / 4h channelPos " + mailStringsMap.get("barLowChannelPos4h") + "%\n";

				double avgHandleSize = tradeTrigger.avgHandleLength(instrument, basicTimeFrame, OfferSide.BID, bidBar, FXUtils.MONTH_WORTH_OF_30min_BARS);
				IBar prevBar = history.getBar(instrument, pPeriod, OfferSide.BID, 2);
				Double 
					barHalf = new Double(barLow.doubleValue() + (barHigh.doubleValue() - barLow.doubleValue()) / 2),
					bullishPivotLevel = new Double(barLow < prevBar.getLow() ? barLow : prevBar.getLow()),
					aggressiveSL = new Double(bullishPivotLevel.doubleValue() - avgHandleSize),
					riskStandard = new Double((barHigh - bullishPivotLevel) * Math.pow(10, instrument.getPipScale())),
					riskAggressive = new Double((barHigh - aggressiveSL) * Math.pow(10, instrument.getPipScale())),
					riskStandard2 = new Double((barHalf - bullishPivotLevel) * Math.pow(10, instrument.getPipScale())),
					riskAggressive2 = new Double((barHalf - aggressiveSL) * Math.pow(10, instrument.getPipScale()));
				mailBody += "\nBUY STP: " + mailStringsMap.get("barHigh30min") 
					+ " (risks " + FXUtils.df1.format(riskStandard.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive.doubleValue()) 
					+ ")\nBUY LMT: " + FXUtils.df5.format(barHalf.doubleValue())  
					+ " (risks " + FXUtils.df1.format(riskStandard2.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive2.doubleValue()) 
					+ ")\nSL: " + FXUtils.df5.format(bullishPivotLevel)
					+ " (aggressive: " + FXUtils.df5.format(aggressiveSL.doubleValue()) + ")\n";
			}
		}
		else if (((Double)mailValuesMap.get("barHighChannelPos30min").getValue()).doubleValue() > 50.0)
		{
			boolean additionalSignal = false;
			if (mailStringsMap.get("MACDHState30min").equals("TICKED_DOWN_ABOVE_ZERO")) {
				mailBody += "\nAdditional signals: MACDHistogram 30min " + mailStringsMap.get("MACDHState30min");
				additionalSignal = true;
			}
			if (momentum.getStochCross(instrument, pPeriod, OfferSide.BID, bidBar.getTime()).toString().equals("BEARISH_CROSS_FROM_OVERBOUGTH")) {
				mailBody += "\nAdditional signals: Stoch 30min BEARISH CROSS from overbought";
				additionalSignal = true;				
			}
			if (momentum.getRSIState(instrument, pPeriod, OfferSide.BID, bidBar.getTime()).toString().equals("TICKED_DOWN_FROM_OVERBOUGHT")) {
				mailBody += "\nAdditional signals: RSI 30min TICKED DOWN from overbought";
				additionalSignal = true;				
			}
			
			if (additionalSignal) {
				mailBody += "\nand bar high channelPos " + mailStringsMap.get("barHighChannelPos30min")  + "% / 4h channelPos " + mailStringsMap.get("barHighChannelPos4h") + "%\n";
				IBar prevBar = history.getBar(instrument, pPeriod, OfferSide.BID, 2);
				double avgHandleSize = tradeTrigger.avgHandleLength(instrument, basicTimeFrame, OfferSide.BID, bidBar, FXUtils.MONTH_WORTH_OF_30min_BARS);
				Double 
					barHalf = new Double(barLow.doubleValue() + (barHigh.doubleValue() - barLow.doubleValue()) / 2),
					bearishPivotLevel = new Double(barHigh > prevBar.getHigh() ? barHigh : prevBar.getHigh()),
					aggressiveSL = new Double(bearishPivotLevel.doubleValue() + avgHandleSize),
					riskStandard = new Double((bearishPivotLevel - barLow) * Math.pow(10, instrument.getPipScale())),
					riskAggressive = new Double((aggressiveSL - barLow) * Math.pow(10, instrument.getPipScale())),
					riskStandard2 = new Double((bearishPivotLevel - barHalf) * Math.pow(10, instrument.getPipScale())),
					riskAggressive2 = new Double((aggressiveSL - barHalf) * Math.pow(10, instrument.getPipScale()));
				mailBody += "\nSELL STP: " + mailStringsMap.get("barLow30min") 
					+ " (risks " + FXUtils.df1.format(riskStandard.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive.doubleValue()) 
					+ ")\nSELL LMT: " + FXUtils.df5.format(barLow.doubleValue() + (barHigh.doubleValue() - barLow.doubleValue()) / 2)  
					+ " (risks " + FXUtils.df1.format(riskStandard2.doubleValue()) + "/" + FXUtils.df1.format(riskAggressive2.doubleValue()) 
					+ ")\nSL: " + FXUtils.df5.format(bearishPivotLevel) 
					+ " (aggressive: " + FXUtils.df5.format(aggressiveSL.doubleValue()) + ")\n";
			}
		}
		
		if (!mailStringsMap.get("MACDCross30min").equals("NONE") || !mailStringsMap.get("MACDCross4h").equals("NONE")) {
			mailBody += "\nMACD cross ! ";
			if (!mailStringsMap.get("MACDCross30min").equals("NONE"))
				mailBody += "MACD30min: " + mailStringsMap.get("MACDCross30min") + " (StDevPos: " + mailStringsMap.get("MACDStDevPos30min") + ")";
			if (!mailStringsMap.get("MACDCross4h").equals("NONE"))
				if (!mailStringsMap.get("MACDCross30min").equals("NONE"))
					mailBody += "; MACD4h: " + mailStringsMap.get("MACDCross4h") + " (StDevPos: " + mailStringsMap.get("MACDStDevPos4h") + ")";
				else
					mailBody += "MACD4h: " + mailStringsMap.get("MACDCross4h");
			mailBody += "\n\n";
		}
		
		String overlaps = new String();
		for (FlexLogEntry e : logLine) {
			if (e.getLabel().contains("Overlap") && e.getFormattedValue().startsWith("YES")) {
				overlaps = e.getLabel() + " " + e.getFormattedValue().substring(3) + "\n";
			}
		}
		if (overlaps.length() > 0)
			overlaps = "\nTimeframes overlaps:\n" + overlaps;
		mailBody += overlaps + "\n\n";

		Double rsi30min = (Double)mailValuesMap.get("RSI30min").getValue();
		Double rsi4h = (Double)mailValuesMap.get("RSI4h").getValue();
		Double cci4h = (Double)mailValuesMap.get("CCI4h").getValue();
		Double barHighPos30min = (Double)mailValuesMap.get("barHighChannelPos30min").getValue(); 
		Double barLowPos30min = (Double)mailValuesMap.get("barLowChannelPos30min").getValue();  
		Double barHighPos4h = (Double)mailValuesMap.get("barHighChannelPos4h").getValue(); 
		Double barLowPos4h = (Double)mailValuesMap.get("barLowChannelPos4h").getValue();
		Double bBandsSqueeze30min = (Double)mailValuesMap.get("volatility30min").getValue();
		Double flatRegime30min = (Double)mailValuesMap.get("MAsDistance30min").getValue();		
		Double flatRegime4h= (Double)mailValuesMap.get("MAsDistance4h").getValue();		
		Double adx4h = (Double)mailValuesMap.get("ADX4h").getValue();
		Double adx30min = (Double)mailValuesMap.get("ADX30min").getValue();
		Double lowVolatility4h = (Double)mailValuesMap.get("volatility4h").getValue();
		Double bar30minStat = (Double)mailValuesMap.get("barStat30min").getValue();
		Integer barsAboveChannel30min = (Integer)mailValuesMap.get("barsAboveChannel30min").getValue();
		Integer barsBelowChannel30min = (Integer)mailValuesMap.get("barsBelowChannel30min").getValue();
		Integer barsAboveChannel4h = (Integer)mailValuesMap.get("barsAboveChannel4h").getValue();
		Integer barsBelowChannel4h = (Integer)mailValuesMap.get("barsBelowChannel4h").getValue();
				
		if ((rsi30min.doubleValue() > 68.0 || rsi30min.doubleValue() < 32.0)
			|| (rsi4h.doubleValue() > 68.0 || rsi4h.doubleValue() < 32.0)
			|| (cci4h.doubleValue() > 190.0 || cci4h.doubleValue() < -190.0)
			|| barHighPos30min.doubleValue() > 95.0
			|| barHighPos30min.doubleValue() < 0.0
			|| barLowPos30min.doubleValue() < 5.0
			|| barLowPos30min.doubleValue() > 100.0
			|| barHighPos4h.doubleValue() > 95.0
			|| barLowPos4h.doubleValue() < 5.0
			|| mailStringsMap.get("StochState30min").contains("OVER")
			|| mailStringsMap.get("StochState4h").contains("OVER")
			|| (bBandsSqueeze30min < 70.0 || bBandsSqueeze30min > 180.0)
			|| flatRegime30min < -0.7
			|| flatRegime4h < -0.7
			|| adx4h > 40.0
			|| adx30min > 40.0
			|| lowVolatility4h < 70.0
			|| bar30minStat > 2.0
			|| barsAboveChannel30min > 1
			|| barsBelowChannel30min > 1
			|| barsAboveChannel4h > 1
			|| barsBelowChannel4h > 1) {
			mailBody += "Extreme values:";
			if (flatRegime30min < -0.7)
				mailBody += "\nflat regime 30min: " + mailStringsMap.get("MAsDistance30min");
			if (flatRegime4h < -0.7)
				mailBody += "\nflat regime 4h: " + mailStringsMap.get("MAsDistance4h");
			if (rsi30min.doubleValue() > 68 || rsi30min.doubleValue() < 32)
				mailBody += "\nRSI 30min: " + mailStringsMap.get("RSI30min");
			if (rsi4h.doubleValue() > 68 || rsi4h.doubleValue() < 32)
				mailBody += "\nRSI 4h: " + mailStringsMap.get("RSI4h");
			if (cci4h.doubleValue() > 190.0 || cci4h.doubleValue() < -190.0) {
				mailBody += "\nCCI 4h: " + mailStringsMap.get("CCI4h") + " (" + mailStringsMap.get("CCIState4h") + ")";
				if (cci4h.doubleValue() < -190.0 && mailStringsMap.get("CCIState4h").contains("TICKED_UP"))
					sendFilteredMail = true;
				if (cci4h.doubleValue() > 190.0 && mailStringsMap.get("CCIState4h").contains("TICKED_DOWN"))
					sendFilteredMail = true;
			}
			if (barHighPos30min.doubleValue() > 95.0)				
				mailBody += "\nBar high in 30min channel: " + mailStringsMap.get("barHighChannelPos30min") + " %";
			if (barHighPos30min.doubleValue() < 0.0)
				mailBody += "\nBar high BELOW 30min channel: " + mailStringsMap.get("barHighChannelPos30min") + " %";
			if (barLowPos30min.doubleValue() < 5)
				mailBody += "\nBar low in 30min channel: " + mailStringsMap.get("barLowChannelPos30min") + " %";
			if (barLowPos30min.doubleValue() > 100)
				mailBody += "\nBar low ABOVE 30min channel: " + mailStringsMap.get("barLowChannelPos30min") + " %";
			if (barHighPos4h.doubleValue() > 95.0)
				mailBody += "\nBar high in 4h channel: " + mailStringsMap.get("barHighChannelPos4h") + " %";
			if (barLowPos4h.doubleValue() < 5)
				mailBody += "\nBar low in 4h channel: " + mailStringsMap.get("barLowChannelPos4h") + " %";
			if (mailStringsMap.get("StochState30min").contains("OVER"))
				mailBody += "\nStoch 30min: " + mailStringsMap.get("StochState30min");
			if (mailStringsMap.get("StochState4h").contains("OVER"))
				mailBody += "\nStoch 4h: " + mailStringsMap.get("StochState4h");
			if (bBandsSqueeze30min < 70.0)
				mailBody += "\nlow 30min volatility (BBands squeeze): " + mailStringsMap.get("volatility30min") + "%";
			if (bBandsSqueeze30min > 180.0)
				mailBody += "\nhigh 30min volatility (BBands squeeze): " + mailStringsMap.get("volatility30min") + "%";
			if (adx4h > 40.0)
				mailBody += "\nADX 4h: " + mailStringsMap.get("ADX4h") + " (DI+: " + mailStringsMap.get("DI_PLUS4h") + " / DI-: " + mailStringsMap.get("DI_MINUS4h") + ")"; 
			if (adx30min > 40.0)
				mailBody += "\nADX 30min: " + mailStringsMap.get("ADX30min") + " (DI+: " + mailStringsMap.get("DI_PLUS30min") + " / DI-: " + mailStringsMap.get("DI_MINUS30min") + ")"; 
			if (lowVolatility4h < 70.0)
				mailBody += "\nlow 4h volatility (BBands squeeze): " + mailStringsMap.get("volatility4h") + "%";
			if (bar30minStat > 2.0) {
				mailBody += "\nBig bar range: " + FXUtils.df1.format(bar30minStat) + " StDev(s) from average ("
					+ FXUtils.df1.format((bidBar.getHigh() - bidBar.getLow()) * Math.pow(10, instrument.getPipScale()))
					+ " pips) !\nCandle stats: upper handle = " 
						+ FXUtils.df1.format(tradeTrigger.barsUpperHandlePerc(bidBar)) + "% / " 
					+ (bidBar.getOpen() < bidBar.getClose() ? "BULLISH" : "BEARISH") + " body = "  
					+ FXUtils.df1.format(tradeTrigger.barsBodyPerc(bidBar)) + "% / lower handle = "
					+ FXUtils.df1.format(tradeTrigger.barsLowerHandlePerc(bidBar)) + "%";
				sendFilteredMail = true;
			}
			if (barsAboveChannel30min > 1)
				mailBody += "\n" + barsAboveChannel30min + " 30min bars ABOVE channel top !";  	
			if (barsBelowChannel30min > 1)
				mailBody += "\n" + barsBelowChannel30min + " 30min bars BELOW channel bottom !";  	
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

		mailBody += "\n\nChannel position:\n"  		
			+ "last 30min bar high/low: " + mailStringsMap.get("barHighChannelPos1d") + "%/"+ mailStringsMap.get("barLowChannelPos1d") + "%\n"
			+ "Channel width: " + mailStringsMap.get("bBandsWidth1d") + " pips\n";
		// zameni sa racunanjem...
/*			+ "last 30min bar high distance to channel top: " + mailStringsMap.get("bBandsTopBarHighDiff1d") + " pips\n"
			+ "last 30min bar low distance to channel bottom: " + mailStringsMap.get("bBandsBottomBarLow1d") + " pips\n";
*/		
		if (!mailStringsMap.get("barsAboveChannel1d").equals("0"))
			mailBody += "barsAboveChannelTop ! (" + mailStringsMap.get("barsAboveChannel1d") + " day(s))\n";
		else if (!mailStringsMap.get("barsBelowChannel1d").equals("0"))
			mailBody += "barsBelowChannel ! (" + mailStringsMap.get("barsBelowChannel1d") + " day(s))\n";
		
		String
			candlesText1d = new String(),
			valueToShow1d = mailStringsMap.get("CandleTrigger1d"),
			bullishCandles1d = null,
			bearishCandles1d = null;
		boolean twoCandleSignals1d = false;
	
		if (valueToShow1d != null && !valueToShow1d.toUpperCase().equals("NONE")) {
			int positionOfAnd = valueToShow1d.indexOf(" AND ");
			if (positionOfAnd > 0) {
				twoCandleSignals1d = true;
				String firstCandle = valueToShow1d.substring(0, positionOfAnd);
				String secondCandle = valueToShow1d.substring(positionOfAnd + 5);
				if (firstCandle.contains("BULLISH")) {
					bullishCandles1d = new String(firstCandle);
					bearishCandles1d = new String(secondCandle);					
				}					
				else {
					bullishCandles1d = new String(secondCandle);
					bearishCandles1d = new String(firstCandle);					
				}					
			}
			else {
				if (valueToShow1d.contains("BULLISH")) 
					bullishCandles1d = new String(valueToShow1d);
				else
					bearishCandles1d = new String(valueToShow1d);
			}
			
			if (twoCandleSignals1d)
				candlesText1d = valueToShow1d + "\n";
			
			if (valueToShow1d.contains("BULLISH")) {
				//TODO: check for any of explicit patterns found by JForex API
				if (twoCandleSignals1d)
					candlesText1d += "\n" + bullishCandles1d;
				else 
					candlesText1d += "\n" + valueToShow1d;
				
				if (valueToShow1d.contains("BULLISH_1_BAR"))
					candlesText1d += " (bar StDev size: " + mailStringsMap.get("barStat1d") + ")\n";
				else
					candlesText1d += 
							", combined signal StDev size: " + mailStringsMap.get("bullishTriggerCombinedSizeStat1d")
							+ ", lower handle : " + mailStringsMap.get("bullishTriggerCombinedLHPerc1d") + "%"
							+ ", real body : " + mailStringsMap.get("bullishTriggerCombinedBodyPerc1d") + "%, ("
							+ mailStringsMap.get("bullishTriggerCombinedBodyDirection1d")
							+ "), upper handle : " + mailStringsMap.get("bullishTriggerCombinedUHPerc1d") + "%"
							+ "\n";
				
				candlesText1d += "pivot bar low 1d BBands channel position: " 
					+ mailStringsMap.get("bullishCandleTriggerChannelPos1d") + "%"
					+ "\nKeltner channel position (pivot bar): " + mailStringsMap.get("bullishCandleTriggerKChannelPos1d") + "%\n";
				double barHighPerc = (Double)mailValuesMap.get("barHighChannelPos1d").getValue();  				
				if (barHighPerc > 100)
					candlesText1d += "WARNING ! Possible breakout, careful with going long ! Bar high channel position " + mailStringsMap.get("barHighChannelPos1d") + "%\n";  				 
			
		        IBar last1dBar = history.getBar(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, 1);
				double 
					avgHandleSize = tradeTrigger.avgHandleLength(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, last1dBar, FXUtils.YEAR_WORTH_OF_1d_BARS);
				Double 
					barHalf = new Double(last1dBar.getLow() + (last1dBar.getHigh() - last1dBar.getLow()) / 2),
					bullishPivotLevel = (Double)mailValuesMap.get("bullishPivotLevel1d").getValue(),
					aggressiveSL = new Double(bullishPivotLevel.doubleValue() - avgHandleSize),
					riskStandardSTP = new Double((last1dBar.getHigh() - bullishPivotLevel) * Math.pow(10, instrument.getPipScale())),
					riskAggressiveSTP = new Double((last1dBar.getHigh() - aggressiveSL) * Math.pow(10, instrument.getPipScale())),
					riskStandardLMT = new Double((barHalf - bullishPivotLevel) * Math.pow(10, instrument.getPipScale())),
					riskAggressiveLMT = new Double((barHalf - aggressiveSL) * Math.pow(10, instrument.getPipScale()));
				candlesText1d += "\nBUY STP: " + FXUtils.df5.format(last1dBar.getHigh()) 
					+ " (risks " + FXUtils.df1.format(riskStandardSTP.doubleValue()) + "/" + FXUtils.df1.format(riskAggressiveSTP.doubleValue()) 
					+ ")\nBUY LMT: " + FXUtils.df5.format(barHalf.doubleValue())  
					+ " (risks " + FXUtils.df1.format(riskStandardLMT.doubleValue()) + "/" + FXUtils.df1.format(riskAggressiveLMT.doubleValue()) 
					+ ")\nSL: " + mailStringsMap.get("bullishPivotLevel1d")
					+ " (aggressive: " + FXUtils.df5.format(aggressiveSL.doubleValue()) + ")\n";
			
				
			}
			if (valueToShow1d.contains(" AND "))
				candlesText1d += "\n";
			if (valueToShow1d.contains("BEARISH")) {
				if (twoCandleSignals1d)
					candlesText1d += "\n" + bearishCandles1d;
				else 
					candlesText1d += "\n" + valueToShow1d;

				if (valueToShow1d.contains("BEARISH_1_BAR"))
					candlesText1d += " (bar StDev size: " + mailStringsMap.get("barStat1d") + ")\n";
				else
					candlesText1d += 
							", combined signal StDev size: " + mailStringsMap.get("bearishTriggerCombinedSizeStat1d")
							+ ", lower handle : " + mailStringsMap.get("bearishTriggerCombinedLHPerc1d") + "%"
							+ ", real body : " + mailStringsMap.get("bearishTriggerCombinedBodyPerc1d") + "%, ("
							+ mailStringsMap.get("bearishTriggerCombinedBodyDirection1d")
							+ "), upper handle : " + mailStringsMap.get("bearishTriggerCombinedUHPerc1d") + "%"
							+ "\n";
				
				candlesText1d += "pivot bar high BBands channel position: " 
					+ mailStringsMap.get("bearishCandleTriggerChannelPos1d") + "%\n"
					+ "Keltner channel position (pivotBar): " + mailStringsMap.get("bearishCandleTriggerKChannelPos1d") + "%\n";
				double barLowPerc = (Double)mailValuesMap.get("barLowChannelPos1d").getValue();  				
				if (barLowPerc < 0)
					candlesText1d += "WARNING ! Possible breakout, careful with going short ! Bar low channel position " + mailStringsMap.get("barLowChannelPos1d") + "%\n";  				 
			
		        IBar last1dBar = history.getBar(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, 1);
				double 
					avgHandleSize = tradeTrigger.avgHandleLength(instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, last1dBar, FXUtils.YEAR_WORTH_OF_1d_BARS);
				Double 
					barHalf = new Double(last1dBar.getLow() + (last1dBar.getHigh() - last1dBar.getLow()) / 2),
					bearishPivotLevel = (Double)mailValuesMap.get("bearishPivotLevel1d").getValue(),
					aggressiveSL = new Double(bearishPivotLevel.doubleValue() + avgHandleSize),
					riskStandardSTP = new Double((bearishPivotLevel - last1dBar.getLow()) * Math.pow(10, instrument.getPipScale())),
					riskAggressiveSTP = new Double((aggressiveSL - barLow) * Math.pow(10, instrument.getPipScale())),
					riskStandardLMT = new Double((bearishPivotLevel - barHalf) * Math.pow(10, instrument.getPipScale())),
					riskAggressiveLMT = new Double((aggressiveSL - barHalf) * Math.pow(10, instrument.getPipScale()));
				candlesText1d += "\nSELL STP: " + FXUtils.df5.format(last1dBar.getLow()) 
					+ " (risks " + FXUtils.df1.format(riskStandardSTP.doubleValue()) + "/" + FXUtils.df1.format(riskAggressiveSTP.doubleValue()) 
					+ ")\nSELL LMT: " + FXUtils.df5.format(last1dBar.getLow() + (last1dBar.getHigh() - last1dBar.getLow()) / 2)  
					+ " (risks " + FXUtils.df1.format(riskStandardLMT.doubleValue()) + "/" + FXUtils.df1.format(riskAggressiveLMT.doubleValue()) 
					+ ")\nSL: " + mailStringsMap.get("bearishPivotLevel1d") 
					+ " (aggressive: " + FXUtils.df5.format(aggressiveSL.doubleValue()) + ")\n";
			}
		}
		mailBody += candlesText1d + "\n";

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
		
		Double 
			channelTop4h = (Double)mailValuesMap.get("bBandsTop4h").getValue(),
			channelBottom4h = (Double)mailValuesMap.get("bBandsBottom4h").getValue();			
		mailBody += "Last price distance from channel top: " 
			+ FXUtils.df1.format((channelTop4h - bidBar.getClose()) * Math.pow(10, instrument.getPipScale())) + " pips, from channel bottom: "
			+ FXUtils.df1.format((bidBar.getClose() - channelBottom4h) * Math.pow(10, instrument.getPipScale())) + " pips\n";
		
		if (!mailStringsMap.get("barsAboveChannel4h").equals("0"))
			mailBody += "barsAboveChannelTop ! (" + mailStringsMap.get("barsAboveChannel4h") + " bar(s))\n";
		else if (!mailStringsMap.get("barsBelowChannel4h").equals("0"))
			mailBody += "barsBelowChannel ! (" + mailStringsMap.get("barsBelowChannel4h") + " bar(s))\n";
		mailBody += "Channel width: " + mailStringsMap.get("bBandsWidth4h") + " pips\n";
		mailBody += "volatility4h (BBands squeeze): " + mailStringsMap.get("volatility4h") + "%\n";
		
		// 30min
		mailBody += "\n\n-------------------------\n30min TIMEFRAME REGIME:\n-------------------------\n\nTrend direction (strength):\n";
		Double trendStrength30min = (Double)mailValuesMap.get("MAsDistance30min").getValue();
		if (trendStrength30min.doubleValue() < -0.7) 
			mailBody += "FLAT (Strength: " + mailStringsMap.get("MAsDistance30min") + "); TrendID: " + mailStringsMap.get("TrendId30min") + ", ";  			
		else
			mailBody += "TrendId" + ": " + mailStringsMap.get("TrendId30min") + " (" + mailStringsMap.get("MAsDistance30min") + ")\n\n";  
		
		mailBody += "Channel position\nbar high/low: " + mailStringsMap.get("barHighChannelPos30min") + "%/"+ mailStringsMap.get("barLowChannelPos30min") + "%\n";  
		
		mailBody += "bar range: " + FXUtils.df1.format((barHigh.doubleValue() - barLow.doubleValue()) * Math.pow(10, instrument.getPipScale())) + " pips";
		mailBody += " (avg. bar range: " + mailStringsMap.get("bar30minAvgSize") + ")\n";
		mailBody += "bar range vs. average range: " + FXUtils.df1.format((Double)mailValuesMap.get("barStat30min").getValue()) + " StDev(s) from average\n";  	
		double 
			avgHandleSize = tradeTrigger.avgHandleLength(instrument, basicTimeFrame, OfferSide.BID, bidBar, FXUtils.MONTH_WORTH_OF_30min_BARS);
		mailBody += "avg. handle size: " + FXUtils.df1.format(avgHandleSize * Math.pow(10, instrument.getPipScale())) + " pips\n";
		mailBody += "prevBarOverlap: " + mailStringsMap.get("prevBarOverlap30min") + "%\n";
		
		mailBody += lastTwo30MinBarsStats;
		
		Double 
			channelTop30min = (Double)mailValuesMap.get("bBandsTop30min").getValue(),
			channelBottom30min = (Double)mailValuesMap.get("bBandsBottom30min").getValue();			
		mailBody += "\nLast price distance from channel top: " 
			+ FXUtils.df1.format((channelTop30min - bidBar.getClose()) * Math.pow(10, instrument.getPipScale())) + " pips, from channel bottom: "
			+ FXUtils.df1.format((bidBar.getClose() - channelBottom30min) * Math.pow(10, instrument.getPipScale())) + " pips\n";
		mailBody += "Channel width: " + mailStringsMap.get("bBandsWidth30min") + " pips\n\nMomentum:\n";
		
		mailBody += "MACDState: " + mailStringsMap.get("MACDState30min") + " (StDevPos: " + mailStringsMap.get("MACDStDevPos30min") + "), ";  
		mailBody += "MACDHState: " + mailStringsMap.get("MACDHState30min")  + " (StDevPos: " + mailStringsMap.get("MACD_HStDevPos30min") + ")\nStochState: " + mailStringsMap.get("StochState30min"); 
		if (mailStringsMap.get("StochState30min").contains("OVER")) {
			Double stochFast30min = (Double)mailValuesMap.get("StochFast30min").getValue();
			Double stochSlow30min = (Double)mailValuesMap.get("StochSlow30min").getValue();
			if (stochFast30min > stochSlow30min)
				mailBody += " and RAISING (difference " + FXUtils.df1.format(stochFast30min - stochSlow30min) + ")";
			else 
				mailBody += " and FALLING (difference " + FXUtils.df1.format(stochFast30min - stochSlow30min) + ")";
		}
				
		mailBody +=  "\n\nOversold / Overbought:\n";		
		if (mailStringsMap.get("StochState30min").contains("OVER"))
			mailBody += "StochState: " + mailStringsMap.get("StochState30min") + "\n";
		mailBody += "Stochs: " + mailStringsMap.get("StochFast30min") + "/";
		mailBody += mailStringsMap.get("StochSlow30min") + ", ";				
		mailBody += "RSI: " + mailStringsMap.get("RSI30min") + "\n\n";  

		mailBody += "Volatility (all TFs)\nATR30min: " + mailStringsMap.get("ATR30min") + ", + 20%: " + mailStringsMap.get("ATR30min + 20%") + ", ";  
		mailBody += "ATR4h: " + mailStringsMap.get("ATR4h") + ", ATR1d: " + mailStringsMap.get("ATR1d") + "\n\n";  

		mailBody += "volatility30min (BBands squeeze): " + mailStringsMap.get("volatility30min") + " %\n\n";  

		mailBody += "\n\n-------------------------\nmoving averages clusters:\n-------------------------\n\n";
		List<SRLevel> mas = getMAsValues(instrument, bidBar);
		mailBody += showSortedMAsList(instrument, mas, bidBar);
		
		if (srLevels != null && srLevels.size() > 0) {
			mailBody += "\n\n-------------------------\nsupport / resistance levels:\n-------------------------\n\n";
			mailBody += showSortedSRLevels(instrument, new ArrayList<SRLevel>(srLevels), bidBar);
		}
		
		if (trendlinesToCheck != null && trendlinesToCheck.size() > 0) {
			mailBody += "\n\n-------------------------\ncurrent trendline values:\n-------------------------\n\n";
			mailBody += showSortedTrendlineValues(instrument, bidBar);
		}
		
		return mailBody;
	}

	private String get3TFOverview(Instrument instrument, Period pPeriod, IBar bidBar, Map<String, String> mailStringsMap, Map<String, FlexLogEntry> mailValuesMap) {
		String res = new String();
		final String 
			colOneEmpties = new String("              |"),
			colOneLines = new String("--------------|"),
			middleColHeader = new String("                                                                           |"),			
			middleColEmpties = new String("                                      |"),
			middleColLines = new String("--------------------------------------|"),
			horLineSeparator = colOneLines + middleColLines + middleColLines + middleColLines + "\n",
			horEmptySeparator = colOneEmpties + middleColEmpties + middleColEmpties + middleColEmpties + "\n";
		
		int 
			replPos = (middleColHeader.length() - "Timeframe".length()) / 2,
			restPos = replPos +  "Timeframe".length();
		res += colOneEmpties + FXUtils.replaceCentered(middleColHeader, "Timeframe") + "\n";
		// next line
		replPos = (colOneEmpties.length() - "Indicators".length()) / 2;
		restPos = replPos +  "Indicators".length();
		res += colOneEmpties.substring(0, replPos) + "Indicators" + colOneEmpties.substring(restPos) 
				+ middleColLines + middleColLines + middleColLines + "\n";
		// next line
		res += colOneEmpties + FXUtils.replaceCentered(middleColEmpties, "1d");
		res += FXUtils.replaceCentered(middleColEmpties, "4h");
		res += FXUtils.replaceCentered(middleColEmpties, "30min") + "\n"; 
		// next line
		res += colOneLines + middleColLines + middleColLines + middleColLines+ "\n";
		// next line
		res += colOneEmpties + middleColEmpties + middleColEmpties + middleColEmpties + "\n";
		// next line
		replPos = (colOneEmpties.length() - "Trend".length()) / 2;
		restPos = replPos +  "Trend".length();
		res += colOneEmpties.substring(0, replPos) + "Trend" + colOneEmpties.substring(restPos);
		
		boolean extraLine = false;
		if (mailValuesMap.get("MAsDistance1d").getDoubleValue() < -0.7) {
			res += FXUtils.replaceCentered(middleColEmpties, "FLAT (" + mailStringsMap.get("MAsDistance1d") + ")");
			extraLine = true;
		} else {
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("TrendId1d") + " (" + mailStringsMap.get("MAsDistance1d") + ")");						
		}
		if (mailValuesMap.get("MAsDistance4h").getDoubleValue() < -0.7) {
			res += FXUtils.replaceCentered(middleColEmpties, "FLAT (" + mailStringsMap.get("MAsDistance4h") + ")");
			extraLine = true;
		} else {
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("TrendId4h") + " (" + mailStringsMap.get("MAsDistance4h") + ")");						
		}
		if (mailValuesMap.get("MAsDistance30min").getDoubleValue() < -0.7) {
			res += FXUtils.replaceCentered(middleColEmpties, "FLAT (" + mailStringsMap.get("MAsDistance30min") + ")") + "\n";
			extraLine = true;
		} else {
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("TrendId30min") + " (" + mailStringsMap.get("MAsDistance30min") + ")") + "\n";						
		}
		if (extraLine) {
			res += colOneEmpties;
			if (mailValuesMap.get("MAsDistance1d").getDoubleValue() < -0.7) {
				res += FXUtils.replaceCentered(middleColEmpties, "(" + mailStringsMap.get("TrendId1d") + ")");						
			} else {
				res += middleColEmpties;
			}
			if (mailValuesMap.get("MAsDistance4h").getDoubleValue() < -0.7) {
				res += FXUtils.replaceCentered(middleColEmpties, "(" + mailStringsMap.get("TrendId4h") + ")");						
			} else {
				res += middleColEmpties;
			}
			if (mailValuesMap.get("MAsDistance30min").getDoubleValue() < -0.7) {
				res += FXUtils.replaceCentered(middleColEmpties, "(" + mailStringsMap.get("TrendId30min") + ")") + "\n";						
			} else {
				res += middleColEmpties + "\n";
			}
		} else {
			res += horEmptySeparator;
		}
		res += horLineSeparator + horEmptySeparator;
		extraLine = false;
		
		// next section and line
		res += FXUtils.replaceCentered(colOneEmpties, "Momentum");
		res += FXUtils.replaceCentered(middleColEmpties, momentumCount(mailStringsMap, mailValuesMap, "1d"));
		res += FXUtils.replaceCentered(middleColEmpties, momentumCount(mailStringsMap, mailValuesMap, "4h"));
		res += FXUtils.replaceCentered(middleColEmpties, momentumCount(mailStringsMap, mailValuesMap, "30min")) + "\n";
		// next line
		res += FXUtils.replaceCentered(colOneEmpties, "MACD");
		res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("MACDState1d"));
		res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("MACDState4h"));
		res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("MACDState30min")) + "\n";
		if (mailStringsMap.get("MACDCross1d").equals("BULL_CROSS_BELOW_ZERO") || mailStringsMap.get("MACDCross1d").equals("BEAR_CROSS_ABOVE_ZERO")
			|| mailStringsMap.get("MACDCross4h").equals("BULL_CROSS_BELOW_ZERO") || mailStringsMap.get("MACDCross4h").equals("BEAR_CROSS_ABOVE_ZERO")
			|| mailStringsMap.get("MACDCross30min").equals("BULL_CROSS_BELOW_ZERO") || mailStringsMap.get("MACDCross30min").equals("BEAR_CROSS_ABOVE_ZERO")) {
			res += colOneEmpties;
			if (mailStringsMap.get("MACDCross1d").equals("BULL_CROSS_BELOW_ZERO") || mailStringsMap.get("MACDCross1d").equals("BEAR_CROSS_ABOVE_ZERO"))
				res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("MACDCross1d"));
			else
				res += middleColEmpties;
			if (mailStringsMap.get("MACDCross4h").equals("BULL_CROSS_BELOW_ZERO") || mailStringsMap.get("MACDCross4h").equals("BEAR_CROSS_ABOVE_ZERO"))
				res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("MACDCross4h"));
			else
				res += middleColEmpties;
			if (mailStringsMap.get("MACDCross30min").equals("BULL_CROSS_BELOW_ZERO") || mailStringsMap.get("MACDCross30min").equals("BEAR_CROSS_ABOVE_ZERO"))
				res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("MACDCross30min")) + "\n";
			else
				res += middleColEmpties + "\n";
		}
		// next line
		res += FXUtils.replaceCentered(colOneEmpties, "MACD-H");
		res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("MACDHState1d"));
		res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("MACDHState4h"));
		res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("MACDHState30min")) + "\n";
		// next line
		res += FXUtils.replaceCentered(colOneEmpties, "StochState");
		res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("StochState1d"));
		res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("StochState4h"));
		res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("StochState30min")) + "\n";
		// next line
		res += FXUtils.replaceCentered(colOneEmpties, "StochsDiff");
		if (mailStringsMap.get("stochsCross1d").contains("CROSS_FROM"))
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("stochsCross1d") + ", " + FXUtils.df1.format(mailValuesMap.get("StochFast1d").getDoubleValue() - mailValuesMap.get("StochSlow1d").getDoubleValue()));
		else
			res += FXUtils.replaceCentered(middleColEmpties, FXUtils.df1.format(mailValuesMap.get("StochFast1d").getDoubleValue() - mailValuesMap.get("StochSlow1d").getDoubleValue()));
		if (mailStringsMap.get("stochsCross4h").contains("CROSS_FROM"))
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("stochsCross4h") + ", " + FXUtils.df1.format(mailValuesMap.get("StochFast4h").getDoubleValue() - mailValuesMap.get("StochSlow4h").getDoubleValue()));
		else
			res += FXUtils.replaceCentered(middleColEmpties, FXUtils.df1.format(mailValuesMap.get("StochFast4h").getDoubleValue() - mailValuesMap.get("StochSlow4h").getDoubleValue()));
		if (mailStringsMap.get("stochsCross30min").contains("CROSS_FROM"))
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("stochsCross30min") + ", " + FXUtils.df1.format(mailValuesMap.get("StochFast30min").getDoubleValue() - mailValuesMap.get("StochSlow30min").getDoubleValue())) + "\n";		
		else
			res += FXUtils.replaceCentered(middleColEmpties, FXUtils.df1.format(mailValuesMap.get("StochFast30min").getDoubleValue() - mailValuesMap.get("StochSlow30min").getDoubleValue())) + "\n";		
		res += horEmptySeparator + horLineSeparator + horEmptySeparator;

		// next section and line
		res += FXUtils.replaceCentered(colOneEmpties, "Channel pos.");
		res += FXUtils.replaceCentered(middleColEmpties, channelPosDesc(mailStringsMap, mailValuesMap, "1d"));
		res += FXUtils.replaceCentered(middleColEmpties, channelPosDesc(mailStringsMap, mailValuesMap, "4h"));
		res += FXUtils.replaceCentered(middleColEmpties, channelPosDesc(mailStringsMap, mailValuesMap, "30min")) + "\n";
		if (mailValuesMap.get("barsAboveChannel30min").getIntegerValue() > 0 || mailValuesMap.get("barsBelowChannel30min").getIntegerValue() > 0
			|| mailValuesMap.get("barsAboveChannel4h").getIntegerValue() > 0 || mailValuesMap.get("barsBelowChannel4h").getIntegerValue() > 0
			|| mailValuesMap.get("barsAboveChannel1d").getIntegerValue() > 0 || mailValuesMap.get("barsBelowChannel1d").getIntegerValue() > 0) {
			res += colOneEmpties;
			if (mailValuesMap.get("barsAboveChannel1d").getIntegerValue() > 0) {
				res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("barsAboveChannel1d") + " bars ABOVE channel !");				
			}
			else if (mailValuesMap.get("barsBelowChannel1d").getIntegerValue() > 0) {
				res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("barsBelowChannel1d") + " bars BELOW channel !");								
			}
			else 
				res += middleColEmpties;
			
			if (mailValuesMap.get("barsAboveChannel4h").getIntegerValue() > 0) {
				res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("barsAboveChannel4h") + " bars ABOVE channel !");				
			}
			else if (mailValuesMap.get("barsBelowChannel4h").getIntegerValue() > 0) {
				res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("barsBelowChannel4h") + " bars BELOW channel !");								
			}
			else 
				res += middleColEmpties;
			
			if (mailValuesMap.get("barsAboveChannel30min").getIntegerValue() > 0) {
				res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("barsAboveChannel30min") + " bars ABOVE channel !");				
			}
			else if (mailValuesMap.get("barsBelowChannel30min").getIntegerValue() > 0) {
				res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("barsBelowChannel30min") + " bars BELOW channel !");								
			}
			else 
				res += middleColEmpties;
			
			res += "\n";
		}			
		res += horEmptySeparator + horLineSeparator;

		// next section and line
		res += FXUtils.replaceCentered(colOneEmpties, "OS / OB") + middleColEmpties + middleColEmpties + middleColEmpties + "\n";
		res += FXUtils.replaceCentered(colOneEmpties, "RSI");
		if (mailValuesMap.get("RSI1d").getDoubleValue() > 68.0)
			res += FXUtils.replaceCentered(middleColEmpties, "OVERBOUGHT ! " + mailStringsMap.get("RSI1d"));
		else if (mailValuesMap.get("RSI1d").getDoubleValue() < 32.0)
			res += FXUtils.replaceCentered(middleColEmpties, "OVERSOLD ! " + mailStringsMap.get("RSI1d"));
		else 
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("RSI1d"));
		
		if (mailValuesMap.get("RSI4h").getDoubleValue() > 68.0)
			res += FXUtils.replaceCentered(middleColEmpties, "OVERBOUGHT ! " + mailStringsMap.get("RSI4h"));
		else if (mailValuesMap.get("RSI4h").getDoubleValue() < 32.0)
			res += FXUtils.replaceCentered(middleColEmpties, "OVERSOLD ! " + mailStringsMap.get("RSI4h"));
		else 
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("RSI4h"));
		
		if (mailValuesMap.get("RSI30min").getDoubleValue() > 68.0)
			res += FXUtils.replaceCentered(middleColEmpties, "OVERBOUGHT ! " + mailStringsMap.get("RSI30min")) + "\n";
		else if (mailValuesMap.get("RSI30min").getDoubleValue() < 32.0)
			res += FXUtils.replaceCentered(middleColEmpties, "OVERSOLD ! " + mailStringsMap.get("RSI30min")) + "\n";
		else 
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("RSI30min")) + "\n";
		res += colOneEmpties 
				+ FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("RSIState1d"))
				+ FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("RSIState4h"))
				+ FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("RSIState30min")) + "\n";

		// next line
		res += FXUtils.replaceCentered(colOneEmpties, "Stoch");
		String stochState = mailStringsMap.get("StochState1d");
		if (stochState.equals("OVERBOUGHT_BOTH"))
			res += FXUtils.replaceCentered(middleColEmpties, "OVERBOUGHT ! (" + mailStringsMap.get("StochFast1d") + "/" + mailStringsMap.get("StochSlow1d") + ")");
		else if (stochState.equals("OVERBSOLD_BOTH"))
			res += FXUtils.replaceCentered(middleColEmpties, "OVERSOLD ! (" + mailStringsMap.get("StochFast1d") + "/" + mailStringsMap.get("StochSlow1d") + ")");
		else 
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("StochFast1d") + "/" + mailStringsMap.get("StochSlow1d"));
		stochState = mailStringsMap.get("StochState4h");
		if (stochState.equals("OVERBOUGHT_BOTH"))
			res += FXUtils.replaceCentered(middleColEmpties, "OVERBOUGHT ! (" + mailStringsMap.get("StochFast4h") + "/" + mailStringsMap.get("StochSlow4h") + ")");
		else if (stochState.equals("OVERBSOLD_BOTH"))
			res += FXUtils.replaceCentered(middleColEmpties, "OVERSOLD ! (" + mailStringsMap.get("StochFast4h") + "/" + mailStringsMap.get("StochSlow4h") + ")");
		else 
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("StochFast4h") + "/" + mailStringsMap.get("StochSlow4h"));
		stochState = mailStringsMap.get("StochState30min");
		if (stochState.equals("OVERBOUGHT_BOTH"))
			res += FXUtils.replaceCentered(middleColEmpties, "OVERBOUGHT ! (" + mailStringsMap.get("StochFast30min") + "/" + mailStringsMap.get("StochSlow30min") + ")") + "\n";
		else if (stochState.equals("OVERBSOLD_BOTH"))
			res += FXUtils.replaceCentered(middleColEmpties, "OVERSOLD ! (" + mailStringsMap.get("StochFast30min") + "/" + mailStringsMap.get("StochSlow30min") + ")") + "\n";
		else 
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("StochFast30min") + "/" + mailStringsMap.get("StochSlow30min")) + "\n";
		
		// next line
		res += FXUtils.replaceCentered(colOneEmpties, "CCI");
		if (mailValuesMap.get("CCI1d").getDoubleValue() > 190)
			res += FXUtils.replaceCentered(middleColEmpties, "OVERBOUGHT ! " + mailStringsMap.get("CCI1d")); 
		else if (mailValuesMap.get("CCI1d").getDoubleValue() < -190)
			res += FXUtils.replaceCentered(middleColEmpties, "OVERSOLD ! " + mailStringsMap.get("CCI1d"));
		else
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("CCI1d")); 

		if (mailValuesMap.get("CCI4h").getDoubleValue() > 190)
			res += FXUtils.replaceCentered(middleColEmpties, "OVERBOUGHT ! " + mailStringsMap.get("CCI4h")); 
		else if (mailValuesMap.get("CCI4h").getDoubleValue() < -190)
			res += FXUtils.replaceCentered(middleColEmpties, "OVERSOLD ! " + mailStringsMap.get("CCI4h"));
		else
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("CCI4h"));

		if (mailValuesMap.get("CCI30min").getDoubleValue() > 190)
			res += FXUtils.replaceCentered(middleColEmpties, "OVERBOUGHT ! " + mailStringsMap.get("CCI30min")); 
		else if (mailValuesMap.get("CCI30min").getDoubleValue() < -190)
			res += FXUtils.replaceCentered(middleColEmpties, "OVERSOLD ! " + mailStringsMap.get("CCI30min"));
		else
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("CCI30min"));		
		res += "\n";
		res += colOneEmpties 
			+ FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("CCIState1d"))
			+ FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("CCIState4h"))
			+ FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("CCIState30min")) + "\n";
		res +=  horEmptySeparator + horLineSeparator + horEmptySeparator;

		// next section and next line			
		res += FXUtils.replaceCentered(colOneEmpties, "Candles");
		extraLine = false;
		boolean 
			twoSignals1d = false,
			twoSignals4h = false,
			twoSignals30min = false,
			bullish1d = false,
			bearish1d = false,
			bullish4h = false,
			bearish4h = false,
			bullish30min = false,
			bearish30min = false;
		String 
			candleTrigger = mailStringsMap.get("CandleTrigger1d"),
			candleText = null;
		if (candleTrigger != null && candleTrigger.length() > 0 && !candleTrigger.equals("none") && !candleTrigger.equals("n/a")) {
			if (candleTrigger.contains(" AND ")) {
				twoSignals1d = true;
				candleText = candleTrigger.substring(0, candleTrigger.indexOf(" AND "));
			}
			else 
				candleText = candleTrigger;
			
			bullish1d = candleTrigger.contains("BULLISH");
			bearish1d = candleTrigger.contains("BEARISH");
			res += FXUtils.replaceCentered(middleColEmpties, candleText);
			extraLine = true;
		} else {
			res += FXUtils.replaceCentered(middleColEmpties, "(none)");			
		}
		candleTrigger = mailStringsMap.get("CandleTrigger4h");
		if (candleTrigger != null && candleTrigger.length() > 0 && !candleTrigger.equals("none") && !candleTrigger.equals("n/a")) {
			if (candleTrigger.contains(" AND ")) {
				twoSignals4h = true;
				candleText = candleTrigger.substring(0, candleTrigger.indexOf(" AND "));
			}
			else 
				candleText = candleTrigger;
			
			bullish4h = candleTrigger.contains("BULLISH");
			bearish4h = candleTrigger.contains("BEARISH");
			res += FXUtils.replaceCentered(middleColEmpties, candleText);
			extraLine = true;
		} else {
			res += FXUtils.replaceCentered(middleColEmpties, "(none)");			
		}
		candleTrigger = mailStringsMap.get("CandleTrigger30min");
		if (candleTrigger != null && candleTrigger.length() > 0 && !candleTrigger.equals("none") && !candleTrigger.equals("n/a")) {
			if (candleTrigger.contains(" AND ")) {
				twoSignals30min = true;
				candleText = candleTrigger.substring(0, candleTrigger.indexOf(" AND "));
			}
			else 
				candleText = candleTrigger;

			bullish30min = candleTrigger.contains("BULLISH");
			bearish30min = candleTrigger.contains("BEARISH");
			res += FXUtils.replaceCentered(middleColEmpties, candleText) + "\n";
			extraLine = true;
		} else {
			res += FXUtils.replaceCentered(middleColEmpties, "(none)") + "\n";			
		}
		if (extraLine) {
			res += colOneEmpties;
			if (bullish1d || bearish1d) {
				if (bullish1d) {
					res += FXUtils.replaceCentered(middleColEmpties, "(" + mailStringsMap.get("bullishCandleTriggerChannelPos1d") + "%)");
				}
				else {
					res += FXUtils.replaceCentered(middleColEmpties, "(" + mailStringsMap.get("bearishCandleTriggerChannelPos1d") + "%)");					
				}
			} else {
				res += middleColEmpties;
			}
			if (bullish4h || bearish4h) {
				if (bullish4h) {
					res += FXUtils.replaceCentered(middleColEmpties, "(1d: " 
							+ mailStringsMap.get("bullishPivotLevelHigherTFChannelPos4h") + "%/" 
							+ mailStringsMap.get("bullishCandleTriggerChannelPos4h") + "%)");
				}
				else {
					res += FXUtils.replaceCentered(middleColEmpties, "(1d: " 
							+ mailStringsMap.get("bearishPivotLevelHigherTFChannelPos4h") + "%/" 
							+ mailStringsMap.get("bearishCandleTriggerChannelPos4h") + "%)");
				}
			} else {
				res += middleColEmpties;
			}
			if (bullish30min || bearish30min) {
				if (bullish30min) {
					res += FXUtils.replaceCentered(middleColEmpties, "(4h: " 
							+ mailStringsMap.get("bullishPivotLevelHigherTFChannelPos30min") + "%/" 
							+ mailStringsMap.get("bullishCandleTriggerChannelPos30min")	+ "%)") + "\n";
				}
				else {
					res += FXUtils.replaceCentered(middleColEmpties, "(4h: " 
							+ mailStringsMap.get("bearishPivotLevelHigherTFChannelPos30min") + "%/" 
							+ mailStringsMap.get("bearishCandleTriggerChannelPos30min") + "%)") + "\n";
				}
			} else {
				res += middleColEmpties + "\n";
			}
		}
		extraLine = false;
		if (twoSignals1d || twoSignals4h || twoSignals30min) {			
			// first line of 2nd candle block
			res += colOneEmpties;
			if (twoSignals1d) {
				res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("CandleTrigger1d").substring(mailStringsMap.get("CandleTrigger1d").indexOf(" AND ") + 5));
			}
			else 
				res += middleColEmpties;
			if (twoSignals4h) {
				res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("CandleTrigger4h").substring(mailStringsMap.get("CandleTrigger4h").indexOf(" AND ") + 5));
			}
			else 
				res += middleColEmpties;
			if (twoSignals30min) {
				res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("CandleTrigger30min").substring(mailStringsMap.get("CandleTrigger30min").indexOf(" AND ") + 5)) + "\n";
			}
			else 
				res += middleColEmpties + "\n";
			// second line of 2nd candle block
			res += colOneEmpties;
			if (twoSignals1d) {
				res += FXUtils.replaceCentered(middleColEmpties, "(" + mailStringsMap.get("bearishCandleTriggerChannelPos1d") + ")");					
			}
			else 
				res += middleColEmpties;
			if (twoSignals4h) {
				res += FXUtils.replaceCentered(middleColEmpties, "(1d: " 
						+ mailStringsMap.get("bearishPivotLevelHigherTFChannelPos4h") + "%/" 
						+ mailStringsMap.get("bearishCandleTriggerChannelPos4h") + "%)");
			}
			else 
				res += middleColEmpties;
			if (twoSignals30min) {
				res += FXUtils.replaceCentered(middleColEmpties, "(4h: " 
						+ mailStringsMap.get("bearishPivotLevelHigherTFChannelPos30min") + "%/" 
						+ mailStringsMap.get("bearishCandleTriggerChannelPos30min") + "%)") + "\n";
			}
			else 
				res += middleColEmpties + "\n";
		}
		res +=  horEmptySeparator + horLineSeparator + horEmptySeparator;
		
		// new line
		res += FXUtils.replaceCentered(colOneEmpties, "Volatility");
		if (mailValuesMap.get("volatility1d").getDoubleValue() > 170.0)
			res += FXUtils.replaceCentered(middleColEmpties, "HIGH ! (" + mailStringsMap.get("volatility1d") + "%)");
		else if (mailValuesMap.get("volatility1d").getDoubleValue() < 70.0)
			res += FXUtils.replaceCentered(middleColEmpties, "LOW ! (" + mailStringsMap.get("volatility1d") + "%)");
		else
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("volatility1d") + "%");
		
		if (mailValuesMap.get("volatility4h").getDoubleValue() > 170.0)
			res += FXUtils.replaceCentered(middleColEmpties, "HIGH ! (" + mailStringsMap.get("volatility4h") + "%)");
		else if (mailValuesMap.get("volatility4h").getDoubleValue() < 70.0)
			res += FXUtils.replaceCentered(middleColEmpties, "LOW ! (" + mailStringsMap.get("volatility4h") + "%)");
		else
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("volatility4h") + "%");
		
		if (mailValuesMap.get("volatility30min").getDoubleValue() > 170.0)
			res += FXUtils.replaceCentered(middleColEmpties, "HIGH ! (" + mailStringsMap.get("volatility30min") + "%)") + "\n";
		else if (mailValuesMap.get("volatility30min").getDoubleValue() < 70.0)
			res += FXUtils.replaceCentered(middleColEmpties, "LOW ! (" + mailStringsMap.get("volatility30min") + "%)") + "\n";
		else
			res += FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("volatility30min") + "%") + "\n";

		// new line
		res += FXUtils.replaceCentered(colOneEmpties, "ATR")
				+ FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("ATR1d") + " pips")
				+ FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("ATR4h") + " pips")
				+ FXUtils.replaceCentered(middleColEmpties, mailStringsMap.get("ATR30min") + ", +20% " + mailStringsMap.get("ATR30min + 20%") + " pips") + "\n";
		res +=  horEmptySeparator + horLineSeparator;
		
		return res;
	}

	private String channelPosDesc(Map<String, String> mailStringsMap, Map<String, FlexLogEntry> mailValuesMap, String timeFrameID) {
		String res = new String();
		
		if (mailValuesMap.get("barHighChannelPos" + timeFrameID).getDoubleValue() > 90.0)
			res += "HIGH (" + mailStringsMap.get("barHighChannelPos" + timeFrameID) + "%/" + mailStringsMap.get("barLowChannelPos" + timeFrameID) + "%)";
		else if (mailValuesMap.get("barLowChannelPos" + timeFrameID).getDoubleValue() < 10.0)
			res += "LOW (" + mailStringsMap.get("barHighChannelPos" + timeFrameID) + "%/" + mailStringsMap.get("barLowChannelPos" + timeFrameID) + "%)";
		else
			res += mailStringsMap.get("barHighChannelPos" + timeFrameID) + "%/" + mailStringsMap.get("barLowChannelPos" + timeFrameID) + "%";
		
		return res;
	}

	private String momentumCount(Map<String, String> mailStringsMap, Map<String, FlexLogEntry> mailValuesMap, String timeFrameID) {
		int 
			upCount = 0,
			downCount = 0;
		String currMomentum = mailStringsMap.get("MACDState" + timeFrameID);
		if (currMomentum != null && currMomentum.length() > 0) {
			if (currMomentum.contains("RAISING") || currMomentum.contains("TICKED_UP"))
				upCount++;
			else
				downCount++;
		}
		currMomentum = mailStringsMap.get("MACDHState" + timeFrameID);
		if (currMomentum != null && currMomentum.length() > 0) {
			if (currMomentum.contains("RAISING") || currMomentum.contains("TICKED_UP"))
				upCount++;
			else
				downCount++;
		}
		double 
			StochFast = mailValuesMap.get("StochFast" + timeFrameID).getDoubleValue(),
			StochSlow = mailValuesMap.get("StochSlow" + timeFrameID).getDoubleValue();
		if (StochFast > StochSlow)
			upCount++;
		else
			downCount++;
		
		String 
			MACDState = mailStringsMap.get("MACDState" + timeFrameID),
			suffix = null;
		if (upCount > downCount) {
			if (MACDState.equals("RAISING_BOTH_BELOW_0"))
				suffix = new String("a");
			else if (MACDState.equals("RAISING_FAST_ABOVE_0"))
				suffix = new String("b");
			else if (MACDState.equals("RAISING_BOTH_ABOVE_0"))
				suffix = new String("c");
			else
				suffix = new String("");
			return new String((upCount == 3 ? "ALL UP ! " : "UP ") + upCount + ":" + downCount + suffix);
		}
		else {
			if (MACDState.equals("FALLING_BOTH_ABOVE_0"))
				suffix = new String("a");
			else if (MACDState.equals("FALLING_FAST_BELOW_0"))
				suffix = new String("b");
			else if (MACDState.equals("FALLING_BOTH_BELOW_0"))
				suffix = new String("c");
			else
				suffix = new String("");
			return new String((downCount == 3 ? "ALL DOWN ! " : "DOWN ") + downCount + ":" + upCount + suffix);
		}
	}

	@Override
	public void onMessage(IMessage message) throws JFException { }

	@Override
	public void onAccount(IAccount account) throws JFException { }

	@Override
	public void onStop() throws JFException {
//		ib.disconnect();
		log.close();
	}

	@Override
	protected String getStrategyName() {
		return "TwoTFStatsCollector";
	}

	@Override
	protected String getReportFileName() {
		return "TwoTFStatsCollector_";
	}

	protected String createMailBody5min(Instrument instrument, Period pPeriod, IBar bidBar) throws JFException {
		// check whether any TP target hit at all
		
		String mailBody = checkTakeProfitLevels(instrument, pPeriod, bidBar);
		if (mailBody.length() > 0) {
			mailBody = instrument.toString() + ": " + mailBody + " (within bar starting at " 
				+ FXUtils.getFormatedTimeCET(bidBar.getTime()) 
				+ " CET (checking in time frame: " + pPeriod.toString() 
				+ ")\n\n";
			
			if (nextSetups.containsKey(instrument.toString())) {
				mailBody += "Next recommended setup: " + nextSetups.get(instrument.toString()) + "\n\n";
			}
		}

		return mailBody;
	}
}
