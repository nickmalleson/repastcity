package uk.ac.leeds.mass.genetic_algorithm;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

/** Used to log messages from MPJ. This will be replaced by Outputter... */
public class GALog {

	private int rank;
	private static Calendar calendar ; // FOr logging time.

	private File logFile ;
	private FileWriter logFileWriter;

	private String directory ; // The subdir to store new scenario in.

	public GALog(int id, String directory) {
		this.rank = id;
		this.directory = directory;
	}
	/**
	 * Used to write any output, saves to mpjlog.txt file.
	 * @param s
	 */

	public void log(String s) {
		calendar = new GregorianCalendar();
		String time = twoDigits(calendar.get(Calendar.HOUR_OF_DAY))+":"+
			twoDigits(calendar.get(Calendar.MINUTE))+":"+twoDigits(calendar.get(Calendar.SECOND));
//		System.out.println(time+"- Proc "+this.rank+" logs: "+s);
		try {
			if (logFile==null) {
				logFile = new File("log/"+this.directory+"/mpjlog"+this.rank+".txt");
				if (!logFile.exists()) {
					logFile.createNewFile();
				}
				logFileWriter = new FileWriter(logFile);
			}
			logFileWriter.write(time+"("+"MODELNO?"+"): "+s+"\n");
			System.out.println(s);	// So output goes to standard out as well
			logFileWriter.flush(); 	// So that output written to log immediately
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/** Makes sure the input has two digits.
	 *
	 * @param i The input number
	 * @return i if i>9 or "0"+i if i<9
	 */
	private String twoDigits(int i) {
		if (i<9)
			return "0"+i;
		else
			return i+"";
	}
	/**
	 * Convenience function Used to log stack traces, calls log(String) saves to mpjlog.txt file.
	 * @param s
	 * @see log(String s)
	 */
	public void log(StackTraceElement[] s) {
      if (s != null && s.length > 0) {
         for (StackTraceElement e : s) {
            log(e.toString());
         }
      }
	}

	public void closeLog() {
		if (this.logFileWriter!=null) {
			try {
				this.logFileWriter.close();
				this.logFileWriter=null;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

//	public void flush() {
//		if (this.logFileWriter!=null) {
//			try {
//				this.logFileWriter.flush();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}
//	}

   	/** Logs are stored in subdirectories (under the log/ parent dir). Each mpj run is given a new, ascending
	 * order, integer subdirectory, so that if a few mpj jobs are started at once the logs will not interfere with
	 * each other. This function finds an appropriate name for the subdirectory for this run. E.g. if this is the
	 * fourth job executed the log will be in /log/3/*.
	 * <p> Because each node will have its own instance of this class, static variables can't be used to make sure
	 * all nodes from the same run put their logs in the same directory. Instead, directories are found as follows:
	 * <ul>
	 * <li>If this node is a master the first thing it does is create a new sub-directory, one integer higher
	 * than any others</li>
	 * <li>If this node is a slave it finds the highest integer directory and uses that one to store logs. Slaves
	 * don't create their GALog until they have received their first message from the master, which ensures that
	 * the master will have created the appropriate directory.</li>
	 * </ul> */
	public static String findNewLogDir(int rank) {
		// Check that a root log directory has been created.
		File thedir = new File("log/");
		if (!thedir.exists()) {
			thedir.mkdir();
		}
		
		// Find the highest numbered directory
		ArrayList<Integer> dirNumbers = new ArrayList<Integer>();
		for (String s:new File("log/").list()) { // Iterate over all directories in log/
			File f = new File("log/"+s);
			if (f.isDirectory()) {
				try {
					// See if the directory name is a number
					Integer i = Integer.parseInt(f.getName());
					dirNumbers.add(i);
				} catch (NumberFormatException e){ } // Directory isn't a number
			}
		}
//		File newDir = ""; // The new directory to store logs in.
		Integer highest = 0;
		for (Integer i:dirNumbers) {
			if (i>highest) {
				highest = i;
			}
		}
		// Have found current highest directory]
		if (rank==0) { // this node is the master, create the new sub-directory.
			String dir = new Integer(highest+1).toString();
			new File("log/"+dir).mkdir();
			return dir;
		}
		else { // this node is a slave, use the highest dir (this will just have been created by master)
			String dir = new Integer(highest).toString();
			return dir;
		}
	}
}
