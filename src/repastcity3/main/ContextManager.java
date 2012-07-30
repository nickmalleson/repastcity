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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

import repast.simphony.context.Context;
import repast.simphony.context.DefaultContext;
import repast.simphony.context.space.gis.GeographyFactoryFinder;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.gis.Geography;
import repast.simphony.space.gis.GeographyParameters;
import repast.simphony.space.gis.SimpleAdder;
import repast.simphony.space.graph.Network;
import repast.simphony.space.graph.RepastEdge;
import repastcity3.agent.AgentFactory;
import repastcity3.agent.BurglaryWeights;
import repastcity3.agent.IAgent;
import repastcity3.agent.ThreadedAgentScheduler;
import repastcity3.environment.Building;
import repastcity3.environment.Community;
import repastcity3.environment.GISFunctions;
import repastcity3.environment.Junction;
import repastcity3.environment.NetworkEdge;
import repastcity3.environment.NetworkEdgeCreator;
import repastcity3.environment.Road;
import repastcity3.environment.SpatialIndexManager;
import repastcity3.environment.contexts.AgentContext;
import repastcity3.environment.contexts.BuildingContext;
import repastcity3.environment.contexts.CommunityContext;
import repastcity3.environment.contexts.JunctionContext;
import repastcity3.environment.contexts.RoadContext;
import repastcity3.exceptions.AgentCreationException;
import repastcity3.exceptions.EnvironmentError;
import repastcity3.exceptions.NoIdentifierException;

public class ContextManager implements ContextBuilder<Object> {

	/*
	 * A logger for this class. Note that there is a static block that is used to configure all logging for the model
	 * (at the bottom of this file).
	 */
	private static Logger LOGGER;

	/**
	 * The logDir can be set by other classes (e.g a GA or main class) to specify a directory other than the root model
	 * directory for log files.
	 */
	public static String logDir = null;

	/** A name for this model run which should be unique (based on the current system time). */
	public static String modelName = null; 

	static {
		
		// Give this run a unique name based on the current time and the current random seed. This has to be done
		// here because the name of the model is required by the logger (initialised below).
		if (modelName == null) { // If not null then something else has already set this model's name
			Calendar calendar = new GregorianCalendar();
			ContextManager.modelName = "model-"+calendar.get(Calendar.YEAR)+"_"+(calendar.get(Calendar.MONTH)+1)+"_"+
				calendar.get(Calendar.DATE)+"-"+calendar.get(Calendar.HOUR_OF_DAY)+"_"+
				calendar.get(Calendar.MINUTE)+"_"+calendar.get(Calendar.SECOND)+"-"+RandomHelper.getSeed(); 
		}
		
		// Also create a directory for results, logs etc to go in to.
		File dir = new File(ContextManager.modelName);
		dir.mkdirs();
		ContextManager.logDir = ContextManager.modelName+"/"; // logDir will be read by the Logger class
		
		// This causes the static initialisation block of logging to to be executed, setting up loggers:
		RepastCityLogging.dummy() ;  
		
		// Configure the logger for this class
		LOGGER = Logger.getLogger(ContextManager.class.getName());
		
		LOGGER.log(Level.FINE, "The name for this model run is "+ContextManager.modelName);

		// Read in the model properties.
		try {
			readProperties();
		} catch (IOException ex) {
			throw new RuntimeException("Could not read model properties,  reason: " + ex.toString(), ex);
		}

	}

	// Optionally force agent threading off (good for debugging)
	private static final boolean TURN_OFF_THREADING = false;;

	private static Properties properties;

	/** An object to record the weights for burglary decisions for this model */
	private static BurglaryWeights burglaryWeights;
	
	/** An object to generate and organise the results of a simulation. The results.generateResults() function is
	 * scheduled to be called at the end of the simulation. */
	private static Results results = new Results(); 


	/*
	 * Pointers to contexts and projections (for convenience). Most of these can be made public, but the agent ones
	 * can't be because multi-threaded agents will simultaneously try to call 'move()' and interfere with each other. So
	 * methods like 'moveAgent()' are provided by ContextManager.
	 */

	private static Context<Object> mainContext;

	// building context and projection cab be public (thread safe) because buildings only queried
	public static Context<Building> buildingContext;
	public static Geography<Building> buildingProjection;

	public static Context<Community> communityContext;
	public static Geography<Community> communityProjection;

	public static Context<Road> roadContext;
	public static Geography<Road> roadProjection;

