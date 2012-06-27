package repastcity3.main;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import repast.simphony.batch.BatchScenarioLoader;
import repast.simphony.engine.controller.Controller;
import repast.simphony.engine.controller.DefaultController;
import repast.simphony.engine.environment.AbstractRunner;
import repast.simphony.engine.environment.ControllerRegistry;
import repast.simphony.engine.environment.DefaultRunEnvironmentBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.environment.RunEnvironmentBuilder;
import repast.simphony.engine.environment.RunState;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.Schedule;
import repast.simphony.parameter.SweeperProducer;



/**
 * Class can be used to control a model without using the repast GUI. It is called from BurgdSimMain.
 * There's a discussion about this on the repast email list: 
 * <url>http://www.nabble.com/How-to-programmatically-start-RepastS---to14351077.html</url>
 * @author Nick Malleson
 * @see RepastCityMain
 *
 */
public class RepastCityRunner extends AbstractRunner {

	private static Logger LOGGER = Logger.getLogger(RepastCityRunner.class.getName());

	private RunEnvironmentBuilder runEnvironmentBuilder;
	protected Controller controller;
	protected boolean pause = false;
	protected Object monitor = new Object();
	protected SweeperProducer producer;
	private ISchedule schedule;

	public RepastCityRunner() {
		runEnvironmentBuilder = new DefaultRunEnvironmentBuilder(this, true);
		controller = new DefaultController(runEnvironmentBuilder);
		controller.setScheduleRunner(this);
	}

	public void load(File scenarioDir) throws Exception {
		if (scenarioDir.exists()) {
			BatchScenarioLoader loader = new BatchScenarioLoader(scenarioDir);
			ControllerRegistry registry = loader.load(runEnvironmentBuilder);
			controller.setControllerRegistry(registry);
		} else {
			LOGGER.log(Level.SEVERE, "Scenario not found", new IllegalArgumentException(
					"Invalid scenario " + scenarioDir.getAbsolutePath()));
			return;
		}

		controller.batchInitialize();
		controller.runParameterSetters(null);
	}

	public void runInitialize(){
//		try {
//			producer = new XMLSweeperProducer(new File("../repastcity3.rs/parameters.xml").toURI().toURL());
//			controller.runInitialize(producer.getParameters());
			controller.runInitialize(null);
			schedule = RunState.getInstance().getScheduleRegistry().getModelSchedule();
//		} catch (MalformedURLException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

	public void cleanUpRun(){
		controller.runCleanup();
	}
	public void cleanUpBatch(){
		controller.batchCleanup();
	}

	// returns the tick count of the next scheduled item
	public double getNextScheduledTime(){
		return ((Schedule)RunEnvironment.getInstance().getCurrentSchedule()).peekNextAction().getNextTime();
	}

	// returns the number of model actions on the schedule
	public int getModelActionCount(){
		return schedule.getModelActionCount();
	}

	// returns the number of non-model actions on the schedule
	public int getActionCount(){
		return schedule.getActionCount();
	}

	// Step the schedule
	public void step(){
		schedule.execute();
	}

	// stop the schedule
	public void stop(){
		if ( schedule != null )
			schedule.executeEndActions();
	}

	public void setFinishing(boolean fin){
		schedule.setFinishing(fin);
	}

	public void execute(RunState toExecuteOn) {
		// required AbstractRunner stub.  We will control the
		//  schedule directly.
	}
} 

