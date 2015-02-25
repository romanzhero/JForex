package jforex;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Properties;

import jforex.utils.FXUtils;

import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;
import com.dukascopy.api.IOrder.State;

public class OrderChecker extends BasicStrategy implements IStrategy {

	public OrderChecker() {
		super();
	}

	public OrderChecker(Properties props) {
		super(props);
	}	

	@Override
	public void onStart(IContext context) throws JFException {
		super.onStartExec(context);
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {
		// TODO Auto-generated method stub

	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (!period.equals(Period.THIRTY_MINS))
			return;

		try {
			PrintWriter out = new PrintWriter(new FileWriter(instrument.toString().replace("/", "") + "_open_orders.txt"));
			List<IOrder> dukaOrders = context.getEngine().getOrders(instrument);
			int openDukaOrders = 0;
			for (IOrder currOrder : dukaOrders) {
				if (currOrder.getState().equals(State.FILLED)) {
					openDukaOrders++;
					out.println(instrument.toString() + ":" 
							+ (currOrder.isLong() ? "LONG" : "SHORT") + ":" 
							+ FXUtils.df1.format(currOrder.getProfitLossInPips()) + ":"
							+ FXUtils.df5.format(currOrder.getOpenPrice()) + ":"
							+ FXUtils.df5.format(currOrder.getStopLossPrice()) + ":"
							+ FXUtils.df5.format(currOrder.getTakeProfitPrice()));
				}
			}
			out.close();
		} catch (IOException e){
			e.printStackTrace();
			return;
		}
	}

	@Override
	public void onMessage(IMessage message) throws JFException {
		// TODO Auto-generated method stub

	}

	@Override
	public void onAccount(IAccount account) throws JFException {
		// TODO Auto-generated method stub

	}

	@Override
	public void onStop() throws JFException {
		// TODO Auto-generated method stub

	}

	@Override
	protected String getStrategyName() {
		return "OrderChecker";
	}

	@Override
	protected String getReportFileName() {
		return "OrderChecker_";
	}

}
