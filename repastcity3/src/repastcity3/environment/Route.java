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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Vector;

import org.apache.commons.lang.ArrayUtils;
import org.geotools.referencing.GeodeticCalculator;

import cern.colt.Arrays;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.operation.distance.DistanceOp;

import repast.simphony.space.gis.Geography;
import repast.simphony.space.graph.RepastEdge;
import repast.simphony.space.graph.ShortestPath;
import repastcity3.agent.IAgent;
import repastcity3.exceptions.RoutingException;
import repastcity3.main.ContextManager;
import repastcity3.main.GlobalVars;

/**
 * Create routes around a GIS road network. The <code>setRoute</code> function actually finds the route and can be
 * overridden by subclasses to create different types of Route. See the method documentation for details of how routes
 * are calculated.
 * 
 * <p>
 * A "unit of travel" is the distance that an agent can cover in one iteration (one square on a grid environment or the
 * distance covered at walking speed in an iteration on a GIS environment). This will change depending on the type of
 * transport the agent is using. E.g. if they are in a car they will be able to travel faster, similarly if they are
 * travelling along a transort route they will cover more ground.
 * </p>
 * 
 * @author Nick Malleson
 */
public class Route implements Cacheable {

	private static Logger LOGGER = Logger.getLogger(Route.class.getName());

	static {
		// Route.routeCache = new Hashtable<CachedRoute, CachedRoute>();
	}

	private IAgent agent;
	private Coordinate destination;
	private Building destinationBuilding;

	/*
	 * The route consists of a list of coordinates which describe how to get to the destination. Each coordinate might
	 * have an attached 'speed' which acts as a multiplier and is used to indicate whether or not the agent is
	 * travelling along a transport route (i.e. if a coordinate has an attached speed of '2' the agent will be able to
	 * get to the next coordinate twice as fast as they would do if they were walking). The current position incicate
	 * where in the lists of coords the agent is up to. Other attribute information about the route can be included as
	 * separate arrays with indices that match those of the 'route' array below.
	 */
	private int currentPosition;
	private List<Coordinate> routeX;
	private List<Double> routeSpeedsX;
	/*
	 * This maps route coordinates to their containing Road, used so that when travelling we know which road/community
	 * the agent is on. private
	 */
	private List<Road> roadsX;

	// Record which function has added each coord, useful for debugging
	private List<String> routeDescriptionX;

	/*
	 * Cache every coordinate which forms a road so that Route.onRoad() is quicker. Also save the Road(s) they are part
	 * of, useful for the agent's awareness space (see getRoadFromCoordCache()).
	 */
	private static volatile Map<Coordinate, List<Road>> coordCache;
	/*
	 * Cache the nearest road Coordinate to every building for efficiency (agents usually/always need to get from the
	 * centroids of houses to/from the nearest road).
	 */
	private static volatile NearestRoadCoordCache nearestRoadCoordCache;
	/*
	 * Store which road every building is closest to. This is used to efficiently add buildings to the agent's awareness
	 * space
	 */
	private static volatile BuildingsOnRoadCache buildingsOnRoadCache;
	// To stop threads competing for the cache:
	private static Object buildingsOnRoadCacheLock = new Object();

	/*
	 * Store a route once it has been created, might be used later (note that the same object acts as key and value).
	 */
	// TODO Re-think route caching, would be better to cache the whole Route object
	// private static volatile Map<CachedRoute, CachedRoute> routeCache;
	// /** Store a route distance once it has been created */
	// private static volatile Map<CachedRouteDistance, Double> routeDistanceCache;

	/*
	 * Keep a record of the last community and road passed so that the same buildings/communities aren't added to the
	 * cognitive map multiple times (the agent could spend a number of iterations on the same road or community).
	 */
	private Road previousRoad;
	private Area previousArea;

	/**
	 * Creates a new Route object.
	 * 
	 * @param burglar
	 *            The burglar which this Route will control.
	 * 
	 * @param destination
	 *            The agent's destination.
	 * 
	 * @param destinationBuilding
	 *            The (optional) building they're heading to.
	 * 
	 * @param type
	 *            The (optional) type of route, used by burglars who want to search.
	 */
	public Route(IAgent agent, Coordinate destination, Building destinationBuilding) {
		this.destination = destination;
		this.agent = agent;
		this.destinationBuilding = destinationBuilding;
	}

	/**
	 * Find a route from the origin to the destination. A route is a list of Coordinates which describe the route to a
	 * destination restricted to a road network. The algorithm consists of three major parts:
	 * <ol>
	 * <li>Find out if the agent is on a road already, if not then move to the nearest road segment</li>
	 * <li>Get from the current location (probably mid-point on a road) to the nearest junction</li>
	 * <li>Travel to the junction which is closest to our destination (using Dijkstra's shortest path)</li>
	 * <li>Get from the final junction to the road which is nearest to the destination
	 * <li>
	 * <li>Move from the road to the destination</li>
	 * </ol>
	 * 
	 * @throws Exception
	 */
	protected void setRoute() throws Exception {
		long time = System.nanoTime();
		// this.routeX = new ArrayList<Coordinate>();
		// this.roadsX = new ArrayList<Road>();
		// this.routeDescriptionX = new ArrayList<String>();
		// this.routeSpeedsX = new ArrayList<Double>();
		this.routeX = new Vector<Coordinate>();
		this.roadsX = new Vector<Road>();
		this.routeDescriptionX = new Vector<String>();
		this.routeSpeedsX = new Vector<Double>();

		LOGGER.log(Level.FINER, "Planning route for: "
				+ this.agent.toString()
				+ " to: "
				+ this.destinationBuilding.toString()
				+ ((this.agent.getTransportAvailable() == null) ? "" : "using transport: "
						+ this.agent.getTransportAvailable().toString()));
		if (atDestination()) {
			LOGGER.log(Level.WARNING, "Already at destination, cannot create a route for " + this.agent.toString());
			return;
		}

		Coordinate currentCoord = ContextManager.getAgentGeometry(this.agent).getCoordinate();
		Coordinate destCoord = this.destination;

		// See if a route has already been cached.
		// CachedRoute cachedRoute = new CachedRoute(currentCoord, destCoord, this.agent.getTransportAvailable());
		// synchronized (Route.routeCache) {
		// if (Route.routeCache.containsKey(cachedRoute)) {
		// TempLogger.out("Route.setRoute, found a cached route from " + currentCoord + " to " + destCoord
		// + " using available transport " + this.agent.getTransportAvailable() + ", returning it.");
		// // Return a clone of the route that is stored in the cache
		// // TODO do we need clones here? I don't think so...
		// CachedRoute cr = Route.routeCache.get(cachedRoute);
		// // this.routeX = Cloning.copy(cr.getRoute());
		// // this.roadsX = new ArrayList<Road>(cr.getRoads());
		// // this.routeSpeedsX = new ArrayList<Double>(cr.getRouteSpeeds());
		// // this.routeDescriptionX = new ArrayList<String>(cr.getDescriptions());
		// this.routeX = new Vector<Coordinate>(cr.getRoute());
		// this.roadsX = new Vector<Road>(cr.getRoads());
		// this.routeSpeedsX = new Vector<Double>(cr.getRouteSpeeds());
		// this.routeDescriptionX = new Vector<String>(cr.getDescriptions());
		//
		// return;
		// }
		// } // synchronized

		// No route cached, have to create a new one (and cache it at the end).
		try {
			/*
			 * See if the current position and the destination are on road segments. If the destination is not on a road
			 * segment we have to move to the closest road segment, then onto the destination.
			 */
			boolean destinationOnRoad = true;
			Coordinate finalDestination = null;
			if (!coordOnRoad(currentCoord)) {
				/*
				 * Not on a road so the first coordinate to add to the route is the point on the closest road segment.
				 */
				currentCoord = getNearestRoadCoord(currentCoord);
				addToRoute(currentCoord, Road.nullRoad, 1, "setRoute() initial");
			}
			if (!coordOnRoad(destCoord)) {
				/*
				 * Not on a road, so need to set the destination to be the closest point on a road, and set the
				 * destinationOnRoad boolean to false so we know to add the final dest coord at the end of the route
				 */
				destinationOnRoad = false;
				finalDestination = destCoord; // Added to route at end of alg.
				destCoord = getNearestRoadCoord(destCoord);
			}

			/*
			 * Find the nearest junctions to our current position (road endpoints)
			 */

			// Start by Finding the road that this coordinate is on
			/*
			 * TODO EFFICIENCY: often the agent will be creating a new route from a building so will always find the
			 * same road, could use a cache. Even better, could implement a cache in FindNearestObject() method!
			 */
			Road currentRoad = Route.findNearestObject(currentCoord, ContextManager.roadProjection, null,
					GlobalVars.GEOGRAPHY_PARAMS.BUFFER_DISTANCE.LARGE);
			// Find which Junction is closest to us on the road.
			List<Junction> currentJunctions = currentRoad.getJunctions();

			/* Find the nearest Junctions to our destination (road endpoints) */

			// Find the road that this coordinate is on
			Road destRoad = Route.findNearestObject(destCoord, ContextManager.roadProjection, null,
					GlobalVars.GEOGRAPHY_PARAMS.BUFFER_DISTANCE.SMALL);
			// Find which Junction connected to the edge is closest to the coordinate.
			List<Junction> destJunctions = destRoad.getJunctions();
			/*
			 * Now have four possible routes (2 origin junctions, 2 destination junctions) need to pick which junctions
			 * form shortest route
			 */
			Junction[] routeEndpoints = new Junction[2];
			List<RepastEdge<Junction>> shortestPath = getShortestRoute(currentJunctions, destJunctions, routeEndpoints);
			// NetworkEdge<Junction> temp = (NetworkEdge<Junction>)
			// shortestPath.get(0);
			Junction currentJunction = routeEndpoints[0];
			Junction destJunction = routeEndpoints[1];

			/* Add the coordinates describing how to get to the nearest junction */
			List<Coordinate> tempCoordList = new Vector<Coordinate>();
			this.getCoordsAlongRoad(currentCoord, currentJunction.getCoords(), currentRoad, true, tempCoordList);
			addToRoute(tempCoordList, currentRoad, 1, "getCoordsAlongRoad (toJunction)");

			/*
			 * Add the coordinates and speeds etc which describe how to move along the chosen path
			 */
			this.getRouteBetweenJunctions(shortestPath, currentJunction);

			/*
			 * Add the coordinates describing how to get from the final junction to the destination.
			 */

			tempCoordList.clear();
			this.getCoordsAlongRoad(ContextManager.junctionGeography.getGeometry(destJunction).getCoordinate(),
					destCoord, destRoad, false, tempCoordList);
			addToRoute(tempCoordList, destRoad, 1, "getCoordsAlongRoad (fromJunction)");

			if (!destinationOnRoad) {
				addToRoute(finalDestination, Road.nullRoad, 1, "setRoute final");
			}

			// Check that a route has actually been created
			checkListSizes();

			// If the algorithm was better no coordinates would have been duplicated
			// removePairs();

			// Check lists are still the same size.
			checkListSizes();

		} catch (RoutingException e) {
			LOGGER.log(Level.SEVERE, "Route.setRoute(): Problem creating route for " + this.agent.toString()
					+ " going from " + currentCoord.toString() + " to " + this.destination.toString() + "("
					+ (this.destinationBuilding == null ? "" : this.destinationBuilding.toString())
					+ ") See earlier messages error messages for more info.");
			throw e;
		}
		// Cache the route and route speeds
		// List<Coordinate> routeClone = Cloning.copy(theRoute);
		// LinkedHashMap<Coordinate, Double> routeSpeedsClone = Cloning.copy(this.routeSpeeds);
		// cachedRoute.setRoute(routeClone);
		// cachedRoute.setRouteSpeeds(routeSpeedsClone);

		// cachedRoute.setRoute(this.routeX, this.roadsX, this.routeSpeedsX, this.routeDescriptionX);
		// synchronized (Route.routeCache) {
		// // Same cached route is both value and key
		// Route.routeCache.put(cachedRoute, cachedRoute);
		// }
		// TempLogger.out("...Route cacheing new route with unique id " + cachedRoute.hashCode());

		LOGGER.log(Level.FINER, "Route Finished planning route for " + this.agent.toString() + "with "
				+ this.routeX.size() + " coords in " + (0.000001 * (System.nanoTime() - time)) + "ms.");

		// Finished, just check that the route arrays are all in sync
		assert this.roadsX.size() == this.routeX.size() && this.routeDescriptionX.size() == this.routeSpeedsX.size()
				&& this.roadsX.size() == this.routeDescriptionX.size();
	}

