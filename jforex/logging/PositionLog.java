package jforex.logging;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import jforex.utils.FXUtils;

import com.dukascopy.api.IOrder;

public class PositionLog {
	private IOrder firstOrder;
	private int bt_run_id;
	private double maxPnL, total_closed_2nd_pos_PnL;
	long maxPnLTime;
	private Connection logDB;

	public PositionLog(IOrder firstOrder, int bt_run_id, Connection logDB) {
		super();
		this.firstOrder = firstOrder;
		this.bt_run_id = bt_run_id;
		this.logDB = logDB;

		maxPnL = Double.NEGATIVE_INFINITY;
		total_closed_2nd_pos_PnL = 0.0;
	}

	public int dbRecordFill(long logTime) {
		maxPnL = 0.01; // just dummy value to avoid usage of
						// Double.NEGATIVE_INFINITY for DB in case report call
						// never made
		maxPnLTime = logTime;
		FXUtils.dbUpdateInsert(
				logDB,
				"INSERT INTO "
						+ FXUtils.getDbToUse()
						+ ".tpositionlog(fk_btrun_id, order_label, start_time) VALUES ("
						+ bt_run_id + ", '" + firstOrder.getLabel() + "', '"
						+ FXUtils.getMySQLTimeStamp(logTime) + "')");
		ResultSet rs = FXUtils.dbReadQuery(logDB, "SELECT positionlog_id FROM "
				+ FXUtils.getDbToUse() + ".tpositionlog WHERE order_label = '"
				+ firstOrder.getLabel() + "' AND fk_btrun_id = " + bt_run_id);
		try {
			if (rs != null && rs.next()) {
				return rs.getInt("positionlog_id");
			} else {
				System.out.println("No new positionlogid found ! Order "
						+ firstOrder.getLabel());
				System.err.println("No new positionlogid found ! Order "
						+ firstOrder.getLabel());
				System.exit(1);
			}
		} catch (SQLException e) {
			System.out.println("Can't get positionlogid ! Exception: "
					+ e.getMessage());
			System.err.println("Can't get positionlogid ! Exception: "
					+ e.getMessage());
			System.exit(1);
		}
		return -1;
	}

	public void updateTotalClosed2ndPosPnL(double latest_closed_2nd_pos_PnL) {
		total_closed_2nd_pos_PnL += latest_closed_2nd_pos_PnL;
	}

	public void updateTotalPnL(double openPositionsTotalPnL, long logTime) {
		if (openPositionsTotalPnL + total_closed_2nd_pos_PnL > maxPnL) {
			maxPnL = openPositionsTotalPnL;
			maxPnLTime = logTime;
		}
	}

	public double getMaxPnL() {
		return maxPnL;
	}

	public long getMaxPnLTime() {
		return maxPnLTime;
	}

}
