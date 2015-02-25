package jforex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import jforex.TradeStateController.TradeState;
import jforex.utils.FXUtils;
import jforex.utils.FlexLogEntry;
import jforex.utils.Logger;

import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.Period;

public class Adaptive_MR_TF_Strategy extends AbstractMultiPositionStrategy implements IStrategy {

	public Instrument selectedInstrument = Instrument.EURUSD;

	public Period selectedPeriod = Period.FOUR_HOURS;
	public Period higherTimeFrame = Period.DAILY_SUNDAY_IN_MONDAY;
	
	public enum StrategyStates {
		NO_POSITION, // start state
		LONG_POSITION,
		SHORT_POSITION
	} 
	
	StrategyStates strategyState;
	long lastFirstExitSignal = 0; // its time. Important not to check further signals on day of its appearance
	
	public Adaptive_MR_TF_Strategy(Properties props) {
		super(props);
		strategyState = StrategyStates.NO_POSITION;
		INTERVAL_BETWEEN_TRADES = 24 * 3600 * 1000; // 24 hours in ms
		lastFirstExitSignal = 0;
	}

	@Override
	protected Instrument getSelectedInstrument() {
		return selectedInstrument;
	}

	@Override
	protected IOrder getPositionOrder() {
		// TODO Auto-generated method stub
		return waitingOrder;
	}

	@Override
	protected String getStrategyName() {
		return this.getClass().getName();
	}

	@Override
	protected String getReportFileName() {
		return getStrategyName() + "_";
	}

	@Override
	public void onStart(IContext context) throws JFException {
		onStartExec(context);		
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (!instrument.equals(selectedInstrument) || !period.equals(selectedPeriod) || !tradingAllowed(bidBar.getTime())) {
            return;
        }
        
        switch (tradeState.getState())
        {
            case SCANNING_TA_SIGNALS:
            	checkForNewSignal(instrument, period, askBar, bidBar);
                break;
	        case ENTRY_ORDER_WAITING:
	        	checkForCancelEntryOrder(instrument, period, bidBar);
                break;
	        case POSITION_OPENED: // includes all situations regardless of # of open orders before 1st exit signal
                if (exitSignalFirstFound(instrument, period, askBar, bidBar))
                {
                	lastFirstExitSignal = bidBar.getTime();
                    tradeState.stateTransition(TradeState.PHASE_1_EXIT_SIGNAL_FOUND, bidBar.getTime());
                    // move all SL levels to break-even !
                    toBreakEvenAll();
                }
                else 
            		checkForNextSignal(instrument, period, askBar, bidBar);
                break;
	        case POSITION_OPENED_AND_ENTRY_ORDER_WAITING: 
	        	checkForCancelEntryOrder(instrument, period, bidBar);
                break;
	        case POSITION_OPENED_MAX_REACHED: // can be reached only if 1st exit signal was never given !
                if (exitSignalFirstFound(instrument, period, askBar, bidBar))
                {
                	lastFirstExitSignal = bidBar.getTime();
                    tradeState.stateTransition(TradeState.PHASE_2_1ST_EXIT_SIGNAL_FOUND, bidBar.getTime());
                    // move all SL levels to break-even !
                    toBreakEvenAll();
                }
                break;
	        case PHASE_1_EXIT_SIGNAL_FOUND: // 1st exit signal occurred with only one position
                checkOppositeSignalOnePosition(instrument, period, askBar, bidBar);
                // alternative to test: SL level of #1 position moved to this trigger level 
                break;
	        case POSITION_N_OPENED: // two or more positions opened
                if (exitSignalFirstFound(instrument, period, askBar, bidBar))
                {
                	lastFirstExitSignal = bidBar.getTime();
                    tradeState.stateTransition(TradeState.PHASE_2_1ST_EXIT_SIGNAL_FOUND, bidBar.getTime());
                    // move all SL levels to break-even !
                    toBreakEvenAll();
                }	  
                else 
            		checkForNextSignal(instrument, period, askBar, bidBar);
	        	break;
	        case POSITION_N_AND_ENTRY_ORDER_WAITING:
	        	checkForCancelEntryOrder(instrument, period, bidBar);
	        	break;
	        case PHASE_2_1ST_EXIT_SIGNAL_FOUND:
                checkOppositeSignal(instrument, period, askBar, bidBar);
	        	break;
	        case PHASE_2_POSITION_N:
                if (exitSignalSecondFound(instrument, period, askBar, bidBar))
                {
                	lastFirstExitSignal = bidBar.getTime();
                	if (strategyState == StrategyStates.LONG_POSITION)
                		setStopLossPrice(bidBar.getLow());
                	else
                		setStopLossPrice(bidBar.getHigh());
                    tradeState.stateTransition(TradeState.EXIT_ORDER_TRAILING, bidBar.getTime());
                }	  
	        	break;
	        case EXIT_ORDER_TRAILING: // situation where 2nd exit signal occurred !
	        	// stop loss / exit levels are set only here, on their own 4h signals
                checkOppositeSignal(instrument, period, askBar, bidBar);
                break;
            default: 
            	return;
        }
	}
	
