package repastcity3.main;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import cern.colt.Arrays;


/**
 * Class can be used to run a model without using the repast GUI by initialising and calling the
 * BurgdSimRunner class. There's a discussion about this on the repast email list: 
 * <url>http://www.nabble.com/How-to-programmatically-start-RepastS---to14351077.html</url>
 * @see BurgdSimRunner
 * @author Nick Malleson
 */

public class RepastCityMain {

	public RepastCityMain() {}

	private static Logger LOGGER = Logger.getLogger(RepastCityMain.class.getName());

	private static boolean stopSim = false;	// Used by other classes to signal model should stop (i.e. error)

	public static void main(String[] args){

		//		System.out.println("***************** PRINT THE CLASSPATH ****************");
		//        //Get the System Classloader
		//        ClassLoader sysClassLoader = ClassLoader.getSystemClassLoader();
		//        //Get the URLs
		//        URL[] urls = ((URLClassLoader)sysClassLoader).getURLs();
		//        for(int i=0; i< urls.length; i++)
		//            System.out.println(urls[i].getFile());
		//        System.out.println("*************************** ***************************");
		//		System.out.println("***************** PRINT AGAIN FROM SYSTEM PROPERTY ****************");
		//        System.out.println(System.getProperties().get("java.class.path").toString());
		//        System.out.println("*************************** ***************************");


		try {
			
			if (args.length!=1) {
				throw new Exception("RepastCityMain expects the scenario to load as the first (and only) " +
						"command-line argument. Got: "+Arrays.toString(args));
			}
			
			File file = new File(args[0]); // the scenario dir
			
			if (!file.exists()) {
				throw new Exception("Scenario directory not found: "+file.toString());
			}
			else {
				LOGGER.info("Loading scenario: "+file.getAbsolutePath());
			}

			RepastCityRunner runner = new RepastCityRunner();
			GlobalVars.RUNNER = runner;
			runner.load(file);     // load the repast scenario

			System.out.println("*********************** STARTING NEW SIM *********************");
			/* INITIALISE THE SIMULATION */
			runner.runInitialize();  	// ContextCreator.build() called here.
			System.out.println("*********************** INITIALISED *********************");
			// For some reason this next line doesn't cause sim to terminate properly, the "end" functions are called
			// but sim keeps running and breaks. So manually check if tickCount>endTime in while loop
			//			RunEnvironment.getInstance().endAt(endTime);			
			double endTime = GlobalVars.RUN_TIME;
			LOGGER.info("RepastCityMain: will end runs at "+GlobalVars.RUN_TIME+" iterations");

			/* RUN THE SIMULATION */				
			//				while (runner.getActionCount() > 0){  // loop until last action is left
			//					double ticks = RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
			//					if (runner.getModelActionCount() == 0 || ticks > endTime) {
			//						runner.setFinishing(true);
			//					}
			//					runner.step();  // execute all scheduled actions at next tick
			//					if (ticks % 500 == 0) {
			//						System.out.println(ticks+", ");
			//					}
			//				}
			// Use a tick counter to determine when to stop sim rather than checking how many actions remain
			double ticks = 0;

			while (ticks <= endTime){  // loop until last action is left
				if (RepastCityMain.stopSim) { // Another class has set this, probably because an error has occurred
					ticks=endTime;
					System.out.println("RepastCityMain has been told to stop, terminating this run.");
					RepastCityMain.stopSim = false; // reset the boolean ready for the next run
				}
				if (ticks == endTime) {
					System.out.println("RepastCityMain, last tick, setting finishing");
					runner.setFinishing(true);
				}
				runner.step();  // execute all scheduled actions at next tick
				ticks++;
			} // while
			System.out.println();

			System.out.println("***********************STOPPING CURRENT SIM*********************");
			runner.stop();          // execute any actions scheduled at run end
			// for some reason this causes a NullPointer in repast somewhere
//			runner.cleanUpRun();


			System.out.println("*********************** FINISHING RUN *********************");
			// If appropriate, summarise all agent's state variables and motive values
			runner.cleanUpBatch();    // run after all runs complete

		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "RepastCityMain caught exception, exitting:", e);
		}
	}// main



	/** Tells this main class to stop the current sim. Can be used by other classes to indicate that an error has
	 * occurred and the sim must terminate.  */
	public static void stopSim() {
		RepastCityMain.stopSim = true;
	}
}

