/*
 *
 * Entropy.java
 *
 * Created on 12 Decemeber 2007 12:52
 *
 */

package uk.ac.leeds.mass.fmf.fit_statistics;

import java.io.Serializable;
import java.util.List;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

/**
 *Calculates the entropy in the model results matrix.
 *
 *@author Kirk Harland
 */
public class Entropy implements Serializable, IGOF{

	private static final long serialVersionUID = 1L;
    
	/**
	 * Run the test.
	 * <p>
	 * Assumes that all matricies have the same dimensions, will probably throw a NullPointerException if not.
	 * 
	 * @param calib The calibration (expected) data.
	 * @param test The test (predicted/simulated) data.
	 * @return The entrophy of the test and calibration data. 
	 */
	public double test(double[][] calib, double[][] test){

		double Nq=0;
		double Hq=0;

		for (int i=0;i<test.length;i++){
			for (int j=0;j<test[i].length;j++){
				//get the total number of trips in the matrix
				Nq+=test[i][j];
			}
		}

		//continue the calculation only if the total is > 0
		if (Nq!=0){
			for (int i=0;i<test.length;i++){
				for (int j=0;j<test[i].length;j++){
					//work out the entropy measure
					if(test[i][j]>0){Hq+=(test[i][j]/Nq)*Math.log(test[i][j]/Nq);}
				}
			}
		}

		return Hq*=-1;

	}

    public boolean isPerfect(double testStat){
        return false;
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
