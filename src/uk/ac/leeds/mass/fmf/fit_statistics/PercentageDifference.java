package uk.ac.leeds.mass.fmf.fit_statistics;

import java.util.List;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

public class PercentageDifference implements IGOF {

	public boolean isPerfect(double testStat) {
		if (testStat == 0) 
			return true;
		else
			return false;
	}

	/**
	 * Compare two datasets by calculating the percentage difference between their
	 * cells.
	 *
	 * @return The mean percentage difference between each cell.
	 */
	public double test(double[][] calib, double[][] test) {
		double totalDifference = 0;
		int N = 0;
		for (int i=0; i<calib.length; i++) {
			for (int j=0; j<calib[i].length; j++) { 
				totalDifference += Math.abs(( test[i][j] - calib[i][j] ));
				N++;
			}
		}
		return totalDifference / N;
	}

	/**
	 * Compare two datasets by calculating the percentage difference between their
	 * cells.
	 *
	 * @param outLocalValues A matrix which will be populated with the percentage
	 * difference between each individual cell
	 * @return The mean percentage difference between each cell.
	 */
	public double test(double[][] calib, double[][] test, double[][] outLocalValues) {
		
		double totalDifference = 0;
		int N = 0;
		for (int i=0; i< outLocalValues.length; i++) {
			for (int j=0; j<outLocalValues[i].length; j++) { 
				outLocalValues[i][j] = 100* ( (test[i][j] - calib[i][j]) / calib[i][j] );
				totalDifference += Math.abs((test[i][j] - calib[i][j]));
				N++;
			}
		}
		return totalDifference / N;
	}

	/**
	 * Not supported.
	 */
	public double test(Point[] calib, Point[] test) {
		throw new UnsupportedOperationException("Method not supported by this test");	
	}

	/**
	 * Not supported.
	 */
	public double test(Point[] calib, Point[] test, List<Geometry> outGeometries) {
		throw new UnsupportedOperationException("Method not supported by this test");
	}

}
