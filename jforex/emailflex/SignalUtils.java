package jforex.emailflex;

import com.dukascopy.api.Period;

import jforex.utils.FXUtils;

public class SignalUtils {
	public static final int 
		IchiCloudBreakoutID = 80,
		BuyDipsSellRalliesID = 81,
		SMACrossFastID = 82,
		EMACrossFastID = 83,
		SMACrossSlowID = 84,
		EMACrossSlowID = 85,
		SqueezeBreakoutID = 86,
		DonchianBreakoutFastID = 87,
		DonchianBreakoutSlowID = 88,
		RangeExtremeID = 89;
	
	public static final String 
		signalInsertStart = "INSERT INTO " + FXUtils.getDbToUse() + ".tsignal (option_id, instrument_id, direction, TimeFrame, signalTime, barTime, stopEntry, limitEntry, stopLoss, atr";

	public static String getSignalInsertValues(int strategy_id, int instrument_id, String direction, String TimeFrame, String signalTime, String barTime, 
				double stopEntry, double lmtEntry, double stopLoss, double atr) {
		return "VALUES (" + strategy_id 
				+ ", " + instrument_id
				+ ", '" + direction
				+ "', '" + TimeFrame
				+ "', '" + signalTime
				+ "', '" + barTime
				+ "', " + FXUtils.df5.format(stopEntry)
				+ ", " + FXUtils.df5.format(lmtEntry)
				+ ", " + FXUtils.df5.format(stopLoss)
				+ ", " + FXUtils.df5.format(atr);
	}

	public static long getBarEndTime(long time, Period pPeriod) {
		if (pPeriod.equals(Period.TEN_MINS))
			return time + 10 * 60 * 1000;
		else if (pPeriod.equals(Period.FIFTEEN_MINS))
			return time + 15 * 60 * 1000;
		else if (pPeriod.equals(Period.TWENTY_MINS))
			return time + 20 * 60 * 1000;
		else if (pPeriod.equals(Period.THIRTY_MINS))
			return time + 30 * 60 * 1000;
		else if (pPeriod.equals(Period.ONE_HOUR))
			return time + 60 * 60 * 1000;
		else if (pPeriod.equals(Period.FOUR_HOURS))
			return time + 4 * 60 * 60 * 1000;
		else if (pPeriod.equals(Period.DAILY) || pPeriod.equals(Period.DAILY_SKIP_SUNDAY) || pPeriod.equals(Period.DAILY_SUNDAY_IN_MONDAY))
			return time + 24 * 60 * 60 * 1000;
		else if (pPeriod.equals(Period.WEEKLY))
			return time + 7 * 24 * 60 * 60 * 1000;
		
		return -1;
	}
}
