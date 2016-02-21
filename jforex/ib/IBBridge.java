package jforex.ib;

import java.util.ArrayList;
import java.util.List;

import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.EWrapperMsgGenerator;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;

public class IBBridge implements EWrapper {
	private static final int NOT_AN_FA_ACCOUNT_ERROR = 321;
	private int faErrorCodes[] = { 503, 504, 505, 522, 1100,
			NOT_AN_FA_ACCOUNT_ERROR };
	private boolean faError;

	private EClientSocket m_client = new EClientSocket(this);
	String faGroupXML, faProfilesXML, faAliasesXML;
	public String m_FAAcctCodes;
	public boolean m_bIsFAAccount = false;

	private boolean m_disconnectInProgress = false, m_requestingOrders = false;

	private List<OrderFull> openOrders = new ArrayList<OrderFull>(),
			filledOrders = new ArrayList<OrderFull>();

	protected String m_lastMessage, m_tickerToFilter = new String("");

	public IBBridge() {
	}

	public void connect(String ipAddress, int port, int clientId) {
		m_bIsFAAccount = false;
		// connect to TWS
		m_disconnectInProgress = false;

		m_client.eConnect(ipAddress, port, clientId);
		if (m_client.isConnected()) {
			m_lastMessage = "Connected to Tws server version "
					+ m_client.serverVersion() + " at "
					+ m_client.TwsConnectionTime();
		}
	}

	public void disconnect() {
		// disconnect from TWS
		m_disconnectInProgress = true;
		m_client.eDisconnect();
	}

	public void requestOpenOrders() {
		// until all open orders are transfered back...
		if (m_requestingOrders)
			return;

		m_requestingOrders = true;
		openOrders.clear();
		filledOrders.clear();
		m_client.reqOpenOrders();
		// dangerous - endless loop ?
		while (m_requestingOrders) {
		}
	}

	public void whatIfOrder() {
		placeOrder(true);
	}

	void onPlaceOrder() {
		placeOrder(false);
	}

	void placeOrder(boolean whatIf) {
		// TODO: collect Order data run m_orderDlg

		// place order
		// m_client.placeOrder( m_orderDlg.m_id, m_orderDlg.m_contract, order );
	}

	public void requestAllOpenOrders() {
		// until all open orders are transfered back...
		if (m_requestingOrders)
			return;

		m_requestingOrders = true;
		openOrders.clear();
		filledOrders.clear();
		// request list of all open orders
		m_client.reqAllOpenOrders();
	}

	public void requestAllOpenOrdersFiltered(String tickerToFilter) {
		// until all open orders are transfered back...
		if (m_requestingOrders)
			return;

		m_requestingOrders = true;
		m_tickerToFilter = tickerToFilter;
		openOrders.clear();
		filledOrders.clear();
		// request list of all open orders
		m_client.reqAllOpenOrders();
	}

	public void reqAutoOpenOrders() {
		// request to automatically bind any newly entered TWS orders
		// to this API client. NOTE: TWS orders can only be bound to
		// client's with clientId=0.
		m_client.reqAutoOpenOrders(true);
	}

	void onReqManagedAccts() {
		// request the list of managed accounts
		m_client.reqManagedAccts();
	}

	void onGlobalCancel() {
		m_client.reqGlobalCancel();
	}

	public void tickPrice(int tickerId, int field, double price,
			int canAutoExecute) {
		// received price tick
		String msg = EWrapperMsgGenerator.tickPrice(tickerId, field, price,
				canAutoExecute);
		// TODO: decide what to do with received price tick
	}

	public void tickOptionComputation(int tickerId, int field,
			double impliedVol, double delta, double optPrice,
			double pvDividend, double gamma, double vega, double theta,
			double undPrice) {
		// received computation tick
		String msg = EWrapperMsgGenerator.tickOptionComputation(tickerId,
				field, impliedVol, delta, optPrice, pvDividend, gamma, vega,
				theta, undPrice);
		// TODO: decide what to do with received data
	}

	public void tickSize(int tickerId, int field, int size) {
		// received size tick
		String msg = EWrapperMsgGenerator.tickSize(tickerId, field, size);
		// TODO: decide what to do with received data
	}

