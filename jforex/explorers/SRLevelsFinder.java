package jforex.explorers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import jforex.BasicTAStrategy;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.TradeTrigger.TriggerDesc;
import jforex.utils.ClimberProperties;
import jforex.utils.FXUtils;

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

public class SRLevelsFinder extends BasicTAStrategy implements IStrategy {

	public class PriceZoneDesc {

		public double bottom = 0.0, top = 0.0;

		protected List<TriggerDesc> oneBars = new ArrayList<TriggerDesc>(),
				twoBars = new ArrayList<TriggerDesc>(),
				threeBars = new ArrayList<TriggerDesc>();

		protected double oneBarsTotalSignalSizes = 0,
				twoBarsTotalSignalSizes = 0, threeBarsTotalSignalSizes = 0;

		public PriceZoneDesc(double bottom, double top) {
			super();
			this.bottom = bottom;
			this.top = top;
		}

		public long totalSignals() {
			return noOfOneBars() + noOfTwoBars() + noOfThreeBars();
		}

		public double totalSignalSizes() {
			return oneBarsTotalSignalSizes + twoBarsTotalSignalSizes
					+ threeBarsTotalSignalSizes;
		}

		public int noOfOneBars() {
			return oneBars.size();
		}

		public int noOfTwoBars() {
			return twoBars.size();
		}

		public int noOfThreeBars() {
			return threeBars.size();
		}

		public void addToOneBars(TriggerDesc currCandle) {
			oneBars.add(currCandle);
			oneBarsTotalSignalSizes += currCandle.reversalSize;
		}

		public void addToTwoBars(TriggerDesc currCandle) {
			twoBars.add(currCandle);
			twoBarsTotalSignalSizes += currCandle.reversalSize;
		}

		public void addToThreeBars(TriggerDesc currCandle) {
			threeBars.add(currCandle);
			threeBarsTotalSignalSizes += currCandle.reversalSize;
		}

		public List<TriggerDesc> getOneBars() {
			return oneBars;
		}

		public List<TriggerDesc> getTwoBars() {
			return twoBars;
		}

		public List<TriggerDesc> getThreeBars() {
			return threeBars;
		}
	}

	List<TradeTrigger.TriggerDesc> candidatesAll = new ArrayList<TradeTrigger.TriggerDesc>();
	List<TradeTrigger.TriggerDesc> candidatesSupports = new ArrayList<TradeTrigger.TriggerDesc>();
	List<TradeTrigger.TriggerDesc> candidatesResistances = new ArrayList<TradeTrigger.TriggerDesc>();

	public SRLevelsFinder(ClimberProperties properties) {
		super(properties);
	}

	@Override
	public void onStart(IContext context) throws JFException {
		super.onStartExec(context);
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar,
			IBar bidBar) throws JFException {
		if (!period.equals(Period.THIRTY_MINS)
				|| !instrument.equals(Instrument.EURUSD))
			return;

		if (!tradingAllowed(bidBar.getTime()))
			return;

		TradeTrigger.TriggerDesc bullishTrigger = tradeTrigger
				.bullishReversalCandlePatternDesc(instrument, period,
						OfferSide.BID, bidBar.getTime());
		if (bullishTrigger != null && bullishTrigger.channelPosition < 50.0) {
			candidatesAll.add(bullishTrigger);
			candidatesSupports.add(bullishTrigger);
		}

		TradeTrigger.TriggerDesc bearishTrigger = tradeTrigger
				.bearishReversalCandlePatternDesc(instrument, period,
						OfferSide.BID, bidBar.getTime());
		if (bearishTrigger != null && bearishTrigger.channelPosition > 50.0) {
			candidatesAll.add(bearishTrigger);
			candidatesResistances.add(bearishTrigger);
		}
	}

	@Override
	public void onMessage(IMessage message) throws JFException {
	}

	@Override
	public void onAccount(IAccount account) throws JFException {
	}

	@Override
	public void onStop() throws JFException {
		String currReport = "Candle-based support/resistance levels for period "
				+ conf.getProperty("testIntervalStart")
				+ " - "
				+ conf.getProperty("testIntervalEnd")
				+ "\nAll levels found observed together:\n"
				+ createSRLevelsReport(candidatesAll);
		currReport += "\nOnly support levels (bullish triggers):\n"
				+ createSRLevelsReport(candidatesSupports);
		currReport += "\nOnly resistance levels (bearish triggers):\n"
				+ createSRLevelsReport(candidatesResistances);
		currReport += "\n\nList of all candle patterns in descending size order:\n"
				+ createSingleCandlesReport(candidatesAll);

		log.print(currReport);
		log.close();
	}

