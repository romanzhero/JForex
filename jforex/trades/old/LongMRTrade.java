package jforex.trades.old;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

import jforex.entries.LongMREntry;
import jforex.profittakers.LongMRProfitTaker;
import jforex.stopmovers.LongMRStopMover;

public class LongMRTrade extends AbstractTrade {
	public LongMRTrade() {
		entry = new LongMREntry();
		stopMover = new LongMRStopMover();
		profitTaker = new LongMRProfitTaker();
	}

	@Override
	public void onStartExec(IContext context) throws JFException {
		entry.onStartExec(context);
		stopMover.onStartExec(context);
		profitTaker.onStartExec(context);
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar,
			IBar bidBar) throws JFException {
		// TODO Auto-generated method stub

	}
}
