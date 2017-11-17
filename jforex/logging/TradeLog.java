package jforex.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jforex.techanalysis.source.FlexTASource;
import jforex.utils.FXUtils;
import jforex.utils.log.FlexLogEntry;
import jforex.utils.log.Logger;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;

public class TradeLog {
	public String orderLabel;
	public boolean isLong;
	public long signalTime, fillTime, maxProfitTime, maxDDTime, exitTime, duration;
	public double entryPrice, fillPrice, SL, initialRisk, maxRisk, maxLoss,
			maxLossATR, maxDD, maxDDATR, maxProfit, maxProfitPrice, PnL;

	public String exitReason = null;

	protected List<FlexLogEntry> entryData = new ArrayList<FlexLogEntry>();
	public String setup;
	private double PnLPerc;

	public TradeLog(String pOrderLabel, boolean pIsLong, String setup, long pSignalTime, double pEntryPrice, double pSL, double pInitialRisk) {
		orderLabel = pOrderLabel;
		isLong = pIsLong;
		signalTime = pSignalTime;
		entryPrice = pEntryPrice;
		fillPrice = entryPrice; // needed for risk calc of unfilled orders, which can get new SL while waiting due to changes in the clould borders !
		SL = pSL;
		initialRisk = pInitialRisk;
		maxRisk = initialRisk;
		this.setup = setup;

		exitReason = null;
		PnL = 0.0;
		maxLossATR = maxLoss = 0.0;
		maxDD = maxDDATR = 0.0;
		maxProfit = 0.0;
		maxProfitPrice = 0.0;
		maxProfitTime = maxDDTime = 0;
		duration = 0;
	}

	public void setOrder(IOrder order) {
		orderLabel = order.getLabel();
		isLong = order.isLong();
		entryPrice = order.getOpenPrice();
		fillPrice = entryPrice; // needed for risk calc of unfilled orders, which can get new SL while waiting due to changes in the clould borders !
		SL = order.getStopLossPrice();
		initialRisk = order.getStopLossPrice() != 0 ? Math.abs(order.getOpenPrice() - order.getStopLossPrice()) : 0.0;
	}

	public double missedProfit(Instrument instrument) {
		// PnL taken from IOrder and already in pips
		return maxProfit * Math.pow(10, instrument.getPipScale()) - PnL;
	}

