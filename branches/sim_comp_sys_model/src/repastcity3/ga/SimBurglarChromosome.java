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

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import repastcity3.agent.BurglaryWeights;
import repastcity3.agent.BurglaryWeights.BURGLARY_WEIGHTS;
import repastcity3.main.ContextManager;
import repastcity3.main.GlobalVars;
import repastcity3.main.RepastCityLogging;
import repastcity3.main.RepastCityMain;
import repastcity3.main.RepastCityRunner;
import repastcity3.main.Results;

import uk.ac.leeds.mass.fmf.fit_statistics.GOF_TEST;
import uk.ac.leeds.mass.genetic_algorithm.Chromosome;
import uk.ac.leeds.mass.genetic_algorithm.GAException;
import uk.ac.leeds.mass.genetic_algorithm.Gene;

/**
 * 
 * @author Nick Malleson
 *
 */
public class SimBurglarChromosome extends Chromosome implements Serializable {

	private static final long serialVersionUID = -902287412457268978L;

	private static Logger LOGGER = Logger.getLogger(SimBurglarChromosome.class.getName());

	//	static {
	//		// See if logging needs to be initialised. This is a good place to do it because the Chromosome
	//		// class is passed to the GeneticMPJ constructor, so this will be done very early on.
	//		if (!RepastCityLogging.isInitialised()) {
	//			RepastCityLogging.initialise();
	//		}
	//	}

	/**
	 * The Genes here represent each of the weights that influence an agent's burglary decision.
	 * These are actually specified as an enum in the <code>BurglaryWeights</code> class.
	 * 
	 * @see BurglaryWeights.BURGLARY_WEIGHTS
	 * @see BurglaryWeights
	 * @return
	 */
	@Override
	public Gene[] createGenes() {

		List<Gene> genes = new ArrayList<Gene>();
		for (BURGLARY_WEIGHTS bw :BurglaryWeights.BURGLARY_WEIGHTS.values()) {
			genes.add(new Gene(bw.name(), 0, 1, 0.05));
		}
		return genes.toArray(new Gene[genes.size()]);


		//		Gene[] thegenes = new Gene[4];
		//		for (int i = 0; i < 4; i++) {
		//			thegenes[i] = new Gene("Gene" + i, 0.0, 10.0, 1.0);
		//		}
		//		return thegenes;
	}

	/**
	 * Run a model and calculate it's fitness.
	 *
	 * @throws GAException If the model failed to run.
	 * @param info The GAInformation object which provides information about the
	 * caller, ignored here.
	 * @see GAInformation
	 */
	@Override
	public void calcFitness() throws GAException {

		// Reset the name of this model. Because it's a static variable it will still have the name
		// of an old one. It will be given a new name by the Logger.
		GlobalVars.MODEL_NAME = null;
		
		// Initialise the logger
		RepastCityLogging.initialise();
//		// See if logging needs to be initialised. This is a good place to do it because the model is about to start
//		if (!RepastCityLogging.isInitialised()) {
//			RepastCityLogging.initialise();
//		}
		try {

			// GAMain will have saved the command line arguments that were passed to it (the Simphony scenario).
			File file = new File(GAMain.getScenarioDir()); // the scenario dir

			if (!file.exists()) {
				throw new Exception("Scenario directory not found: "+file.toString());
			}
			else {
				LOGGER.info("Loading scenario: "+file.getAbsolutePath());
			}

			RepastCityRunner runner = new RepastCityRunner();
			GlobalVars.RUNNER = runner;
			runner.load(file);     // load the repast scenario

			LOGGER.info("*********************** STARTING NEW SIM *********************");
			/* INITIALISE THE SIMULATION */
			runner.runInitialize();  	// ContextCreator.build() called here.
			LOGGER.info("*********************** INITIALISED *********************");

			// For some reason this next line doesn't cause sim to terminate properly, the "end" functions are called
			// but sim keeps running and breaks. So manually check if tickCount>endTime in while loop
			//			RunEnvironment.getInstance().endAt(endTime);			
			double endTime = GlobalVars.RUN_TIME;
			LOGGER.info("RepastCityMain: will end runs at "+GlobalVars.RUN_TIME+" iterations");
			
			// Set the burglary weights
			
			ContextManager.setBurglaryWeights(this.convertGenesToBurglaryWeights());

			/* RUN THE SIMULATION */				

			// Use a tick counter to determine when to stop sim rather than checking how many actions remain
			double ticks = 0;

			while (ticks <= endTime){  // loop until last action is left
				if (RepastCityMain.isStopSim()) { // Another class has set this, probably because an error has occurred
					ticks=endTime;
					System.out.println("RepastCityMain has been told to stop, terminating this run.");
					//					RepastCityMain.stopSim = false; // reset the boolean ready for the next run
				}
				if (ticks == endTime) {
					LOGGER.info("RepastCityMain, last tick, setting finishing");
					runner.setFinishing(true);
				}
				runner.step();  // execute all scheduled actions at next tick
				ticks++;
			} // while

			LOGGER.info("***********************STOPPING CURRENT SIM*********************");
			runner.stop();          // execute any actions scheduled at run end

			// for some reason this causes a NullPointer in repast somewhere
			//			runner.cleanUpRun();

			LOGGER.info("*********************** FINISHING RUN *********************");

			// If appropriate, summarise all agent's state variables and motive values
			runner.cleanUpBatch();    // run after all runs complete

		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "SimBurglarChromosome caught exception, cannot calculate fitness:", e);
			throw new GAException("SimBurglarChromosome caught exception, cannot calculate fitness", e);
		}

		// Model has finished, CALCULATE FITNESS
		
		GOF_TEST srmse = GOF_TEST.SRMSE;
		double error = Results.calculateError(srmse);
		LOGGER.info("The total "+srmse.getTestName()+" error is "+error);
		this.fitness = error;
	}


	/**
	 * Converts the genes in this chromosome to burglary weights that will drive the model.
	 * 
	 * @throws GAException If a gene could not be found for a given burglary weight 
	 */
	private BurglaryWeights convertGenesToBurglaryWeights() throws GAException {
		
		BurglaryWeights bw = new BurglaryWeights(); 
		
		// Iterate over all possible weights and find the corresponding Gene to set the weight value
		for (BURGLARY_WEIGHTS w:BURGLARY_WEIGHTS.values()) {
			boolean foundGene = false;
			for (Gene g:this.genes) {
				if (g.getName().equals(w.name())) {
					bw.setWeight(w, g.getValue());
					foundGene = true;
					break;
				}
			} // for genes
			if (!foundGene) {
				throw new GAException("Could not find a gene for the burglary weight "+w.name());
			}
		} // for weights
		return bw;
	}

}
