package jforex.strategies;

import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
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
import jforex.techanalysis.TradeTrigger.TriggerDesc;
import jforex.techanalysis.TradeTrigger.TriggerType;
import jforex.utils.FXUtils;

public class Simple1BarFollow extends BasicTAStrategy implements IStrategy {

	private static final int STAT_LENGTH = 1000; // TODO: treba biti zavisna od
													// TF ?
	protected Period usedTimeFrame = Period.FIVE_MINS;
	private Instrument selectedInstrument = Instrument.EURUSD;

	IOrder positionOrder = null;
	boolean waitingOrder = false, openPosition = false;
	double prevMAsDistance = Double.POSITIVE_INFINITY, nextStopLossLevel = 0.0;
	Queue<Double> last3MAsDistances = new LinkedList<Double>();

	public enum MAsDistanceChange {
		INIT, RAISING, FALLING, NON_FALLING, NON_RAISING, FLAT, TICKED_UP, TICKED_DOWN
	};

	MAsDistanceChange directionOfMAsDistanceChange = MAsDistanceChange.INIT;
	int noOfBarsInTrade = -1;
	private int orderCounter = 0;

	public Simple1BarFollow(Properties props) {
		super(props);
	}

	@Override
	public void onStart(IContext context) throws JFException {
		onStartExec(context);
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar,
			IBar bidBar) throws JFException {
		if (!tradingAllowed(bidBar.getTime()))
			return;
		if (!period.equals(usedTimeFrame))
			return;

		if (positionOrder != null
				&& positionOrder.getState().equals(IOrder.State.FILLED)) {
			// already in position
			noOfBarsInTrade++;
			updateMAsDistanceDirection(bidBar);
			if (noOfBarsInTrade > 2) {
				List<IBar> twoBarsBackList = history.getBars(
						selectedInstrument, usedTimeFrame,
						positionOrder.isLong() ? OfferSide.BID : OfferSide.ASK,
						Filter.WEEKENDS, 3, bidBar.getTime(), 0);
				IBar twoBarsBack = twoBarsBackList.get(0);
				if (positionOrder.isLong()) {
					if (twoBarsBack.getLow() > nextStopLossLevel) {
						nextStopLossLevel = twoBarsBack.getLow();
						// check if trend is gaining strength i.e. MAs
						// difference is raising
						// in that case don't move the SL
						if (// directionOfMAsDistanceChange ==
							// MAsDistanceChange.FALLING
						(fallingMAsDistanceChange() && !favourableBullishSituation(
								bidBar, askBar))
								|| !favourableBullishSituation(bidBar, askBar)
								|| profitExtreme(askBar)) {
							ITick lastPrice = history.getLastTick(instrument);
							if (lastPrice.getBid() > nextStopLossLevel) {
								positionOrder.setStopLossPrice(twoBarsBack
										.getLow());
							} else {
								// market is already too low compared to the
								// last SL value - close on market !!
								positionOrder.close();
							}
						}
					}
				} else {
					if (twoBarsBack.getHigh() < nextStopLossLevel) {
						nextStopLossLevel = twoBarsBack.getHigh();
						if (// directionOfMAsDistanceChange ==
							// MAsDistanceChange.FALLING
						(fallingMAsDistanceChange() && !favourableBearishSituation(
								askBar, bidBar))
								|| !favourableBearishSituation(askBar, bidBar)
								|| profitExtreme(askBar)) {
							ITick lastPrice = history.getLastTick(instrument);
							if (lastPrice.getAsk() < nextStopLossLevel) {
								positionOrder.setStopLossPrice(twoBarsBack
										.getHigh());
							} else {
								// market is already too high compared to the
								// last SL value - close on market !!
								positionOrder.close();
							}
						}
					}
				}
			}
			return;
		}

		// at least average bar range, probably needs increasing
		if (tradeTrigger.barLengthStatPos(instrument, period, OfferSide.BID,
				bidBar, STAT_LENGTH) < 0)
			return;
		boolean bullishSignal = false, bearishSignal = false;
		TriggerDesc signalDesc = null;

		bullishSignal = !strongDownTrend(askBar, bidBar)
				&& (tradeTrigger.bullishMaribouzuBar(instrument, period,
						OfferSide.ASK, askBar.getTime()) != null || (signalDesc = tradeTrigger
						.bullishReversalCandlePatternDesc(instrument, period,
								OfferSide.ASK, askBar.getTime())) != null
						&& signalDesc.type == TriggerType.BULLISH_1_BAR
						&& askBar.getClose() > askBar.getOpen());

		bearishSignal = !bullishSignal
				&& !strongUpTrend(bidBar, askBar)
				&& (tradeTrigger.bearishMaribouzuBar(instrument, period,
						OfferSide.BID, bidBar.getTime()) != Double.MAX_VALUE || (signalDesc = tradeTrigger
						.bearishReversalCandlePatternDesc(instrument, period,
								OfferSide.ASK, bidBar.getTime())) != null
						&& signalDesc.type == TriggerType.BEARISH_1_BAR
						&& bidBar.getClose() < bidBar.getOpen());
		if (bullishSignal) {
			if (waitingOrder) {
				positionOrder.close();
			}
			placeBullishOrder(askBar, bidBar);
		} else if (bearishSignal) {
			if (waitingOrder) {
				positionOrder.close();
			}
			placeBearishOrder(bidBar, askBar);
		}
	}

