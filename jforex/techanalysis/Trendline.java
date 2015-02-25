package jforex.techanalysis;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.dukascopy.api.IBar;

public class Trendline {
	protected String name;
	protected DateTime 
		startTime,
		endTime;
	protected double 
		startValue,
		endValue,
		slope30min;
	
	public Trendline(String name, DateTime startTime, DateTime endTime, double startValue, double endValue) {
		super();
		this.name = new String(name);
		this.startTime = startTime;
		this.endTime = endTime;
		this.startValue = startValue;
		this.endValue = endValue;
		// calculate number of 30min bars between start and end point
		DateTime
			startTimeAt2230 = new DateTime(startTime.getYear(), startTime.getMonthOfYear(), startTime.getDayOfMonth(), 22, 30, 0, 0),
			fridayCloseForStartPoint = new DateTime(startTimeAt2230.plusDays(5 - startTime.getDayOfWeek()));
		long numberOf30minBars = 0;
		// 1. check whether start and end are in the same week
		if (endTime.isBefore(fridayCloseForStartPoint)) {
			numberOf30minBars = (endTime.getMillis() - startTime.getMillis()) / (30 * 60 * 1000);
		}
		else {
			// calculate number of weekends between start and end date
			double numberOfHours = (endTime.getMillis() - fridayCloseForStartPoint.getMillis()) / (60 * 60 * 1000);
			double weekendsInBetween = Math.ceil(numberOfHours / 168);
			numberOf30minBars = (long) ((endTime.getMillis() - startTime.getMillis()) / (30 * 60 * 1000) - weekendsInBetween * 96);
		}
		slope30min = (endValue - startValue) / numberOf30minBars;
	}	
	
	public boolean hit(IBar bar, double tolerance) {
		if (bar.getTime() < endTime.getMillis())
			return false;
		double value = getCurrentValue(bar);
		return bar.getLow() - tolerance <= value && bar.getHigh() + tolerance >= value;
	}
	
	public boolean isNear(IBar bar, double fromPrice, double tolerance) {
		if (bar.getTime() < endTime.getMillis())
			return false;
		double value = getCurrentValue(bar);
		return fromPrice >= value - tolerance && fromPrice <= value + tolerance;
	}
	

	public double getCurrentValue(IBar bar) {
		// Careful ! IBar.getTime() returns GMT time zone, whereas endTime is in CET !
		DateTime barTimeCET = new DateTime(bar.getTime(), DateTimeZone.forID("Europe/Zurich"));
		DateTime
			endTimeAt2230 = new DateTime(endTime.getYear(), endTime.getMonthOfYear(), endTime.getDayOfMonth(), 22, 30, 0, 0),
			fridayCloseForEndTime = new DateTime(endTimeAt2230.plusDays(5 - endTime.getDayOfWeek()));
		double numberOf30minBars = 0;
		// 1. check whether start and end are in the same week
		if (barTimeCET.isBefore(fridayCloseForEndTime)) {
			numberOf30minBars = (barTimeCET.getMillis() - endTime.getMillis()) / (30 * 60 * 1000);
		}
		else {
			// calculate number of weekends between start and end date
			double numberOfHours = (barTimeCET.getMillis() - fridayCloseForEndTime.getMillis()) / (60 * 60 * 1000);
			double weekendsInBetween = Math.ceil(numberOfHours / 168);
			numberOf30minBars = (barTimeCET.getMillis() - endTime.getMillis()) / (30 * 60 * 1000) - weekendsInBetween * 96;
		}
		double value = endValue + (numberOf30minBars * slope30min);
		return value;
	}

	public String getName() {
		return name;
	}

	public double getSlope30min() {
		return slope30min;
	}

}
