package jforex;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.joda.time.DateTime;

import jforex.TradeStateController.TradeState;
import jforex.filters.FilterFactory;
import jforex.filters.IFilter;
import jforex.techanalysis.Momentum;
import jforex.utils.FXUtils;
import jforex.utils.SortedProperties;
import jforex.utils.FXUtils.EntryFilters;
import com.dukascopy.api.Configurable;
import com.dukascopy.api.Filter;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public abstract class AbstractSinglePairSingleStrategy extends AbstractTradingStrategy {
	
	protected boolean breakEvenSet = false;
	protected Set<EntryFilters> entryFilters = new HashSet<EntryFilters>();
	protected Properties filtersConfiguration = new SortedProperties();
	
	@Configurable("Instrument")
	public Instrument selectedInstrument = Instrument.EURUSD;
	@Configurable("Period")
	public Period selectedPeriod = Period.THIRTY_MINS;
	public Period higherTimeFrame = Period.FOUR_HOURS;
	@Configurable("SMA filter")
	public Filter indicatorFilter = Filter.NO_FILTER;
	
	protected IOrder positionOrder;
	
	protected boolean useFilters;
	protected boolean reportFilters;
	protected Map<String, IFilter> allFilters = new TreeMap<String, IFilter>();

	public AbstractSinglePairSingleStrategy(Properties p) {
		super(p);
		// expect that correct validation and correct object type setting have been done already 
			
		trendStDevDefinitionMin = Double.parseDouble(p.getProperty("trendStDevDefinitionMin"));
		trendStDevDefinitionMax = Double.parseDouble(p.getProperty("trendStDevDefinitionMax"));
		
		
		useFilters = Boolean.parseBoolean(p.getProperty("USE_FILTERS", "false").equalsIgnoreCase("yes") ? "true" : "false");
		reportFilters = Boolean.parseBoolean(p.getProperty("REPORT_FILTERS", "false").equalsIgnoreCase("yes") ? "true" : "false");
		
		// now get all the filter settings. Filtering is done only for members of this set
		if (p.containsKey(EntryFilters.FILTER_MOMENTUM.toString()) && p.getProperty(EntryFilters.FILTER_MOMENTUM.toString()).equals("yes")) 
			entryFilters.add(EntryFilters.FILTER_MOMENTUM);
		if (p.containsKey(EntryFilters.FILTER_ENTRY_BAR_HIGH.toString()) && p.getProperty(EntryFilters.FILTER_ENTRY_BAR_HIGH.toString()).equals("yes")) 
			entryFilters.add(EntryFilters.FILTER_ENTRY_BAR_HIGH);
		if (p.containsKey(EntryFilters.FILTER_ENTRY_BAR_LOW.toString()) && p.getProperty(EntryFilters.FILTER_ENTRY_BAR_LOW.toString()).equals("yes")) 
			entryFilters.add(EntryFilters.FILTER_ENTRY_BAR_LOW);
		if (p.containsKey(EntryFilters.FILTER_4H.toString()) && p.getProperty(EntryFilters.FILTER_4H.toString()).equals("yes")) 
			entryFilters.add(EntryFilters.FILTER_4H);
		
	}
	
	public void onStartExec(IContext context) throws JFException {
		super.onStartExec(context);
		
        momentum = new Momentum(history, indicators, filtersConfiguration);
        
		Set<Object> keys = conf.keySet();
		for (Object key : keys) {
			String strKey = (String)key;
			if (strKey.startsWith(FilterFactory.FILTER_PREFIX)) {
				filtersConfiguration.setProperty(strKey, conf.getProperty(strKey));
				allFilters.put(strKey, FilterFactory.createFilter(strKey, conf.getProperty(strKey), indicators, history));
			}
		}        
       
        this.positionOrder = null;
        log.print("Checking open orders...");
        for (IOrder entryOrder : engine.getOrders()) {
            if (entryOrder.getState().equals(IOrder.State.OPENED))
            {
            	log.print("Open order " + entryOrder.getLabel() + " found; Setting state to " + TradeState.ENTRY_ORDER_WAITING);
            	tradeState.stateTransition(TradeState.ENTRY_ORDER_WAITING, new DateTime().getMillis());
            	break;
            }
            if (entryOrder.getState().equals(IOrder.State.FILLED))
            {
            	log.print("Position with order " + entryOrder.getLabel() + " found; Current profit/loss: " + FXUtils.df1.format(entryOrder.getProfitLossInPips()) + " pips");
            	log.print("Setting state to " + TradeState.POSITION_OPENED);
            	tradeState.stateTransition(TradeState.POSITION_OPENED, new DateTime().getMillis());
            	break;
            }
		}

	}

	protected void placeBuyOrder(Instrument instrument, Period period, IBar askBar,	IBar bidBar) throws JFException {
				double entryPrice = bidBar.getHigh() + safeZone / 10000;
				// adjust stop loss to be below 35 pips
				if ((entryPrice - protectiveStop) * 10000 > MAX_RISK)
				{
					protectiveStop = entryPrice - MAX_RISK / 10000;
				}
				
				double tradeCandleLeverage = 1;
				if (FLEXIBLE_LEVERAGE) {
					tradeCandleLeverage = MAX_RISK / (entryPrice - protectiveStop) / 10000; 
				}
				
				FXUtils.setProfitLossHelper(account.getCurrency(), history);
				BigDecimal dollarAmount = FXUtils.convertByTick(BigDecimal.valueOf(account.getEquity()), account.getCurrency(), instrument.getSecondaryCurrency(), OfferSide.BID);
				// in millions
			    double tradeAmount = dollarAmount.doubleValue() * tradeCandleLeverage * DEFAULT_LEVERAGE / 1000000.0; 
			
			    positionOrder = engine.submitOrder(getLabel(instrument), instrument, OrderCommand.BUYSTOP, tradeAmount, entryPrice);
			}

	protected void placeSellOrder(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		double entryPrice = bidBar.getLow() - safeZone / 10000;
		// adjust stop loss to be below 35 pips
		if ((protectiveStop - entryPrice) * 10000 > MAX_RISK)
		{
			protectiveStop = entryPrice + MAX_RISK / 10000;
		}
		
		double tradeCandleLeverage = 1;
		if (FLEXIBLE_LEVERAGE) {
			tradeCandleLeverage = MAX_RISK / (protectiveStop - entryPrice); 
		}
		
		// in millions
	    double tradeAmount = account.getEquity() * tradeCandleLeverage * DEFAULT_LEVERAGE / 1000000.0; 
	
	    positionOrder = engine.submitOrder(getLabel(instrument), instrument, OrderCommand.SELLSTOP, tradeAmount, entryPrice);
	}

	protected void positionExitCleanUp() {
		breakEvenSet = false;
		protectProfitSet = false;
		tradeTrigger.resetT1BL();
		lastStopType = LastStopType.NONE;
		tradeStats.reset();
	}

	protected IOrder getPositionOrder() {
		return positionOrder;
	}

	protected Instrument getSelectedInstrument() {
		return selectedInstrument;
	}	
}