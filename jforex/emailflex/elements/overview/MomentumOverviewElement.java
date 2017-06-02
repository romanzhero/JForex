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

public class MomentumOverviewElement extends AbstractOverview {

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
		return new MomentumOverviewElement();
	}

	@Override
	public String printHTML(Instrument instrument, Period pPeriod, IBar bidBar,
			Connection logDB) {
		if (!pPeriod.equals(Period.THIRTY_MINS))
			return new String("Trend overview available only for 30min period");

		String macdCross1d = dbGetString(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB, "MACDCross",
				false), macdCross4h = dbGetString(instrument,
				Period.FOUR_HOURS, bidBar, logDB, "MACDCross", false), macdCross30min = dbGetString(
				instrument, pPeriod, bidBar, logDB, "MACDCross", true), res = new String();
		boolean atLeastOneMACDCross = macdCross1d
				.equals("BULL_CROSS_BELOW_ZERO")
				|| macdCross1d.equals("BEAR_CROSS_ABOVE_ZERO")
				|| macdCross4h.equals("BULL_CROSS_BELOW_ZERO")
				|| macdCross4h.equals("BEAR_CROSS_ABOVE_ZERO")
				|| macdCross30min.equals("BULL_CROSS_BELOW_ZERO")
				|| macdCross30min.equals("BEAR_CROSS_ABOVE_ZERO");

		res += "<tr><td><span style=\"display:block;\">Momentum</span><span style=\"display:block;\">MACD</span>"
				+ (atLeastOneMACDCross ? "<span style=\"display:block;\">&nbsp;</span>"
						: "")
				+ "<span style=\"display:block;\">MACD-H</span><span style=\"display:block;\">StochState</span><span style=\"display:block;\">StochsDiff</span></td>";

		String currMACD = dbGetString(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB, "MACDState",
				false), currMACD_H = dbGetString(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB, "MACDHState",
				false), currStochState = dbGetString(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB, "StochState",
				false), currStochCrossState = dbGetString(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB, "StochsCross",
				false), currCountTxt = momentumCount(instrument, bidBar, logDB,
				Period.DAILY_SUNDAY_IN_MONDAY, false), currCountColor = getCountColor(
				currCountTxt, currMACD);
		float currStochDiff = dbGetFloat(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB, "StochFast",
				false)
				- dbGetFloat(instrument, Period.DAILY_SUNDAY_IN_MONDAY, bidBar,
						logDB, "StochSlow", false);

		res += "<td><span style=\"background-color:" + currCountColor
				+ "; display:block; margin:0 1px;\">"
				+ FXUtils.beautify(currCountTxt) + "</span>"
				+ printMACD(currMACD, macdCross1d, atLeastOneMACDCross)
				+ printMACD_H(currMACD_H) + printStochState(currStochState)
				+ printStochsDiff(currStochDiff, currStochCrossState) + "</td>";

		currMACD = dbGetString(instrument, Period.FOUR_HOURS, bidBar, logDB,
				"MACDState", false);
		currMACD_H = dbGetString(instrument, Period.FOUR_HOURS, bidBar, logDB,
				"MACDHState", false);
		currStochState = dbGetString(instrument, Period.FOUR_HOURS, bidBar,
				logDB, "StochState", false);
		currStochCrossState = dbGetString(instrument, Period.FOUR_HOURS,
				bidBar, logDB, "StochsCross", false);
		currCountTxt = momentumCount(instrument, bidBar, logDB,
				Period.FOUR_HOURS, false);
		currCountColor = getCountColor(currCountTxt, currMACD);
		currStochDiff = dbGetFloat(instrument, Period.FOUR_HOURS, bidBar,
				logDB, "StochFast", false)
				- dbGetFloat(instrument, Period.FOUR_HOURS, bidBar, logDB,
						"StochSlow", false);
		res += "<td><span style=\"background-color:" + currCountColor
				+ "; display:block; margin:0 1px;\">"
				+ FXUtils.beautify(currCountTxt) + "</span>"
				+ printMACD(currMACD, macdCross4h, atLeastOneMACDCross)
				+ printMACD_H(currMACD_H) + printStochState(currStochState)
				+ printStochsDiff(currStochDiff, currStochCrossState) + "</td>";

		currMACD = dbGetString(instrument, Period.THIRTY_MINS, bidBar, logDB,
				"MACDState", true);
		currMACD_H = dbGetString(instrument, Period.THIRTY_MINS, bidBar, logDB,
				"MACDHState", true);
		currStochState = dbGetString(instrument, Period.THIRTY_MINS, bidBar,
				logDB, "StochState", true);
		currStochCrossState = dbGetString(instrument, Period.THIRTY_MINS,
				bidBar, logDB, "StochsCross", true);
		currCountTxt = momentumCount(instrument, bidBar, logDB,
				Period.THIRTY_MINS, true);
		currCountColor = getCountColor(currCountTxt, currMACD);
		currStochDiff = dbGetFloat(instrument, Period.THIRTY_MINS, bidBar,
				logDB, "StochFast", true)
				- dbGetFloat(instrument, Period.THIRTY_MINS, bidBar, logDB,
						"StochSlow", true);
		res += "<td><span style=\"background-color:" + currCountColor
				+ "; display:block; margin:0 1px;\">"
				+ FXUtils.beautify(currCountTxt) + "</span>"
				+ printMACD(currMACD, macdCross30min, atLeastOneMACDCross)
				+ printMACD_H(currMACD_H) + printStochState(currStochState)
				+ printStochsDiff(currStochDiff, currStochCrossState) + "</td>";

		return res += "</tr>";
	}

	private String printStochsDiff(float currStochDiff,
			String currStochCrossState) {
		String text = null, color = null;
		if (currStochCrossState != null && !currStochCrossState.equals("NONE")) {
			text = new String(FXUtils.beautify(currStochCrossState) + ", "
					+ FXUtils.df1.format(currStochDiff));
			if (currStochCrossState.toUpperCase().equals(
					"BULLISH_CROSS_FROM_OVERSOLD"))
				color = new String("#090");
			else if (currStochCrossState.toUpperCase().equals("BULLISH_CROSS"))
				color = new String("#0C3");
			else if (currStochCrossState.toUpperCase().equals(
					"BEARISH_CROSS_FROM_OVERBOUGTH"))
				color = new String("#C00");
			else if (currStochCrossState.toUpperCase().equals("BEARISH_CROSS"))
				color = new String("#F00");
			else
				color = new String("#FFF");
		} else {
			text = new String(FXUtils.df1.format(currStochDiff));
			if (currStochDiff > 0.0 && currStochDiff < 5.0)
				color = new String("#0F6");
			else if (currStochDiff >= 5.0 && currStochDiff < 10.0)
				color = new String("#0C3");
			else if (currStochDiff >= 10.0)
				color = new String("#090");
			else if (currStochDiff <= 0.0 && currStochDiff > -5.0)
				color = new String("#F66");
			else if (currStochDiff <= -5.0 && currStochDiff > -10.0)
				color = new String("#F00");
			else if (currStochDiff <= -10.0)
				color = new String("#C00");
			else
				color = new String("#FFF");
		}
		return new String("<span style=\"background-color:" + color
				+ "; display:block; margin:0 1px;\">" + text + "</span>");
	}

	private String printStochState(String currStochState) {
		String color = null;
		if (currStochState.toUpperCase().equals("OVERSOLD_SLOW"))
			color = new String("#0F6");
		else if (currStochState.toUpperCase().equals("RAISING_IN_MIDDLE")
				|| currStochState.toUpperCase().equals("OVERBOUGHT_FAST"))
			color = new String("#0C3");
		else if (currStochState.toUpperCase().equals("OVERBOUGHT_BOTH"))
			color = new String("#090");
		else if (currStochState.toUpperCase().equals("OVERBOUGHT_SLOW"))
			color = new String("#F66");
		else if (currStochState.toUpperCase().equals("FALLING_IN_MIDDLE")
				|| currStochState.toUpperCase().equals("OVERSOLD_FAST"))
			color = new String("#F00");
		else if (currStochState.toUpperCase().equals("OVERSOLD_BOTH"))
			color = new String("#C00");
		else
			color = new String("#FFF");

		return new String("<span style=\"background-color:" + color
				+ "; display:block; margin:0 1px;\">"
				+ FXUtils.beautify(currStochState) + "</span>");
	}

	private String printMACD_H(String currMACD_H) {
		String color = null;
		if (currMACD_H.toUpperCase().equals("TICKED_UP_BELOW_ZERO"))
			color = new String("#0F6");
		else if (currMACD_H.toUpperCase().equals("RAISING_BELOW_0"))
			color = new String("#0C3");
		else if (currMACD_H.toUpperCase().equals("RAISING_ABOVE_0"))
			color = new String("#090");
		else if (currMACD_H.toUpperCase().equals("TICKED_UP_ABOVE_ZERO"))
			color = new String("#0C3");

		else if (currMACD_H.toUpperCase().equals("TICKED_DOWN_ABOVE_ZERO"))
			color = new String("#F66");
		else if (currMACD_H.toUpperCase().equals("FALLING_ABOVE_0"))
			color = new String("#F00");
		else if (currMACD_H.toUpperCase().equals("FALLING_BELOW_0"))
			color = new String("#C00");
		else if (currMACD_H.toUpperCase().equals("TICKED_DOWN_BELOW_ZERO"))
			color = new String("#F00");
		else
			color = new String("#FFF");

		return new String("<span style=\"background-color:" + color
				+ "; display:block; margin:0 1px;\">"
				+ FXUtils.beautify(currMACD_H) + "</span>");
	}

	private String printMACD(String currMACD, String macdCross,
			boolean atLeastOneMACDCross) {
		String res = new String(), color = null;
		if (currMACD.toUpperCase().trim().equals("RAISING_BOTH_BELOW_0"))
			color = new String("#0F6");
		else if (currMACD.toUpperCase().trim().equals("RAISING_FAST_ABOVE_0"))
			color = new String("#0C3");
		else if (currMACD.toUpperCase().trim().equals("RAISING_BOTH_ABOVE_0"))
			color = new String("#090");
		else if (currMACD.toUpperCase().trim().equals("FALLING_BOTH_ABOVE_0"))
			color = new String("#F66");
		else if (currMACD.toUpperCase().trim().equals("FALLING_FAST_BELOW_0"))
			color = new String("F00");
		else if (currMACD.toUpperCase().trim().equals("FALLING_BOTH_BELOW_0"))
			color = new String("#C00");
		else
			color = new String("#FFF");

		res += "<span style=\"background-color:" + color
				+ "; display:block; margin:0 1px;\">"
				+ FXUtils.beautify(currMACD) + "</span>";
		if (atLeastOneMACDCross) {
			if (macdCross.contains("CROSS")) {
				if (macdCross.toLowerCase().equals("bull_cross_below_zero"))
					color = new String("#0F6");
				else if (macdCross.toLowerCase()
						.equals("bull_cross_above_zero"))
					color = new String("#090");
				else if (macdCross.toLowerCase()
						.equals("bear_cross_above_zero"))
					color = new String("#F66");
				else if (macdCross.toLowerCase()
						.equals("bear_cross_below_zero"))
					color = new String("#C00");
				res += "<span style=\"background-color:" + color
						+ "; display:block; margin:0 1px;\">"
						+ FXUtils.beautify(macdCross) + "</span>";
			} else
				res += "<span style=\"background-color:" + color
						+ "; display:block; margin:0 1px;\">&nbsp;</span>";
		}

		return res;
	}

	private String getCountColor(String currCountTxt, String macd) {
		if (currCountTxt.toLowerCase().equals("all down ! 3:0a"))
			return new String("#C00");
		if (currCountTxt.toLowerCase().equals("all down ! 3:0b"))
			return new String("#F00");
		if (currCountTxt.toLowerCase().equals("all down ! 3:0c"))
			return new String("#F66");
		if (currCountTxt.toLowerCase().contains("down")) {
			if (macd.toLowerCase().contains("falling"))
				return new String("#F00");
			else
				return new String("#F66");
		}

		if (currCountTxt.toLowerCase().equals("all up ! 3:0a"))
			return new String("#090");
		if (currCountTxt.toLowerCase().equals("all up ! 3:0b"))
			return new String("#0C3");
		if (currCountTxt.toLowerCase().equals("all up ! 3:0c"))
			return new String("#0F6");
		if (currCountTxt.toLowerCase().contains("up")) {
			if (macd.toLowerCase().contains("raising"))
				return new String("#0C3");
			else
				return new String("#0F6");
		}

		return null;
	}

	private String momentumCount(Instrument instrument, IBar bidBar,
			Connection logDB, Period timeFrameID, boolean exact) {
		int upCount = 0, downCount = 0;
		String currMomentum = dbGetString(instrument, timeFrameID, bidBar,
				logDB, "MACDState", exact);
		if (currMomentum != null && currMomentum.length() > 0) {
			if (currMomentum.contains("RAISING")
					|| currMomentum.contains("TICKED_UP"))
				upCount++;
			else
				downCount++;
		}
		currMomentum = dbGetString(instrument, timeFrameID, bidBar, logDB,
				"MACDHState", exact);
		if (currMomentum != null && currMomentum.length() > 0) {
			if (currMomentum.contains("RAISING")
					|| currMomentum.contains("TICKED_UP"))
				upCount++;
			else
				downCount++;
		}
		double StochFast = dbGetFloat(instrument, timeFrameID, bidBar, logDB,
				"StochFast", exact), StochSlow = dbGetFloat(instrument,
				timeFrameID, bidBar, logDB, "StochSlow", exact);
		if (StochFast > StochSlow)
			upCount++;
		else
			downCount++;

		String MACDState = dbGetString(instrument, timeFrameID, bidBar, logDB,
				"MACDState", exact), suffix = null;
		if (upCount > downCount) {
			if (MACDState.equals("RAISING_BOTH_BELOW_0"))
				suffix = new String("a");
			else if (MACDState.equals("RAISING_FAST_ABOVE_0"))
				suffix = new String("b");
			else if (MACDState.equals("RAISING_BOTH_ABOVE_0"))
				suffix = new String("c");
			else
				suffix = new String("");
			return new String((upCount == 3 ? "ALL UP ! " : "UP ") + upCount
					+ ":" + downCount + suffix);
		} else {
			if (MACDState.equals("FALLING_BOTH_ABOVE_0"))
				suffix = new String("a");
			else if (MACDState.equals("FALLING_FAST_BELOW_0"))
				suffix = new String("b");
			else if (MACDState.equals("FALLING_BOTH_BELOW_0"))
				suffix = new String("c");
			else
				suffix = new String("");
			return new String((downCount == 3 ? "ALL DOWN ! " : "DOWN ")
					+ downCount + ":" + upCount + suffix);
		}
	}

}