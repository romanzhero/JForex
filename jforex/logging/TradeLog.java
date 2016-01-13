package jforex.logging;

import jforex.explorers.IchimokuTradeTestRun;
import jforex.utils.Logger;

import com.dukascopy.api.IBar;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.util.DateUtils;

public class TradeLog {
	public String orderLabel;
    public boolean 
        isLong;
    public long 
        signalTime,
        fillTime,
        maxProfitTime,
        maxDDTime,
        exitTime;
    public double 
        entryPrice,
        fillPrice,
        SL,
        initialRisk,
        maxRisk,
        maxLoss,
        maxLossATR,
        maxDD,
        maxDDATR,
        maxProfit,
        maxProfitPrice,
        PnL;
    
     public String
         exitReason = null;
     
     public TradeLog(String pOrderLabel, boolean pIsLong, long pSignalTime, double pEntryPrice, double pSL, double pInitialRisk) {
			orderLabel = pOrderLabel;
            isLong = pIsLong;
            signalTime = pSignalTime;
            entryPrice = pEntryPrice;
            fillPrice = entryPrice; // needed for risk calc of unfilled orders, which can get new SL while waiting due to changes in the clould borders !
            SL = pSL;
            initialRisk = pInitialRisk;
            maxRisk = initialRisk;
            
            exitReason = null;
            PnL = 0.0;
            maxLossATR = maxLoss = 0.0;
            maxDD = maxDDATR = 0.0;
            maxProfit = 0.0;
            maxProfitPrice = 0.0;
            maxProfitTime = maxDDTime = 0;
 }
     
     public double missedProfit(Instrument instrument) {
         // PnL taken from IOrder and already in pips
         return maxProfit * Math.pow(10, instrument.getPipScale()) - PnL;
     }
     
     public double missedProfitPerc(Instrument instrument) {
         // PnL taken from IOrder and already in pips
         return PnL > 0.0 ? 100 * missedProfit(instrument) / (maxProfit * Math.pow(10, instrument.getPipScale())) : 0.0;
     }
     
     public void updateMaxRisk(double newSL) {
         if (isLong) {
             if (fillPrice - newSL > maxRisk)
                 maxRisk = fillPrice - newSL;
         }
         else {
             if (newSL - fillPrice > maxRisk)
                 maxRisk = newSL - fillPrice;
         }
     }
     
     public void updateMaxLoss(IBar bidBar, double ATR) {
         if (isLong) {
             if (bidBar.getLow() < fillPrice && bidBar.getLow() - fillPrice  < maxLoss) {
                 maxLoss = bidBar.getLow() - fillPrice;
                 maxLossATR = maxLoss / ATR;
             }
         }
         else {
             if (bidBar.getHigh() > fillPrice && fillPrice - bidBar.getHigh() < maxLoss) {
                 maxLoss = fillPrice - bidBar.getHigh();
                 maxLossATR = maxLoss / ATR;
             }
         }
     }
     
     public void updateMaxDD(IBar bidBar, double slowLine, double ATR) {
         //updateMaxProfit(bidBar);
         // avoid low of the maxProfit bar
    	 if (bidBar.getTime() <= maxProfitTime) 
             return;
             
         if (isLong) {
             if (bidBar.getClose() > slowLine && bidBar.getLow() >= fillPrice && maxProfitPrice - bidBar.getLow() > maxDD) {
                 maxDD = maxProfitPrice - bidBar.getLow();
                 maxDDTime = bidBar.getTime();
                 maxDDATR = maxDD / ATR;
             }
         }
         else {
             if (bidBar.getClose() < slowLine && bidBar.getHigh() <= fillPrice && bidBar.getHigh() - maxProfitPrice > maxDD) {
                 maxDD = bidBar.getHigh() - maxProfitPrice;
                 maxDDTime = bidBar.getTime();
                 maxDDATR = maxDD / ATR;
             }
         }
     }
     
     public void updateMaxProfit(IBar bidBar) {
         if (isLong) {
             if (bidBar.getHigh() > fillPrice && bidBar.getHigh() - fillPrice > maxProfit) {
                 maxProfit = bidBar.getHigh() - fillPrice;                 
                 maxProfitPrice = bidBar.getHigh();
                 maxProfitTime = bidBar.getTime();
             }
         }
         else {
             if (bidBar.getLow() < fillPrice && fillPrice - bidBar.getLow() > maxProfit) {
                 maxProfit = fillPrice - bidBar.getLow();                                  
                 maxProfitPrice = bidBar.getLow();
                 maxProfitTime = bidBar.getTime();
             }
         }             
     }     

     public void exitReport(Instrument instrument, Logger log) {
        log.print(prepareExitReport(instrument));
           
     }
     
     public String prepareExitReport(Instrument instrument) {
    	 return new String("ER;" 
    	            + orderLabel + ";" 
    	            + (isLong ? "LONG" : "SHORT") + ";" 
    	            + DateUtils.format(signalTime) + ";" 
    	            + DateUtils.format(fillTime) + ";"
    	            + DateUtils.format(exitTime) + ";"
    	            + exitReason + ";" 
    	            + (instrument.getPipScale() == 2 ? IchimokuTradeTestRun.df2.format(entryPrice) : IchimokuTradeTestRun.df5.format(entryPrice)) + ";" 
    	            + (instrument.getPipScale() == 2 ? IchimokuTradeTestRun.df2.format(fillPrice) : IchimokuTradeTestRun.df5.format(fillPrice)) + ";" 
    	            + (instrument.getPipScale() == 2 ? IchimokuTradeTestRun.df2.format(SL) : IchimokuTradeTestRun.df5.format(SL)) + ";" 
    	            + IchimokuTradeTestRun.df1.format(initialRisk * Math.pow(10, instrument.getPipScale())) + ";" 
    	            + IchimokuTradeTestRun.df1.format(maxRisk * Math.pow(10, instrument.getPipScale())) + ";" 
    	            + IchimokuTradeTestRun.df1.format(maxLoss * Math.pow(10, instrument.getPipScale())) + ";" 
    	            + IchimokuTradeTestRun.df2.format(maxLossATR) + ";" 
    	            + IchimokuTradeTestRun.df1.format(maxDD * Math.pow(10, instrument.getPipScale())) + ";" 
    	            + IchimokuTradeTestRun.df2.format(maxDDATR) + ";" 
    	            + DateUtils.format(maxDDTime) + ";" 
    	            + IchimokuTradeTestRun.df1.format(maxProfit * Math.pow(10, instrument.getPipScale())) + ";" 
    	            + (instrument.getPipScale() != 2 ? IchimokuTradeTestRun.df5.format(maxProfitPrice) : IchimokuTradeTestRun.df2.format(maxProfitPrice)) + ";"
    	            + IchimokuTradeTestRun.df1.format(PnL) + ";" 
    	            + IchimokuTradeTestRun.df1.format(missedProfit(instrument)) + ";" 
    	            + IchimokuTradeTestRun.df1.format(missedProfitPerc(instrument)));
     }
}