	public void tickGeneric(int tickerId, int tickType, double value) {
		// received generic tick
		String msg = EWrapperMsgGenerator
				.tickGeneric(tickerId, tickType, value);
		// TODO: decide what to do with received data
	}

	public void tickString(int tickerId, int tickType, String value) {
		// received String tick
		String msg = EWrapperMsgGenerator.tickString(tickerId, tickType, value);
		// TODO: decide what to do with received data
	}

	public void tickSnapshotEnd(int tickerId) {
		String msg = EWrapperMsgGenerator.tickSnapshotEnd(tickerId);
		// TODO: decide what to do with received data
	}

	public void tickEFP(int tickerId, int tickType, double basisPoints,
			String formattedBasisPoints, double impliedFuture, int holdDays,
			String futureExpiry, double dividendImpact, double dividendsToExpiry) {
		// received EFP tick
		String msg = EWrapperMsgGenerator.tickEFP(tickerId, tickType,
				basisPoints, formattedBasisPoints, impliedFuture, holdDays,
				futureExpiry, dividendImpact, dividendsToExpiry);
		// TODO: decide what to do with received data
	}

	public void orderStatus(int orderId, String status, int filled,
			int remaining, double avgFillPrice, int permId, int parentId,
			double lastFillPrice, int clientId, String whyHeld) {
		// received order status
		String msg = EWrapperMsgGenerator.orderStatus(orderId, status, filled,
				remaining, avgFillPrice, permId, parentId, lastFillPrice,
				clientId, whyHeld);
		// TODO: decide what to do with received order status data

		// TODO: make sure id for next order is at least orderId+1
		// m_orderDlg.setIdAtLeast( orderId + 1);
	}

	public void openOrder(int orderId, Contract contract, Order order,
			OrderState orderState) {
		// received open order
		m_lastMessage = EWrapperMsgGenerator.openOrder(orderId, contract,
				order, orderState);
		if (m_tickerToFilter.equals("")) {
			openOrders.add(new OrderFull(orderId, order, contract, orderState));
			if (orderState.m_status.equals("Filled")) {
				filledOrders.add(new OrderFull(orderId, order, contract,
						orderState));
			}
		} else {
			if (contract.m_localSymbol.equals(m_tickerToFilter)) {
				openOrders.add(new OrderFull(orderId, order, contract,
						orderState));
				if (orderState.m_status.equals("Filled")) {
					filledOrders.add(new OrderFull(orderId, order, contract,
							orderState));
				}
			}
		}
	}

	public void openOrderEnd() {
		// received open order end
		m_lastMessage = EWrapperMsgGenerator.openOrderEnd();
		m_requestingOrders = false;
		m_tickerToFilter = new String("");
	}

	public void contractDetails(int reqId, ContractDetails contractDetails) {
		String msg = EWrapperMsgGenerator.contractDetails(reqId,
				contractDetails);
		// TODO: decide what to do with received data
	}

	public void contractDetailsEnd(int reqId) {
		String msg = EWrapperMsgGenerator.contractDetailsEnd(reqId);
		// TODO: decide what to do with received data
	}

	public void scannerData(int reqId, int rank,
			ContractDetails contractDetails, String distance, String benchmark,
			String projection, String legsStr) {
		String msg = EWrapperMsgGenerator.scannerData(reqId, rank,
				contractDetails, distance, benchmark, projection, legsStr);
		// TODO: decide what to do with received data
	}

	public void scannerDataEnd(int reqId) {
		String msg = EWrapperMsgGenerator.scannerDataEnd(reqId);
		// TODO: decide what to do with received data
	}

	public void bondContractDetails(int reqId, ContractDetails contractDetails) {
		String msg = EWrapperMsgGenerator.bondContractDetails(reqId,
				contractDetails);
		// TODO: decide what to do with received data
	}

	public void execDetails(int reqId, Contract contract, Execution execution) {
		String msg = EWrapperMsgGenerator.execDetails(reqId, contract,
				execution);
		// TODO: decide what to do with received data
	}

	public void execDetailsEnd(int reqId) {
		String msg = EWrapperMsgGenerator.execDetailsEnd(reqId);
		// TODO: decide what to do with received data
	}

