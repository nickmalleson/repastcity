/*
 * IGOF.java
 *
 * Created on 12 December 2007, 11:37
 *
 */

package uk.ac.leeds.mass.fmf.fit_statistics;

import java.util.List;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

/**
 * Interface implemented by the matrix GOF statistics
 *
 * @author Kirk Harland
 */

public interface IGOF {

	/**
	 * Perform the test (optional).
	 * <p>
	 * Will accept two matricies, compare them, and output a global goodness-of-fit value.
	 * @param calib The calibration (expected) data.
	 * @param test The test (predicted/simulated) data.
	 * @return The value of the test
	 */
	public double test(double[][] calib, double[][] test);
	
	/**
	 * Perform the test (optional).
	 * <p>
	 * Will output the accuracy of the test on the individual cells. Some tests only calculate a
	 * global value, in which case this isn't applicable.
	 * @param calib The calibration (expected) data.
	 * @param test The test (predicted/simulated) data.
	 * @param localValues An optional 2D list which will be populated with the results of the test for each
	 * individual cell (if applicable to the test itself and not-null). 
	 * @return The global value of the test (if applicable)
	 */
	public double test(double[][] calib, double[][] test, double[][] outLocalValues);
	
	/**
	 * Perform the test (optional).
	 * <p>
	 * Will take two lists of <code>Point</code>s and calculate the spatial similarity between them.  
	 * @param calib The calibration (expected) data.
	 * @param test The test (predicted/simulated) data.
	 * @return The global value of the test.
	 */
	public double test(Point[] calib, Point[] test);
	
	/**
	 * Perform the test (optional).
	 * <p>
	 * Takes two lists of <code>Point</code>s and calculate the spatial similarity between them. Will
	 * also return some Geometry objects which illustrate, spatially, where the points are similar.   
	 * @param calib The calibration (expected) data.
	 * @param test The test (predicted/simulated) data.
	 * @param outGeometries A list of geometries that will be populated to illustrate where the points
	 * are similar.
	 * @return The value of the test.
	 */
	public double test(Point[] calib, Point[] test, List<Geometry> outGeometries);
	
//	/** The name of the test */
//	public String testName();
//	
//	/** A short name which can be used for fields in databases or GISs */
//	public String fieldName();
//	
//	/** 
//	 * A short name representing the name of the test if it can be applied to local values
//	 * @return the name or 'na' if the test does not calculate local statistics.
//	 */
//	public String localFieldName();
//
//	/**
//	 * Whether or not a lower fit is good or bad.
//	 * @return True if a lower test result implies a closer fit, false if a greater value implies a better fit.
//	 */
//	public boolean calibrateToLessThan();
	
	
    public boolean isPerfect(double testStat);

}
