package jforex.explorers;

import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import jforex.BasicTAStrategy;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.Filter;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IIndicators.AppliedPrice;

public class RangeExplorer extends BasicTAStrategy implements IStrategy {

	protected boolean headerPrinted = false;
	protected boolean firstAndOnlyBarStatsDone = false;
	protected Map<Currency, List<IBar>> dailyConvertors = new HashMap<Currency, List<IBar>>();
	protected Map<Currency, List<IBar>> fourHConvertors = new HashMap<Currency, List<IBar>>();

	protected Map<Instrument, Boolean> pairDone = new HashMap<Instrument, Boolean>();

	// 3 months
	protected final int DAILY_BARS_TO_EXPLORE = 60;

	@Configurable("Period")
	public Period selectedPeriod = Period.THIRTY_MINS;

	@Override
	public void onStart(IContext context) throws JFException {
		super.onStartExec(context);

		Set<Instrument> instruments = context.getSubscribedInstruments();
		for (Instrument i : instruments) {
			pairDone.put(i, new Boolean(false));
		}
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar,
			IBar bidBar) throws JFException {
		// for all subscribed instruments i.e. no filtering there
		if (!period.equals(selectedPeriod)) {
			return;
		}

		if (!pairDone.get(instrument).booleanValue()) {
			pairDone.put(instrument, new Boolean(true));
		} else
			return;

		// This explorer should be used for one single snapshot in time. Rest of
		// the set interval is ignored.
		if (!firstAndOnlyBarStatsDone) {
			firstAndOnlyBarStatsDone = true;
			// Prepare convertor pairs
			prepareConvertors(bidBar);
		}

		long fourHBarTime = history.getPreviousBarStart(Period.FOUR_HOURS,
				bidBar.getTime());
		long dailyBarTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());

		double[] atr4hInPipsTS = vola.getATRTimeSeries(instrument,
				Period.FOUR_HOURS, OfferSide.BID, fourHBarTime, 14,
				DAILY_BARS_TO_EXPLORE * 6);
		double[] atr1dInPipsTS = vola.getATRTimeSeries(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, dailyBarTime, 14,
				DAILY_BARS_TO_EXPLORE);
		double[] atr1dInPerc = new double[atr1dInPipsTS.length];

		List<IBar> all1dBars = history.getBars(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, Filter.WEEKENDS,
				DAILY_BARS_TO_EXPLORE, dailyBarTime, 0);
		double[] dayRanges60d = new double[all1dBars.size()];
		for (int i = 0; i < dayRanges60d.length; i++) {
			dayRanges60d[i] = (all1dBars.get(i).getHigh() - all1dBars.get(i)
					.getLow()) * Math.pow(10, instrument.getPipScale());
			dayRanges60d[i] = convertTo1dUSDPips(instrument, dayRanges60d[i], i);
		}

		for (int i = 0; i < atr1dInPipsTS.length; i++) {
			atr1dInPerc[i] = atr1dInPipsTS[i]
					/ all1dBars.get(all1dBars.size() - i - 1).getClose() * 100;

			atr1dInPipsTS[i] *= Math.pow(10, instrument.getPipScale());
			atr1dInPipsTS[i] = convertTo1dUSDPips(instrument, atr1dInPipsTS[i],
					i);

		}
		for (int i = 0; i < atr4hInPipsTS.length; i++) {
			atr4hInPipsTS[i] *= Math.pow(10, instrument.getPipScale());
			atr4hInPipsTS[i] = convertTo4hUSDPips(instrument, atr4hInPipsTS[i],
					i);
		}

