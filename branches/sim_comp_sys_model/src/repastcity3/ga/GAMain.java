/*
©Copyright 2012 Nick Malleson
This file is part of RepastCity.

RepastCity is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

RepastCity is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with RepastCity.  If not, see <http://www.gnu.org/licenses/>.
 */
package repastcity3.ga;

import java.net.URL;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;


import repastcity3.main.RepastCityLogging;
import uk.ac.leeds.mass.genetic_algorithm.Chromosome;
import uk.ac.leeds.mass.genetic_algorithm.GeneticMPJ;

/**
 * Runs a GA by executing numerous simultaneous instances of the model using MPJ. The GA implementation is code 
 * that I wrote for another project. All that has to be done is to apply the GA to the burglary model is implement 
 * an instance of <code>Chromosome</code> and then pass the instance to the <code>GeneticMPJ</code> constructor. See
 * the main() function for this class, all it really does is call the <code>GeneticMPJ</code> constructor. It also 
 * keeps a reference to command-line arguments because these are needed by the chromosome to run the model.
 *
 * <P>There are scripts to get this running as MPJ needs to be initialised, you can't simply run this class. 
 * This class will be launched on each separate node in its own JVM, or as a seperate process on multi-core
 * machines.</P>
 *
 * @author Nick Malleson
 * @see GeneticMPJ
 * @see Chromosome
 *
 */
public class GAMain {

	//	   /**
	//	    * Initialises a new <code>GeneticMPJ</code> object and initialise MPI as
	//	    * well as GA requirements (genes, chromosomes etc).
	//	    *
	//	    * @param args Command line arguments are required for MPI.
	//	    * @param clazz The class of the <code>Chromosome</code> that make up the population.
	//	    * @param maxIter The number of iterations to run
	//	    */
	//	   public GAMain(String[] args, Class<? extends Chromosome> clazz, int maxIter) {
	//		   super(args, clazz, maxIter);
	//
	//	   }

	private static String scenarioDir;

	public static void main(String[] args) throws Exception {

		// The last command line argument should be the Simphony scenario directory
		GAMain.scenarioDir = args[args.length-1];

		//		if (args.length!=1) {
		//			throw new Exception("RepastCityMain expects the scenario to load as the first (and only) " +
		//					"command-line argument. Got: "+Arrays.toString(args));
		//		}

//		int  maxIter=5, numChromes=2, numKeep=1; // for laptop
		int  maxIter=10, numChromes=20, numKeep=5; // for cluster
		new GeneticMPJ(args, SimBurglarChromosome.class, maxIter, numChromes, numKeep);
	}

	public static String getScenarioDir() {
		return GAMain.scenarioDir;
	}

}
