package trading.strategies;

import com.dukascopy.api.Instrument;

import jforex.strategies.OrderFillEvent;
import jforex.strategies.OrderSLCloseEvent;
import jforex.utils.FXUtils;
import jforex.utils.Logger;
import trading.elements.AbstractTradingStrategy;
import trading.elements.IBrokerEngine;
import trading.elements.IBrokerEvent;
import trading.elements.ITAEvent;
import trading.elements.ITAEventSource;
import trading.elements.ITradeState;
import trading.elements.ITradingStrategy;
import trading.elements.Position;
import trading.elements.TimeFrames;
import trading.events.ichi.IchiCloudBreakout;
import trading.states.ichi.IchiStateNoPosition;
import trading.states.ichi.IchiStatePositionNo1;
import trading.states.ichi.IchiStatePositionNo2;
import trading.states.ichi.IchiStateWaiting;

public class IchiForexStrategy extends AbstractTradingStrategy implements
		ITradingStrategy {

	public enum IchiEvents {
		ENTRY, CANCEL_lONG, CANCEL_SHORT, REENTRY
	}

	public enum IchiValues {
		ATR, ATR_DOLLAR
	}

	public IchiForexStrategy(int pMaxPositions, IBrokerEngine brokerEngine,
			TimeFrames pBasicTimeFrame) {
		super(pMaxPositions, brokerEngine, pBasicTimeFrame);
		name = "IchiForexStrategy";
		shortName = "Ichi";
	}

	@Override
	public void processTAEvent(String ticker, TimeFrames timeframe, long time,
			ITAEventSource eventSource) {
		TickerTradeData currTickerData = tickersTradeData.get(ticker);
		if (currTickerData == null) {
			currTickerData = new TickerTradeData(new IchiStateNoPosition(false));
			tickersTradeData.put(ticker, currTickerData);
		}

		ITradeState currentState = currTickerData.getCurrentState(), previousState = currTickerData
				.getPreviousState();

		if (currentState.getClass().equals(IchiStateNoPosition.class)) {
			if (previousState == null
					|| previousState.getClass().equals(IchiStateWaiting.class)
					|| previousState.getClass().equals(
							IchiStatePositionNo1.class)) {
				ITAEvent ichiEntry = eventSource.get(ticker,
						IchiEvents.ENTRY.toString(), timeframe, time);
				if (ichiEntry == null)
					return;

				IchiCloudBreakout entryEvent = (IchiCloudBreakout) ichiEntry;

				currTickerData.changeState(new IchiStateWaiting(entryEvent
						.isLong(), entryEvent.getOpen(), entryEvent.getLow(),
						entryEvent.getHigh(), entryEvent.getClose()));
				currTickerData.setLastBarTime(time);
				currTickerData.setStopLoss(entryEvent.getStopLossPrice());

				double amount = calcPositionAmount(brokerEngine.getEquity(),
						entryEvent.getDolarAdjustedAtr());
				if (entryEvent.isLong()) {
					String orderID = createOrderID(ticker, true, timeframe,
							time);
					brokerEngine.submitBuyStpOrder(ticker, orderID, amount,
							entryEvent.getSTPPrice(),
							entryEvent.getStopLossPrice(), 0);
					log.printAction(Logger.logTags.ENTRY_FOUND.toString(),
							orderID, FXUtils.getFormatedTimeGMT(time),
							"BUY order submit", entryEvent.getStopLossPrice(),
							0.0, 0.0, 0.0);
					currTickerData.setWaitingOrderID(orderID);
				} else {
					String orderID = createOrderID(ticker, false, timeframe,
							time);
					brokerEngine.submitSellStpOrder(ticker, orderID, amount,
							entryEvent.getSTPPrice(),
							entryEvent.getStopLossPrice(), 0);
					log.printAction(Logger.logTags.ENTRY_FOUND.toString(),
							orderID, FXUtils.getFormatedTimeGMT(time),
							"SELL order submit", entryEvent.getStopLossPrice(),
							0.0, 0.0, 0.0);
					currTickerData.setWaitingOrderID(orderID);
				}
			} else {
				// TODO: now process all other paths to no position - after SL,
				// TP etc...
			}

			return;
		}

		currTickerData.setLastBarTime(time);

		if (currentState.getClass().equals(IchiStateWaiting.class)) {
			ITAEvent ichiCancel = null;
			if (currentState.isLong())
				ichiCancel = eventSource.get(ticker,
						IchiEvents.CANCEL_lONG.toString(), timeframe, time);
			else
				ichiCancel = eventSource.get(ticker,
						IchiEvents.CANCEL_SHORT.toString(), timeframe, time);
			if (ichiCancel == null)
				return;

			currTickerData.changeState(new IchiStateNoPosition(false));
			brokerEngine
					.cancelOrder(ticker, currTickerData.getWaitingOrderID());
			log.printAction(Logger.logTags.ENTRY_CANCELED.toString(),
					currTickerData.getWaitingOrderID(),
					FXUtils.getFormatedTimeGMT(time), "order cancelled", 0.0,
					0.0, 0.0, 0.0);
			currTickerData.reset();

			return;
		}

		if (currentState.getClass().equals(IchiStatePositionNo1.class)) {
			// TODO: dodaj u sva stanja gde treba
			currTickerData.incTradeBarNo();

			ITAEvent ichiEntry = eventSource.get(ticker,
					IchiEvents.ENTRY.toString(), timeframe, time);
			if (ichiEntry == null)
				return;

			if (currentState.isLong()) {
				if (ichiEntry.isLong())
					return;

				// opposite signal found !
				Position p = currTickerData.getPositions().get(0);
				brokerEngine.closeOrderMkt(ticker, p.getOrderID());
				brokerEngine.cancelOrder(ticker,
						currTickerData.getWaitingOrderID());

				currTickerData.reset();

				IchiCloudBreakout entryEvent = (IchiCloudBreakout) ichiEntry;
				currTickerData.changeState(new IchiStateWaiting(entryEvent
						.isLong(), entryEvent.getOpen(), entryEvent.getLow(),
						entryEvent.getHigh(), entryEvent.getClose()));
				currTickerData.setLastBarTime(time);

				double amount = calcPositionAmount(brokerEngine.getEquity(),
						entryEvent.getDolarAdjustedAtr());
				String orderID = createOrderID(ticker, false, timeframe, time);
				brokerEngine.submitSellStpOrder(ticker, orderID, amount,
						entryEvent.getSTPPrice(),
						entryEvent.getStopLossPrice(), 0);
				log.printAction(Logger.logTags.ENTRY_FOUND.toString(), orderID,
						FXUtils.getFormatedTimeGMT(time), "SELL order submit",
						entryEvent.getStopLossPrice(), 0.0, 0.0, 0.0);
				currTickerData.setWaitingOrderID(orderID);
			} else {
				if (!ichiEntry.isLong())
					return;

				// opposite signal found !
				Position p = currTickerData.getPositions().get(0);
				brokerEngine.closeOrderMkt(ticker, p.getOrderID());
				brokerEngine.cancelOrder(ticker,
						currTickerData.getWaitingOrderID());

				currTickerData.reset();

				IchiCloudBreakout entryEvent = (IchiCloudBreakout) ichiEntry;
				currTickerData.changeState(new IchiStateWaiting(entryEvent
						.isLong(), entryEvent.getOpen(), entryEvent.getLow(),
						entryEvent.getHigh(), entryEvent.getClose()));
				currTickerData.setLastBarTime(time);

				double amount = calcPositionAmount(brokerEngine.getEquity(),
						entryEvent.getDolarAdjustedAtr());
				String orderID = createOrderID(ticker, true, timeframe, time);
				brokerEngine.submitBuyStpOrder(ticker, orderID, amount,
						entryEvent.getSTPPrice(),
						entryEvent.getStopLossPrice(), 0);
				log.printAction(Logger.logTags.ENTRY_FOUND.toString(), orderID,
						FXUtils.getFormatedTimeGMT(time), "BUY order submit",
						entryEvent.getStopLossPrice(), 0.0, 0.0, 0.0);
				currTickerData.setWaitingOrderID(orderID);
			}
			return;
		}

	}

	private String createOrderID(String ticker, boolean isLong,
			TimeFrames timeframe, long time) {
		return new String(Instrument.fromString(ticker).name()
				+ (isLong ? "_BUY_" : "_SELL_") + timeframe.toString() + "_"
				+ getShortName() + "_" + FXUtils.getFileTimeStamp(time));
	}

	private double calcPositionAmount(double equity, double atr) {
		// must be 1% of equity...
		// TODO: check this formulae !!!
		return 0.01 * equity / atr;
	}

	@Override
	public void processBrokerEvent(String ticker, IBrokerEvent event,
			ITAEventSource eventSource) {
		TickerTradeData currTickerData = tickersTradeData.get(ticker);
		if (currTickerData == null)
			return;

		ITradeState currentState = currTickerData.getCurrentState();

		if (currentState.getClass().equals(IchiStateWaiting.class)) {
			if (event.getClass().equals(OrderFillEvent.class)) {
				OrderFillEvent fillEvent = (OrderFillEvent) event;
				currTickerData.getPositions().add(
						new Position(currTickerData.getWaitingOrderID(),
								currentState.isLong(), fillEvent.getAmount(),
								fillEvent.getEntryPrice()));
				currTickerData.changeState(new IchiStatePositionNo1(
						currentState.isLong()));
				log.printAction(Logger.logTags.ENTRY_FILLED.toString(),
						currTickerData.getWaitingOrderID(), FXUtils
								.getFormatedTimeGMT(event.getTime()), event
								.getType().toString(), 0.0, 0.0, 0.0, 0.0);

				// immediately set 2nd order
				submitNextOrder(ticker, eventSource, currTickerData,
						currentState, fillEvent, "_2nd");

				return;
			}
		}

		if (currentState.getClass().equals(IchiStatePositionNo1.class)) {
			if (event.getClass().equals(OrderSLCloseEvent.class)) {
				brokerEngine.cancelOrder(ticker,
						currTickerData.getWaitingOrderID());
				// TODO: must remove this order from engine !
				Position p = currTickerData.getPositions().get(0);
				currTickerData.changeState(new IchiStateNoPosition(false));
				log.printAction(Logger.logTags.EXIT_STOP_LOSS.toString(), p
						.getOrderID(), FXUtils.getFormatedTimeGMT(event
						.getTime()), event.getType().toString(), 0.0, 0.0, 0.0,
						0.0);
				currTickerData.reset();
				return;
			}
			if (event.getClass().equals(OrderFillEvent.class)) {
				// add 3rd position...
				OrderFillEvent fillEvent = (OrderFillEvent) event;
				currTickerData.getPositions().add(
						new Position(currTickerData.getWaitingOrderID(),
								currentState.isLong(), fillEvent.getAmount(),
								fillEvent.getEntryPrice()));
				currTickerData.changeState(new IchiStatePositionNo2(
						currentState.isLong()));
				log.printAction(Logger.logTags.ENTRY_FILLED.toString(),
						currTickerData.getWaitingOrderID(), FXUtils
								.getFormatedTimeGMT(event.getTime()), event
								.getType().toString(), 0.0, 0.0, 0.0, 0.0);

				// immediately set 3rd order
				submitNextOrder(ticker, eventSource, currTickerData,
						currentState, fillEvent, "_3rd");
				return;
			}
		}

		if (currentState.getClass().equals(IchiStatePositionNo2.class)) {
			if (event.getClass().equals(OrderSLCloseEvent.class)) {
				brokerEngine.cancelOrder(ticker,
						currTickerData.getWaitingOrderID());
				// TODO: must remove these orders from engine !
				Position p = currTickerData.getPositions().get(0);
				log.printAction(Logger.logTags.EXIT_STOP_LOSS.toString(), p
						.getOrderID(), FXUtils.getFormatedTimeGMT(event
						.getTime()), event.getType().toString(), 0.0, 0.0, 0.0,
						0.0);
				p = currTickerData.getPositions().get(1);
				log.printAction(Logger.logTags.EXIT_STOP_LOSS.toString(), p
						.getOrderID(), FXUtils.getFormatedTimeGMT(event
						.getTime()), event.getType().toString(), 0.0, 0.0, 0.0,
						0.0);

				currTickerData.changeState(new IchiStateNoPosition(false));
				currTickerData.reset();
				return;
			}
		}

	}

	protected void submitNextOrder(String ticker, ITAEventSource eventSource,
			TickerTradeData currTickerData, ITradeState currentState,
			OrderFillEvent fillEvent, String orderSuffix) {
		double atr = eventSource.getValue(ticker, IchiValues.ATR.toString(),
				basicTimeFrame, currTickerData.getLastBarTime()), atr_money = eventSource
				.getValue(ticker, IchiValues.ATR_DOLLAR.toString(),
						basicTimeFrame, currTickerData.getLastBarTime()), amount = calcPositionAmount(
				brokerEngine.getEquity(), atr_money);
		if (currentState.isLong()) {
			String orderID = createOrderID(ticker, true, basicTimeFrame,
					currTickerData.getLastBarTime()) + orderSuffix;
			brokerEngine.submitBuyStpOrder(ticker, orderID, amount,
					fillEvent.getEntryPrice() + 2 * atr,
					currTickerData.getStopLoss(), 0);
			currTickerData.setWaitingOrderID(orderID);
		} else {
			String orderID = createOrderID(ticker, false, basicTimeFrame,
					currTickerData.getLastBarTime()) + orderSuffix;
			brokerEngine.submitSellStpOrder(ticker, orderID, amount,
					fillEvent.getEntryPrice() - 2 * atr,
					currTickerData.getStopLoss(), 0);
			currTickerData.setWaitingOrderID(orderID);
		}
	}

}
