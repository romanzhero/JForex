package jforex.utils;

import java.util.Map;
import java.util.TreeMap;

import org.joda.time.DateTime;

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
		tradingHoursPerInstrument.put(Instrument.AUSIDXAUD, new InstrumentTradingHours(1, 0, 17, 0));
		tradingHoursPerInstrument.put(Instrument.JPNIDXJPY, new InstrumentTradingHours(1, 0, 17, 0));
		tradingHoursPerInstrument.put(Instrument.HKGIDXHKD, new InstrumentTradingHours(1, 0, 17, 0));
		tradingHoursPerInstrument.put(Instrument.DEUIDXEUR, new InstrumentTradingHours(7, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.CHEIDXCHF, new InstrumentTradingHours(7, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.EUSIDXEUR, new InstrumentTradingHours(7, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.ESPIDXEUR, new InstrumentTradingHours(7, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.FRAIDXEUR, new InstrumentTradingHours(7, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.GBRIDXGBP, new InstrumentTradingHours(7, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.USA30IDXUSD, new InstrumentTradingHours(7, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.USA500IDXUSD, new InstrumentTradingHours(7, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.USATECHIDXUSD, new InstrumentTradingHours(7, 0, 20, 0));
		tradingHoursPerInstrument.put(Instrument.BRENTCMDUSD, new InstrumentTradingHours(1, 0, 22, 0));
		tradingHoursPerInstrument.put(Instrument.LIGHTCMDUSD, new InstrumentTradingHours(1, 0, 22, 0));
	}
	
	public static boolean withinTradingHours(Instrument instrument, long time) {
		InstrumentTradingHours tradingHours = tradingHoursPerInstrument.get(instrument);
		if (tradingHours == null)
			// default 24/7 trading for FX, XAU/USD and XAG/USD
			return true;
		DateTime
			currentDay = new DateTime(time),
			currentDayStart = new DateTime(currentDay.getYear(), currentDay.getMonthOfYear(), currentDay.getDayOfMonth(), 0, 0, 0, 0),
			currentDayEnd = new DateTime(currentDay.getYear(), currentDay.getMonthOfYear(), currentDay.getDayOfMonth(), 0, 0, 0, 0),
			currentTime = new DateTime(time);
		currentDayStart = currentDayStart.plusHours(tradingHours.startHour);
		currentDayStart = currentDayStart.plusMinutes(tradingHours.startMinute);
		currentDayEnd = currentDayEnd.plusHours(tradingHours.endHour);
		currentDayEnd = currentDay.plusMinutes(tradingHours.endMinute);
		return currentTime.getMillis() >= currentDayStart.getMillis()
			   && currentTime.getMillis() < currentDayEnd.getMillis();
	}
	
	public static long tradingHoursEnd(Instrument instrument, long time) {
		DateTime
			currentDay = new DateTime(time),
			currentDayEnd = new DateTime(currentDay.getYear(), currentDay.getMonthOfYear(), currentDay.getDayOfMonth(), 0, 0, 0, 0);
		InstrumentTradingHours tradingHours = tradingHoursPerInstrument.get(instrument);
		if (tradingHours == null) {
			// default 24/7 trading for FX, XAU/USD and XAG/USD
			currentDayEnd = currentDayEnd.plusHours(23);
			currentDayEnd = currentDayEnd.plusMinutes(55);
		} else {			
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
			currentDayStart = currentDayStart.plusHours(tradingHours.startHour);
			currentDayStart = currentDayStart.plusMinutes(tradingHours.startMinute);
		}
		return currentDayStart.getMillis();
	}	
}
