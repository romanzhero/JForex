package jforex;

import jforex.utils.FXUtils;
import jforex.utils.Logger;

public class TradeStateController {
	/**
	 * 
	 */
	private Logger logger;

	public enum TradeState {
		SCANNING_TA_SIGNALS, ENTRY_ORDER_WAITING, POSITION_OPENED, POSITION_OPENED_AND_ENTRY_ORDER_WAITING, POSITION_OPENED_MAX_REACHED, EXIT_ORDER_TRAILING, POSITION_CLOSED, ENTRY_ORDER_CANCELLED, FIRST_EXIT_SIGNAL_FOUND, FIRST_EXIT_SIGNAL_EXECUTED, // after
																																																															// order
																																																															// #1
																																																															// is
																																																															// closed
																																																															// but
																																																															// order(s)
																																																															// #n
																																																															// still
																																																															// active
		SECOND_EXIT_SIGNAL_FOUND,
		// new states for 1d / 4h adaptive trend following / mean-reversal
		// strategy
		POSITION_N_OPENED, PHASE_1_EXIT_SIGNAL_FOUND, // 1st exit signal found
														// while having only 1
														// position
		POSITION_N_AND_ENTRY_ORDER_WAITING, PHASE_2_1ST_EXIT_SIGNAL_FOUND, // multiple
																			// positions
																			// and
																			// 1st
																			// exit
																			// signal
																			// found
		PHASE_2_POSITION_N, // no more new position after 1st exit signal
	}

	private TradeState state = TradeState.SCANNING_TA_SIGNALS; // Always the
																// start state
	private boolean log = false;

	public TradeStateController(Logger pLog, boolean shouldLog) {
		this.logger = pLog;
		log = shouldLog;
	}

	public TradeState getState() {
		return state;
	}

	public void stateTransition(TradeState to, long time) {
		// ToDo: state transition logic
		if (log) {
			logger.printAction(Logger.logTags.STRATEGY.toString(),
					"STATE TRANSITION", FXUtils.getFormatedTimeGMT(time),
					"From: " + state.toString() + " to: " + to.toString(), 0d,
					0d, 0d, 0d);

		}
		state = to;
	}
}
