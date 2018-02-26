/**
 * 
 */
package jforex.techanalysis.source;

import jforex.techanalysis.Momentum;
import jforex.utils.FXUtils;

/**
 * A central class to describe complete technical situation
 * Most importantly it has an explicit bullish/bearish/neutral (unclear) flag
 * All setups should use this flag to filter out entries and to avoid unnecessary exits
 * The calculation of the fields i.e. assessing of the whole technical situation
 * is done in another class - this one is just a "messanger"
 *
 */
public class TechnicalSituation {
	public enum OverallTASituation {BULLISH, BEARISH, NEUTRAL, UNCLEAR, LOW_VOLA, DUMMY};
	public enum TASituationReason {TREND, MOMENTUM, LOW_VOLA, OVERSOLD, OVERBOUGHT, FLAT, NONE};
	
	public OverallTASituation taSituation = OverallTASituation.UNCLEAR;
	public TASituationReason taReason;
	public String txtSummary = null;
	
	public Momentum.SMI_STATE smiState;
	public Momentum.SINGLE_LINE_STATE
		slowSMIState, fastSMIState;
	public Momentum.STOCH_STATE stochState;
	public Momentum.MACD_H_STATE macdHistoState;
	public double
		fastSMI = 0.0,
		slowSMI = 0.0,
		fastStoch = 0.0,
		slowStoch = 0.0;
	
	@Override
	public String toString() {
		String res = new String();
		res += taSituation.toString() 
			+ ";" + taReason.toString()
			+ ";" + txtSummary
			+ "| SMI state: " + smiState.toString()
			+ " (slow: " + slowSMIState.toString() + ", " + FXUtils.df1.format(slowSMI)
			+ ", fast: " + fastSMIState.toString()  + ", " + FXUtils.df1.format(fastSMI)
			+ ") Stoch state: " + stochState.toString()  
			+ " (" + FXUtils.df1.format(fastStoch)  + "/" + FXUtils.df1.format(slowStoch) + ")"
			+ " MACD-H state: " + macdHistoState.toString();		
		return res;
	}

}
