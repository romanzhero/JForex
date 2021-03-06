package jforex.utils.log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import com.dukascopy.api.IConsole;

import jforex.utils.FXUtils;
import jxl.Cell;
import jxl.CellView;
import jxl.Workbook;
import jxl.write.Label;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import jxl.write.biff.RowsExceededException;
import jxl.write.Number;
import jxl.write.NumberFormat;
import jxl.write.WritableCell;
import jxl.write.WritableCellFormat;

public class Logger {
	IConsole console;
	BufferedWriter logFile;
	boolean isOpen = false;
	static String SEPARATOR = ";";
	String xlsReportFileName;
	File xlsFile = null;
	WritableWorkbook xlsWB = null;
	WritableSheet xlsSheet = null;
	private int xlsRow = 0; // Same call for header and data !

	public enum logTags {
		ORDER, // log entries for orders
		STRATEGY, // log entries for strategies
		ENTRY_FOUND, ENTRY_CANCELED, ENTRY_FILLED, EXIT_STOP_LOSS, STOP_UPDATED, TRAILING_CONDITION_FOUND, TRAILING_STOP_UPDATED, EXIT_TRAILING_STOP, ENTRY_STATS, EXIT_STATS, PROFIT_REPORT
	}

	public Logger(IConsole pConsole, String logFileName) {
		super();
		this.console = pConsole;
		try {
			this.logFile = new BufferedWriter(new FileWriter(logFileName));
			isOpen = true;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Logger(String logFileName) {
		super();
		try {
			this.logFile = new BufferedWriter(new FileWriter(logFileName));
			isOpen = true;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Logger(IConsole pConsole) {
		super();
		this.console = pConsole;
	}
	
	public void createXLS(String xlsName) {
		xlsFile = new File(xlsName);
		try {
			xlsWB = Workbook.createWorkbook(xlsFile);
			xlsSheet = xlsWB.createSheet("Stats", 0);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void printAction(String type, String orderID, String timestamp,
			String action, double stop, double stopDistanceToEntryPips,
			double PnLPips, double commisions) {
		String outStr = type + SEPARATOR + orderID + SEPARATOR + timestamp
				+ SEPARATOR + action;
		DecimalFormat df5 = new DecimalFormat("#.#####"); // half pip
		outStr = outStr + SEPARATOR + df5.format(stop);
		DecimalFormat df1 = new DecimalFormat("#.#");
		outStr = outStr + SEPARATOR + df1.format(stopDistanceToEntryPips)
				+ SEPARATOR + df1.format(PnLPips) + SEPARATOR
				+ df1.format(commisions);

		print(outStr);
	}

	public void printStatsHeader(String type, // might be both ENTRY_STATS and
												// EXIT_STATS
			String orderID, String timestamp) {
		String outStr = type + SEPARATOR + orderID + SEPARATOR + timestamp
				+ SEPARATOR + "MACD" + SEPARATOR + "MACD_Signal" + SEPARATOR
				+ "MACD_H" + SEPARATOR + "StochFast" + SEPARATOR + "StochSlow"
				+ SEPARATOR + "StocsDiff" + SEPARATOR + "ADX" + SEPARATOR
				+ "DI_PLUS" + SEPARATOR + "DI_MINUS" + SEPARATOR
				+ "entryBarHighChannelPerc" + SEPARATOR
				+ "stopBarLowChannelPerc" + SEPARATOR + "MAsStDevPos";

		print(outStr);
	}

	public void printStats(
			String type, // might be both ENTRY_STATS and EXIT_STATS
			String orderID, String timestamp, double macd, double macd_signal,
			double macd_h, double stochFast, double stochSlow, double adx,
			double di_plus, double di_minus, double entryBarHighChannelPerc,
			double stopBarLowChannelPerc, double MAStDevPos) {
		DecimalFormat dfMACD = new DecimalFormat("#.##########");
		DecimalFormat df1 = new DecimalFormat("#.#");

		String outStr = type + SEPARATOR + orderID + SEPARATOR + timestamp
				+ SEPARATOR + dfMACD.format(macd) + SEPARATOR
				+ dfMACD.format(macd_signal) + SEPARATOR
				+ dfMACD.format(macd_h) + SEPARATOR + df1.format(stochFast)
				+ SEPARATOR + df1.format(stochSlow) + SEPARATOR
				+ df1.format(stochFast - stochSlow) + SEPARATOR
				+ df1.format(adx) + SEPARATOR + df1.format(di_plus) + SEPARATOR
				+ df1.format(di_minus) + SEPARATOR
				+ df1.format(entryBarHighChannelPerc) + SEPARATOR
				+ df1.format(stopBarLowChannelPerc) + SEPARATOR
				+ df1.format(MAStDevPos);

		print(outStr);
	}

	public void printLabelsFlex(List<FlexLogEntry> line) {
		String output = new String();
		int cnt = 0;
		for (FlexLogEntry e : line) {
			if (cnt++ > 0)
				output += SEPARATOR + e.getHeaderLabel();
			else
				output += e.getHeaderLabel();
		}
		print(output);
	}

	public void printValuesFlex(List<FlexLogEntry> line, boolean flush) {
		String output = new String();
		int cnt = 0;
		for (FlexLogEntry e : line) {
			if (cnt++ > 0)
				output += SEPARATOR + e.getFormattedValue();
			else
				output += e.getFormattedValue();
		}
		print(output, flush);
	}
	
	public void printValuesFlex(List<FlexLogEntry> line) {
		printValuesFlex(line, false);
	}

	public void print(String message) {
		if (console != null)
			console.getOut().println(message);
		if (logFile != null && isOpen)
			try {
				logFile.write(message + "\n");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

	public void print(String message, boolean flush) {
		if (console != null)
			console.getOut().println(message);
		if (logFile != null && isOpen)
			try {
				logFile.write(message + "\n");
				if (flush)
					logFile.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

	public void close() {
		if (logFile != null)
			try {
				isOpen = false;
				logFile.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		if (xlsWB != null)
			try {
				// remove columns with same content
				xlsRemoveSameContentColumns();
				
				//TODO: mozda ovo treba pri otvaranju, pre pisanja sadrzaja...
		    	CellView cv = new CellView();
		    	cv.setAutosize(true);
		    	for (int i = 0; i < xlsSheet.getColumns(); i++)
		    		xlsSheet.setColumnView(i, cv);
		    	
				xlsWB.write();				
				xlsWB.close();
			} catch (WriteException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
	}

	protected void xlsRemoveSameContentColumns() {
		List<Integer> columnsToRemove = new ArrayList<Integer>();
		List<String> 
			removedHeaders = new ArrayList<String>(),
			removedCellContent = new ArrayList<String>();
		WritableSheet xlsSh = xlsWB.getSheet(0);
		for (int i = 0; i < xlsSh.getColumns(); i++) {
			Cell[] currColumn = xlsSh.getColumn(i);
			boolean differentCellContent = false;
			// Skip header !
			for (int j = 1; j < currColumn.length - 1; j++) {
				Cell 
					currCell = currColumn[j],
					nextCell = currColumn[j+1];
				differentCellContent = !currCell.getContents().equals(nextCell.getContents());
				if (differentCellContent) {
					break;
				}
			}
			if (differentCellContent == false) {
				if (currColumn.length > 1) {
					columnsToRemove.add(new Integer(i));
					removedHeaders.add(new String(currColumn[0].getContents()));
					removedCellContent.add(new String(currColumn[1].getContents()));
				} else {
					removedHeaders.add("Column with no content, not removed: " + new String(currColumn[0].getContents()));					
					removedCellContent.add("Column empty");
				}
			}
		}
		String removedColumnsIdxs = new String("Removed columns indices: ");
		int 
			deletedColumns = 0;
		for (Integer currCol : columnsToRemove) {
			// koriguj vrednost indexa preostalih kolona nakon brisanja kolone !
			xlsSh.removeColumn(currCol.intValue() - deletedColumns++);
			removedColumnsIdxs += currCol.intValue() + ", ";
		}
		// Write removed headers to second sheet for reference
		WritableSheet reportSheet = xlsWB.createSheet("Removed columns", xlsWB.getNumberOfSheets());
		int currRow = 0;
		try {
			int i = 0;
			for (String currHeader: removedHeaders) {
				writeCell(reportSheet, 0, currRow, currHeader);
				writeCell(reportSheet, 1, currRow++, removedCellContent.get(i++));
			}
			writeCell(reportSheet, 0, currRow, removedColumnsIdxs);
		} catch (RowsExceededException e) {
			e.printStackTrace();
		} catch (WriteException e) {
			e.printStackTrace();
		}
	}	
   
    private void writeCell(WritableSheet sheet, int column, int row, String s) throws RowsExceededException, WriteException {
        Label label = new Label(column, row, s);
        sheet.addCell(label);
    }
    
    private int writeCell(WritableSheet sheet, int column, int row, FlexLogEntry logEntry) throws RowsExceededException, WriteException {
    	WritableCell cell = null;
    	if (logEntry.getValue() == null
    		&& logEntry.getDa1dim_values() == null
    		&& logEntry.getDa2DimValue() == null)  {
        	StringTokenizer st = new StringTokenizer(logEntry.getHeaderLabel(), SEPARATOR);
        	int additionalColumns = 0;
        	while (st.hasMoreTokens()) {
            	st.nextToken();
	    		additionalColumns++;
        	}
        	return additionalColumns > 0 ? additionalColumns - 1 : 0;    		
    	} else if (logEntry.isDouble()) {
    		if (logEntry.getDecimalFormat() == null)
    			cell = new Number(column, row, logEntry.getDoubleValue());
    		else {
//    			WritableCellFormat cellFormat = new WritableCellFormat(calcCellFormat(logEntry)); 
//    			cell = new Number(column, row, logEntry.getDoubleValue(), cellFormat);
    			cell = new Number(column, row, logEntry.getDoubleValue());
    		}
    	}
    	else if (logEntry.isInteger())
    		cell = new Number(column, row, logEntry.getIntegerValue());
    	else if (logEntry.isLong())
    		cell = new Number(column, row, logEntry.getLongValue());
    	else {
    		// one of the complex types. If double array format accordingly and use Number cell type !
    		WritableCellFormat cellFormat = new WritableCellFormat(calcCellFormat(logEntry));
        	StringTokenizer st = new StringTokenizer(logEntry.getFormattedValue(), SEPARATOR);
        	int additionalColumns = 0;
        	while (st.hasMoreTokens()) {
            	String nextToken = st.nextToken();
            	if (logEntry.getDecimalFormat() != null) { 
            		try {
                		//cell = new Number(column + additionalColumns, row, Double.parseDouble(nextToken), cellFormat);
                		cell = new Number(column + additionalColumns, row, Double.parseDouble(nextToken));
            		} catch (Exception e) {
                		cell = new Label(column + additionalColumns, row, nextToken);
					} 
            	}
            	else
            		cell = new Label(column + additionalColumns, row, nextToken);
	    		sheet.addCell(cell);
	    		additionalColumns++;
        	}
        	return additionalColumns > 0 ? additionalColumns - 1 : 0;
    	}
        sheet.addCell(cell);
        return 0;
    }

	protected NumberFormat calcCellFormat(FlexLogEntry logEntry) {
		if (logEntry.getDecimalFormat() == null)
			return new NumberFormat("#.#####");
		
		if (logEntry.getDecimalFormat().equals(FXUtils.df1))
			return new NumberFormat("#.#"); 
		else if (logEntry.getDecimalFormat().equals(FXUtils.df2))
			return new NumberFormat("#.##");
		else
			return new NumberFormat("#.#####");
	}
    
    public void printXlsCSVLine(String csvLine) {
    	if (xlsWB == null)
    		return;
    	
    	StringTokenizer st = new StringTokenizer(csvLine, SEPARATOR);
		int column = 0;
    	while (st.hasMoreTokens()) {
    		String currCell = st.nextToken();
    		try {
				writeCell(xlsSheet, column++, xlsRow, currCell);
			} catch (RowsExceededException e) {
				e.printStackTrace();
			} catch (WriteException e) {
				e.printStackTrace();
			}
    	}
		xlsRow++;
    }
    
	public void printXlsLabelsFlex(List<FlexLogEntry> line) {
    	if (xlsWB == null)
    		return;
		int column = 0;
		for (FlexLogEntry e : line) {
			try {
				StringTokenizer st = new StringTokenizer(e.getHeaderLabel(), SEPARATOR);
				while (st.hasMoreTokens()) {
					String currHeader = st.nextToken();
					writeCell(xlsSheet, column++, 0, currHeader);
				}
			} catch (RowsExceededException e1) {
				e1.printStackTrace();
			} catch (WriteException e1) {
				e1.printStackTrace();
			}
		}
		try {
			xlsWB.write();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
	
	public void printXlsValuesFlex(List<FlexLogEntry> line) {
    	if (xlsWB == null)
    		return;
    	
		int 
			column = 0,
			additionalColumn = 0;
		if (xlsRow == 0)
			xlsRow++; // avoid overwriting the first row with labels
		
		for (FlexLogEntry e : line) {
			try {
				additionalColumn = writeCell(xlsSheet, column, xlsRow, e);
				column += additionalColumn + 1;
			} catch (RowsExceededException e1) {
				e1.printStackTrace();
			} catch (WriteException e1) {
				e1.printStackTrace();
			}
		}
		xlsRow++;
/*		if (xlsRow % 100 == 0) {
			try {
				xlsWB.write();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}*/
	}
    
}