	private void checkListSizes() {
		assert this.roadsX.size() > 0 && this.roadsX.size() == this.routeX.size()
				&& this.routeDescriptionX.size() == this.routeSpeedsX.size()
				&& this.roadsX.size() == this.routeDescriptionX.size() : this.routeX.size() + "," + this.roadsX.size()
				+ "," + this.routeDescriptionX.size() + "," + this.routeSpeedsX.size();

	}

	/**
	 * Convenience function that can be used to add details to the route. This should be used rather than updating
	 * individual lists because it makes sure that all lists stay in sync
	 * 
	 * @param coord
	 *            The coordinate to add to the route
	 * @param road
	 *            The road that the coordinate is part of
	 * @param speed
	 *            The speed that the road can be travelled along
	 * @param description
	 *            A description of why the coordinate has been added
	 */
	private void addToRoute(Coordinate coord, Road road, double speed, String description) {
		this.routeX.add(coord);
		this.roadsX.add(road);
		this.routeSpeedsX.add(speed);
		this.routeDescriptionX.add(description);
	}

	/**
	 * A convenience for adding to the route that will add a number of coordinates with the same description, road and
	 * speed.
	 * 
	 * @param coord
	 *            A list of coordinates to add to the route
	 * @param road
	 *            The road that the coordinates are part of
	 * @param speed
	 *            The speed that the road can be travelled along
	 * @param description
	 *            A description of why the coordinates have been added
	 */
	private void addToRoute(List<Coordinate> coords, Road road, double speed, String description) {
		for (Coordinate c : coords) {
			this.routeX.add(c);
			this.roadsX.add(road);
			this.routeSpeedsX.add(speed);
			this.routeDescriptionX.add(description);
		}
	}

