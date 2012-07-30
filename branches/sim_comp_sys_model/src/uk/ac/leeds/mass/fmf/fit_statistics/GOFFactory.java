/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package uk.ac.leeds.mass.fmf.fit_statistics;

import java.awt.GridLayout;
import java.util.ArrayList;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

/**
 *
 * @author geo8kh
 */
public class GOFFactory {
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

//	public enum GOF_TEST{ AED, ENTROPHY };
	
	
	
//    public static final int GOF_TEST_COUNT = 5;
//
//    public static final int GOF_SRMSE = 0;
//    public static final int GOF_AED = 1;
//    public static final int GOF_R2 = 2;
//    public static final int GOF_ENTROPY = 3;
//    public static final int GOF_CHI2 = 4;
//    
    private static GOF_TEST[] allTests = null;
//
//    JCheckBox[] cb = new JCheckBox[GOF_TEST_COUNT];
//    
    public static IGOF createTest(GOF_TEST testType){
        switch(testType){   
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
    
 
//    
//    /**
//     * Convenience method to get the name of a test. Returns different names depending on the value of
//     * <code>name</code>.
//     * @param test The test to get the name of.
//     * @param name The type of name to get. 1 calls the function <code>testName</code>, 2 calls 
//     * <code>fieldName</code>, 3 calls <code>localLestName</code>.
//     * @return The relevant name or null if either <code>test</code> doesn't refer to a test or if
//     * <code>name</code> isn't 1, 2 or 3.
//     */
//    public static String getTestName(int test, int name) {
//    	IGOF theTest = null;
//        switch(test){   
//        	case GOF_SRMSE: 
//        		theTest = new SRMSE();
//        		break;
//        	case GOF_AED:
//        		theTest = new AED();
//        		break;
//        	case GOF_R2:
//        		theTest = new R2();
//        		break;
//        	case GOF_ENTROPY:
//        		theTest = new Entropy();
//        		break;
//        	case GOF_CHI2:
//        		theTest = new CHI2();
//        		break;
//        	default:
//        		return null;
//        }// switch
//    	if (name == 1)
//    		return theTest.testName();
//    	else if (name == 2)
//    		return theTest.fieldName();
//    	else if (name == 3) {
//    		return theTest.localFieldName();
//    	}
//    	else {
//    		return null;
//    	}
//    }
//    
    public static GOF_TEST[] getAllTests() {
    	if(GOFFactory.allTests==null) {
    		allTests = new GOF_TEST[7];
    		allTests[0] = GOF_TEST.SRMSE;
    		allTests[1] = GOF_TEST.AED;
    		allTests[2] = GOF_TEST.R2;
    		allTests[3] = GOF_TEST.ENTROPY;
    		allTests[4] = GOF_TEST.CHI2;
    		allTests[5] = GOF_TEST.PERCENT_DIFF;
    		allTests[6] = GOF_TEST.ABSOLUTE_DIFF;
    	}
    	return GOFFactory.allTests;
    }
//
//    public JPanel getGOFPanel(){
//
//        JPanel jp = new JPanel();
//        GridLayout grid = new GridLayout();
//        grid.setRows(cb.length);
//        jp.setLayout( grid );
//        
//        for (int i = 0; i < cb.length; i++) {
//            cb[i] = new JCheckBox(createTest(i).testName());
//            jp.add(cb[i]);
//        }
//
//        return jp;
//
//    }
//
//
//
//    public IGOF[] getGOFTestsSelected(){
//        ArrayList<IGOF> selGof = new ArrayList<IGOF>();
//        for (int i = 0; i < cb.length; i++) {
//            if ( cb[i].isSelected() ){
//                selGof.add( createTest(i) );
//            }
//        }
//
//        IGOF[] g = new IGOF[selGof.size()];
//        selGof.toArray(g);
//
//        return g;
//    }


}
