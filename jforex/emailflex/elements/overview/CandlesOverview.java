package jforex.emailflex.elements.overview;

import java.sql.Connection;
import java.util.Properties;

import jforex.emailflex.IFlexEmailElement;
import jforex.utils.FXUtils;

import com.dukascopy.api.IBar;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.Period;

public class CandlesOverview extends AbstractOverview {

	@Override
	public String printHTML(Instrument instrument, Period pPeriod, IBar bidBar,
			Connection logDB) {
		if (!pPeriod.equals(Period.THIRTY_MINS))
			return new String("Trend overview available only for 30min period");

		String res = new String(
				"<tr style=\"background-color:#f4f4f4\"><td>Candles</td>");
		boolean atLeastOneSignal = false, twoSignals1d = false, twoSignals4h = false, twoSignals30min = false;
		String candleTrigger1d = dbGetString(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar, logDB, "CandleTrigger",
				false);
		if (candleTrigger1d != null && candleTrigger1d.length() > 0
				&& !candleTrigger1d.toLowerCase().equals("none")
				&& !candleTrigger1d.toLowerCase().equals("n/a")) {
			atLeastOneSignal = true;
			twoSignals1d = candleTrigger1d.contains(" AND ");
		}
		String candleTrigger4h = dbGetString(instrument, Period.FOUR_HOURS,
				bidBar, logDB, "CandleTrigger", false);
		if (candleTrigger4h != null && candleTrigger4h.length() > 0
				&& !candleTrigger4h.toLowerCase().equals("none")
				&& !candleTrigger4h.toLowerCase().equals("n/a")) {
			atLeastOneSignal = true;
			twoSignals4h = candleTrigger4h.contains(" AND ");
		}
		String candleTrigger30min = dbGetString(instrument, Period.THIRTY_MINS,
				bidBar, logDB, "CandleTrigger", true);
		if (candleTrigger30min != null && candleTrigger30min.length() > 0
				&& !candleTrigger30min.toLowerCase().equals("none")
				&& !candleTrigger30min.toLowerCase().equals("n/a")) {
			atLeastOneSignal = true;
			twoSignals30min = candleTrigger30min.contains(" AND ");
		}
		boolean atLeastOneTwoSignals = twoSignals1d || twoSignals4h
				|| twoSignals30min;

		res += printCell(candleTrigger1d, atLeastOneTwoSignals, twoSignals1d,
				Period.DAILY_SUNDAY_IN_MONDAY, instrument, bidBar, logDB,
				atLeastOneSignal);
		res += printCell(candleTrigger4h, atLeastOneTwoSignals, twoSignals4h,
				Period.FOUR_HOURS, instrument, bidBar, logDB, atLeastOneSignal);
		res += printCell(candleTrigger30min, atLeastOneTwoSignals,
				twoSignals30min, Period.THIRTY_MINS, instrument, bidBar, logDB,
				atLeastOneSignal);

		return res += "</tr>";
	}

