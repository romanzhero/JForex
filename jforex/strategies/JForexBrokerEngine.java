package jforex.strategies;

import java.util.HashMap;
import java.util.Map;

import com.dukascopy.api.IAccount;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;

import trading.elements.IBrokerEngine;

public class JForexBrokerEngine implements IBrokerEngine {

	protected IEngine engine = null;
	protected IAccount account = null;

	protected Map<String, Map<String, IOrder>> orders = new HashMap<String, Map<String, IOrder>>();

	public JForexBrokerEngine(IEngine pEngine, IAccount pAccount) {
		engine = pEngine;
		account = pAccount;
	}

	@Override
	public String submitBuyStpOrder(String ticker, String ID, double amount,
			double stp, double sl, double tp) {
		try {
			// amounts are in millions !
			IOrder o = engine.submitOrder(ID, Instrument.fromString(ticker),
					OrderCommand.BUYSTOP, amount / 1000000, stp, 3, sl, tp);
			logOrder(ticker, ID, o);
		} catch (JFException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}
		return ID;
	}

	protected void logOrder(String ticker, String ID, IOrder o) {
		Map<String, IOrder> tickerOrders = orders.get(ticker);
		if (tickerOrders == null) {
			tickerOrders = new HashMap<String, IOrder>();
			orders.put(ticker, tickerOrders);
		}
		tickerOrders.put(ID, o);
	}

	@Override
	public String submitSellStpOrder(String ticker, String ID, double amount,
			double stp, double sl, double tp) {
		try {
			// amounts are in millions !
			IOrder o = engine.submitOrder(ID, Instrument.fromString(ticker),
					OrderCommand.SELLSTOP, amount / 1000000, stp, 3, sl, tp);
			// return engine.submitOrder(label, instrument, orderCmd, amount,
			// price, slippage, stopLossPrice, 0);
			logOrder(ticker, ID, o);
		} catch (JFException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}
		return ID;
	}

	@Override
	public void cancelOrder(String ticker, String orderID) {
		try {
			Map<String, IOrder> tickerOrders = orders.get(ticker);
			IOrder o = tickerOrders.get(orderID);
			o.close();
			tickerOrders.remove(o);
		} catch (JFException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}

	}

	@Override
	public double getEquity() {
		return account.getEquity();
	}

	@Override
	public void closeOrderMkt(String ticker, String orderID) {
		cancelOrder(ticker, orderID);
	}

}
