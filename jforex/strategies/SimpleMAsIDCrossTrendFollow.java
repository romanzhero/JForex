package jforex.strategies;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.Filter;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

import jforex.BasicTAStrategy;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Trend.TREND_STATE;
import jforex.utils.FXUtils;
import jforex.utils.FXUtils.TradeLog;
import jforex.utils.FlexLogEntry;

public class SimpleMAsIDCrossTrendFollow extends BasicTAStrategy implements IStrategy {
	
	protected Period usedTimeFrame = Period.FOUR_HOURS;
	
	protected class LocalTradeLog extends FXUtils.TradeLog {
		boolean 
			isMA200Highest = false, isMA200Lowest = false,
			reEntry = false;
		
		double 
			entryBarBottomChPos = 0.0, 
			entryBarTopChPos = 0.0,
			
			maxFavourableChangeATR1Bar = -1.0,
			maxAdverseChangeATR1Bar = -1.0,
			maxFavourableChangeATR2Bar = -1.0,
			maxAdverseChangeATR2Bar = -1.0,
			maxFavourableChangeATR3Bar = -1.0,
			maxAdverseChangeATR3Bar = -1.0;
		
		long
			maxFavourableChangeATR1BarTime = -1,
			maxAdverseChangeATR1BarTime = -1,
			maxFavourableChangeATR2BarTime = -1,
			maxAdverseChangeATR2BarTime = -1,
			maxFavourableChangeATR3BarTime = -1,
			maxAdverseChangeATR3BarTime = -1;
		
		int bBandsWalk5Up = -1, bBandsWalkDown5 = -1;
		
		Trend.TREND_STATE entryTrendState;
		
		public LocalTradeLog(String pOrderLabel, boolean pIsLong, long pSignalTime, double pEntryPrice, double pSL, double pInitialRisk,
							 boolean isMA200Highest, boolean isMA200Lowest, 
							 boolean reEntry,
							 double entryBarBottomChPos, double entryBarTopChPos,
							 int bBandsWalk5Up, int bBandsWalkDown5,
							 TREND_STATE entryTrendState) {
			super(pOrderLabel, pIsLong, pSignalTime, pEntryPrice, pSL, pInitialRisk);
			this.isMA200Highest = isMA200Highest;
			this.isMA200Lowest = isMA200Lowest;
			this.reEntry = reEntry;
			this.entryBarBottomChPos = entryBarBottomChPos;
			this.entryBarTopChPos = entryBarTopChPos;
			this.bBandsWalk5Up = bBandsWalk5Up;
			this.bBandsWalkDown5 = bBandsWalkDown5;
			this.entryTrendState = entryTrendState;
		}
		
		public String exitReport(Instrument instrument, int noOfBarsInTrade) {
			return new String(super.exitReport(instrument) + ";" + isMA200Highest + ";" + isMA200Lowest
					 + ";" + reEntry + ";" + entryTrendState
					 + ";" + FXUtils.df1.format(entryBarBottomChPos)  + ";" + FXUtils.df1.format(entryBarTopChPos)
					 + ";" + FXUtils.if1.format(bBandsWalkDown5)  + ";" + FXUtils.if1.format(bBandsWalk5Up)
					 + ";" + FXUtils.df1.format(this.maxAdverseChangeATR1Bar) + ";" + FXUtils.getFormatedTimeGMT(maxAdverseChangeATR1BarTime)  
					 + ";" + FXUtils.df1.format(this.maxAdverseChangeATR2Bar) + ";" + FXUtils.getFormatedTimeGMT(maxAdverseChangeATR2BarTime)  
					 + ";" + FXUtils.df1.format(this.maxAdverseChangeATR3Bar) + ";" + FXUtils.getFormatedTimeGMT(maxAdverseChangeATR3BarTime)  
					 + ";" + FXUtils.df1.format(this.maxFavourableChangeATR1Bar) + ";" + FXUtils.getFormatedTimeGMT(maxFavourableChangeATR1BarTime)  
					 + ";" + FXUtils.df1.format(this.maxFavourableChangeATR2Bar) + ";" + FXUtils.getFormatedTimeGMT(maxFavourableChangeATR2BarTime)  
					 + ";" + FXUtils.df1.format(this.maxFavourableChangeATR3Bar) + ";" + FXUtils.getFormatedTimeGMT(maxFavourableChangeATR3BarTime)  
					 + ";" + FXUtils.if1.format(noOfBarsInTrade));
		}
		
