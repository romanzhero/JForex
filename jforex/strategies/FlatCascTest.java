package jforex.strategies;

import java.util.*;
import com.dukascopy.api.*;

import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;
import jforex.utils.Logger;

import jforex.logging.TradeLog;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.techanalysis.Channel;
import jforex.techanalysis.TradeTrigger;
import jforex.trades.*;

public class FlatCascTest implements IStrategy {
    @Configurable(value = "Period", description = "Choose the time frame")
    public Period selectedPeriod = Period.FOUR_HOURS;
    
    @Configurable(value = "Filter", description = "Choose the candle filter")
    public Filter selectedFilter = Filter.WEEKENDS;
    
    @Configurable(value = "Amount" , stepSize = 0.0001, description = "Choose amount (step size 0.0001)")
    public double selectedAmount = 0.0001;
    
    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IIndicators indicators;
    private IDataService dataService;
    
    private Map<String, IOrder> orderPerPair = new HashMap<String, IOrder>();
    private long orderCnt = 1;
    
    private List<ITradeSetup> tradeSetups = new ArrayList<ITradeSetup>();
    private ITakeOverRules takeOverRules = new SmiSmaTakeOverRules();
    private ITradeSetup currentSetup = null;
    
    private Trend trend = null;
    private Volatility vola = null;
    private Channel channel = null;
    private TradeTrigger candles = null;
    
    private Logger log = null;    
    private TradeLog tradeLog = null;
    
    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.indicators = context.getIndicators();
        context.getUserInterface();
        
        dataService = context.getDataService();
        
        trend = new Trend(indicators);
        vola = new Volatility(indicators);
        channel = new Channel(context.getHistory(), indicators);
        candles = new TradeTrigger(indicators, context.getHistory(), null);
        
        log = new Logger("D://Users//Leteci macak//Documents//dropbox//Trading//SMI//logs//SMI_report_" + FXUtils.getFileTimeStamp(System.currentTimeMillis()) + ".txt");

