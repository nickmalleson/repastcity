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
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
//import java.util.logging.Logger;

import repastcity3.exceptions.NoIdentifierException;
import repastcity3.main.Functions;

import com.vividsolutions.jts.geom.Coordinate;

/**
 * Implementation of communities using the Output Area Classification (Vickers) for each area.
 * <p>
 * Classification consists of 41 individual variables and supergroup, group and subgroup
 * membership.    
 * <p>
 * To create Community objects a shapefile is read which will contain each variable value for every
 * output area. The variables are called v1-v41 so here there are get/set methods
 * for each variable. 
 * <p>
 * A map called 'variableValues' maps the variable numbers to their values. The map is populated as 
 * the shapefile containing OAC data is read by using the set() methods.
 * 
 * @author Nick Malleson
 */

public class Community implements FixedGeography, Identified {
	
//	private static Logger LOGGER = Logger.getLogger(Community.class.getName());
	
	/**
	 * A unique identifier for communities, usually set from the 'identifier' column in a shapefile
	 */
	private String identifier;
	
	/** A list of all buildings in this community */
	private List<Building> buildings = new ArrayList<Building>();

	/**
	 * The coordinates of the Community. This is also stored by the projection that contains this Community but it is
	 * useful to have it here too. As they will never change (buildings don't move) we don't need to worry about keeping
	 * them in sync with the projection.
	 */
	private Coordinate coords;
	
	// Map of variable values (e.g. v31 maps to key 31). This is initialised in the get/set methods
	private volatile Map<Integer, Double> variableValues;
	

	public Community() { 
		this.variableValues = new Hashtable<Integer, Double>();
	}
	
	private double collectiveEfficacy = 0.5;
	
	private double attractiveness = -1; // This is calculated the first time that getAttractiveness() is called
	
	
	
	/**
	 * Attractiveness caluclated from following: (see thesis/model_dev/classes.tex, section 'Socioeconomic Types')
	 * <br>full time students, rooms per household, > 2 car household, HE qualifications
	 */
	public double getAttractiveness() {

		if (this.attractiveness==-1) { // Value isn't cached, calculate it
			double v31 = getVariableValue(31);
			double v22 = getVariableValue(22);
			double v26 = getVariableValue(26);
			double v24 = getVariableValue(24);
			// Attractiveness calculated from this OAs individual variable values, these must
			// be normalised first.
			this.attractiveness = ( Functions.normalise(v31, minv31, maxv31) + Functions.normalise(v22, minv22, maxv22) + 
					Functions.normalise(v26, minv26, maxv26) + Functions.normalise(v24, minv24, maxv24)) / 4;
			//				System.out.println(v31+", "+minv31+", "+maxv31+", "+normalise(v31, minv31, maxv31));
			//				System.out.println(v22+", "+minv22+", "+maxv22+", "+normalise(v22, minv22, maxv22));
			//				System.out.println(v26+", "+minv26+", "+maxv26+", "+normalise(v26, minv26, maxv26));
			//				System.out.println(v24+", "+minv24+", "+maxv24+", "+normalise(v24, minv24, maxv24));

		}
		return this.attractiveness;
	}
	
	public double getCollectiveEfficacy() {
		return this.collectiveEfficacy;
	}
//	public void setCollectiveEfficacy(double collectiveEfficacy) {
//		this.collectiveEfficacy = collectiveEfficacy;
//	}

	/* These are required so that collectiveEfficacy can be abbreviated to 'ce' in the shapefile) */

	public double getCE() {
		return this.collectiveEfficacy;
	}
	
	public void setCE(double ce) {
		this.collectiveEfficacy = ce;
	}
	
	
	public void addBuilding(Building b) {
		this.buildings.add(b);
	}

