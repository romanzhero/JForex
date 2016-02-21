package jforex.logging;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import jforex.utils.FXUtils;

public class BacktestRun {
	protected int run_id, no_of_instruments_used;

	protected long period_start, period_end, exec_start, exec_end;
	protected String strategy, instruments_used;
	protected boolean isRunning = false;
	protected Connection logDB = null;

	public BacktestRun(int no_of_instruments_used, long period_start,
			long period_end, long exec_start, String strategy,
			String instruments_used, Connection pLogDB) {
		super();
		this.no_of_instruments_used = no_of_instruments_used;
		this.period_start = period_start;
		this.period_end = period_end;
		this.exec_start = exec_start;
		this.strategy = strategy;
		this.instruments_used = instruments_used;
		this.logDB = pLogDB;

		isRunning = true; // important for control of start method call
	}

	public void stop() {
		exec_end = System.currentTimeMillis();
		isRunning = false;
		FXUtils.dbUpdateInsert(logDB,
				"UPDATE " + FXUtils.getDbToUse() + ".tbtrun SET exec_end = '"
						+ FXUtils.getMySQLTimeStamp(exec_end)
						+ "' WHERE bt_run_id = " + run_id);
	}

	public void start(int userId) {
		if (!isRunning)
			return;
		FXUtils.dbUpdateInsert(
				logDB,
				"INSERT INTO "
						+ FXUtils.getDbToUse()
						+ ".`tbtrun` (user_id, `period_start`, `period_end`, `strategy`, `no_of_instruments_used`, `instruments_used`, `exec_start`) VALUES ("
						+ userId + ", '"
						+ FXUtils.getMySQLTimeStamp(period_start) + "', '"
						+ FXUtils.getMySQLTimeStamp(period_end) + "', '"
						+ strategy + "', " + no_of_instruments_used + ", '"
						+ instruments_used + "', '"
						+ FXUtils.getMySQLTimeStamp(exec_start) + "')");
		ResultSet new_rec = FXUtils.dbReadQuery(
				logDB,
				"SELECT bt_run_id FROM " + FXUtils.getDbToUse()
						+ ".tbtrun WHERE strategy = '" + strategy
						+ "' AND exec_start = '"
						+ FXUtils.getMySQLTimeStamp(exec_start)
						+ "' AND period_start = '"
						+ FXUtils.getMySQLTimeStamp(period_start) + "'");
		try {
			if (new_rec != null && new_rec.next()) {
				run_id = new_rec.getInt("bt_run_id");
			} else {
				// report problem and stop
				System.out.println("No ID found for new "
						+ FXUtils.getDbToUse() + ".tbrun record !");
				System.err.println("No ID found for new "
						+ FXUtils.getDbToUse() + ".tbrun record !");
				System.exit(1);
			}
		} catch (SQLException e) {
			System.out.println("Can't get ID for new " + FXUtils.getDbToUse()
					+ ".tbrun record ! " + e.getMessage());
			System.err.println("Can't get ID for new " + FXUtils.getDbToUse()
					+ ".tbrun record ! " + e.getMessage());
			System.exit(1);
		}
	}

	public int getRun_id() {
		return run_id;
	}
}
