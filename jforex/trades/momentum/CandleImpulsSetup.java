/**
 * 
 */
package jforex.trades.momentum;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IIndicators.MaType;
import com.dukascopy.api.impl.Indicators;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.tictactec.ta.lib.MAType;

import jforex.events.TAEventDesc;
import jforex.events.TAEventDesc.TAEventType;
import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.Trend;
import jforex.techanalysis.source.FlexTASource;
import jforex.techanalysis.source.FlexTAValue;
import jforex.techanalysis.source.TechnicalSituation;
import jforex.trades.ITradeSetup;
import jforex.trades.TradeSetup;
import jforex.utils.FXUtils;
import jforex.utils.RollingAverage;

/**
 * Simple setup to enter on 3 candles all in the same direction. At least one must be strong, with range above average and 80% body
 * Exit on the first opposite candle, but not while Stoch is OS/OB in the direction of the trade
 * Stop-loss is from the entry until the extreme of the first bar in the block of three
 * The strategy should avoid trading after profit is above some significant percentage of daily range
 * Idea is to fetch few small but relatively safe trades per day
 *
 */
public class CandleImpulsSetup extends TradeSetup implements ITradeSetup {
	
	protected class SignalDesc {
		public SignalDesc(BodyDirection bodyDirection, double[] barSizes, double[] barBodys) {
			direction = bodyDirection;
			bar1Size = barSizes[0];
			bar2Size = barSizes[1];
			bar3Size = barSizes[2];
			avgBarSize = (bar1Size + bar2Size + bar3Size) / 3;
			bar1Body = barBodys[0];
			bar2Body = barBodys[1];
			bar3Body = barBodys[2];
			avgBodySize = (bar1Body + bar2Body + bar3Body) / 3;
		}
		double	
			bar1Size, bar2Size, bar3Size, avgBarSize,
			bar1Body, bar2Body, bar3Body, avgBodySize;
		BodyDirection direction;
	}
	
	protected Map<Instrument, RollingAverage> barRangeAverages = null;
	protected TAEventDesc lastEntryDesc = null;
	
	protected enum BodyDirection {UP, DOWN, NONE};

	public CandleImpulsSetup(IEngine engine, IContext context, Map<Instrument, RollingAverage> averages) {
		super(engine, context);
		barRangeAverages = averages;
	}


	public CandleImpulsSetup(IEngine engine, IContext context, Map<Instrument, RollingAverage> averages, boolean pTakeOverOnly) {
		super(engine, context, pTakeOverOnly);
		barRangeAverages = averages;
	}


	@Override
	public String getName() {
		return "CandleImpulsSetup";
	}