	/**
	 * Travel towards our destination, as far as we can go this turn.
	 * <p>
	 * Also adds houses to the agent's cognitive environment. This is done by saving each coordinate the person passes,
	 * creating a polygon with a radius given by the "cognitive_map_search_radius" and adding all houses which touch the
	 * polygon.
	 * <p>
	 * Note: the agent might move their position many times depending on how far they are allowed to move each turn,
	 * this requires many calls to geometry.move(). This function could be improved (quite easily) by working out where
	 * the agent's final destination will be, then calling move() just once.
	 * 
	 * @param housesPassed
	 *            If not null then the buildings which the agent passed during their travels this iteration will be
	 *            calculated and stored in this array. This can be useful if a agent needs to know which houses it has
	 *            just passed and, therefore, which are possible victims. This isn't done by default because it's quite
	 *            an expensive operation (lots of geographic tests which must be carried out in each iteration). If the
	 *            array is null then the houses passed are not calculated.
	 * @return null or the buildings passed during this iteration if housesPassed boolean is true
	 * @throws Exception
	 */
	public void travel() throws Exception {
		// Check that the route has been created
		if (this.routeX == null) {
			this.setRoute();
		}
		try {
			if (this.atDestination()) {
				return;
			}
			double time = System.nanoTime();

			// Store the roads the agent walks along (used to populate the awareness space)
			// List<Road> roadsPassed = new ArrayList<Road>();
			double distTravelled = 0; // The distance travelled so far
			Coordinate currentCoord = null; // Current location
			Coordinate target = null; // Target coordinate we're heading for (in route list)
			boolean travelledMaxDist = false; // True when travelled maximum distance this iteration
			double speed; // The speed to travel to next coord
			GeometryFactory geomFac = new GeometryFactory();
			currentCoord = ContextManager.getAgentGeometry(this.agent).getCoordinate();

			while (!travelledMaxDist && !this.atDestination()) {
				target = this.routeX.get(this.currentPosition);
				speed = this.routeSpeedsX.get(this.currentPosition);
				/*
				 * TODO Remember which roads have been passed, used to work out what should be added to cognitive map.
				 * Only add roads once the agent has moved all the way down them
				 */
				// roadsPassed.add(this.roads.get(this.previousRouteCoord()));
				// Work out the distance and angle to the next coordinate
				double[] distAndAngle = new double[2];
				Route.distance(currentCoord, target, distAndAngle);
				// divide by speed because distance might effectively be shorter

				double distToTarget = distAndAngle[0] / speed;
				// If we can get all the way to the next coords on the route then just go there
				if (distTravelled + distToTarget < GlobalVars.GEOGRAPHY_PARAMS.TRAVEL_PER_TURN) {

					distTravelled += distToTarget;
					currentCoord = target;

					// See if agent has reached the end of the route.
					if (this.currentPosition == (this.routeX.size() - 1)) {
						ContextManager.moveAgent(this.agent, geomFac.createPoint(currentCoord));
						// ContextManager.agentGeography.move(this.agent, geomFac.createPoint(currentCoord));
						break; // Break out of while loop, have reached end of route.
					}
					// Haven't reached end of route, increment the counter
					this.currentPosition++;
				} // if can get all way to next coord

				// Check if dist to next coordinate is exactly same as maximum
				// distance allowed to travel (unlikely but possible)
				else if (distTravelled + distToTarget == GlobalVars.GEOGRAPHY_PARAMS.TRAVEL_PER_TURN) {
					travelledMaxDist = true;
					ContextManager.moveAgent(agent, geomFac.createPoint(target));
					// ContextManager.agentGeography.move(agent, geomFac.createPoint(target));
					this.currentPosition++;
					LOGGER.log(Level.WARNING, "Travel(): UNUSUAL CONDITION HAS OCCURED!");
				} else {
					// Otherwise move as far as we can towards the target along the road we're on.
					// Move along the vector the maximum distance we're allowed this turn (take into account relative
					// speed)
					double distToTravel = (GlobalVars.GEOGRAPHY_PARAMS.TRAVEL_PER_TURN - distTravelled) * speed;
					// Move the agent, first move them to the current coord (the first part of the while loop doesn't do
					// this for efficiency)
					// ContextManager.agentGeography.move(this.agent, geomFac.createPoint(currentCoord));
					ContextManager.moveAgent(this.agent, geomFac.createPoint(currentCoord));
					// Now move by vector towards target (calculated angle earlier).
					ContextManager.moveAgentByVector(this.agent, distToTravel, distAndAngle[1]);
					// ContextManager.agentGeography.moveByVector(this.agent, distToTravel, distAndAngle[1]);

					travelledMaxDist = true;
				} // else
			} // while

//			this.printRoute();

			/*
			 * TODO Agent has finished moving, now just add all the buildings and communities passed to their awareness
			 * space (unless they're on a transport route). Note also that if on a transport route without an associated
			 * road no roads are added to the 'roads' map so even if the check wasn't made here no buildings would be
			 * added anyway.
			 */
			// Community c = null;
			// if (!this.onTransportRoute) {
			// String outputString = "Route.travel() adding following to awareness space for '"
			// + this.agent.toString() + "':";
			// // roadsPassed will have duplicates, this is used to ignore them
			// Road current = roadsPassed.get(0);
			// // TODO The next stuff is a mess when it comes to adding communities to the memory. Need to go
			// // through and make sure communities aren't added too many times (i.e. more than once for each journey)
			// // and that they are always added when they should be.
			//
			// for (Road r : roadsPassed) { // last road in list is the one the
			// // agent finishes iteration on
			// if (r != null && roadsPassed.get(0) != null && !current.equals(r)) {
			// // Check road isn't null () and that buildings on road haven't already been added
			// // (road can be null when coords that aren't part of a road are added to the route)
			// current = r;
			// if (r.equals(this.previousRoad)) {
			// // The agent has just passed over this road, don't add the buildings or communities again
			// } else {
			// outputString += "\n\t" + r.toString() + ": ";
			// List<Building> passedBuildings = getBuildingsOnRoad(r);
			// List<Community> passedCommunities = new ArrayList<Community>();
			// if (passedBuildings != null) { // There might not be any buildings close to the road (unlikely)
			// outputString += passedBuildings.toString();
			// this.passedObjects(passedBuildings, Building.class);
			// // For efficiency just find one of the building's communities and hope no other
			// // communities were passed through - NO! I'VE CHANGED THIS BELOW!
			// c = passedBuildings.get(0).getCommunity();
			// // Check all buildings to make sure that if the agent has passed more than one community
			// // then they are all added.
			// for (Building b : passedBuildings) {
			// if (!passedCommunities.contains(b.getCommunity())) {
			// passedCommunities.add(b.getCommunity());
			// }
			// }
			// for (Community com : passedCommunities) {
			// if (com != null) {
			// this.passedObject(com, Community.class);
			// }
			// }
			//
			// } else { // Community won't have been added because no buildings passed, use slow method
			// c = GlobalVars.COMMUNITY_ENVIRONMENT.getObjectAt(Community.class, currentCoord);
			// if (c != null) {
			// this.passedObject(c, Community.class);
			// }
			// // TODO I think the following line is wrong, if the agent has made
			// // a long move they might have passed right through a community that doesn't
			// // have any buildings, perhaps this should check *all* the communities that touch
			// // the road, not just the community the agent finished the move in (i.e. currentCoord)
			// passedCommunities.add(GlobalVars.COMMUNITY_ENVIRONMENT.getObjectAt(Community.class,
			// currentCoord));
			// }
			// }
			// }
			// } // for roadsPassed
			// TempLogger.out(outputString + "\n");
			// } // if !onTransportRoute
			// else {
			// TempLogger.out("Route.travel() not adding to burglar '" + this.agent.toString()
			// + "' awareness space beecause on transport route: ");
			// }
			//
			// // Finally set the previousRoad and previousCommunity so that if these haven't changed in the next
			// iteration they're not added to
			// // the cognitive map again.
			// this.previousRoad = roadsPassed.get(roadsPassed.size() - 1);
			// // this.previousCommunity = c; // This was the most recent community passed over
			//
			// TempLogger.out("...Finished Travelling(" + (0.000001 * (System.nanoTime() - time)) + "ms)");
			// // } // synchronized GlobalVars.TRANSPORT_PARAMS.currentBurglar
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Route.trave(): Caught error travelling for " + this.agent.toString()
					+ " going to " + "destination "
					+ (this.destinationBuilding == null ? "" : this.destinationBuilding.toString() + ")"));
			throw e;
		} // catch exception
	}

	/**
	 * Get the distance (on a network) between the origin and destination. Take into account the Burglar because they
	 * might be able to speed up the route by using different transport methods. Actually calculates the distance
	 * between the nearest Junctions between the source and destination. Note that the GRID environment doesn't have any
	 * transport routes in it so all distances will always be the same regardless of the agent.
	 * 
	 * @param agent
	 * @param destination
	 * @return
	 */
	public double getDistance(IAgent theBurglar, Coordinate origin, Coordinate destination) {

		// // See if this distance has already been calculated
		// if (Route.routeDistanceCache == null) {
		// Route.routeDistanceCache = new Hashtable<CachedRouteDistance, Double>();
		// }
		// CachedRouteDistance crd = new CachedRouteDistance(origin, destination, theBurglar.getTransportAvailable());
		//
		// synchronized (Route.routeDistanceCache) {
		// Double dist = Route.routeDistanceCache.get(crd);
		// if (dist != null) {
		// TempLogger.out("Route.ggetDistance, found a cached route distance from " + origin + " to "
		// + destination + " using available transport " + theBurglar.getTransportAvailable()
		// + ", returning it.");
		// return dist;
		// }
		// }
		// No distance in the cache, calculate it
		synchronized (GlobalVars.TRANSPORT_PARAMS.currentBurglarLock) {
			GlobalVars.TRANSPORT_PARAMS.currentAgent = theBurglar;
			// Find the closest Junctions to the origin and destination
			double minOriginDist = Double.MAX_VALUE;
			double minDestDist = Double.MAX_VALUE;
			double dist;
			Junction closestOriginJunc = null;
			Junction closestDestJunc = null;
			DistanceOp distOp = null;
			GeometryFactory geomFac = new GeometryFactory();
			// TODO EFFICIENCY: here could iterate over near junctions instead of all?
			for (Junction j : ContextManager.junctionContext.getObjects(Junction.class)) {
				// Check that the agent can actually get to the junction (if might be part of a transport route
				// that the agent doesn't have access to)
				boolean accessibleJunction = false;
				accessibleJunc: for (RepastEdge<Junction> e : ContextManager.roadNetwork.getEdges(j)) {
					NetworkEdge<Junction> edge = (NetworkEdge<Junction>) e;
					for (String s : edge.getTypes()) {
						if (theBurglar.getTransportAvailable().contains(s)) {
							accessibleJunction = true;
							break accessibleJunc;
						}
					} // for types
				}// for edges
				if (!accessibleJunction) { // Agent can't get to the junction, ignore it
					continue;
				}
				Point juncPoint = geomFac.createPoint(j.getCoords());

				distOp = new DistanceOp(juncPoint, geomFac.createPoint(origin));
				dist = distOp.distance();
				if (dist < minOriginDist) {
					minOriginDist = dist;
					closestOriginJunc = j;
				}
				// Destination
				distOp = new DistanceOp(juncPoint, geomFac.createPoint(destination));
				dist = distOp.distance();
				if (dist < minDestDist) {
					minDestDist = dist;
					closestDestJunc = j;
				}
			} // for Junctions
				// Return the shortest path plus the distance from the origin/destination to their junctions
				// TODO NOTE: Bug in ShortestPath so have to make finalize is called, otherwise following lines are
				// neater
				// - MAYBE THIS HAS BEEN FIXED BY REPAST NOW.
				// return (new ShortestPath<Junction>(EnvironmentFactory.getRoadNetwork(),
				// closestOriginJunc)).getPathLength(closestDestJunc)+ minOriginDist + minDestDist ;
				// TODO : using non-deprecated methods don't work on NGS, probably need to update repast libraries
			ShortestPath<Junction> p = new ShortestPath<Junction>(ContextManager.roadNetwork, closestOriginJunc);
			double theDist = p.getPathLength(closestDestJunc);
			// ShortestPath<Junction> p = new
			// ShortestPath<Junction>(EnvironmentFactory.getRoadNetwork());
			// double theDist = p.getPathLength(closestOriginJunc,closestDestJunc);
			p.finalize();
			p = null;
			double finalDist = theDist + minOriginDist + minDestDist;
			// // Cache this distance
			// synchronized (Route.routeDistanceCache) {
			// Route.routeDistanceCache.put(crd, finalDist);
			// }
			return finalDist;
		} // synchronized

	}

	/**
	 * Find the nearest coordinate which is part of a Road. Returns the coordinate which is actually the closest to the
	 * given coord, not just the corner of the segment which is closest. Uses the DistanceOp class which finds the
	 * closest points between two geometries.
	 * <p>
	 * When first called, the function will populate the 'nearestRoadCoordCache' which calculates where the closest road
	 * coordinate is to each building. The agents will commonly start journeys from within buildings so this will
	 * improve efficiency.
	 * </p>
	 * 
	 * @param inCoord
	 *            The coordinate from which to find the nearest road coordinate
	 * @return the nearest road coordinate
	 * @throws Exception
	 */
	private synchronized Coordinate getNearestRoadCoord(Coordinate inCoord) throws Exception {
		// double time = System.nanoTime();

		synchronized (buildingsOnRoadCacheLock) {
			if (nearestRoadCoordCache == null) {
				LOGGER.log(Level.FINE, "Route.getNearestRoadCoord called for first time, "
						+ "creating cache of all roads and the buildings which are on them ...");
				// Create a new cache object, this will be read from disk if
				// possible (which is why the getInstance() method is used
				// instead of the constructor.
				String gisDir = ContextManager.getProperty(GlobalVars.GISDataDirectory);
				File buildingsFile = new File(gisDir + ContextManager.getProperty(GlobalVars.BuildingShapefile));
				File roadsFile = new File(gisDir + ContextManager.getProperty(GlobalVars.RoadShapefile));
				File serialisedLoc = new File(gisDir + ContextManager.getProperty(GlobalVars.BuildingsRoadsCoordsCache));

				nearestRoadCoordCache = NearestRoadCoordCache.getInstance(ContextManager.buildingProjection,
						buildingsFile, ContextManager.roadProjection, roadsFile, serialisedLoc, new GeometryFactory());
			} // if not cached
		} // synchronized
		return nearestRoadCoordCache.get(inCoord);
	}

	/**
	 * Finds the shortest route between multiple origin and destination junctions. Will return the shortest path and
	 * also, via two parameters, can return the origin and destination junctions which make up the shortest route.
	 * 
	 * @param currentJunctions
	 *            An array of origin junctions
	 * @param destJunctions
	 *            An array of destination junctions
	 * @param routeEndpoints
	 *            An array of size 2 which can be used to store the origin (index 0) and destination (index 1) Junctions
	 *            which form the endpoints of the shortest route.
	 * @return the shortest route between the origin and destination junctions
	 * @throws Exception
	 */
	private List<RepastEdge<Junction>> getShortestRoute(List<Junction> currentJunctions, List<Junction> destJunctions,
			Junction[] routeEndpoints) throws Exception {
		double time = System.nanoTime();
		synchronized (GlobalVars.TRANSPORT_PARAMS.currentBurglarLock) {
			// This must be set so that NetworkEdge.getWeight() can adjust the weight depending on how this
			// particular agent is getting around the city
			GlobalVars.TRANSPORT_PARAMS.currentAgent = this.agent;
			double shortestPathLength = Double.MAX_VALUE;
			double pathLength = 0;
			ShortestPath<Junction> p;
			List<RepastEdge<Junction>> shortestPath = null;
			for (Junction o : currentJunctions) {
				for (Junction d : destJunctions) {
					if (o == null || d == null) {
						LOGGER.log(Level.WARNING, "Route.getShortestRoute() error: either the destination or origin "
								+ "junction is null. This can be caused by disconnected roads. It's probably OK"
								+ "to ignore this as a route should still be created anyway.");
					} else {
						p = new ShortestPath<Junction>(ContextManager.roadNetwork);
						pathLength = p.getPathLength(o,d);
						if (pathLength < shortestPathLength) {
							shortestPathLength = pathLength;
							shortestPath = p.getPath(o,d);
//							ShortestPath<Junction> p2 = new ShortestPath<Junction>(ContextManager.roadNetwork);
//							shortestPath = p2.getPath(o, d);
//							p2.finalize();
//							p2 = null;
							// shortestPath = p1.getPath(o, d);
							// p1.finalize(); p1 = null;
							routeEndpoints[0] = o;
							routeEndpoints[1] = d;
						}
						// TODO See if the shortestpath bug has been fixed, would make this unnecessary
						p.finalize();
						p = null;
					} // if junc null
				} // for dest junctions
			} // for origin junctions
			if (shortestPath == null) {
				String debugString = "Route.getShortestRoute() could not find a route. Looking for the shortest route between :\n";
				for (Junction j : currentJunctions)
					debugString += "\t" + j.toString() + ", roads: " + j.getRoads().toString() + "\n";
				for (Junction j : destJunctions)
					debugString += "\t" + j.toString() + ", roads: " + j.getRoads().toString() + "\n";
				throw new RoutingException(debugString);
			}
			LOGGER.log(Level.FINER, "Route.getShortestRoute (" + (0.000001 * (System.nanoTime() - time))
					+ "ms) found shortest path " + "(length: " + shortestPathLength + ") from "
					+ routeEndpoints[0].toString() + " to " + routeEndpoints[1].toString());
			return shortestPath;
		} // synchronized
	}

	/**
	 * Calculates the coordinates required to move an agent from their current position to the destination along a given
	 * road. The algorithm to do this is as follows:
	 * <ol>
	 * <li>Starting from the destination coordinate, record each vertex and check inside the booundary of each line
	 * segment until the destination point is found.</li>
	 * <li>Return all but the last vertex, this is the route to the destination.</li>
	 * </ol>
	 * A boolean allows for two cases: heading towards a junction (the endpoint of the line) or heading away from the
	 * endpoint of the line (this function can't be used to go to two midpoints on a line)
	 * 
	 * @param currentCoord
	 * @param destinationCoord
	 * @param road
	 * @param toJunction
	 *            whether or not we're travelling towards or away from a Junction
	 * @param coordList
	 *            A list which will be populated with the coordinates that the agent should follow to move along the
	 *            road.
	 * @param roadList
	 *            A list of roads associated with each coordinate.
	 * @throws Exception
	 */
	private void getCoordsAlongRoad(Coordinate currentCoord, Coordinate destinationCoord, Road road,
			boolean toJunction, List<Coordinate> coordList) throws RoutingException {

		Route.checkNotNull(currentCoord, destinationCoord, road, coordList);

		double time = System.nanoTime();
		Coordinate[] roadCoords = ContextManager.roadProjection.getGeometry(road).getCoordinates();

		// Check that the either the destination or current coordinate are actually part of the road
		boolean currentCorrect = false, destinationCorrect = false;
		for (int i = 0; i < roadCoords.length; i++) {
			if (toJunction && destinationCoord.equals(roadCoords[i])) {
				destinationCorrect = true;
				break;
			} else if (!toJunction && currentCoord.equals(roadCoords[i])) {
				currentCorrect = true;
				break;
			}
		} // for

		if (!(destinationCorrect || currentCorrect)) {
			String roadCoordsString = "";
			for (Coordinate c : roadCoords)
				roadCoordsString += c.toString() + " - ";
			throw new RoutingException("Neigher the origin or destination nor the current"
					+ "coordinate are part of the road '" + road.toString() + "' (person '" + this.agent.toString()
					+ "').\n" + "Road coords: " + roadCoordsString + "\n" + "\tOrigin: " + currentCoord.toString()
					+ "\n" + "\tDestination: " + destinationCoord.toString() + " ( "
					+ this.destinationBuilding.toString() + " )\n " + "Heading " + (toJunction ? "to" : "away from")
					+ " a junction, so " + (toJunction ? "destination" : "origin")
					+ " should be part of a road segment");
		}

		// Might need to reverse the order of the road coordinates
		if (toJunction && !destinationCoord.equals(roadCoords[roadCoords.length - 1])) {
			// If heading towards a junction, destination coordinate must be at end of road segment
			ArrayUtils.reverse(roadCoords);
		} else if (!toJunction && !currentCoord.equals(roadCoords[0])) {
			// If heading away form junction current coord must be at beginning of road segment
			ArrayUtils.reverse(roadCoords);
		}
		GeometryFactory geomFac = new GeometryFactory();
		Point destinationPointGeom = geomFac.createPoint(destinationCoord);
		Point currentPointGeom = geomFac.createPoint(currentCoord);
		// If still false at end then algorithm hasn't worked
		boolean foundAllCoords = false;
		search: for (int i = 0; i < roadCoords.length - 1; i++) {
			Coordinate[] segmentCoords = new Coordinate[] { roadCoords[i], roadCoords[i + 1] };
			// Draw a small buffer around the line segment and look for the coordinate within the buffer
			Geometry buffer = geomFac.createLineString(segmentCoords).buffer(GlobalVars.GEOGRAPHY_PARAMS.BUFFER_DISTANCE.SMALL.dist);
			if (!toJunction) {
				/* If heading away from a junction, keep adding road coords until we find the destination */
				coordList.add(roadCoords[i]);
				if (destinationPointGeom.within(buffer)) {
					coordList.add(destinationCoord);
					foundAllCoords = true;
					break search;
				}
			} else if (toJunction) {
				/*
				 * If heading towards a junction: find the curent coord, add it to the route, then add all the remaining
				 * coords which make up the road segment
				 */
				if (currentPointGeom.within(buffer)) {
					for (int j = i + 1; j < roadCoords.length; j++) {
						coordList.add(roadCoords[j]);
					}
					coordList.add(destinationCoord);
					foundAllCoords = true;
					break search;
				}
			}
		} // for
		if (foundAllCoords) {
			LOGGER.log(Level.FINER, "getCoordsAlongRoad (" + (0.000001 * (System.nanoTime() - time)) + "ms)");
			return;
		} else { // If we get here then the route hasn't been created
			// A load of debugging info
			String error = "Route: getCoordsAlongRoad: could not find destination coordinates "
					+ "along the road.\n\tHeading *" + (toJunction ? "towards" : "away from")
					+ "* a junction.\n\t Person: " + this.agent.toString() + ")\n\tDestination building: "
					+ destinationBuilding.toString() + "\n\tRoad causing problems: " + road.toString()
					+ "\n\tRoad vertex coordinates: " + Arrays.toString(roadCoords);
			throw new RoutingException(error);
			/*
			 * Hack: ignore the error, printing a message and just returning the origin destination and coordinates.
			 * This means agent will jump to/from the junction but I can't figure out why the fuck it occasionally
			 * doesn't work!! It's so rare that hopefully this isn't a problem.
			 */
			// TempLogger.err("Route: getCoordsAlongRoad: error... (not debugging).");
			// List<Coord> coords = new ArrayList<Coord>();
			// coords.add(currentCoord);
			// coords.add(destinationCoord);
			// for (Coord c : coords)
			// this.roads.put(c, road); // Remember the roads each coord is
			// // part of
			// return coords;

		}
	}

	private static void checkNotNull(Object... args) throws RoutingException {
		for (Object o : args) {
			if (o == null) {
				throw new RoutingException("An input argument is null");
			}
		}
		return;
	}

	/**
	 * Returns all the coordinates that describe how to travel along a path, restricted to road coordinates. In some
	 * cases the route wont have an associated road, this occurs if the route is part of a transport network. In this
	 * case just the origin and destination coordinates are added to the route.
	 * 
	 * @param shortestPath
	 * @param startingJunction
	 *            The junction the path starts from, this is required so that the algorithm knows which road coordinate
	 *            to add first (could be first or last depending on the order that the road coordinates are stored
	 *            internally).
	 * @return the coordinates as a mapping between the coord and its associated speed (i.e. how fast the agent can
	 *         travel to the next coord) which is dependent on the type of edge and the agent (e.g.
	 *         driving/walking/bus). LinkedHashMap is used to guarantee the insertion order of the coords is maintained.
	 * @throws RoutingException
	 */
	private void getRouteBetweenJunctions(List<RepastEdge<Junction>> shortestPath, Junction startingJunction)
			throws RoutingException {
		double time = System.nanoTime();
		if (shortestPath.size() < 1) {
			// This could happen if the agent's destination is on the same road
			// as the origin
			return;
		}
		// Lock the currentAgent so that NetworkEdge obejcts know what speed to use (depends on transport available to
		// the specific agent).
		synchronized (GlobalVars.TRANSPORT_PARAMS.currentBurglarLock) {
			GlobalVars.TRANSPORT_PARAMS.currentAgent = this.agent;

			// Iterate over all edges in the route adding coords and weights as appropriate
			NetworkEdge<Junction> e;
			Road r;
			// Use sourceFirst to represent whether or not the edge's source does actually represent the start of the
			// edge (agent could be going 'forwards' or 'backwards' over edge
			boolean sourceFirst;
			for (int i = 0; i < shortestPath.size(); i++) {
				e = (NetworkEdge<Junction>) shortestPath.get(i);
				if (i == 0) {
					// No coords in route yet, compare the source to the starting junction
					sourceFirst = (e.getSource().equals(startingJunction)) ? true : false;
				} else {
					// Otherwise compare the source to the last coord added to the list
					sourceFirst = (e.getSource().getCoords().equals(this.routeX.get(this.routeX.size() - 1))) ? true
							: false;
				}
				/*
				 * Now add the coordinates describing how to move along the road. If there is no road associated with
				 * the edge (i.e. it is a transport route) then just add the source/dest coords. Note that the shared
				 * coordinates between two edges will be added twice, these must be removed later
				 */
				r = e.getRoad();
				/*
				 * Get the speed that the agent will be able to travel along this edge (depends on the transport
				 * available to the agent and the edge). Some speeds will be < 1 if the agent shouldn't be using this
				 * edge but doesn't have any other way of getting to the destination. in these cases set speed to 1
				 * (equivalent to walking).
				 */
				double speed = e.getSpeed();
				if (speed < 1)
					speed = 1;

				if (r == null) { // No road associated with this edge (it is a
									// transport link) so just add source
					if (sourceFirst) {
						this.addToRoute(e.getSource().getCoords(), r, speed, "getRouteBetweenJunctions - no road");
						this.addToRoute(e.getTarget().getCoords(), r, -1, "getRouteBetweenJunctions - no road");
						// (Note speed = -1 used because we don't know the weight to the next
						// coordinate - this can be removed later)
					} else {
						this.addToRoute(e.getTarget().getCoords(), r, speed, "getRouteBetweenJunctions - no road");
						this.addToRoute(e.getSource().getCoords(), r, -1, "getRouteBetweenJunctions - no road");
					}
				} else {
					// This edge is a road, add all the coords which make up its geometry
					Coordinate[] roadCoords = ContextManager.roadProjection.getGeometry(r).getCoordinates();
					if (roadCoords.length < 2)
						throw new RoutingException("Route.getRouteBetweenJunctions: for some reason road " + "'"
								+ r.toString() + "' doesn't have at least two coords as part of its geometry ("
								+ roadCoords.length + ")");
					// Make sure the coordinates of the road are added in the correct order
					if (!sourceFirst) {
						ArrayUtils.reverse(roadCoords);
					}
					// Add all the road geometry's coords
					for (int j = 0; j < roadCoords.length; j++) {
						this.addToRoute(roadCoords[j], r, speed, "getRouteBetweenJuctions - on road");
						// (Note that last coord will have wrong weight)
					} // for roadCoords.length
				} // if road!=null
			}
			// Check all lists are still the same size.
			assert this.roadsX.size() == this.routeX.size()
					&& this.routeDescriptionX.size() == this.routeSpeedsX.size()
					&& this.roadsX.size() == this.routeDescriptionX.size();

			// Check all lists are still the same size.
			assert this.roadsX.size() == this.routeX.size()
					&& this.routeDescriptionX.size() == this.routeSpeedsX.size()
					&& this.roadsX.size() == this.routeDescriptionX.size();

			// Finished!
			LOGGER.log(Level.FINER, "getRouteBetweenJunctions (" + (0.000001 * (System.nanoTime() - time)) + "ms");
			return;
		} // synchronized
	} // getRouteBetweenJunctions

	/**
	 * Determine whether or not the person associated with this Route is at their destination. Compares their current
	 * coordinates to the destination coordinates (must be an exact match).
	 * 
	 * @return True if the person is at their destination
	 */
	public boolean atDestination() {
		return ContextManager.getAgentGeometry(this.agent).getCoordinate().equals(this.destination);
	}

	// /**
	// * Removes any duplicate coordinates from the curent route (coordinates which
	// * are the same *and* next to each other in the list).
	// * <p>
	// * If my route-generating algorithm was better this would't be necessary.
	// */
	// @Deprecated
	// private void removePairs() throws RoutingException {
	// if (this.routeX.size() < 1) {
	// // No coords to iterate over, probably something has gone wrong
	// throw new RoutingException("Route.removeDuplicateCoordinates(): WARNING an empty list has been "
	// + "passed to this function, something has probably gone wrong");
	// }
	// TempLogger.out("ROUTE BEFORE REMOVING PAIRS");
	// this.printRoute();
	//
	// // (setRoute() has already checked that lists are same size)
	//
	// // Iterate over the list, removing coordinates that are the same as their neighbours.
	// // (and associated objects in other lists)
	// Iterator<Road> roadIt = this.roadsX.iterator();
	// Iterator<Coordinate> routeIt = this.routeX.iterator();
	// Iterator<Double> routeSpeedIt = this.routeSpeedsX.iterator();
	// Iterator<String> routeDescIt = this.routeDescriptionX.iterator();
	// Coordinate c1, c2;
	// Road currentRoad = roadIt.next();
	// Road nextRoad = null;
	// routeIt.next(); routeSpeedIt.next(); routeDescIt.next();
	// while ( roadIt.hasNext() ) {
	// nextRoad = roadIt.next();
	// routeIt.next();
	// routeSpeedIt.next();
	// routeDescIt.next();
	//
	// c1 = currentRoad.getCoords();
	// c2 = nextRoad.getCoords();
	//
	// if (c1.equals(c2)) {
	// // Remove objects from the lists
	// roadIt.remove();
	// routeIt.remove();
	// routeSpeedIt.remove();
	// routeDescIt.remove();
	// }
	// else {
	// currentRoad = nextRoad;
	// }
	// }
	//
	// TempLogger.out("ROUTE AFTER REMOVING PAIRS");
	// this.printRoute();
	// }

	private void printRoute() {
		StringBuilder out = new StringBuilder();
		out.append("Printing route (" + this.agent.toString() + "). Current position in list is "
				+ this.currentPosition + " ('" + this.routeDescriptionX.get(this.currentPosition) + "')");
		for (int i = 0; i < this.routeX.size(); i++) {
			out.append("\t(" + this.agent.toString() + ") " + this.routeX.get(i).toString() + "\t"
					+ this.routeSpeedsX.get(i).toString() + "\t" + this.roadsX.get(i) + "\t"
					+ this.routeDescriptionX.get(i));
		}
		LOGGER.info(out.toString());
	}

	
	/**
	 * Find the nearest object in the given geography to the coordinate.
	 * 
	 * @param <T>
	 * @param x
	 *            The coordinate to search from
	 * @param geography
	 *            The given geography to look through
	 * @param closestPoints
	 *            An optional List that will be populated with the closest points to x (i.e. the results of
	 *            <code>distanceOp.closestPoints()</code>.
	 * @param searchDist
	 *            The maximum distance to search for objects in. Small distances are more efficient but larger ones are
	 *            less likely to find no objects.
	 * @return The nearest object.
	 * @throws RoutingException
	 *             If an object cannot be found.
	 */
	public static synchronized <T> T findNearestObject(Coordinate x, Geography<T> geography,
			List<Coordinate> closestPoints, GlobalVars.GEOGRAPHY_PARAMS.BUFFER_DISTANCE searchDist)
			throws RoutingException {
		if (x == null) {
			throw new RoutingException("The input coordinate is null, cannot find the nearest object");
		}

		T nearestObject = SpatialIndexManager.findNearestObject(geography, x, closestPoints, searchDist);

		// Old way without using spatial index:
		//
		// GeometryFactory geomFac = new GeometryFactory();
		// Point point = geomFac.createPoint(x);
		// // TODO Use an expanding buffer that starts small but gets bigger if no object is found.
		//
		// Geometry buffer = point.buffer(searchDist.dist);
		// double minDist = Double.MAX_VALUE;
		// T nearestObject = null;
		// for (T t : geography.getObjectsWithin(buffer.getEnvelopeInternal())) {
		// DistanceOp distOp = new DistanceOp(point, geography.getGeometry(t));
		// double thisDist = distOp.distance();
		// if (thisDist < minDist) {
		// minDist = thisDist;
		// nearestObject = t;
		// // Optionally record the closest points
		// if (closestPoints != null) {
		// closestPoints.clear();
		// // TODO clean conversion of array to List (don't have access
		// // to internet!)
		// Coordinate[] crds = distOp.closestPoints();
		// List<Coordinate> temp = new ArrayList(crds.length);
		// for (Coordinate c : crds)
		// temp.add(c);
		// closestPoints.addAll(temp);
		// }
		// } // if thisDist < minDist
		// } // for nearRoads
		if (nearestObject == null) {
			throw new RoutingException("Couldn't find an object close to these coordinates:\n\t" + x.toString());
		} else {
			return nearestObject;
		}
	}

	/**
	 * Returns the angle of the vector from p0 to p1 relative to the x axis
	 * <p>
	 * The angle will be between -Pi and Pi. I got this directly from the JUMP program source.
	 * 
	 * @return the angle (in radians) that p0p1 makes with the positive x-axis.
	 */
	public static synchronized double angle(Coordinate p0, Coordinate p1) {
		double dx = p1.x - p0.x;
		double dy = p1.y - p0.y;

		return Math.atan2(dy, dx);
	}

	/**
	 * The building which this Route is targeting
	 * 
	 * @return the destinationHouse
	 */
	public Building getDestinationBuilding() {
		if (this.destinationBuilding == null) {
			LOGGER.log(Level.WARNING, "Route: getDestinationBuilding(), warning, no destination building has "
					+ "been set. This might be ok, the agent might be supposed to be heading to a coordinate "
					+ "not a particular building(?)");
			return null;
		}
		return destinationBuilding;
	}

	/**
	 * The coordinate the route is targeting
	 * 
	 * @return the destination
	 */
	public Coordinate getDestination() {
		return this.destination;
	}

	/**
	 * Maintain a cache of all coordinates which are part of a road segment. Store the coords and all the road(s) they
	 * are part of.
	 * 
	 * @param coord
	 *            The coordinate which should be part of a road geometry
	 * @return The road(s) which the coordinate is part of or null if the coordinate is not part of any road
	 */
	private List<Road> getRoadFromCoordCache(Coordinate coord) {

		populateCoordCache(); // Check the cache has been populated
		return coordCache.get(coord);
	}

	/**
	 * Test if a coordinate is part of a road segment.
	 * 
	 * @param coord
	 *            The coordinate which we want to test
	 * @return True if the coordinate is part of a road segment
	 */
	private boolean coordOnRoad(Coordinate coord) {
		populateCoordCache(); // check the cache has been populated
		return coordCache.containsKey(coord);
	}

	private synchronized static void populateCoordCache() {

		double time = System.nanoTime();
		if (coordCache == null) { // Fist check cache has been created
			coordCache = new HashMap<Coordinate, List<Road>>();
			LOGGER.log(Level.FINER,
					"Route.populateCoordCache called for first time, creating new cache of all Road coordinates.");
		}
		if (coordCache.size() == 0) { // Now popualte it if it hasn't already
										// been populated
			LOGGER.log(Level.FINER, "Route.populateCoordCache: is empty, creating new cache of all Road coordinates.");

			for (Road r : ContextManager.roadContext.getObjects(Road.class)) {
				for (Coordinate c : ContextManager.roadProjection.getGeometry(r).getCoordinates()) {
					if (coordCache.containsKey(c)) {
						coordCache.get(c).add(r);
					} else {
						List<Road> l = new ArrayList<Road>();
						l.add(r);
						// TODO Need to put *new* coordinate here? Not use
						// existing one in memory?
						coordCache.put(new Coordinate(c), l);
					}
				}
			}

			LOGGER.log(Level.FINER, "... finished caching all road coordinates (in " + 0.000001
					* (System.nanoTime() - time) + "ms)");
		}
	}

	/**
	 * Find the buildings which can be accessed from the given road (the given road is the closest to the buildings).
	 * Uses a separate cache object which can be serialised so that the cache doesn't need to be rebuilt every time.
	 * 
	 * @param road
	 * @return
	 * @throws Exception
	 */
	private List<Building> getBuildingsOnRoad(Road road) throws Exception {
		if (buildingsOnRoadCache == null) {
			LOGGER.log(Level.FINER, "Route.getBuildingsOnRoad called for first time, "
					+ "creating cache of all roads and the buildings which are on them ...");
			// Create a new cache object, this will be read from disk if possible (which is why the
			// getInstance() method is used instead of the constructor.
			String gisDir = GlobalVars.GISDataDirectory;
			File buildingsFile = new File(gisDir + GlobalVars.BuildingShapefile);
			File roadsFile = new File(gisDir + GlobalVars.RoadShapefile);
			File serialLoc = new File(gisDir + ContextManager.getProperty(GlobalVars.BuildingsRoadsCache));
			buildingsOnRoadCache = BuildingsOnRoadCache.getInstance(ContextManager.buildingProjection, buildingsFile,
					ContextManager.roadProjection, roadsFile, serialLoc, new GeometryFactory());
		} // if not cached
		return buildingsOnRoadCache.get(road);
	}

	/**
	 * Calculate the distance (in meters) between two Coordinates, using the coordinate reference system that the
	 * roadGeography is using. For efficiency it can return the angle as well (in the range -0 to 2PI) if returnVals
	 * passed in as a double[2] (the distance is stored in index 0 and angle stored in index 1).
	 * 
	 * @param c1
	 * @param c2
	 * @param returnVals
	 *            Used to return both the distance and the angle between the two Coordinates. If null then the distance
	 *            is just returned, otherwise this array is populated with the distance at index 0 and the angle at
	 *            index 1.
	 * @return The distance between Coordinates c1 and c2.
	 */
	public static synchronized double distance(Coordinate c1, Coordinate c2, double[] returnVals) {
		// TODO check this now, might be different way of getting distance in new Simphony
		GeodeticCalculator calculator = new GeodeticCalculator(ContextManager.roadProjection.getCRS());
		calculator.setStartingGeographicPoint(c1.x, c1.y);
		calculator.setDestinationGeographicPoint(c2.x, c2.y);
		double distance = calculator.getOrthodromicDistance();
		if (returnVals != null && returnVals.length == 2) {
			returnVals[0] = distance;
			double angle = Math.toRadians(calculator.getAzimuth()); // Angle in range -PI to PI
			// Need to transform azimuth (in range -180 -> 180 and where 0 points north)
			// to standard mathematical (range 0 -> 360 and 90 points north)
			if (angle > 0 && angle < 0.5 * Math.PI) { // NE Quadrant
				angle = 0.5 * Math.PI - angle;
			} else if (angle >= 0.5 * Math.PI) { // SE Quadrant
				angle = (-angle) + 2.5 * Math.PI;
			} else if (angle < 0 && angle > -0.5 * Math.PI) { // NW Quadrant
				angle = (-1 * angle) + 0.5 * Math.PI;
			} else { // SW Quadrant
				angle = -angle + 0.5 * Math.PI;
			}
			returnVals[1] = angle;
		}
		return distance;
	}

	/**
	 * Converts a distance lat/long distance (e.g. returned by DistanceOp) to meters. The calculation isn't very
	 * accurate because (probably) it assumes that the distance is between two points that lie exactly on a line of
	 * longitude (i.e. one is exactly due north of the other). For this reason the value shouldn't be used in any
	 * calculations which is why it's returned as a String.
	 * 
	 * @param dist
	 *            The distance (as returned by DistanceOp) to convert to meters
	 * @return The approximate distance in meters as a String (to discourage using this approximate value in
	 *         calculations).
	 * @throws Exception
	 * @see com.vividsolutions.jts.operation.distance.DistanceOp
	 */
	public static synchronized String distanceToMeters(double dist) throws Exception {
		// Works by creating two coords (close to a randomly chosen object) which are a certain distance apart
		// then using similar method as other distance() function
		GeodeticCalculator calculator = new GeodeticCalculator(ContextManager.roadProjection.getCRS());
		Coordinate c1 = ContextManager.buildingContext.getRandomObject().getCoords();
		calculator.setStartingGeographicPoint(c1.x, c1.y);
		calculator.setDestinationGeographicPoint(c1.x, c1.y + dist);
		return String.valueOf(calculator.getOrthodromicDistance());
	}

	public void clearCaches() {
		if (coordCache != null)
			coordCache.clear();
		if (nearestRoadCoordCache != null) {
			nearestRoadCoordCache.clear();
			nearestRoadCoordCache = null;
		}
		if (buildingsOnRoadCache != null) {
			buildingsOnRoadCache.clear();
			buildingsOnRoadCache = null;
		}
		// if (routeCache != null) {
		// routeCache.clear();
		// routeCache = null;
		// }
		// if (routeDistanceCache != null) {
		// routeDistanceCache.clear();
		// routeDistanceCache = null;
		// }
	}

	// /**
	// * Will add the given buildings to the awareness space of the Burglar who is
	// * being controlled by this Route. Also tells the burglar which buildings
	// * have been passed if appropriate, this is needed for agents who are
	// * currently looking for a burglary target.
	// *
	// * @param buildings
	// * A list of buildings
	// */
	// @SuppressWarnings("unchecked")
	// protected <T> void passedObjects(List<T> objects, Class<T> clazz) {
	// this.agent.addToMemory(objects, clazz);
	// if (clazz.isAssignableFrom(Building.class)) {
	// // System.out.println("Route.passedObjects(): "+objects.toString());
	// this.agent.buildingsPassed((List<Building>) objects);
	// }
	// }

	/**
	 * Will add the given buildings to the awareness space of the Burglar who is being controlled by this Route.
	 * 
	 * @param buildings
	 *            A list of buildings
	 */
	protected <T> void passedObject(T object, Class<T> clazz) {
		List<T> list = new ArrayList<T>(1);
		list.add(object);
		this.agent.addToMemory(list, clazz);
	}

}

