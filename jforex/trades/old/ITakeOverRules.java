package jforex.trades.old;

public interface ITakeOverRules {
	public boolean canTakeOver(String currentTrade, String nextTrade);
}
