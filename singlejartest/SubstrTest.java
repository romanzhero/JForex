package singlejartest;

import jforex.utils.FXUtils;

public class SubstrTest {

	public static void main(String[] args) {
		String test = "AUDJPY_2013_03_05_00_00_Ichi_01_2nd";
		int 
		ordNoPos = test.lastIndexOf("_"),
		orderNumber = Integer.parseInt(test.substring(ordNoPos - 2, ordNoPos)) + 1;
	String 
		orderNumberStr = "_" + FXUtils.if2.format(orderNumber),
		org_order_label = test.substring(0, test.lastIndexOf("_") - 3), 
		new_label = org_order_label + orderNumberStr + "_2nd";
	System.out.println("New label: " + new_label + ", org label: " + org_order_label);
		

	}

}
