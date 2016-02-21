package jforex.logging;

import java.util.ArrayList;
import java.util.List;

import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;
import jforex.utils.Logger;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;

public class TradeLog {
	public String orderLabel;
	public boolean isLong;
	public long signalTime, fillTime, maxProfitTime, maxDDTime, exitTime;
	public double entryPrice, fillPrice, SL, initialRisk, maxRisk, maxLoss,
			maxLossATR, maxDD, maxDDATR, maxProfit, maxProfitPrice, PnL;

	public String exitReason = null;

	protected List<FlexLogEntry> entryData = new ArrayList<FlexLogEntry>();
	public String setup;

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
	}

	public void setOrder(IOrder order) {
		orderLabel = order.getLabel();
		isLong = order.isLong();
		entryPrice = order.getOpenPrice();
		fillPrice = entryPrice; // needed for risk calc of unfilled orders,
								// which can get new SL while waiting due to
								// changes in the clould borders !
		SL = order.getStopLossPrice();
		initialRisk = order.getStopLossPrice() == 0 ? (order.getOpenPrice() - order
				.getStopLossPrice())
				* Math.pow(10, order.getInstrument().getPipScale()) : 0.0;
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
						+ FXUtils.getFileTimeStamp(signalTime) + ";"
						+ FXUtils.getFileTimeStamp(fillTime) + ";"
						+ FXUtils.getFileTimeStamp(exitTime) + ";"
						+ exitReason + ";"
						+ FXUtils.df1.format(PnL) + ";"
						+ FXUtils.df1.format(maxProfit * Math.pow(10, instrument.getPipScale())) + ";"
						+ FXUtils.df1.format(missedProfit(instrument)) + ";"
						+ FXUtils.df1.format(missedProfitPerc(instrument)) + ";"
						+ (instrument.getPipScale() != 2 ? FXUtils.df5.format(maxProfitPrice) : FXUtils.df2.format(maxProfitPrice)) + ";"						
						+ FXUtils.df1.format(maxLoss * Math.pow(10, instrument.getPipScale()))	+ ";"
						+ FXUtils.df2.format(maxLossATR) + ";"
						+ (instrument.getPipScale() == 2 ? FXUtils.df2
								.format(entryPrice) : FXUtils.df5
								.format(entryPrice))
						+ ";"
						+ (instrument.getPipScale() == 2 ? FXUtils.df2
								.format(fillPrice) : FXUtils.df5
								.format(fillPrice))
						+ ";"
						+ (instrument.getPipScale() == 2 ? FXUtils.df2
								.format(SL) : FXUtils.df5.format(SL))
						+ ";"
						+ FXUtils.df1.format(initialRisk
								* Math.pow(10, instrument.getPipScale()))
						+ ";"
						+ FXUtils.df1.format(maxRisk
								* Math.pow(10, instrument.getPipScale()))
						+ ";"
						+ FXUtils.df1.format(maxDD
								* Math.pow(10, instrument.getPipScale()))
						+ ";"
						+ FXUtils.df2.format(maxDDATR)	+ ";"
						+ FXUtils.getFileTimeStamp(maxDDTime));
		for (FlexLogEntry e : entryData)
			result += ";" + e.getFormattedValue();
		return result;

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
						+ "PnL;"
						+ "maxProfit;"
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
			result += ";" + e.getLabel();
		return result;

	}
}