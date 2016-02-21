package jforex;

import java.util.Properties;
import java.util.StringTokenizer;

import jforex.trades.AbstractTrade;
import jforex.trades.LongMRTrade;

import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public class SinglePairSingleSetupStrategy extends BasicTAStrategy implements
		IStrategy {

	protected AbstractTrade trade;
	Instrument selectedPair;

	public SinglePairSingleSetupStrategy(Properties props) {
		super(props);
		trade = new LongMRTrade();
		StringTokenizer st = new StringTokenizer(props.getProperty(
				"pairsToCheck", "EUR/USD"), ";");
		assert (st.countTokens() == 1);

		String nextPair = st.nextToken();
		selectedPair = Instrument.fromString(nextPair);
	}

	@Override
	public void onStart(IContext context) throws JFException {
		trade.onStartExec(context);
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {
		// TODO Auto-generated method stub

	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar,
			IBar bidBar) throws JFException {
		if (!instrument.equals(selectedPair))
			return;

		trade.onBar(instrument, period, askBar, bidBar);
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
		return "SingleTradeStrategy";
	}

	@Override
	protected String getReportFileName() {
		return getStrategyName() + "_";
	}

}
