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

package repastcity3.environment;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.vividsolutions.jts.geom.Coordinate;

import repastcity3.agent.IAgent;
import repastcity3.exceptions.NoIdentifierException;

public class Building implements FixedGeography, Identified {

	private static Logger LOGGER = Logger.getLogger(Building.class.getName());
	
	/** A list of agents who live here */
	private List<IAgent> agents = new ArrayList<IAgent>();

	/**
	 * A unique identifier for buildings, usually set from the 'identifier' column in a shapefile
	 */
	private String identifier;

	/**
	 * The coordinates of the Building. This is also stored by the projection that contains this Building but it is
	 * useful to have it here too. As they will never change (buildings don't move) we don't need to worry about keeping
	 * them in sync with the projection.
	 */
	private Coordinate coords;
	
	/** The community that this building is within */
	private Community community = null;
	
	/* Variables unique to burglary: describe different aspects of the environment..*/
	private double accessibility = 0.5;
	private double visibility = 0.5;
	private double security = 0.5;
//	protected double baseSecurity = 0.5; // Security returns to this level over time.
//	private double trafficVolume = 0.5; // Trafic volume will vary depending on the time of day
	
	/* Some information about the buildings that can be built up as the simulation runs */
	
	private int numBurglaries = 0;
	private int timesPassed = 0; // The number of times an agent passed this building looking for a burglary target

	public Building() {
		
	}

	@Override
	public Coordinate getCoords() {
		return this.coords;
	}

	@Override
	public void setCoords(Coordinate c) {
		this.coords = c;

	}

	public String getIdentifier() throws NoIdentifierException {
		if (this.identifier == null) {
			throw new NoIdentifierException("This building has no identifier. This can happen "
					+ "when roads are not initialised correctly (e.g. there is no attribute "
					+ "called 'identifier' present in the shapefile used to create this Road)");
		} else {
			return identifier;
		}
	}

	public void setIdentifier(String id) {
		this.identifier = id;
	}

	public void addAgent(IAgent a) {
		this.agents.add(a);
	}

	public List<IAgent> getAgents() {
		return this.agents;
	}
	
	public Community getCommunity() {
		return this.community;
	}
	
	public void setCommunity(Community c) {
		this.community = c;
	}

	public int getNumBurglaries() {
		return numBurglaries;
	}

	public void incrementNumBurglaries() {
		this.numBurglaries++;
	}

	

	/* Get/set methods for physical parameters relevant to burglary. Note the short versions of
	 * the function names to match the field names in the buildings shapefile */

	public double getAccessibility() {
		return accessibility;
	}

	public void setAccessibility(double accessibility) {
		this.accessibility = accessibility;
	}
	public double getAcc() {
		return accessibility;
	}

	public void setAcc(double accessibility) {
		this.accessibility = accessibility;
	}

	public double getVisibility() {
		return visibility;
	}

	public void setVis(double visibility) {
		this.visibility = visibility;
	}
	
	public double getVis() {
		return visibility;
	}

	public void setVisibility(double visibility) {
		this.visibility = visibility;
	}

	public double getSecurity() {
		return security;
	}

	public void setSecurity(double security) {
		this.security = security;
	}
	
	public double getSec() {
		return security;
	}

	public void setSec(double security) {
		this.security = security;
	}


	/** Get the number of times an agent passed this building whilst looking for a burglary
	 * target
	 * @return
	 */
	public int getTimesPassed() {
		return timesPassed;
	}

	public void incrementTimesPassed() {
		this.timesPassed++;
	}

	@Override
	public String toString() {
		return "building: " + this.identifier;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Building))
			return false;
		Building b = (Building) obj;
		return this.identifier.equals(b.identifier);
	}

	/**
	 * Returns the hash code of this <code>Building</code>'s identifier string. 
	 */
	@Override
	public int hashCode() {
		if (this.identifier==null) {
			LOGGER.severe("hashCode called but this object's identifier has not been set. It is likely that you're " +
					"reading a shapefile that doesn't have a string column called 'identifier'");
		}

		return this.identifier.hashCode();
	}

}
