package jforex.techanalysis.source;

import java.util.HashMap;
import java.util.Map;

import jforex.techanalysis.Channel;
import jforex.techanalysis.Momentum;
import jforex.techanalysis.TradeTrigger;
import jforex.techanalysis.Trend;
import jforex.techanalysis.Volatility;
import jforex.utils.FXUtils;

import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class FlexTASource {
	final public static String
		BULLISH_CANDLES = "Bullish candles",
		BEARISH_CANDLES = "Bearish candles",
		MAs = "Moving averages",
		TREND_ID = "TrendID",
		MA200_HIGHEST = "MA200Highest",
		MA200_LOWEST = "MA200Lowest",
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
		MA200MA100_TREND_DISTANCE_PERC = "MA200 MA100 Distance percentile";
	
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
		
		result.put(BULLISH_CANDLES, new FlexTAValue(BULLISH_CANDLES, candles.bullishReversalCandlePatternDesc(instrument, period, filter, OfferSide.ASK, askBar.getTime())));
		result.put(BEARISH_CANDLES, new FlexTAValue(BEARISH_CANDLES, candles.bearishReversalCandlePatternDesc(instrument, period, filter, OfferSide.BID, bidBar.getTime())));
		
		addMAs(instrument, period, bidBar, result);
		result.put(TREND_ID, new FlexTAValue(TREND_ID, trend.getTrendState(instrument, period, filter, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime())));
		result.put(MA200_HIGHEST, new FlexTAValue(MA200_HIGHEST, new Boolean(trend.isMA200Highest(instrument, period, filter, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime()))));
		result.put(MA200_LOWEST, new FlexTAValue(MA200_LOWEST, new Boolean(trend.isMA200Lowest(instrument, period, filter, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime()))));

		addSMI(instrument, period, bidBar, result);
		result.put(STOCH, new FlexTAValue(STOCH, momentum.getStochs(instrument, period, filter, OfferSide.BID, bidBar.getTime(), 2), FXUtils.df1));
		result.put(RSI3, new FlexTAValue(RSI3, new Double(indicators.rsi(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 3, filter, 1, bidBar.getTime(), 0)[0]), FXUtils.df1));
		result.put(CHANNEL_POS, new FlexTAValue(CHANNEL_POS, new Double(channel.priceChannelPos(instrument, period, filter, OfferSide.BID, bidBar.getTime(), bidBar.getClose())), FXUtils.df1));
		result.put(BBANDS_SQUEEZE_PERC, new FlexTAValue(BBANDS_SQUEEZE_PERC, new Double(vola.getBBandsSqueezePercentile(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, filter, bidBar.getTime(), 20, FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df1));
		result.put(FLAT_REGIME, new FlexTAValue(FLAT_REGIME, trend.isFlatRegime(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, filter, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS, 30)));
		result.put(MAs_DISTANCE_PERC, new FlexTAValue(MAs_DISTANCE_PERC, new Double(trend.getMAsMaxDiffPercentile(instrument, period, filter, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df1));
		result.put(UPTREND_MAs_DISTANCE_PERC, new FlexTAValue(UPTREND_MAs_DISTANCE_PERC, new Double(trend.getUptrendMAsMaxDiffPercentile(instrument, period, filter, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df1));
		result.put(DOWNTREND_MAs_DISTANCE_PERC, new FlexTAValue(DOWNTREND_MAs_DISTANCE_PERC, new Double(trend.getDowntrendMAsMaxDiffPercentile(instrument, period, filter, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df1));
		result.put(ATR, new FlexTAValue(ATR, new Double(vola.getATR(instrument, period, filter, OfferSide.BID, bidBar.getTime(), 14)), FXUtils.df1));
		result.put(ICHI, new FlexTAValue(ICHI, trend.getIchi(history, instrument, period, OfferSide.BID, filter, bidBar.getTime())));
		result.put(MA200MA100_TREND_DISTANCE_PERC, new FlexTAValue(MA200MA100_TREND_DISTANCE_PERC, new Double(trend.getMA200MA100TrendDiffPercentile(instrument, period, filter, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, bidBar.getTime(), FXUtils.YEAR_WORTH_OF_4H_BARS)), FXUtils.df1));

		lastResult = result;
		return result;
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
		
		result.put(SMI, new FlexTAValue(SMI, smis, FXUtils.df5));
	}

	protected void addMAs(Instrument instrument, Period period, IBar bidBar, Map<String, FlexTAValue> result) throws JFException {
		double[] 
				mas20 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 20, filter, 2, bidBar.getTime(), 0),
				mas50 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 50, filter, 2, bidBar.getTime(), 0),
				mas100 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 100, filter, 2, bidBar.getTime(), 0),
				mas200 = indicators.sma(instrument, period, OfferSide.BID, IIndicators.AppliedPrice.CLOSE, 200, filter, 2, bidBar.getTime(), 0);
		double[][] mas = new double[2][4];
		// first block - previous, last block - current bar values
		mas[0][0] = mas20[0];
		mas[0][1] = mas50[0];
		mas[0][2] = mas100[0];
		mas[0][3] = mas200[0];
		
		mas[1][0] = mas20[1];
		mas[1][1] = mas50[1];
		mas[1][2] = mas100[1];
		mas[1][3] = mas200[1];
		result.put(MAs, new FlexTAValue(MAs, mas, instrument.getPipScale() == 5 ? FXUtils.df5 : FXUtils.df2));
	}

}
