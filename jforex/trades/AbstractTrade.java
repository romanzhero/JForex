package jforex.trades;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

import jforex.entries.IEntry;
import jforex.profittakers.IProfitTaker;
import jforex.stopmovers.IStopMover;

public abstract class AbstractTrade {
	protected IEntry entry;
	protected IStopMover stopMover;
	protected IProfitTaker profitTaker;

	public abstract void onStartExec(IContext context) throws JFException;

	public abstract void onBar(Instrument instrument, Period period,
			IBar askBar, IBar bidBar) throws JFException;

}
