package jforex.filters;

import jforex.techanalysis.Channel;

import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.AppliedPrice;

public class ConditionalFilterChannelPosADX extends ConditionalFilterOnADX
		implements IConditionalFilter {

	@Override
	public IFilter cloneFilter() {
		return new ConditionalFilterChannelPosADX();
	}

	@Override
	public IConditionalFilter cloneConditionalFilter() {
		return new ConditionalFilterChannelPosADX();
	}

	@Override
	protected void setMainFilter() {
		mainFilter = new ChannelPosFilter();
	}

	public boolean check(Instrument instrument, OfferSide side,
			AppliedPrice appliedPrice, long time) throws JFException {
		// must ensure that conditional filter gets correct time. Possible
		// situations:
		// 1. both timeframes same - no action needed
		// 2. conditional timeframe higher then main - calculate start of the
		// previous completed bar for CONDITIONAL period !
		// 3. conditional timeframe lower then main - calculate start of the
		// previous completed bar for MAIN period !
		long condTime = time, mainTime = time;
		// TODO: make a comparable wrapper for Dukascopy Period so all
		// timeframes can be used
		if (condPeriod == Period.FOUR_HOURS)
			condTime = history.getPreviousBarStart(condPeriod, time);
		if (period == Period.FOUR_HOURS)
			mainTime = history.getPreviousBarStart(period, time);
		double condFilterValue = calcCondFilterValue(instrument, side,
				appliedPrice, condTime);
		for (int i = 0; i < mainMin.length; i++) {
			// find condition range which applies
			if (condFilterValue >= conditionMin[i]
					&& condFilterValue <= conditionMax[i]) {
				Channel ch = new Channel(history, indicators);
				// double[] last2BarsChannelPos =
				// ch.bullishTriggerChannelStats(instrument, period, side,
				// mainTime);
				// if (last2BarsChannelPos[1] >= mainMax[i])
				// return false;
				double triggerBarLowChannelPos = ch.priceChannelPos(instrument,
						period, side, (long) auxParams[0], auxParams[1]);
				if (triggerBarLowChannelPos >= mainMax[i])
					return false;
			}
		}
		// If none of conditional ranges applies, filter is irrelevant and check
		// passes on with filtering by returning true
		return true;
	}
}
