package jforex.filters;

import jforex.techanalysis.Channel;

import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.IIndicators.AppliedPrice;

public class ChannelPosFilter extends AbstractSimpleFilter implements IFilter {

	private static int TIME = 0;
	private static int PRICE = 1;

	@Override
	public IFilter cloneFilter() {
		return new ChannelPosFilter();
	}

	@Override
	protected double calcIndicator(Instrument instrument, OfferSide side,
			AppliedPrice appliedPrice, long time) throws JFException {
		Channel ch = new Channel(null, indicators);
		return ch.priceChannelPos(instrument, period, side,
				(long) auxParams[TIME], auxParams[PRICE]);
	}

}
