package repastcity3.agent;

import java.util.HashMap;
import java.util.Map;

/** 
 * A class to store the values of the different weights that influence the aspects of a burglar's burglary decision.
 * 
 * @author nick
 * @see BURGLARY_WEIGHTS
 *
 */
public class BurglaryWeights {
	
	// Store the values of each weight
	Map<BURGLARY_WEIGHTS, Double> weightMap = new HashMap<BURGLARY_WEIGHTS, Double>(BURGLARY_WEIGHTS.values().length);
	
	/**
	 * Default constructor sets all weights to 0.5.
	 */
	public BurglaryWeights() {
		for (BURGLARY_WEIGHTS w:BURGLARY_WEIGHTS.values()) {
			this.setWeight(w, 0.5);
		}
	}
	
	/**
	 * Set the value of the given weight. Must be between 0 and 1.
	 * @param weight
	 * @param value
	 * @throws IllegalArgumentException if the weight is <0 or >1
	 */
	public void setWeight(BURGLARY_WEIGHTS weight, double value) throws IllegalArgumentException {
		if (value<0 || value>1) {
			throw new IllegalArgumentException("Value for the weight "+weight.toString()+" must be within the range "+
					"0 -- 1, not: "+value);
		}
		this.weightMap.put(weight, value);
	}
	
	public double getWeight(BURGLARY_WEIGHTS weight) {
		return this.weightMap.get(weight);
	}

	/**
	 * Definitions o the different weights that will affect a burglar's decision.
	 * @author nick
	 *
	 */
	enum BURGLARY_WEIGHTS {

		// Weights for choosing targets to travel to
		
		/** distance between current position and potential target */
		DIST_W, 
		/** attractiveness of potential target */
		ATTRACT_W, 
		/** difference between target sociotype and home sociotype */
		SOCIALDIFF_W, 
		/** number of previous successes */
		PREVSUCC_W,
		
		// Weights for choosing an individual house
		
		/** Collective efficacy of the community the house is in */
		CE_W,
		/** Traffic volume of the community */
		TV_W, 
		/** Occupancy levels in the community */
		OCC_W, 
		/** Accessibility of the house */
		ACC_W, 
		/** Visibility of the house */
		VIS_W, 
		/** Security of the house */
		SEC_W; 
		
	}

	
}