	private String printCell(String candleTrigger,
			boolean atLeastOneTwoSignals, boolean twoSignals, Period p,
			Instrument instrument, IBar bidBar, Connection logDB,
			boolean atLeastOneSignal) {
		String res = new String("<td>"), candleText = null;
		boolean bullish = false, bearish = false;
		if (candleTrigger != null && candleTrigger.length() > 0
				&& !candleTrigger.equals("none")
				&& !candleTrigger.equals("n/a")) {
			if (twoSignals) {
				candleText = candleTrigger.substring(0,
						candleTrigger.indexOf(" AND "));
			} else
				candleText = candleTrigger;

			bullish = candleTrigger.contains("BULLISH");
			bearish = candleTrigger.contains("BEARISH");
		} else {
			candleText = new String("(none)");
		}

		if (twoSignals) {
			// bullish signals always first
			res += "<span style=\"background-color:" + getGreen()
					+ "; display:block; margin:0 1px;\"> "
					+ FXUtils.beautify(candleText) + "</span>";
			res += "<span style=\"background-color:" + getGreen()
					+ "; display:block; margin:0 1px;\">(";
			if (p.equals(Period.FOUR_HOURS))
				res += "1d: "
						+ FXUtils.df1.format(dbGetFloat(instrument, p, bidBar,
								logDB, "bullishPivotLevelHigherTFChannelPos",
								false)) + "%/";
			else if (p.equals(Period.THIRTY_MINS))
				res += "4h: "
						+ FXUtils.df1.format(dbGetFloat(instrument, p, bidBar,
								logDB, "bullishPivotLevelHigherTFChannelPos",
								true)) + "%/";
			res += FXUtils.df1.format(dbGetFloat(instrument, p, bidBar, logDB,
					"bullishCandleTriggerChannelPos",
					p.equals(Period.THIRTY_MINS)))
					+ "%)</span>";

			String secondCandle = candleTrigger.substring(candleTrigger
					.indexOf(" AND ") + 5);
			res += "<span style=\"background-color:" + getRed()
					+ "; display:block; margin:0 1px;\"> "
					+ FXUtils.beautify(secondCandle) + "</span>";
			res += "<span style=\"background-color:" + getRed()
					+ "; display:block; margin:0 1px;\">(";
			if (p.equals(Period.FOUR_HOURS))
				res += "1d: "
						+ FXUtils.df1.format(dbGetFloat(instrument, p, bidBar,
								logDB, "bearishPivotLevelHigherTFChannelPos",
								false)) + "%/";
			else if (p.equals(Period.THIRTY_MINS))
				res += "4h: "
						+ FXUtils.df1.format(dbGetFloat(instrument, p, bidBar,
								logDB, "bearishPivotLevelHigherTFChannelPos",
								true)) + "%/";
			res += FXUtils.df1.format(dbGetFloat(instrument, p, bidBar, logDB,
					"bearishCandleTriggerChannelPos",
					p.equals(Period.THIRTY_MINS)))
					+ "%)</span>";
		} else { // but not on this timeframe, it has either only one or no
					// candle signals
			if (bullish) {
				res += "<span style=\"background-color:" + getGreen()
						+ "; display:block; margin:0 1px;\"> "
						+ FXUtils.beautify(candleText) + "</span>";
				res += "<span style=\"background-color:" + getGreen()
						+ "; display:block; margin:0 1px;\">(";
				if (p.equals(Period.FOUR_HOURS))
					res += "1d: "
							+ FXUtils.df1.format(dbGetFloat(instrument, p,
									bidBar, logDB,
									"bullishPivotLevelHigherTFChannelPos",
									false)) + "%/";
				else if (p.equals(Period.THIRTY_MINS))
					res += "4h: "
							+ FXUtils.df1.format(dbGetFloat(instrument, p,
									bidBar, logDB,
									"bullishPivotLevelHigherTFChannelPos",
									false)) + "%/";
				res += FXUtils.df1.format(dbGetFloat(instrument, p, bidBar,
						logDB, "bullishCandleTriggerChannelPos",
						p.equals(Period.THIRTY_MINS)))
						+ "%)</span>";
			} else if (bearish) {
				res += "<span style=\"background-color:" + getRed()
						+ "; display:block; margin:0 1px;\"> "
						+ FXUtils.beautify(candleText) + "</span>";
				res += "<span style=\"background-color:" + getRed()
						+ "; display:block; margin:0 1px;\">(";
				if (p.equals(Period.FOUR_HOURS))
					res += "1d: "
							+ FXUtils.df1.format(dbGetFloat(instrument, p,
									bidBar, logDB,
									"bearishPivotLevelHigherTFChannelPos",
									false)) + "%/";
				else if (p.equals(Period.THIRTY_MINS))
					res += "4h: "
							+ FXUtils.df1
									.format(dbGetFloat(
											instrument,
											p,
											bidBar,
											logDB,
											"bearishPivotLevelHigherTFChannelPos",
											true)) + "%/";
				res += FXUtils.df1.format(dbGetFloat(instrument, p, bidBar,
						logDB, "bearishCandleTriggerChannelPos",
						p.equals(Period.THIRTY_MINS)))
						+ "%)</span>";
			} else
				res += "<span style=\"background-color:" + getWhite()
						+ "; display:block; margin:0 1px;\">(none)</span>";

			// now check if empty cells are needed
			if (atLeastOneTwoSignals) {
				if (bullish) {
					res += "<span style=\"background-color:" + getGreen()
							+ "; display:block; margin:0 1px;\">&nbsp;</span>";
					res += "<span style=\"background-color:" + getGreen()
							+ "; display:block; margin:0 1px;\">&nbsp;</span>";
				} else if (bearish) {
					res += "<span style=\"background-color:" + getRed()
							+ "; display:block; margin:0 1px;\">&nbsp;</span>";
					res += "<span style=\"background-color:" + getRed()
							+ "; display:block; margin:0 1px;\">&nbsp;</span>";
				} else {
					res += "<span style=\"background-color:" + getWhite()
							+ "; display:block; margin:0 1px;\">&nbsp;</span>";
					res += "<span style=\"background-color:" + getWhite()
							+ "; display:block; margin:0 1px;\">&nbsp;</span>";
					res += "<span style=\"background-color:" + getWhite()
							+ "; display:block; margin:0 1px;\">&nbsp;</span>";
				}
			} else if (!(bullish || bearish) && atLeastOneSignal)
				res += "<span style=\"background-color:" + getWhite()
						+ "; display:block; margin:0 1px;\">&nbsp;</span>";
		}
		return res + "</td>";
	}

	@Override
	public IFlexEmailElement cloneIt() {
		return new CandlesOverview();
	}

	@Override
	public IFlexEmailElement cloneIt(Properties conf) {
		return cloneIt();
	}

}