		@Override
		public List<FlexLogEntry> exportToFlexLogs(Instrument instrument,int noOfBarsInTrade) {
			List<FlexLogEntry> l = super.exportToFlexLogs(instrument, noOfBarsInTrade);
			l.add(new FlexLogEntry("isMA200Highest", isMA200Highest ? "yes" : "no"));
			l.add(new FlexLogEntry("isMA200Lowest", isMA200Lowest ? "yes" : "no"));
			l.add(new FlexLogEntry("reEntry", reEntry ? "yes" : "no"));
			l.add(new FlexLogEntry("entryTrendState", entryTrendState.toString()));
			l.add(new FlexLogEntry("entryBarBottomChPos", new Double(entryBarBottomChPos), FXUtils.df1));
			l.add(new FlexLogEntry("entryBarTopChPos", new Double(entryBarTopChPos), FXUtils.df1));
			l.add(new FlexLogEntry("bBandsWalkDown5", new Double(bBandsWalkDown5), FXUtils.df1));
			l.add(new FlexLogEntry("bBandsWalk5Up", new Double(bBandsWalk5Up), FXUtils.df1));
			l.add(new FlexLogEntry("maxAdverseChangeATR1Bar", new Double(maxAdverseChangeATR1Bar), FXUtils.df1));
			l.add(new FlexLogEntry("maxAdverseChangeATR1BarTime", FXUtils.getFormatedTimeGMT(maxAdverseChangeATR1BarTime)));
			l.add(new FlexLogEntry("maxAdverseChangeATR2Bar", new Double(maxAdverseChangeATR2Bar), FXUtils.df1));
			l.add(new FlexLogEntry("maxAdverseChangeATR2BarTime", FXUtils.getFormatedTimeGMT(maxAdverseChangeATR2BarTime)));
			l.add(new FlexLogEntry("maxAdverseChangeATR3Bar", new Double(maxAdverseChangeATR3Bar), FXUtils.df1));
			l.add(new FlexLogEntry("maxAdverseChangeATR3BarTime", FXUtils.getFormatedTimeGMT(maxAdverseChangeATR3BarTime)));
			l.add(new FlexLogEntry("maxFavourableChangeATR1Bar", new Double(maxFavourableChangeATR1Bar), FXUtils.df1));
			l.add(new FlexLogEntry("maxFavourableChangeATR1BarTime", FXUtils.getFormatedTimeGMT(maxFavourableChangeATR1BarTime)));
			l.add(new FlexLogEntry("maxFavourableChangeATR2Bar", new Double(maxFavourableChangeATR2Bar), FXUtils.df1));
			l.add(new FlexLogEntry("maxFavourableChangeATR2BarTime", FXUtils.getFormatedTimeGMT(maxFavourableChangeATR2BarTime)));
			l.add(new FlexLogEntry("maxFavourableChangeATR3Bar", new Double(maxFavourableChangeATR3Bar), FXUtils.df1));
			l.add(new FlexLogEntry("maxFavourableChangeATR3BarTime", FXUtils.getFormatedTimeGMT(maxFavourableChangeATR3BarTime)));
			l.add(new FlexLogEntry("noOfBarsInTrade", new Double(noOfBarsInTrade), FXUtils.df1));
			return l;
		}

