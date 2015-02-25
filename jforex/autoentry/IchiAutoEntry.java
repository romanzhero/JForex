package jforex.autoentry;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import jforex.BasicStrategy;
import jforex.logging.BacktestRun;
import jforex.logging.LogUtils.LogEvents;
import jforex.logging.PositionLog;
import jforex.logging.RunMonitor;
import jforex.logging.TradeLog;
import jforex.utils.FXUtils;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IMessage.Reason;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class IchiAutoEntry extends BasicStrategy implements IStrategy {
	
	private Period 
		selectedPeriod = Period.TEN_MINS,
		reportingPeriod = Period.THIRTY_MINS;
	
	private long
		period_start, period_end,
		exec_start;
	private BacktestRun btRun;
	private RunMonitor rm;
	private Map<String, PositionLog> positionLogs = new HashMap<String, PositionLog>();
	private Map<String, TradeLog> tradeLogs = new HashMap<String, TradeLog>();
	
	public IchiAutoEntry(Properties props, long period_start, long period_end, long exec_start) { 
		super(props); 
		this.period_start = period_start;
		this.period_end = period_end;
		this.exec_start = exec_start;
	}

	@Override
	public void onStart(IContext context) throws JFException {
		super.onStartExec(context);
		dbLogOnStart();
		Set<Instrument> insts = context.getSubscribedInstruments();
		String instsStr = new String();
		for (Instrument i : insts) {
			instsStr += i.toString() + ", ";
		}
		cleanUpOrderStatuses(insts);
		closeHanging2ndOrders();
		
		FXUtils.setProfitLossHelper(Instrument.EURUSD.getSecondaryCurrency(), history);
		if (conf.getProperty("tradeLogs", "no").equals("yes")) {
			btRun = new BacktestRun(context.getSubscribedInstruments().size(), period_start, period_end, exec_start, "Ichimoku", instsStr.substring(0, instsStr.length() - 2), logDB);
			btRun.start(FXUtils.dbGetUserID(logDB, conf.getProperty("user_email")));
			
			rm = new RunMonitor(logDB, btRun.getRun_id());
		}
		
	}

	/**
	 * If first order was closed due to SL while robot was disconnected,
	 * the second order wasn't closed and is "hanging". It should be closed now
	 * @throws JFException 
	 */
	private void closeHanging2ndOrders() throws JFException {
		List<IOrder> 
			brokerOrders = engine.getOrders(),
			secondOrders = new ArrayList<IOrder>();	
		Set<String> firstOrdersLabels = new HashSet<String>();
 		// separate them in two groups, 1st and 2nd orders
		for (IOrder currOrder : brokerOrders) {
			if (currOrder.getLabel().endsWith("_2nd"))
				secondOrders.add(currOrder);
			else
				firstOrdersLabels.add(currOrder.getLabel());
		}
		// 2nd order must have a corresponding 1st order, strictly speaking in FILLED state
		for (IOrder curr2ndOrder : secondOrders) {
			// Careful ! 2nd orders have format label_nn_2nd !! Find the right one !
			String firstOrderLabel = curr2ndOrder.getLabel().substring(0, curr2ndOrder.getLabel().length() - 7);
			if (!firstOrdersLabels.contains(firstOrderLabel)) {
				log.print("2nd order found with no corresponding 1st order,  closing (ID = " + curr2ndOrder.getLabel() + ")", true);
				curr2ndOrder.close();
				// onMessage should deal with DB update if necessary
			}
		}
	}

	/**
	 * @param insts
	 * If client disconnected and there were some changes to orders,
	 * statuses in torder table become inconsistent. These need to be cleaned up now,
	 * otherwise new signals can not be taken correctly
	 * @throws JFException 
	 */
	private void cleanUpOrderStatuses(Set<Instrument> insts) throws JFException {		
		ResultSet ordersToCheck = FXUtils.dbReadQuery(logDB, "SELECT order_id, label FROM " 
				+ FXUtils.getDbToUse() + ".`torder` WHERE user_id = " + FXUtils.dbGetUserID(logDB, conf.getProperty("user_email")) 
				+ " AND status < 3");
		if (ordersToCheck == null) {
			log.print("Check for open orders in database could not be done, ResultSet null...", true);
			return;
		}
		try {
			Map<String, Integer> dbLabelsToCheck = new HashMap<String, Integer>();
			while (ordersToCheck.next()) {
				dbLabelsToCheck.put(ordersToCheck.getString("label"), new Integer(ordersToCheck.getInt("order_id")));
			}
			if (dbLabelsToCheck.isEmpty()) {
				log.print("No open orders in database...", true);
				return;
			}
			log.print("Found " + dbLabelsToCheck.size() + " open orders in database, cross-checking broker orders...", true);
			List<IOrder> openBrokerOrders = engine.getOrders();
			for (IOrder brokerOrder : openBrokerOrders) {
				// if this order can be found in DB order list, that's fine. Just make sure its status
				// is correct in database
				int orderStatus = 0;
				if (brokerOrder.getState().equals(IOrder.State.OPENED))
					orderStatus = 1;
				else if (brokerOrder.getState().equals(IOrder.State.FILLED))
					orderStatus = 2;
				Integer order_id_Int = dbLabelsToCheck.get(brokerOrder.getLabel());
				if (order_id_Int != null) {
					log.print("Broker order with label = " + brokerOrder.getLabel() + " found in database, updating status...", true);
					int order_id = order_id_Int.intValue();
					FXUtils.dbUpdateInsert(logDB, "UPDATE " + FXUtils.getDbToUse() + 
							".torder SET status = " + orderStatus + " WHERE order_id = " 
							+ order_id);
					// now remove this order from DB order list - what is left should be cleaned up
					// since now corresponding open orders were found
					dbLabelsToCheck.remove(brokerOrder.getLabel());
				}
			}
			log.print(dbLabelsToCheck.size() + " database orders with inconsistent status, cleaning them up..", true);
			Collection<Integer> orderIDsToCleanUp = dbLabelsToCheck.values();
			for (Integer currOrderID : orderIDsToCleanUp) {
				log.print("Clean up order with ID = " + currOrderID.intValue(), true);
				FXUtils.dbUpdateInsert(logDB, "UPDATE " + FXUtils.getDbToUse() 
						+ ".torder SET status = status + 100 WHERE order_id = " 
						+ currOrderID.intValue());				
			}
		} catch (SQLException e) {
			System.out.println("Problem reading orders while checking consistency: " +  e.getMessage());
			System.exit(1);
		}
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (!period.equals(selectedPeriod) && !period.equals(reportingPeriod))
			return;
		
		if (!tradingAllowed(bidBar.getTime()))
			return;
		
		//checkTakeProfitDistance(instrument, period, askBar, bidBar);
				
		if (period.equals(reportingPeriod) && conf.getProperty("tradeLogs", "no").equals("yes")) {
			// record current PnL and other data for open position of this pair
			reportAllOpenPositions(instrument, bidBar, askBar, bidBar.getTime() + 30 * 60 * 1000);
		}
		
		//TODO: here's the right spot to check whether trading is allowed or not due to pending news
		// design: ideally record start - end time in database and on pair level
		// plus special code for all pairs ("ALL" instead of ticker)
		// if bidBar.getTime() falls within start - end time of no trading period
		// 1. don't take any waiting signals. Once no trading period elapses, next onBar call will simply pick them up
		// 2. _temporarily_ close all waiting 2nd orders. Put them in sort of queue (in memory or DB ?)
		// and wait for no trade period to pass. After that check the queue and place orders back again
		// however first analyse the market - if price went to far away from entry STP cancel the order forever
		
		ResultSet signals = getSignals(conf.getProperty("user_email"), instrument, FXUtils.getMySQLTimeStamp(bidBar.getTime()));
		if (signals == null)
			return;
		
		try {
			int count = 1;
			while (signals.next()) {
				if (count > 1) {
					log.print("More then one signal found ! (" + count + ") for SQL " + getSignalsSQL(conf.getProperty("user_email"), instrument, FXUtils.getMySQLTimeStamp(bidBar.getTime())), true);
					System.exit(1);
				}
				
				// check if there is already open order in the same direction, skip this one in that case
				// creating tignoresignal entry in this method will prevent this signal from appearing again
				// due to ventrysignals view definition
				if (sameDirectionOrders(signals.getInt("user_id"), signals.getString("ticker"), signals.getString("direction"), signals.getInt("signal_id")))
						continue;

				// check for active opposite orders and positions for same pair and close them
				closeOppositeOpenOrdersAndPositions(signals.getInt("user_id"), signals.getString("ticker"), signals.getString("direction"));
				// submit orders according to the signals
				String orderLabel = getLabel(signals.getString("ticker"), signals.getTimestamp("signal_time")); 
				// if current price already exceeded entry stop, enter on Mkt
				boolean 
					entryOnMarket = false,
					isLong = signals.getString("direction").equals("BUY");
				double 
					stopPrice = FXUtils.roundToPip(signals.getDouble("stopEntry"), instrument),
					takeProfitSign = isLong ? 1.0 : -1.0;
				if (isLong) {
					if (askBar.getClose() >= stopPrice) {
						entryOnMarket = true;
						stopPrice = askBar.getClose();
						log.print("Order " + orderLabel + " to be entered on market !");
					}
				} else {
					if (bidBar.getClose() <= stopPrice) {
						entryOnMarket = true;
						stopPrice = bidBar.getClose();
						log.print("Order " + orderLabel + " to be entered on market !");
					}
				}
				// record it in DB with torder / tposition entries
				double 
					amount = Double.parseDouble(conf.getProperty("tradeAmount", "100000.0")) / 1e6,
					TP = FXUtils.roundToPip(signals.getDouble("stopEntry") + 4 * takeProfitSign * signals.getDouble("atr") / Math.pow(10, instrument.getPipScale()), instrument);
				//TODO: seems not to work... 
				BigDecimal amount_trade_currency = FXUtils.convertByBar(BigDecimal.valueOf(amount), Instrument.EURUSD.getSecondaryCurrency(), instrument.getPrimaryCurrency(), selectedPeriod, OfferSide.BID, bidBar.getTime());
				dbRecordTrade(signals.getInt("user_id"), signals.getInt("signal_id"), FXUtils.dbGetInstrumentID(logDB, signals.getString("ticker")), orderLabel, signals.getString("ticker"), amount_trade_currency.doubleValue(), amount, TP, signals);
				submitOrder(orderLabel, 
						signals.getString("ticker"), 
						signals.getString("direction"), 
						amount_trade_currency.doubleValue(),
						entryOnMarket,						
						stopPrice, 
						signals.getDouble("stopLoss"), 
						TP);
				count++;
			}
		} catch (SQLException e) {
			System.out.println("SQL problem when reading signals: " + e.getMessage());
			System.exit(1);
		}
		
	}

	private void checkTakeProfitDistance(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		// any open positions for this pair ?
		List<IOrder> orders = engine.getOrders(instrument);
		for (IOrder o : orders) {
			if (o.getState().equals(IOrder.State.FILLED) && o.getTakeProfitPrice() != 0.0) {
				// calc distance between curr price and TP, compare in % with total profit "planned"
				if (o.isLong()) {
					double 
						distToTP = o.getTakeProfitPrice() - bidBar.getClose(),
						totalProfitWanted = o.getTakeProfitPrice() - o.getOpenPrice(),
						percProfitReached = 1 - distToTP / totalProfitWanted,
						lockProfitThreshold = Double.parseDouble(conf.getProperty("lockProfitThreshold", "85.0")) / 100.0;
					if (percProfitReached >= lockProfitThreshold) {
						// time to tighen SL to lock in profit !
						double 
							lockStep = Double.parseDouble(conf.getProperty("profitLockStep", "10.0")) / 100.0,
							newSL = o.getOpenPrice() + (totalProfitWanted * (percProfitReached - lockStep));
						if (newSL > o.getStopLossPrice()) {
							log.print("TP threshold reached for " 
									+ o.getLabel() + " at " + FXUtils.getFormatedTimeGMT(bidBar.getTime()) + "; " 
									+ FXUtils.df1.format(percProfitReached * 100.0)
									+ "%; SL moved to " + FXUtils.df5.format(newSL) + " from " + FXUtils.df5.format(o.getStopLossPrice()), true);	
							o.setStopLossPrice(FXUtils.roundToPip(newSL, instrument));
						}
					}
				} else {
					double 
						distToTP = askBar.getClose() - o.getTakeProfitPrice(),
						totalProfitWanted = o.getOpenPrice() - o.getTakeProfitPrice(),
						percProfitReached = 1 - distToTP / totalProfitWanted,
						lockProfitThreshold = Double.parseDouble(conf.getProperty("lockProfitThreshold", "85.0")) / 100;
					if (percProfitReached >= lockProfitThreshold) {
						// time to tighen SL to lock in profit !
						double 
							lockStep = Double.parseDouble(conf.getProperty("profitLockStep", "10.0")) / 100,
							newSL = o.getOpenPrice() - (totalProfitWanted * (percProfitReached - lockStep));
						if (newSL < o.getStopLossPrice()) {
							log.print("TP threshold reached for " 
									+ o.getLabel() + " at " + FXUtils.getFormatedTimeGMT(bidBar.getTime()) + "; " 
									+ FXUtils.df1.format(percProfitReached * 100.0)
									+ "%; SL moved to " + FXUtils.df5.format(newSL) + " from " + FXUtils.df5.format(o.getStopLossPrice()), true);	
							o.setStopLossPrice(FXUtils.roundToPip(newSL, instrument));
						}
					}					
				}
			}
		}
	}

	private void reportAllOpenPositions(Instrument instrument, IBar bidBar, IBar askBar, long logTime) throws JFException {
		int instrument_id = FXUtils.dbGetInstrumentID(logDB, instrument.toString());
		if (instrument_id == -1) {
			System.out.println("Can not fetch instrument_id for : " + instrument.toString());
			System.exit(1);
			
		}
		// Attention: there can be multiple open orders per instrument !
		ResultSet openOrders = FXUtils.dbReadQuery(logDB, "SELECT label FROM " + FXUtils.getDbToUse() + ".torder WHERE instrument_id = " + instrument_id + " AND status = 2 AND user_id = " + FXUtils.dbGetUserID(logDB, conf.getProperty("user_email")));
		try {
			IOrder firstOrder = null;
			// for future - if there are more then 2 positions per trade
			List<IOrder> secondaryOrders = new ArrayList<IOrder>();
			while (openOrders != null && openOrders.next()) {
				String label = openOrders.getString("label");
				IOrder currOrder = engine.getOrder(label);
				if (!currOrder.getLabel().endsWith("_2nd"))
					firstOrder = currOrder;
				else
					secondaryOrders.add(currOrder);				
			}
			double currTotalPnL = 0.0;
			for (IOrder o : secondaryOrders) {
				orderBookKeeping(o, bidBar, askBar, logTime);	
				currTotalPnL += o.getProfitLossInUSD();
			}
			if (firstOrder != null) {
				orderBookKeeping(firstOrder, bidBar, askBar, logTime);
				currTotalPnL += firstOrder.getProfitLossInUSD();
				PositionLog pl = positionLogs.get(firstOrder.getLabel());
				pl.updateTotalPnL(currTotalPnL, logTime);
			}

		} catch (SQLException e) {
			System.out.println("Problem when fetching open order(s) for " + instrument.toString() + ": " + e.getMessage());
			System.exit(1);
		}
	}

	private void orderBookKeeping(IOrder currOrder, IBar bidBar, IBar askBar, long logTime) {
		TradeLog currTradeLog = tradeLogs.get(currOrder.getLabel());
		currTradeLog.update(bidBar, askBar, logTime);		
	}

	private boolean sameDirectionOrders(int user_id, String ticker, String direction, int signal_id) {
		ResultSet openOrders = FXUtils.dbReadQuery(logDB, "SELECT order_id, o.label FROM " + FXUtils.getDbToUse() + ".`torder` o join " + FXUtils.getDbToUse() + ".ventrysignals s on s.signal_id = o.signal_id WHERE o.user_id = " + user_id 
				+ " AND o.instrument_id = " + FXUtils.dbGetInstrumentID(logDB, ticker) 
				+ " AND s.direction = '" + direction + "'"
				+ " AND status < 3");
		if (openOrders == null)
			return false;
		try {
			if (openOrders.next()) {
				// mark this signal for this user to be ignored in next runs
				FXUtils.dbUpdateInsert(logDB, "INSERT INTO " + FXUtils.getDbToUse() + ".tignoresignal (signal_id, user_id) VALUES (" 
						+ signal_id + ", " + user_id + ")");
				return true;
			}
		} catch (SQLException e) {
			System.out.println("Problem reading unfilled orders: " +  e.getMessage());
			System.exit(1);
		}
		return false;
	}

	private void closeOppositeOpenOrdersAndPositions(int user_id, String ticker, String direction) throws JFException {
		ResultSet openOrders = FXUtils.dbReadQuery(logDB, "SELECT order_id, o.label, status FROM " + FXUtils.getDbToUse() 
				+ ".`torder` o join " + FXUtils.getDbToUse() 
				+ ".ventrysignals s on s.signal_id = o.signal_id WHERE o.user_id = " + user_id 
				+ " AND o.instrument_id = " + FXUtils.dbGetInstrumentID(logDB, ticker) 
				+ " AND s.direction <> '" + direction + "'"
				+ " AND status < 3");
		if (openOrders == null)
			return;
		try {
			while (openOrders.next()) {
				// can not have different orders with same label, despite they belong to the same position !
				// add _2 to 2nd !
				String orderLabel = openOrders.getString("label");
				int orderStatus = openOrders.getInt("status");
				IOrder order = engine.getOrder(orderLabel);
				if (order != null) {
					FXUtils.dbUpdateInsert(logDB, "UPDATE " + FXUtils.getDbToUse() + ".torder SET status = 4 WHERE order_id = " + openOrders.getInt("order_id"));
					order.close();
				} else {
					log.print("No broker order found for order " + orderLabel + ", status" + orderStatus, true);
				}
			}
		} catch (SQLException e) {
			System.out.println("Problem reading unfilled orders: " +  e.getMessage());
			System.exit(1);
		}
		
	}

	private void dbRecordTrade(int user_id, int signal_id, int instrument_id, String orderLabel, String ticker, double amount, double amount_bc, double TP, ResultSet orderData) {
		try {
			FXUtils.dbUpdateInsert(logDB, "INSERT INTO " + FXUtils.getDbToUse() + ".`torder` (`signal_id`, `user_id`, `instrument_id`, `ordinal_number`, `status`, `label`, `broker`, "
				+ "direction, amount, amount_bc, stopEntry, limitEntry, stopLoss, takeProfit) "
				+ "VALUES (" + signal_id + ", " + user_id + ", " + instrument_id + ", 1, 0, '" + orderLabel + "', 'Dukascopy', '"
				+ orderData.getString("direction") + "', " + FXUtils.df2.format(amount) + ", " + FXUtils.df2.format(amount_bc) + ", " 
				+ FXUtils.df5.format(orderData.getDouble("stopEntry")) + ", " 
				+ FXUtils.df5.format(orderData.getDouble("limitEntry")) + ", " 
				+ FXUtils.df5.format(orderData.getDouble("stopLoss")) + ", " 
				+ FXUtils.df5.format(TP) + ")"); 
			ResultSet order = FXUtils.dbReadQuery(logDB, "SELECT order_id FROM " + FXUtils.getDbToUse() + ".torder WHERE signal_id = " + signal_id + " AND user_id = " + user_id);
			if (order != null && order.next()) {
				FXUtils.dbUpdateInsert(logDB, "INSERT INTO " + FXUtils.getDbToUse() + ".tposition (user_id, instrument_id, label, direction, max_no_of_positions, no_of_positions) VALUES (" 
						+ user_id + ", "+ instrument_id  + ", '" + orderLabel + "', '"+ orderData.getString("direction") + "', 2, 1)");
				ResultSet position = FXUtils.dbReadQuery(logDB, "SELECT position_id FROM " + FXUtils.getDbToUse() + ".tposition WHERE instrument_id = " + instrument_id 
						+ " AND user_id = " + user_id + " AND label = '" + orderLabel + "'");
				if (position != null && position.next()) {
					FXUtils.dbUpdateInsert(logDB, "INSERT INTO " + FXUtils.getDbToUse() + ".`tpositionorders` (`position_id`, `order_id`) VALUES (" + position.getInt("position_id") + ", " + order.getInt("order_id") + ")");
				}
				
			}			
		} catch (SQLException e) {
			System.out.println("SQL problem when recording trades: " + e.getMessage());
			System.exit(1);
		}
	}

	private void submitOrder(String label, String instrument, String direction,	double amount, boolean entryOnMarket, double stopEntry, double stopLoss, double takeProfit) throws JFException {
		if (!direction.equals("BUY") && !direction.equals("SELL")) {
			log.print("Wrong direction string (" + direction + ") ! No order submitted !");
			return;
		}
		
		OrderCommand entryType;
		if (direction.equals("BUY")) {
			if (!entryOnMarket)
				entryType = OrderCommand.BUYSTOP;
			else
				entryType = OrderCommand.BUY;
		} else {
			if (!entryOnMarket)
				entryType = OrderCommand.SELLSTOP;
			else
				entryType = OrderCommand.SELL;			
		}
		engine.submitOrder(label, Instrument.fromString(instrument), entryType, amount, stopEntry, 20, stopLoss, takeProfit);
	}

	private String getLabel(String ticker, Timestamp timestamp) {
		return new String(ticker.replace("/", "") + "_" + new SimpleDateFormat("yyyy_MM_dd_HH_mm").format(timestamp) + "_Ichi");
	}

	private ResultSet getSignals(String email, Instrument instrument, String barTime) {
		String 
			statementStr = getSignalsSQL(email, instrument, barTime);
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			return result;
		} catch (SQLException ex) {
			   log.print("Log database problem: " + ex.getMessage());
			   log.print(statementStr);
			   log.close();
	           System.exit(1);
		}
		return null;
	}

	protected String getSignalsSQL(String email, Instrument instrument,	String barTime) {
		return "SELECT s.user_id, s.signal_id, s.strategy, s.ticker, s.signal_time, s.direction, s.TimeFrame, s.stopEntry, s.limitEntry, s.stopLoss, s.ValueD1 as atr "
			+ "FROM " + FXUtils.getDbToUse() + ".ventrysignals s left join " + FXUtils.getDbToUse() + ".torder o on o.user_id = s.user_id and o.signal_id = s.signal_id "
			+ "WHERE ticker = '" + instrument.toString() + "' AND email = '" + email + "' AND signal_time <= '" + barTime  
			+ "' AND strategy = 'IchiEntrySignals' and o.order_id is null";
	}

	@Override
	public void onMessage(IMessage message) throws JFException {
		if (message.getType().equals(IMessage.Type.ORDER_SUBMIT_REJECTED)) {
			log.print("Order submit rejected ! Order " + message.getOrder().getLabel() + ", entry stop "
				+ (message.getOrder().getInstrument().getPipScale() == 2 ? FXUtils.df2.format(message.getOrder().getOpenPrice()) : FXUtils.df5.format(message.getOrder().getOpenPrice()))
				+ ", reason " + message.getContent());			
			return;
		}
		if (message.getType().equals(IMessage.Type.ORDER_FILL_REJECTED)) {
			log.print("Order fill rejected ! Order " + message.getOrder().getLabel() + ", entry stop "
				+ (message.getOrder().getInstrument().getPipScale() == 2 ? FXUtils.df2.format(message.getOrder().getOpenPrice()) : FXUtils.df5.format(message.getOrder().getOpenPrice()))
				+ ", reason " + message.getContent());			
			return;
		}		
		
		IOrder order = message.getOrder();
		if (order != null) {
			String orderLabel = message.getOrder().getLabel();
			int order_id = -1;
			ResultSet orderToUse = FXUtils.dbReadQuery(logDB, "SELECT order_id FROM " + FXUtils.getDbToUse() + ".torder WHERE label = '" + orderLabel + "' AND user_id = " + FXUtils.dbGetUserID(logDB, conf.getProperty("user_email")));
			try {
				if (orderToUse != null && orderToUse.next()) {
					order_id = orderToUse.getInt("order_id");
				}
			} catch (SQLException e) {
				System.out.println("Problem when fething order: " + e.getMessage());
				System.exit(1);
			}

			DecimalFormat df = order.getInstrument().getPipScale() == 2 ? FXUtils.df2 : FXUtils.df5;
			if (message.getType().equals(IMessage.Type.ORDER_SUBMIT_OK)) {
				log.print("Order " + order.getLabel() + " submitted with entry price " + df.format(order.getOpenPrice()) + ", SL " + df.format(order.getStopLossPrice()) + " (" + FXUtils.df1.format(Math.abs(order.getOpenPrice() - order.getStopLossPrice()) * Math.pow(10, order.getInstrument().getPipScale())) + " pips)");
				dbUpdateOrderStatus(orderLabel, 1);
			}
			else if (message.getType().equals(IMessage.Type.ORDER_FILL_OK)) {
				onOrderFill(message, order, order_id, df);
			}
			else if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK)) {
				onOrderClose(message, order, orderLabel, df);
				return;
			}
			else
				return;

		}
	}

	private void onOrderClose(IMessage message, IOrder order, String orderLabel, DecimalFormat df) throws JFException {
		// two cases have to be handled:
		// 1. close of the 1st position with SL --> remove waiting 2nd position order
		// 2. SL of 2nd position only was triggered but 1st position is still open -->
		// in this case immediatelly put back entry order for 2nd position in case it recovers !
		// both of these cases need to be clearly distinguished from TP closes !!!
		
		
		// if closed with SL check for waiting 2nd order position and cancel it
		// simply use _2nd to identify !
		if (order.getLabel() != null && !order.getLabel().endsWith("_2nd")) {
			// case 1) Careful ! 2nd orders have format label_nn_2nd !! Find the right one !
			// in addition, if 1st order is cancelled by an opposite signal there is no 2nd order to cancel !
			boolean 
				SLClose = false,
				TPClose = false;
			Set<Reason> closeReasons = message.getReasons();
			// BUT !!! Order can be also closed by strategy because of appearance of opposite signal !!!
			// handle that case correcly !!!
			for (Reason curr : closeReasons) {
				if (curr.equals(Reason.ORDER_CLOSED_BY_SL)) {							
					SLClose = true;
				} else if (curr.equals(Reason.ORDER_CLOSED_BY_TP)) {
					TPClose = true;
				}
			}
			if (SLClose) {
				if (conf.getProperty("tradeLogs", "no").equals("yes"))
					record1stOrderClose(message, order, LogEvents.SL_1ST_POSITION);

				log.print(FXUtils.getFormatedTimeGMT(message.getCreationTime()) + ": order " + order.getLabel() + " closed due to SL", true);

				String secondOrderLabel = dbGet2ndOrderLabel(order.getLabel());
				IOrder secondOrder = null;
				if (secondOrderLabel != null)
					secondOrder = engine.getOrder(secondOrderLabel);
				else {
					log.print("No 2nd order found for " + order.getLabel(), true);
					// System.exit(1);
				}					
				if (secondOrder != null) {
					log.print(FXUtils.getFormatedTimeGMT(message.getCreationTime()) + ": canceling 2nd order " + secondOrderLabel + " due to SL of " + order.getLabel(), true);
					try {
						secondOrder.close(); // triggers new onMessage !
					} catch (JFException e) {
						System.out.println("Exception when trying to close order " + secondOrderLabel + " ! " + e.getMessage());
						System.exit(1);
					} 
				}
			} else if (TPClose && conf.getProperty("tradeLogs", "no").equals("yes")) {
				record1stOrderClose(message, order, LogEvents.TP_1ST_POSITION);
			}
		} else if (order.getLabel() != null && order.getLabel().endsWith("_2nd")) {
			// 2nd position order got closed. Now check whether it's cancel, TP or SL and in latter case
			// submit entry order again
			boolean 
				TPClose = false,
				SLClose = false;
			Set<Reason> closeReasons = message.getReasons();
			for (Reason curr : closeReasons) {
				if (curr.equals(Reason.ORDER_CLOSED_BY_SL)) {
					// case 2)
					SLClose = true;
				} else if (curr.equals(Reason.ORDER_CLOSED_BY_TP)) {
					TPClose = true;
				}
			}
			if (SLClose) {
				if (conf.getProperty("tradeLogs", "no").equals("yes"))
					record2ndOrderClose(message, order, LogEvents.SL_2ND_POSITION);
				submit2ndOrderAgain(order, message.getCreationTime());
			} else if (TPClose && conf.getProperty("tradeLogs", "no").equals("yes")) {
				record2ndOrderClose(message, order, LogEvents.TP_2ND_POSITION);
			}
		}
		log.print(FXUtils.getFormatedTimeGMT(message.getCreationTime()) + ": order " + order.getLabel() + " closed at price " + df.format(order.getOpenPrice()) + ", PnL " + FXUtils.df1.format(order.getProfitLossInPips()));
		
		dbUpdateOrderStatus(orderLabel, 3);
	}

	private void dbUpdateOrderStatus(String orderLabel, int status) {
		ResultSet orderToUpdate = FXUtils.dbReadQuery(logDB, "SELECT order_id FROM " + FXUtils.getDbToUse() + ".torder WHERE label = '" + orderLabel + "' AND user_id = " + FXUtils.dbGetUserID(logDB, conf.getProperty("user_email")));
		try {
			while (orderToUpdate != null && orderToUpdate.next()) {
				FXUtils.dbUpdateInsert(logDB, "UPDATE " + FXUtils.getDbToUse() + ".torder SET status = " + status + " WHERE order_id = " + orderToUpdate.getInt("order_id"));
			}
		} catch (SQLException e) {
			System.out.println("Problem when updating orders: " + e.getMessage());
			System.exit(1);
		}
	}

	private void onOrderFill(IMessage message, IOrder order, int order_id, DecimalFormat df) throws JFException {
		// submit 2nd entry order 2 ATRs away from original entry, but only for the first order !
		if (order.getLabel() != null && !order.getLabel().endsWith("_2nd")) {
			// logging ! Open a tpositionlog entry and the first ttradelog, update RunMonitor
			//TODO: moram ove objekte da nacrtam, treba mi pregled !
			if (conf.getProperty("tradeLogs", "no").equals("yes")) {
				PositionLog pl = new PositionLog(order, btRun.getRun_id(), logDB);
				int positionlog_id = pl.dbRecordFill(message.getCreationTime());
				positionLogs.put(order.getLabel(), pl);
				TradeLog tl = new TradeLog(order_id, positionlog_id, order, logDB);
				tradeLogs.put(order.getLabel(), tl);
				tl.dbRecordFill(message.getCreationTime());
			}
			submit2ndOrder(order, message.getCreationTime());
		}
		else if (order.getLabel() != null && order.getLabel().endsWith("_2nd") && conf.getProperty("tradeLogs", "no").equals("yes")) {
			//TODO: in case 2nd order was filled update number of orders for the position !
			String firstOrderLabel = getFirstOrderLabel(order);
			ResultSet position = FXUtils.dbReadQuery(logDB, "SELECT positionlog_id FROM " + FXUtils.getDbToUse() + ".tpositionlog WHERE fk_btrun_id = "
					+ btRun.getRun_id() + " AND order_label = '" + firstOrderLabel + "'");
			try {
				if (position != null && position.next()) {
					TradeLog tl2ndOrder = new TradeLog(order_id, position.getInt("positionlog_id"), order, logDB);	
					tradeLogs.put(order.getLabel(), tl2ndOrder);
					tl2ndOrder.dbRecord2ndFill(message.getCreationTime());
					FXUtils.dbUpdateInsert(logDB, "UPDATE " + FXUtils.getDbToUse() + ".tpositionlog SET 2nd_pos_attempts = 2nd_pos_attempts + 1 WHERE positionlog_id = " 
							+ position.getInt("positionlog_id"));
				}
				else {
					System.out.println("Can't fetch tpositionlog record !");
					System.err.println("Can't fetch tpositionlog record !");
					System.exit(1);
				}
			} catch (SQLException e) {
				System.out.println("Problem fetching tpositionlog record ! " + e.getMessage());
				System.err.println("Problem fetching tpositionlog record ! " + e.getMessage());
				System.exit(1);
			}			
		}
		log.print("Order " + order.getLabel() + " filled with fill price " + df.format(order.getOpenPrice()));
		dbUpdateOrderStatus(order.getLabel(), 2);
	}


	private String dbGet2ndOrderLabel(String firstOrderLabel) {
		String statementStr = "SELECT label FROM " + FXUtils.getDbToUse() + ".torder WHERE label LIKE '" 
				+ firstOrderLabel + "%2nd' AND status < 3";
		try {
			Statement qry = logDB.createStatement();
			ResultSet result = qry.executeQuery(statementStr);
			if (result != null && result.next()) {
				return result.getString("label");
			}
		} catch (SQLException ex) {
			   log.print("Problem fetching waiting 2nd order: " + ex.getMessage());
			   log.print(statementStr);
			   log.close();
	           System.exit(1);
		}
		return null;
	}

	private void submit2ndOrder(IOrder firstOrder, long time) throws JFException {
		try {
			// first check that there is only ONE open order for this signal !
			// whole position can only have total of two orders
			if (firstOrder.getLabel().endsWith("_2nd"))
				return;
			
			int user_id = FXUtils.dbGetUserID(logDB, conf.getProperty("user_email"));
			if (user_id == -1)
				return;
			
			ResultSet firstOrderRecord = FXUtils.dbReadQuery(logDB, "SELECT signal_id, user_id, instrument_id FROM " + FXUtils.getDbToUse() + ".torder WHERE label = '" + firstOrder.getLabel()
						+ "' AND user_id = " + user_id + " AND ordinal_number = 1");
			if (firstOrderRecord != null && firstOrderRecord.next()) {
				// need ATR to calc entry price
				ResultSet signal = FXUtils.dbReadQuery(logDB, "SELECT direction, ValueD1 FROM " + FXUtils.getDbToUse() + ".tsignal WHERE idsignal = " + firstOrderRecord.getInt("signal_id"));
				if (signal != null && signal.next()) {
					double
						amount = 2.0 * firstOrder.getAmount(),
						directionSign = firstOrder.isLong() ? 1.0 : -1.0,
						ATRAbsValue = signal.getDouble("ValueD1") / Math.pow(10, firstOrder.getInstrument().getPipScale()),
						entryPrice = FXUtils.roundToPip(firstOrder.getOpenPrice() + directionSign * 2.0 * ATRAbsValue, firstOrder.getInstrument()),
						SL = FXUtils.roundToPip(entryPrice - directionSign * 1.5 * ATRAbsValue, firstOrder.getInstrument()),
						TP = FXUtils.roundToPip(firstOrder.getTakeProfitPrice(), firstOrder.getInstrument());
					BigDecimal amount_bc = FXUtils.convertByBar(BigDecimal.valueOf(amount), firstOrder.getInstrument().getPrimaryCurrency(), Instrument.EURUSD.getSecondaryCurrency(), selectedPeriod, OfferSide.BID, time);

					String new_label = new String(firstOrder.getLabel() + "_01_2nd");
					// can not have different orders with same label, despite they belong to the same position !
					// add _2 to 2nd !
					// carefully check this situation everywhere !
					// record 2nd order
					FXUtils.dbUpdateInsert(logDB, "INSERT INTO " + FXUtils.getDbToUse() + ".`torder` (`signal_id`, `user_id`, `instrument_id`, `ordinal_number`, `status`, `label`, `broker`, "
							+ "direction, amount, amount_bc, stopEntry, limitEntry, stopLoss, takeProfit) "
							+ "VALUES (" + firstOrderRecord.getInt("signal_id") + ", " 
							+ firstOrderRecord.getInt("user_id")+ ", " 
							+ firstOrderRecord.getInt("instrument_id") + ", 2, 0, '" + new_label + "', 'Dukascopy', '" 					
							+ signal.getString("direction") + "', " + FXUtils.df2.format(amount) + ", " + FXUtils.df2.format(amount_bc.doubleValue()) + ", " 
							+ FXUtils.df5.format(entryPrice) + ", " 
							+ FXUtils.df5.format(entryPrice) + ", " 
							+ FXUtils.df5.format(SL) + ", " 
							+ FXUtils.df5.format(TP) + ")"); 
					
					submitOrder(new_label, 
								firstOrder.getInstrument().toString(), 
								firstOrder.isLong() ? "BUY" : "SELL", 
								amount, 
								false, entryPrice, SL, TP);
					// attention: this will trigger onMessage ! But since it only uses exact label match 
					// that should be OK
					// now update tposition and add tpositionorder entry...
					ResultSet order = FXUtils.dbReadQuery(logDB, "SELECT order_id FROM " + FXUtils.getDbToUse() + ".torder WHERE label = '" + new_label + "' AND user_id = " + user_id);
					if (order != null && order.next()) {
						ResultSet position = FXUtils.dbReadQuery(logDB, "SELECT position_id FROM " + FXUtils.getDbToUse() + ".tposition WHERE label = '" + firstOrder.getLabel() + "'  AND user_id = " + user_id);
						if (position != null && position.next()) {
							FXUtils.dbUpdateInsert(logDB, "INSERT INTO " + FXUtils.getDbToUse() + ".`tpositionorders` (`position_id`, `order_id`) VALUES (" + position.getInt("position_id") + ", " + order.getInt("order_id") + ")");
						}
						FXUtils.dbUpdateInsert(logDB, "UPDATE " + FXUtils.getDbToUse() + ".tposition SET no_of_positions = 2 WHERE position_id = " + position.getInt("position_id"));
						
					}
				}
			}
		} catch (SQLException e) {
			log.print("Problem fetching the first order: " + e.getMessage());
		}		
	}

	private void submit2ndOrderAgain(IOrder secondOrder, long time) throws JFException {
		try {	
			int user_id = FXUtils.dbGetUserID(logDB, conf.getProperty("user_email"));
			if (user_id == -1)
				return;
			
			ResultSet secondOrderRecord = FXUtils.dbReadQuery(logDB, "SELECT signal_id, user_id, instrument_id FROM " + FXUtils.getDbToUse() + ".torder WHERE label = '" + secondOrder.getLabel()
						+ "' AND user_id = " + user_id + " AND ordinal_number >= 2");
			if (secondOrderRecord != null && secondOrderRecord.next()) {
				// need ATR to calc entry price
				ResultSet signal = FXUtils.dbReadQuery(logDB, "SELECT direction, ValueD1 FROM " + FXUtils.getDbToUse() + ".tsignal WHERE idsignal = " + secondOrderRecord.getInt("signal_id"));
				if (signal != null && signal.next()) {
					int 
						ordNoPos = secondOrder.getLabel().lastIndexOf("_"),
						orderNumber = Integer.parseInt(secondOrder.getLabel().substring(ordNoPos - 2, ordNoPos)) + 1;
					String 
						orderNumberStr = "_" + FXUtils.if2.format(orderNumber),
						org_order_label = secondOrder.getLabel().substring(0, secondOrder.getLabel().lastIndexOf("_") - 3), 
						new_label = org_order_label + orderNumberStr + "_2nd";
					BigDecimal amount_bc = FXUtils.convertByBar(BigDecimal.valueOf(secondOrder.getAmount()), secondOrder.getInstrument().getPrimaryCurrency(), Instrument.EURUSD.getSecondaryCurrency(), selectedPeriod, OfferSide.BID, time);
					// can not have different orders with same label, despite they belong to the same position !
					// add _2 to 2nd !
					// carefully check this situation everywhere !
					// record 2nd order
					FXUtils.dbUpdateInsert(logDB, "INSERT INTO " + FXUtils.getDbToUse() + ".`torder` (`signal_id`, `user_id`, `instrument_id`, `ordinal_number`, `status`, `label`, `broker`, "
							+ "direction, amount, amount_bc, stopEntry, limitEntry, stopLoss, takeProfit) "
							+ "VALUES (" + secondOrderRecord.getInt("signal_id") + ", " 
							+ secondOrderRecord.getInt("user_id")+ ", " +
							+ secondOrderRecord.getInt("instrument_id") + ", " + (orderNumber + 1) + ", 0, '" + new_label + "', 'Dukascopy', '"
							+ signal.getString("direction") + "', " + FXUtils.df2.format(secondOrder.getAmount()) + ", " + FXUtils.df2.format(amount_bc.doubleValue()) + ", " 
							+ FXUtils.df5.format(secondOrder.getOpenPrice()) + ", " 
							+ FXUtils.df5.format(secondOrder.getOpenPrice()) + ", " 
							+ FXUtils.df5.format(secondOrder.getStopLossPrice()) + ", " 
							+ FXUtils.df5.format(secondOrder.getTakeProfitPrice()) + ")"); 
					
					submitOrder(new_label, 
								secondOrder.getInstrument().toString(), 
								secondOrder.isLong() ? "BUY" : "SELL", 
								secondOrder.getAmount(), 
								false, 
								FXUtils.roundToPip(secondOrder.getOpenPrice(), secondOrder.getInstrument()), 
								FXUtils.roundToPip(secondOrder.getStopLossPrice(), secondOrder.getInstrument()),
								FXUtils.roundToPip(secondOrder.getTakeProfitPrice(), secondOrder.getInstrument()));
					// attention: this will trigger onMessage ! But since it only uses exact label match 
					// that should be OK
					// now update tposition and add tpositionorder entry...
					ResultSet order = FXUtils.dbReadQuery(logDB, "SELECT order_id FROM " + FXUtils.getDbToUse() + ".torder WHERE label = '" + new_label + "' AND user_id = " + user_id);
					if (order != null && order.next()) {
						ResultSet position = FXUtils.dbReadQuery(logDB, "SELECT position_id FROM " + FXUtils.getDbToUse() + ".tposition WHERE label = '" + org_order_label + "'  AND user_id = " + user_id);
						if (position != null && position.next()) {
							FXUtils.dbUpdateInsert(logDB, "INSERT INTO " + FXUtils.getDbToUse() + ".`tpositionorders` (`position_id`, `order_id`) VALUES (" + position.getInt("position_id") + ", " + order.getInt("order_id") + ")");
						}
					}
				}
			}
		} catch (SQLException e) {
			log.print("Problem fetching the first order: " + e.getMessage());
		}		
	}	

	@Override
	public void onStop() throws JFException {
		if (conf.getProperty("tradeLogs", "no").equals("yes"))
			btRun.stop();
		// live trading strategies must NOT close orders open left 
		if (conf.getProperty("closeAtEnd", "no").equals("yes")) {
			super.onStopExec();
		} else {
		    log.print("Stopping strategy " + getStrategyName());
			log.close();			
		}
	}

	@Override
	protected String getStrategyName() {
		return "JForex_auto_entry";
	}

	@Override
	protected String getReportFileName() {
		return getStrategyName() + "_";
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {	}

	@Override
	public void onAccount(IAccount account) throws JFException { }

	private void record1stOrderClose(IMessage message, IOrder order, LogEvents event) throws JFException {
		TradeLog tl = tradeLogs.get(order.getLabel());
		PositionLog pl = positionLogs.get(order.getLabel());
		// PnL of 2nd orders constantly updated in this field at their closes
		FXUtils.dbUpdateInsert(logDB, "UPDATE " + FXUtils.getDbToUse() + ".tpositionlog SET total_PnL = total_PnL + " + order.getProfitLossInUSD()
				+ ", close_time = '"
				+ FXUtils.getMySQLTimeStampGMT(message.getCreationTime()) 
				+ "', max_PnL = " + FXUtils.df1.format(pl.getMaxPnL())
				+ ", max_PnL_time = '" + FXUtils.getMySQLTimeStampGMT(pl.getMaxPnLTime())
				+ "' WHERE fk_btrun_id = " + btRun.getRun_id() 
				+ " AND order_label = '" + order.getLabel() + "'");
		
		double pipsMultiply = order.getInstrument().getPipScale() == 2 ? 100.00 : 1;
		FXUtils.dbUpdateInsert(logDB, "INSERT INTO " + FXUtils.getDbToUse() + ".ttradelog (positionlog_id, order_id, log_time, event, "
				+ "min_TP_distance_pips, min_TP_distance_time, "
				+ "`PnLPips`, `PnL`, `PnLPips_bc`, `PnL_bc`) VALUES ("
				+ tl.getPosition_log_id() + ", " + tl.getOrder_id() 
				+ ", '" + FXUtils.getMySQLTimeStampGMT(message.getCreationTime()) + "', '" + event 
				+ "', " + FXUtils.df2.format(tl.getMin_tp_distance_pips() * Math.pow(10, order.getInstrument().getPipScale()))
				+ ", '" + FXUtils.getMySQLTimeStampGMT(tl.getMin_tp_distance_time()) + "', "
				+ FXUtils.df2.format(order.getProfitLossInPips()) + ", "
				+ FXUtils.df2.format(FXUtils.convertByBar(BigDecimal.valueOf(order.getProfitLossInUSD()), Instrument.EURUSD.getSecondaryCurrency(), order.getInstrument().getSecondaryCurrency(), selectedPeriod, OfferSide.BID, message.getCreationTime())) + ", "
				+ FXUtils.df2.format(pipsMultiply * FXUtils.convertByBar(BigDecimal.valueOf(order.getProfitLossInPips()), order.getInstrument().getSecondaryCurrency(), Instrument.EURUSD.getSecondaryCurrency(), selectedPeriod, OfferSide.BID, message.getCreationTime()).doubleValue()) + ", "
				+ FXUtils.df2.format(order.getProfitLossInUSD()) + ")");
	}
	
	private void record2ndOrderClose(IMessage message, IOrder order, LogEvents event) throws JFException {
		TradeLog tl = tradeLogs.get(order.getLabel());
		PositionLog pl = positionLogs.get(getFirstOrderLabel(order));
		pl.updateTotalClosed2ndPosPnL(order.getProfitLossInUSD());
		
		FXUtils.dbUpdateInsert(logDB, "UPDATE " + FXUtils.getDbToUse() + ".tpositionlog SET total_PnL = total_PnL + " + order.getProfitLossInUSD()
				+ ", total_2nd_pos_PnL = total_2nd_pos_PnL + " + order.getProfitLossInUSD()
				+ " WHERE fk_btrun_id = " + btRun.getRun_id() 
				+ " AND order_label = '" + getFirstOrderLabel(order) + "'");

		double pipsMultiply = order.getInstrument().getPipScale() == 2 ? 100.00 : 1;
		FXUtils.dbUpdateInsert(logDB, "INSERT INTO " + FXUtils.getDbToUse() + ".ttradelog (positionlog_id, order_id, log_time, event, "
				+ "min_TP_distance_pips, min_TP_distance_time, "
				+ "`PnLPips`, `PnL`, `PnLPips_bc`, `PnL_bc`) VALUES ("
				+ tl.getPosition_log_id() + ", " + tl.getOrder_id() 
				+ ", '" + FXUtils.getMySQLTimeStampGMT(message.getCreationTime()) + "', '" + event 
				+ "', " + FXUtils.df2.format(tl.getMin_tp_distance_pips() * Math.pow(10, order.getInstrument().getPipScale()))
				+ ", '" + FXUtils.getMySQLTimeStampGMT(tl.getMin_tp_distance_time()) + "', "
				+ FXUtils.df2.format(order.getProfitLossInPips()) + ", "
				+ FXUtils.df2.format(FXUtils.convertByBar(BigDecimal.valueOf(order.getProfitLossInUSD()), Instrument.EURUSD.getSecondaryCurrency(), order.getInstrument().getSecondaryCurrency(), selectedPeriod, OfferSide.BID, message.getCreationTime())) + ", "
				+ FXUtils.df2.format(pipsMultiply * FXUtils.convertByBar(BigDecimal.valueOf(order.getProfitLossInPips()), order.getInstrument().getSecondaryCurrency(), Instrument.EURUSD.getSecondaryCurrency(), selectedPeriod, OfferSide.BID, message.getCreationTime()).doubleValue()) + ", "
				+ FXUtils.df2.format(order.getProfitLossInUSD()) + ")");
	}

	private String getFirstOrderLabel(IOrder secondOrder) {
		return secondOrder.getLabel().substring(0, secondOrder.getLabel().length() - 7);
	}

}
