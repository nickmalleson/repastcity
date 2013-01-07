package repastcity3.main;

/** 
 * Implementing this interface implies that the class has static variables or caches that
 * need to be reset between model runs. This is only relevant if the same JVM is used to
 * run more than one model one after the other because Java will not reload classes.
 * @author Nick Malleson
 *
 */
public interface Resetable {
	
	/**
	 * Reset this class so it looks as if it has just been loaded.
	 */
	abstract void reset() ;

}
