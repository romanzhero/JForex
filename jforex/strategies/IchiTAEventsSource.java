package jforex.strategies;

import trading.elements.ITAEvent;
import trading.elements.ITAEventSource;
import trading.elements.ITradeState;
import trading.elements.TimeFrames;
import trading.events.ichi.IchiCancel;
import trading.events.ichi.IchiCloudBreakout;
import trading.strategies.IchiForexStrategy;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.Period;

import jforex.techanalysis.TAEventsSource;
import jforex.techanalysis.Trend;
import jforex.utils.FXUtils;

public class IchiTAEventsSource extends TAEventsSource implements ITAEventSource {

	public IchiTAEventsSource(IHistory history, IIndicators indicators) {
		super(history, indicators);
	}

	@Override
	public ITAEvent get(String ticker, String eventName, TimeFrames timeFrame, long time) {
		Instrument inst = Instrument.fromString(ticker);
		Period 
			period = TimeFramesMap.toJForexMap.get(timeFrame),
			higherTF = FXUtils.timeFramesHigherTFPeriodMap.get(period);

		if (eventName.equals(IchiForexStrategy.IchiEvents.ENTRY.toString())) {
			try {
				Trend.ICHI_CLOUD_CROSS ichiCloudBreakout = trendDetector.isIchiCloudCross(history, inst, period, OfferSide.BID, AppliedPrice.CLOSE, time);
				if (ichiCloudBreakout != Trend.ICHI_CLOUD_CROSS.NONE) {
					IBar bar = history.getBars(inst, period, OfferSide.BID, Filter.WEEKENDS, 1, time, 0).get(0);
			        long prevFinishedPeriodHigherTF = history.getPreviousBarStart(higherTF, time);
			        double 
			        	ATR = Math.round(vola.getATR(inst, period, OfferSide.BID, time, 14) * Math.pow(10, inst.getPipScale())),
			        	dollarATR = vola.getATR(inst, higherTF, OfferSide.BID, prevFinishedPeriodHigherTF, 14) * Math.pow(10, inst.getPipScale());

					boolean isLong = ichiCloudBreakout == Trend.ICHI_CLOUD_CROSS.BULLISH;
					ITAEvent res = new IchiCloudBreakout(isLong, isLong ? bar.getHigh() : bar.getLow(), (bar.getHigh() - bar.getLow()) / 2, time, 
							ATR / Math.pow(10, inst.getPipScale()),
							inst.getPipScale() == 2 ? dollarATR / Math.pow(10, inst.getPipScale()) / 100 : dollarATR / Math.pow(10, inst.getPipScale()),
							bar.getOpen(), bar.getLow(), bar.getHigh(), bar.getClose());
					res.roundSL(inst.getPipScale()); // needed for Duka engine otherwise order gets rejected
					return res;
				}
				else 
					return null;
			} catch (JFException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}		
		}
		
		if (eventName.equals(IchiForexStrategy.IchiEvents.CANCEL_lONG.toString())) {
			try {
				if (trendDetector.isIchiLongCancel(history, inst, period, OfferSide.BID, AppliedPrice.CLOSE, time)) {
					return new IchiCancel(true);
				}
			} catch (JFException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}
		
		if (eventName.equals(IchiForexStrategy.IchiEvents.CANCEL_SHORT.toString())) {
			try {
				if (trendDetector.isIchiShortCancel(history, inst, period, OfferSide.BID, AppliedPrice.CLOSE, time)) {
					return new IchiCancel(false);
				}
			} catch (JFException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			return null;
		}
		return null;
	}

	@Override
	public ITAEvent get(String ticker, String eventName, TimeFrames timeFrame,	long time, ITradeState relevantState) {
		if (eventName.equals(IchiForexStrategy.IchiEvents.REENTRY.toString())) {
		}
		return null;
	}

	@Override
	public double getValue(String ticker, String eventName,	TimeFrames timeFrame, long time) {
		Instrument inst = Instrument.fromString(ticker);
		Period 
			period = TimeFramesMap.toJForexMap.get(timeFrame);

		if (eventName.equals(IchiForexStrategy.IchiValues.ATR.toString())) {
			try {
				return Math.round(vola.getATR(inst, period, OfferSide.BID, time, 14) * Math.pow(10, inst.getPipScale())) / Math.pow(10, inst.getPipScale());
			} catch (JFException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}		
		}
		if (eventName.equals(IchiForexStrategy.IchiValues.ATR_DOLLAR.toString())) {
			try {
				Period higherTF = FXUtils.timeFramesHigherTFPeriodMap.get(period);
		        long prevFinishedPeriodHigherTF = history.getPreviousBarStart(higherTF, time);
	        	double dollarATR = vola.getATR(inst, higherTF, OfferSide.BID, prevFinishedPeriodHigherTF, 14) * Math.pow(10, inst.getPipScale());
				return inst.getPipScale() == 2 ? dollarATR / Math.pow(10, inst.getPipScale()) / 100 : dollarATR / Math.pow(10, inst.getPipScale());
				
			} catch (JFException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}		
		}
		return -1.0;
	}

}