	public double missedProfitPerc(Instrument instrument) {
		// PnL taken from IOrder and already in pips
		return PnL > 0.0 ? 100 * missedProfit(instrument)
				/ (maxProfit * Math.pow(10, instrument.getPipScale())) : 0.0;
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
			if (bidBar.getClose() > slowLine && bidBar.getLow() >= fillPrice
					&& maxProfitPrice - bidBar.getLow() > maxDD) {
				maxDD = maxProfitPrice - bidBar.getLow();
				maxDDTime = bidBar.getTime();
				maxDDATR = maxDD / ATR;
			}
		} else {
			if (bidBar.getClose() < slowLine && bidBar.getHigh() <= fillPrice
					&& bidBar.getHigh() - maxProfitPrice > maxDD) {
				maxDD = bidBar.getHigh() - maxProfitPrice;
				maxDDTime = bidBar.getTime();
				maxDDATR = maxDD / ATR;
			}
		}
	}

	public void updateMaxProfit(IBar bidBar) {
		duration++;
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

	public void addLogEntry(FlexLogEntry e) {
		entryData.add(e);
	}

	public void exitReport(Instrument instrument, Logger log) {
		log.print(prepareExitReport(instrument));

	}

	public String prepareExitReport(Instrument instrument) {
		String result = new String("ER;"
						+ instrument.name() + ";"		
						+ orderLabel + ";"
						+ (isLong ? "LONG" : "SHORT") + ";"
						+ setup + ";"
						+ FXUtils.getFormatedTimeGMT(signalTime) + ";"
						+ FXUtils.getFormatedTimeGMT(fillTime) + ";"
						+ FXUtils.getFormatedTimeGMT(exitTime) + ";"
						+ exitReason + ";"
						+ duration + ";"
						+ (PnL > 0 ? "WIN" : "LOSS") + ";"
						+ FXUtils.df1.format(PnL) + ";"
						+ FXUtils.df2.format(PnLPerc) + ";"
						+ FXUtils.df1.format(maxProfit * Math.pow(10, instrument.getPipScale())) + ";"
						+ FXUtils.df2.format((isLong ? maxProfitPrice - entryPrice : entryPrice - maxProfitPrice) / entryPrice * 100) + ";"
						+ FXUtils.df1.format(missedProfit(instrument)) + ";"
						+ FXUtils.df1.format(missedProfitPerc(instrument)) + ";"
						+ (instrument.getPipScale() != 2 ? FXUtils.df5.format(maxProfitPrice) : FXUtils.df2.format(maxProfitPrice)) + ";"						
						+ FXUtils.df1.format(maxLoss * Math.pow(10, instrument.getPipScale()))	+ ";"
						+ FXUtils.df2.format(maxLossATR) + ";"
						+ (instrument.getPipScale() == 2 ? FXUtils.df2.format(entryPrice) : FXUtils.df5.format(entryPrice)) + ";"
						+ (instrument.getPipScale() == 2 ? FXUtils.df2.format(fillPrice) : FXUtils.df5.format(fillPrice)) + ";"
						+ (instrument.getPipScale() == 2 ? FXUtils.df2.format(SL) : FXUtils.df5.format(SL))	+ ";"
						+ FXUtils.df1.format(initialRisk * Math.pow(10, instrument.getPipScale()))	+ ";"
						+ FXUtils.df1.format(maxRisk * Math.pow(10, instrument.getPipScale())) + ";"
						+ FXUtils.df1.format(maxDD	* Math.pow(10, instrument.getPipScale())) + ";"
						+ FXUtils.df2.format(maxDDATR)	+ ";"
						+ FXUtils.getFormatedTimeGMT(maxDDTime));
		for (FlexLogEntry e : entryData)
			result += ";" + e.getFormattedValue();
		return result;

	}
	
	public List<FlexLogEntry> prepareExitReportAsList(Instrument instrument) {
		List<FlexLogEntry> exitReport = new ArrayList<FlexLogEntry>();
		exitReport.add(new FlexLogEntry("ER", "ER"));
		exitReport.add(new FlexLogEntry("Instrument", instrument.name()));
		exitReport.add(new FlexLogEntry("OrderLabel", orderLabel));
		exitReport.add(new FlexLogEntry("Direction", (isLong ? "LONG" : "SHORT")));
		exitReport.add(new FlexLogEntry("Setup", setup));
		exitReport.add(new FlexLogEntry("signalTime", FXUtils.getFormatedTimeGMT(signalTime)));
		exitReport.add(new FlexLogEntry("fillTime", FXUtils.getFormatedTimeGMT(fillTime)));
		exitReport.add(new FlexLogEntry("fillTime", FXUtils.getFormatedTimeGMT(exitTime)));
		exitReport.add(new FlexLogEntry("exitReason", exitReason));
		exitReport.add(new FlexLogEntry("duration", new Long(duration)));
		exitReport.add(new FlexLogEntry("Direction", (PnL > 0 ? "WIN" : "LOSS")));
		exitReport.add(new FlexLogEntry("PnL", new Double(PnL), FXUtils.df1));
		exitReport.add(new FlexLogEntry("PnLPerc", new Double(PnLPerc), FXUtils.df2));
		exitReport.add(new FlexLogEntry("maxProfit", new Double(maxProfit * Math.pow(10, instrument.getPipScale())), FXUtils.df1));
		exitReport.add(new FlexLogEntry("maxProfit2", new Double((isLong ? maxProfitPrice - entryPrice : entryPrice - maxProfitPrice) / entryPrice * 100), FXUtils.df2));
		exitReport.add(new FlexLogEntry("missedProfit", new Double(missedProfit(instrument)), FXUtils.df1));
		exitReport.add(new FlexLogEntry("missedProfitPerc", new Double(missedProfitPerc(instrument)), FXUtils.df1));
		exitReport.add(new FlexLogEntry("maxProfitPrice", new Double(maxProfitPrice), (instrument.getPipScale() != 2 ? FXUtils.df5 : FXUtils.df2)));
		exitReport.add(new FlexLogEntry("maxLoss", new Double(maxLoss * Math.pow(10, instrument.getPipScale())), FXUtils.df1));
		exitReport.add(new FlexLogEntry("maxLossATR", new Double(maxLossATR), FXUtils.df2));
		exitReport.add(new FlexLogEntry("entryPrice", new Double(entryPrice), (instrument.getPipScale() != 2 ? FXUtils.df5 : FXUtils.df2)));
		exitReport.add(new FlexLogEntry("entryPrice", new Double(fillPrice), (instrument.getPipScale() != 2 ? FXUtils.df5 : FXUtils.df2)));
		exitReport.add(new FlexLogEntry("entryPrice", new Double(SL), (instrument.getPipScale() != 2 ? FXUtils.df5 : FXUtils.df2)));
		exitReport.add(new FlexLogEntry("initialRisk", new Double(initialRisk * Math.pow(10, instrument.getPipScale())), FXUtils.df1));
		exitReport.add(new FlexLogEntry("maxRisk", new Double(maxRisk * Math.pow(10, instrument.getPipScale())), FXUtils.df1));
		exitReport.add(new FlexLogEntry("maxDD", new Double(maxDD	* Math.pow(10, instrument.getPipScale())), FXUtils.df1));
		exitReport.add(new FlexLogEntry("maxDD", new Double(maxDDATR), FXUtils.df2));
		exitReport.add(new FlexLogEntry("signalTime", FXUtils.getFormatedTimeGMT(maxDDTime)));

		for (FlexLogEntry e : entryData)
			exitReport.add(e);
		return exitReport;

	}
	
	public String prepareHeader() {
		String result = new String("Header;"
						+ "Instrument;"
						+ "OrderID;"
						+ "Direction;"
						+ "Setup;"
						+ "signalTime;"
						+ "fillTime;"
						+ "exitTime;"
						+ "exitReason;"
						+ "duration (bars);"
						+ "WinOrLoss;"
						+ "PnL;"
						+ "PnLPerc;"
						+ "maxProfit;"
						+ "maxProfitPerc;"
						+ "missedProfit;"
						+ "missedProfitPerc;"
						+ "maxProfitPrice;"
						+ "maxLoss;"
						+ "maxLossATR;"
						+ "entryPrice;"
						+ "fillPrice;"
						+ "SL;"
						+ "initialRisk;"
						+ "maxRisk;"
						+ "maxDD;"
						+ "maxDDATR;"
						+ "maxDDTime");
		for (FlexLogEntry e : entryData)
			result += ";" + e.getHeaderLabel();
		return result;

	}
	
	public void addTAData(Map<String, FlexLogEntry> taValues) {
		//TODO: promeniti u eksplicitni REDOSLED tako da bude citljivije. Paziti na EXIT values !!
/*		
 * 		TA_SITUATION = "TASituationDescription",
		TREND_ID = "TrendID",
		MAs_DISTANCE_PERC = "MAs distance percentile",
		FLAT_REGIME = "Flat regime",
		BBANDS_SQUEEZE_PERC = "BBands squeeze percentile",
		CHANNEL_WIDTH_DIRECTION = "ChannelWidthDirection";
		CHANNEL_POS = "Channel position",
		MA200_HIGHEST = "MA200Highest",
		MA200_LOWEST = "MA200Lowest",
		MA200_IN_CHANNEL = "MA200InChannel",
		MA200MA100_TREND_DISTANCE_PERC = "MA200 MA100 Distance percentile",
		MA20_SLOPE = "MA20 slope",
		MA50_SLOPE = "MA50 slope",
		MA100_SLOPE = "MA100 slope",
		MA200_SLOPE = "MA200 slope",
		MA_SLOPES_SCORE = "MA slopes slope",
		BULLISH_CANDLES = "Bullish candles",
		BEARISH_CANDLES = "Bearish candles",
		SMI = "SMI",
		STOCH = "Stoch",
		RSI3 = "RSI3",
 * 
		MAs = "Moving averages",
		ATR = "ATR",
		ICHI = "Ichi",
		BBANDS = "BBands",
*/
		addLogEntry(taValues.get(FlexTASource.TA_SITUATION));
		addLogEntry(taValues.get(FlexTASource.TREND_ID));
		addLogEntry(taValues.get(FlexTASource.MAs_DISTANCE_PERC));
		addLogEntry(taValues.get(FlexTASource.FLAT_REGIME));
		addLogEntry(taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC));
		addLogEntry(taValues.get(FlexTASource.CHANNEL_WIDTH_DIRECTION));
		addLogEntry(taValues.get(FlexTASource.CHANNEL_POS));
		addLogEntry(taValues.get(FlexTASource.MA200_HIGHEST));
		addLogEntry(taValues.get(FlexTASource.MA200_LOWEST));
		addLogEntry(taValues.get(FlexTASource.MA200_IN_CHANNEL));
		addLogEntry(taValues.get(FlexTASource.MA200MA100_TREND_DISTANCE_PERC));
		addLogEntry(taValues.get(FlexTASource.MA20_SLOPE));
		addLogEntry(taValues.get(FlexTASource.MA50_SLOPE));
		addLogEntry(taValues.get(FlexTASource.MA100_SLOPE));
		addLogEntry(taValues.get(FlexTASource.MA200_SLOPE));
		addLogEntry(taValues.get(FlexTASource.MA_SLOPES_SCORE));
		addLogEntry(taValues.get(FlexTASource.BULLISH_CANDLES));
		addLogEntry(taValues.get(FlexTASource.BEARISH_CANDLES));
		addLogEntry(taValues.get(FlexTASource.SMI));
		addLogEntry(taValues.get(FlexTASource.STOCH));
		addLogEntry(taValues.get(FlexTASource.RSI3));
	}

	public void setPnLInPips(double exitPrice, Instrument instrument) {
		if (isLong)
			PnL = (exitPrice - entryPrice) * Math.pow(10, instrument.getPipScale());
		else
			PnL = (entryPrice - exitPrice) * Math.pow(10, instrument.getPipScale());		
	}

	public void setPnLInPerc(double exitPrice) {
		if (isLong) 
			PnLPerc = (exitPrice - entryPrice) / entryPrice * 100.0;
		else 
			PnLPerc = (entryPrice - exitPrice) / exitPrice * 100.0;				}
}