	public List<Building> getBuildings() {
		throw new UnsupportedOperationException("Haven't read buildings into communities yet");
//		return this.buildings;
		
	}

	
	/**
	 * Compare communities by calculating the Euclidean distance between all their variables. The return value
	 * is normalised and "reversed" so that dissimilar areas return 0, identical ones return 1.
	 * Also, each variable is normalised so that those with larger magnitude don't dominate the calculation.
	 * Checks to see whether to use average subgroup values or individual values for each community
	 */
	public double compare(Community c) {
		double sum = 0; // The sum of squares
		for (int i=1; i<42; i++) { // Iterate over the 41 variables
				sum += (Math.pow( this.getVariableValue(i)-c.getVariableValue(i), 2 ));
				
		}
		double val = Math.sqrt(sum);
		// Now normalise and 'reverse' so that range is 0 (dissimilar) to 1 (identical)
		double maxSize = Math.sqrt(41); // i.e. difference between every variable is 1 (maximum)
		return 1 - Functions.normalise(val, 0, maxSize);
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



	@Override
	public boolean equals(Object obj) {
//		if (!(obj instanceof Building))
//			return false;
//		Building b = (Building) obj;
//		return this.identifier.equals(b.identifier);
		throw new UnsupportedOperationException("Not implemented yet");
	}

	/**
	 * Returns the hash code of this <code>Building</code>'s identifier string. 
	 */
	@Override
	public int hashCode() {
		throw new UnsupportedOperationException("Not implemented yet");
//		if (this.identifier==null) {
//			LOGGER.severe("hashCode called but this object's identifier has not been set. It is likely that you're " +
//					"reading a shapefile that doesn't have a string column called 'identifier'");
//		}
//
//		return this.identifier.hashCode();
	}

	/**
	 * Convenience function to get values from the store
	 * @param i The variable number
	 * @return The value of the variable
	 */
	private double getVariableValue(int i) {
		return this.variableValues.get(i);
	}
	
	private void setVariableValue(int i, double v) {

		this.variableValues.put(i, v);
	}
	
	
	/*
	 * Get/Set methods for the 41 individual variable which make up the classification. These are
	 * needed when the individual OAC values are read in from a shapefile along with the Communities
	 * they represent. Also need min/max values to normalise. 
	 * (These were auto-generated from csv files in extras/ directory (load them into a spreadsheet,
	 * copy and paste into vim, do some formatting then copy and past into here)) */
	private static double maxv1 = Double.MIN_VALUE ; private static double minv1 = Double.MAX_VALUE ;
	private static double maxv2 = Double.MIN_VALUE ; private static double minv2 = Double.MAX_VALUE ;
	private static double maxv3 = Double.MIN_VALUE ; private static double minv3 = Double.MAX_VALUE ;
	private static double maxv4 = Double.MIN_VALUE ; private static double minv4 = Double.MAX_VALUE ;
	private static double maxv5 = Double.MIN_VALUE ; private static double minv5 = Double.MAX_VALUE ;
	private static double maxv6 = Double.MIN_VALUE ; private static double minv6 = Double.MAX_VALUE ;
	private static double maxv7 = Double.MIN_VALUE ; private static double minv7 = Double.MAX_VALUE ;
	private static double maxv8 = Double.MIN_VALUE ; private static double minv8 = Double.MAX_VALUE ;
	private static double maxv9 = Double.MIN_VALUE ; private static double minv9 = Double.MAX_VALUE ;
	private static double maxv10 = Double.MIN_VALUE ; private static double minv10 = Double.MAX_VALUE ;
	private static double maxv11 = Double.MIN_VALUE ; private static double minv11 = Double.MAX_VALUE ;
	private static double maxv12 = Double.MIN_VALUE ; private static double minv12 = Double.MAX_VALUE ;
	private static double maxv13 = Double.MIN_VALUE ; private static double minv13 = Double.MAX_VALUE ;
	private static double maxv14 = Double.MIN_VALUE ; private static double minv14 = Double.MAX_VALUE ;
	private static double maxv15 = Double.MIN_VALUE ; private static double minv15 = Double.MAX_VALUE ;
	private static double maxv16 = Double.MIN_VALUE ; private static double minv16 = Double.MAX_VALUE ;
	private static double maxv17 = Double.MIN_VALUE ; private static double minv17 = Double.MAX_VALUE ;
	private static double maxv18 = Double.MIN_VALUE ; private static double minv18 = Double.MAX_VALUE ;
	private static double maxv19 = Double.MIN_VALUE ; private static double minv19 = Double.MAX_VALUE ;
	private static double maxv20 = Double.MIN_VALUE ; private static double minv20 = Double.MAX_VALUE ;
	private static double maxv21 = Double.MIN_VALUE ; private static double minv21 = Double.MAX_VALUE ;
	private static double maxv22 = Double.MIN_VALUE ; private static double minv22 = Double.MAX_VALUE ;
	private static double maxv23 = Double.MIN_VALUE ; private static double minv23 = Double.MAX_VALUE ;
	private static double maxv24 = Double.MIN_VALUE ; private static double minv24 = Double.MAX_VALUE ;
	private static double maxv25 = Double.MIN_VALUE ; private static double minv25 = Double.MAX_VALUE ;
	private static double maxv26 = Double.MIN_VALUE ; private static double minv26 = Double.MAX_VALUE ;
	private static double maxv27 = Double.MIN_VALUE ; private static double minv27 = Double.MAX_VALUE ;
	private static double maxv28 = Double.MIN_VALUE ; private static double minv28 = Double.MAX_VALUE ;
	private static double maxv29 = Double.MIN_VALUE ; private static double minv29 = Double.MAX_VALUE ;
	private static double maxv30 = Double.MIN_VALUE ; private static double minv30 = Double.MAX_VALUE ;
	private static double maxv31 = Double.MIN_VALUE ; private static double minv31 = Double.MAX_VALUE ;
	private static double maxv32 = Double.MIN_VALUE ; private static double minv32 = Double.MAX_VALUE ;
	private static double maxv33 = Double.MIN_VALUE ; private static double minv33 = Double.MAX_VALUE ;
	private static double maxv34 = Double.MIN_VALUE ; private static double minv34 = Double.MAX_VALUE ;
	private static double maxv35 = Double.MIN_VALUE ; private static double minv35 = Double.MAX_VALUE ;
	private static double maxv36 = Double.MIN_VALUE ; private static double minv36 = Double.MAX_VALUE ;
	private static double maxv37 = Double.MIN_VALUE ; private static double minv37 = Double.MAX_VALUE ;
	private static double maxv38 = Double.MIN_VALUE ; private static double minv38 = Double.MAX_VALUE ;
	private static double maxv39 = Double.MIN_VALUE ; private static double minv39 = Double.MAX_VALUE ;
	private static double maxv40 = Double.MIN_VALUE ; private static double minv40 = Double.MAX_VALUE ;
	private static double maxv41 = Double.MIN_VALUE ; private static double minv41 = Double.MAX_VALUE ;
	

	public void setV1 (double v1 ) { if ( v1 >maxv1 ) maxv1 = v1 ; if ( v1 <minv1 ) minv1 = v1 ; this.setVariableValue( 1 , v1 );}
	public void setV2 (double v2 ) { if ( v2 >maxv2 ) maxv2 = v2 ; if ( v2 <minv2 ) minv2 = v2 ; this.setVariableValue( 2 , v2 );}
	public void setV3 (double v3 ) { if ( v3 >maxv3 ) maxv3 = v3 ; if ( v3 <minv3 ) minv3 = v3 ; this.setVariableValue( 3 , v3 );}
	public void setV4 (double v4 ) { if ( v4 >maxv4 ) maxv4 = v4 ; if ( v4 <minv4 ) minv4 = v4 ; this.setVariableValue( 4 , v4 );}
	public void setV5 (double v5 ) { if ( v5 >maxv5 ) maxv5 = v5 ; if ( v5 <minv5 ) minv5 = v5 ; this.setVariableValue( 5 , v5 );}
	public void setV6 (double v6 ) { if ( v6 >maxv6 ) maxv6 = v6 ; if ( v6 <minv6 ) minv6 = v6 ; this.setVariableValue( 6 , v6 );}
	public void setV7 (double v7 ) { if ( v7 >maxv7 ) maxv7 = v7 ; if ( v7 <minv7 ) minv7 = v7 ; this.setVariableValue( 7 , v7 );}
	public void setV8 (double v8 ) { if ( v8 >maxv8 ) maxv8 = v8 ; if ( v8 <minv8 ) minv8 = v8 ; this.setVariableValue( 8 , v8 );}
	public void setV9 (double v9 ) { if ( v9 >maxv9 ) maxv9 = v9 ; if ( v9 <minv9 ) minv9 = v9 ; this.setVariableValue( 9 , v9 );}
	public void setV10 (double v10 ) { if ( v10 >maxv10 ) maxv10 = v10 ; if ( v10 <minv10 ) minv10 = v10 ; this.setVariableValue( 10 , v10 );}
	public void setV11 (double v11 ) { if ( v11 >maxv11 ) maxv11 = v11 ; if ( v11 <minv11 ) minv11 = v11 ; this.setVariableValue( 11 , v11 );}
	public void setV12 (double v12 ) { if ( v12 >maxv12 ) maxv12 = v12 ; if ( v12 <minv12 ) minv12 = v12 ; this.setVariableValue( 12 , v12 );}
	public void setV13 (double v13 ) { if ( v13 >maxv13 ) maxv13 = v13 ; if ( v13 <minv13 ) minv13 = v13 ; this.setVariableValue( 13 , v13 );}
	public void setV14 (double v14 ) { if ( v14 >maxv14 ) maxv14 = v14 ; if ( v14 <minv14 ) minv14 = v14 ; this.setVariableValue( 14 , v14 );}
	public void setV15 (double v15 ) { if ( v15 >maxv15 ) maxv15 = v15 ; if ( v15 <minv15 ) minv15 = v15 ; this.setVariableValue( 15 , v15 );}
	public void setV16 (double v16 ) { if ( v16 >maxv16 ) maxv16 = v16 ; if ( v16 <minv16 ) minv16 = v16 ; this.setVariableValue( 16 , v16 );}
	public void setV17 (double v17 ) { if ( v17 >maxv17 ) maxv17 = v17 ; if ( v17 <minv17 ) minv17 = v17 ; this.setVariableValue( 17 , v17 );}
	public void setV18 (double v18 ) { if ( v18 >maxv18 ) maxv18 = v18 ; if ( v18 <minv18 ) minv18 = v18 ; this.setVariableValue( 18 , v18 );}
	public void setV19 (double v19 ) { if ( v19 >maxv19 ) maxv19 = v19 ; if ( v19 <minv19 ) minv19 = v19 ; this.setVariableValue( 19 , v19 );}
	public void setV20 (double v20 ) { if ( v20 >maxv20 ) maxv20 = v20 ; if ( v20 <minv20 ) minv20 = v20 ; this.setVariableValue( 20 , v20 );}
	public void setV21 (double v21 ) { if ( v21 >maxv21 ) maxv21 = v21 ; if ( v21 <minv21 ) minv21 = v21 ; this.setVariableValue( 21 , v21 );}
	public void setV22 (double v22 ) { if ( v22 >maxv22 ) maxv22 = v22 ; if ( v22 <minv22 ) minv22 = v22 ; this.setVariableValue( 22 , v22 );}
	public void setV23 (double v23 ) { if ( v23 >maxv23 ) maxv23 = v23 ; if ( v23 <minv23 ) minv23 = v23 ; this.setVariableValue( 23 , v23 );}
	public void setV24 (double v24 ) { if ( v24 >maxv24 ) maxv24 = v24 ; if ( v24 <minv24 ) minv24 = v24 ; this.setVariableValue( 24 , v24 );}
	public void setV25 (double v25 ) { if ( v25 >maxv25 ) maxv25 = v25 ; if ( v25 <minv25 ) minv25 = v25 ; this.setVariableValue( 25 , v25 );}
	public void setV26 (double v26 ) { if ( v26 >maxv26 ) maxv26 = v26 ; if ( v26 <minv26 ) minv26 = v26 ; this.setVariableValue( 26 , v26 );}
	public void setV27 (double v27 ) { if ( v27 >maxv27 ) maxv27 = v27 ; if ( v27 <minv27 ) minv27 = v27 ; this.setVariableValue( 27 , v27 );}
	public void setV28 (double v28 ) { if ( v28 >maxv28 ) maxv28 = v28 ; if ( v28 <minv28 ) minv28 = v28 ; this.setVariableValue( 28 , v28 );}
	public void setV29 (double v29 ) { if ( v29 >maxv29 ) maxv29 = v29 ; if ( v29 <minv29 ) minv29 = v29 ; this.setVariableValue( 29 , v29 );}
	public void setV30 (double v30 ) { if ( v30 >maxv30 ) maxv30 = v30 ; if ( v30 <minv30 ) minv30 = v30 ; this.setVariableValue( 30 , v30 );}
	public void setV31 (double v31 ) { if ( v31 >maxv31 ) maxv31 = v31 ; if ( v31 <minv31 ) minv31 = v31 ; this.setVariableValue( 31 , v31 );}
	public void setV32 (double v32 ) { if ( v32 >maxv32 ) maxv32 = v32 ; if ( v32 <minv32 ) minv32 = v32 ; this.setVariableValue( 32 , v32 );}
	public void setV33 (double v33 ) { if ( v33 >maxv33 ) maxv33 = v33 ; if ( v33 <minv33 ) minv33 = v33 ; this.setVariableValue( 33 , v33 );}
	public void setV34 (double v34 ) { if ( v34 >maxv34 ) maxv34 = v34 ; if ( v34 <minv34 ) minv34 = v34 ; this.setVariableValue( 34 , v34 );}
	public void setV35 (double v35 ) { if ( v35 >maxv35 ) maxv35 = v35 ; if ( v35 <minv35 ) minv35 = v35 ; this.setVariableValue( 35 , v35 );}
	public void setV36 (double v36 ) { if ( v36 >maxv36 ) maxv36 = v36 ; if ( v36 <minv36 ) minv36 = v36 ; this.setVariableValue( 36 , v36 );}
	public void setV37 (double v37 ) { if ( v37 >maxv37 ) maxv37 = v37 ; if ( v37 <minv37 ) minv37 = v37 ; this.setVariableValue( 37 , v37 );}
	public void setV38 (double v38 ) { if ( v38 >maxv38 ) maxv38 = v38 ; if ( v38 <minv38 ) minv38 = v38 ; this.setVariableValue( 38 , v38 );}
	public void setV39 (double v39 ) { if ( v39 >maxv39 ) maxv39 = v39 ; if ( v39 <minv39 ) minv39 = v39 ; this.setVariableValue( 39 , v39 );}
	public void setV40 (double v40 ) { if ( v40 >maxv40 ) maxv40 = v40 ; if ( v40 <minv40 ) minv40 = v40 ; this.setVariableValue( 40 , v40 );}
	public void setV41 (double v41 ) { if ( v41 >maxv41 ) maxv41 = v41 ; if ( v41 <minv41 ) minv41 = v41 ; this.setVariableValue( 41 , v41 );}
	
	
}