	private void checkOppositeSignalOnePosition(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		// simply any candle trigger of opposite direction
		List<FlexLogEntry> logLine = new ArrayList<FlexLogEntry>();
        eventsSource.collectAllStats(instrument, period, bidBar, logLine);
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}        
		
		if (strategyState == StrategyStates.LONG_POSITION) {
			if (eventsSource.any4hBearishTrigger(instrument, period, bidBar, logLine)) {
				setStopLossPrice(bidBar.getLow());				
			}			
		} else {
			if (eventsSource.any4hBullishTrigger(instrument, period, bidBar, logLine)) {
				setStopLossPrice(bidBar.getHigh());		
			}			
		}
	}
	
	private void checkOppositeSignal(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		// simply any candle trigger of opposite direction
		List<FlexLogEntry> logLine = new ArrayList<FlexLogEntry>();
        eventsSource.collectAllStats(instrument, period, bidBar, logLine);
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}        
		
		if (strategyState == StrategyStates.LONG_POSITION) {
			if (eventsSource.any4hBearishTrigger(instrument, period, bidBar, logLine)) {
				// set SL for 1/2 of #1 position
				if (tradeState.getState() == TradeState.PHASE_2_1ST_EXIT_SIGNAL_FOUND) {
					setFirstOrderSL(bidBar.getLow());
				}
				else if (tradeState.getState() == TradeState.EXIT_ORDER_TRAILING)
					setStopLossPrice(bidBar.getLow());				
			}			
		} else {
			if (eventsSource.any4hBullishTrigger(instrument, period, bidBar, logLine)) {
				// set SL for 1/2 of #1 position
				if (tradeState.getState() == TradeState.PHASE_2_1ST_EXIT_SIGNAL_FOUND) {
					setFirstOrderSL(bidBar.getHigh());
				}
				else if (tradeState.getState() == TradeState.EXIT_ORDER_TRAILING)
					setStopLossPrice(bidBar.getHigh());		
			}			
		}
	}

	private void checkForNextSignal(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		//if (bidBar.getTime() < timeOfLastTrade + INTERVAL_BETWEEN_TRADES)
		if (!FXUtils.tradingDistanceOK(bidBar.getTime(), timeOfLastTrade + INTERVAL_BETWEEN_TRADES))
			return;
		
		List<FlexLogEntry> logLine = new ArrayList<FlexLogEntry>();
        eventsSource.collectAllStats(instrument, period, bidBar, logLine);
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}        
		
		if (strategyState == StrategyStates.LONG_POSITION) {
				if (eventsSource.strong4hBullishNextEntrySignal(instrument, period, bidBar, logLine)) {
					double stopLossValue = mailValuesMap.get("bullishPivotLevel4h").getDoubleValue();
				    placeBuyOrder(instrument, period, askBar, bidBar, stopLossValue);
				    // Careful, placeOrder will trigger onMessage call !
				    log.printAction(Logger.logTags.ORDER.toString(), 
							waitingOrder.getLabel(), 
							FXUtils.getFormatedBarTime(bidBar), 
							Logger.logTags.ENTRY_FOUND.toString() + " BUY STOP", 
							waitingOrder.getOpenPrice(), 
							(protectiveStop - waitingOrder.getOpenPrice()) * Math.pow(10, instrument.getPipScale()), 0, 0);
				} 
		} else if (eventsSource.strong4hBearishNextEntrySignal(instrument, period, bidBar, logLine)) {
					double stopLossValue = mailValuesMap.get("bearishPivotLevel4h").getDoubleValue();
                    placeSellOrder(instrument, period, askBar, bidBar, stopLossValue);
                    // Careful, placeOrder will trigger onMessage call !
                    log.printAction(Logger.logTags.ORDER.toString(), 
                    		waitingOrder.getLabel(), 
                			FXUtils.getFormatedBarTime(bidBar), 
                			Logger.logTags.ENTRY_FOUND.toString() + " SELL STOP", 
                			waitingOrder.getOpenPrice(), 
                			(waitingOrder.getOpenPrice() - protectiveStop) * Math.pow(10, instrument.getPipScale()), 0, 0);
					
		}
	}

	private boolean exitSignalFirstFound(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		// next checks can be done only once NEW daily signal is formed, + at least 1 full day AFTER last entry
		if (noExitSignalDueToTimeChecks(bidBar))
			return false;
		
		List<FlexLogEntry> logLine = new ArrayList<FlexLogEntry>();
        eventsSource.collectAllStats(instrument, period, bidBar, logLine);
		if (strategyState == StrategyStates.LONG_POSITION) {
			return  eventsSource.bullishExitSignalFirst(instrument, period, bidBar, logLine);
			//return eventsSource.strong4hBearishFirstEntrySignal(instrument, period, bidBar, logLine);
		} else {
			return eventsSource.bearishExitSignalFirst(instrument, period, bidBar, logLine);
			//return eventsSource.strong4hBullishFirstEntrySignal(instrument, period, bidBar, logLine);
		}
	}

	private boolean exitSignalSecondFound(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		// next checks can be done only once NEW daily signal is formed
		if (noExitSignalDueToTimeChecks(bidBar))
			return false;
		
		List<FlexLogEntry> logLine = new ArrayList<FlexLogEntry>();
        eventsSource.collectAllStats(instrument, period, bidBar, logLine);
		if (strategyState == StrategyStates.LONG_POSITION) {
			if (eventsSource.bullishExitSignalSecond(instrument, period, bidBar, logLine))
				return true;
			return eventsSource.bullishExitSignalFirst(instrument, period, bidBar, logLine);
			//return eventsSource.strong4hBearishFirstEntrySignal(instrument, period, bidBar, logLine);
		} else {
			if (eventsSource.bearishExitSignalSecond(instrument, period, bidBar, logLine))
				return true;
			return eventsSource.bearishExitSignalFirst(instrument, period, bidBar, logLine);
			//return eventsSource.strong4hBullishFirstEntrySignal(instrument, period, bidBar, logLine);
		}
	}
	
	protected boolean noExitSignalDueToTimeChecks(IBar bidBar) {
		DateTime
			currentBarTime = new DateTime(bidBar.getTime()),
			currentDay = new DateTime(currentBarTime.getYear(), currentBarTime.getMonthOfYear(), currentBarTime.getDayOfMonth(), 0, 0, 0, 0),
			lastSignalTime = new DateTime(lastFirstExitSignal),
			lastSignalDay = new DateTime(lastSignalTime .getYear(), lastSignalTime .getMonthOfYear(), lastSignalTime .getDayOfMonth(), 0, 0, 0, 0);
		if (currentDay.equals(lastSignalDay))
			return true;
		
		// also doesn't make sense to check for 1d bar in which LAST (??) ENTRY signal appeared, as well as the next ! 
		DateTime
			firstEntrySignalTime = new DateTime(orders.get(orders.size() - 1).getCreationTime(), DateTimeZone.forID("UTC")),
			firstEntrySignalDay = new DateTime(firstEntrySignalTime.getYear(), firstEntrySignalTime.getMonthOfYear(), firstEntrySignalTime.getDayOfMonth(), 0, 0, 0, 0, DateTimeZone.forID("UTC")),
			lastDay = FXUtils.getLastTradingDay(bidBar.getTime());
		if (lastDay.getMillis() - firstEntrySignalDay.getMillis() <= 24 * 60 * 60 * 1000)
			return true;
		
		return false;
	}

	protected boolean noCancelTimeCheck(IBar bidBar) {
		if (waitingOrder == null)
			return true;
		
		// also doesn't make sense to check for 1d bar in which LAST (??) ENTRY signal appeared, as well as the next ! 
		DateTime
			waitingEntrySignalTime = new DateTime(waitingOrder.getCreationTime(), DateTimeZone.forID("UTC")),
			waitingEntrySignalDay = new DateTime(waitingEntrySignalTime.getYear(), waitingEntrySignalTime.getMonthOfYear(), waitingEntrySignalTime.getDayOfMonth(), 0, 0, 0, 0, DateTimeZone.forID("UTC")),
			lastDay = FXUtils.getLastTradingDay(bidBar.getTime());
		if (lastDay.getMillis() - waitingEntrySignalDay.getMillis() <= 24 * 60 * 60 * 1000)
			return true;
		
		return false;
	}

	private void checkForCancelEntryOrder(Instrument instrument, Period period,	IBar bidBar) throws JFException {
		if (waitingOrder == null || noCancelTimeCheck(bidBar))
			return;
		
		List<FlexLogEntry> logLine = new ArrayList<FlexLogEntry>();
        eventsSource.collectAllStats(instrument, period, bidBar, logLine);
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}
		
		if (waitingOrder.isLong()) {
			if (eventsSource.bullishEntryNoGoZone(instrument, period, bidBar, logLine) || eventsSource.strong4hBearishFirstEntrySignal(instrument, period, bidBar, logLine)) {
				waitingOrder.close();
			    // Careful, waitingOrder.close will trigger onMessage call !
			    log.printAction(Logger.logTags.ORDER.toString(), 
						waitingOrder.getLabel(), 
						FXUtils.getFormatedBarTime(bidBar), 
						Logger.logTags.ENTRY_CANCELED.toString(), 
						waitingOrder.getOpenPrice(), 
						(waitingOrder.getOpenPrice() - protectiveStop) * Math.pow(10, instrument.getPipScale()), 0, 0);				
			} else if (eventsSource.strong4hBullishNextEntrySignal(instrument, period, bidBar, logLine)) {
				// Better / newer entry signal found !
				waitingOrder.close();
			    // Careful, waitingOrder.close will trigger onMessage call !
			    log.printAction(Logger.logTags.ORDER.toString(), 
						waitingOrder.getLabel(), 
						FXUtils.getFormatedBarTime(bidBar), 
						Logger.logTags.ENTRY_CANCELED.toString(), 
						waitingOrder.getOpenPrice(), 
						(waitingOrder.getOpenPrice() - protectiveStop) * Math.pow(10, instrument.getPipScale()), 0, 0);
			    
			    // set new long order according to new signal ! 
				double stopLossValue = mailValuesMap.get("bullishPivotLevel4h").getDoubleValue();
			    placeBuyOrder(instrument, period, null, bidBar, stopLossValue);
			    // Careful, placeOrder will trigger onMessage call !
			    log.printAction(Logger.logTags.ORDER.toString(), 
						waitingOrder.getLabel(), 
						FXUtils.getFormatedBarTime(bidBar), 
						Logger.logTags.ENTRY_FOUND.toString() + " BUY STOP", 
						waitingOrder.getOpenPrice(), 
						(protectiveStop - waitingOrder.getOpenPrice()) * Math.pow(10, instrument.getPipScale()), 0, 0);				
			}
		} else {
			if (eventsSource.bearishEntryNoGoZone(instrument, period, bidBar, logLine) || eventsSource.strong4hBullishFirstEntrySignal(instrument, period, bidBar, logLine)) {
				waitingOrder.close();
			    // Careful, waitingOrder.close will trigger onMessage call !
			    log.printAction(Logger.logTags.ORDER.toString(), 
						waitingOrder.getLabel(), 
						FXUtils.getFormatedBarTime(bidBar), 
						Logger.logTags.ENTRY_CANCELED.toString(), 
						waitingOrder.getOpenPrice(), 
						(protectiveStop - waitingOrder.getOpenPrice()) * Math.pow(10, instrument.getPipScale()), 0, 0);				
			} else if (eventsSource.strong4hBearishNextEntrySignal(instrument, period, bidBar, logLine)) {
				// Better / newer entry signal found !
				waitingOrder.close();
			    // Careful, waitingOrder.close will trigger onMessage call !
			    log.printAction(Logger.logTags.ORDER.toString(), 
						waitingOrder.getLabel(), 
						FXUtils.getFormatedBarTime(bidBar), 
						Logger.logTags.ENTRY_CANCELED.toString(), 
						waitingOrder.getOpenPrice(), 
						(protectiveStop - waitingOrder.getOpenPrice()) * Math.pow(10, instrument.getPipScale()), 0, 0);
			    
			    // set new short order according to new signal ! 
				double stopLossValue = mailValuesMap.get("bearishPivotLevel4h").getDoubleValue();
                placeSellOrder(instrument, period, null, bidBar, stopLossValue);
                // Careful, placeOrder will trigger onMessage call !
                log.printAction(Logger.logTags.ORDER.toString(), 
                		waitingOrder.getLabel(), 
            			FXUtils.getFormatedBarTime(bidBar), 
            			Logger.logTags.ENTRY_FOUND.toString() + " SELL STOP", 
            			waitingOrder.getOpenPrice(), 
            			(waitingOrder.getOpenPrice() - protectiveStop) * Math.pow(10, instrument.getPipScale()), 0, 0);
			}			
		}
		
	}

	private void checkForNewSignal(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (strategyState != StrategyStates.NO_POSITION)
			return;
		
		List<FlexLogEntry> logLine = new ArrayList<FlexLogEntry>();
        eventsSource.collectAllStats(instrument, period, bidBar, logLine);
		Map<String, String> mailStringsMap = new HashMap<String, String>();
		Map<String, FlexLogEntry> mailValuesMap = new HashMap<String, FlexLogEntry>();
		for (FlexLogEntry e : logLine) {
			mailStringsMap.put(e.getLabel(), e.getFormattedValue());
			mailValuesMap.put(e.getLabel(), e);
		}        	

		// OK to check both long and short
		if (eventsSource.strong4hBullishFirstEntrySignal(instrument, period, bidBar, logLine)) {
			double stopLossValue = mailValuesMap.get("bullishPivotLevel4h").getDoubleValue();
		    placeBuyOrder(instrument, period, askBar, bidBar, stopLossValue);
		    // Careful, placeOrder will trigger onMessage call !
		    log.printAction(Logger.logTags.ORDER.toString(), 
					waitingOrder.getLabel(), 
					FXUtils.getFormatedBarTime(bidBar), 
					Logger.logTags.ENTRY_FOUND.toString() + " BUY STOP", 
					waitingOrder.getOpenPrice(), 
					(protectiveStop - waitingOrder.getOpenPrice()) * Math.pow(10, instrument.getPipScale()), 0, 0);
		} else if (eventsSource.strong4hBearishFirstEntrySignal(instrument, period, bidBar, logLine)) {
			double stopLossValue = mailValuesMap.get("bearishPivotLevel4h").getDoubleValue();
            placeSellOrder(instrument, period, askBar, bidBar, stopLossValue);
            // Careful, placeOrder will trigger onMessage call !
            log.printAction(Logger.logTags.ORDER.toString(), 
            		waitingOrder.getLabel(), 
        			FXUtils.getFormatedBarTime(bidBar), 
        			Logger.logTags.ENTRY_FOUND.toString() + " SELL STOP", 
        			waitingOrder.getOpenPrice(), 
        			(waitingOrder.getOpenPrice() - protectiveStop) * Math.pow(10, instrument.getPipScale()), 0, 0);
			
		}		
	}

	@Override
	public void onMessage(IMessage message) throws JFException {
		       switch (tradeState.getState())
		        {
		            case SCANNING_TA_SIGNALS:
		                    if (message.getType().equals(IMessage.Type.ORDER_SUBMIT_OK))
		                    {
		                    	if (message.getOrder().isLong())
		                    		waitingOrder.setStopLossPrice(protectiveStop - safeZone / Math.pow(10, message.getOrder().getInstrument().getPipScale()));
		                    	else
		                    		waitingOrder.setStopLossPrice(protectiveStop + safeZone / Math.pow(10, message.getOrder().getInstrument().getPipScale()));
			                    tradeState.stateTransition(TradeState.ENTRY_ORDER_WAITING, message.getCreationTime());
		                    }
		                break; 
		            case ENTRY_ORDER_WAITING:
		                    if (message.getType().equals(IMessage.Type.ORDER_FILL_OK))
		                    {         
		                    	processOrderFill(message);
		                    	if (message.getOrder().isLong())
		                    		strategyState = StrategyStates.LONG_POSITION;
		                    	else
		                    		strategyState = StrategyStates.SHORT_POSITION;
		                     }
		                    else if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK))
		                    {
		                    	// order canceled
		                    	tradeState.stateTransition(TradeState.SCANNING_TA_SIGNALS, message.getCreationTime());
		                    }
		                break;
		            case POSITION_OPENED:	                    
	                    if (message.getType().equals(IMessage.Type.ORDER_SUBMIT_OK))
	                    {
	                    	if (message.getOrder().isLong())
	                    		waitingOrder.setStopLossPrice(protectiveStop - safeZone / 10000);
	                    	else
	                    		waitingOrder.setStopLossPrice(protectiveStop + safeZone / 10000);
		                    tradeState.stateTransition(TradeState.POSITION_OPENED_AND_ENTRY_ORDER_WAITING, message.getCreationTime());
	                    }
	            		// message will be send for all the open orders !
	                    else if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK))
	                    {
	                        processPositionsCloseBare(message, false);
	                        if (orders.size() == 0)
	                        	strategyState = StrategyStates.NO_POSITION;
	                    }
	                    else if (message.getType().equals(IMessage.Type.ORDER_CHANGED_OK))
	                    {
	                    	String msgSuffix = new String(" (" + lastStopType.toString() + ")");
	                        log.printAction(Logger.logTags.ORDER.toString(), 
	                    			message.getOrder().getLabel(), 
	                    			FXUtils.getFormatedTimeGMT(message.getCreationTime()), 
	                    			Logger.logTags.STOP_UPDATED.toString() + msgSuffix, 
	                    			message.getOrder().getStopLossPrice(), 
	                    			(protectiveStop - message.getOrder().getOpenPrice()) * 10000, 
	                    			message.getOrder().getProfitLossInPips(), 0);
	                    }
	                break;
		            case POSITION_OPENED_MAX_REACHED:	    
		            		// this happens only when SL is hit for some (or all ?!) of the open orders...
		            		if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK))
		                    {
		                        processPositionsCloseBare(message, false);
		                        if (orders.size() == 0)
		                        	strategyState = StrategyStates.NO_POSITION;
		                        else // if not all the orders are closed that means there are free slots now !
				                    tradeState.stateTransition(TradeState.POSITION_N_OPENED, message.getCreationTime());
		                    }
		                    else if (message.getType().equals(IMessage.Type.ORDER_CHANGED_OK))
		                    {
		                    	String msgSuffix = new String(" (" + lastStopType.toString() + ")");
		                        log.printAction(Logger.logTags.ORDER.toString(), 
		                    			message.getOrder().getLabel(), 
		                    			FXUtils.getFormatedTimeGMT(message.getCreationTime()), 
		                    			Logger.logTags.STOP_UPDATED.toString() + msgSuffix, 
		                    			message.getOrder().getStopLossPrice(), 
		                    			(protectiveStop - message.getOrder().getOpenPrice()) * 10000, 
		                    			message.getOrder().getProfitLossInPips(), 0);
		                    }
		                break;
		            case POSITION_OPENED_AND_ENTRY_ORDER_WAITING:	                    
		                if (message.getType().equals(IMessage.Type.ORDER_FILL_OK))
		                {         
		                	processOrderFill(message, true);
		                 }
		        		// message will be send for all the open orders !
		                else if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK))
		                {
		                	if (!message.getOrder().equals(waitingOrder)) {
		                        processPositionsCloseBare(message, false);
		                        if (orders.size() == 0)
		                        	strategyState = StrategyStates.NO_POSITION;		                
		                        
		                        if (!waitingOrder.getState().equals(IOrder.State.CREATED)
		                        	&& !waitingOrder.getState().equals(IOrder.State.CANCELED))
		                        	waitingOrder.close();                    		
		                	}
		                	else
		                    	// order canceled
		                    	tradeState.stateTransition(TradeState.POSITION_OPENED, message.getCreationTime());
		                }
		                else if (message.getType().equals(IMessage.Type.ORDER_CHANGED_OK))
		                {
		                	String msgSuffix = new String(" (" + lastStopType.toString() + ")");
		                    log.printAction(Logger.logTags.ORDER.toString(), 
		                			message.getOrder().getLabel(), 
		                			FXUtils.getFormatedTimeGMT(message.getCreationTime()), 
		                			Logger.logTags.STOP_UPDATED.toString() + msgSuffix, 
		                			message.getOrder().getStopLossPrice(), 
		                			(protectiveStop - message.getOrder().getOpenPrice()) * 10000, 
		                			message.getOrder().getProfitLossInPips(), 0);
		                }
		            break;
		            case PHASE_1_EXIT_SIGNAL_FOUND: // only one position and exit signal appeared, SL level is getting changed
		                if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK))
		                {
		                        processPositionsCloseBare(message, false);
		                        if (orders.size() == 0)
		                        	strategyState = StrategyStates.NO_POSITION;		                
		                }
		                else if (message.getType().equals(IMessage.Type.ORDER_CHANGED_OK))
		                {
		                	String msgSuffix = new String(" (" + lastStopType.toString() + ")");
		                    log.printAction(Logger.logTags.ORDER.toString(), 
		                			message.getOrder().getLabel(), 
		                			FXUtils.getFormatedTimeGMT(message.getCreationTime()), 
		                			Logger.logTags.STOP_UPDATED.toString() + msgSuffix, 
		                			message.getOrder().getStopLossPrice(), 
		                			(protectiveStop - message.getOrder().getOpenPrice()) * 10000, 
		                			message.getOrder().getProfitLossInPips(), 0);
		                }		            	
		            break;
		            case POSITION_N_OPENED:
	                    if (message.getType().equals(IMessage.Type.ORDER_SUBMIT_OK))
	                    {
	                    	if (message.getOrder().isLong())
	                    		waitingOrder.setStopLossPrice(protectiveStop - safeZone / 10000);
	                    	else
	                    		waitingOrder.setStopLossPrice(protectiveStop + safeZone / 10000);
		                    tradeState.stateTransition(TradeState.POSITION_N_AND_ENTRY_ORDER_WAITING, message.getCreationTime());
	                    }
	            		// message will be send for all the open orders !
	                    else if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK))
	                    {
	                        processPositionsCloseBare(message, false);
	                        if (orders.size() == 0)
	                        	strategyState = StrategyStates.NO_POSITION;
	                    }		            	
		            break;
		            case POSITION_N_AND_ENTRY_ORDER_WAITING:
		                if (message.getType().equals(IMessage.Type.ORDER_FILL_OK))
		                {         
		                	processOrderFill(message, true);
		                 }
		        		// message will be send for all the open orders !
		                else if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK))
		                {
		                	if (!message.getOrder().equals(waitingOrder)) {
		                        processPositionsCloseBare(message, false);
		                        if (!waitingOrder.getState().equals(IOrder.State.CREATED)
		                        	&& !waitingOrder.getState().equals(IOrder.State.CANCELED))
		                        	waitingOrder.close();                    		
		                        if (orders.size() == 0)
		                        	strategyState = StrategyStates.NO_POSITION;
		                	}
		                	else
		                    	// order canceled
		                    	tradeState.stateTransition(TradeState.POSITION_N_OPENED, message.getCreationTime());
		                }
		            break;
		            case PHASE_2_1ST_EXIT_SIGNAL_FOUND:
	                    if (message.getType().equals(IMessage.Type.ORDER_CHANGED_OK))
	                    {
	                        log.printAction(Logger.logTags.ORDER.toString(), 
	                    			message.getOrder().getLabel(), 
	                    			FXUtils.getFormatedTimeGMT(message.getCreationTime()), 
	                    			Logger.logTags.STOP_UPDATED.toString(), 
	                    			message.getOrder().getStopLossPrice(), 
	                    			(protectiveStop - message.getOrder().getOpenPrice()) * 10000, 
	                    			message.getOrder().getProfitLossInPips(), 0);
	                    }
	                    else if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK))
	                    {
	                    	processPositionsCloseBare(message, false);
	                    	if (orders.size() == 0)
	                    		strategyState = StrategyStates.NO_POSITION;		
	                    	else 
	                    		tradeState.stateTransition(TradeState.PHASE_2_POSITION_N, message.getCreationTime());		                    		
	                    }
		            break;
		            case PHASE_2_POSITION_N:
	                    if (message.getType().equals(IMessage.Type.ORDER_CHANGED_OK))
	                    {
	                        log.printAction(Logger.logTags.ORDER.toString(), 
	                    			message.getOrder().getLabel(), 
	                    			FXUtils.getFormatedTimeGMT(message.getCreationTime()), 
	                    			Logger.logTags.STOP_UPDATED.toString(), 
	                    			message.getOrder().getStopLossPrice(), 
	                    			(protectiveStop - message.getOrder().getOpenPrice()) * 10000, 
	                    			message.getOrder().getProfitLossInPips(), 0);
	                    }
	                    if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK))
	                    {
	                    	processPositionsCloseBare(message, false);
	                    	if (orders.size() == 0)  
	                    		strategyState = StrategyStates.NO_POSITION;		
	                    }
		            break;
		            case EXIT_ORDER_TRAILING:
	            		// message will be send for all the open orders !
	                    if (message.getType().equals(IMessage.Type.ORDER_CHANGED_OK))
	                    {
	                        log.printAction(Logger.logTags.ORDER.toString(), 
	                    			message.getOrder().getLabel(), 
	                    			FXUtils.getFormatedTimeGMT(message.getCreationTime()), 
	                    			Logger.logTags.TRAILING_STOP_UPDATED.toString(), 
	                    			message.getOrder().getStopLossPrice(), 
	                    			(protectiveStop - message.getOrder().getOpenPrice()) * 10000, 
	                    			message.getOrder().getProfitLossInPips(), 0);
	                    }
	                    else if (message.getType().equals(IMessage.Type.ORDER_CLOSE_OK))
	                    {
	                    	processPositionsCloseBare(message, true);
	                    	if (orders.size() == 0)  
	                    		strategyState = StrategyStates.NO_POSITION;		
	                    }
		            break;
		            case POSITION_CLOSED:
		                break;
		            case ENTRY_ORDER_CANCELLED:
		                break;
		            default: return;
		        }
	}

	@Override
	public void onAccount(IAccount account) throws JFException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStop() throws JFException {
		onStopExec();		
	}

}