/* ************************************************************************ */

/**
 * Class can be used to store a cache of all roads and the buildings which can be accessed by them (a map of
 * Road<->List<Building>. Buildings are 'accessed' by travelling to the road which is nearest to them.
 * <p>
 * This class can be serialised so that if the GIS data doesn't change it doesn't have to be re-calculated each time.
 * However, the Roads and Buildings themselves cannot be serialised because if they are there will be two sets of Roads
 * and BUildings, the serialised ones and those that were created when the model was initialised. To get round this, an
 * array which contains the road and building ids is serialised and the cache is re-built using these caches ids after
 * reading the serialised cache. This means that the id's given to Buildings and Roads must not change (i.e.
 * auto-increment numbers are no good because if a simulation is restarted the static auto-increment variables will not
 * be reset to 0).
 * 
 * @author Nick Malleson
 */
class BuildingsOnRoadCache implements Serializable {

	private static Logger LOGGER = Logger.getLogger(BuildingsOnRoadCache.class.getName());

	private static final long serialVersionUID = 1L;
	// The actual cache, this isn't serialised
	private static transient Hashtable<Road, ArrayList<Building>> theCache;
	// The 'reference' cache, stores the building and road ids and can be
	// serialised
	private Hashtable<String, ArrayList<String>> referenceCache;

