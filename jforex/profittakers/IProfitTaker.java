package jforex.profittakers;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public interface IProfitTaker {
	public boolean isLong();

	public boolean signalFoundBool(Instrument instrument, Period period,
			IBar askBar, IBar bidBar) throws JFException;

	public double signalFound(Instrument instrument, Period period,
			IBar askBar, IBar bidBar) throws JFException;

	public void onStartExec(IContext context) throws JFException;
}