	public TAEventDesc checkEntry(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, Map<String, FlexTAValue> taValues) throws JFException {
		//TODO: potrebna poboljsanja:
		// - nema breakout ulaza, pogotovo ne kada je uzak kanal !
		// - vezano sa tim, dno prve od trie svecice mora biti u povoljnoj polovini kanala, mozda cak i 30%-40%:
		//		- prosta analiza postojeceg loga (treca, ne prva svecica iznad 50% za short stedi 190 pipsa !!!)
		//		- i jos 145 pipsa na long !
		// - bolje analizirati SMI i Stoch momentum da se ne bi menjao SL bez potrebe. Ne mora SMI da bude 
		// potpuno UP/DOWN, dovoljno je da OBA budu na povoljnom nivou i da makar jedan ide u pravcu trejda
		// - preskociti signale protiv jasnog trenda, pogotovo na pogresnoj strani kanala (breakout) !!!
		// GENERALNO: razlika izmedju brzog i sporog SMI i pravac njene promene su vazni !
		// velika razlika je cesto kraj momentuma, flat entry protiv OK. Ali mala razlika koja se uveceva
		// nakon crossa je znak novog i svezeg momentuma, ne treba dirati SL !! Odnosno treba zatvoriti 
		// suprotni trejd
		List<IBar> last3BidBars = context.getHistory().getBars(instrument, period, OfferSide.BID, Filter.WEEKENDS, 3, bidBar.getTime(), 0);
		DateTime
			firstBarDate = new DateTime(last3BidBars.get(0).getTime()),
			lastBarDate = new DateTime(last3BidBars.get(2).getTime());
		if (firstBarDate.getDayOfWeek() == DateTimeConstants.FRIDAY && lastBarDate.getDayOfWeek() == DateTimeConstants.SUNDAY)
			return null;
		List<IBar> last3AskBars = context.getHistory().getBars(instrument, period, OfferSide.ASK, Filter.WEEKENDS, 3, bidBar.getTime(), 0);
		
		double[][]
				bidBarsChPos = context.getIndicators().bbands(instrument, period, OfferSide.BID, AppliedPrice.CLOSE, 20, 2.0, 2.0, MaType.SMA, filter, 3, bidBar.getTime(), 0),
				askBarsChPos = context.getIndicators().bbands(instrument, period, OfferSide.ASK, AppliedPrice.CLOSE, 20, 2.0, 2.0, MaType.SMA, filter, 3, bidBar.getTime(), 0);
		double 
			chPos = taValues.get(FlexTASource.CHANNEL_POS).getDoubleValue(),
			firstBidBarChWidth = bidBarsChPos[0][0] - bidBarsChPos[2][0], 
			firstAskBarChWidth = askBarsChPos[0][0] - askBarsChPos[2][0], 
			firstBidBarChBottom = bidBarsChPos[2][0], 
			firstAskBarChBottom = askBarsChPos[2][0], 
			firstBidBarTopChPos = (last3BidBars.get(0).getHigh() - firstBidBarChBottom) / firstBidBarChWidth * 100,
			firstAskBarBottomChPos = (last3AskBars.get(0).getLow() - firstAskBarChBottom) / firstAskBarChWidth * 100,
			vola = taValues.get(FlexTASource.BBANDS_SQUEEZE_PERC).getDoubleValue(),
			bidBarRangeAvg = (bidBar.getHigh() - bidBar.getLow()) / barRangeAverages.get(instrument).getAverage(),
			askBarRangeAvg = (askBar.getHigh() - askBar.getLow()) / barRangeAverages.get(instrument).getAverage();
/*		String taRegime = FXUtils.getRegimeString(taValues.get(FlexTASource.TREND_ID).getTrendStateValue(), 
				taValues.get(FlexTASource.MAs_DISTANCE_PERC).getDoubleValue(),
				(Trend.FLAT_REGIME_CAUSE)taValues.get(FlexTASource.FLAT_REGIME).getValue(), 
				taValues.get(FlexTASource.MA200_HIGHEST).getBooleanValue(), 
				taValues.get(FlexTASource.MA200_LOWEST).getBooleanValue());*/
		TechnicalSituation taSituation = taValues.get(FlexTASource.TA_SITUATION).getTehnicalSituationValue();
		SignalDesc signal = barsSignalGiven(last3BidBars, instrument);
		if (signal == null)
			return null;
		if (!taSituation.taSituation.equals(TechnicalSituation.OverallTASituation.BULLISH)
			&& signal.direction.equals(BodyDirection.DOWN)
			&& chPos > 45) {
			updateTradeStats(taValues, signal);
			lastEntryDesc = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, false, askBar, bidBar, period);
			lastEntryDesc.stopLossLevel = last3AskBars.get(0).getHigh();
			return lastEntryDesc;
		} else if (!taSituation.taSituation.equals(TechnicalSituation.OverallTASituation.BEARISH)
				&& signal.direction.equals(BodyDirection.UP)
				&& chPos < 55) {
			updateTradeStats(taValues, signal);
			lastEntryDesc = new TAEventDesc(TAEventType.ENTRY_SIGNAL, getName(), instrument, true, askBar, bidBar, period);
			lastEntryDesc.stopLossLevel = last3BidBars.get(0).getLow();
			return lastEntryDesc;
		}
		return null;
	}


	protected void updateTradeStats(Map<String, FlexTAValue> taValues, SignalDesc signal) {
		taValues.put("CandleImpulsSizes", new FlexTAValue("CandleImpulsSizesAvg", new Double(signal.avgBarSize), FXUtils.df1));
		taValues.put("CandleImpulsBodies", new FlexTAValue("CandleImpulsBodiesAvg", new Double(signal.avgBodySize), FXUtils.df2));
		taValues.put("CandleImpuls", 
				new FlexTAValue("CandleImpuls", "bar sizes: (" 
						+ FXUtils.df1.format(signal.bar1Size) + ", " 
						+ FXUtils.df1.format(signal.bar2Size) + ", " 
						+ FXUtils.df1.format(signal.bar3Size) + "), avg: " 
						+ FXUtils.df1.format(signal.avgBarSize) + ", bar bodies: ("
						+ FXUtils.df2.format(signal.bar1Body) + ", " 
						+ FXUtils.df2.format(signal.bar2Body) + ", " 
						+ FXUtils.df2.format(signal.bar3Body) + "), avg: "
						+ FXUtils.df2.format(signal.avgBodySize)));
		
	}
	
	protected boolean strongBearishBreakOut(double chPos, double vola, double bidBarRangeAvg, IBar bidBar) {
		return chPos < 0 
				&& vola > 65
				&& bidBarRangeAvg > 1
				&& (bidBar.getOpen() - bidBar.getClose()) / (bidBar.getHigh() - bidBar.getLow()) > 0.65;
	}

	protected boolean strongBullishBreakOut(double chPos, double vola, double askBarRangeAvg, IBar askBar) {
		return chPos > 100 
				&& vola > 65
				&& askBarRangeAvg > 1
				&& (askBar.getClose() - askBar.getOpen()) / (askBar.getHigh() - askBar.getLow()) > 0.65;
	}

	protected SignalDesc barsSignalGiven(List<IBar> bars, Instrument instrument) {
		double avgBarSize, avgBodySize;
		double[]	
			barSizes = new double[3],
			barBodys = new double[3];
		int i = 0;
		boolean barSizeOK = false;
		BodyDirection prevBodyDirection = BodyDirection.NONE;
		RollingAverage average = barRangeAverages.get(instrument);
		for (IBar currBar : bars) {
			BodyDirection currBodyDirection = currBar.getClose() > currBar.getOpen() ? BodyDirection.UP : BodyDirection.DOWN;
			if (!prevBodyDirection.equals(BodyDirection.NONE)) {
				if (!currBodyDirection.equals(prevBodyDirection))
					return null;
			}
			barSizes[i] = (currBar.getHigh() - currBar.getLow()) / average.getAverage();
			barBodys[i] = Math.abs(currBar.getClose() - currBar.getOpen()) / (currBar.getHigh() - currBar.getLow());
			if (!barSizeOK) // one big candle in trade direction enough
				barSizeOK = (currBar.getHigh() - currBar.getLow()) > average.getAverage() 
							|| barBodys[i] > 0.6;
			prevBodyDirection = currBodyDirection;
			i++;
		}
		if (barSizeOK) {
			return new SignalDesc(prevBodyDirection, barSizes, barBodys);	
		}
		return null;
	}

	/*
	 * Moves SL to the oposite extreme of the last candle if there is
	 * - profitable trade (if price moved above entry leave the original SL - let the trade "breathe")
	 * - strong candle opposite the trade (range above average and body > 80% of range)
	 * (possible refinement: in addition any strong reversal 1- or 2-bar candle pattern, with same range criteria)
	 * - but only if there is no more favourable OS/OB in Stoch 
	 */
	@Override
	public void inTradeProcessing(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter,
			IOrder order, Map<String, FlexTAValue> taValues, List<TAEventDesc> marketEvents) throws JFException {
		super.inTradeProcessing(instrument, period, askBar, bidBar, filter, order, taValues, marketEvents);
		// analyze the overall situation. If favourable don't do anything !
		TechnicalSituation taSituation = taValues.get(FlexTASource.TA_SITUATION).getTehnicalSituationValue();
		if (order.isLong()
			&& (taSituation.taSituation.equals(TechnicalSituation.OverallTASituation.BULLISH)
				|| taSituation.smiState.equals(Momentum.SMI_STATE.BULLISH_OVERBOUGHT_BOTH)
				|| taSituation.stochState.equals(Momentum.STOCH_STATE.BULLISH_OVERBOUGHT_BOTH)))
			return;
		if (!order.isLong()
			&& (taSituation.taSituation.equals(TechnicalSituation.OverallTASituation.BEARISH)
				|| taSituation.smiState.equals(Momentum.SMI_STATE.BEARISH_OVERSOLD_BOTH)
				|| taSituation.stochState.equals(Momentum.STOCH_STATE.BEARISH_OVERSOLD_BOTH)))
			return;
		
		// also check the opposite signal ! Close the trade if profitable, otherwise let SL work !
		if (((order.isLong() && bidBar.getLow() > order.getOpenPrice())
			|| (!order.isLong() && askBar.getHigh() < order.getOpenPrice()))
			&& oppositeSignal(order, marketEvents, taValues)) {
			lastTradingEvent = "opposite signal !";
			order.close();
			order.waitForUpdate(null);
			return;
		}
		
		RollingAverage average = barRangeAverages.get(instrument);
		double[][] stochs = taValues.get(FlexTASource.STOCH).getDa2DimValue();
		double 
			fastStoch = stochs[0][1], 
			slowStoch = stochs[1][1]; 
		if (order.isLong()) {
			if ((fastStoch >= 80 && slowStoch >= 80)
				|| bidBar.getClose() < order.getOpenPrice())
				return;
			
			double barRange = bidBar.getHigh() - bidBar.getLow();
			if (((bidBar.getClose() < bidBar.getOpen()
				&& barRange > average.getAverage()
				&& Math.abs(bidBar.getOpen() - bidBar.getClose()) / barRange > 0.8)
				|| (taSituation.taSituation.equals(TechnicalSituation.OverallTASituation.BEARISH)
					&& taSituation.taReason.equals(TechnicalSituation.TASituationReason.MOMENTUM))) 
				&& bidBar.getLow() > order.getOpenPrice()
				&& bidBar.getLow() > order.getStopLossPrice()) {
				lastTradingEvent = "CandleImpuls move SL signal (long)";
				order.setStopLossPrice(bidBar.getLow(), OfferSide.BID);
			}
		} else {
			if ((fastStoch <= 20 && slowStoch <= 20)
				|| askBar.getClose() > order.getOpenPrice())
					return;
			
				double barRange = askBar.getHigh() - askBar.getLow();
				if (((askBar.getClose() > askBar.getOpen()
					&&  barRange > average.getAverage()
					&& Math.abs(askBar.getOpen() - askBar.getClose()) / barRange > 0.8)
					|| (taSituation.taSituation.equals(TechnicalSituation.OverallTASituation.BEARISH)
						&& taSituation.taReason.equals(TechnicalSituation.TASituationReason.MOMENTUM))) 
					&& askBar.getHigh() < order.getOpenPrice()
					&& askBar.getHigh() < order.getStopLossPrice()) {
					lastTradingEvent = "CandleImpuls move SL signal (short)";
					order.setStopLossPrice(askBar.getHigh(), OfferSide.ASK);
				}
		}
		
	}


	protected boolean oppositeSignal(IOrder order, List<TAEventDesc> marketEvents, Map<String, FlexTAValue> taValues) {
		for (TAEventDesc event : marketEvents) {
			if (event.eventType.equals(TAEventType.ENTRY_SIGNAL)
				&& event.eventName.equals(getName())
				&& order.isLong() != event.isLong)
				return true;
		}
		return false;
	}


	@Override
	public IOrder submitOrder(String label, Instrument instrument, boolean isLong, double amount, IBar bidBar, IBar askBar) throws JFException {
		return submitMktOrder(label, instrument, isLong, amount, bidBar, askBar, lastEntryDesc.stopLossLevel);
	}


	@Override
	public void afterTradeReset(Instrument instrument) {
		super.afterTradeReset(instrument);
		lastEntryDesc = null;
	}


	@Override
	public void updateOnBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) {
		super.updateOnBar(instrument, period, askBar, bidBar);
		RollingAverage average = barRangeAverages.get(instrument);
		average.calcUpdatedAverage(bidBar.getHigh() - bidBar.getLow());
	}
	
	

}
