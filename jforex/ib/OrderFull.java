package jforex.ib;

import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.OrderState;

public class OrderFull {
	
	protected int orderId;
	protected Order m_Order;
	protected Contract m_Contract;
	protected OrderState m_OrderState;
	
	public OrderFull(int pOrderId, Order pOrder, Contract pContract, OrderState pOrderState) {
		super();
		orderId = pOrderId;
		this.m_Order = pOrder;
		this.m_Contract = pContract;
		this.m_OrderState = pOrderState;
	}

	public Order getOrder() {
		return m_Order;
	}

	public Contract getContract() {
		return m_Contract;
	}

	public OrderState getOrderState() {
		return m_OrderState;
	}	

}
