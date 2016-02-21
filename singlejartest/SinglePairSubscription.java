package singlejartest;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import jforex.utils.FXUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SinglePairSubscription {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
	private static int subscription_id, instrument_id;

	public static void main(String[] args) {
		final Properties properties = new Properties();

		if (args.length < 3) {
			LOGGER.error("3 arguments needed: name of the config file, subscription ID and new instrument ID");
			System.exit(1);
		}
		try {
			properties.load(new FileInputStream(args[0]));
		} catch (IOException e) {
			LOGGER.error("Can't open or can't read properties file " + args[0]
					+ "...");
			System.exit(1);
		}

		try {
			subscription_id = Integer.parseInt(args[1]);
		} catch (NumberFormatException e) {
			LOGGER.error("Format of subscription ID wrong, must be integer: "
					+ args[1] + ", exception " + e.getMessage());
			System.exit(1);
		}
		try {
			instrument_id = Integer.parseInt(args[2]);
		} catch (NumberFormatException e) {
			LOGGER.error("Format of instument ID wrong, must be integer: "
					+ args[2] + ", exception " + e.getMessage());
			System.exit(1);
		}
		FXUtils.setDbToUse(properties.getProperty("dbToUse"));

		Connection logDB = dbLogOnStart(properties);
		FXUtils.dbExecSQL(logDB,
				"DELETE FROM " + properties.getProperty("dbToUse")
						+ ".tsubscriptionpackage WHERE fk_subscription_id = "
						+ subscription_id);
		FXUtils.dbUpdateInsert(
				logDB,
				"INSERT INTO "
						+ properties.getProperty("dbToUse")
						+ ".tsubscriptionpackage(`fk_subscription_id`, `fk_instrument_id`, `time_frame_basic`, `time_frame_higher`, `time_frame_highest`) VALUES ("
						+ subscription_id + ", " + instrument_id + ", 2, 2, 2)");
		LOGGER.info("Subscription with ID " + subscription_id
				+ " set to instrument with ID " + instrument_id);
	}

	protected static Connection dbLogOnStart(Properties prop) {
		try {
			Class.forName(prop.getProperty("sqlDriver",
					"sun.jdbc.odbc.JdbcOdbcDriver"));
		} catch (java.lang.ClassNotFoundException e1) {
			LOGGER.error("ODBC driver load failure (ClassNotFoundException: "
					+ e1.getMessage() + ")");
			System.exit(1);
		}
		String url = prop.getProperty("odbcURL");
		try {
			return DriverManager.getConnection(url,
					prop.getProperty("dbUserName", ""),
					prop.getProperty("dbPassword", ""));
		} catch (SQLException ex) {
			LOGGER.error("Log database problem: " + ex.getMessage());
			System.exit(1);
		}
		return null;
	}
}