	public static Context<Junction> junctionContext;
	public static Geography<Junction> junctionGeography;
	public static Network<Junction> roadNetwork;

	public static Context<IAgent> agentContext;
	public static Geography<IAgent> agentGeography;

	/**
	 * Used by threads or other functions to indicate that an error has occurred to prevent normal model output results
	 * being generated
	 */
	public static boolean error = false;

	@Override
	public Context<Object> build(Context<Object> con) {

		// Keep a useful static link to the main context
		mainContext = con;
		
		// This is the name of the 'root'context
		mainContext.setId(GlobalVars.CONTEXT_NAMES.MAIN_CONTEXT);

		// Configure the environment
		String gisDataDir = ContextManager.getProperty(GlobalVars.GISDataDirectory);
		LOGGER.log(Level.FINE, "Configuring the environment with data from " + gisDataDir);

		try {

			// Create the buildings - context and geography projection
			buildingContext = new BuildingContext();
			buildingProjection = GeographyFactoryFinder.createGeographyFactory(null).createGeography(
					GlobalVars.CONTEXT_NAMES.BUILDING_GEOGRAPHY, buildingContext,
					new GeographyParameters<Building>(new SimpleAdder<Building>()));
			String buildingFile = gisDataDir + getProperty(GlobalVars.BuildingShapefile);
			GISFunctions.readShapefile(Building.class, buildingFile, buildingProjection, buildingContext);
			mainContext.addSubContext(buildingContext);
			SpatialIndexManager.createIndex(buildingProjection, Building.class);
			LOGGER.log(Level.FINER, "Read " + buildingContext.getObjects(Building.class).size() + " buildings from "
					+ buildingFile);

			// TODO Cast the buildings to their correct subclass. At the moment this model doesn't differentiate
			// between houses, workplaces etc.

			// Create the Roads - context and geography
			roadContext = new RoadContext();
			roadProjection = GeographyFactoryFinder.createGeographyFactory(null).createGeography(
					GlobalVars.CONTEXT_NAMES.ROAD_GEOGRAPHY, roadContext,
					new GeographyParameters<Road>(new SimpleAdder<Road>()));
			String roadFile = gisDataDir + getProperty(GlobalVars.RoadShapefile);
			GISFunctions.readShapefile(Road.class, roadFile, roadProjection, roadContext);
			mainContext.addSubContext(roadContext);
			SpatialIndexManager.createIndex(roadProjection, Road.class);
			LOGGER.log(Level.FINER, "Read " + roadContext.getObjects(Road.class).size() + " roads from " + roadFile);

			// Create the communities (OAs with OAC data).
			communityContext = new CommunityContext();
			communityProjection = GeographyFactoryFinder.createGeographyFactory(null).createGeography(
					GlobalVars.CONTEXT_NAMES.COMMUNITY_GEOGRAPHY, communityContext,
					new GeographyParameters<Community>(new SimpleAdder<Community>()));
			String communityFile = gisDataDir + getProperty(GlobalVars.CommunityShapefile);
			GISFunctions.readShapefile(Community.class, communityFile, communityProjection, communityContext);
			mainContext.addSubContext(communityContext);
			SpatialIndexManager.createIndex(communityProjection, Community.class);

			// Tell the buildings and communities about each other (not sure if it's quicker to search communities
			// for buildings or the other way round
			for (Community c : communityContext.getObjects(Community.class)) {
				Geometry comGeom = ContextManager.communityProjection.getGeometry(c);
				for (Building b : SpatialIndexManager.search(ContextManager.buildingProjection, comGeom)) {
					// Examine geographies to see which buildings are actually within the community. Note:
					// 'intersect' is used here because sometimes community boundaries cut through buildings.
					// Hopefully this doesn't introduce much error.
					if (ContextManager.buildingProjection.getGeometry(b).intersects(comGeom)) {
						b.setCommunity(c);
						c.addBuilding(b);
					}
				} // for buildings
			} // for communities
				// Check all buildings have a community
			for (Building b : ContextManager.buildingContext.getObjects(Building.class)) {
				if (b.getCommunity() == null) {
					throw new EnvironmentError("The building " + b.toString() + " does not have a community.");
				}
			}
			// // Some communities might not have buildings in them, so the check below is incorrect
			// for (Community c:ContextManager.communityContext.getObjects(Community.class)) {
			// if (c.getBuildings()==null || c.getBuildings().size()==0) {
			// throw new EnvironmentError("The community "+c.toString()+" does not have any buildings.");
			// }
			// }

			LOGGER.log(Level.FINER, "Read " + communityContext.getObjects(Community.class).size()
					+ " communities from " + communityFile);

			// Create road network

			// 1.junctionContext and junctionGeography
			junctionContext = new JunctionContext();
			mainContext.addSubContext(junctionContext);
			junctionGeography = GeographyFactoryFinder.createGeographyFactory(null).createGeography(
					GlobalVars.CONTEXT_NAMES.JUNCTION_GEOGRAPHY, junctionContext,
					new GeographyParameters<Junction>(new SimpleAdder<Junction>()));

			// 2. roadNetwork
			NetworkBuilder<Junction> builder = new NetworkBuilder<Junction>(GlobalVars.CONTEXT_NAMES.ROAD_NETWORK,
					junctionContext, false);
			builder.setEdgeCreator(new NetworkEdgeCreator<Junction>());
			roadNetwork = builder.buildNetwork();
			GISFunctions.buildGISRoadNetwork(roadProjection, junctionContext, junctionGeography, roadNetwork);

			// Add the junctions to a spatial index (couldn't do this until the road network had been created).
			SpatialIndexManager.createIndex(junctionGeography, Junction.class);

			testEnvironment();

		} catch (MalformedURLException e) {
			LOGGER.log(Level.SEVERE, "", e);
			return null;
		} catch (EnvironmentError e) {
			LOGGER.log(Level.SEVERE, "There is an eror with the environment, cannot start simulation", e);
			return null;
		} catch (NoIdentifierException e) {
			LOGGER.log(Level.SEVERE, "One of the input buildings had no identifier (this should be read"
					+ "from the 'identifier' column in an input GIS file)", e);
			return null;
		} catch (FileNotFoundException e) {
			LOGGER.log(Level.SEVERE, "Could not find an input shapefile to read objects from.", e);
			return null;
		}

		// Now create the agents (note that their step methods are scheduled later)
		try {

			agentContext = new AgentContext();
			mainContext.addSubContext(agentContext);
			agentGeography = GeographyFactoryFinder.createGeographyFactory(null).createGeography(
					GlobalVars.CONTEXT_NAMES.AGENT_GEOGRAPHY, agentContext,
					new GeographyParameters<IAgent>(new SimpleAdder<IAgent>()));

			// String agentDefn = ContextManager.getParameter(MODEL_PARAMETERS.AGENT_DEFINITION.toString());
			String agentDefn = getProperty(GlobalVars.AGENT_DEFINITION);
			LOGGER.log(Level.INFO, "Creating agents with the agent definition: '" + agentDefn + "'");

			AgentFactory agentFactory = new AgentFactory(agentDefn);
			agentFactory.createAgents(agentContext);

			// } catch (ParameterNotFoundException e) {
			// LOGGER.log(Level.SEVERE, "Could not find the parameter which defines how agents should be "
			// + "created. The parameter is called " + MODEL_PARAMETERS.AGENT_DEFINITION
			// + " and should be added to the parameters.xml file.", e);
			// return null;
		} catch (AgentCreationException e) {
			LOGGER.log(Level.SEVERE, "", e);
			return null;
		}

		// Initialise the object that holds the weights applied to agent's burglary decisions. This is the
		// default one and can be changed.
		ContextManager.setBurglaryWeights(new BurglaryWeights());

		// Create the schedule
		createSchedule();

		return mainContext;
	}