	private String createSingleCandlesReport(List<TriggerDesc> candidates) {
		Collections.sort(candidates, new Comparator<TriggerDesc>() {
			public int compare(TriggerDesc o1, TriggerDesc o2) {
				// in descending srLevelOrder
				if (o1.reversalSize > o2.reversalSize)
					return -1;
				else if (o1.reversalSize < o2.reversalSize)
					return 1;
				return 0;
			}
		});

		String result = new String();
		int i = 1;
		for (TriggerDesc curr : candidates) {
			result += i++
					+ ". "
					+ FXUtils.getFormatedTimeCET(curr.getLastBar().getTime())
					+ ": "
					+ curr.type.toString()
					+ ", S/R level "
					+ FXUtils.df4.format(curr.srLevel)
					+ ", channel position "
					+ FXUtils.df1.format(curr.channelPosition)
					+ ", reversal size "
					+ FXUtils.df1.format(curr.reversalSize
							* Math.pow(10.0, Instrument.EURUSD.getPipScale()))
					+ " pips\n";
		}
		return result;
	}

	private String createSRLevelsReport(List<TriggerDesc> candidates) {
		Collections.sort(candidates, new Comparator<TriggerDesc>() {
			public int compare(TriggerDesc o1, TriggerDesc o2) {
				// in descending srLevelOrder
				if (o1.srLevel > o2.srLevel)
					return -1;
				else if (o1.srLevel < o2.srLevel)
					return 1;
				return 0;
			}
		});

		double maxValue = candidates.get(0).srLevel, minValue = candidates
				.get(candidates.size() - 1).srLevel;

		// now create the price zones of 10 pips (experiments possible with ATR
		// average etc)
		List<PriceZoneDesc> priceZones = new ArrayList<PriceZoneDesc>();
		for (double currBlockTop = maxValue; currBlockTop > minValue; currBlockTop -= 0.0010) {
			priceZones.add(new PriceZoneDesc(currBlockTop - 0.0010,
					currBlockTop));
		}

		// now do the counting
		for (TriggerDesc currCandle : candidates) {
			for (PriceZoneDesc currPriceZone : priceZones) {
				if (currCandle.srLevel >= currPriceZone.bottom
						&& currCandle.srLevel <= currPriceZone.top) {
					if (currCandle.type.toString().contains("_1_BAR"))
						currPriceZone.addToOneBars(currCandle);
					else if (currCandle.type.toString().contains("_2_BARS"))
						currPriceZone.addToTwoBars(currCandle);
					else if (currCandle.type.toString().contains("_3_BARS"))
						currPriceZone.addToThreeBars(currCandle);
					// PriceZones mutually exclusive i.e. candle can belong only
					// to one
					break;
				}
			}
		}

		// OK. Sort the zones in descending order of candle count and print the
		// report
		Collections.sort(priceZones, new Comparator<PriceZoneDesc>() {
			public int compare(PriceZoneDesc o1, PriceZoneDesc o2) {
				// in descending order of total signals found
				if (o1.totalSignals() > o2.totalSignals())
					return -1;
				else if (o1.totalSignals() < o2.totalSignals())
					return 1;
				return 0;
			}
		});

		String result = new String();
		result += "List of price zones in descending order of total candle signals found\n";
		int i = 1;
		for (PriceZoneDesc currZone : priceZones) {
			result += "\n----------------------------------------------------------------------------------------------------------------\n"
					+ i++
					+ ". "
					+ FXUtils.df4.format(currZone.bottom)
					+ " - "
					+ FXUtils.df4.format(currZone.top)
					+ ": total signals "
					+ currZone.totalSignals()
					+ " (1-bar signals "
					+ currZone.noOfOneBars()
					+ ", 2-bar signals "
					+ currZone.noOfTwoBars()
					+ ", 3-bar signals "
					+ currZone.noOfThreeBars()
					+ ")\n----------------------------------------------------------------------------------------------------------------\n";
			if (currZone.noOfOneBars() > 0) {
				result += "One bar signals:\n";
				for (TriggerDesc curr : currZone.getOneBars()) {
					result += FXUtils.getFormatedTimeCET(curr.getLastBar()
							.getTime())
							+ ": "
							+ curr.type.toString()
							+ ", reversal size "
							+ FXUtils.df1.format(curr.reversalSize
									* Math.pow(10.0,
											Instrument.EURUSD.getPipScale()))
							+ " pips\n";
				}
			}
			if (currZone.noOfTwoBars() > 0) {
				result += "Two bars signals:\n";
				for (TriggerDesc curr : currZone.getTwoBars()) {
					result += FXUtils.getFormatedTimeCET(curr.getLastBar()
							.getTime())
							+ ": "
							+ curr.type.toString()
							+ ", reversal size "
							+ FXUtils.df1.format(curr.reversalSize
									* Math.pow(10.0,
											Instrument.EURUSD.getPipScale()))
							+ " pips\n";
				}
			}
			if (currZone.noOfThreeBars() > 0) {
				result += "Three bar signals:\n";
				for (TriggerDesc curr : currZone.getThreeBars()) {
					result += FXUtils.getFormatedTimeCET(curr.getLastBar()
							.getTime())
							+ ": "
							+ curr.type.toString()
							+ ", reversal size "
							+ FXUtils.df1.format(curr.reversalSize
									* Math.pow(10.0,
											Instrument.EURUSD.getPipScale()))
							+ " pips\n";
				}
			}
		}

		// For experiment, sort the zones in descending order of reversal sizes
		// and print such report
		Collections.sort(priceZones, new Comparator<PriceZoneDesc>() {
			public int compare(PriceZoneDesc o1, PriceZoneDesc o2) {
				// in descending order of total signals found
				if (o1.totalSignalSizes() > o2.totalSignalSizes())
					return -1;
				else if (o1.totalSignalSizes() < o2.totalSignalSizes())
					return 1;
				return 0;
			}
		});

		result += "\nList of price zones in descending order of total signal sizes\n";
		i = 1;
		for (PriceZoneDesc currZone : priceZones) {
			result += "\n----------------------------------------------------------------------------------------------------------------\n"
					+ i++
					+ ". "
					+ FXUtils.df4.format(currZone.bottom)
					+ " - "
					+ FXUtils.df4.format(currZone.top)
					+ ": total signal sizes "
					+ FXUtils.df1.format(currZone.totalSignalSizes()
							* Math.pow(10.0, Instrument.EURUSD.getPipScale()))
					+ "\n----------------------------------------------------------------------------------------------------------------\n";
			if (currZone.noOfOneBars() > 0) {
				result += "One bar signals:\n";
				for (TriggerDesc curr : currZone.getOneBars()) {
					result += FXUtils.getFormatedTimeCET(curr.getLastBar()
							.getTime())
							+ ": "
							+ curr.type.toString()
							+ ", reversal size "
							+ FXUtils.df1.format(curr.reversalSize
									* Math.pow(10.0,
											Instrument.EURUSD.getPipScale()))
							+ " pips\n";
				}
			}
			if (currZone.noOfTwoBars() > 0) {
				result += "Two bars signals:\n";
				for (TriggerDesc curr : currZone.getTwoBars()) {
					result += FXUtils.getFormatedTimeCET(curr.getLastBar()
							.getTime())
							+ ": "
							+ curr.type.toString()
							+ ", reversal size "
							+ FXUtils.df1.format(curr.reversalSize
									* Math.pow(10.0,
											Instrument.EURUSD.getPipScale()))
							+ " pips\n";
				}
			}
			if (currZone.noOfThreeBars() > 0) {
				result += "Three bar signals:\n";
				for (TriggerDesc curr : currZone.getThreeBars()) {
					result += FXUtils.getFormatedTimeCET(curr.getLastBar()
							.getTime())
							+ ": "
							+ curr.type.toString()
							+ ", reversal size "
							+ FXUtils.df1.format(curr.reversalSize
									* Math.pow(10.0,
											Instrument.EURUSD.getPipScale()))
							+ " pips\n";
				}
			}
		}

		return result;
	}

	@Override
	protected String getStrategyName() {
		return "SRLevelsFinder";
	}

	@Override
	protected String getReportFileName() {
		return getStrategyName() + "_";
	}

}
