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

You should have received a copy of  the GNU General Public License
along with RepastCity.  If not, see <http://www.gnu.org/licenses/>.
 */

package repastcity3.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import repastcity3.environment.Building;
import repastcity3.environment.Route;
import repastcity3.main.ContextManager;

// TODO: rename to 'DumbBurglar' (and also change BurglarFactory because random method only creates DefaultAgent..)
public class DefaultAgent implements IAgent {

	private static Logger LOGGER = Logger.getLogger(DefaultAgent.class.getName());

	private Building home; // Where the agent lives
	private Route route; // An object to move the agent around the world
	 
	// Need to keep track of current day to make sure the agent burgle (leave home) once per day
	// If this is less than the day counter then the agent hasn't burgled
	private int currentDay = -1;  
	
	private Building targetArea = null; // SHOULD BE OA / Community

	private static int uniqueID = 0;
	private int id;

	/** The state of the agent */
	enum AGENT_STATE {
		AT_HOME, // Agent is at home
		TRAVELLING_HOME, // Agent is travelling home
		// The following are for when the agent is committing burglary:
		BURGLARY_INIT, // Just created or agent couldn't find a victim, start again
		TRAVEL_TO_TARGET, // Agent is travelling to their chosen target
		SEARCHING;// Agent is searching for a victim.
	}

	private AGENT_STATE currentState = AGENT_STATE.AT_HOME;

	public DefaultAgent() {
		this.id = uniqueID++;
	}

	@Override
	public void step() throws Exception {

		LOGGER.log(Level.FINE, "Agent " + this.id + " is stepping. State: " + this.currentState.toString());

		switch (this.currentState) {
		case AT_HOME:
			if (ContextManager.realTime >= 9.0 && this.currentDay < ContextManager.numberOfDays) {
				// It's after 9am and the agent hasn't left home yet, so choose somewhere to burgle
				this.currentState = AGENT_STATE.BURGLARY_INIT;
				this.currentDay = ContextManager.numberOfDays; // This will stop the agent burgling again today.
			}
			break;
			
		case TRAVELLING_HOME:
			
			if (this.route.atDestination()) {
				this.currentState = AGENT_STATE.AT_HOME; 
				this.route = null;
			}
			else {
				this.route.travel(null);
			}
			break;				

			
		case BURGLARY_INIT:
			// Need to start a burglar. Choose where to travel to.
			
			// XXXX CHOOSE AREA TO TRAVEL TO
			this.targetArea = ContextManager.buildingContext.getRandomObject();
			this.route = new Route(this, this.targetArea.getCoords(), null);
			this.currentState = AGENT_STATE.TRAVEL_TO_TARGET;
			
			break;
			
		case TRAVEL_TO_TARGET:
			if (this.route.atDestination()) {
				// Have reached destination but haven't found a target. Just choose another victim
				this.currentState = AGENT_STATE.BURGLARY_INIT;
			}
			else { // Keep going towards target, possibly burgling one of the passed houses.
				List<Building> passedBuildings = new ArrayList<Building>();
				this.route.travel(passedBuildings);
				if ((passedBuildings.size() > 0) && (ContextManager.nextDouble() < 0.01) ) { // Possibly burgle one of the houses we have passed.
					Building victim = passedBuildings.get(ContextManager.nextIntFromTo(0, passedBuildings.size()-1));
					
					// Successful burglary, now go home.
					this.currentState = AGENT_STATE.TRAVELLING_HOME;
					this.route = new Route(this, this.home.getCoords(), this.home);
					LOGGER.log(Level.INFO, "Agent " + this.id + " has burgled "+victim.toString());
				}				
			}
			
			break;
			
		case SEARCHING:
			// TODO: Implement a nice search. For now this is never actually called.. 
								
			break;
		}

	

	} // step()

	/**
	 * There will be no inter-agent communication so these agents can be executed simulataneously in separate threads.
	 */
	@Override
	public final boolean isThreadable() {
		return true;
	}

	@Override
	public void setHome(Building home) {
		this.home = home;
	}

	@Override
	public Building getHome() {
		return this.home;
	}

	@Override
	public <T> void addToMemory(List<T> objects, Class<T> clazz) {
	}

	@Override
	public List<String> getTransportAvailable() {
		return null;
	}

	@Override
	public String toString() {
		return "Agent " + this.id;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof DefaultAgent))
			return false;
		DefaultAgent b = (DefaultAgent) obj;
		return this.id == b.id;
	}

	@Override
	public int hashCode() {
		return this.id;
	}

}