	private boolean profitExtreme(IBar currBar) throws JFException {
		double[] dailyATRs = indicators.atr(selectedInstrument, usedTimeFrame,
				positionOrder.isLong() ? OfferSide.BID : OfferSide.ASK, 14,
				Filter.WEEKENDS, 10, currBar.getTime(), 0);
		double avgDailyATR = FXUtils.average(dailyATRs)
				* Math.pow(10, selectedInstrument.getPipScale());
		return positionOrder.getProfitLossInPips() > 2 * avgDailyATR;
	}

	private boolean fallingMAsDistanceChange() {
		double[] queueValues = new double[3];
		int i = 2;
		for (Double curr : last3MAsDistances) {
			queueValues[i--] = curr.doubleValue();
		}
		return queueValues[0] < queueValues[1]
				&& queueValues[1] < queueValues[2];
	}

	private boolean strongDownTrend(IBar askBar, IBar bidBar)
			throws JFException {
		int trendId = trendDetector.getTrendId(selectedInstrument,
				usedTimeFrame, OfferSide.ASK, AppliedPrice.CLOSE,
				askBar.getTime());
		double trendStrength = trendDetector.getMAsMaxDiffStDevPos(
				selectedInstrument, usedTimeFrame, OfferSide.ASK,
				AppliedPrice.CLOSE, bidBar.getTime(), STAT_LENGTH);
		return trendId == 1 && trendStrength > 1;
	}

	private boolean favourableBearishSituation(IBar askBar, IBar bidBar)
			throws JFException {
		double ma20 = indicators.sma(selectedInstrument, usedTimeFrame,
				OfferSide.ASK, AppliedPrice.CLOSE, 20, Filter.WEEKENDS, 1,
				askBar.getTime(), 0)[0];
		int trendId = trendDetector.getTrendId(selectedInstrument,
				usedTimeFrame, OfferSide.ASK, AppliedPrice.CLOSE,
				askBar.getTime());
		double trendStrength = trendDetector.getMAsMaxDiffStDevPos(
				selectedInstrument, usedTimeFrame, OfferSide.ASK,
				AppliedPrice.CLOSE, bidBar.getTime(), STAT_LENGTH);
		return askBar.getClose() <= ma20 && trendId == 1 && trendStrength > 0;
	}

	private boolean strongUpTrend(IBar bidBar, IBar askBar) throws JFException {
		int trendId = trendDetector.getTrendId(selectedInstrument,
				usedTimeFrame, OfferSide.BID, AppliedPrice.CLOSE,
				askBar.getTime());
		double trendStrength = trendDetector.getMAsMaxDiffStDevPos(
				selectedInstrument, usedTimeFrame, OfferSide.BID,
				AppliedPrice.CLOSE, bidBar.getTime(), STAT_LENGTH);
		return trendId == 6 && trendStrength > 1;
	}

