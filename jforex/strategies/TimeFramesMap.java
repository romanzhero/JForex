package jforex.strategies;

import java.util.HashMap;
import java.util.Map;

import trading.elements.TimeFrames;

import com.dukascopy.api.Period;

public class TimeFramesMap {

	public static Map<Period, TimeFrames> fromJForexMap = new HashMap<Period, TimeFrames>();

	static {
		fromJForexMap.put(Period.FOUR_HOURS, TimeFrames.FOUR_HOURS);
	}

	public static Map<TimeFrames, Period> toJForexMap = new HashMap<TimeFrames, Period>();

	static {
		toJForexMap.put(TimeFrames.FOUR_HOURS, Period.FOUR_HOURS);
	}
}
