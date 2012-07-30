/*
 *
 * CHI2.java
 *
 * Created on 13 Decemeber 2007 10:58
 *
 */

package uk.ac.leeds.mass.fmf.fit_statistics;

import java.io.Serializable;
import java.util.List;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

/**
 *Calculates a goodness of fit statistic between two matricies.
 * Uses the Chi sq method
 *
 *@author Kirk Harland
 */
public class CHI2 implements Serializable, IGOF{

	private static final long serialVersionUID = 1L;

    
	/**
	 * Run the test.
	 * <p>
	 * Assumes that all matricies have the same dimensions, will probably throw a NullPointerException if not.
	 * 
	 * @param calib The calibration (expected) data.
	 * @param test The test (predicted/simulated) data.
	 * @return The Chi-squared error of the test and calibration data. 
	 */
	public double test(double[][] calib, double[][] test){		

		
		//sum of ((Tij-Pij)^2/Pij)
		//where
		//Tij is the number of interactions between origin i and destination j
		//Pij is the predicted number of interactions between origin i and destination j


		double chi=0;

		// Calculate Chi2
		for (int i=0; i<calib.length; i++) {
			for (int j=0; j<calib[i].length; j++) {
				chi += (Math.pow(calib[i][j]-test[i][j],2)/test[i][j]);
			}
		}

		return chi;

	}

    public boolean isPerfect(double testStat){

        //this method needs to be completed
        //should return true if the testStat represents a perfect fit
        return false;
    }
    
	public String localFieldName() {
		return "na";
	}
	/**
	 * Not supported
	 */
	public double test(double[][] calib, double[][] test, double[][] localValues) {
		throw new UnsupportedOperationException("Method not supported by this test");
	}

	/**
	 * Not supported
	 */
	public double test(Point[] calib, Point[] test) {
		throw new UnsupportedOperationException("Method not supported by this test");
	}

	/**
	 * Not supported
	 */
	public double test(Point[] calib, Point[] test, List<Geometry> outGeometries) {
		throw new UnsupportedOperationException("Method not supported by this test");
	}


}
