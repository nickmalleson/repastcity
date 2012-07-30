/*
 *
 * GOF_SRMSE.java
 *
 * Created on 27 November 2007 08:55
 *
 */

package uk.ac.leeds.mass.fmf.fit_statistics;

import java.io.Serializable;
import java.util.List;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

/**
 * Calculates a goodness of fit statistic between two matricies using the Standardized Root Mean Square Error
 * method.
 *
 *@author Kirk Harland
 */
public class SRMSE implements Serializable, IGOF {

	private static final long serialVersionUID = 1L;

    public boolean isPerfect(double testStat){
        if ( testStat == 0.0 ){return true;}else{return false;}
    }
    
	/**
	 * Run the test.
	 * <p>
	 * Assumes that all matricies have the same dimensions, will probably throw a NullPointerException if not.
	 * 
	 * @param calib The calibration (expected) data.
	 * @param test The test (predicted/simulated) data.
	 * @return The standardised root mean square error of the test and calibration data. 
	 */
	public double test(double[][] calib, double[][] test) {
		double m = (double)calib.length * (double)calib[0].length;
		double ss = 0;
		double T = 0;

		for (int i=0;i<calib.length;i++){
			for (int j=0;j<calib[i].length;j++){
				ss += (Math.pow(calib[i][j]-test[i][j],2)/m);
				T += (calib[i][j]/m); 
			}
		}
		double d = Math.pow(ss,0.5)/T;
		return d;
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