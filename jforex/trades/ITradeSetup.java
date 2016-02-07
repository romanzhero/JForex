package jforex.trades;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public interface ITradeSetup {
	
	public enum EntryDirection {NONE, LONG, SHORT}

	public String getName();
	
	public EntryDirection checkEntry(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter) throws JFException;
	public EntryDirection checkTakeOver(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter) throws JFException;
	public IOrder submitOrder(String label, Instrument instrument, boolean isLong, double amount, IBar bidBar, IBar askBar) throws JFException;
	
	public EntryDirection checkCancel(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter) throws JFException;
	public EntryDirection checkExit(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, IOrder order) throws JFException;
	
	public void takeTradingOver(IOrder order);
	public void inTradeProcessing(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, IOrder order) throws JFException;
	// under which conditions this setup will not allow others to take over the trade at all
	public boolean isTradeLocked(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, IOrder order) throws JFException;

	public void afterTradeReset(Instrument instrument);
	
	public void log(IMessage message);	
}
