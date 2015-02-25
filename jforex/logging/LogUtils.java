package jforex.logging;

public class LogUtils {
	public enum LogEvents {BT_START, BT_END, 
		OPEN_1ST_POSITION, OPEN_2ND_POSITION, 
		SL_1ST_POSITION, SL_2ND_POSITION, 
		TP_1ST_POSITION, TP_2ND_POSITION,
		// force close
		FC_1ST_POSITION, FC_2ND_POSITION,
		TIME_LOG
	}

}