	// Check that the road/building data hasn't been changed since the cache was
	// last created
	private File buildingsFile;
	private File roadsFile;
	// The location that the serialised object might be found.
	private File serialisedLoc;
	// The time that this cache was created, can be used to check data hasn't
	// changed since
	private long createdTime;

	// Private constructor because getInstance() should be used
	private BuildingsOnRoadCache(Geography<Building> buildingEnvironment, File buildingsFile,
			Geography<Road> roadEnvironment, File roadsFile, File serialisedLoc, GeometryFactory geomFac)
			throws Exception {
		// this.buildingEnvironment = buildingEnvironment;
		// this.roadEnvironment = roadEnvironment;
		this.buildingsFile = buildingsFile;
		this.roadsFile = roadsFile;
		this.serialisedLoc = serialisedLoc;
		theCache = new Hashtable<Road, ArrayList<Building>>();
		this.referenceCache = new Hashtable<String, ArrayList<String>>();

		LOGGER.log(Level.FINE, "BuildingsOnRoadCache() creating new cache with data (and modification date):\n\t"
				+ this.buildingsFile.getAbsolutePath() + " (" + new Date(this.buildingsFile.lastModified()) + ")\n\t"
				+ this.roadsFile.getAbsolutePath() + " (" + new Date(this.roadsFile.lastModified()) + ")\n\t"
				+ this.serialisedLoc.getAbsolutePath());

		populateCache(buildingEnvironment, roadEnvironment, geomFac);
		this.createdTime = new Date().getTime();
		serialise();
	}

