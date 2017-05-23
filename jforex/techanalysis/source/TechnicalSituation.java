/**
 * 
 */
package jforex.techanalysis.source;

import jforex.techanalysis.Momentum;

/**
 * A central class to describe complete technical situation
 * Most importantly it has an explicit bullish/bearish/neutral (unclear) flag
 * All setups should use this flag to filter out entries and to avoid unnecessary exits
 * The calculation of the fields i.e. assessing of the whole technical situation
 * is done in another class - this one is just a "mesagener"
 *
 */
public class TechnicalSituation {
	public enum OverallTASituation {BULLISH, BEARISH, NEUTRAL, UNCLEAR, LOW_VOLA};
	public enum TASituationReason {TREND, MOMENTUM, LOW_VOLA, OVERSOLD, OVERBOUGHT, FLAT, NONE};
	
	public OverallTASituation taSituation = OverallTASituation.UNCLEAR;
	public TASituationReason taReason;
	public String txtSummary = null;
	
	public Momentum.SMI_STATE smiState;
	public Momentum.SINGLE_LINE_STATE
		slowSMIState, fastSMIState;
	public Momentum.STOCH_STATE stochState;

}
