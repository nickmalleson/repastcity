package repastcity3.main;

public abstract class Functions {
	

	/**
	 * Normalise a value.
	 * @param val The value to normalise
	 * @param min The minimum.
	 * @param max The maximum
	 * @return The value normalised to the range 0-1, calculated by  (val-min)/(max-min)
	 */
	public static double normalise (double val, double min, double max) {
		if (val == min && val == max) 
			return val;
		return (val-min)/(max-min);
	}
	
	/**
	 * Normalise a value to the range 0 -> 1.
	 * @param val The value to normalise
	 * @param minMax The minimum (index 0) and maximum (index 1) values.
	 * @return The value normalised to the range 0 -> 1. If val = min = max then return 0.5.
	 */
	public static double normalise(double val, double[] minMax) {
		if (val == minMax[0] && val == minMax[1])
			return 0.5;
//			return val;
		return (val-minMax[0])/(minMax[1]-minMax[0]);
	}
	
	/**
	 * Sort a 2D array on the first dimension [n][0]. Copy of Kirk's modified bubble sort implementation.
	 * @param array The array to sort.
	 * @param lowToHigh If true the array is sorted in ascending order, otherwise it is descending order.
	 * @return The array, sorted from low to high
	 */
	public static double[][] sort(double[][] array, boolean lowToHigh) {
		
		double d[][] = new double [array.length][array[0].length]; // The array to return
		double dTemp[][] = new double [2][array[0].length];
		for (int i=0;i<array.length;i++){
			for (int j=0;j<array[0].length;j++){
				d[i][j]=array[i][j];
			}
		}
		boolean notSorted=true; // 'Modified' bubble sort stops once array is sotted
		while(notSorted){	//starts the sorting	
			for(int j=0;j<d.length;j++){
				if (j==0) {
					notSorted = false;
				}
				else {
					if (lowToHigh) {
						if (d[j-1][0]>d[j][0]){	//if the second number in a pair is less than the first
							// Swap the two elements
							for (int i=0;i<d[j].length;i++){
								dTemp[0][i]=d[j-1][i];
								dTemp[1][i]=d[j][i];
							}						
							for (int i=0;i<d[j].length;i++){
								d[j-1][i]=dTemp[1][i];
								d[j][i]=dTemp[0][i];
							}						
							notSorted=true;	//this stops the while loop when all are sorted
						} //end of if comparison loop
					} // if lowToHigh
					else {
						if (d[j-1][0]<d[j][0]){	//if the second number in a pair is less than the first
							// Swap the two elements
							for (int i=0;i<d[j].length;i++){
								dTemp[0][i]=d[j-1][i];
								dTemp[1][i]=d[j][i];
							}						
							for (int i=0;i<d[j].length;i++){
								d[j-1][i]=dTemp[1][i];
								d[j][i]=dTemp[0][i];
							}						
							notSorted=true;	//this stops the while loop when all are sorted
						} //end of if comparison loop
					}// else if lowToHigh
					
				}	//End of if(j==0)
			}		//End of j loop (using d)
		}	//end of while loop		
		return d;
	}

}