	public void clear() {
		theCache.clear();
		this.referenceCache.clear();

	}

	private void populateCache(Geography<Building> buildingEnvironment, Geography<Road> roadEnvironment,
			GeometryFactory geomFac) throws Exception {
		double time = System.nanoTime();
		for (Building b : buildingEnvironment.getAllObjects()) {
			// Find the closest road to this building
			Geometry buildingPoint = geomFac.createPoint(b.getCoords());
			double minDistance = Double.MAX_VALUE;
			Road closestRoad = null;
			double distance;
			Envelope e = buildingPoint.buffer(GlobalVars.GEOGRAPHY_PARAMS.BUFFER_DISTANCE.LARGE.dist)
					.getEnvelopeInternal();
			for (Road r : roadEnvironment.getObjectsWithin(e)) {
				distance = DistanceOp.distance(buildingPoint, ContextManager.roadProjection.getGeometry(r));
				if (distance < minDistance) {
					minDistance = distance;
					closestRoad = r;
				}
			} // for roads
				// Found the closest road, add the information to the cache
			if (theCache.containsKey(closestRoad)) {
				theCache.get(closestRoad).add(b);
				this.referenceCache.get(closestRoad.getIdentifier()).add(b.getIdentifier());
			} else {
				ArrayList<Building> l = new ArrayList<Building>();
				l.add(b);
				theCache.put(closestRoad, l);
				ArrayList<String> l2 = new ArrayList<String>();
				l2.add(b.getIdentifier());
				this.referenceCache.put(closestRoad.getIdentifier(), l2);
			}
		} // for buildings
		int numRoads = theCache.keySet().size();
		int numBuildings = 0;
		for (List<Building> l : theCache.values())
			numBuildings += l.size();
		LOGGER.log(Level.FINER, "Finished caching roads and buildings. Cached " + numRoads + " roads and "
				+ numBuildings + " buildings in " + 0.000001 * (System.nanoTime() - time) + "ms");
	}

