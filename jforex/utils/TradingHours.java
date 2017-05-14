package jforex.utils;

import java.util.Map;
import java.util.TreeMap;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;

import com.dukascopy.api.Instrument;

public class TradingHours {
	
	public static class InstrumentTradingHours {
		// vremena u satima i minutima, dodatim na nulti Java datum
		int
			startHour, startMinute,
			endHour, endMinute;

		public InstrumentTradingHours(int startHour, int startMinute, int endHour, int endMinute) {
			super();
			this.startHour = startHour;
			this.startMinute = startMinute;
			this.endHour = endHour;
			this.endMinute = endMinute;
		}
	}
	
	protected static Map<Instrument, InstrumentTradingHours> tradingHoursPerInstrument = new TreeMap<Instrument, InstrumentTradingHours>();
	static {
		tradingHoursPerInstrument.put(Instrument.AUSIDXAUD, new InstrumentTradingHours(22, 50, 20, 0));
		tradingHoursPerInstrument.put(Instrument.JPNIDXJPY, new InstrumentTradingHours(1, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.HKGIDXHKD, new InstrumentTradingHours(1, 30, 15, 45));
		tradingHoursPerInstrument.put(Instrument.DEUIDXEUR, new InstrumentTradingHours(6, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.CHEIDXCHF, new InstrumentTradingHours(6, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.EUSIDXEUR, new InstrumentTradingHours(6, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.ESPIDXEUR, new InstrumentTradingHours(7, 0, 18, 0));
		tradingHoursPerInstrument.put(Instrument.FRAIDXEUR, new InstrumentTradingHours(6, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.GBRIDXGBP, new InstrumentTradingHours(6, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.USA30IDXUSD, new InstrumentTradingHours(6, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.USA500IDXUSD, new InstrumentTradingHours(6, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.USATECHIDXUSD, new InstrumentTradingHours(6, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.BRENTCMDUSD, new InstrumentTradingHours(0, 0, 21, 0));
		tradingHoursPerInstrument.put(Instrument.LIGHTCMDUSD, new InstrumentTradingHours(0, 0, 21, 0));
		tradingHoursPerInstrument.put(Instrument.XAGUSD, new InstrumentTradingHours(22, 0, 21, 0));
		tradingHoursPerInstrument.put(Instrument.XAUUSD, new InstrumentTradingHours(22, 0, 21, 0));
	}
	
	public static boolean withinTradingHours(Instrument instrument, long time) {
		InstrumentTradingHours tradingHours = tradingHoursPerInstrument.get(instrument);
		if (tradingHours == null)
			// default 24/7 trading for FX, XAU/USD and XAG/USD
			return true;
		DateTime currentTime = new DateTime(time);
		return currentTime.getMillis() >= tradingHoursStart(instrument, time)
			   && currentTime.getMillis() < tradingHoursEnd(instrument, time);
	}
	
	public static long tradingHoursEnd(Instrument instrument, long time) {
		DateTime
			currentDay = new DateTime(time),
			currentDayEnd = new DateTime(currentDay.getYear(), currentDay.getMonthOfYear(), currentDay.getDayOfMonth(), 0, 0, 0, 0);
		if (currentDay.getDayOfWeek() == DateTimeConstants.SUNDAY)
			return -1; // No limit for trading on Sunday
		InstrumentTradingHours tradingHours = tradingHoursPerInstrument.get(instrument);
		if (tradingHours == null) {
			// default 24/7 trading for FX, XAU/USD and XAG/USD
			currentDayEnd = currentDayEnd.plusHours(23);
			currentDayEnd = currentDayEnd.plusMinutes(55);
		} else {			
			if (tradingHours.endHour > tradingHours.startHour && currentDay.getHourOfDay() >= tradingHours.endHour)
				currentDayEnd.plusDays(1);
			currentDayEnd = currentDayEnd.plusHours(tradingHours.endHour);
			currentDayEnd = currentDayEnd.plusMinutes(tradingHours.endMinute);
		}
		return currentDayEnd.getMillis();
	}	
	
	public static long tradingHoursStart(Instrument instrument, long time) {
		DateTime
			currentDay = new DateTime(time),
			currentDayStart = new DateTime(currentDay.getYear(), currentDay.getMonthOfYear(), currentDay.getDayOfMonth(), 0, 0, 0, 0);
		InstrumentTradingHours tradingHours = tradingHoursPerInstrument.get(instrument);
		if (tradingHours == null) {
			// default 24/7 trading for FX, XAU/USD and XAG/USD
			currentDayStart = currentDayStart.plusHours(0);
			currentDayStart = currentDayStart.plusMinutes(0);
		} else {		
			if (tradingHours.endHour > tradingHours.startHour && currentDay.getHourOfDay() < tradingHours.endHour)
				// within next day
				currentDayStart.minusDays(1);
			currentDayStart = currentDayStart.plusHours(tradingHours.startHour);
			currentDayStart = currentDayStart.plusMinutes(tradingHours.startMinute);
		}
		return currentDayStart.getMillis();
	}	
}
