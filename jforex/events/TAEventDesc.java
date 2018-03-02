package jforex.events;

import jforex.techanalysis.TradeTrigger;

import com.dukascopy.api.IBar;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.Period;

public class TAEventDesc {

	public enum TAEventType {
		NONE, ENTRY_SIGNAL, EXIT_SIGNAL, ENTRY_CANCEL_SIGNAL, TA_EVENT, DAILY_PNL_INFO, MAX_TRADE_PROFIT_IN_PERC
	}
	public static String
		MAX_TRADE_PROFIT_IN_PERC = "MaxTradeProfitInPerc";

	public TAEventType eventType;
	public Instrument instrument;
	public String eventName;
	public boolean isLong;
	public IBar askBar, bidBar;
	public Period timeFrame;
	public TradeTrigger.TriggerDesc candles;
	public double 
		channelPosition, 
		stopLossLevel, takeProfitLevel,
		bBandsSqueezePerc, maDistancePerc,
		pnlDayRangeRatio = 0, avgPnLRange = 0, tradeProfitInPerc = 0;

	public TAEventDesc(TAEventType eventType, String eventName,	Instrument instrument, boolean isLong, IBar askBar, IBar bidBar, Period timeFrame) {
		super();
		this.eventType = eventType;
		this.eventName = eventName;
		this.instrument = instrument;
		this.isLong = isLong;
		this.askBar = askBar;
		this.bidBar = bidBar;
		this.timeFrame = timeFrame;
	}

}