		double[] doubleStdDev4hInPipsTS = channelPosition.bBandsWidthTS(
				instrument, Period.FOUR_HOURS, OfferSide.BID, fourHBarTime,
				DAILY_BARS_TO_EXPLORE * 6);
		double[] doubleStdDev1dInPipsTS = channelPosition.bBandsWidthTS(
				instrument, Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				dailyBarTime, DAILY_BARS_TO_EXPLORE);
		for (int i = 0; i < doubleStdDev4hInPipsTS.length; i++) {
			doubleStdDev4hInPipsTS[i] /= 4; // To get 1 StDev from channel width
			doubleStdDev4hInPipsTS[i] *= Math.pow(10, instrument.getPipScale());
			doubleStdDev4hInPipsTS[i] = convertTo4hUSDPips(instrument,
					doubleStdDev4hInPipsTS[i], i);
		}
		for (int i = 0; i < doubleStdDev1dInPipsTS.length; i++) {
			doubleStdDev1dInPipsTS[i] /= 4; // To get 1 StDev from channel width
			doubleStdDev1dInPipsTS[i] *= Math.pow(10, instrument.getPipScale());
			doubleStdDev1dInPipsTS[i] = convertTo1dUSDPips(instrument,
					doubleStdDev1dInPipsTS[i], i);
		}

		all1dBars = history.getBars(instrument, Period.DAILY_SUNDAY_IN_MONDAY,
				OfferSide.BID, Filter.WEEKENDS, DAILY_BARS_TO_EXPLORE,
				dailyBarTime, 0);
		double[] dayRanges = new double[all1dBars.size()], dayRangesUSD = new double[all1dBars
				.size()];
		for (int i = 0; i < dayRangesUSD.length; i++) {
			dayRanges[i] = (all1dBars.get(i).getHigh() - all1dBars.get(i)
					.getLow()) * Math.pow(10, instrument.getPipScale());
			dayRangesUSD[i] = convertTo1dUSDPips(instrument, dayRanges[i], i);
		}

		double[] volume4h = indicators.volumeWAP(instrument, Period.FOUR_HOURS,
				OfferSide.BID, AppliedPrice.CLOSE, 20, Filter.WEEKENDS,
				DAILY_BARS_TO_EXPLORE * 6, fourHBarTime, 0);
		double[] volume1d = indicators.volumeWAP(instrument,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID,
				AppliedPrice.CLOSE, 20, Filter.WEEKENDS, DAILY_BARS_TO_EXPLORE,
				dailyBarTime, 0);

		List<FlexLogEntry> logLine = new ArrayList<FlexLogEntry>();

		logLine.add(new FlexLogEntry("Pair", instrument.toString()));
		logLine.add(new FlexLogEntry("Time 4h", FXUtils
				.getFormatedTimeGMT(bidBar.getTime())));
		logLine.add(new FlexLogEntry("MA(1d range, "
				+ Integer.toString(DAILY_BARS_TO_EXPLORE) + ") in USD pips",
				new Double(FXUtils.average(dayRanges60d)), FXUtils.df1));
		logLine.add(new FlexLogEntry("MA(1d range, "
				+ Integer.toString(FXUtils.YEAR_WORTH_OF_1d_BARS)
				+ ") in org. pips", new Double(FXUtils.average(dayRanges)),
				FXUtils.df1));
		logLine.add(new FlexLogEntry("MA(1d range, "
				+ Integer.toString(FXUtils.YEAR_WORTH_OF_1d_BARS)
				+ ") in USD pips", new Double(FXUtils.average(dayRangesUSD)),
				FXUtils.df1));
		logLine.add(new FlexLogEntry(
				"MA(ATR4h(14), " + Integer.toString(DAILY_BARS_TO_EXPLORE * 6)
						+ ") in USD pips", new Double(FXUtils
						.average(atr4hInPipsTS)), FXUtils.df1));
		logLine.add(new FlexLogEntry("MA(ATR1d(14), "
				+ Integer.toString(DAILY_BARS_TO_EXPLORE) + ") in USD pips",
				new Double(FXUtils.average(atr1dInPipsTS)), FXUtils.df1));
		logLine.add(new FlexLogEntry("MA(ATR1d(14) in perc, "
				+ Integer.toString(DAILY_BARS_TO_EXPLORE) + ")", new Double(
				FXUtils.average(atr1dInPerc)), FXUtils.df2));
		logLine.add(new FlexLogEntry(
				"MA(2xStDev4h(20), "
						+ Integer.toString(DAILY_BARS_TO_EXPLORE * 6)
						+ ") in USD pips", new Double(FXUtils
						.average(doubleStdDev4hInPipsTS)), FXUtils.df1));
		logLine.add(new FlexLogEntry("MA(2xStDev1d(20), "
				+ Integer.toString(DAILY_BARS_TO_EXPLORE) + ") in USD pips",
				new Double(FXUtils.average(doubleStdDev1dInPipsTS)),
				FXUtils.df1));
		logLine.add(new FlexLogEntry("MA(volumeWAP4h(20), "
				+ Integer.toString(DAILY_BARS_TO_EXPLORE * 6) + ")",
				new Double(FXUtils.average(volume4h)), FXUtils.df1));
		logLine.add(new FlexLogEntry("MA(volumeWAP1d(20), "
				+ Integer.toString(DAILY_BARS_TO_EXPLORE) + ")", new Double(
				FXUtils.average(volume1d)), FXUtils.df1));