	private void createSchedule() {
		ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();

		// Schedule something that outputs ticks every 1000 iterations.
		schedule.schedule(ScheduleParameters.createRepeating(1, 1000, ScheduleParameters.LAST_PRIORITY), this,
				"printTicks");

		// Schedule the time keeper (maintains a proper clock)
		schedule.schedule(ScheduleParameters.createRepeating(1, 1, ScheduleParameters.LAST_PRIORITY), this,
				"updateRealTime");

		// Schedule a method to run at the end and generate some results
		schedule.schedule(ScheduleParameters.createAtEnd(ScheduleParameters.LAST_PRIORITY), ContextManager.results, "generateResults");

		/*
		 * Schedule the agents. This is slightly complicated because if all the agents can be stepped at the same time
		 * (i.e. there are no inter- agent communications that make this difficult) then the scheduling is controlled by
		 * a separate function that steps them in different threads. This massively improves performance on multi-core
		 * machines.
		 */
		boolean isThreadable = true;
		for (IAgent a : agentContext.getObjects(IAgent.class)) {
			if (!a.isThreadable()) {
				isThreadable = false;
				break;
			}
		}

		if (ContextManager.TURN_OFF_THREADING) { // Overide threading?
			isThreadable = false;
		}
		if (isThreadable && (Runtime.getRuntime().availableProcessors() > 1)) {
			/*
			 * Agents can be threaded so the step scheduling not actually done by repast scheduler, a method in
			 * ThreadedAgentScheduler is called which manually steps each agent.
			 */
			LOGGER.log(Level.INFO, "The multi-threaded scheduler will be used. There are "
					+ Runtime.getRuntime().availableProcessors() + " processors.");
			ThreadedAgentScheduler s = new ThreadedAgentScheduler();
			ScheduleParameters agentStepParams = ScheduleParameters.createRepeating(1, 1, 0);
			schedule.schedule(agentStepParams, s, "agentStep");
		} else { // Agents will execute in serial, use the repast scheduler.
			LOGGER.log(Level.INFO, "The single-threaded scheduler will be used.");
			ScheduleParameters agentStepParams = ScheduleParameters.createRepeating(1, 1, 0);
			// Schedule the agents' step methods.
			for (IAgent a : agentContext.getObjects(IAgent.class)) {
				schedule.schedule(agentStepParams, a, "step");
			}
		}

	}

