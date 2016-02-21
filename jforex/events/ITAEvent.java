package jforex.events;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public interface ITAEvent {
	public String getName();

	public TAEventDesc checkEvent(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter) throws JFException;

}
