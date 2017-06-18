package jforex.techanalysis.source;

import java.util.HashMap;
import java.util.Map;

import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.techanalysis.source.TechnicalSituation.OverallTASituation;
import jforex.techanalysis.source.TechnicalSituation.TASituationReason;
import jforex.techanalysis.Trend.FLAT_REGIME_CAUSE;
import jforex.techanalysis.Trend.TREND_STATE;
import jforex.utils.FXUtils;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IIndicators.MaType;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class FlexTASource {
	final public static String
		BULLISH_CANDLES = "Bullish candles",
		BEARISH_CANDLES = "Bearish candles",
		MAs = "Moving averages",
		MA20_SLOPE = "MA20 slope",
		MA50_SLOPE = "MA50 slope",
		MA100_SLOPE = "MA100 slope",
		MA200_SLOPE = "MA200 slope",
		MA_SLOPES_SCORE = "MA slopes slope",
		TREND_ID = "TrendID",
		MA200_HIGHEST = "MA200Highest",
		MA200_LOWEST = "MA200Lowest",
		MA200_IN_CHANNEL = "MA200InChannel",
		SMI = "SMI",
		STOCH = "Stoch",
		RSI3 = "RSI3",
		CHANNEL_POS = "Channel position",
		BBANDS_SQUEEZE_PERC = "BBands squeeze percentile",
		FLAT_REGIME = "Flat regime",
		MAs_DISTANCE_PERC = "MAs distance percentile",
		UPTREND_MAs_DISTANCE_PERC = "Uptrend MAs distance percentile",
		DOWNTREND_MAs_DISTANCE_PERC = "Downtrend MAs distance percentile",
		ATR = "ATR",
		ICHI = "Ichi",
		MA200MA100_TREND_DISTANCE_PERC = "MA200 MA100 Distance percentile",
		BBANDS = "BBands",
		TA_SITUATION = "TASituationDescription",
		CHANNEL_WIDTH_DIRECTION = "ChannelWidthDirection";
	
	protected IIndicators indicators = null;
	protected IHistory history = null;
	protected Filter filter = null;
	
	protected Volatility vola = null;
	protected Trend trend = null;
	protected TradeTrigger candles = null;
	protected Momentum momentum = null;
	protected Channel channel = null;
	
	protected Map<String, FlexTAValue> lastResult = null;

	public FlexTASource(IIndicators i, IHistory h, Filter f) {
		indicators = i;
		history = h;
		filter = f;
		
		vola = new Volatility(i);
		trend = new Trend(i);
		candles = new TradeTrigger(i, h, null);
		momentum = new Momentum(h, i);
		channel = new Channel(h, i);
	}
	
	public Map<String, FlexTAValue> calcTAValues(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		Map<String, FlexTAValue> result = new HashMap<String, FlexTAValue>();
		addMAs(instrument, period, bidBar, result);
		addBBands(instrument, period, bidBar, result);
		addChannelWidthDirection(instrument, period, bidBar, result);
		addSMI(instrument, period, bidBar, result);
		
		result.put(BULLISH_CANDLES, new FlexTAValue(BULLISH_CANDLES, candles.bullishReversalCandlePatternDesc(instrument, period, filter, OfferSide.ASK, askBar.getTime())));
		result.put(BEARISH_CANDLES, new FlexTAValue(BEARISH_CANDLES, candles.bearishReversalCandlePatternDesc(instrument, period, filter, OfferSide.BID, bidBar.getTime())));
		
		result.put(TREND_ID, new FlexTAValue(TREND_ID, trend.getTrendState(instrument, period, filter, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime())));
		result.put(MA200_HIGHEST, new FlexTAValue(MA200_HIGHEST, new Boolean(trend.isMA200Highest(instrument, period, filter, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime()))));
		result.put(MA200_LOWEST, new FlexTAValue(MA200_LOWEST, new Boolean(trend.isMA200Lowest(instrument, period, filter, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime()))));
		result.put(MA200_IN_CHANNEL, new FlexTAValue(MA200_IN_CHANNEL, new Boolean(isMA200InChannel(result))));

		result.put(STOCH, new FlexTAValue(STOCH, momentum.getStochs(instrument, period, filter, OfferSide.BID, bidBar.getTime(), 2), FXUtils.df1));
		result.put(RSI3, new FlexTAValue(RSI3, new Double(indicators.rsi(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 3, filter, 1, bidBar.getTime(), 0)[0]), FXUtils.df1));
		result.put(CHANNEL_POS, new FlexTAValue(CHANNEL_POS, new Double(channel.priceChannelPos(instrument, period, filter, OfferSide.BID, bidBar.getTime(), bidBar.getClose())), FXUtils.df1));
		result.put(BBANDS_SQUEEZE_PERC, new FlexTAValue(BBANDS_SQUEEZE_PERC, new Double(vola.getBBandsSqueezePercentile(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, filter, bidBar.getTime(), 20, FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df1));
		result.put(FLAT_REGIME, new FlexTAValue(FLAT_REGIME, trend.isFlatRegime(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, filter, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS, 30)));
		result.put(MAs_DISTANCE_PERC, new FlexTAValue(MAs_DISTANCE_PERC, new Double(trend.getMAsMaxDiffPercentile(instrument, period, filter, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df1));
		//result.put(UPTREND_MAs_DISTANCE_PERC, new FlexTAValue(UPTREND_MAs_DISTANCE_PERC, new Double(trend.getUptrendMAsMaxDiffPercentile(instrument, period, filter, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df1));
		//result.put(DOWNTREND_MAs_DISTANCE_PERC, new FlexTAValue(DOWNTREND_MAs_DISTANCE_PERC, new Double(trend.getDowntrendMAsMaxDiffPercentile(instrument, period, filter, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df1));
		result.put(ATR, new FlexTAValue(ATR, new Double(vola.getATR(instrument, period, filter, OfferSide.BID, bidBar.getTime(), 14)), FXUtils.df1));
		result.put(ICHI, new FlexTAValue(ICHI, trend.getIchi(history, instrument, period, OfferSide.BID, filter, bidBar.getTime())));
		result.put(MA200MA100_TREND_DISTANCE_PERC, new FlexTAValue(MA200MA100_TREND_DISTANCE_PERC, new Double(trend.getMA200MA100TrendDiffPercentile(instrument, period, filter, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df1));

		result.put(TA_SITUATION, new FlexTAValue(TA_SITUATION, assessTASituation(result, bidBar, askBar)));
		
		lastResult = result;
		return result;
	}

	private boolean isMA200InChannel(Map<String, FlexTAValue> taValues) {
		double[][] bBands = taValues.get(FlexTASource.BBANDS).getDa2DimValue();
		double 
			ma200 = taValues.get(FlexTASource.MAs).getDa2DimValue()[1][3],
			bBandsBottom = bBands[Channel.BOTTOM][0],
			bBandsTop = bBands[Channel.TOP][0];
		return ma200 <= bBandsTop && ma200 >= bBandsBottom;
	}

	private TechnicalSituation assessTASituation(Map<String, FlexTAValue> taValues, IBar bidBar, IBar askBar) {
		TREND_STATE entryTrendID = taValues.get(TREND_ID).getTrendStateValue();
		double 
			maDistance = taValues.get(MAs_DISTANCE_PERC).getDoubleValue(),
			bBandsSqueeze = taValues.get(BBANDS_SQUEEZE_PERC).getDoubleValue(),
			ma200ma100distance = taValues.get(MA200MA100_TREND_DISTANCE_PERC).getDoubleValue();
		FLAT_REGIME_CAUSE isFlat = (FLAT_REGIME_CAUSE)taValues.get(FLAT_REGIME).getValue();
		boolean 
			ma200Highest = taValues.get(MA200_HIGHEST).getBooleanValue(), 
			ma200Lowest = taValues.get(MA200_LOWEST).getBooleanValue(),
			ma200InChannel = taValues.get(MA200_IN_CHANNEL).getBooleanValue();
		double[][] smis = taValues.get(SMI).getDa2DimValue();
		double
			//fastSMI = smis[0][2],
			slowSMI = smis[1][2];
		
		// first fill out the description fields before deciding on definitive situation flag
		TechnicalSituation result = new TechnicalSituation();
		assessSMIState(taValues.get(SMI).getDa2DimValue(), result);
		assessStochState(taValues.get(STOCH).getDa2DimValue(), result);
			
		/*
		 * Summary section has the same structure for all the regimes. There are mandatory and optional parts (in <>)
		 * 1. Trend: 
		 * 		Strong uptrend|TREND_ID (MAs distance) 
		 * 		<, narrow channel: 21.7>
		 * 		<, all MAs in channel !>
		 * 		<(MA200 bullish (lowest), MA100-MA200 distance 71.2)>
		 * 		<, MA200 in channel !>
		 * 		<
		 */
		String 
			trendDesc = new String(),
			ma200Desc = new String(),
			bBandsDesc = new String();
		trendDesc += entryTrendID.toString() + " (" + FXUtils.df1.format(maDistance) + ")";
		if (ma200Highest)
			ma200Desc += " (MA200 bearish (highest), MA100-MA200 distance " + FXUtils.df1.format(ma200ma100distance) + ")";
		else if (ma200Lowest)
			ma200Desc += " (MA200 bullish (lowest), MA100-MA200 distance " + FXUtils.df1.format(ma200ma100distance) + ")";
		if (ma200InChannel)
			ma200Desc += ", MA200 in channel !";
		if (bBandsSqueeze < 25.0)
			bBandsDesc += ", narrow channel: " + FXUtils.df1.format(bBandsSqueeze);
		if (isFlat.equals(FLAT_REGIME_CAUSE.MAs_WITHIN_CHANNEL))
			bBandsDesc += ", all MAs in channel !";
		
		Momentum.SINGLE_LINE_STATE channelWidthDirection = (Momentum.SINGLE_LINE_STATE)taValues.get(FlexTASource.CHANNEL_WIDTH_DIRECTION).getValue();
		double[][] 
				mas = taValues.get(FlexTASource.MAs).getDa2DimValue(),
				bBands = taValues.get(FlexTASource.BBANDS).getDa2DimValue();
		boolean 
			highAboveAllMAs = askBar.getHigh() > mas[1][0] && askBar.getHigh() > mas[1][1] && askBar.getHigh() > mas[1][2] && askBar.getHigh() > mas[1][3],
			lowBelowAllMAs = bidBar.getLow() < mas[1][0] && bidBar.getLow() < mas[1][1] && bidBar.getLow() < mas[1][2] && bidBar.getLow() < mas[1][3],
			bullishMomentum = highAboveAllMAs && askBar.getHigh() > bBands[Volatility.BBANDS_TOP][0] 
							&& channelWidthDirection.equals(Momentum.SINGLE_LINE_STATE.RAISING_IN_MIDDLE)
							&& (result.smiState.equals(Momentum.SMI_STATE.BULLISH_BOTH_RAISING_IN_MIDDLE)
							|| result.smiState.equals(Momentum.SMI_STATE.BULLISH_OVERBOUGHT_BOTH)
							|| result.smiState.equals(Momentum.SMI_STATE.BULLISH_OVERBOUGHT_FAST_ABOVE_RAISING_SLOW)
							|| (result.smiState.equals(Momentum.SMI_STATE.BULLISH_WEAK_RAISING_IN_MIDDLE) && slowSMI > 0))
							&& result.stochState.toString().startsWith("BULLISH"),
			bearishMomentum = lowBelowAllMAs && bidBar.getLow() < bBands[Volatility.BBANDS_BOTTOM][0] 
							&& channelWidthDirection.equals(Momentum.SINGLE_LINE_STATE.RAISING_IN_MIDDLE)
							&& (result.smiState.equals(Momentum.SMI_STATE.BEARISH_BOTH_FALLING_IN_MIDDLE)
							|| result.smiState.equals(Momentum.SMI_STATE.BEARISH_OVERSOLD_BOTH)
							|| result.smiState.equals(Momentum.SMI_STATE.BEARISH_OVERSOLD_FAST_BELOW_FALLING_SLOW)
							|| (result.smiState.equals(Momentum.SMI_STATE.BEARISH_WEAK_FALLING_IN_MIDDLE)&& slowSMI < 0))
							&& result.stochState.toString().startsWith("BEARISH");
		
		if (!(bullishMomentum || bearishMomentum)
			&& !(result.stochState.equals(Momentum.STOCH_STATE.BULLISH_OVERBOUGHT_BOTH)
				|| result.stochState.equals(Momentum.STOCH_STATE.BEARISH_OVERSOLD_BOTH)
				|| result.smiState.equals(Momentum.SMI_STATE.BULLISH_OVERBOUGHT_BOTH)
				|| result.smiState.equals(Momentum.SMI_STATE.BEARISH_OVERSOLD_BOTH))
			&& maDistance < 25.0 &&
			(isFlat.equals(FLAT_REGIME_CAUSE.MAs_WITHIN_CHANNEL) || bBandsSqueeze < 25.0)) {
			result.taSituation = OverallTASituation.NEUTRAL;
			if (bBandsSqueeze < 25.0)
				result.taReason = TASituationReason.LOW_VOLA;
			else 
				result.taReason = TASituationReason.FLAT;
			result.txtSummary = "MAs distance: " + FXUtils.df1.format(maDistance)
				+ ", channel width: " + FXUtils.df1.format(bBandsSqueeze)
				+ ", trend ID: " + taValues.get(TREND_ID).getTrendStateValue().toString();
			if (isFlat.equals(FLAT_REGIME_CAUSE.MAs_WITHIN_CHANNEL))
				result.txtSummary += ", all MAs in channel !";
			result.txtSummary += ma200Desc;
			return result;
		}
		

		// the method to determine the technical situation is to go through most extreme/clear situations
		// and try to detect them. If none detected situation is rather unclear
		// Tests should be explicit lists of criteria, even if some lines are repeated.
		// Code clarity must be above conciseness !!!!
		// za jasan trend moguca i kombinacija i da su MAs vrlo blizu ali u ekstremnom rasporedu
		// TrendID = UP_STRONG && ma200 lowest / TrendID = DOWN_STRONG && ma200 highest		
		if (entryTrendID.equals(TREND_STATE.UP_STRONG)
			&& !ma200InChannel
			&& ma200Lowest 
			&& maDistance > 25.0) {
			result.taSituation = OverallTASituation.BULLISH;
			result.taReason = TASituationReason.TREND;
			result.txtSummary = "Strong uptrend (" + FXUtils.df1.format(maDistance) + ")";
			result.txtSummary += bBandsDesc + ma200Desc; 
			return result;
		}
		if (entryTrendID.equals(TREND_STATE.UP_STRONG)
			&& !ma200InChannel
			&& !ma200Highest
			&& (ma200Lowest || maDistance > 25.0)) {
			result.taSituation = OverallTASituation.BULLISH;
			result.taReason = TASituationReason.TREND;
			result.txtSummary = entryTrendID.toString() + " (" + FXUtils.df1.format(maDistance) + ")";
			result.txtSummary += bBandsDesc + ma200Desc; 
			return result;
		}
		if (entryTrendID.equals(TREND_STATE.UP_MILD)
			&& !ma200InChannel
			&& ma200Lowest 
			&& maDistance > 25.0) {
			result.taSituation = OverallTASituation.BULLISH;
			result.taReason = TASituationReason.TREND;
			result.txtSummary = "Up mild, strong (" + FXUtils.df1.format(maDistance) + ")";
			result.txtSummary += bBandsDesc + ma200Desc; 
			return result;
		}
		if (entryTrendID.equals(TREND_STATE.UP_MILD)
			&& !ma200InChannel
			&& !ma200Highest
			&& (ma200Lowest || maDistance > 25.0)) {
			result.taSituation = OverallTASituation.BULLISH;
			result.taReason = TASituationReason.TREND;
			result.txtSummary = entryTrendID.toString() + " (" + FXUtils.df1.format(maDistance) + ")";
			result.txtSummary += bBandsDesc + ma200Desc; 
			return result;
		}
		if (entryTrendID.equals(TREND_STATE.DOWN_STRONG)
			&& !ma200InChannel
			&& ma200Highest
			&& maDistance > 25.0) {
			result.taSituation = OverallTASituation.BEARISH;
			result.taReason = TASituationReason.TREND;
			result.txtSummary = "Strong downtrend (" + FXUtils.df1.format(maDistance) + ")";
			result.txtSummary += bBandsDesc + ma200Desc; 
			return result;
		}
		if (entryTrendID.equals(TREND_STATE.DOWN_STRONG)
			&& !ma200InChannel
			&& !ma200Lowest
			&& (ma200Highest || maDistance > 25.0)) {
			result.taSituation = OverallTASituation.BEARISH;
			result.taReason = TASituationReason.TREND;
			result.txtSummary = entryTrendID.toString() + " (" + FXUtils.df1.format(maDistance) + ")";
			result.txtSummary += bBandsDesc + ma200Desc; 
			return result;
		}		
		if (entryTrendID.equals(TREND_STATE.DOWN_MILD)
			&& !ma200InChannel
			&& ma200Highest
			&& maDistance > 25.0) {
			result.taSituation = OverallTASituation.BEARISH;
			result.taReason = TASituationReason.TREND;
			result.txtSummary = "Down mild, strong (" + FXUtils.df1.format(maDistance) + ")";
			result.txtSummary += bBandsDesc + ma200Desc; 
			return result;
		}
		if (entryTrendID.equals(TREND_STATE.DOWN_MILD)
			&& !ma200InChannel
			&& !ma200Lowest
			&& (ma200Highest || maDistance > 25.0)) {
			result.taSituation = OverallTASituation.BEARISH;
			result.taReason = TASituationReason.TREND;
			result.txtSummary = entryTrendID.toString() + " (" + FXUtils.df1.format(maDistance) + ")";
			result.txtSummary += bBandsDesc + ma200Desc; 
			return result;
		}		
		if (bullishMomentum) {
			result.taSituation = OverallTASituation.BULLISH;
			result.taReason = TASituationReason.MOMENTUM;
			result.txtSummary = "Bullish momentum";
			result.txtSummary += trendDesc + bBandsDesc + ma200Desc; 
			return result;
			
		}
		if (bearishMomentum) {
			result.taSituation = OverallTASituation.BEARISH;
			result.taReason = TASituationReason.MOMENTUM;
			result.txtSummary = "Bearish momentum";
			result.txtSummary += trendDesc + bBandsDesc + ma200Desc; 
			return result;				
		}
		
		// For long slow SMI not yet overbought and raising below fast SMI (no matter if it falls),
		// confirmed with Stoch fast raising above Stoch slow or both Stochs OB		
		// but these should decide on overall situation ONLY if there is not a clear flat - check further
		if (!isFlat.equals(FLAT_REGIME_CAUSE.NONE)) {
			result.taSituation = OverallTASituation.NEUTRAL;
			result.taReason = TASituationReason.FLAT;
			result.txtSummary = "Flat (" + isFlat.toString() + ")";
			result.txtSummary += trendDesc + bBandsDesc + ma200Desc; 
			return result;
		}
		
		result.taSituation = OverallTASituation.NEUTRAL;
		result.taReason = TASituationReason.NONE;
		result.txtSummary = "Unclear, (TrendID " + entryTrendID.toString() + ")";
		result.txtSummary += trendDesc + bBandsDesc + ma200Desc; 
		return result;
	}

	private void assessStochState(double[][] stochs, TechnicalSituation taSituation) {
		double 
			fastStochPrev = stochs[0][0], 
			slowStochPrev = stochs[1][0], 
			fastStochLast = stochs[0][1], 
			slowStochLast = stochs[1][1];
		taSituation.fastStoch = fastStochLast;
		taSituation.slowStoch = slowStochLast;
		
		if (fastStochLast <= 20 && slowStochLast <= 20)
			taSituation.stochState = Momentum.STOCH_STATE.BEARISH_OVERSOLD_BOTH;
		else if (fastStochLast >= 80 && slowStochLast >= 80)
			taSituation.stochState = Momentum.STOCH_STATE.BULLISH_OVERBOUGHT_BOTH;
		else if (slowStochLast >= 80
				&& fastStochPrev >= 80 && fastStochLast < 80)
			taSituation.stochState = Momentum.STOCH_STATE.BEARISH_CROSS_FROM_OVERBOUGTH;
		else if (slowStochLast >= 80)
			taSituation.stochState = Momentum.STOCH_STATE.BEARISH_WEAK_OVERBOUGHT_SLOW;
		else if (fastStochLast >= 80)
			taSituation.stochState = Momentum.STOCH_STATE.BULLISH_OVERBOUGHT_FAST;
		else if (slowStochLast <= 20
				&& fastStochPrev <= 20 && fastStochLast > 20)
			taSituation.stochState = Momentum.STOCH_STATE.BULLISH_CROSS_FROM_OVERSOLD;
		else if (slowStochLast <= 20)
			taSituation.stochState = Momentum.STOCH_STATE.BULLISH_WEAK_OVERSOLD_SLOW;
		else if (fastStochLast <= 20)
			taSituation.stochState = Momentum.STOCH_STATE.BEARISH_OVERSOLD_FAST;
		else if (slowStochPrev > fastStochPrev
				&& slowStochLast < fastStochLast)
			taSituation.stochState = Momentum.STOCH_STATE.BULLISH_CROSS;
		else if (slowStochPrev < fastStochPrev
				&& slowStochLast > fastStochLast)
			taSituation.stochState = Momentum.STOCH_STATE.BEARISH_CROSS;
		else if (fastStochLast > slowStochLast)
			taSituation.stochState = Momentum.STOCH_STATE.BULLISH_RAISING_IN_MIDDLE;
		else if (fastStochLast < slowStochLast)
			taSituation.stochState = Momentum.STOCH_STATE.BEARISH_FALLING_IN_MIDDLE;
		else
			taSituation.stochState = Momentum.STOCH_STATE.OTHER;
	}

	private void assessSMIState(double[][] smis, TechnicalSituation taSituation) {
		double
			fastSMIFirst = smis[0][0],
			fastSMIPrev = smis[0][1],
			fastSMILast = smis[0][2],
		
			slowSMIFirst = smis[1][0],
			slowSMIPrev = smis[1][1],
			slowSMILast = smis[1][2];
		taSituation.fastSMI = fastSMILast;
		taSituation.slowSMI = slowSMILast;

		if (slowSMILast <= -60 && slowSMILast < slowSMIPrev && slowSMIPrev < slowSMIFirst)
			taSituation.slowSMIState = Momentum.SINGLE_LINE_STATE.FALLING_OVERSOLD;
		else if (slowSMILast <= -60 && slowSMILast > slowSMIPrev && slowSMIPrev > slowSMIFirst)
			taSituation.slowSMIState = Momentum.SINGLE_LINE_STATE.RAISING_OVERSOLD;
		else if (slowSMILast <= -60 && slowSMILast >= slowSMIPrev && slowSMIPrev <= slowSMIFirst)
			taSituation.slowSMIState = Momentum.SINGLE_LINE_STATE.TICKED_UP_OVERSOLD;
		else if (slowSMILast <= -60 && slowSMILast <= slowSMIPrev && slowSMIPrev >= slowSMIFirst)
			taSituation.slowSMIState = Momentum.SINGLE_LINE_STATE.TICKED_DOWN_OVERSOLD;
		else if (slowSMILast >= 60 && slowSMILast < slowSMIPrev && slowSMIPrev < slowSMIFirst)
			taSituation.slowSMIState = Momentum.SINGLE_LINE_STATE.FALLING_OVERBOUGHT;
		else if (slowSMILast >= 60 && slowSMILast > slowSMIPrev && slowSMIPrev > slowSMIFirst)
			taSituation.slowSMIState = Momentum.SINGLE_LINE_STATE.RAISING_OVERBOUGHT;
		else if (slowSMILast >= 60 && slowSMILast >= slowSMIPrev && slowSMIPrev <= slowSMIFirst)
			taSituation.slowSMIState = Momentum.SINGLE_LINE_STATE.TICKED_UP_OVERBOUGHT;
		else if (slowSMILast >= 60 && slowSMILast <= slowSMIPrev && slowSMIPrev >= slowSMIFirst)
			taSituation.slowSMIState = Momentum.SINGLE_LINE_STATE.TICKED_DOWN_OVERBOUGHT;
		else if (slowSMILast > slowSMIPrev && slowSMIPrev > slowSMIFirst)
			taSituation.slowSMIState = Momentum.SINGLE_LINE_STATE.RAISING_IN_MIDDLE;
		else if (slowSMILast < slowSMIPrev && slowSMIPrev < slowSMIFirst)
			taSituation.slowSMIState = Momentum.SINGLE_LINE_STATE.FALLING_IN_MIDDLE;
		else if (slowSMILast <= slowSMIPrev && slowSMIPrev >= slowSMIFirst)
			taSituation.slowSMIState = Momentum.SINGLE_LINE_STATE.TICKED_DOWN_IN_MIDDLE;
		else if (slowSMILast >= slowSMIPrev && slowSMIPrev <= slowSMIFirst)
			taSituation.slowSMIState = Momentum.SINGLE_LINE_STATE.TICKED_UP_IN_MIDDLE;
		else
			taSituation.slowSMIState = Momentum.SINGLE_LINE_STATE.OTHER;
		
		if (fastSMILast <= -60 && fastSMILast < fastSMIPrev && fastSMIPrev < fastSMIFirst)
			taSituation.fastSMIState = Momentum.SINGLE_LINE_STATE.FALLING_OVERSOLD;
		else if (fastSMILast <= -60 && fastSMILast > fastSMIPrev && fastSMIPrev > fastSMIFirst)
			taSituation.fastSMIState = Momentum.SINGLE_LINE_STATE.RAISING_OVERSOLD;
		else if (fastSMILast <= -60 && fastSMILast >= fastSMIPrev && fastSMIPrev <= fastSMIFirst)
			taSituation.fastSMIState = Momentum.SINGLE_LINE_STATE.TICKED_UP_OVERSOLD;
		else if (fastSMILast <= -60 && fastSMILast <= fastSMIPrev && fastSMIPrev >= fastSMIFirst)
			taSituation.fastSMIState = Momentum.SINGLE_LINE_STATE.TICKED_DOWN_OVERSOLD;
		else if (fastSMILast >= 60 && fastSMILast < fastSMIPrev && fastSMIPrev < fastSMIFirst)
			taSituation.fastSMIState = Momentum.SINGLE_LINE_STATE.FALLING_OVERBOUGHT;
		else if (fastSMILast >= 60 && fastSMILast > fastSMIPrev && fastSMIPrev > fastSMIFirst)
			taSituation.fastSMIState = Momentum.SINGLE_LINE_STATE.RAISING_OVERBOUGHT;
		else if (fastSMILast >= 60 && fastSMILast >= fastSMIPrev && fastSMIPrev <= fastSMIFirst)
			taSituation.fastSMIState = Momentum.SINGLE_LINE_STATE.TICKED_UP_OVERBOUGHT;
		else if (fastSMILast >= 60 && fastSMILast <= fastSMIPrev && fastSMIPrev >= fastSMIFirst)
			taSituation.fastSMIState = Momentum.SINGLE_LINE_STATE.TICKED_DOWN_OVERBOUGHT;
		else if (fastSMILast > fastSMIPrev && fastSMIPrev > fastSMIFirst)
			taSituation.fastSMIState = Momentum.SINGLE_LINE_STATE.RAISING_IN_MIDDLE;
		else if (fastSMILast < fastSMIPrev && fastSMIPrev < fastSMIFirst)
			taSituation.fastSMIState = Momentum.SINGLE_LINE_STATE.FALLING_IN_MIDDLE;
		else if (fastSMILast <= fastSMIPrev && fastSMIPrev >= fastSMIFirst)
			taSituation.fastSMIState = Momentum.SINGLE_LINE_STATE.TICKED_DOWN_IN_MIDDLE;
		else if (fastSMILast >= fastSMIPrev && fastSMIPrev <= fastSMIFirst)
			taSituation.fastSMIState = Momentum.SINGLE_LINE_STATE.TICKED_UP_IN_MIDDLE;
		else
			taSituation.fastSMIState = Momentum.SINGLE_LINE_STATE.OTHER;
		
		if (fastSMILast <= -60.0 && slowSMILast <= -60.0)
			taSituation.smiState = Momentum.SMI_STATE.BEARISH_OVERSOLD_BOTH;		
		else if (fastSMILast <= -60.0 && fastSMILast < slowSMILast
				&& (taSituation.slowSMIState.equals(Momentum.SINGLE_LINE_STATE.FALLING_IN_MIDDLE)
					|| taSituation.slowSMIState.equals(Momentum.SINGLE_LINE_STATE.TICKED_DOWN_IN_MIDDLE)))
			taSituation.smiState = Momentum.SMI_STATE.BEARISH_OVERSOLD_FAST_BELOW_FALLING_SLOW;
		else if (fastSMILast > -60.0 && slowSMILast <= -60.0
				&& fastSMILast > slowSMILast
				&& (taSituation.fastSMIState.equals(Momentum.SINGLE_LINE_STATE.RAISING_IN_MIDDLE)
					|| taSituation.fastSMIState.equals(Momentum.SINGLE_LINE_STATE.TICKED_UP_IN_MIDDLE)))
			taSituation.smiState = Momentum.SMI_STATE.BULLISH_WEAK_OVERSOLD_SLOW_BELOW_RAISING_FAST;
		else if (fastSMILast > -60.0 && slowSMILast <= -60.0
				&& fastSMILast > slowSMILast)
			taSituation.smiState = Momentum.SMI_STATE.BULLISH_WEAK_OVERSOLD_SLOW_BELOW_FAST;
		else if (fastSMILast >= 60.0 && slowSMILast >= 60.0)
			taSituation.smiState = Momentum.SMI_STATE.BULLISH_OVERBOUGHT_BOTH;
		else if (fastSMILast >= 60.0 && fastSMILast > slowSMILast
			&& (taSituation.slowSMIState.equals(Momentum.SINGLE_LINE_STATE.RAISING_IN_MIDDLE)
				|| taSituation.slowSMIState.equals(Momentum.SINGLE_LINE_STATE.TICKED_UP_IN_MIDDLE)))
			taSituation.smiState = Momentum.SMI_STATE.BULLISH_OVERBOUGHT_FAST_ABOVE_RAISING_SLOW;
		else if (slowSMILast >= 60.0 && fastSMILast < 60
				&& fastSMILast < slowSMILast)
			taSituation.smiState = Momentum.SMI_STATE.BEARISH_WEAK_OVERBOUGHT_SLOW_ABOVE_FAST;
		else if (slowSMILast >= 60.0 && fastSMILast < slowSMILast
				&& (taSituation.fastSMIState.equals(Momentum.SINGLE_LINE_STATE.FALLING_IN_MIDDLE)
					|| taSituation.fastSMIState.equals(Momentum.SINGLE_LINE_STATE.TICKED_DOWN_IN_MIDDLE)))
			taSituation.smiState = Momentum.SMI_STATE.BEARISH_WEAK_OVERBOUGHT_SLOW_ABOVE_FALLING_FAST;
		else if (fastSMILast > slowSMILast
				&& (taSituation.slowSMIState.equals(Momentum.SINGLE_LINE_STATE.RAISING_IN_MIDDLE)
					|| taSituation.slowSMIState.equals(Momentum.SINGLE_LINE_STATE.TICKED_UP_IN_MIDDLE))
				&& (taSituation.fastSMIState.equals(Momentum.SINGLE_LINE_STATE.RAISING_IN_MIDDLE)
					|| taSituation.fastSMIState.equals(Momentum.SINGLE_LINE_STATE.TICKED_UP_IN_MIDDLE)))
			taSituation.smiState = Momentum.SMI_STATE.BULLISH_BOTH_RAISING_IN_MIDDLE;
		else if (fastSMILast < slowSMILast
				&& (taSituation.fastSMIState.equals(Momentum.SINGLE_LINE_STATE.FALLING_IN_MIDDLE)
					|| taSituation.fastSMIState.equals(Momentum.SINGLE_LINE_STATE.TICKED_DOWN_IN_MIDDLE))
				&& (taSituation.slowSMIState.equals(Momentum.SINGLE_LINE_STATE.FALLING_IN_MIDDLE)
					|| taSituation.slowSMIState.equals(Momentum.SINGLE_LINE_STATE.TICKED_DOWN_IN_MIDDLE)))
			taSituation.smiState = Momentum.SMI_STATE.BEARISH_BOTH_FALLING_IN_MIDDLE;
		else if (fastSMILast > slowSMILast
				&& !taSituation.slowSMIState.equals(Momentum.SINGLE_LINE_STATE.FALLING_IN_MIDDLE))
			taSituation.smiState = Momentum.SMI_STATE.BULLISH_WEAK_RAISING_IN_MIDDLE;
		else if (fastSMILast >= slowSMILast
				&& ((!taSituation.slowSMIState.equals(Momentum.SINGLE_LINE_STATE.RAISING_IN_MIDDLE)
				&& !taSituation.fastSMIState.equals(Momentum.SINGLE_LINE_STATE.RAISING_IN_MIDDLE))
				|| taSituation.slowSMIState.toString().contains("FALLING")))
			taSituation.smiState = Momentum.SMI_STATE.BEARISH_WEAK_FALLING_IN_MIDDLE;
		else if (fastSMILast < slowSMILast
				&& !taSituation.slowSMIState.equals(Momentum.SINGLE_LINE_STATE.RAISING_IN_MIDDLE))
			taSituation.smiState = Momentum.SMI_STATE.BEARISH_WEAK_FALLING_IN_MIDDLE;
		else if (fastSMILast <= slowSMILast
				&& ((!taSituation.slowSMIState.equals(Momentum.SINGLE_LINE_STATE.FALLING_IN_MIDDLE)
				&& !taSituation.fastSMIState.equals(Momentum.SINGLE_LINE_STATE.FALLING_IN_MIDDLE))
					|| taSituation.slowSMIState.toString().contains("RAISING")))
			taSituation.smiState = Momentum.SMI_STATE.BULLISH_WEAK_RAISING_IN_MIDDLE;
		else
			taSituation.smiState = Momentum.SMI_STATE.OTHER;
	}

	private void addBBands(Instrument instrument, Period period, IBar bidBar, Map<String, FlexTAValue> result) throws JFException {
		double[][] bBands = indicators.bbands(instrument, period, OfferSide.BID, AppliedPrice.CLOSE, 20, 2.0, 2.0, MaType.SMA, filter, 1, bidBar.getTime(), 0);
		result.put(BBANDS, new FlexTAValue(BBANDS, bBands, instrument.getPipScale() == 5 ? FXUtils.df5 : FXUtils.df2));
	}
	
	private void addChannelWidthDirection(Instrument instrument, Period period, IBar bidBar, Map<String, FlexTAValue> result) throws JFException {
		double[][] bBands = indicators.bbands(instrument, period, OfferSide.BID, AppliedPrice.CLOSE, 20, 2.0, 2.0, MaType.SMA, filter, 3, bidBar.getTime(), 0);
		double
			lastChannelWidth = bBands[Volatility.BBANDS_TOP][2] - bBands[Volatility.BBANDS_BOTTOM][2],
			middleChannelWidth = bBands[Volatility.BBANDS_TOP][1] - bBands[Volatility.BBANDS_BOTTOM][1],
			firstChannelWidth = bBands[Volatility.BBANDS_TOP][0] - bBands[Volatility.BBANDS_BOTTOM][0];
		
		result.put(CHANNEL_WIDTH_DIRECTION, new FlexTAValue(CHANNEL_WIDTH_DIRECTION, FXUtils.getLineDirection(firstChannelWidth, middleChannelWidth, lastChannelWidth)));
	}

	private void addSMI(Instrument instrument, Period period, IBar bidBar, Map<String, FlexTAValue> result) throws JFException {
		double[][] 
			slowSMI = indicators.smi(instrument, period, OfferSide.BID,	50, 15, 5, 3, filter, 3, bidBar.getTime(), 0), 
			fastSMI = indicators.smi(instrument, period, OfferSide.BID, 10, 3, 5, 3, filter, 3,	bidBar.getTime(), 0),
			smis = new double[2][3];
		
		// first fast SMIs in chronological order, then slow ones
		smis[0][0] = fastSMI[0][0];
		smis[0][1] = fastSMI[0][1];
		smis[0][2] = fastSMI[0][2];
		
		smis[1][0] = slowSMI[0][0];
		smis[1][1] = slowSMI[0][1];
		smis[1][2] = slowSMI[0][2];
		
		result.put(SMI, new FlexTAValue(SMI, smis, instrument.getPipScale() == 5 ? FXUtils.df5 : FXUtils.df2));
	}

	protected void addMAs(Instrument instrument, Period period, IBar bidBar, Map<String, FlexTAValue> result) throws JFException {
		double[] 
				mas20 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 20, filter, 3, bidBar.getTime(), 0),
				mas50 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, filter, 3, bidBar.getTime(), 0),
				mas100 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100, filter, 3, bidBar.getTime(), 0),
				mas200 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, filter, 3, bidBar.getTime(), 0);
		double[][] mas = new double[2][4];
		// first block - previous, last block - current bar values
		mas[0][0] = mas20[1];
		mas[0][1] = mas50[1];
		mas[0][2] = mas100[1];
		mas[0][3] = mas200[1];
		
		mas[1][0] = mas20[2];
		mas[1][1] = mas50[2];
		mas[1][2] = mas100[2];
		mas[1][3] = mas200[2];
		result.put(MAs, new FlexTAValue(MAs, mas, instrument.getPipScale() == 5 ? FXUtils.df5 : FXUtils.df2));
		
		Momentum.SINGLE_LINE_STATE
			ma20Slope = FXUtils.getLineDirection(mas20[0], mas20[1], mas20[2]),
			ma50Slope = FXUtils.getLineDirection(mas50[0], mas50[1], mas50[2]),
			ma100Slope = FXUtils.getLineDirection(mas100[0], mas100[1], mas100[2]),
			ma200Slope = FXUtils.getLineDirection(mas200[0], mas200[1], mas200[2]);
		result.put(MA20_SLOPE, new FlexTAValue(MA20_SLOPE, ma20Slope));
		result.put(MA50_SLOPE, new FlexTAValue(MA50_SLOPE, ma50Slope));
		result.put(MA100_SLOPE, new FlexTAValue(MA100_SLOPE, ma100Slope));
		result.put(MA200_SLOPE, new FlexTAValue(MA200_SLOPE, ma200Slope));
		int
			bullishCnt = 0,
			bearishCnt = 0;
		if (ma20Slope.equals(Momentum.SINGLE_LINE_STATE.RAISING_IN_MIDDLE) || ma20Slope.equals(Momentum.SINGLE_LINE_STATE.TICKED_UP_IN_MIDDLE))
			bullishCnt++;
		if (ma20Slope.equals(Momentum.SINGLE_LINE_STATE.FALLING_IN_MIDDLE) || ma20Slope.equals(Momentum.SINGLE_LINE_STATE.TICKED_DOWN_IN_MIDDLE))
			bearishCnt++;
		if (ma50Slope.equals(Momentum.SINGLE_LINE_STATE.RAISING_IN_MIDDLE) || ma50Slope.equals(Momentum.SINGLE_LINE_STATE.TICKED_UP_IN_MIDDLE))
			bullishCnt++;
		if (ma50Slope.equals(Momentum.SINGLE_LINE_STATE.FALLING_IN_MIDDLE) || ma50Slope.equals(Momentum.SINGLE_LINE_STATE.TICKED_DOWN_IN_MIDDLE))
			bearishCnt++;
		if (ma100Slope.equals(Momentum.SINGLE_LINE_STATE.RAISING_IN_MIDDLE) || ma100Slope.equals(Momentum.SINGLE_LINE_STATE.TICKED_UP_IN_MIDDLE))
			bullishCnt++;
		if (ma100Slope.equals(Momentum.SINGLE_LINE_STATE.FALLING_IN_MIDDLE) || ma100Slope.equals(Momentum.SINGLE_LINE_STATE.TICKED_DOWN_IN_MIDDLE))
			bearishCnt++;
		if (ma200Slope.equals(Momentum.SINGLE_LINE_STATE.RAISING_IN_MIDDLE) || ma200Slope.equals(Momentum.SINGLE_LINE_STATE.TICKED_UP_IN_MIDDLE))
			bullishCnt++;
		if (ma200Slope.equals(Momentum.SINGLE_LINE_STATE.FALLING_IN_MIDDLE) || ma200Slope.equals(Momentum.SINGLE_LINE_STATE.TICKED_DOWN_IN_MIDDLE))
			bearishCnt++;
		result.put(MA_SLOPES_SCORE, new FlexTAValue(MA_SLOPES_SCORE, FXUtils.if1.format(bullishCnt) + ":" + FXUtils.if1.format(bearishCnt)));
	}

}
