package jforex.filters;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.Period;

public class FilterFactory {
	public static final String FILTER_PREFIX = "FILTER_";
	public static final String FILTER_CONDITIONAL_PREFIX = "FILTER_CONDITIONAL_";
	private static final String FILTER_DELIMITER = "_";
	private static final String THIRTY_MINS = "30min";
	private static final String FOUR_HOURS = "4h";
	private static final String VALUE_DELIMITER = ";";

	protected static Map<String, IFilter> simpleFiltersMap = new HashMap<String, IFilter>();
	protected static Map<String, IConditionalFilter> conditionalFiltersMap = new HashMap<String, IConditionalFilter>();

	static {
		simpleFiltersMap.put("MACDSignal", new MACDSignalFilter());
		simpleFiltersMap.put("MACDHistogram", new MACDHistogramFilter());
		simpleFiltersMap.put("StochsDiff", new StochsDiffFilter());
		simpleFiltersMap.put("StochSlow", new StochSlowFilter());
		simpleFiltersMap.put("ADX", new ADXFilter());
		simpleFiltersMap.put("UptrendMAsDistance",
				new UptrendMAsDistanceFilter());
		simpleFiltersMap.put("EntryChannelPos", new ChannelPosFilter());
		simpleFiltersMap.put("StopChannelPos", new ChannelPosFilter());
		simpleFiltersMap.put("DowntrendMAsDistance",
				new DowntrendMAsDistanceFilter());
		simpleFiltersMap.put("StochFast", new StochFastFilter());
		simpleFiltersMap.put("ADXDiPlus", new ADXDiPlusFilter());
	}

	static {
		conditionalFiltersMap.put("MACDSignal_MACDsPosition",
				new ConditionalFilterMACDSignalMACDsPosition());
		conditionalFiltersMap.put("MACDHistogram_MACDsPosition",
				new ConditionalFilterMACD_HMACDsPosition());
		conditionalFiltersMap.put("StochsDiff_MACDsPosition",
				new ConditionalFilterStochsDiffMACDsPosition());
		conditionalFiltersMap.put("StochSlow_MACDsPosition",
				new ConditionalFilterStochSlowMACDsPosition());
		conditionalFiltersMap.put("ADX_MAsPosition",
				new ConditionalFilterADXMAsPosition());
		conditionalFiltersMap.put("UptrendMAsDistance_MAsPosition",
				new ConditionalFilterUptrendMAsDistanceMAsPosition());
		conditionalFiltersMap.put("ChannelPos_MAsPosition",
				new ConditionalFilterChannelPosMAsPosition());
		conditionalFiltersMap.put("StochsDiff_MAsPosition",
				new ConditionalFilterStochsDiffMAsPosition());
		conditionalFiltersMap.put("MACDSignal_MAsPosition",
				new ConditionalFilterMACDSignalMAsPosition());
		conditionalFiltersMap.put("MACDHistogram_MAsPosition",
				new ConditionalFilterMACD_HMAsPosition());
		conditionalFiltersMap.put("DowntrendMAsDistance_MAsPosition",
				new ConditionalFilterDowntrendMAsDistanceMAsPosition());
		conditionalFiltersMap.put("StochSlow_MAsPosition",
				new ConditionalFilterStochSlowMAsPosition());
		conditionalFiltersMap.put("StochFast_MAsPosition",
				new ConditionalFilterStochFastMAsPosition());
		conditionalFiltersMap.put("ADXDiPlus_MAsPosition",
				new ConditionalFilterADXDiPlusMAsPosition());
		conditionalFiltersMap.put("ADXDiPlus_ADXsPosition",
				new ConditionalFilterADXDiPlusADXsPosition());
		conditionalFiltersMap.put("ChannelPos_ADX",
				new ConditionalFilterChannelPosADX());
	}