	// /** Stops the simulation. Attempts to call stop methods for GUI and if model is running programatically
	// * or on NGS */
	// public static void haltSim() {
	// RunEnvironment.getInstance().endRun(); // stop sim us using gui
	// RepastCityMain.stopSim(); // stop sim if running programatically
	// //RepastCityMainMPJ.stopSim(); // stop sim if on NGS
	// }



	/*
	 * For creating a clock: A variable to represent the real time in decimal hours (e.g. 14.5 means 2:30pm) and a
	 * method, called at every iteration, to update the variable.
	 */
	public static double realTime = 8.0; // (start at 8am)
	public static int numberOfDays = 0; // It is also useful to count the number of days
	
	public void updateRealTime() {
		// System.out.println("Time: "+RunEnvironment.getInstance().getCurrentSchedule().getTickCount()+", "+realTime);
		realTime += (1.0 / 60.0); // Increase the time by one minute (a 60th of an hour)

		if (realTime >= 24.0) { // If it's the end of a day then reset the time
			realTime = 0.0;
			numberOfDays++; // Also increment our day counter
			// Print some information about memory, time taken, etc.
			LOGGER.log(Level.INFO, "Simulating day " + numberOfDays);
		}
	}
	

	/*
	 * Convenience methods for getting random numbers, using RandomHelper directly is not thread safe.
	 */
	private static Object randomLock = new Object(); // Lock to ensure only one thread accesses RandomHelper

	public static int nextIntFromTo(int a, int b) {
		synchronized (ContextManager.randomLock) {
			// This synchronized block ensures that only one agent at a time can access RandomHelper
			return RandomHelper.nextIntFromTo(a, b);
		}
	}

	public static double nextDouble() {
		synchronized (ContextManager.randomLock) {
			// This synchronized block ensures that only one agent at a time can access RandomHelper
			return RandomHelper.nextDouble();
		}
	}

	public static double nextDoubleFromTo(double a, double b) {
		synchronized (ContextManager.randomLock) {
			// This synchronized block ensures that only one agent at a time can access RandomHelper
			return RandomHelper.nextDoubleFromTo(a, b);
		}
	}

	/**
	 * Useful method that returns a random element from the list, using the <code>RandomHelper</code> to choose.
	 * 
	 * @param l
	 *            The list from which to return an object
	 * @see RandomHelper
	 */
	public static <T> T randomChoice(List<T> l) {
		synchronized (ContextManager.randomLock) {
			// This synchronized block ensures that only one agent at a time can access RandomHelper
			return l.get(RandomHelper.nextIntFromTo(0, l.size() - 1));
		}
	}

