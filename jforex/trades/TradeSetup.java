package jforex.trades;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public abstract class TradeSetup implements ITradeSetup {
	protected IIndicators indicators = null;
	protected IHistory history = null;
	protected IEngine engine = null;
	protected IOrder order = null;
	
	protected boolean locked = false;

	public TradeSetup(IIndicators indicators, IHistory history, IEngine engine) {
		super();
		this.indicators = indicators;
		this.history = history;
		this.engine = engine;
	}
	
	@Override
	public EntryDirection checkCancel(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter) throws JFException {
		return ITradeSetup.EntryDirection.NONE;
	}


	@Override
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order) throws JFException { }

	@Override
	public void log(IMessage message) {	}

	@Override
	public void takeTradingOver(IOrder order) {
		this.order = order;		
	}

	@Override
	public void afterTradeReset(Instrument instrument) { }


	@Override
	public EntryDirection checkExit(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, IOrder order) throws JFException {
			return ITradeSetup.EntryDirection.NONE;
	}

	@Override
	public EntryDirection checkTakeOver(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter) throws JFException {
		return ITradeSetup.EntryDirection.NONE;
	}

	@Override
	public boolean isTradeLocked(Instrument instrument, Period period, IBar askBar,	IBar bidBar, Filter filter, IOrder order) throws JFException {
		return locked;
	}

	@Override
	public IOrder submitOrder(String label, Instrument instrument,	boolean isLong, double amount, IBar bidBar, IBar askBar) throws JFException {
        return engine.submitOrder(label, instrument, isLong ? IEngine.OrderCommand.BUY : IEngine.OrderCommand.SELL, amount);
	}

}
