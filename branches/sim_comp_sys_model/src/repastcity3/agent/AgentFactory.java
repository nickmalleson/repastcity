/*©Copyright 2012 Nick Malleson
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
along with RepastCity.  If not, see <http://www.gnu.org/licenses/>.*/

package repastcity3.agent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.logging.Logger;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.operation.distance.DistanceOp;

import repast.simphony.context.Context;
import repastcity3.environment.Building;
import repastcity3.environment.Community;
import repastcity3.environment.GISFunctions;
import repastcity3.environment.SpatialIndexManager;
import repastcity3.exceptions.AgentCreationException;
import repastcity3.main.ContextManager;
import repastcity3.main.GlobalVars;

/**
 * Create agents. There are three methods that can be used to create agents: randomly create a number of agents, create
 * agents from a point shapefile or create a certain number of agents per neighbourhood specified in an area shapefile.
 * 
 * <P>
 * The method to use is specified by the 'agent_definition' parameter in <code>parameters.xml</code>. The parameter
 * takes the following form:
 * </P>
 * 
 * <pre>
 * {@code
 * <method>:<definition>
 * }
 * </pre>
 * 
 * <P>
 * where method and can be one of the following:
 * </P>
 * 
 * <ul>
 * <li>
 * 
 * <pre>
 * {@code random:<num_agents>}
 * </pre>
 * 
 * Create 'num_agents' agents in randomly chosen houses. The agents are of type <code>DefaultAgent</code>. For example,
 * this will create 10 agents in randomly chosen houses: '<code>random:1</code>'. See the
 * <code>createRandomAgents</code> function for implementation details.</li>
 * 
 * <li>
 * 
 * <pre>
 * {@code point:<filename>%<agent_class>}
 * </pre>
 * 
 * Create agents from the given point shapefile (one agent per point). If a point in the agent shapefile is within a
 * building object then the agent's home will be set to that building, otherwise the agent will be snapped to the 
 * nearest building. The type of the agent can be given in two ways:
 * <ol>
 * <li>The 'agent_class' parameter can be used - this is the fully qualified (e.g. including package) name of a class
 * that will be used to create all the agents. For example the following will create instances of <code>MyAgent</code>
 * at each point in the shapefile '<code>point:data/my_shapefile.shp$my_package.agents.MyAgent</code>'.</li>
 * <li>A String column in the input shapefile called 'agent_type' provides the class of the agents. IIn this manner
 * agents of different types can be created from the same input. For example, the following will read the shapefile and
 * look at the values in the 'agent_type' column to create agents: '<code>point:data/my_shapefile.shp</code>' (note that
 * unlike the previous method there is no '$').</li>
 * </ol>
 * 
 * See the <code>createPointAgents</code> function for implementation details.
 * 
 * <li>
 * 
 * <pre>
 * {@code area:<filename>$BglrC1%<agent_class1>$ .. $BglrC5%<agent_class5>}
 * </pre>
 * 
 * Create agents from the given areas shapefile. Up to five different types of agents can be created. Columns in the
 * shapefile specify how many agents of each type to create per area and the agents created are randomly assigned to
 * houses withing their area. The columns names must follow the format 'BglrCX' where 1 <= X <= 5. For example the
 * following string:<br>
 * 
 * <pre>
 * {@code area:area.shp$BglrC1%BurglarAgent$BglrC2%EmployedAgent}
 * </pre>
 * 
 * will read the <code>area.shp</code> and, for each area, create a number of <code>BurglarAgent</code> and
 * <code>EmployedAgent</code> agents in each area, the number being specied by columns called <code>BglrC1</code> and
 * <code>BglrC2</code> respectively. See the <code>createAreaAgents</code> function for implementation details.</li>
 * </ul>
 * 
 * @author Nick Malleson
 * @see DefaultAgent
 */
public class AgentFactory {

	private static Logger LOGGER = Logger.getLogger(AgentFactory.class.getName());