	private static long speedTimer = -1; // For recording time per N iterations
	DecimalFormat memFormatter = new DecimalFormat("###,###"); // For printing memory nicely
	public void printTicks() {
		LOGGER.info("Iterations: " + RunEnvironment.getInstance().getCurrentSchedule().getTickCount() + ". Speed: "
				+ ((double) (System.currentTimeMillis() - ContextManager.speedTimer) / 1000.0) + "sec/ticks."
				+ ". Memory (used/max/free): "+
					memFormatter.format((int)Runtime.getRuntime().totalMemory()/1000000)+"Mb/"+
					memFormatter.format((int)Runtime.getRuntime().maxMemory()/1000000)+"Mb/"+
					memFormatter.format((int)Runtime.getRuntime().freeMemory()/1000000)+"Mb)."
				);
		ContextManager.speedTimer = System.currentTimeMillis();
	}

	// Have removed this because no longer using Simphony parameters. They don't work when running the simulation
	// from the command line (!)
	// /**
	// * Convenience function to get a Simphony parameter
	// *
	// * @param <T>
	// * The type of the parameter
	// * @param paramName
	// * The name of the parameter
	// * @return The parameter.
	// * @throws ParameterNotFoundException
	// * If the parameter could not be found.
	// */
	// public static <V> V getParameter(String paramName) throws ParameterNotFoundException {
	// Parameters p = RunEnvironment.getInstance().getParameters();
	// Object val = p.getValue(paramName);
	//
	// if (val == null) {
	// throw new ParameterNotFoundException(paramName);
	// }
	//
	// // Try to cast the value and return it
	// @SuppressWarnings("unchecked")
	// V value = (V) val;
	// return value;
	// }

	/**
	 * Get the value of a property in the properties file. If the input is empty or null or if there is no property with
	 * a matching name, throw a RuntimeException.
	 * 
	 * @param property
	 *            The property to look for.
	 * @return A value for the property with the given name.
	 */
	public static String getProperty(String property) {
		if (property == null || property.equals("")) {
			throw new RuntimeException("getProperty() error, input parameter (" + property + ") is "
					+ (property == null ? "null" : "empty"));
		} else {
			String val = ContextManager.properties.getProperty(property);
			if (val == null || val.equals("")) { // No value exists in the
													// properties file
				throw new RuntimeException("checkProperty() error, the required property (" + property + ") is "
						+ (property == null ? "null" : "empty"));
			}
			return val;
		}
	}

