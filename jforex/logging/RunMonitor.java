package jforex.logging;

import java.sql.Connection;

import jforex.utils.FXUtils;

public class RunMonitor {
	private int bt_run_id, open_1st_positions, open_2nd_positions;

	private double total_bc_pips_risk, total_bc_risk, total_bc_open_amount,
			total_bc_PnL, total_bc_PnL_pips;

	private Connection logDB;

	public RunMonitor(Connection pLogDB, int bt_run_id) {
		super();
		this.bt_run_id = bt_run_id;
		logDB = pLogDB;
	}

	public void incOpen1stPositions() {
		open_1st_positions++;
	}

	public void decOpen1stPositions() {
		open_1st_positions--;
	}

	public void incOpen2ndPositions() {
		open_2nd_positions++;
	}

	public void decOpen2ndPositions() {
		open_2nd_positions--;
	}

	public void incTotalBCPipsRisk(double addRisk) {
		total_bc_pips_risk += addRisk;
	}

	public void decTotalBCPipsRisk(double addRisk) {
		total_bc_pips_risk -= addRisk;
	}

	public void incTotalBCRisk(double addRisk) {
		total_bc_risk += addRisk;
	}

	public void decTotalBCRisk(double addRisk) {
		total_bc_risk -= addRisk;
	}

	public void incTotalBCOpenAmount(double addAmount) {
		total_bc_open_amount += addAmount;
	}

	public void decTotalBCOpenAmount(double addAmount) {
		total_bc_open_amount -= addAmount;
	}

	public double getTotal_bc_PnL() {
		return total_bc_PnL;
	}

	public void setTotal_bc_PnL(double total_bc_PnL) {
		this.total_bc_PnL = total_bc_PnL;
	}

	public double getTotal_bc_PnL_pips() {
		return total_bc_PnL_pips;
	}

	public void setTotal_bc_PnL_pips(double total_bc_PnL_pips) {
		this.total_bc_PnL_pips = total_bc_PnL_pips;
	}

	public void dbLogBTEntry(long log_time) {
		FXUtils.dbUpdateInsert(
				logDB,
				"INSERT INTO "
						+ FXUtils.getDbToUse()
						+ ".`tbtrunlogentry` (`fk_btrun_id`, `log_time`, `open_1st_positions`, `open_2nd_positions`, `total_bc_pips_risk`, `total_bc_risk`, `total_bc_open_amount`, `total_bc_PnL`, `total_bc_PnL_pips`) VALUES ("
						+ bt_run_id + ", '"
						+ FXUtils.getMySQLTimeStamp(log_time) + "', "
						+ open_1st_positions + ", " + open_2nd_positions + ", "
						+ FXUtils.df1.format(total_bc_pips_risk) + ", "
						+ FXUtils.df1.format(total_bc_risk) + ", "
						+ FXUtils.df1.format(total_bc_open_amount) + ", "
						+ FXUtils.df1.format(total_bc_PnL) + ", "
						+ FXUtils.df1.format(total_bc_PnL_pips) + ")");
	}
}
