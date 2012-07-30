/*
 *
 * AED.java
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
 * Uses the Absolute Entropy Difference method to calculate the fit between two matricies.
 *
 *@author Kirk Harland
 */
public class AED implements Serializable, IGOF{

	private static final long serialVersionUID = 1L;

	/**
	 * Run the test.
	 * <p>
	 * Assumes that all matricies have the same dimensions, will probably throw a NullPointerException if not.
	 * 
	 * @param calib The calibration (expected) data.
	 * @param test The test (predicted/simulated) data.
	 * @return The absolute entropy difference between the test and calibration data. 
	 */
	public double test(double[][] calib, double[][]test){

		//Pij=Tij/N
		//where
		//Tij is the number of interactions between origin i and destination j
		//and N is the total number of trips in the matirx

		//AED = |Hp-Hq|
		//where
		//(basically observed matrix)Hp= negative sum of Pij in the matrix * ln(Pij)
		//(basically predicted matrix)Hq= negative sum of Qij in the matrix * ln(Qij)

//		double Pij = 0;
//		double Qij = 0;
//		double sumPij=0;
//		double sumQij=0;
		double Nq=0;
		double Np=0;
		double Hp=0;
		double Hq=0;

		for (int i=0;i<calib.length;i++){
			for (int j=0;j<calib[i].length;j++){
				//get the total number of trips in each matrix
				Nq+=test[i][j];
				Np+=calib[i][j];
			}
		}

		//continue the calculation only if the totals are not 0
		if (Nq!=0 & Np!=0){


			for (int i=0;i<calib.length;i++){
				for (int j=0;j<calib[i].length;j++){
					//work out the entropy measures for the two matricies
					if(calib[i][j]>0){Hp+=(calib[i][j]/Np)*Math.log(calib[i][j]);}
					if(test[i][j]>0){Hq+=(test[i][j]/Nq)*Math.log(test[i][j]);}
				}
			}
		}
		Hp*=-1;
		Hq*=-1;

		return Math.abs(Hp-Hq);

	}
	
	public String localFieldName() {
		return "na";
	}


    public boolean isPerfect(double testStat){
        if ( testStat == 0.0 ){return true;}else{return false;}
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