		if (!headerPrinted) {
			headerPrinted = true;
			log.printLabelsFlex(logLine);
		}
		log.printValuesFlex(logLine);
		// log.print("Pair;Date;Range");
		// for (IBar bar : all1dBars) {
		// double range = (bar.getHigh() - bar.getLow()) * Math.pow(10,
		// instrument.getPipScale());
		// if (range > 0)
		// log.print(instrument.toString() + ";"
		// + FXUtils.getFormatedBarTime(bar) + ";"
		// + FXUtils.df1.format(range));
		// }

	}

	private double convertTo1dUSDPips(Instrument instrument, double d, int i) {
		if (instrument.getSecondaryCurrency().equals(
				Currency.getInstance("USD")))
			return d;

		List<IBar> converter = dailyConvertors.get(instrument
				.getSecondaryCurrency());
		if (converter == null || i >= converter.size()) {
			System.out.println("No converter for " + instrument.toString()
					+ " at 1d bar " + i);
			System.exit(1);
		}
		IBar c = converter.get(i);
		return calcConversion(instrument, d, c);
	}

	public double calcConversion(Instrument instrument, double d, IBar c) {
		if (!instrument.getSecondaryCurrency().equals(
				Currency.getInstance("USD"))) {
			if (instrument.getSecondaryCurrency().equals(
					Currency.getInstance("JPY")))
				return d / c.getClose() * 100;
			else
				return d / c.getClose();
		} else {
			return d * c.getClose();
		}
	}

	private double convertTo4hUSDPips(Instrument instrument, double d, int i) {
		if (instrument.getSecondaryCurrency().equals(
				Currency.getInstance("USD")))
			return d;

		List<IBar> converter = fourHConvertors.get(instrument
				.getSecondaryCurrency());
		if (converter == null || i >= converter.size()) {
			System.out.println("No converter for " + instrument.toString()
					+ " at 4h bar " + i);
			System.exit(1);
		}
		IBar c = converter.get(i);
		return calcConversion(instrument, d, c);
	}

	protected void prepareConvertors(IBar bidBar) throws JFException {

		// USD/CHF;GBP/JPY;AUD/CAD;GBP/CHF;USD/JPY;CHF/JPY;AUD/JPY;EUR/AUD;AUD/NZD;USD/CAD
		// Konvertori za USD trebaju za CHF, JPY, CAD, AUD, NZD

		long dailyBarTime = history.getPreviousBarStart(
				Period.DAILY_SUNDAY_IN_MONDAY, bidBar.getTime());
		List<IBar> chfBars = history.getBars(Instrument.USDCHF,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, Filter.WEEKENDS,
				FXUtils.YEAR_WORTH_OF_1d_BARS, dailyBarTime, 0);
		dailyConvertors.put(Currency.getInstance("CHF"), chfBars);
		List<IBar> jpyBars = history.getBars(Instrument.USDJPY,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, Filter.WEEKENDS,
				FXUtils.YEAR_WORTH_OF_1d_BARS, dailyBarTime, 0);
		dailyConvertors.put(Currency.getInstance("JPY"), jpyBars);
		List<IBar> cadBars = history.getBars(Instrument.USDCAD,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, Filter.WEEKENDS,
				FXUtils.YEAR_WORTH_OF_1d_BARS, dailyBarTime, 0);
		dailyConvertors.put(Currency.getInstance("CAD"), cadBars);
		List<IBar> audBars = history.getBars(Instrument.AUDUSD,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, Filter.WEEKENDS,
				FXUtils.YEAR_WORTH_OF_1d_BARS, dailyBarTime, 0);
		dailyConvertors.put(Currency.getInstance("AUD"), audBars);
		List<IBar> nzdBars = history.getBars(Instrument.NZDUSD,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, Filter.WEEKENDS,
				FXUtils.YEAR_WORTH_OF_1d_BARS, dailyBarTime, 0);
		dailyConvertors.put(Currency.getInstance("NZD"), nzdBars);
		List<IBar> gbpBars = history.getBars(Instrument.GBPUSD,
				Period.DAILY_SUNDAY_IN_MONDAY, OfferSide.BID, Filter.WEEKENDS,
				FXUtils.YEAR_WORTH_OF_1d_BARS, dailyBarTime, 0);
		dailyConvertors.put(Currency.getInstance("GBP"), gbpBars);

		long fourHBarTime = history.getPreviousBarStart(Period.FOUR_HOURS,
				bidBar.getTime());
		List<IBar> chf4HBars = history.getBars(Instrument.USDCHF,
				Period.FOUR_HOURS, OfferSide.BID, Filter.WEEKENDS,
				DAILY_BARS_TO_EXPLORE * 6, fourHBarTime, 0);
		fourHConvertors.put(Currency.getInstance("CHF"), chf4HBars);
		List<IBar> jpy4HBars = history.getBars(Instrument.USDJPY,
				Period.FOUR_HOURS, OfferSide.BID, Filter.WEEKENDS,
				DAILY_BARS_TO_EXPLORE * 6, fourHBarTime, 0);
		fourHConvertors.put(Currency.getInstance("JPY"), jpy4HBars);
		List<IBar> cad4HBars = history.getBars(Instrument.USDCAD,
				Period.FOUR_HOURS, OfferSide.BID, Filter.WEEKENDS,
				DAILY_BARS_TO_EXPLORE * 6, fourHBarTime, 0);
		fourHConvertors.put(Currency.getInstance("CAD"), cad4HBars);
		List<IBar> aud4HBars = history.getBars(Instrument.AUDUSD,
				Period.FOUR_HOURS, OfferSide.BID, Filter.WEEKENDS,
				DAILY_BARS_TO_EXPLORE * 6, fourHBarTime, 0);
		fourHConvertors.put(Currency.getInstance("AUD"), aud4HBars);
		List<IBar> nzd4HBars = history.getBars(Instrument.NZDUSD,
				Period.FOUR_HOURS, OfferSide.BID, Filter.WEEKENDS,
				DAILY_BARS_TO_EXPLORE * 6, fourHBarTime, 0);
		fourHConvertors.put(Currency.getInstance("NZD"), nzd4HBars);
		List<IBar> gbp4HBars = history.getBars(Instrument.GBPUSD,
				Period.FOUR_HOURS, OfferSide.BID, Filter.WEEKENDS,
				DAILY_BARS_TO_EXPLORE * 6, fourHBarTime, 0);
		fourHConvertors.put(Currency.getInstance("GBP"), gbp4HBars);
	}

	@Override
	public void onMessage(IMessage message) throws JFException {
	}

	@Override
	public void onAccount(IAccount account) throws JFException {
	}

	@Override
	public void onStop() throws JFException {
		log.close();
	}

	public RangeExplorer(Properties p) {
		super(p);
	}

	@Override
	protected String getStrategyName() {
		return "RangeExlorer";
	}

	@Override
	protected String getReportFileName() {
		return "RangeExplorer_";
	}

}