	/**
	 * Read the properties file and add properties. Will check if any properties have been included on the command line
	 * as well as in the properties file, in these cases the entries in the properties file are ignored in preference
	 * for those specified on the command line.
	 * 
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private static void readProperties() throws FileNotFoundException, IOException {

		File propFile = new File("./repastcity.properties");
		if (!propFile.exists()) {
			throw new FileNotFoundException("Could not find properties file in the default location: "
					+ propFile.getAbsolutePath());
		}

		LOGGER.log(Level.FINE, "Initialising properties from file " + propFile.toString());

		ContextManager.properties = new Properties();

		FileInputStream in = new FileInputStream(propFile.getAbsolutePath());
		ContextManager.properties.load(in);
		in.close();

		// See if any properties are being overridden by command-line arguments
		for (Enumeration<?> e = properties.propertyNames(); e.hasMoreElements();) {
			String k = (String) e.nextElement();
			String newVal = System.getProperty(k);
			if (newVal != null) {
				// The system property has the same name as the one from the
				// properties file, replace the one in the properties file.
				LOGGER.log(Level.INFO, "Found a system property '" + k + "->" + newVal
						+ "' which matches a NeissModel property '" + k + "->" + properties.getProperty(k)
						+ "', replacing the non-system one.");
				properties.setProperty(k, newVal);
			}
		} // for
		return;
	} // readProperties

	/**
	 * Check that the environment looks ok
	 * 
	 * @throws NoIdentifierException
	 */
	@SuppressWarnings("unchecked")
	private void testEnvironment() throws EnvironmentError, NoIdentifierException {

		LOGGER.log(Level.FINE, "Testing the environment");
		// Get copies of the contexts/projections from main context
		Context<Building> bc = (Context<Building>) mainContext.getSubContext(GlobalVars.CONTEXT_NAMES.BUILDING_CONTEXT);
		Context<Road> rc = (Context<Road>) mainContext.getSubContext(GlobalVars.CONTEXT_NAMES.ROAD_CONTEXT);
		Context<Junction> jc = (Context<Junction>) mainContext.getSubContext(GlobalVars.CONTEXT_NAMES.JUNCTION_CONTEXT);

		// Geography<Building> bg = (Geography<Building>)
		// bc.getProjection(GlobalVars.CONTEXT_NAMES.BUILDING_GEOGRAPHY);
		// Geography<Road> rg = (Geography<Road>)
		// rc.getProjection(GlobalVars.CONTEXT_NAMES.ROAD_GEOGRAPHY);
		// Geography<Junction> jg = (Geography<Junction>)
		// rc.getProjection(GlobalVars.CONTEXT_NAMES.JUNCTION_GEOGRAPHY);
		Network<Junction> rn = (Network<Junction>) jc.getProjection(GlobalVars.CONTEXT_NAMES.ROAD_NETWORK);

		// 1. Check that there are some objects in each of the contexts
		checkSize(bc, rc, jc);

		// 2. Check that the number of roads matches the number of edges
		if (sizeOfIterable(rc.getObjects(Road.class)) != sizeOfIterable(rn.getEdges())) {
			StringBuilder errormsg = new StringBuilder();
			errormsg.append("There should be equal numbers of roads in the road context and edges in the "
					+ "road network. But there are " + sizeOfIterable(rc.getObjects(Road.class)) + "roads and "
					+ sizeOfIterable(rn.getEdges()) + " edges. ");

			// If there are more edges than roads then something is pretty weird.
			if (sizeOfIterable(rc.getObjects(Road.class)) < sizeOfIterable(rn.getEdges())) {
				errormsg.append("There are more edges than roads, no idea how this could happen.");
				throw new EnvironmentError(errormsg.toString());
			} else { // Fewer edges than roads, try to work out which roads do not have associated edges.
				/*
				 * This can be caused when two roads connect the same two junctions and can be fixed by splitting one of
				 * the two roads so that no two roads will have the same source/destination junctions ("e.g. see here
				 * http://webhelp.esri.com/arcgisdesktop/9.2/index.cfm?TopicName=Splitting_line_features), or by
				 * deleting them. The logger should print a list of all roads that don't have matching edges below.
				 */
				HashSet<Road> roads = new HashSet<Road>();
				for (Road r : rc.getObjects(Road.class)) {
					roads.add(r);
				}
				for (RepastEdge<Junction> re : rn.getEdges()) {
					NetworkEdge<Junction> e = (NetworkEdge<Junction>) re;
					roads.remove(e.getRoad());
				}
				// Log this info (also print the list of roads in a format that is good for ArcGIS searches.
				String er = errormsg.toString() + "The " + roads.size()
						+ " roads that do not have associated edges are: " + roads.toString()
						+ "\nHere is a list of roads in a format that copied into AcrGIS for searching:\n";
				for (Road r : roads) {
					er += ("\"identifier\"= '" + r.getIdentifier() + "' Or ");
				}
				LOGGER.log(Level.SEVERE, er);
				throw new EnvironmentError(errormsg.append("See previous log messages for debugging info.").toString());
			}

		}

		// 3. Check that the number of junctions matches the number of nodes
		if (sizeOfIterable(jc.getObjects(Junction.class)) != sizeOfIterable(rn.getNodes())) {
			throw new EnvironmentError("There should be equal numbers of junctions in the junction "
					+ "context and nodes in the road network. But there are "
					+ sizeOfIterable(jc.getObjects(Junction.class)) + " and " + sizeOfIterable(rn.getNodes()));
		}

		LOGGER.log(Level.FINE, "The road network has " + sizeOfIterable(rn.getNodes()) + " nodes and "
				+ sizeOfIterable(rn.getEdges()) + " edges.");

		// 4. Check that Roads and Buildings have unique identifiers
		HashMap<String, ?> idList = new HashMap<String, Object>();
		for (Building b : bc.getObjects(Building.class)) {
			if (idList.containsKey(b.getIdentifier()))
				throw new EnvironmentError("More than one building found with id " + b.getIdentifier());
			idList.put(b.getIdentifier(), null);
		}
		idList.clear();
		for (Road r : rc.getObjects(Road.class)) {
			if (idList.containsKey(r.getIdentifier()))
				throw new EnvironmentError("More than one building found with id " + r.getIdentifier());
			idList.put(r.getIdentifier(), null);
		}

	}

	public static int sizeOfIterable(Iterable<?> i) {
		int size = 0;
		Iterator<?> it = i.iterator();
		while (it.hasNext()) {
			size++;
			it.next();
		}
		return size;
	}