	public void updateMktDepth(int tickerId, int position, int operation,
			int side, double price, int size) {

		// TODO: decide whether this is relevant at all
	}

	public void updateMktDepthL2(int tickerId, int position,
			String marketMaker, int operation, int side, double price, int size) {
		// TODO: decide whether this is relevant at all
	}

	public void nextValidId(int orderId) {
		// received next valid order id
		String msg = EWrapperMsgGenerator.nextValidId(orderId);
		// TODO: decide how to handle / calculate
	}

	public void error(Exception ex) {
		// do not report exceptions if we initiated disconnect
		if (!m_disconnectInProgress) {
			String msg = EWrapperMsgGenerator.error(ex);
			// TODO: decide what to do
		}
	}

	public void error(String str) {
		m_lastMessage = EWrapperMsgGenerator.error(str);
	}

	public void error(int id, int errorCode, String errorMsg) {
		// received error
		String msg = EWrapperMsgGenerator.error(id, errorCode, errorMsg);
		// TODO: decide what to do with received data
		for (int ctr = 0; ctr < faErrorCodes.length; ctr++) {
			faError |= (errorCode == faErrorCodes[ctr]);
		}
	}

	public void connectionClosed() {
		String msg = EWrapperMsgGenerator.connectionClosed();
		// TODO: decide what to do with received data
	}

	public void updateAccountValue(String key, String value, String currency,
			String accountName) {
		// TODO: decide what to do with received data
	}

	public void updatePortfolio(Contract contract, int position,
			double marketPrice, double marketValue, double averageCost,
			double unrealizedPNL, double realizedPNL, String accountName) {
		// TODO: decide what to do with received data
	}

	public void updateAccountTime(String timeStamp) {
		// TODO: decide what to do with received data
	}

	public void accountDownloadEnd(String accountName) {
		// TODO: decide what to do with received data
		String msg = EWrapperMsgGenerator.accountDownloadEnd(accountName);
	}

	public void updateNewsBulletin(int msgId, int msgType, String message,
			String origExchange) {
		String msg = EWrapperMsgGenerator.updateNewsBulletin(msgId, msgType,
				message, origExchange);
		// TODO: decide what to do with received data
	}

	public void managedAccounts(String accountsList) {
		m_bIsFAAccount = true;
		m_FAAcctCodes = accountsList;
		String msg = EWrapperMsgGenerator.managedAccounts(accountsList);
		// TODO: decide what to do with received data
	}

	public void historicalData(int reqId, String date, double open,
			double high, double low, double close, int volume, int count,
			double WAP, boolean hasGaps) {
		String msg = EWrapperMsgGenerator.historicalData(reqId, date, open,
				high, low, close, volume, count, WAP, hasGaps);
		// TODO: decide what to do with received data
	}

	public void realtimeBar(int reqId, long time, double open, double high,
			double low, double close, long volume, double wap, int count) {
		String msg = EWrapperMsgGenerator.realtimeBar(reqId, time, open, high,
				low, close, volume, wap, count);
		// TODO: decide what to do with received data
	}

	public void scannerParameters(String xml) {
	}

	public void currentTime(long time) {
		String msg = EWrapperMsgGenerator.currentTime(time);
		// TODO: decide what to do with received data
	}

	public void fundamentalData(int reqId, String data) {
	}

	public void deltaNeutralValidation(int reqId, UnderComp underComp) {
	}

	public void receiveFA(int faDataType, String xml) {
	}

	public void marketDataType(int reqId, int marketDataType) {
		String msg = EWrapperMsgGenerator.marketDataType(reqId, marketDataType);
		// TODO: decide what to do with received data
	}