	/** The method to use when creating agents (determined in constructor). */
	private AGENT_FACTORY_METHODS methodToUse;

	/** The definition of the agents - specific to the method being used */
	private String definition;

	/**
	 * Create a new agent factory from the given definition.
	 * 
	 * @param agentDefinition
	 */
	public AgentFactory(String agentDefinition) throws AgentCreationException {

		// First try to parse the definition
		String[] split = agentDefinition.split(":");
		if (split.length != 2) {
			throw new AgentCreationException("Problem parsin the definition string '" + agentDefinition
					+ "': it split into " + split.length + " parts but should split into 2.");
		}
		String method = split[0]; // The method to create agents
		String defn = split[1]; // Information about the agents themselves

		if (method.equals(AGENT_FACTORY_METHODS.RANDOM.toString())) {
			this.methodToUse = AGENT_FACTORY_METHODS.RANDOM;

		} else if (method.equals(AGENT_FACTORY_METHODS.POINT_FILE.toString())) {
			this.methodToUse = AGENT_FACTORY_METHODS.POINT_FILE;
		}

		else if (method.equals(AGENT_FACTORY_METHODS.AREA_FILE.toString())) {
			this.methodToUse = AGENT_FACTORY_METHODS.AREA_FILE;
		}

		else {
			throw new AgentCreationException("Unrecognised method of creating agents: '" + method
					+ "'. Method must be one of " + AGENT_FACTORY_METHODS.RANDOM.toString() + ", "
					+ AGENT_FACTORY_METHODS.POINT_FILE.toString() + " or " + AGENT_FACTORY_METHODS.AREA_FILE.toString());
		}

		this.definition = defn; // Method is OK, save the definition for creating agents later.

		// Check the rest of the definition is also correct (passing false means don't create agents)
		// An exception will be thrown if it doesn't work.
		this.methodToUse.createAgMeth().createagents(false, this);
	}

	public void createAgents(Context<? extends IAgent> context) throws AgentCreationException {
		this.methodToUse.createAgMeth().createagents(true, this);
	}

	/**
	 * Create a number of in randomly chosen houses. If there are more agents than houses then some houses will have
	 * more than one agent in them.
	 * 
	 * @param dummy
	 *            Whether or not to actually create agents. If this is false then just check that the definition can be
	 *            parsed.
	 * @throws AgentCreationException
	 */
	private void createRandomAgents(boolean dummy) throws AgentCreationException {
		// Check the definition is as expected, in this case it should be a number
		int numAgents = -1;
		try {
			numAgents = Integer.parseInt(this.definition);
		} catch (NumberFormatException ex) {
			throw new AgentCreationException("Using " + this.methodToUse + " method to create "
					+ "agents but cannot convert " + this.definition + " into an integer.");
		}
		// The definition has been parsed OK, no can either stop or create the agents
		if (dummy) {
			return;
		}

		// Create agents in randomly chosen houses. Use two while loops in case there are more agents
		// than houses, so that houses have to be looped over twice.
		LOGGER.info("Creating " + numAgents + " agents using " + this.methodToUse + " method.");
		int agentsCreated = 0;
		while (agentsCreated < numAgents) {
			Iterator<Building> i = ContextManager.buildingContext.getRandomObjects(Building.class, numAgents)
					.iterator();
			while (i.hasNext() && agentsCreated < numAgents) {
				Building b = i.next(); // Find a building
				IAgent a = new DefaultAgent(); // Create a new agent
				a.setHome(b); // Tell the agent where it lives
				b.addAgent(a); // Tell the building that the agent lives there
				AgentFactory.setCommunity(a); // Tell the agent in which Community it lives
				ContextManager.addAgentToContext(a); // Add the agent to the context
				// Finally move the agent to the place where it lives.
				ContextManager.moveAgent(a, ContextManager.buildingProjection.getGeometry(b).getCentroid());
				agentsCreated++;
			}
		}
	}

