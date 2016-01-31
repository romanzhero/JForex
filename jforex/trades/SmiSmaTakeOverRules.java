package jforex.trades;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SmiSmaTakeOverRules implements ITakeOverRules {
	protected Map<String, Set<String>> noTakeOverRules = new HashMap<String, Set<String>>();

	public SmiSmaTakeOverRules() {
		Set<String> rule1 = new HashSet<String>();
		rule1.add("SMI");
		noTakeOverRules.put("SMATrendIDFollow", rule1);
	}

	@Override
	public boolean canTakeOver(String currentTrade, String nextTrade) {
		Set<String> noTakeOver = noTakeOverRules.get(currentTrade);
		return noTakeOver == null || !noTakeOver.contains(nextTrade);
	}

}