	private boolean favourableBullishSituation(IBar bidBar, IBar askBar)
			throws JFException {
		double ma20 = indicators.sma(selectedInstrument, usedTimeFrame,
				OfferSide.BID, AppliedPrice.CLOSE, 20, Filter.WEEKENDS, 1,
				askBar.getTime(), 0)[0];
		int trendId = trendDetector.getTrendId(selectedInstrument,
				usedTimeFrame, OfferSide.BID, AppliedPrice.CLOSE,
				askBar.getTime());
		double trendStrength = trendDetector.getMAsMaxDiffStDevPos(
				selectedInstrument, usedTimeFrame, OfferSide.BID,
				AppliedPrice.CLOSE, bidBar.getTime(), STAT_LENGTH);
		return bidBar.getClose() >= ma20 && trendId == 6 && trendStrength > 0;
	}

	private void updateMAsDistanceDirection(IBar bidBar) throws JFException {
		double currMAsDistance = trendDetector.getMAsMaxDiffStDevPos(
				selectedInstrument, usedTimeFrame,
				positionOrder.isLong() ? OfferSide.BID : OfferSide.ASK,
				AppliedPrice.CLOSE, bidBar.getTime(), STAT_LENGTH);
		if (prevMAsDistance == Double.POSITIVE_INFINITY) {
			directionOfMAsDistanceChange = MAsDistanceChange.INIT;
		} else {
			if (currMAsDistance > prevMAsDistance) {
				directionOfMAsDistanceChange = MAsDistanceChange.RAISING;
			} else if (currMAsDistance == prevMAsDistance) {
				if (directionOfMAsDistanceChange
						.equals(MAsDistanceChange.RAISING))
					directionOfMAsDistanceChange = MAsDistanceChange.NON_FALLING;
				else if (directionOfMAsDistanceChange
						.equals(MAsDistanceChange.FALLING))
					directionOfMAsDistanceChange = MAsDistanceChange.NON_RAISING;
			} else {
				directionOfMAsDistanceChange = MAsDistanceChange.FALLING;
			}
		}
		prevMAsDistance = currMAsDistance;

		if (last3MAsDistances.size() == 3)
			last3MAsDistances.remove();
		last3MAsDistances.add(new Double(currMAsDistance));
	}

	private void placeBearishOrder(IBar bidBar, IBar askBar) throws JFException {
		positionOrder = engine.submitOrder(getOrderLabel(), selectedInstrument,
				OrderCommand.SELLSTOP, 0.1, bidBar.getLow(), 5.0,
				askBar.getHigh(), 0.0);
	}

	private void placeBullishOrder(IBar askBar, IBar bidBar) throws JFException {
		positionOrder = engine.submitOrder(getOrderLabel(), selectedInstrument,
				OrderCommand.BUYSTOP, 0.1, askBar.getHigh(), 5.0,
				bidBar.getLow(), 0.0);
	}

	private String getOrderLabel() {
		String res = new String(getStrategyName() + "_"
				+ selectedInstrument.name() + "_"
				+ FXUtils.if3.format(++orderCounter));
		return res;
	}

	@Override
	public void onMessage(IMessage message) throws JFException {
		if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {
			// might be cancel of unfilled order or SL close
			/*
			 * Set<Reason> closeReasons = message.getReasons(); for (Reason r :
			 * closeReasons) { if (r.equals(Reason.ORDER_CLOSED_BY_SL))
			 * 
			 * }
			 */
			resetVars();
			return;
		}
		if (message.getType().equals(IMessage.Type.ORDER_SUBMIT_OK)) {
			waitingOrder = true;
		}
		if (message.getType().equals(IMessage.Type.ORDER_FILL_OK)) {
			nextStopLossLevel = positionOrder.getStopLossPrice();
		}

	}

	@Override
	public void onAccount(IAccount account) throws JFException {
	}

	@Override
	public void onStop() throws JFException {
		onStopExec();
	}

	protected void resetVars() {
		prevMAsDistance = Double.POSITIVE_INFINITY;
		nextStopLossLevel = 0.0;
		directionOfMAsDistanceChange = MAsDistanceChange.INIT;
		noOfBarsInTrade = -1;
		waitingOrder = false;
	}

	@Override
	protected String getStrategyName() {
		return "Simple1BarFollow";
	}

	@Override
	protected String getReportFileName() {
		return "Simple1BarFollow_Report_";
	}

}
