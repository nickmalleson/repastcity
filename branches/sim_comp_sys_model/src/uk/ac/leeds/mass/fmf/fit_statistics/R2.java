/*
 *
 * R2.java
 *
 * Created on 13 Decemeber 2007 10:32
 *
 */

package uk.ac.leeds.mass.fmf.fit_statistics;

import java.io.Serializable;
import java.util.List;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

/**
 * Calculates a goodness of fit statistic between two matricies. Uses the R-squared method.
 *
 * @author Kirk Harland
 */
public class R2 implements Serializable, IGOF{

	private static final long serialVersionUID = 1L;


	/**
	 * Run the test.
	 * <p>
	 * Assumes that all matricies have the same dimensions, will probably throw a NullPointerException if not.
	 * 
	 * @param calib The calibration (expected) data.
	 * @param test The test (predicted/simulated) data.
	 * @return The R-squared error of the test and calibration data. 
	 */
	public double test(double[][] calib, double[][] test){
		
		double eq1 = 0;
		double eq2 = 0;
		double eq3 = 0;
		double obsMean;
		double preMean;
		double obsTot=0;
		double preTot=0;

		// Calculate averages
		for (int i=0; i<calib.length; i++) {
			for (int j=0; j<calib[i].length; j++) {
				obsTot += calib[i][j];
				preTot += test[i][j];
			}
		}

		obsMean = obsTot / (calib.length*calib[0].length);
		preMean = preTot / (calib.length*calib[0].length);

		// Calculate R2
		for (int i=0; i<calib.length; i++) {
			for (int j=0; j<calib[i].length; j++) {
				eq1 += (calib[i][j]-obsMean) * (test[i][j]-preMean);
				eq2 += Math.pow((calib[i][j]-obsMean),2);
				eq3 += Math.pow((test[i][j]-preMean),2);
			}
		}

		double R2 = Math.pow((eq1 / Math.pow(eq2*eq3,0.5)),2);
		
		// Check for divide by 0 error, think this means that value should be 1
		if (eq2 == 0.0 || eq3 == 0) {
			return 1.0;
		}
		else {
			return R2;	
		}
		

		

	}
	
    public boolean isPerfect(double testStat){
        if ( testStat == 1.0 ){return true;}else{return false;}
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

