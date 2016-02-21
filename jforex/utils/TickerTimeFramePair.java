package jforex.utils;

public class TickerTimeFramePair {
	public String ticker;
	public int time_frame;

	public TickerTimeFramePair(String ticker, int time_frame) {
		super();
		this.ticker = ticker;
		this.time_frame = time_frame;
	}

	@Override
	public String toString() {
		return new String("[" + ticker + ", " + time_frame + "]");
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		// ??? result = prime * result + getOuterType().hashCode();
		result = prime * result + ((ticker == null) ? 0 : ticker.hashCode());
		result = prime * result + time_frame;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TickerTimeFramePair other = (TickerTimeFramePair) obj;
		if (ticker == null) {
			if (other.ticker != null)
				return false;
		} else if (!ticker.equals(other.ticker))
			return false;
		if (time_frame != other.time_frame)
			return false;
		return true;
	}

}
