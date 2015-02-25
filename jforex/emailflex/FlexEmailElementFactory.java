package jforex.emailflex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import jforex.emailflex.elements.*;
import jforex.emailflex.elements.candles.*;
import jforex.emailflex.elements.channel.*;
import jforex.emailflex.elements.extremes.*;
import jforex.emailflex.elements.momentum.*;
import jforex.emailflex.elements.osob.*;
import jforex.emailflex.elements.overview.*;
import jforex.emailflex.elements.trend.*;
import jforex.emailflex.elements.vola.*;

public class FlexEmailElementFactory {
    protected static Map<String, IFlexEmailElement> elementMap = new HashMap<String, IFlexEmailElement>();
    static {
        elementMap.put("reportTitle", new ReportTitleElement());
        elementMap.put("barOHLC", new BarOHLCElement());
        elementMap.put("barStat", new BarStatElement());
        elementMap.put("BodyDirection", new BodyDirectionElement());
        elementMap.put("CandleTrigger", new CandleTriggerElement());
        elementMap.put("CandleTriggerChannelPos", new CandleTriggerChannelPosElement());
        elementMap.put("CandleTriggerKChannelPos", new CandleTriggerKChannelPosElement());
        elementMap.put("PivotLevel", new PivotLevelElement());
        elementMap.put("candleTriggerCombinedBodyDirection", new CandleTriggerCombinedBodyDirectionElement());
        elementMap.put("relatedCandlesSameTF", new RelatedCandlesSameTFElement());
        elementMap.put("relatedCandlesTwoTF", new RelatedCandlesTwoTFElement());
        elementMap.put("MAsProximity", new MAsProximityElement());
        elementMap.put("barHighChannelPos", new BarHighChannelPosElement());
        elementMap.put("barHighChannelPosOverlap", new BarHighChannelPosOverlapElement());
        elementMap.put("barLowChannelPos", new BarLowChannelPosElement());
        elementMap.put("barLowChannelPosOverlap", new BarLowChannelPosOverlapElement());
        elementMap.put("barsAboveChannel", new BarsAboveChannelElement());
        elementMap.put("barsBelowChannel", new BarsBelowChannelElement());
        elementMap.put("bBandsBottom", new BBandsBottomElement());
        elementMap.put("bBandsTop", new BBandsTopElement());
        elementMap.put("bBandsWidth", new BBandsWidthElement());
        elementMap.put("MAsDistanceExtreme", new MAsDistanceExtremeElement());
        elementMap.put("CCIExtreme", new CCIExtremeElement());
        elementMap.put("RSIExtreme", new RSIExtremeElement());
        elementMap.put("StochsExtreme", new StochsExtremeElement());
        elementMap.put("ChannelPosExtreme", new ChannelPosExtremeElement());
        elementMap.put("barsAboveChannelExtreme", new BarsAboveChannelExtremeElement());
        elementMap.put("barsBelowChannelExtreme", new BarsBelowChannelExtremeElement());
        elementMap.put("barStatExtreme", new BarStatExtremeElement());
        elementMap.put("volatilityExtreme", new VolatilityExtremeElement());
        elementMap.put("MACDCrossExtreme", new MACDCrossExtremeElement());
        elementMap.put("MACD", new MACDElement());
        elementMap.put("MACDHistogram", new MACDHistogramElement());
        elementMap.put("Stoch", new StochElement());
        elementMap.put("CCIState", new CCIStateElement());
        elementMap.put("MACDCross", new MACDCrossElement());
        elementMap.put("MACDHState", new MACDHStateElement());
        elementMap.put("MACD_HStDevPos", new MACD_HStDevPosElement());
        elementMap.put("MACDHStateOverlap", new MACDHStateOverlapElement());
        elementMap.put("MACDState", new MACDStateElement());
        elementMap.put("MACDStDevPos", new MACDStDevPosElement());
        elementMap.put("MACDStateOverlap", new MACDStateOverlapElement());
        elementMap.put("RSIState", new RSIStateElement());
        elementMap.put("StochState", new StochStateElement());
        elementMap.put("StochsDiff", new StochsDiffElement());
        elementMap.put("StochCross", new StochCrossElement());
        elementMap.put("CCI", new CCIElement());
        elementMap.put("RSI", new RSIElement());
        elementMap.put("RSIOverlap", new RSIOverlapElement());
        elementMap.put("StochStateOverlap", new StochStateOverlapElement());
        elementMap.put("TrendOverview", new TrendOverviewElement());
        elementMap.put("MomentumOverview", new MomentumOverviewElement());
        elementMap.put("ChannelPosOverview", new ChannelPosOverviewElement());
        elementMap.put("OSOBOverview", new OSOBOverviewElement());
        elementMap.put("CandlesOverview", new CandlesOverview());
        elementMap.put("VolatilityOverview", new VolatilityOverviewElement());
        elementMap.put("MAsPosition", new MAsPositionElement());
        elementMap.put("MAsDistance", new MAsDistanceElement());
        elementMap.put("IsMA200Highest", new IsMA200HighestElement());
        elementMap.put("IsMA200Lowest", new IsMA200LowestElement());
        elementMap.put("MAsDistanceOverlap", new MAsDistanceOverlapElement());
        elementMap.put("TrendId", new TrendIdElement());
        elementMap.put("TrendIdOverlap", new TrendIdOverlapElement());
        elementMap.put("ATR", new ATRElement());
        elementMap.put("volatility", new VolatilityElement());        
        elementMap.put("IchiCloudCross", new IchiCloudCrossElement());        
        elementMap.put("IchiCloudDist", new IchiCloudDistElement());        
        elementMap.put("IchiCloudBreakout", new IchiCloudBreakoutElement());
        elementMap.put("BuyDipsSellRallies", new BuyDipsSellRalliesElement());              
        elementMap.put("SMACrossElementFast", new SMACrossElementFast());
        elementMap.put("SMACrossElementSlow", new SMACrossElementSlow());
        elementMap.put("EMACrossElementFast", new EMACrossElementFast());
        elementMap.put("EMACrossElementSlow", new EMACrossElementSlow());
        elementMap.put("SqueezeBreakout", new SqueezeBreakout());
        elementMap.put("DonchianBreakoutFast", new DonchianBreakoutFast());
        elementMap.put("DonchianBreakoutSlow", new DonchianBreakoutSlow());
        elementMap.put("RangeExtremes", new RangeExtremesStrategy());
    }
    
