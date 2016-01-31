package jforex.trades;

public interface ITakeOverRules {
	public boolean canTakeOver(String currentTrade, String nextTrade);
}