	public List<Building> get(Road r) {
		return theCache.get(r);
	}

	private void serialise() throws IOException {
		double time = System.nanoTime();
		FileOutputStream fos = null;
		ObjectOutputStream out = null;
		try {
			if (!this.serialisedLoc.exists())
				this.serialisedLoc.createNewFile();
			fos = new FileOutputStream(this.serialisedLoc);
			out = new ObjectOutputStream(fos);
			out.writeObject(this);
			out.close();
		} catch (IOException ex) {
			if (serialisedLoc.exists())
				serialisedLoc.delete(); // delete to stop problems loading incomplete file next time
			throw ex;
		}
		LOGGER.log(Level.FINER, "Serialised BuildingsOnRoadCache to " + this.serialisedLoc.getAbsolutePath() + " in ("
				+ 0.000001 * (System.nanoTime() - time) + "ms)");
	}

	/**
	 * Used to create a new BuildingsOnRoadCache object. This function is used instead of the constructor directly so
	 * that the class can check if there is a serialised version on disk already. If not then a new one is created and
	 * returned.
	 * 
	 * @param buildingEnv
	 * @param buildingsFile
	 * @param roadEnv
	 * @param roadsFile
	 * @param serialisedLoc
	 * @param geomFac
	 * @return
	 * @throws Exception
	 */
	public synchronized static BuildingsOnRoadCache getInstance(Geography<Building> buildingEnv, File buildingsFile,
			Geography<Road> roadEnv, File roadsFile, File serialisedLoc, GeometryFactory geomFac) throws Exception {
		double time = System.nanoTime();
		// See if there is a cache object on disk.
		if (serialisedLoc.exists()) {
			FileInputStream fis = null;
			ObjectInputStream in = null;
			BuildingsOnRoadCache bc = null;
			try {
				fis = new FileInputStream(serialisedLoc);
				in = new ObjectInputStream(fis);
				bc = (BuildingsOnRoadCache) in.readObject();
				in.close();

				// Check that the cache is representing the correct data and the
				// modification dates are ok
				// (WARNING, if this class is re-compiled the serialised object
				// will still be read in).
				if (!buildingsFile.getAbsolutePath().equals(bc.buildingsFile.getAbsolutePath())
						|| !roadsFile.getAbsolutePath().equals(bc.roadsFile.getAbsolutePath())
						|| buildingsFile.lastModified() > bc.createdTime || roadsFile.lastModified() > bc.createdTime) {
					LOGGER.log(Level.FINER, "BuildingsOnRoadCache, found serialised object but it doesn't match the "
							+ "data (or could have different modification dates), will create a new cache.");
				} else {
					// Have found a useable serialised cache. Now use the cached
					// list of id's to construct a
					// new cache of buildings and roads.
					// First need to buld list of existing roads and buildings
					Hashtable<String, Road> allRoads = new Hashtable<String, Road>();
					for (Road r : roadEnv.getAllObjects())
						allRoads.put(r.getIdentifier(), r);
					Hashtable<String, Building> allBuildings = new Hashtable<String, Building>();
					for (Building b : buildingEnv.getAllObjects())
						allBuildings.put(b.getIdentifier(), b);

					// Now create the new cache
					theCache = new Hashtable<Road, ArrayList<Building>>();

					for (String roadId : bc.referenceCache.keySet()) {
						ArrayList<Building> buildings = new ArrayList<Building>();
						for (String buildingId : bc.referenceCache.get(roadId)) {
							buildings.add(allBuildings.get(buildingId));
						}
						theCache.put(allRoads.get(roadId), buildings);
					}
					LOGGER.log(Level.FINER, "BuildingsOnRoadCache, found serialised cache, returning it (in "
							+ 0.000001 * (System.nanoTime() - time) + "ms)");
					return bc;
				}
			} catch (IOException ex) {
				if (serialisedLoc.exists())
					serialisedLoc.delete(); // delete to stop problems loading incomplete file next tinme
				throw ex;
			} catch (ClassNotFoundException ex) {
				if (serialisedLoc.exists())
					serialisedLoc.delete();
				throw ex;
			}

		}

		// No serialised object, or got an error when opening it, just create a
		// new one
		return new BuildingsOnRoadCache(buildingEnv, buildingsFile, roadEnv, roadsFile, serialisedLoc, geomFac);
	}
}

/* ************************************************************************ */

/**
 * Caches the nearest road Coordinate to every building for efficiency (agents usually/always need to get from the
 * centroids of houses to/from the nearest road).
 * <p>
 * This class can be serialised so that if the GIS data doesn't change it doesn't have to be re-calculated each time.
 * 
 * @author Nick Malleson
 */
class NearestRoadCoordCache implements Serializable {

	private static Logger LOGGER = Logger.getLogger(NearestRoadCoordCache.class.getName());

	private static final long serialVersionUID = 1L;
	private Hashtable<Coordinate, Coordinate> theCache; // The actual cache
	// Check that the road/building data hasn't been changed since the cache was
	// last created
	private File buildingsFile;
	private File roadsFile;
	// The location that the serialised object might be found.
	private File serialisedLoc;
	// The time that this cache was created, can be used to check data hasn't
	// changed since
	private long createdTime;

	private GeometryFactory geomFac;

	private NearestRoadCoordCache(Geography<Building> buildingEnvironment, File buildingsFile,
			Geography<Road> roadEnvironment, File roadsFile, File serialisedLoc, GeometryFactory geomFac)
			throws Exception {

		this.buildingsFile = buildingsFile;
		this.roadsFile = roadsFile;
		this.serialisedLoc = serialisedLoc;
		this.theCache = new Hashtable<Coordinate, Coordinate>();
		this.geomFac = geomFac;

		LOGGER.log(Level.FINE, "NearestRoadCoordCache() creating new cache with data (and modification date):\n\t"
				+ this.buildingsFile.getAbsolutePath() + " (" + new Date(this.buildingsFile.lastModified()) + ") \n\t"
				+ this.roadsFile.getAbsolutePath() + " (" + new Date(this.roadsFile.lastModified()) + "):\n\t"
				+ this.serialisedLoc.getAbsolutePath());

		populateCache(buildingEnvironment, roadEnvironment);
		this.createdTime = new Date().getTime();
		serialise();
	}

	public void clear() {
		this.theCache.clear();
	}

	private void populateCache(Geography<Building> buildingEnvironment, Geography<Road> roadEnvironment)
			throws Exception {
		double time = System.nanoTime();
		theCache = new Hashtable<Coordinate, Coordinate>();
		// Iterate over every building and find the nearest road point
		for (Building b : buildingEnvironment.getAllObjects()) {
			List<Coordinate> nearestCoords = new ArrayList<Coordinate>();
			Route.findNearestObject(b.getCoords(), roadEnvironment, nearestCoords,
					GlobalVars.GEOGRAPHY_PARAMS.BUFFER_DISTANCE.LARGE);
			// Two coordinates returned by closestPoints(), need to find the one
			// which isn't the building coord
			Coordinate nearestPoint = null;
			for (Coordinate c : nearestCoords) {
				if (!c.equals(b.getCoords())) {
					nearestPoint = c;
					break;
				}
			} // for nearestCoords
			if (nearestPoint == null) {
				throw new Exception("Route.getNearestRoadCoord() error: couldn't find a road coordinate which "
						+ "is close to building " + b.toString());
			}
			theCache.put(b.getCoords(), nearestPoint);
		}// for Buildings
		LOGGER.log(Level.FINER, "Finished caching nearest roads (" + (0.000001 * (System.nanoTime() - time)) + "ms)");
	} // if nearestRoadCoordCache = null;

