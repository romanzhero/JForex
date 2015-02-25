package jforex.profittakers;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.AppliedPrice;

public class LongMRProfitTaker implements IProfitTaker {

	final protected Period basicTimeFrame = Period.THIRTY_MINS;
	final protected Period higherTimeFrame = Period.FOUR_HOURS;

	protected IContext context;

	@Override
	public boolean isLong() {
		return true;
	}

	@Override
	public boolean signalFoundBool(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		return signalFound(instrument, period, askBar, bidBar) != Double.MIN_VALUE;
	}

	@Override
	public double signalFound(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (!period.equals(basicTimeFrame))
			return Double.MIN_VALUE;

		if (context.getIndicators().rsi(instrument, higherTimeFrame, OfferSide.BID, AppliedPrice.CLOSE, 14, Filter.WEEKENDS, 1, bidBar.getTime(), 0)[0] > 70.0)
			return bidBar.getLow();
		else
			return Double.MIN_VALUE;
	}

	@Override
	public void onStartExec(IContext context) throws JFException {
		this.context = context;		
	}

}
