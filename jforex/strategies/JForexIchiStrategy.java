package jforex.strategies;

import java.util.Properties;

import trading.elements.IBrokerEvent;
import trading.elements.ITAEventSource;
import trading.elements.ITradingStrategy;
import trading.elements.TimeFrames;
import trading.strategies.IchiForexStrategy;
import jforex.BasicStrategy;
import jforex.utils.FXUtils;

import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public class JForexIchiStrategy extends BasicStrategy implements IStrategy {
	
	protected ITradingStrategy strategy = null;
	protected ITAEventSource taEventsSource = null;

	public JForexIchiStrategy() {
		super();
	}

	public JForexIchiStrategy(Properties props) {
		super(props);
	}

	@Override
	public void onStart(IContext context) throws JFException {
		super.onStartExec(context);
		strategy = new IchiForexStrategy(3, new JForexBrokerEngine(engine, context.getAccount()), TimeFrames.FOUR_HOURS);
		strategy.setLogger(log);
		taEventsSource = new IchiTAEventsSource(history, indicators); 
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (!tradingAllowed(bidBar.getTime()))
			return;
		if (skipPairs.contains(instrument))
			return;
		String 
			targetTimeFrame = pairsTimeFrames.get(instrument),
			currentTimeFrame = FXUtils.timeFrameNamesMap.get(period.toString());
		if (currentTimeFrame == null || !currentTimeFrame.equals(targetTimeFrame))
			return;
		
		strategy.processTAEvent(instrument.toString(), TimeFramesMap.fromJForexMap.get(period), bidBar.getTime(), taEventsSource);
	}

	@Override
	public void onMessage(IMessage message) throws JFException {
		//TODO: next task is to distinguish between 1st and other fills...
        if (message.getType().equals(IMessage.Type.ORDER_FILL_OK)) {
        	IBrokerEvent event = new OrderFillEvent(message.getOrder().getInstrument().toString(), message.getOrder().getLabel(), message.getCreationTime(), message.getOrder().getAmount(), message.getOrder().getOpenPrice());
        	strategy.processBrokerEvent(message.getOrder().getInstrument().toString(), event, taEventsSource);
        	return;
        }
        
        if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {
        	IBrokerEvent event = new OrderSLCloseEvent(message.getOrder().getInstrument().toString(), message.getOrder().getLabel(), message.getCreationTime());
        	strategy.processBrokerEvent(message.getOrder().getInstrument().toString(), event, taEventsSource);
        	return;
        }
        
	}

	@Override
	public void onAccount(IAccount account) throws JFException {
		// TODO Auto-generated method stub

	}

	@Override
	public void onStop() throws JFException {
		super.onStopExec();
	}

	@Override
	protected String getStrategyName() {
		return "IchiJForexStrategy";
	}

	@Override
	protected String getReportFileName() {
		return getStrategyName() + "_";
	}

}