	/**
	 * 
	 * @param c
	 * @return
	 * @throws Exception
	 */
	public Coordinate get(Coordinate c) throws Exception {
		if (c == null) {
			throw new Exception("Route.NearestRoadCoordCache.get() error: the given coordinate is null.");
		}
		double time = System.nanoTime();
		Coordinate nearestCoord = this.theCache.get(c);
		if (nearestCoord != null) {
			LOGGER.log(Level.FINER, "NearestRoadCoordCache.get() (using cache) - ("
					+ (0.000001 * (System.nanoTime() - time)) + "ms)");
			return nearestCoord;
		}
		// If get here then the coord is not in the cache, agent not starting their journey from a house, search for
		// it manually. Search all roads in the vicinity, looking for the point which is nearest the person
		double minDist = Double.MAX_VALUE;
		Coordinate nearestPoint = null;
		Point coordGeom = this.geomFac.createPoint(c);

		// Note: could use an expanding envelope that starts small and gets bigger
		double bufferDist = GlobalVars.GEOGRAPHY_PARAMS.BUFFER_DISTANCE.LARGE.dist;
		double bufferMultiplier = 1.0;
		Envelope searchEnvelope = coordGeom.buffer(bufferDist * bufferMultiplier).getEnvelopeInternal();
		StringBuilder debug = new StringBuilder(); // incase the operation fails

		for (Road r : ContextManager.roadProjection.getObjectsWithin(searchEnvelope)) {

			DistanceOp distOp = new DistanceOp(coordGeom, ContextManager.roadProjection.getGeometry(r));
			double thisDist = distOp.distance();
			// BUG?: if an agent is on a really long road, the long road will not be found by getObjectsWithin because
			// it is not within the buffer
			debug.append("\troad ").append(r.toString()).append(" is ").append(thisDist).append(
					" distance away (at closest point). ");

			if (thisDist < minDist) {
				minDist = thisDist;
				Coordinate[] closestPoints = distOp.closestPoints();
				// Two coordinates returned by closestPoints(), need to find the
				// one which isn''t the coord parameter
				debug.append("Closest points (").append(closestPoints.length).append(") are: ").append(
						Arrays.toString(closestPoints));
				nearestPoint = (c.equals(closestPoints[0])) ? closestPoints[1] : closestPoints[0];
				debug.append("Nearest point is ").append(nearestPoint.toString());
				nearestPoint = (c.equals(closestPoints[0])) ? closestPoints[1] : closestPoints[0];
			} // if thisDist < minDist
			debug.append("\n");

		} // for nearRoads

		if (nearestPoint != null) {
			LOGGER.log(Level.FINER, "NearestRoadCoordCache.get() (not using cache) - ("
					+ (0.000001 * (System.nanoTime() - time)) + "ms)");
			return nearestPoint;
		}
		/* IF HERE THEN ERROR, PRINT DEBUGGING INFO */
		StringBuilder debugIntro = new StringBuilder(); // Some extra info for debugging
		debugIntro.append("Route.NearestRoadCoordCache.get() error: couldn't find a coordinate to return.\n");
		Iterable<Road> roads = ContextManager.roadProjection.getObjectsWithin(searchEnvelope);
		debugIntro.append("Looking for nearest road coordinate around ").append(c.toString()).append(".\n");
		debugIntro.append("RoadEnvironment.getObjectsWithin() returned ").append(
				ContextManager.sizeOfIterable(roads) + " roads, printing debugging info:\n");
		debugIntro.append(debug);
		throw new Exception(debugIntro.toString());

	}

	private void serialise() throws IOException {
		double time = System.nanoTime();
		FileOutputStream fos = null;
		ObjectOutputStream out = null;
		try {
			if (!this.serialisedLoc.exists())
				this.serialisedLoc.createNewFile();
			fos = new FileOutputStream(this.serialisedLoc);
			out = new ObjectOutputStream(fos);
			out.writeObject(this);
			out.close();
		} catch (IOException ex) {
			if (serialisedLoc.exists()) {
				// delete to stop problems loading incomplete file next time
				serialisedLoc.delete();
			}
			throw ex;
		}
		LOGGER.log(Level.FINE, "... serialised NearestRoadCoordCache to " + this.serialisedLoc.getAbsolutePath()
				+ " in (" + 0.000001 * (System.nanoTime() - time) + "ms)");
	}

	/**
	 * Used to create a new BuildingsOnRoadCache object. This function is used instead of the constructor directly so
	 * that the class can check if there is a serialised version on disk already. If not then a new one is created and
	 * returned.
	 * 
	 * @param buildingEnv
	 * @param buildingsFile
	 * @param roadEnv
	 * @param roadsFile
	 * @param serialisedLoc
	 * @param geomFac
	 * @return
	 * @throws Exception
	 */
	public synchronized static NearestRoadCoordCache getInstance(Geography<Building> buildingEnv, File buildingsFile,
			Geography<Road> roadEnv, File roadsFile, File serialisedLoc, GeometryFactory geomFac) throws Exception {
		double time = System.nanoTime();
		// See if there is a cache object on disk.
		if (serialisedLoc.exists()) {
			FileInputStream fis = null;
			ObjectInputStream in = null;
			NearestRoadCoordCache ncc = null;
			try {

				fis = new FileInputStream(serialisedLoc);
				in = new ObjectInputStream(fis);
				ncc = (NearestRoadCoordCache) in.readObject();
				in.close();

				// Check that the cache is representing the correct data and the
				// modification dates are ok
				if (!buildingsFile.getAbsolutePath().equals(ncc.buildingsFile.getAbsolutePath())
						|| !roadsFile.getAbsolutePath().equals(ncc.roadsFile.getAbsolutePath())
						|| buildingsFile.lastModified() > ncc.createdTime || roadsFile.lastModified() > ncc.createdTime) {
					LOGGER.log(Level.FINE, "BuildingsOnRoadCache, found serialised object but it doesn't match the "
							+ "data (or could have different modification dates), will create a new cache.");
				} else {
					LOGGER.log(Level.FINER, "NearestRoadCoordCache, found serialised cache, returning it (in "
							+ 0.000001 * (System.nanoTime() - time) + "ms)");
					return ncc;
				}
			} catch (IOException ex) {
				if (serialisedLoc.exists())
					serialisedLoc.delete(); // delete to stop problems loading incomplete file next tinme
				throw ex;
			} catch (ClassNotFoundException ex) {
				if (serialisedLoc.exists())
					serialisedLoc.delete();
				throw ex;
			}

		}

		// No serialised object, or got an error when opening it, just create a new one
		return new NearestRoadCoordCache(buildingEnv, buildingsFile, roadEnv, roadsFile, serialisedLoc, geomFac);
	}

}

/**
 * Used to cache routes. Saves the origin and destination coords and the transport available to the agent (if transport
 * changes then the agent might have to create a new route.
 * 
 * @author Nick Malleson
 */
class CachedRoute {
	private List<Coordinate> theRoute;
	private List<Double> routeSpeeds;
	private List<String> routeDescriptions;
	private List<Road> roads;
	private Coordinate origin;
	private Coordinate destination;
	private List<String> transportAvailable;
	// Used to generate hash codes (each route must have unique ID)
	private static int uniqueRouteCacheID;
	private int uniqueID;

	public CachedRoute(Coordinate origin, Coordinate destination, List<String> transportAvailable) {
		this.origin = origin;
		this.destination = destination;
		this.transportAvailable = transportAvailable;
		this.uniqueID = CachedRoute.uniqueRouteCacheID++;
	}

	public void setRoute(List<Coordinate> theRoute, List<Road> roads, List<Double> routeSpeeds,
			List<String> routeDescriptions) {
		this.theRoute = theRoute;
		this.roads = roads;
		this.routeSpeeds = routeSpeeds;
		this.routeDescriptions = routeDescriptions;
	}

	public List<Coordinate> getRoute() {
		return this.theRoute;
	}

	public List<Double> getRouteSpeeds() {
		return this.routeSpeeds;
	}

	public List<Road> getRoads() {
		return this.roads;
	}

	public List<String> getDescriptions() {
		return this.routeDescriptions;
	}

	@Override
	public String toString() {
		return "CachedRoute " + this.uniqueID;
	}

	/**
	 * Returns true if input object is a CachedRoute and the the origin, destination and transport available are the
	 * same as this CachedRoute
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof CachedRoute) {
			CachedRoute r = (CachedRoute) obj;
			return (r.origin.equals(this.origin)) && (r.destination.equals(this.destination))
					&& (r.transportAvailable.equals(this.transportAvailable));
		} else {
			return false;
		}
	}

	/**
	 * Returns: <code>Float.floatToIntBits((float)(this.origin.getX()+this.origin.getY()))</code>
	 */
	@Override
	public int hashCode() {
		return Float.floatToIntBits((float) (this.origin.x + this.origin.y));
	}
}

/**
 * Used to cache route distances. Saves the origin and destination coords and the transport available to the agent (if
 * transport changes then the agent might have to create a new route).
 * 
 * @author Nick Malleson
 */
class CachedRouteDistance {
	private Coordinate origin;
	private Coordinate destination;
	private List<String> transportAvailable;
	private static int uniqueRouteCacheID; // Used to generate hash codes (each
											// route must have unique ID)
	private int uniqueID;

	// private List<Coord> theRoute; // The actual route (a list of coords)

	public CachedRouteDistance(Coordinate origin, Coordinate destination, List<String> transportAvailable) {
		this.origin = origin;
		this.destination = destination;
		this.transportAvailable = transportAvailable;
		this.uniqueID = CachedRouteDistance.uniqueRouteCacheID++;
	}

	@Override
	public String toString() {
		return "CachedRouteDistance " + this.uniqueID;
	}

	/**
	 * Returns true if input object is a CachedRoute and the the origin, destination and transport available are the
	 * same as this CachedRoute. Because routes are non-directional the origins and destinations are interchangeable.
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof CachedRouteDistance) {
			CachedRouteDistance r = (CachedRouteDistance) obj;
			return ((r.origin.equals(this.origin) && r.destination.equals(this.destination)) || (r.origin
					.equals(this.destination) && r.destination.equals(this.origin)))
					&& r.transportAvailable.equals(this.transportAvailable);
		} else {
			return false;
		}
	}

	/**
	 * Returns: <code>Float.floatToIntBits((float)(this.origin.getX()+this.origin.getY()))</code>
	 */
	@Override
	public int hashCode() {
		return Float.floatToIntBits((float) (this.origin.x + this.origin.y));
	}
}

/**
 * Convenience class for creating deep copies of lists/maps (copies the values stored as well). Haven't made this
 * generic because need access to constructors to create new objects (e.g. new Coord(c))
 */
final class Cloning {

	public static List<Coordinate> copy(List<Coordinate> in) {

		List<Coordinate> out = new ArrayList<Coordinate>(in.size());
		for (Coordinate c : in) {
			// TODO Check this Coordinate constructor does what I expect it to
			out.add(new Coordinate(c));
		}
		return out;
	}

	// Not used now that route speeds are a list, not a map
	// public static LinkedHashMap<Coordinate, Double>
	// copy(LinkedHashMap<Coordinate, Double> in) {
	//
	// LinkedHashMap<Coordinate, Double> out = new LinkedHashMap<Coordinate,
	// Double>(in.size());
	// for (Coordinate c : in.keySet()) {
	// out.put(c, in.get(c));
	// }
	// return out;
	// }

}