		public void updatePriceMoves(Instrument instrument, boolean isLong, List<IBar> last3Bars, double atr) throws JFException {
			if (isLong) {
				// check favourable price moves, counting from last close
				IBar currBar = last3Bars.get(2);
				double
					goodMove1 = last3Bars.get(2).getClose() - last3Bars.get(2).getLow(), // current bar
					goodMove2 = last3Bars.get(2).getClose() - last3Bars.get(1).getLow(),
					goodMove3 = last3Bars.get(2).getClose() - last3Bars.get(0).getLow();
				if (goodMove1 / atr > maxFavourableChangeATR1Bar) {
					maxFavourableChangeATR1Bar = goodMove1 / atr; 
					maxFavourableChangeATR1BarTime = currBar.getTime();
				}
				if (goodMove2 / atr > maxFavourableChangeATR2Bar) {
					maxFavourableChangeATR2Bar = goodMove2 / atr;
					maxFavourableChangeATR2BarTime = currBar.getTime();
				}
				if (goodMove3 / atr > maxFavourableChangeATR3Bar) {
					maxFavourableChangeATR3Bar = goodMove3 / atr;
					maxFavourableChangeATR3BarTime = currBar.getTime();
				}
				double barHighChannelPos = tradeTrigger.priceChannelPos(instrument, usedTimeFrame, OfferSide.ASK, currBar.getTime(), currBar.getHigh(), 0);				
				// check adverse price moves, counting from last close. Only from upper channel half of start bar
				if (barHighChannelPos > 50.0) {
					double 
						badMove1 = last3Bars.get(2).getHigh() - last3Bars.get(2).getClose();
					if (badMove1 / atr > maxAdverseChangeATR1Bar) {
						maxAdverseChangeATR1Bar = badMove1 / atr;
						maxAdverseChangeATR1BarTime = currBar.getTime();
					}
				}
				barHighChannelPos = tradeTrigger.priceChannelPos(instrument, usedTimeFrame, OfferSide.ASK, last3Bars.get(1).getTime(), last3Bars.get(1).getHigh(), 0);				
				if (barHighChannelPos > 50.0) {
					double badMove2 = last3Bars.get(1).getHigh() - last3Bars.get(2).getClose();
					if (badMove2 / atr > maxAdverseChangeATR2Bar) {
						maxAdverseChangeATR2Bar = badMove2 / atr;
						maxAdverseChangeATR2BarTime = currBar.getTime();
					}
				}
				barHighChannelPos = tradeTrigger.priceChannelPos(instrument, usedTimeFrame, OfferSide.ASK, last3Bars.get(2).getTime(), last3Bars.get(2).getHigh(), 0);				
				if (barHighChannelPos > 50.0) {
					double badMove3 = last3Bars.get(0).getHigh() - last3Bars.get(2).getClose();
					if (badMove3 / atr > maxAdverseChangeATR3Bar) {
						maxAdverseChangeATR3Bar = badMove3 / atr;
						maxAdverseChangeATR3BarTime = currBar.getTime();
					}
				}

			} else {
				// check favourable price moves, counting from last close
				IBar currBar = last3Bars.get(2);
				double
					goodMove1 = last3Bars.get(2).getHigh() - last3Bars.get(2).getClose() , // current bar
					goodMove2 = last3Bars.get(1).getHigh() - last3Bars.get(2).getClose(),
					goodMove3 = last3Bars.get(0).getHigh() - last3Bars.get(2).getClose();
				if (goodMove1 / atr > maxFavourableChangeATR1Bar) {
					maxFavourableChangeATR1Bar = goodMove1 / atr; 
					maxFavourableChangeATR1BarTime = currBar.getTime();
				}
				if (goodMove2 / atr > maxFavourableChangeATR2Bar) {
					maxFavourableChangeATR2Bar = goodMove2 / atr;
					maxFavourableChangeATR2BarTime = currBar.getTime();
				}
				if (goodMove3 / atr > maxFavourableChangeATR3Bar) {
					maxFavourableChangeATR3Bar = goodMove3 / atr;
					maxFavourableChangeATR3BarTime = currBar.getTime();
				}
				// check adverse price moves, counting from last close. Only from lower channel half
				double barHighChannelPos = tradeTrigger.priceChannelPos(instrument, usedTimeFrame, OfferSide.ASK, currBar.getTime(), currBar.getHigh(), 0);				
				if (barHighChannelPos < 50.0) {
					double badMove1 = last3Bars.get(2).getClose() - last3Bars.get(2).getLow();
					if (badMove1 / atr > maxAdverseChangeATR1Bar) {
						maxAdverseChangeATR1Bar = badMove1 / atr;
						maxAdverseChangeATR1BarTime = currBar.getTime();
					}
				}				
				barHighChannelPos = tradeTrigger.priceChannelPos(instrument, usedTimeFrame, OfferSide.ASK, last3Bars.get(1).getTime(), last3Bars.get(1).getHigh(), 0);				
				if (barHighChannelPos < 50.0) {
					double badMove2 = last3Bars.get(2).getClose() - last3Bars.get(1).getLow();
					if (badMove2 / atr > maxAdverseChangeATR2Bar) {
						maxAdverseChangeATR2Bar = badMove2 / atr;
						maxAdverseChangeATR2BarTime = currBar.getTime();
					}
				}				
				barHighChannelPos = tradeTrigger.priceChannelPos(instrument, usedTimeFrame, OfferSide.ASK, last3Bars.get(2).getTime(), last3Bars.get(2).getHigh(), 0);				
				if (barHighChannelPos < 50.0) {
					double badMove3 = last3Bars.get(2).getClose() - last3Bars.get(0).getLow();
					if (badMove3 / atr > maxAdverseChangeATR3Bar) {
						maxAdverseChangeATR3Bar = badMove3 / atr;
						maxAdverseChangeATR3BarTime = currBar.getTime();
					}
				}				
			}
		}
	}

