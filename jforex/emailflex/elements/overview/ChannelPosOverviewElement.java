package jforex.emailflex.elements.overview;

import java.sql.Connection;
import java.util.List;
import java.util.Properties;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

import jforex.emailflex.IFlexEmailElement;
import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.utils.FXUtils;
import jforex.utils.log.FlexLogEntry;

public class ChannelPosOverviewElement extends AbstractOverview {

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			IHistory history, IIndicators indicators, Trend trendDetector,
			Channel channelPosition, Momentum momentum, Volatility vola,
			TradeTrigger tradeTrigger, Properties conf,
			List<FlexLogEntry> logLine, Connection logDB) throws JFException {
		return new String();
	}

	@Override
	public String print(Instrument instrument, Period pPeriod, IBar bidBar,
			List<FlexLogEntry> logLine, Connection logDB) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IFlexEmailElement cloneIt(Properties conf) {
		return cloneIt();
	}

	@Override
	public IFlexEmailElement cloneIt() {
		return new ChannelPosOverviewElement();
	}

	@Override
	public String printHTML(Instrument instrument, Period pPeriod, IBar bidBar,
			Connection logDB) {
		if (!pPeriod.equals(Period.THIRTY_MINS))
			return new String(
					"Channel position overview available only for 30min period");

		float barsAbove1d = dbGetFloat(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB,
				"barsAboveChannel", false), barsBelow1d = dbGetFloat(
				instrument, Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB,
				"barsBelowChannel", false), barsAbove4h = dbGetFloat(
				instrument, Period.FOUR_HOURS, bidBar, logDB,
				"barsAboveChannel", false), barsBelow4h = dbGetFloat(
				instrument, Period.FOUR_HOURS, bidBar, logDB,
				"barsBelowChannel", false), barsAbove30min = dbGetFloat(
				instrument, Period.THIRTY_MINS, bidBar, logDB,
				"barsAboveChannel", true), barsBelow30min = dbGetFloat(
				instrument, Period.THIRTY_MINS, bidBar, logDB,
				"barsBelowChannel", true);

		boolean extraLine = false;
		if (barsAbove1d > 0.0 || barsBelow1d > 0.0 || barsAbove4h > 0.0
				|| barsBelow4h > 0.0 || barsAbove30min > 0.0
				|| barsBelow30min > 0.0)
			extraLine = true;

		String res = new String(
				"<tr style=\"background-color:#f4f4f4\"><td><span style=\"display:block;\">Channel pos.</span>");
		if (extraLine)
			res += "<span style=\"display:block;\">&nbsp;</span>";
		res += "</td>";

		float currHigh = dbGetFloat(instrument, Period.DAILY_SUNDAY_IN_MONDAY,
				bidBar, logDB, "barHighChannelPos", false), currLow = dbGetFloat(
				instrument, Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB,
				"barLowChannelPos", false);
		res += printCell(currLow, currHigh, barsBelow1d, barsAbove1d, extraLine);

		currHigh = dbGetFloat(instrument, Period.FOUR_HOURS, bidBar, logDB,
				"barHighChannelPos", false);
		currLow = dbGetFloat(instrument, Period.FOUR_HOURS, bidBar, logDB,
				"barLowChannelPos", false);
		res += printCell(currLow, currHigh, barsBelow4h, barsAbove4h, extraLine);

		currHigh = dbGetFloat(instrument, Period.THIRTY_MINS, bidBar, logDB,
				"barHighChannelPos", true);
		currLow = dbGetFloat(instrument, Period.THIRTY_MINS, bidBar, logDB,
				"barLowChannelPos", true);
		res += printCell(currLow, currHigh, barsBelow30min, barsAbove30min,
				extraLine);

		res += "</tr>";
		return res;
	}

	private String printCell(float currLow, float currHigh, float barsBelow,
			float barsAbove, boolean extraLine) {
		String res = new String("<td><span style=\"background-color:"), color = null;

		if (currHigh > 90.0) {
			color = new String("#090");
			res += "#090; display:block; margin:0 1px; \">High ! ("
					+ FXUtils.df1.format(currHigh) + "%/"
					+ FXUtils.df1.format(currLow) + "%)</span>";
		} else if (currLow < 10.0) {
			color = new String("#C00");
			res += "#C00; display:block; margin:0 1px; \">Low ! ("
					+ FXUtils.df1.format(currHigh) + "%/"
					+ FXUtils.df1.format(currLow) + "%)</span>";
		} else {
			color = getWhite();
			res += color + "; display:block; margin:0 1px; \">"
					+ FXUtils.df1.format(currHigh) + "%/"
					+ FXUtils.df1.format(currLow) + "%</span>";
		}

		if (extraLine) {
			res += "<span style=\"background-color:";
			if (barsAbove > 0.0 || barsBelow > 0.0) {
				if (barsAbove > 0.0) {
					res += "#090; display:block; margin:0 1px; \">"
							+ FXUtils.if1.format(barsAbove) + " bar"
							+ (barsAbove > 1.0 ? "s" : "")
							+ " ABOVE channel !</span>";
				} else if (barsBelow > 0.0) {
					res += "#C00; display:block; margin:0 1px; \">"
							+ FXUtils.if1.format(barsBelow) + " bar"
							+ (barsBelow > 1.0 ? "s" : "")
							+ " BELOW channel !</span>";
				}
			} else {
				res += color
						+ "; display:block; margin:0 1px; \">&nbsp;</span>";
			}
		}

		return res + "</td>";
	}

}