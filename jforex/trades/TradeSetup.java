package jforex.trades;

import java.util.List;

import org.omg.PortableInterceptor.IORInterceptorOperations;

import jforex.events.TAEventDesc;
import jforex.utils.FXUtils;

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
	protected String lastTradingEvent = "none";

	public TradeSetup(IIndicators indicators, IHistory history, IEngine engine) {
		super();
		this.indicators = indicators;
		this.history = history;
		this.engine = engine;
	}

	@Override
	public EntryDirection checkCancel(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter) throws JFException {
		return ITradeSetup.EntryDirection.NONE;
	}

	@Override
	public void inTradeProcessing(Instrument instrument, Period period,	IBar askBar, IBar bidBar, Filter filter, IOrder order, List<TAEventDesc> marketEvents) throws JFException {
	}

	@Override
	public void log(IMessage message) {
	}

	@Override
	public void takeTradingOver(IOrder order) {
		this.order = order;
	}

	@Override
	public void afterTradeReset(Instrument instrument) {
	}

	@Override
	public EntryDirection checkExit(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, IOrder order) throws JFException {
		return ITradeSetup.EntryDirection.NONE;
	}

	@Override
	public EntryDirection checkTakeOver(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter) throws JFException {
		return ITradeSetup.EntryDirection.NONE;
	}

	@Override
	public boolean isTradeLocked(Instrument instrument, Period period, IBar askBar, IBar bidBar, Filter filter, IOrder order) throws JFException {
		return locked;
	}

	@Override
	public IOrder submitOrder(String label, Instrument instrument, boolean isLong, double amount, IBar bidBar, IBar askBar)	throws JFException {
		return submitMktOrder(label, instrument, isLong, amount, bidBar, askBar);
	}
	
	public IOrder submitMktOrder(String label, Instrument instrument, boolean isLong, double amount, IBar bidBar, IBar askBar) throws JFException {
		IOrder order = engine.submitOrder(label, instrument, isLong ? IEngine.OrderCommand.BUY : IEngine.OrderCommand.SELL,	amount);
		//order.waitForUpdate(IOrder.State.FILLED);
		return order;
	}
	
	public IOrder submitStpOrder(String label, Instrument instrument, boolean isLong, double amount, IBar bidBar, IBar askBar, double stopLoss) throws JFException {
		double 
			stpPrice = isLong ? askBar.getHigh() : bidBar.getLow(),
			stopLossPrice = stopLoss;
		stpPrice = FXUtils.roundToPip(stpPrice, instrument);
		stopLossPrice = FXUtils.roundToPip(stopLossPrice, instrument);
		IOrder order = engine.submitOrder(label, instrument, isLong ? IEngine.OrderCommand.BUYSTOP: IEngine.OrderCommand.SELLSTOP, amount,	stpPrice, -1, stopLossPrice, 0);
		//order.waitForUpdate(IOrder.State.OPENED, IOrder.State.FILLED);
		return order;
	}

	@Override
	public String getLastTradingEvent() {
		return lastTradingEvent ;
	}


}
