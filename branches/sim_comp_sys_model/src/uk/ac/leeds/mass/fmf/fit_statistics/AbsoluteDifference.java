package uk.ac.leeds.mass.fmf.fit_statistics;

import java.util.List;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

/**
 * Calculat the absolute difference between two datasets.
 * @author Nick Malleson
 */
public class AbsoluteDifference implements IGOF {

	public boolean isPerfect(double testStat) {
		if (testStat == 0) 
			return true;
		else
			return false;
	}

	/**
	 * Compare two datasets by calculating the absolute difference between their
	 * cells.
	 * @return The mean absolute difference between the two datasets.
	 */
	public double test(double[][] calib, double[][] test) {
		double totalDifference = 0;
		int N = 0;
		for (int i=0; i< calib.length; i++) {
			for (int j=0; j<calib[i].length; j++) { 
				totalDifference += Math.abs(( test[i][j] - calib[i][j] ));
				N++;
			}
		}
		return totalDifference / N;
	}

	/**
	 * Compare two datasets by calculating the absolute difference between their
	 * cells.
	 *
	 * @param outLocalValues A matrix which will be populated with the absolute
	 * difference between each cell
	 * @return The mean absolute difference between each cell.
	 */
	public double test(double[][] calib, double[][] test, double[][] outLocalValues) {
		double totalDifference = 0;
		int N = 0;
		for (int i=0; i< outLocalValues.length; i++) {
			for (int j=0; j<outLocalValues[i].length; j++) { 
				outLocalValues[i][j] = Math.abs(( test[i][j] - calib[i][j] ));
				totalDifference += Math.abs(( test[i][j] - calib[i][j] ));
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