	protected class PairTradeData {
		public Instrument pair;
		public LocalTradeLog tradeLog = null;
		public IOrder positionOrder = null;
		public boolean 
			waitingOrder = false,
			openPosition = false;
		public int 
			orderCounter = 0,
			noOfBarsInTrade = 0;		

		public PairTradeData(Instrument pair) {
			super();
			this.pair = pair;
		}

		protected void resetVars() {
			waitingOrder = false;
			tradeLog = null;
			noOfBarsInTrade = 0;			
		}		
	}
	
	protected Map<String, PairTradeData> pairsTradeData = new HashMap<String, PairTradeData>();
	protected Set<Instrument> instrumentsToIgnore = new HashSet<Instrument>();

	public SimpleMAsIDCrossTrendFollow(Properties props) {
		super(props);
	}

	@Override
	public void onStart(IContext context) throws JFException {
		onStartExec(context);		
		if (conf.getProperty("barStatsToDB", "no").equals("yes"))
			dbLogOnStart();
        StringTokenizer st = new StringTokenizer(conf.getProperty("pairsToCheck"), ";");
        Set<Instrument> instruments = new HashSet<Instrument>();
        while(st.hasMoreTokens()) {
        	String nextPair = st.nextToken();
            instruments.add(Instrument.fromString(nextPair));        	
        }

		Set<Instrument> pairs = context.getSubscribedInstruments();
		for (Instrument i : pairs) {
			if (instruments.contains(i))
				pairsTradeData.put(i.toString(), new PairTradeData(i));
			else
				instrumentsToIgnore.add(i);
		}
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (!period.equals(usedTimeFrame)
			|| !tradingAllowed(bidBar.getTime())
			|| instrumentsToIgnore.contains(instrument))
			return;
		
		PairTradeData currPairData = pairsTradeData.get(instrument.toString());
		TradeLog tradeLog = currPairData.tradeLog;
		IOrder positionOrder = currPairData.positionOrder;
		
		if (openPositionProcessing(instrument, period, bidBar, currPairData))
			return;
		
		Trend.TREND_STATE 
			trendStateForBull = trendDetector.getTrendState(instrument, usedTimeFrame, OfferSide.ASK, AppliedPrice.CLOSE, bidBar.getTime()),
			trendStateForBear = trendDetector.getTrendState(instrument, usedTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());

		waitingOrderProcessing(period, bidBar, tradeLog, positionOrder, trendStateForBull, trendStateForBear);
		
		boolean 
			bullishSignal = false,
			bearishSignal = false;
		
		bullishSignal = trendStateForBull.equals(Trend.TREND_STATE.UP_STRONG);
		
		bearishSignal = !bullishSignal && 
						(trendStateForBear.equals(Trend.TREND_STATE.DOWN_STRONG));
		if (bullishSignal) {
			if (currPairData.waitingOrder && positionOrder.isLong() && askBar.getHigh() < positionOrder.getOpenPrice())
				return;
			
			if (currPairData.waitingOrder && !positionOrder.isLong()) { 
				if (tradeLog != null)
					tradeLog.exitReason = new String("cancelled");
				positionOrder.close();
				positionOrder.waitForUpdate(IOrder.State.CANCELED);
			}
			
			placeBullishOrder(instrument, currPairData, askBar, bidBar);
			boolean 
				isMA200Highest = trendDetector.isMA200Highest(instrument, period, OfferSide.ASK, AppliedPrice.CLOSE, askBar.getTime()),
				isMA200Lowest = trendDetector.isMA200Lowest(instrument, period, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
			Trend.TREND_STATE prevTrendState = trendDetector.getTrendState(instrument, usedTimeFrame, OfferSide.ASK, AppliedPrice.CLOSE, history.getPreviousBarStart(usedTimeFrame, bidBar.getTime()));
				
			currPairData.tradeLog = new LocalTradeLog(currPairData.positionOrder.getLabel(), 
					currPairData.positionOrder.isLong(), 
					bidBar.getTime(), 
					currPairData.positionOrder.getOpenPrice(), 
					currPairData.positionOrder.getStopLossPrice(), 
					currPairData.positionOrder.getOpenPrice() - currPairData.positionOrder.getStopLossPrice(),
					isMA200Highest, isMA200Lowest,
					trendStateForBull.equals(prevTrendState),
					tradeTrigger.priceChannelPos(instrument, usedTimeFrame, OfferSide.ASK, bidBar.getTime(), askBar.getLow(), 0),
					tradeTrigger.priceChannelPos(instrument, usedTimeFrame, OfferSide.ASK, bidBar.getTime(), askBar.getHigh(), 0),
					channelPosition.consequitiveBarsAbove(instrument, usedTimeFrame, OfferSide.ASK, askBar.getTime(), 5),
					channelPosition.consequitiveBarsBelow(instrument, usedTimeFrame, OfferSide.BID, bidBar.getTime(), 5),
					prevTrendState);

		}
		else if (bearishSignal) {
			if (currPairData.waitingOrder && !positionOrder.isLong() && bidBar.getLow() > positionOrder.getOpenPrice())
				return;
			
			if (currPairData.waitingOrder && positionOrder.isLong()) {
				if (tradeLog != null)
					tradeLog.exitReason = new String("cancelled");
				positionOrder.close();
				positionOrder.waitForUpdate(IOrder.State.CANCELED);
			}
			
			placeBearishOrder(instrument, currPairData, bidBar, askBar);
			boolean 
				isMA200Highest = trendDetector.isMA200Highest(instrument, period, OfferSide.ASK, AppliedPrice.CLOSE, askBar.getTime()),
				isMA200Lowest = trendDetector.isMA200Lowest(instrument, period, OfferSide.BID, AppliedPrice.CLOSE, bidBar.getTime());
			Trend.TREND_STATE prevTrendState = trendDetector.getTrendState(instrument, usedTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, history.getPreviousBarStart(usedTimeFrame, bidBar.getTime()));
            currPairData.tradeLog = new LocalTradeLog(currPairData.positionOrder.getLabel(), 
					currPairData.positionOrder.isLong(), 
					bidBar.getTime(), 
					currPairData.positionOrder.getOpenPrice(), 
					currPairData.positionOrder.getStopLossPrice(), 
					currPairData.positionOrder.getStopLossPrice() - currPairData.positionOrder.getOpenPrice(),
					isMA200Highest, isMA200Lowest,
					trendStateForBear.equals(prevTrendState),
					tradeTrigger.priceChannelPos(instrument, usedTimeFrame, OfferSide.BID, bidBar.getTime(), bidBar.getLow(), 0),
					tradeTrigger.priceChannelPos(instrument, usedTimeFrame, OfferSide.BID, bidBar.getTime(), bidBar.getHigh(), 0),
					channelPosition.consequitiveBarsAbove(instrument, usedTimeFrame, OfferSide.ASK, askBar.getTime(), 5),
					channelPosition.consequitiveBarsBelow(instrument, usedTimeFrame, OfferSide.BID, bidBar.getTime(), 5),
					prevTrendState);
		}		
	}

	protected void waitingOrderProcessing(Period period, IBar bidBar, TradeLog tradeLog, IOrder positionOrder,
			Trend.TREND_STATE trendStateForBull, Trend.TREND_STATE trendStateForBear) throws JFException {
		if (positionOrder != null && positionOrder.getState().equals(IOrder.State.OPENED)) {
			if (positionOrder.isLong() && !trendStateForBull.equals(Trend.TREND_STATE.UP_STRONG)) {
				if (positionOrder.getState().equals(IOrder.State.OPENED)) {
					if (tradeLog != null)
						tradeLog.exitReason = new String("cancelled");
					positionOrder.close();
					positionOrder.waitForUpdate(IOrder.State.CANCELED);
				}
				else {
					log.print("CRITICAL ! Order " 
							+ positionOrder.getLabel() 
							+ " not yet submitted, can not be closed ! Created: " 
							+ FXUtils.getFormatedTimeGMT(positionOrder.getCreationTime()) + "; Happened at " + period.toString() + " time frame bar " 
							+ FXUtils.getFormatedTimeGMT(bidBar.getTime()));
					log.close();
					System.exit(1);
				}
			}
			else if (!positionOrder.isLong() && !trendStateForBear.equals(Trend.TREND_STATE.DOWN_STRONG)) {
				if (positionOrder.getState().equals(IOrder.State.OPENED)) {
					if (tradeLog != null)
						tradeLog.exitReason = new String("cancelled");
					positionOrder.close();
					positionOrder.waitForUpdate(IOrder.State.CANCELED);
				}
				else {
					log.print("CRITICAL ! Order " 
							+ positionOrder.getLabel() 
							+ " not yet submitted, can not be closed ! Created: " 
							+ FXUtils.getFormatedTimeGMT(positionOrder.getCreationTime()) + "; Happened at " + period.toString() + " time frame bar " 
							+ FXUtils.getFormatedTimeGMT(bidBar.getTime()));
					log.close();
					System.exit(1);
				}
			}
		}
	}

	protected boolean openPositionProcessing(Instrument instrument, Period period, IBar bidBar, PairTradeData currPairData) throws JFException {
		LocalTradeLog tradeLog = currPairData.tradeLog;
		IOrder positionOrder = currPairData.positionOrder;
		if (positionOrder != null && positionOrder.getState().equals(IOrder.State.FILLED)) {
			// already in position
			if (positionOrder.isLong()) {
				double ma100 = indicators.sma(instrument, usedTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, 100, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0];
				if (ma100 > positionOrder.getStopLossPrice()) {
					positionOrder.setStopLossPrice(ma100);
					tradeLog.updateMaxRisk(ma100);
				}
			} else {
				double ma100 = indicators.sma(instrument, usedTimeFrame, OfferSide.ASK, AppliedPrice.CLOSE, 100, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0];
				if (ma100 < positionOrder.getStopLossPrice()) {	
					positionOrder.setStopLossPrice(ma100);
					tradeLog.updateMaxRisk(ma100);
				}
			}
            // update trade logs too
			double atr = indicators.atr(instrument, period, OfferSide.BID, 14, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0];
			List<IBar> last3Bars = positionOrder.isLong() ? history.getBars(instrument, usedTimeFrame, OfferSide.BID, Filter.WEEKENDS, 3, bidBar.getTime(), 0) : history.getBars(instrument, usedTimeFrame, OfferSide.ASK, Filter.WEEKENDS, 3, bidBar.getTime(), 0);

			if (currPairData.noOfBarsInTrade++ > 0) {
				tradeLog.updateMaxLoss(bidBar, atr);
	            tradeLog.updateMaxProfit(bidBar);
	            tradeLog.updateMaxDD(bidBar, atr);
	            tradeLog.updatePriceMoves(instrument, positionOrder.isLong(), last3Bars, atr);
			}
			return true;
		} else
			return false;
	}

	private void placeBearishOrder(Instrument instrument, PairTradeData currPair, IBar bidBar, IBar askBar) throws JFException {
		double twoAtrAbs = FXUtils.roundToPip(2 * indicators.atr(instrument, usedTimeFrame, OfferSide.ASK, 14, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0] / Math.pow(10, instrument.getPipValue()), instrument);
		currPair.positionOrder = engine.submitOrder(getOrderLabel(instrument, currPair), instrument, OrderCommand.SELLSTOP, 0.1, bidBar.getLow(), 5.0, askBar.getLow() + twoAtrAbs, 0.0);		
	}

	private void placeBullishOrder(Instrument instrument, PairTradeData currPair, IBar askBar, IBar bidBar) throws JFException {
		double twoAtrAbs = FXUtils.roundToPip(2 * indicators.atr(instrument, usedTimeFrame, OfferSide.BID, 14, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0] / Math.pow(10, instrument.getPipValue()), instrument);
		currPair.positionOrder = engine.submitOrder(getOrderLabel(instrument, currPair), instrument, OrderCommand.BUYSTOP, 0.1, askBar.getHigh(), 5.0, bidBar.getHigh() - twoAtrAbs, 0.0);		
	}

	private String getOrderLabel(Instrument instrument, PairTradeData currPair) {
		String res = new String(getStrategyName() + "_" + instrument.name() + "_" + FXUtils.if3.format(++currPair.orderCounter));
		return res;
	}

	@Override
	public void onMessage(IMessage message) throws JFException {
		if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {
			// might be cancel of unfilled order or SL close
            Set<IMessage.Reason> reasons = message.getReasons();
            String reasonsStr = new String();
            for (IMessage.Reason r : reasons) {
                reasonsStr += r.toString();                
            } 
            
            PairTradeData currPair = pairsTradeData.get(message.getOrder().getInstrument().toString());
            if (currPair.tradeLog != null) {
            	currPair.tradeLog.exitTime = message.getCreationTime();
	            if (currPair.tradeLog.exitReason == null)
		            currPair.tradeLog.exitReason = reasonsStr;
	            currPair.tradeLog.PnL = currPair.positionOrder.getProfitLossInPips();
	            log.print(currPair.tradeLog.exitReport(message.getOrder().getInstrument(), currPair.noOfBarsInTrade));
	    		if (conf.getProperty("barStatsToDB", "no").equals("yes")) {
		            List<FlexLogEntry> l = currPair.tradeLog.exportToFlexLogs(message.getOrder().getInstrument(), currPair.noOfBarsInTrade);
		            String insSQL = FXUtils.dbGetTradeLogInsert(l, conf.getProperty("backTestRunID", getReportFileName()), message.getOrder().getLabel(), message.getOrder().isLong() ? "LONG" : "SHORT", "ER");
		            FXUtils.dbUpdateInsert(logDB, insSQL);
	    		}
            }			
			currPair.resetVars();
			return;
		}
		if (message.getType().equals(IMessage.Type.ORDER_SUBMIT_OK)) {
			PairTradeData currPair = pairsTradeData.get(message.getOrder().getInstrument().toString());
			currPair.waitingOrder = true;
		}
		if (message.getType().equals(IMessage.Type.ORDER_FILL_OK)) {
			PairTradeData currPairData = pairsTradeData.get(message.getOrder().getInstrument().toString());
            currPairData.tradeLog.fillTime = message.getCreationTime();
            currPairData.tradeLog.fillPrice = message.getOrder().getOpenPrice();			
		}
		
	}

	@Override
	public void onAccount(IAccount account) throws JFException {	}

	@Override
	public void onStop() throws JFException {
		onStopExec();		
	}
	
	@Override
	protected String getStrategyName() { return "SimpleMAsIDCrossTrendFollow";	}

	@Override
	protected String getReportFileName() { return "SimpleMAsIDCrossTrendFollow_Report_"; }


}