	static public IFlexEmailElement create(String description, Properties conf) {
		String elementID = parseID(description);
		if (elementID == null)
			return null;
		List<String> parameters = parseParameters(description);
		if (parameters == null)
			return null;
		
		if (!elementMap.containsKey(elementID))
			return null;
		
		IFlexEmailElement e = elementMap.get(elementID).cloneIt(conf);
		e.setParameters(parseParameters(description));
		return e;
	}
	
	static public IFlexEmailElement create(String description) {	
		if (!elementMap.containsKey(description))
			return null;
		
		IFlexEmailElement e = elementMap.get(description).cloneIt();
		return e;
	}	

	private static List<String> parseParameters(String description) {
		int firstDelimiter = description.indexOf(";");
		if (firstDelimiter == -1)
			return null;
		
		String parameterValues = description.substring(firstDelimiter + 1);
		StringTokenizer t = new StringTokenizer(parameterValues, ";");
		if (!t.hasMoreTokens())
			return null;
		
		String noOfParamsStr = t.nextToken();
		try {
			int noOfParams = Integer.parseInt(noOfParamsStr);
			List<String> parameters = new ArrayList<String>();
			while (t.hasMoreTokens()) {
				parameters.add(t.nextToken());
			}
			if (noOfParams != parameters.size())
				return null;
			return parameters;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String parseID(String description) {
		int firstDelimiter = description.indexOf(";");
		if (firstDelimiter == -1)
			return null;
		return new String(description.substring(0, firstDelimiter));
	}
}
