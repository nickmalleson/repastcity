/*
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.operation.distance.DistanceOp;

import repast.simphony.engine.environment.RunEnvironment;
import repastcity3.environment.Building;
import repastcity3.environment.Community;
import repastcity3.environment.Route;
import repastcity3.environment.SpatialIndexManager;
import repastcity3.main.ContextManager;
import repastcity3.main.Functions;
import repastcity3.main.GlobalVars;

// TODO: rename to 'DumbBurglar' (and also change BurglarFactory because random method only creates DefaultAgent..)
public class DefaultAgent implements IAgent {

	private static Logger LOGGER = Logger.getLogger(DefaultAgent.class.getName());
	
	// A variable used to represent how motivated the agent is. This can increase as the agent becomes more
	// desperate for burglary but at the moment it is left static
	private final double motiveIntensity = 0.3;

	private Building home; // The building where the agent lives
	private Community community; // The community where the agent lives
	private Route route; // An object to move the agent around the world
	
	// Remmber the number of burglaries per community
	private Map<Community, Integer> burgledCommunities = new HashMap<Community, Integer>();
	
	// Store the buildings that the agent burgled
	private List<Building> burgledBuildings = new ArrayList<Building>();
	 
	// Need to keep track of current day to make sure the agent burgle (leave home) once per day
	// If this is less than the day counter then the agent hasn't burgled
	private int currentDay = -1;  
	
	private Community targetArea = null; // The target community chosen from which to search for a victim
	private Building targetHouse = null; // The house within the target area to actually travel to

	private static int uniqueID = 0;
	private int id; // An auto-generated numerical id for agents who do not have an identifier read in from GIS data
	private String identifier = null; // An identifier read in from data (same as buildings and roads).

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
			// Need to start a burglary. Choose which area to travel to.
			this.targetArea = this.chooseTargetArea();

			// Have chosen target area, now choose a house randomly from the area to travel to.
			// The chooseTargetArea() won't return a community without a house in it.
			this.targetHouse = ContextManager.randomChoice(this.targetArea.getBuildings());

			this.route = new Route(this, this.targetHouse.getCoords(), null);
			this.currentState = AGENT_STATE.TRAVEL_TO_TARGET;

			break;

		case TRAVEL_TO_TARGET:
			if (this.route.atDestination()) {
				// Have reached destination but haven't found a target. Just choose another victim.
				// TODO Move to SEARCHING activity and start a nice search
				this.currentState = AGENT_STATE.BURGLARY_INIT;
				
			}
			else { // Keep going towards target, possibly burgling one of the passed houses.

				List<Building> passedBuildings = new ArrayList<Building>();
				try {
					this.route.travel(passedBuildings);
				}
				catch (Exception e) {
					System.err.println("Problem routing for agent "+this.toString()+" travelling to " +
							"the burglary target "+this.targetHouse.toString()+
							" in community "+this.targetArea.toString()+". Error will be propagated.");
					throw e;
				}
				// For info, tell each house it was passed
				for (Building b:passedBuildings) {
					b.incrementTimesPassed();
				}				
				// Look for a victim
				Building victim = this.chooseVictim(passedBuildings);
				
				if (victim == null) {
					// No victim was found, continue travelling in the next iteration
				}
				else { // Successful burglary. Store some info and then now go home.
					victim.incrementNumBurglaries(); // Tell the building that it has been burgled
					this.burgledBuildings.add(victim); // Remember that the burglar burgled the house - (not sure if this is still necessary now that results are stoorde in Results object)
					ContextManager.getResults().saveBurglaryInfo(this, 
							(int)RunEnvironment.getInstance().getCurrentSchedule().getTickCount(), ContextManager.realTime, victim);
					
					this.currentState = AGENT_STATE.TRAVELLING_HOME;
					this.route = new Route(this, this.home.getCoords(), this.home);
					LOGGER.log(Level.INFO, "Agent " + this.id + " has burgled "+victim.toString());
				}
			} // elseif atDestination()				

			break;

		case SEARCHING:
			// TODO: Implement a nice search. For now this is never actually called.. 

			break;
		}



	} // step()
	
	/* To choose a target area, this function runs through each community and collects all the parameters which will 
	 * make up its overall attractiveness, remembering the min/max values of each for normalisation.
	 * Then run through the communities again, calculate overall attractiveness and store, sorted, in a
	 * list. To do this I maintain a list of communities and a 2D list of doubles which stores the community
	 * index and the parameters which make up the overall attractiveness. Then it is easy to run through the
	 * doubles list calculating overall attractiveness (now that min/max parameter values are known) and then
	 * sort it on the attractiveness. Once the doubles list has been sorted (very quick because it only 
	 * contains primitive types) can use the element which stores the original community index to identify
	 * the associated community in the communities list. Sounds complicated but just want a list of communities
	 * sorted by overall attractiveness!*/
	private Community chooseTargetArea() throws NoSuchElementException, Exception {
		
		// Create a list of Communities with houses, not all will necessary have buildings in them and must
		// therefore be ignored as the burglar will want to travel to a house within the community.
		List<Community> goodComs = new ArrayList<Community>();
		for (Community c:ContextManager.communityContext.getObjects(Community.class)) {
			if (c.getBuildings().size()>0) {
				goodComs.add(c);
			}
		}
		int numCommunities = goodComs.size();
		
		Community[] communities = goodComs.toArray(new Community[numCommunities]);
		// Community params: { { overallAttract, index, dist, attract, socialDiff, prevSucc }, { .. } ... { .. } }
		double[][] communityParams = new double[numCommunities][6];
		double totalAttract = 0;	// Needed to pick most attractive community later

		// Need to store max/min values for normalisation (index 0 is min value, 1 is max)
		double[] distMM = new double[2]; distMM[0] = Double.MAX_VALUE; distMM[1] = 0;
		double[] attractMM = new double[2]; attractMM[0] = Double.MAX_VALUE; attractMM[1] = 0;
		double[] socialDiffMM = new double[2]; socialDiffMM[0] = Double.MAX_VALUE; socialDiffMM[1] = 0;
		double[] prevSuccMM = new double[2]; prevSuccMM[0] = Double.MAX_VALUE; prevSuccMM[1] = 0;
		
		// Find the community the agent is currently in, this will affect the distance calculation.
		Community currentCommunity = SpatialIndexManager.findObjectAt(ContextManager.communityProjection, 
				(Point)ContextManager.agentGeography.getGeometry(this), GlobalVars.GEOGRAPHY_PARAMS.BUFFER_DISTANCE.SMALL);
//		System.out.println("Current community (for agent "+this.toString()+") is "+currentCommunity.toString());
		
		// Loop over every Community to calculate min/max values and remember each parameter
		// value for each community so they don't need to be recalculated when calculating overall attractiveness 
		for (int i=0; i<communities.length; i++) { 
			Community c = communities[i];
			communityParams[i][1] = i; // Second element stores the original index, used to link to communities list.
			double dist = -1; // The distance to c, need to check if the agent is in c at the moment or not
			if (c.equals(currentCommunity)) {
				dist = c.getAverageDistance(); // The average distance to every point in the area
			}
			else {
				dist = DistanceOp.distance(ContextManager.agentGeography.getGeometry(this),
						ContextManager.communityProjection.getGeometry(c) );
				if (dist<0) {
					throw new Exception("For some reason the distance from the agent's location ("+
							ContextManager.agentGeography.getGeometry(this) + ") to the target community ("+
							c.toString()+" is less than 0: "+dist);
				}
			}

			checkMinMax(dist, distMM);  // Remember the min/max values for distance
			communityParams[i][2] = dist; // Remember this community's distance value (so not recalculated again)

			double attract = c.getAttractiveness();
			checkMinMax(attract, attractMM);
			communityParams[i][3] = attract;

			double socialDiff = c.compare(this.getHomeCommunity());
			checkMinMax(socialDiff, socialDiffMM);
			communityParams[i][4] = socialDiff;

			// Num. of successful burglaries (will be zero if the community isn't in the list of previous burglaries
			double prevSucc = this.burgledCommunities.containsKey(c) ? this.burgledCommunities.get(c) : 0; 
			checkMinMax(prevSucc, prevSuccMM);
			communityParams[i][5] = prevSucc;

		} // for communities

		// Now loop again and calculate overall attractiveness as the individual parameters can be normalised
		for (int i=0; i<communities.length; i++) {
			double distN = Functions.normalise(communityParams[i][2], distMM);
			double attractN = Functions.normalise(communityParams[i][3], attractMM);
			// Have to reverse socialDiff because 1 = very different (should lower attractiveness).
			double socialDiffN = Functions.normalise(communityParams[i][4], socialDiffMM);
			double prevSuccN = Functions.normalise(communityParams[i][5], prevSuccMM);
			
			BurglaryWeights bw = ContextManager.getBurglaryWeights(); // Object holds the weights of each parameter
			
			double overallAttract =  
				( bw.getWeight(BurglaryWeights.BURGLARY_WEIGHTS.DIST_W) * distN ) +
				( bw.getWeight(BurglaryWeights.BURGLARY_WEIGHTS.ATTRACT_W) * attractN ) +
				( bw.getWeight(BurglaryWeights.BURGLARY_WEIGHTS.SOCIALDIFF_W) * socialDiffN ) +
				( bw.getWeight(BurglaryWeights.BURGLARY_WEIGHTS.PREVSUCC_W) * prevSuccN );
			communityParams[i][0] = overallAttract;
			totalAttract += overallAttract; // Used for roulette wheel selection
			
//			System.out.println(communities[i].toString()+"\n"+
//					"\tdistN: "+distN+", "+distMM[0]+ " -> "+ distMM[1]+
//					"\t(distance: "+DistanceOp.distance(ContextManager.agentGeography.getGeometry(this),
//							ContextManager.communityProjection.getGeometry(communities[i]) )+")"+
//					"\n\tattractN: "+attractN+", "+attractMM[0]+ " -> "+ attractMM[1]+
//					"\n\tsocialDiffN: "+socialDiffN+", "+socialDiffMM[0]+ " -> "+ socialDiffMM[1]+
//					"\n\tprevSuccN: " + prevSuccN+", "+prevSuccMM[0]+ " -> "+ prevSuccMM[1]+
//					"\n\tOverall attract: "+overallAttract);
		}

		// Sort the list of community parameters on the overall attractiveness from low to high
		double[][] sortedCommunityParams = Functions.sort(communityParams, false);			
//		System.out.println("Communities in order of attractiveness: ");
//		for (int i=0; i<sortedCommunityParams.length; i++) {
//			System.out.println("\t"+communities[(int)(sortedCommunityParams[i][1])].toString()+":"+sortedCommunityParams[i][0]);
//		}

		// Now use RWS to find a community
		double roulette = ContextManager.nextDoubleFromTo(0, totalAttract);
		//		System.out.println("roulette val: "+roulette);
		double currentAttract = 0;
		for (int i=0; i<numCommunities; i++) {
			currentAttract += sortedCommunityParams[i][0]; // index 0 stores the overall attractivenss
			if (roulette < currentAttract) {
				Community c = communities[(int)(sortedCommunityParams[i][1])]; // Remember this element stores original index 
				LOGGER.fine("TargetChooser.chooseTarget() for burglar '"+this.toString()+"' found "+
						"most attractive community to travel to: "+c.toString()+" with overall attractiveness: "+sortedCommunityParams[i][0]);
				return c;
			}
		}
		
		// Error, shouldn't have got here
		
		String msg = ("TargetChooser.chooseTarget() error. Shouldn't have got here, for some reason no " +
				"community was chosen as the most attractive for burglar "+this.toString()+"\n" +
				"Here's some information about the communities which were examined:\n");
		for (int i=0; i<sortedCommunityParams.length; i++) {
			msg += (("Community: "+communities[(int)sortedCommunityParams[i][1]]+
					"\t overallAttract: "+sortedCommunityParams[i][0]+
					"\n\t dist: "+sortedCommunityParams[i][2]+", "+distMM[0]+"->"+distMM[1]+
					"\n\t attract: "+sortedCommunityParams[i][3]+ ", "+attractMM[0]+"->"+attractMM[1]+
					"\n\t socialDiff: "+sortedCommunityParams[i][4]+ ", "+socialDiffMM[0]+"->"+socialDiffMM[1]+
					"\n\t prevSucc: "+sortedCommunityParams[i][5]+ ", "+prevSuccMM[0]+"->"+prevSuccMM[1]+ 
					"\n"));
		}
		throw new Exception(msg);		
	}
	
	/**
	 * From the List of given houses see if any are suitable burglary victims.
	 * <P>The following variables are used in the calculation:</P>
	 * <ol>
	 * <li>ce - collective efficacy of the community.</li>
	 * <li>occ - occupancy levels of the community. NOT CURRENTLY USED</li>
	 * <li>acc - accessibility of the house</li>
	 * <li>vis - visibility of the house</li>
	 * <li>sec - security of the house. NOT CURRENTLY USED</li>
	 * <li>tv - traffic volume on the road next to the house (a house variable). NOT CURRENTLY USED</li>
	 * <li>ce_w - the weight, specific to the burglar applied to the collective efficacy variable</li>
	 * <li>tv_w</li>
	 * <li>occ_w</li>
	 * <li>acc_w</li>
	 * <li>vis_w</li>
	 * <li>sec_w</li>
	 * <li>suitability - the suitability value, derived from above variables</li>
	 * <li>motive_intensity - the intensity of the motive driving the burglar</li>
	 * <li>difference - the difference between suitability and motive intensity</li>
	 * <li>probability - the probability that a burglary will actually occur (derived from difference).</li>
	 * </ol>	
	 *   
	 * @param passedBuildings The Buildings that the agent is passing during this iteration. Assumes this list is
	 * not empty.
	 * 
	 * @return the house which is suitable or null if none are.
	 * @throws Exception 
	 */
	private Building chooseVictim(List<Building> passedBuildings) {
		
//		double motiveIntensity = this.burglar.getActionGuidingMotive().getIntensity();
//		double motiveIntensity = ContextManager.nextDoubleFromTo(0, 0.3);
		
		BurglaryWeights bw = ContextManager.getBurglaryWeights(); // Weights applied to each building parameter
		
		Building h; Community c;
		for (int i=0; i<passedBuildings.size(); i++) {
			h = passedBuildings.get(i); c = h.getCommunity();
			if (!h.equals(this.getHome())) {
				double ce = c.getCollectiveEfficacy();
//				double occ = s.getOccupancy(GlobalVars.time);
				double acc = h.getAccessibility(); // TODO XXXX should this be -accessibility, because high accessibility is good for burglar?
				double vis = h.getVisibility();
//				double sec = h.getSecurity();
//				double tv = h.getTrafficVolume(GlobalVars.time);
				double ce_w = bw.getWeight(BurglaryWeights.BURGLARY_WEIGHTS.CE_W);
//				double occ_w = bw.getWeight(BurglaryWeights.BURGLARY_WEIGHTS.OCC_W);
				double acc_w = bw.getWeight(BurglaryWeights.BURGLARY_WEIGHTS.ACC_W);
				double vis_w = bw.getWeight(BurglaryWeights.BURGLARY_WEIGHTS.VIS_W);
//				double sec_w = bw.getWeight(BurglaryWeights.BURGLARY_WEIGHTS.SEC_W);
//				double tv_w = bw.getWeight(BurglaryWeights.BURGLARY_WEIGHTS.TV_W);
				// See BurglarAgents.docx for definition of suitability. Low suitability value here means house is
				// very suitable for burglary. This should really be renamed 'risk' or something similar
				double suitability =
					( ce*ce_w + acc*acc_w + vis*vis_w ) /
					( ce_w + acc_w + vis_w  );
				
//					( ce*ce_w + tv*tv_w + occ*occ_w + acc*acc_w + vis*vis_w + sec*sec_w ) /
//					( ce_w + tv_w + occ_w + acc_w + vis_w + sec_w );

				// Do *not* burgle if suitability > motive intensity
				if (this.motiveIntensity > suitability ) { 
					// House is suitable, now see if a burglary is going to occur (include random component, greater
					// probability the greater the difference between suitability and motive intensity)
					double difference = this.motiveIntensity - suitability;
					double prob = Math.pow(difference, 3); // exponential probability
					if (prob > ContextManager.nextDouble()) {
						LOGGER.log(Level.FINE, 
								"VictimChooser: found suitable house: "+h.toString()+
								". Probability: "+prob+". Individual components of suitability:"+
								"\n\tce: "+ce+" w: "+bw.getWeight(BurglaryWeights.BURGLARY_WEIGHTS.CE_W)+
//								"\n\ttv: "+tv+" w: "+bw.getWeight(BurglaryWeights.BURGLARY_WEIGHTS.TV_W)+
//								"\n\tocc: "+occ+" w: "+bw.getWeight(BurglaryWeights.BURGLARY_WEIGHTS.OCC_W)+
								"\n\tacc: "+acc+" w: "+bw.getWeight(BurglaryWeights.BURGLARY_WEIGHTS.ACC_W)+
								"\n\tvis: "+vis+" w: "+bw.getWeight(BurglaryWeights.BURGLARY_WEIGHTS.VIS_W)+
//								"\n\tsec: "+sec+" w: "+ bw.getWeight(BurglaryWeights.BURGLARY_WEIGHTS.SEC_W)+
								"\n\tsuitability: "+suitability+
								"\n\tmotive intensity: "+this.motiveIntensity+
								"\n\tprobability difference: "+difference);
						return h;
					} // if prob > random
					else {
//						LOGGER.log(Level.FINE, ("House "+h.toString()+" not suitable (probability "+prob+")."));
					}
				} // if motive intensity > suitability
			} // if house != agent's home
			else {
				// Current house is the agent's home
//				System.out.println("chooseVictim() not choosing burglar's ("+this.toString()+
//						") house ("+h.toString()+") to burgle in.");
			}
		} // for
		// No suitable building found
		return null;		
		
	}
		
		
		/**
		 * Checks if the given value is smaller than the element at index 0 in the given array or larger than
		 * element 1 and, if so, replace the old values in the array with value. Can be called repeatedly to
		 * store min/max values. 
		 * @param value
		 * @param minMaxArray
		 */
		private void checkMinMax(double value, double[] minMaxArray) {
			if (value < minMaxArray[0])
				minMaxArray[0] = value;
			if (value > minMaxArray[1])
				minMaxArray[1] = value;
		}
		
		
		public List<Building> getBurgledBuildings() {
			return this.burgledBuildings;
		}

	/**
	 * There will be no inter-agent communication so these agents can be executed simulataneously in separate threads.
	 */
	@Override
	public final boolean isThreadable() {
		return true;
	}
	
	@Override
	public void setHomeCommunity(Community home) {
		this.community = home;
	}
	
	@Override
	public Community getHomeCommunity() {
		return this.community;
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
		return "Agent " + this.getIdentifier();
	}
	
	/**
	 * Get the agent's unique auto-generated ID number. This is created by this class when objects are created, not
	 * set by any data. It's useful for distinguishing between different agents but not really any good for
	 * identifying them externally, e.g. in results. The <code>getIdentifier()</code> function can be used to
	 * give agents IDs that relate to real data.
	 * @see getIdentifier()
	 */
	public int getID() {
		return this.id;
	}
	
	/**
	 * Return the agent's unique identifier. This can be set when reading in data from a point shapefile. If this is
	 * null (e.g. when agents are created randomly) then the agent's auto-generated unique ID is returned instead
	 * (see <code>getID()</code>). 
	 * @return
	 */
	public String getIdentifier() {
		if (this.identifier == null) {
			this.identifier = String.valueOf(this.id);
		}
		return this.identifier;
	}
	
	public void setIdentifier(String identifier) {
		this.identifier = identifier;
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
