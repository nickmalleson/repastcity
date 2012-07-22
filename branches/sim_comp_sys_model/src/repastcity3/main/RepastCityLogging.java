/*
©Copyright 2012 Nick Malleson
This file is part of RepastCity.

RepastCity is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

RepastCity is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with RepastCity.  If not, see <http://www.gnu.org/licenses/>.
 */

package repastcity3.main;

import java.io.File;
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import cern.colt.Arrays;

/**
 * Used to configure logging. This code could go anywhere really but this seems like a good place.
 * 
 * @author Nick Malleson
 * 
 */
abstract class RepastCityLogging {
	
	/*
	 * The static block will configure logging for the whole model (all classes). We want one file for general info
	 * messages and another for more specific messages. Only info messages should go to the console.
	 */
	static {
		// See if another class has already set a log directory (e.g. when being run as part of a GA).
		String rootDir = ContextManager.logDir == null ? "" : ContextManager.logDir;

		// Make sure the directory finishes with a trailing slash (unless it's the empty string
		if (rootDir != "" && rootDir.endsWith("/")) {
			rootDir += "/";
		}

		// Create file handlers
		String infoFile = GlobalVars.LogInfo;
		String allFile = GlobalVars.LogAll;

		FileHandler infoHandler = null;
		FileHandler allHandler = null;
		try {
			infoHandler = new FileHandler(rootDir + infoFile);
			infoHandler.setLevel(Level.INFO);
			infoHandler.setFormatter(new SimpleFormatter());
			allHandler = new FileHandler(rootDir + allFile);
			allHandler.setLevel(Level.FINEST);
			allHandler.setFormatter(new SimpleFormatter());

		} catch (SecurityException e) {
			// System.err.println("Error ("+e.getClass().toString()+"), could not configure a file handler. Message: " +
			// (e.getMessage() == null ? "null" : e.getMessage()) + ". Will not continue,.");

			throw new RuntimeException("Could not configure a file handler, will not continue,.", e);
			// e.printStackTrace();
			// System.exit(-1);
		} catch (IOException e) {
			throw new RuntimeException("Could not configure a file handler, will not continue,.", e);
			// e.printStackTrace();
			// System.exit(-1);
		}

		ConsoleHandler consoleHandler = new ConsoleHandler();
		consoleHandler.setLevel(Level.INFO);
		consoleHandler.setFormatter(new Formatter() { // Formatter that just returns the message, no other info.

					@Override
					public String format(LogRecord rec) {
						return (rec.getMessage() + "\n");
					}
				});

		// Add the handlers to the main (root) logger, after removing any that are there already
		Logger repastCityLogger = Logger.getLogger("repastcity3"); // (use repastcity3 otherwise we get all the FINE+ JVM messages!)
		repastCityLogger.setLevel(Level.ALL); // This is necessary otherwise FINE+ messages don't get passed the logger even if Handler's levels are ALL 
		
		repastCityLogger.setUseParentHandlers(false); // Stops messages being printed to the console twice (once by the root console logger as well) 
		// Remove any existing handlers from the main repast logger
		Handler[] handlers = repastCityLogger.getHandlers();
		for (Handler h : handlers) {
			repastCityLogger.removeHandler(h);
		}
		
		// Add the handlers to the logger
		repastCityLogger.addHandler(infoHandler);
		repastCityLogger.addHandler(allHandler);
		repastCityLogger.addHandler(consoleHandler);

		// Run a couple of tests
		Logger LOGGER = Logger.getLogger(RepastCityLogging.class.getName());
		LOGGER.log(Level.INFO, "This logger is configured for INFO");
		LOGGER.log(Level.SEVERE, "This logger is configured for SEVERE");
		LOGGER.log(Level.WARNING, "This logger is configured for WARNING");
		LOGGER.log(Level.FINE, "This logger is configured for FINE");
		LOGGER.log(Level.FINEST, "This logger is configured for FINEST");
		// XXXX HERE SEE WHY LEVEL.FINEST ISN'T PRINTED TO CONSOLE AND HECK THAT LOG FILES ARE AS EXPECTED
	}
	
	/** Dummy function whose only purpose is to allow a RepastCityLogging object to be created and the
	 *  static initialiser block called */
	public static void dummy() {
		
	}
	

//	// Make sure this is only initialised once
//	private static boolean initialised = false;

	// // Creates a file handler that outputs everything and add's it to the root logger.
	// public static void init() {
	// if (RepastCityLogging.initialised) {
	// return;
	// }
	// else {
	// RepastCityLogging.initialised = true;
	// }
	//
	// try {
	//
	// // Get the root logger
	// Logger logger = Logger.getLogger("");
	// // Logger logger = Logger.getLogger("repastcity3");
	//
	// // Create handlers
	// File logFile = new File("model_log.txt");
	// if (logFile.exists())
	// logFile.delete(); // Delete an old log file
	// FileHandler fileHandler = new FileHandler(logFile.getAbsolutePath());
	// fileHandler.setLevel(Level.ALL); // Write everything to the file
	//
	// // Create a formatters.
	// Formatter fileFormatter = new SimpleFormatter();
	//
	// // Add the formatters to the handlers
	// fileHandler.setFormatter(fileFormatter);
	//
	// // Add the handlers to the logger
	// logger.addHandler(fileHandler);
	//
	// } catch (Exception e) {
	// System.err.println("Problem creating loggers, cannot continue (exit with -1).");
	// System.exit(-1);
	// }
	//
	// }
	
}
