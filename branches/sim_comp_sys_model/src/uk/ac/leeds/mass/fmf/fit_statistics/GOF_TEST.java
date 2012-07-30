package uk.ac.leeds.mass.fmf.fit_statistics;

public enum GOF_TEST {

	AED("Absolute Entropy Difference", "AED", true),
	ENTROPY("Entrophy", "Entrophy", true),
	CHI2("Chi-Squared", "CHI2", true),
	R2("R-squared", "R2", false),
	SRMSE("Standardised Root-Mean-Square Error", "SRMSE", true),
	ABSOLUTE_DIFF("Absolute Differece", "AbsDiff", true),
	PERCENT_DIFF("Percentage Differece", "PctDiff", true);

	// Test variables
	private String testName;
	private String fieldName;
	private String localFieldName;
	private boolean calibrateToLessThan;

	{this.fieldName = "";}

	// Constructors
	private GOF_TEST (String testName, String fieldName, boolean calibrateToLessThan) {
		this.testName = testName;
		this.fieldName = fieldName;
		this.calibrateToLessThan = calibrateToLessThan; 
	}
	private GOF_TEST (String testName, String fieldName, boolean calibrateToLessThan, String localFieldName) {
		this(testName, fieldName, calibrateToLessThan);
		this.localFieldName =  localFieldName;
	}

	public IGOF getTest() {
		switch(this){   
		case SRMSE: 
			return new SRMSE();
		case AED:
			return new AED();
		case R2:
			return new R2();
		case ENTROPY:
			return new Entropy();
		case CHI2:
			return new CHI2();
		case PERCENT_DIFF:
			return new PercentageDifference();
		case ABSOLUTE_DIFF:
			return new AbsoluteDifference();
		default:
			return null;
		}
	}
	
	/** Normalises values in the input matrix so that they sum to 1 (i.e. represent proportions rather than
	 * absolute numbers. This is useful for methods which require matricies to have the same sum.*/
	public static void normalise(double[][] in) {
		// Calculate total
		double total = 0;
		for (int i=0; i<in.length; i++){
			for (int j=0; j<in[i].length; j++) {
				total += in[i][j];
			}
		}
		// Now calculate proportions
		for (int i=0; i<in.length; i++){
			for (int j=0; j<in[i].length; j++) {
				in[i][j] = in[i][j] / total;
			}
		}
	}
	

	// Get methods
	/**
	 * Return a "long-winded" version of the test name
	 * @return The test name.
	 */
	 public String getTestName() {
		return testName;
	}

	/**
	 * Return a short version of the test name, suitable for table headings.
	 * @return The field name.
	 */
	 public String getFieldName() {
		 return fieldName;
	 }

	 /**
	  * Return the name of the statistic that assess local variations in the test (if applicable).
	  * @return the local field name.
	  */
	 public String getLocalFieldName() {
		 return localFieldName;
	 }

	 /**
	  * Determine whether or not, if comparing two test results, whether or not a lower value implies
	  * a closer fit.
	  * @return True if a lower value implies a better fit, false otherwise.
	  */
	 public boolean isCalibrateToLessThan() {
		 return calibrateToLessThan;
	 }

}
