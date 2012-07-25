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

package repastcity3.agent;

import java.util.List;

import repastcity3.environment.Building;
import repastcity3.environment.Community;

/**
 * All agents must implement this interface so that it the simulation knows how
 * to step them.
 * 
 * @author Nick Malleson
 * 
 */
public interface IAgent {
	

	/**
	 * Controls the agent. This method will be called by the scheduler once per
	 * iteration.
	 */
	 void step() throws Exception;

	/**
	 * Used by Agents as a means of stating whether or not they can be
	 * run in parallel (i.e. there is no inter-agent communication which will
	 * make parallelisation non-trivial). If all the agents in a simulation
	 * return true, and the computer running the simulation has
	 * more than one core, it is possible to step agents simultaneously.
	 * 
	 * @author Nick Malleson
	 */
	boolean isThreadable();
	
	/**
	 * Set the biulding where the agent lives. This doesn't actually move the agent - the agentProjection needs to be used
	 * for that. It is just a record for efficient access.
	 */
	void setHome(Building home);
	
	/**
	 * Get the agent's home building.
	 */
	Building getHome();
	
	/**
	 * Set the community where the agent lives. This doesn't actually move the agent - the agentProjection needs to be used
	 * for that. It is just a record for efficient access.
	 */
	void setHomeCommunity(Community home);
	
	/**
	 * Get the agent's home community..
	 */
	Community getHomeCommunity();
	
	/**
	 * (Optional). Add objects to the agents memory. Used to keep a record of all the
	 * buildings that they have passed.
	 * @param <T>
	 * @param objects The objects to add to the memory.
	 * @param clazz The type of object.
	 */
	<T> void addToMemory(List<T> objects, Class<T> clazz);
	
	/**
	 * (Optional). Get the transport options available to this agent. E.g.
	 * an agent with a car who also could use public transport would return
	 * <code>{"bus", "car"}</code>. If null then it is assumed that the agent
	 * walks (the slowest of all transport methods). 
	 */
	List<String> getTransportAvailable();
	
	
	/**
	 * Each agent will have a unique identifier assigned when the object is created.
	 * @return
	 */
	int getID(); 
	
}