	static public IFilter createFilter(String filterName, String filterValue,
			IIndicators pIndicators, IHistory pHistory) {
		if (!filterName.startsWith(FILTER_PREFIX)
				&& !filterName.startsWith(FILTER_CONDITIONAL_PREFIX))
			return null;
		if (filterName.startsWith(FILTER_CONDITIONAL_PREFIX)) {
			String mainIndicator = parseMainIndicator(filterName);
			String condIndicator = parseCondIndicator(filterName);
			String indicatorCombination = mainIndicator + "_" + condIndicator;
			if (conditionalFiltersMap.containsKey(indicatorCombination)) {
				Period mainPeriod = parseMainPeriod(filterName);
				Period condPeriod = parseCondPeriod(filterName);
				double[] mainMin = parseMainMin(filterValue);
				double[] mainMax = parseMainMax(filterValue);
				double[] condMin = parseCondMin(filterValue);
				double[] condMax = parseCondMax(filterValue);
				IConditionalFilter f = conditionalFiltersMap.get(
						indicatorCombination).cloneConditionalFilter();
				f.set(filterName, pIndicators, mainPeriod,
						Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
				f.setConditionalParams(pHistory, condPeriod, mainMin, mainMax,
						condMin, condMax);
				return f;
			} else
				return null;
		} else {
			String indicator = parseIndicator(filterName);
			if (simpleFiltersMap.containsKey(indicator)) {
				Period period = parsePeriod(filterName);
				double min = parseMin(filterValue);
				double max = parseMax(filterValue);
				IFilter f = simpleFiltersMap.get(indicator).cloneFilter();
				f.set(filterName, pIndicators, period, min, max);
				return f;
			} else
				return null;
		}
	}

	// conditional filters values' format is
	// <cond min 1>;<cond max 1>;<min 1>;<max 1>;<cond min 2>;<cond max 2>;<min
	// 2>;<max 2>;...;<cond min n>;<cond max n>;<min n>;<max n>
	// missing values must be replaced with keyword NONE
	private static double[] parseCondMax(String filterValue) {
		StringTokenizer st = new StringTokenizer(filterValue, VALUE_DELIMITER);
		int totalTokens = st.countTokens(); // mod 4 must be 0 if all syntax
											// rules were kept
		if (totalTokens % 4 != 0)
			return null;
		int totalFours = totalTokens / 4;
		double[] result = new double[totalFours];
		for (int i = 0; i < totalFours && st.hasMoreTokens(); i++) {
			st.nextToken();

			String token = st.nextToken();
			if (token.equals("NONE"))
				result[i] = Double.POSITIVE_INFINITY;
			else
				result[i] = Double.parseDouble(token);

			st.nextToken();
			st.nextToken();
		}
		return result;
	}

	// conditional filters values' format is
	// <cond min 1>;<cond max 1>;<min 1>;<max 1>;<cond min 2>;<cond max 2>;<min
	// 2>;<max 2>;...;<cond min n>;<cond max n>;<min n>;<max n>
	private static double[] parseCondMin(String filterValue) {
		StringTokenizer st = new StringTokenizer(filterValue, VALUE_DELIMITER);
		int totalTokens = st.countTokens(); // mod 4 must be either 0 (complete
											// conditions) or 1 (last max empty)
		if (totalTokens % 4 != 0)
			return null;
		int totalFours = totalTokens / 4;
		double[] result = new double[totalFours];
		for (int i = 0; i < totalFours && st.hasMoreTokens(); i++) {
			String token = st.nextToken();
			if (token.equals("NONE"))
				result[i] = Double.NEGATIVE_INFINITY;
			else
				result[i] = Double.parseDouble(token);

			st.nextToken();
			st.nextToken();
			st.nextToken();
		}
		return result;
	}

	// conditional filters values' format is
	// <cond min 1>;<cond max 1>;<min 1>;<max 1>;<cond min 2>;<cond max 2>;<min
	// 2>;<max 2>;...;<cond min n>;<cond max n>;<min n>;<max n>
	private static double[] parseMainMax(String filterValue) {
		StringTokenizer st = new StringTokenizer(filterValue, VALUE_DELIMITER);
		int totalTokens = st.countTokens(); // mod 4 must be either 0 (complete
											// conditions) or 1 (last max empty)
		if (totalTokens % 4 != 0)
			return null;
		int totalFours = totalTokens / 4;
		double[] result = new double[totalFours];
		for (int i = 0; i < totalFours && st.hasMoreTokens(); i++) {
			st.nextToken();
			st.nextToken();
			st.nextToken();
			String token = st.nextToken();
			if (token.equals("NONE"))
				result[i] = Double.POSITIVE_INFINITY;
			else
				result[i] = Double.parseDouble(token);
		}
		return result;
	}

	// conditional filters values' format is
	// <cond min 1>;<cond max 1>;<min 1>;<max 1>;<cond min 2>;<cond max 2>;<min
	// 2>;<max 2>;...;<cond min n>;<cond max n>;<min n>;<max n>
	private static double[] parseMainMin(String filterValue) {
		StringTokenizer st = new StringTokenizer(filterValue, VALUE_DELIMITER);
		int totalTokens = st.countTokens(); // mod 4 must be either 0 (complete
											// conditions) or 1 (last max empty)
		if (totalTokens % 4 != 0)
			return null;
		int totalFours = totalTokens / 4;
		double[] result = new double[totalFours];
		for (int i = 0; i < totalFours && st.hasMoreTokens(); i++) {
			st.nextToken();
			st.nextToken();
			String token = st.nextToken();
			if (token.equals("NONE"))
				result[i] = Double.NEGATIVE_INFINITY;
			else {
				result[i] = Double.parseDouble(token);
			}
			st.nextToken();
		}
		return result;
	}

	// conditional filters have format FILTER_CONDITIONAL_<time frame>_<main
	// indicator>_<conditional indicator>_<conditional time frame>
	// = <cond min 1>;<cond max 1>;<min 1>;<max 1>;<cond min 2>;<cond max
	// 2>;<min 2>;<max 2>;...;<cond min n>;<cond max n>;<min n>;<max n>

	private static Period parseCondPeriod(String filterName) {
		StringTokenizer st = new StringTokenizer(filterName, FILTER_DELIMITER);
		st.nextToken();
		st.nextToken();
		st.nextToken();
		st.nextToken();
		st.nextToken();
		String token = st.nextToken();
		if (token.equals(THIRTY_MINS))
			return Period.THIRTY_MINS;
		else if (token.equals(FOUR_HOURS))
			return Period.FOUR_HOURS;
		else
			return null;
	}

	private static Period parseMainPeriod(String filterName) {
		StringTokenizer st = new StringTokenizer(filterName, FILTER_DELIMITER);
		st.nextToken();
		st.nextToken();
		String token = st.nextToken();
		if (token.equals(THIRTY_MINS))
			return Period.THIRTY_MINS;
		else if (token.equals(FOUR_HOURS))
			return Period.FOUR_HOURS;
		else
			return null;
	}

	private static String parseCondIndicator(String filterName) {
		StringTokenizer st = new StringTokenizer(filterName, FILTER_DELIMITER);
		st.nextToken();
		st.nextToken();
		st.nextToken();
		st.nextToken();
		return st.nextToken();
	}

	private static String parseMainIndicator(String filterName) {
		StringTokenizer st = new StringTokenizer(filterName, FILTER_DELIMITER);
		st.nextToken();
		st.nextToken();
		st.nextToken();
		return st.nextToken();
	}

	private static double parseMax(String filterValue) {
		// Format of simple filters' values is
		// <NEGATIVE_INFINITY;<POSITIVE_INFINITY>
		StringTokenizer st = new StringTokenizer(filterValue, VALUE_DELIMITER);
		st.nextToken();
		String token = st.nextToken();
		if (token.equals("NONE"))
			return Double.POSITIVE_INFINITY;
		else {
			return Double.parseDouble(token);
		}
	}

	private static double parseMin(String filterValue) {
		// Format of simple filters' values is
		// <NEGATIVE_INFINITY;<POSITIVE_INFINITY>
		StringTokenizer st = new StringTokenizer(filterValue, VALUE_DELIMITER);
		String token = st.nextToken();
		if (token.equals("NONE"))
			return Double.NEGATIVE_INFINITY;
		else {
			return Double.parseDouble(token);
		}
	}

	private static String parseIndicator(String filterName) {
		// Format of simple filters' names is FILTER_<time frame>_<indicator>
		StringTokenizer st = new StringTokenizer(filterName, FILTER_DELIMITER);
		st.nextToken();
		st.nextToken();
		return st.nextToken();
	}

	private static Period parsePeriod(String filterName) {
		// Format of simple filters' names is FILTER_<time frame>_<indicator>
		StringTokenizer st = new StringTokenizer(filterName, FILTER_DELIMITER);
		String token = st.nextToken();
		token = st.nextToken();
		if (token.equals(THIRTY_MINS))
			return Period.THIRTY_MINS;
		else if (token.equals(FOUR_HOURS))
			return Period.FOUR_HOURS;
		else
			return null;
	}
}