	/**
	 * Read a shapefile and create an agent at each location. If there is a column called
	 * 
	 * @param dummy
	 *            Whether or not to actually create agents. If this is false then just check that the definition can be
	 *            parsed.
	 * @throws AgentCreationException
	 */
	@SuppressWarnings("unchecked")
	private void createPointAgents(boolean dummy) throws AgentCreationException {

		// See if there is a single type of agent to create or should read a colum in shapefile
		boolean singleType = this.definition.contains("$");

		String fileName;
		String className;
		Class<IAgent> clazz;
		if (singleType) {
			// Agent class provided, can use the Simphony Shapefile loader to load agents of the given class

			// Work out the file and class names from the agent definition
			String[] split = this.definition.split("\\$");
			if (split.length != 2) {
				throw new AgentCreationException("There is a problem with the agent definition, I should be "
						+ "able to split the definition into two parts on '$', but only split it into " + split.length
						+ ". The definition is: '" + this.definition + "'");
			}
			 // (Need to append root data directory to the filename).
			fileName = ContextManager.getProperty(GlobalVars.GISDataDirectory)+split[0];
			className = split[1];
			// Try to create a class from the given name.
			try {
				clazz = (Class<IAgent>) Class.forName(className);
				GISFunctions.readAgentShapefile(clazz, fileName, ContextManager.getAgentGeography(), ContextManager
						.getAgentContext());
			} catch (Exception e) {
				throw new AgentCreationException(e);
			}
		} else {
			// TODO Implement agent creation from shapefile value;
			throw new AgentCreationException("Have not implemented the method of reading agent classes from a "
					+ "shapefile yet.");
		}

		// Assign agents to houses
		int numAgents = 0;
		for (IAgent agent : ContextManager.getAllAgents()) {
			numAgents++;
			boolean foundHouse = false;
			Geometry agentGeom = ContextManager.getAgentGeometry(agent);
			for (Building b : SpatialIndexManager.search(ContextManager.buildingProjection, agentGeom)) {
				if (ContextManager.buildingProjection.getGeometry(b).contains(agentGeom)) { // See if agent is within the building
					b.addAgent(agent);
					agent.setHome(b);
					AgentFactory.setCommunity(agent); // Tell the agent in which Community it lives
					foundHouse = true;
					break;
				} // if
			} // for buildings
			if (!foundHouse) { // Agent wasn't within a house, find the nearest non-occupied house
				Map<Building, Double> houseDists = new HashMap<Building, Double>();
				for (Building b:SpatialIndexManager.search(ContextManager.buildingProjection, agentGeom)){
					double dist = DistanceOp.distance(agentGeom, ContextManager.buildingProjection.getGeometry(b));
					houseDists.put(b, dist);
				}
				// Sory the buildings by distance (the map value)
				ValueComparator bvc =  new ValueComparator(houseDists);
		        @SuppressWarnings("rawtypes") // ?
				TreeMap<Building,Double> sorted_map = new TreeMap(bvc);
		        sorted_map.putAll(houseDists);
		        System.out.println("XXXX "+agent.toString()+" - "+sorted_map.toString());
		        System.out.println("YYYY "+SpatialIndexManager.search(ContextManager.buildingProjection, agentGeom).toString());
		        // GO through the sorted map looking for unoccuppied houses
		        for (Entry<Building, Double> entry:sorted_map.entrySet()) {
		        	Building b = entry.getKey(); 
		        	if (b.getAgents().size() == 0) {
						b.addAgent(agent);
						agent.setHome(b);
						ContextManager.agentGeography.move(agent, ContextManager.buildingProjection.getGeometry(b));
						AgentFactory.setCommunity(agent); // Tell the agent in which Community it lives
						foundHouse = true;
						break;
		        	} // if no agents
		        } // for buildings
		        if (!foundHouse) { // Still no house for the agent!
					throw new AgentCreationException("Could not find an unoccuppied house for agent "+agent.toString()+
							". The houses (and their distances from the agent) were: "+sorted_map.toString());
		        }
			} // if !foundHouse
		} // for agents

		if (singleType) {
			LOGGER.info("Have created " + numAgents + " of type " + clazz.getName().toString() + " from file "
					+ fileName);
		} else {
			// (NOTE: at the moment this will never happen because not implemented yet.)
			LOGGER.info("Have created " + numAgents + " of different types from file " + fileName);
		}

	}

