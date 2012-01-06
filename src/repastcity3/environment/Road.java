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

package repastcity3.environment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vividsolutions.jts.geom.Coordinate;

import repastcity3.exceptions.DuplicateIdentifierException;
import repastcity3.exceptions.NoIdentifierException;
import repastcity3.main.GlobalVars;

/**
 * Represents road objects.
 * 
 * @author Nick Malleson
 */
public class Road implements FixedGeography, Identified {

	private static Logger LOGGER = Logger.getLogger(Road.class.getName());

	/*
	 * An identifier which can be used to link Roads (in a spatial GIS) with Edges (in a Network). Should be found using
	 * the column name in a GIS table (e.g. TOID). Each road *must* have a unique ID
	 */
	private String identifier;
	// Map to make sure road IDs are unique
	private static Map<String, Object> idMap = new HashMap<String, Object>();

	// The junctions at either end of the road
	transient private ArrayList<Junction> junctions;

	private Coordinate coord;
	// The NetworkEdge which represents this Road in the roadNetwork
	transient private NetworkEdge<Junction> edge;

	// These determine whether or not the the road can be traversed on foot and/or by car.
	private String access; // To be used by ShapefileLoader, should contain string of words separated by spaces
	private List<String> accessibility; // access String should be parsed into this list (see initialise()).

	private String name; // Doesn't affect model but useful for debugging

	private boolean majorRoad = false;

	/**
	 * The null road represents Road objects that do not actually exist, preventing NullPointerExceptions. This is
	 * necessary for routes that include transport networks as these wont necessarily have a Road object associated with
	 * them (e.g. train lines).
	 */
	public static Road nullRoad;
	static {
		Road.nullRoad = new Road();
		try {
			Road.nullRoad.setIdentifier("NULLROAD");
			Road.nullRoad.setCoords(new Coordinate());
		} catch (DuplicateIdentifierException e) { // This should never happen
			LOGGER.log(Level.SEVERE, "", e);
		}

	}

	public Road() {
		this.junctions = new ArrayList<Junction>();
	}

	/**
	 * This should be called once this Road object has been created to perform some extra initialisation (e.g. setting
	 * the accessibility methods available to this Road).
	 * 
	 * @throws NoIdentifierException
	 */
	public void initialise() throws NoIdentifierException {
		if (this.identifier == null || this.identifier == "") {
			throw new NoIdentifierException("This road has no identifier. This can happen "
					+ "when roads are not initialised correctly (e.g. there is no attribute "
					+ "called 'identifier' present in the shapefile used to create this Road)");
		}
		// Parse the access string and work out which accessibility methods can be used to travel this Road
		if (this.access != null) { // Could be null because not using accessibility in GRID environment for example
			this.accessibility = new ArrayList<String>();
			for (String word : this.access.split(" ")) {
				if (word.equals(GlobalVars.TRANSPORT_PARAMS.MAJOR_ROAD)) {
					// Special case: 'majorRoad' isn't a type of access, means the road is quick for car drivers
					this.majorRoad = true;
				} else {
					// Otherwise just add the accessibility type to the list
					this.accessibility.add(word);
				}
			}
		}
	}

	/**
	 * Sets the access methods which can be used to get down this road (e.g. "walk", "car" etc).
	 * <p>
	 * Different roads can be accessed differently depending on the transportation available to the agents. The 'access'
	 * variable can be used by ShapefileLoader to set the different accessibility methods, but it must be parsed and the
	 * accessibility list populated in initialise() (once the Road has been created). E.g. the String "walk car"
	 * indicates agents can either walk or drive down the Road. Note that, ultimately, Roads might also form parts of
	 * transport networks (e.g. busses) but this is done by changing the edges in the roadNetwork directly (in
	 * EnvironmentFactory.createTransportNetworks) and does not affect Road objects.
	 * 
	 * @param access
	 *            A string indicating how this road can be traversed, separated by spaces.
	 */
	public void setAccess(String access) {
		this.access = access;
	}

	public boolean isMajorRoad() {
		return this.majorRoad;
	}

	/**
	 * Get the accessibility methods (not including public transport) which agents can use to travel along this road.
	 * 
	 * @return
	 * @see setAccess
	 */
	public List<String> getAccessibility() {
		return this.accessibility;
	}

	@Override
	public String toString() {
		return "road: " + this.identifier + (this.name == null ? "" : "(" + this.name + ")");
	}

	/**
	 * Get the unique identifier for this Road. This identifier is used to link road features in a GIS with Edges added
	 * to the RoadNetwork (a repast Network Projection).
	 * 
	 * @return the identifier for this Road.
	 * @throws NoIdentifierException
	 *             if the identifier has not been set correctly. This might occur if the roads are not initialised
	 *             correctly (e.g. there is no attribute called 'identifier' present in the shapefile used to create
	 *             this Road).
	 */
	public String getIdentifier() throws NoIdentifierException {
		if (this.identifier == null) {
			throw new NoIdentifierException("This road has no identifier. This can happen "
					+ "when roads are not initialised correctly (e.g. there is no attribute "
					+ "called 'identifier' present in the shapefile used to create this Road)");
		} else {
			return identifier;
		}
	}

	/**
	 * Set the road's identifier. Will check that it is unique.
	 * 
	 * @param identifier
	 * @throws DuplicateIdentifierException
	 *             If a road with the given identifier has already been created
	 */
	public void setIdentifier(String identifier) throws DuplicateIdentifierException {
		// Check the ID is unique
		if (Road.idMap.containsKey(identifier)) {
			throw new DuplicateIdentifierException("A road with identifier '" + identifier + "' has already "
					+ "been created - cannot have two roads with the same unique ID.");
		}
		this.identifier = identifier;
	}

	/**
	 * Used to tell this Road who it's Junctions (endpoints) are.
	 * 
	 * @param j
	 *            the Junction at either end of this Road.
	 */
	public void addJunction(Junction j) {
		if (this.junctions.size() == 2) {
			System.err.println("Road: Error: this Road object already has two Junctions.");
		}
		this.junctions.add(j);
	}

	public ArrayList<Junction> getJunctions() {
		if (this.junctions.size() != 2) {
			System.err.println("Road: Error: This Road does not have two Junctions");
		}
		return this.junctions;
	}

	/**
	 * @return the coord
	 */
	public Coordinate getCoords() {
		return coord;
	}

	/**
	 * @param coord
	 *            the coord to set
	 */
	public void setCoords(Coordinate coord) {
		this.coord = coord;
	}

	/**
	 * Get the NetworkEdge which represents this Road object in the roadNetwork
	 * 
	 * @return the edge
	 */
	public NetworkEdge<Junction> getEdge() {
		return edge;
	}

	/**
	 * @param edge
	 *            the edge to set
	 */
	public void setEdge(NetworkEdge<Junction> edge) {
		this.edge = edge;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Checks to see if passed object is a Road and if the unique id's are equal
	 */
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Road))
			return false;
		Road r = (Road) obj;
		return this.identifier.equals(r.identifier);
	}

	/**
	 * Returns the hash code of this Road's unique identifier.
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