	private void copyExtendedOrderDetails(Order destOrder, Order srcOrder) {
		destOrder.m_tif = srcOrder.m_tif;
		destOrder.m_ocaGroup = srcOrder.m_ocaGroup;
		destOrder.m_ocaType = srcOrder.m_ocaType;
		destOrder.m_openClose = srcOrder.m_openClose;
		destOrder.m_origin = srcOrder.m_origin;
		destOrder.m_orderRef = srcOrder.m_orderRef;
		destOrder.m_transmit = srcOrder.m_transmit;
		destOrder.m_parentId = srcOrder.m_parentId;
		destOrder.m_blockOrder = srcOrder.m_blockOrder;
		destOrder.m_sweepToFill = srcOrder.m_sweepToFill;
		destOrder.m_displaySize = srcOrder.m_displaySize;
		destOrder.m_triggerMethod = srcOrder.m_triggerMethod;
		destOrder.m_outsideRth = srcOrder.m_outsideRth;
		destOrder.m_hidden = srcOrder.m_hidden;
		destOrder.m_discretionaryAmt = srcOrder.m_discretionaryAmt;
		destOrder.m_goodAfterTime = srcOrder.m_goodAfterTime;
		destOrder.m_shortSaleSlot = srcOrder.m_shortSaleSlot;
		destOrder.m_designatedLocation = srcOrder.m_designatedLocation;
		destOrder.m_exemptCode = srcOrder.m_exemptCode;
		destOrder.m_ocaType = srcOrder.m_ocaType;
		destOrder.m_rule80A = srcOrder.m_rule80A;
		destOrder.m_allOrNone = srcOrder.m_allOrNone;
		destOrder.m_minQty = srcOrder.m_minQty;
		destOrder.m_percentOffset = srcOrder.m_percentOffset;
		destOrder.m_eTradeOnly = srcOrder.m_eTradeOnly;
		destOrder.m_firmQuoteOnly = srcOrder.m_firmQuoteOnly;
		destOrder.m_nbboPriceCap = srcOrder.m_nbboPriceCap;
		destOrder.m_optOutSmartRouting = srcOrder.m_optOutSmartRouting;
		destOrder.m_auctionStrategy = srcOrder.m_auctionStrategy;
		destOrder.m_startingPrice = srcOrder.m_startingPrice;
		destOrder.m_stockRefPrice = srcOrder.m_stockRefPrice;
		destOrder.m_delta = srcOrder.m_delta;
		destOrder.m_stockRangeLower = srcOrder.m_stockRangeLower;
		destOrder.m_stockRangeUpper = srcOrder.m_stockRangeUpper;
		destOrder.m_overridePercentageConstraints = srcOrder.m_overridePercentageConstraints;
		destOrder.m_volatility = srcOrder.m_volatility;
		destOrder.m_volatilityType = srcOrder.m_volatilityType;
		destOrder.m_deltaNeutralOrderType = srcOrder.m_deltaNeutralOrderType;
		destOrder.m_deltaNeutralAuxPrice = srcOrder.m_deltaNeutralAuxPrice;
		destOrder.m_deltaNeutralConId = srcOrder.m_deltaNeutralConId;
		destOrder.m_deltaNeutralSettlingFirm = srcOrder.m_deltaNeutralSettlingFirm;
		destOrder.m_deltaNeutralClearingAccount = srcOrder.m_deltaNeutralClearingAccount;
		destOrder.m_deltaNeutralClearingIntent = srcOrder.m_deltaNeutralClearingIntent;
		destOrder.m_continuousUpdate = srcOrder.m_continuousUpdate;
		destOrder.m_referencePriceType = srcOrder.m_referencePriceType;
		destOrder.m_trailStopPrice = srcOrder.m_trailStopPrice;
		destOrder.m_scaleInitLevelSize = srcOrder.m_scaleInitLevelSize;
		destOrder.m_scaleSubsLevelSize = srcOrder.m_scaleSubsLevelSize;
		destOrder.m_scalePriceIncrement = srcOrder.m_scalePriceIncrement;
		destOrder.m_hedgeType = srcOrder.m_hedgeType;
		destOrder.m_hedgeParam = srcOrder.m_hedgeParam;
		destOrder.m_account = srcOrder.m_account;
		destOrder.m_settlingFirm = srcOrder.m_settlingFirm;
		destOrder.m_clearingAccount = srcOrder.m_clearingAccount;
		destOrder.m_clearingIntent = srcOrder.m_clearingIntent;
	}

	public String getLastMessage() {
		return m_lastMessage;
	}

	public List<OrderFull> getOpenOrders() {
		return openOrders;
	}

	public List<OrderFull> getFilledOrders() {
		return filledOrders;
	}
}