        Set<Instrument> pairs = context.getSubscribedInstruments();
        for (Instrument currI : pairs) {
            orderPerPair.put(currI.name(), null);
        }  
        tradeSetups.add(new FlatTradeSetup(indicators, history, engine, true)); 
        // tradeSetups.add(new SmiTradeSetup(indicators, history, engine, true));        
        // tradeSetups.add(new SmaTradeSetup(indicators, history, engine, context.getSubscribedInstruments(), true, true));        
    }
    
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (!period.equals(selectedPeriod)
            || !tradingAllowed(bidBar.getTime())
            || (bidBar.getClose() == bidBar.getOpen() && bidBar.getClose() == bidBar.getHigh() && bidBar.getClose() == bidBar.getLow())) 
            return;
        
        double[][] 
            slowSMI = indicators.smi(instrument, period, OfferSide.BID, 50, 15, 5, 3, selectedFilter, 2, bidBar.getTime(), 0),
            fastSMI = indicators.smi(instrument, period, OfferSide.BID, 10, 3, 5, 3, selectedFilter, 2, bidBar.getTime(), 0);
        double
            atr = indicators.atr(instrument, period, OfferSide.BID, 14, selectedFilter, 1, bidBar.getTime(), 0)[0],
            ma20 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 20, selectedFilter, 1, bidBar.getTime(), 0)[0],
            ma50 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, selectedFilter, 1, bidBar.getTime(), 0)[0],
            ma100 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100, selectedFilter, 1, bidBar.getTime(), 0)[0],
            ma200 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, selectedFilter, 1, bidBar.getTime(), 0)[0];
            
        IOrder order = orderPerPair.get(instrument.name());
        if (order != null) {
            // there is an open order, might be pending (waiting) or filled !
            // go through all the other (non-current) setups and check whether they should take over (their conditions fullfilled)           
            ITradeSetup.EntryDirection signal = ITradeSetup.EntryDirection.NONE; 
            boolean takeOver = false;
            if (!currentSetup.isTradeLocked(instrument, period, askBar, bidBar, selectedFilter, order)) {
                for (ITradeSetup setup : tradeSetups) {
                    if (!setup.getName().equals(currentSetup.getName())) {
                        signal = setup.checkTakeOver(instrument, period, askBar, bidBar, selectedFilter);
                        takeOver = (order.isLong() && signal.equals(ITradeSetup.EntryDirection.LONG) && takeOverRules.canTakeOver(currentSetup.getName(), setup.getName())) 
                                    || (!order.isLong() && signal.equals(ITradeSetup.EntryDirection.SHORT) && takeOverRules.canTakeOver(currentSetup.getName(), setup.getName()));
                        if (takeOver) {
                            currentSetup = setup;
                            String logLine = new String(FXUtils.getFormatedBarTime(bidBar) + ": " + signal.toString() + " takeover by " + currentSetup.getName() + ", order " + order.getLabel());
                            console.getOut().println(logLine);
                            log.print(logLine);
                            setup.takeTradingOver(order);
                            break;
                        }
                    }
                }
            }
            if (!takeOver) {
                currentSetup.inTradeProcessing(instrument, period, askBar, bidBar, selectedFilter, order);
                if (order != null) {
                    // inTradeProcessing might CLOSE the order, onMessage will set it to null !
                    if (order.getStopLossPrice() != 0.0)
                        tradeLog.updateMaxRisk(order.getStopLossPrice());
                    tradeLog.updateMaxLoss(bidBar, atr);
                    tradeLog.updateMaxProfit(bidBar);
                    ITradeSetup.EntryDirection exitSignal = currentSetup.checkExit(instrument, period, askBar, bidBar, selectedFilter, order);
                    if ((order.isLong() && exitSignal.equals(ITradeSetup.EntryDirection.LONG)) || (!order.isLong() && exitSignal.equals(ITradeSetup.EntryDirection.SHORT))) {
                        if (tradeLog != null)
                            tradeLog.exitReason = new String("exit criteria");
                        String logLine = new String(FXUtils.getFormatedBarTime(bidBar) + ": " + exitSignal.toString() + "  exit signal by " + currentSetup.getName() + " setup, order " + order.getLabel());
                        console.getOut().println(logLine);
                        log.print(logLine);
    
                        order.close();
                        order.waitForUpdate(null);
                        order = null; 
                        orderPerPair.put(instrument.name(), null);
                    }                
                }                
            }
        }
        // enable re-entry on the same bar !
        if (order == null) {
            // no open position
            ITradeSetup.EntryDirection signal = ITradeSetup.EntryDirection.NONE; 
            for (ITradeSetup setup : tradeSetups) {
                signal = setup.checkEntry(instrument, period, askBar, bidBar, selectedFilter);
                if (!signal.equals(ITradeSetup.EntryDirection.NONE)) {
                    order = setup.submitOrder(getOrderLabel(instrument, bidBar.getTime(), signal.equals(ITradeSetup.EntryDirection.LONG) ? "BUY" : "SELL"), instrument, signal.equals(ITradeSetup.EntryDirection.LONG), selectedAmount, bidBar, askBar);
                                 
                    createTradeLog(instrument, period, askBar, OfferSide.ASK, order, slowSMI[0][1], fastSMI[0][1], ma200 > ma100 && ma200 > ma50 && ma200 > ma20, ma200 < ma100 && ma200 < ma50 && ma200 < ma20);                                      
                    orderPerPair.put(instrument.name(), order);                    
                    currentSetup = setup;
                    String logLine = new String(FXUtils.getFormatedBarTime(bidBar) + ": " + (signal.equals(ITradeSetup.EntryDirection.LONG) ? "long" : "short") + " entry with " + currentSetup.getName() + ", order " + order.getLabel());
                    console.getOut().println(logLine);
                    log.print(logLine);
                  
                    break;
                }
            }
        }        
    }

    public void onMessage(IMessage message) throws JFException {
        if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {
            // might be cancel of unfilled order or SL close
            Set<IMessage.Reason> reasons = message.getReasons();
            String reasonsStr = new String();
            for (IMessage.Reason r : reasons) {
                reasonsStr += r.toString();                
            } 
            
            if (tradeLog != null) {
                tradeLog.exitTime = message.getCreationTime();
                if (tradeLog.exitReason == null)
                    tradeLog.exitReason = reasonsStr;
                tradeLog.PnL = message.getOrder().getProfitLossInPips();
                String logLine = tradeLog.prepareExitReport(message.getOrder().getInstrument());
                console.getOut().println(logLine);
                log.print(logLine);
            }

            orderPerPair.put(message.getOrder().getInstrument().name(), null);            
            for (ITradeSetup setup : tradeSetups)
                setup.afterTradeReset(message.getOrder().getInstrument());
            currentSetup = null;
        }
        if (message.getType().equals(IMessage.Type.ORDER_FILL_OK)) {
            tradeLog.fillTime = message.getCreationTime();
            tradeLog.fillPrice = message.getOrder().getOpenPrice();            
        }
    }

    public void onStop() throws JFException {
        log.close();
    }

    public void onAccount(IAccount account) throws JFException {
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {
    }
    
    String getOrderLabel(Instrument instrument, long time, String direction) {
        return new String("FlatCascTest_" + instrument.name() + "_" + FXUtils.getFormatedTimeGMTforID(time) + "_" + orderCnt++ + "_" + direction);
    }
        
    boolean tradingAllowed(long time) throws JFException {
       ITimeDomain timeDomain = dataService.getOfflineTimeDomain();      

        return time + selectedPeriod.getInterval() < timeDomain.getStart();
    }
    
    void createTradeLog(Instrument instrument, Period period, IBar bar, OfferSide side, IOrder order, double slowSMI, double fastSMI, boolean ma200Highest, boolean ma200Lowest) throws JFException{
        Trend.TREND_STATE entryTrendID;

        tradeLog = new TradeLog(order.getLabel(), order.isLong(), bar.getTime(), order.getOpenPrice(), order.getStopLossPrice(), order.getStopLossPrice() == 0 ? (order.getOpenPrice() - order.getStopLossPrice()) * Math.pow(10, instrument.getPipScale()) : 0.0);        
            
        entryTrendID = trend.getTrendState(instrument, period, side, IIndicators.AppliedPrice.CLOSE, bar.getTime());
        tradeLog.addLogEntry(new FlexLogEntry("TrendID", new String(entryTrendID.toString())));
        tradeLog.addLogEntry(new FlexLogEntry("generalTrendStrength", new Double(trend.getMAsMaxDiffStDevPos(instrument, period, side, IIndicators.AppliedPrice.CLOSE, bar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df2));
        tradeLog.addLogEntry(new FlexLogEntry("generalTrendStrengthPerc", new Double(trend.getMAsMaxDiffPercentile(instrument, period, side, IIndicators.AppliedPrice.CLOSE, bar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df1));
        if (entryTrendID.equals(Trend.TREND_STATE.UP_STRONG)) {
            tradeLog.addLogEntry(new FlexLogEntry("trendingTrendStrength", new Double(trend.getUptrendMAsMaxDifStDevPos(instrument, period, side, IIndicators.AppliedPrice.CLOSE, bar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df2));
            tradeLog.addLogEntry(new FlexLogEntry("trendingTrendStrengthPerc", new Double(trend.getUptrendMAsMaxDiffPercentile(instrument, period, side, IIndicators.AppliedPrice.CLOSE, bar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df1));
        }
        else if (entryTrendID.equals(Trend.TREND_STATE.DOWN_STRONG)) {
            tradeLog.addLogEntry(new FlexLogEntry("trendingTrendStrength", new Double(trend.getDowntrendMAsMaxDifStDevPos(instrument, period, side, IIndicators.AppliedPrice.CLOSE, bar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df2));
            tradeLog.addLogEntry(new FlexLogEntry("trendingTrendStrengthPerc", new Double(trend.getDowntrendMAsMaxDiffPercentile(instrument, period, side, IIndicators.AppliedPrice.CLOSE, bar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df1));
        }
        else {
            tradeLog.addLogEntry(new FlexLogEntry("trendingTrendStrength", new Double(0.0), FXUtils.df2));
            tradeLog.addLogEntry(new FlexLogEntry("trendingTrendStrengthPerc", new Double(0.0), FXUtils.df1));            
        }
        tradeLog.addLogEntry(new FlexLogEntry("bBandsSqueeze", new Double(vola.getBBandsSqueeze(instrument, period, side, bar.getTime(), 20)), FXUtils.df1));
        tradeLog.addLogEntry(new FlexLogEntry("bBandsSqueezePerc", new Double(vola.getBBandsSqueezePercentile(instrument, period, side, IIndicators.AppliedPrice.CLOSE, Filter.WEEKENDS, bar.getTime(), 20, FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df1));
        tradeLog.addLogEntry(new FlexLogEntry("highPos", new Double(channel.priceChannelPos(instrument, period, side, bar.getTime(), bar.getHigh())), FXUtils.df1));
        tradeLog.addLogEntry(new FlexLogEntry("lowPos", new Double(channel.priceChannelPos(instrument, period, side, bar.getTime(), bar.getLow())), FXUtils.df1));
        tradeLog.addLogEntry(new FlexLogEntry("closePos", new Double(channel.priceChannelPos(instrument, period, side, bar.getTime(), bar.getClose())), FXUtils.df1));
        tradeLog.addLogEntry(new FlexLogEntry("barSizeStat", new Double(candles.barLengthStatPos(instrument, period, side, bar, FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df2));
        tradeLog.addLogEntry(new FlexLogEntry("bodySizePerc", new Double(candles.barsBodyPerc(bar)), FXUtils.df1));        
        tradeLog.addLogEntry(new FlexLogEntry("fastSMI", new Double(fastSMI), FXUtils.df1));        
        tradeLog.addLogEntry(new FlexLogEntry("slowSMI", new Double(slowSMI), FXUtils.df1));        
        tradeLog.addLogEntry(new FlexLogEntry("SMIcross", new String(fastSMI > slowSMI ? "fast" : "slow")));
        tradeLog.addLogEntry(new FlexLogEntry("ma200Highest", new String(ma200Highest ? "ma200Highest" : "no")));
        tradeLog.addLogEntry(new FlexLogEntry("ma200Lowest", new String(ma200Lowest ? "ma200Lowest" : "no")));
    }

}
