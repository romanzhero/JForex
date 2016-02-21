package jforex.utils;

import com.dukascopy.api.Instrument;
import com.dukascopy.api.Period;

public class SignalGeneratorResponse {

	public Instrument pair;
	public Period timeframe;
	public long barTime;
	public String[] signals = null, emailRecepients = null;
	public String emailSubject = null, emailBody = null;

	public SignalGeneratorResponse(Instrument pair, Period timeframe,
			long barTime) {
		super();
		this.pair = pair;
		this.timeframe = timeframe;
		this.barTime = barTime;
	}

}
