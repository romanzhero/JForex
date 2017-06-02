package jforex;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import jforex.AbstractTradingStrategy.LastStopType;
import jforex.TradeStateController.TradeState;
import jforex.utils.FXUtils;
import jforex.utils.log.Logger;

import com.dukascopy.api.IBar;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.IEngine.OrderCommand;

public abstract class AbstractMultiPositionStrategy extends
		AbstractTradingStrategy {

	protected List<IOrder> orders = new ArrayList<IOrder>();
	protected int MAX_POSITIONS = 4;
	protected long INTERVAL_BETWEEN_TRADES = 1 * 3600 * 1000; // 2 hours in ms
	protected long timeOfLastTrade = 0;

	/**
	 * the next order, submitted but waiting to be filled
	 */
	protected IOrder waitingOrder;

	public AbstractMultiPositionStrategy(Properties props) {
		super(props);
		MAX_POSITIONS = Integer.parseInt(props
				.getProperty("MAX_POSITIONS", "4"));
	}

	protected void placeBuyOrder(Instrument instrument, Period period,
			IBar askBar, IBar bidBar) throws JFException {
		double entryPrice = bidBar.getHigh() + safeZone
				/ Math.pow(10, instrument.getPipScale());
		// adjust stop loss to be below 35 pips
		if (orders.size() == 0
				&& (entryPrice - protectiveStop)
						* Math.pow(10, instrument.getPipScale()) > MAX_RISK) {
			protectiveStop = entryPrice - MAX_RISK
					/ Math.pow(10, instrument.getPipScale());
		}

		double tradeCandleLeverage = 1;
		if (FLEXIBLE_LEVERAGE) {
			tradeCandleLeverage = MAX_RISK / (entryPrice - protectiveStop)
					/ Math.pow(10, instrument.getPipScale());
		}

		FXUtils.setProfitLossHelper(account.getCurrency(), history);
		BigDecimal dollarAmount = FXUtils.convertByTick(
				BigDecimal.valueOf(account.getEquity()), account.getCurrency(),
				instrument.getSecondaryCurrency(), OfferSide.BID);
		// in millions
		double tradeAmount = dollarAmount.doubleValue() * tradeCandleLeverage
				* DEFAULT_LEVERAGE / 1000000.0;

		waitingOrder = engine.submitOrder(getLabel(instrument), instrument,
				OrderCommand.BUYSTOP, tradeAmount, entryPrice);
	}

	protected void placeBuyOrder(Instrument instrument, Period period,
			IBar askBar, IBar bidBar, double stopLossLevel) throws JFException {
		double entryPrice = bidBar.getHigh() + safeZone
				/ Math.pow(10, instrument.getPipScale());
		protectiveStop = stopLossLevel;
		// adjust stop loss to be below 35 pips
		if (orders.size() == 0
				&& (entryPrice - protectiveStop)
						* Math.pow(10, instrument.getPipScale()) > MAX_RISK) {
			protectiveStop = entryPrice - MAX_RISK
					/ Math.pow(10, instrument.getPipScale());
		}

		double tradeCandleLeverage = 1;
		if (FLEXIBLE_LEVERAGE) {
			tradeCandleLeverage = MAX_RISK / (entryPrice - protectiveStop)
					/ Math.pow(10, instrument.getPipScale());
		}

		FXUtils.setProfitLossHelper(account.getCurrency(), history);
		BigDecimal dollarAmount = FXUtils.convertByTick(
				BigDecimal.valueOf(account.getEquity()), account.getCurrency(),
				instrument.getSecondaryCurrency(), OfferSide.BID);
		// in millions
		double tradeAmount = dollarAmount.doubleValue() * tradeCandleLeverage
				* DEFAULT_LEVERAGE / 1000000.0;

		waitingOrder = engine.submitOrder(getLabel(instrument), instrument,
				OrderCommand.BUYSTOP, tradeAmount, entryPrice);
	}

	protected void placeSellOrder(Instrument instrument, Period period,
			IBar askBar, IBar bidBar) throws JFException {
		double entryPrice = bidBar.getLow() - safeZone
				/ Math.pow(10, instrument.getPipScale());
		// adjust stop loss to be below 35 pips
		if (orders.size() == 0
				&& (protectiveStop - entryPrice)
						* Math.pow(10, instrument.getPipScale()) > MAX_RISK) {
			protectiveStop = entryPrice + MAX_RISK
					/ Math.pow(10, instrument.getPipScale());
		}

		double tradeCandleLeverage = 1;
		if (FLEXIBLE_LEVERAGE) {
			tradeCandleLeverage = MAX_RISK / (protectiveStop - entryPrice);
		}

		// in millions
		double tradeAmount = account.getEquity() * tradeCandleLeverage
				* DEFAULT_LEVERAGE / 1000000.0;

		waitingOrder = engine.submitOrder(getLabel(instrument), instrument,
				OrderCommand.SELLSTOP, tradeAmount, entryPrice);
	}

	protected void placeSellOrder(Instrument instrument, Period period,
			IBar askBar, IBar bidBar, double stopLoss) throws JFException {
		double entryPrice = bidBar.getLow() - safeZone
				/ Math.pow(10, instrument.getPipScale());
		protectiveStop = stopLoss;
		// adjust stop loss to be below 35 pips
		if (orders.size() == 0
				&& (protectiveStop - entryPrice)
						* Math.pow(10, instrument.getPipScale()) > MAX_RISK) {
			protectiveStop = entryPrice + MAX_RISK
					/ Math.pow(10, instrument.getPipScale());
		}

		double tradeCandleLeverage = 1;
		if (FLEXIBLE_LEVERAGE) {
			tradeCandleLeverage = MAX_RISK / (protectiveStop - entryPrice);
		}

		// in millions
		double tradeAmount = account.getEquity() * tradeCandleLeverage
				* DEFAULT_LEVERAGE / 1000000.0;

		waitingOrder = engine.submitOrder(getLabel(instrument), instrument,
				OrderCommand.SELLSTOP, tradeAmount, entryPrice);
	}

	protected void toBreakEvenAll() throws JFException {
		for (IOrder currOrder : orders) {
			double entryPrice = currOrder.getOpenPrice();
			currOrder.setStopLossPrice(entryPrice);
		}
	}

	protected void setStopLossPrice(double protectiveStop) throws JFException {
		for (IOrder currOrder : orders) {
			currOrder.setStopLossPrice(protectiveStop);
		}

	}

	protected void setFirstOrderSL(double stopLossLevel) throws JFException {
		IOrder firstOrder = orders.get(0);
		if (firstOrder != null) {
			// TODO: only on 1/2 position !
			double originalAmount = firstOrder.getOriginalAmount();
			firstOrder.setStopLossPrice(stopLossLevel);
		}
	}

	protected void processOrderFill(IMessage message) {
		timeOfLastTrade = message.getCreationTime();
		orders.add(waitingOrder);
		if (orders.size() < MAX_POSITIONS)
			tradeState.stateTransition(TradeState.POSITION_OPENED,
					message.getCreationTime());
		else
			tradeState.stateTransition(TradeState.POSITION_OPENED_MAX_REACHED,
					message.getCreationTime());
		log.printAction(
				Logger.logTags.ORDER.toString(),
				waitingOrder.getLabel(),
				FXUtils.getFormatedTimeGMT(message.getCreationTime()),
				Logger.logTags.ENTRY_FILLED.toString(),
				waitingOrder.getOpenPrice(),
				(protectiveStop - waitingOrder.getOpenPrice())
						* Math.pow(10, message.getOrder().getInstrument()
								.getPipScale()), 0, 0);
		// TODO: find a way to calculate realistic commisions, in the worst case
		// by replicating table from Dukascopy site
		// SEE ITesterClient.getCommissions !!
		dbLogRecordEntry(message, waitingOrder);
	}

	protected void processOrderFill(IMessage message, boolean phaseTwo) {
		timeOfLastTrade = message.getCreationTime();
		orders.add(waitingOrder);
		if (orders.size() < MAX_POSITIONS)
			tradeState.stateTransition(phaseTwo ? TradeState.POSITION_N_OPENED
					: TradeState.POSITION_OPENED, message.getCreationTime());
		else
			tradeState.stateTransition(TradeState.POSITION_OPENED_MAX_REACHED,
					message.getCreationTime());
		log.printAction(
				Logger.logTags.ORDER.toString(),
				waitingOrder.getLabel(),
				FXUtils.getFormatedTimeGMT(message.getCreationTime()),
				Logger.logTags.ENTRY_FILLED.toString(),
				waitingOrder.getOpenPrice(),
				(protectiveStop - waitingOrder.getOpenPrice())
						* Math.pow(10, message.getOrder().getInstrument()
								.getPipScale()), 0, 0);
		// TODO: find a way to calculate realistic commisions, in the worst case
		// by replicating table from Dukascopy site
		// SEE ITesterClient.getCommissions !!
		dbLogRecordEntry(message, waitingOrder);
	}

	protected void processOrderFill(IMessage message, TradeState nextState) {
		timeOfLastTrade = message.getCreationTime();
		orders.add(waitingOrder);
		if (orders.size() < MAX_POSITIONS)
			tradeState.stateTransition(nextState, message.getCreationTime());
		else
			tradeState.stateTransition(TradeState.POSITION_OPENED_MAX_REACHED,
					message.getCreationTime());
		log.printAction(
				Logger.logTags.ORDER.toString(),
				waitingOrder.getLabel(),
				FXUtils.getFormatedTimeGMT(message.getCreationTime()),
				Logger.logTags.ENTRY_FILLED.toString(),
				waitingOrder.getOpenPrice(),
				(protectiveStop - waitingOrder.getOpenPrice())
						* Math.pow(10, message.getOrder().getInstrument()
								.getPipScale()), 0, 0);
		// TODO: find a way to calculate realistic commisions, in the worst case
		// by replicating table from Dukascopy site
		// SEE ITesterClient.getCommissions !!
		dbLogRecordEntry(message, waitingOrder);
	}

	protected void processPositionsCloseBare(IMessage message, boolean trailing)
			throws JFException {
		log.printAction(
				Logger.logTags.ORDER.toString(),
				message.getOrder().getLabel(),
				FXUtils.getFormatedTimeGMT(message.getCreationTime()),
				trailing ? Logger.logTags.EXIT_TRAILING_STOP.toString()
						: Logger.logTags.EXIT_STOP_LOSS.toString(),
				message.getOrder().getClosePrice(),
				(protectiveStop - message.getOrder().getOpenPrice())
						* Math.pow(10, message.getOrder().getInstrument()
								.getPipScale()), message.getOrder()
						.getProfitLossInPips(), 0);
		// TODO: find a way to calculate realistic commissions, in the worst
		// case by replicating table from Dukascopy site

		orders.remove(message.getOrder());
		if (orders.size() == 0) {
			protectProfitSet = false;
			timeOfLastTrade = 0;
			tradeState.stateTransition(TradeState.SCANNING_TA_SIGNALS,
					message.getCreationTime());
			lastStopType = LastStopType.NONE;
			tradeStats.reset();
		}
	}

}
