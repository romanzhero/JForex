package jforex.logging;

import java.sql.Connection;

import jforex.logging.LogUtils.LogEvents;
import jforex.utils.FXUtils;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IOrder;

public class AbstTradeLog {
	private int 
		order_id, position_log_id;

	private double
		maxPnL,
		maxPnl_pips,
		maxPnL_bc,
		maxPnL_pips_bc,
		min_tp_distance, // in ATRs
		min_tp_distance_pips;
	private long min_tp_distance_time;
	private IOrder order;
	Connection logDB;
	
	public AbstTradeLog(int order_id, int position_log_id, IOrder order, Connection pLogDB) {
		super();
		this.order_id = order_id;
		this.position_log_id = position_log_id;
		this.order = order;
		
		maxPnL = Double.NEGATIVE_INFINITY;
		maxPnl_pips = Double.NEGATIVE_INFINITY;
		maxPnL_bc = Double.NEGATIVE_INFINITY;
		maxPnL_pips_bc = Double.NEGATIVE_INFINITY;
		min_tp_distance = Double.MAX_VALUE;
		min_tp_distance_pips = Math.abs(order.getTakeProfitPrice() - order.getOpenPrice());
		
		logDB = pLogDB;
	}
	
	public int getOrder_id() {
		return order_id;
	}

	public double getMin_tp_distance() {
		return min_tp_distance;
	}

	public double getMin_tp_distance_pips() {
		return min_tp_distance_pips;
	}

	public long getMin_tp_distance_time() {
		return min_tp_distance_time;
	}

	public IOrder getOrder() {
		return order;
	}

	public void dbRecordFill(long logTime) {
		FXUtils.dbUpdateInsert(logDB, "INSERT INTO " + FXUtils.getDbToUse() + ".ttradelog(order_id, positionlog_id, log_time, event) VALUES ("
				+ order_id + ", " + position_log_id 
				+ ", '" + FXUtils.getMySQLTimeStamp(logTime) 
				+ "', '" + LogEvents.OPEN_1ST_POSITION.toString() + "')");
		
	}

	public void dbRecord2ndFill(long logTime) {
		FXUtils.dbUpdateInsert(logDB, "INSERT INTO " + FXUtils.getDbToUse() + ".ttradelog(order_id, positionlog_id, log_time, event) VALUES ("
				+ order_id + ", " + position_log_id 
				+ ", '" + FXUtils.getMySQLTimeStamp(logTime) 
				+ "', '" + LogEvents.OPEN_2ND_POSITION.toString() + "')");
	}	
	
	public int getPosition_log_id() {
		return position_log_id;
	}

	public void update(IBar bidBar, IBar askBar, long logTime) {
		if (order.isLong()) {
			double tpDistancePips = order.getTakeProfitPrice() - bidBar.getHigh();
			if (tpDistancePips < min_tp_distance_pips) {
				min_tp_distance_pips = tpDistancePips;
				min_tp_distance_time = logTime;
			}
		} else {
			double tpDistancePips = askBar.getLow() - order.getTakeProfitPrice();
			if (tpDistancePips < min_tp_distance_pips) {
				min_tp_distance_pips = tpDistancePips;
				min_tp_distance_time = logTime;
			}			
		}
	}
}