	/**
	 * Get the results object that has been organising the results of this simulation.
	 * @return
	 */
	public static Results getResults() {
		return ContextManager.results;
	}
	
	/**
	 * Checks that the given <code>Context</code>s have more than zero objects in them
	 * 
	 * @param contexts
	 * @throws EnvironmentError
	 */
	public void checkSize(Context<?>... contexts) throws EnvironmentError {
		for (Context<?> c : contexts) {
			int numObjs = sizeOfIterable(c.getObjects(Object.class));
			if (numObjs == 0) {
				throw new EnvironmentError("There are no objects in the context: " + c.getId().toString());
			}
		}
	}

	public static void stopSim(Exception ex, Class<?> clazz) {
		ISchedule sched = RunEnvironment.getInstance().getCurrentSchedule();
		sched.setFinishing(true);
		sched.executeEndActions();
		LOGGER.log(Level.SEVERE, "ContextManager has been told to stop by " + clazz.getName(), ex);
	}

	public static void setBurglaryWeights(BurglaryWeights bw) {
		ContextManager.burglaryWeights = bw;
	}

	/** Get the object that maintains the weights used in agent's burglary decisions */
	public static BurglaryWeights getBurglaryWeights() {
		return ContextManager.burglaryWeights;
	}

	/**
	 * Move an agent by a vector. This method is required -- rather than giving agents direct access to the
	 * agentGeography -- because when multiple threads are used they can interfere with each other and agents end up
	 * moving incorrectly.
	 * 
	 * @param agent
	 *            The agent to move.
	 * @param distToTravel
	 *            The distance that they will travel
	 * @param angle
	 *            The angle at which to travel.
	 * @see Geography
	 */
	public static synchronized void moveAgentByVector(IAgent agent, double distToTravel, double angle) {
		ContextManager.agentGeography.moveByVector(agent, distToTravel, angle);
	}

	/**
	 * Move an agent. This method is required -- rather than giving agents direct access to the agentGeography --
	 * because when multiple threads are used they can interfere with each other and agents end up moving incorrectly.
	 * 
	 * @param agent
	 *            The agent to move.
	 * @param point
	 *            The point to move the agent to
	 */
	public static synchronized void moveAgent(IAgent agent, Point point) {
		ContextManager.agentGeography.move(agent, point);
	}

	/**
	 * Add an agent to the agent context. This method is required -- rather than giving agents direct access to the
	 * agentGeography -- because when multiple threads are used they can interfere with each other and agents end up
	 * moving incorrectly.
	 * 
	 * @param agent
	 *            The agent to add.
	 */
	public static synchronized void addAgentToContext(IAgent agent) {
		ContextManager.agentContext.add(agent);
	}

	/**
	 * Get all the agents in the agent context. This method is required -- rather than giving agents direct access to
	 * the agentGeography -- because when multiple threads are used they can interfere with each other and agents end up
	 * moving incorrectly.
	 * 
	 * @return An iterable over all agents, chosen in a random order. See the <code>getRandomObjects</code> function in
	 *         <code>DefaultContext</code>
	 * @see DefaultContext
	 */
	public static synchronized Iterable<IAgent> getAllAgents() {
		return ContextManager.agentContext.getRandomObjects(IAgent.class, ContextManager.agentContext.size());
	}

	/**
	 * Get the geometry of the given agent. This method is required -- rather than giving agents direct access to the
	 * agentGeography -- because when multiple threads are used they can interfere with each other and agents end up
	 * moving incorrectly.
	 */
	public static synchronized Geometry getAgentGeometry(IAgent agent) {
		return ContextManager.agentGeography.getGeometry(agent);
	}

	/**
	 * Get a pointer to the agent context.
	 * 
	 * <p>
	 * Warning: accessing the context directly is not thread safe so this should be used with care. The functions
	 * <code>getAllAgents()</code> and <code>getAgentGeometry()</code> can be used to query the agent context or
	 * projection.
	 * </p>
	 */
	public static Context<IAgent> getAgentContext() {
		return ContextManager.agentContext;
	}

	/**
	 * Get a pointer to the agent geography.
	 * 
	 * <p>
	 * Warning: accessing the context directly is not thread safe so this should be used with care. The functions
	 * <code>getAllAgents()</code> and <code>getAgentGeometry()</code> can be used to query the agent context or
	 * projection.
	 * </p>
	 */
	public static Geography<IAgent> getAgentGeography() {
		return ContextManager.agentGeography;
	}

}