	private void createAreaAgents(boolean dummy) throws AgentCreationException {
		throw new AgentCreationException("Have not implemented the createAreaAgents method yet.");
	}
	
	/* Convenience method to find the community an agent lives in in and store this information. Assumes the agent's Home
	 * Building has already been set. Returns the Community as well. */
	private static Community setCommunity (IAgent a) throws AgentCreationException {
		Community c;
		try {
			c = SpatialIndexManager.findObjectAt(ContextManager.communityProjection, a.getHome().getCoords(), 
					GlobalVars.GEOGRAPHY_PARAMS.BUFFER_DISTANCE.SMALL);
		} catch (NoSuchElementException e) {
			throw new AgentCreationException(e);
		} catch (Exception e) {
			throw new AgentCreationException(e);
		}
		a.setHomeCommunity(c);
		return c;
	}

	/**
	 * The methods that can be used to create agents. The CreateAgentMethod stuff is just a long-winded way of
	 * hard-coding the specific method to use for creating agents into the enum (much simpler in python).
	 * 
	 * @author Nick Malleson
	 */
	private enum AGENT_FACTORY_METHODS {
		/** Default: create a number of agents randomly assigned to buildings */
		RANDOM("random", new CreateAgentMethod() {
			@Override
			public void createagents(boolean b, AgentFactory af) throws AgentCreationException {
				af.createRandomAgents(b);
			}
		}),
		/** Specify an agent shapefile, one agent will be created per point */
		POINT_FILE("point", new CreateAgentMethod() {
			@Override
			public void createagents(boolean b, AgentFactory af) throws AgentCreationException {
				af.createPointAgents(b);
			}
		}),
		/**
		 * Specify the number of agents per area as a shaefile. Agents will be randomly assigned to houses within the
		 * area.
		 */
		AREA_FILE("area", new CreateAgentMethod() {
			@Override
			public void createagents(boolean b, AgentFactory af) throws AgentCreationException {
				af.createAreaAgents(b);
			}
		});

		String stringVal;
		CreateAgentMethod meth;

		/**
		 * @param val
		 *            The string representation of the enum which must match the method given in the 'agent_definition'
		 *            parameter in parameters.xml.
		 * @param f
		 */
		AGENT_FACTORY_METHODS(String val, CreateAgentMethod f) {
			this.stringVal = val;
			this.meth = f;
		}

		public String toString() {
			return this.stringVal;
		}

		public CreateAgentMethod createAgMeth() {
			return this.meth;
		}

		interface CreateAgentMethod {
			void createagents(boolean dummy, AgentFactory af) throws AgentCreationException;
		}
	}

	/**
	 * For comparing values of map elements. From
	 * http://stackoverflow.com/questions/109383/how-to-sort-a-mapkey-value-on-the-values-in-java
	 */
	class ValueComparator implements Comparator<Double> {

		Map<?, Double> base;

		public ValueComparator(Map<?, Double> base) {
			this.base = base;
		}

		public int compare(Double a, Double b) {

			if (base.get(a) < base.get(b)) {
				return 1;
			} else if (base.get(a) == base.get(b)) {
				return 0;
			} else {
				return -1;
			}

			/*
			 * or like this tested
			 * 
			 * return ((Double)base.get(a)).compareTo((Double)base.get(b));
			 */

		}
	} // ValueComparator class

}
