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
package uk.ac.leeds.mass.genetic_algorithm;

import java.io.Serializable;
import java.util.Random;
import java.util.ArrayList;

/**
 * The Chromosome class encapsulates the idea of a chromosome used in a genetic
 * algorithm. Each chromosome is made from a number of genes. Subclalsses need
 * to extend calcFitness() and createGenes().
 *
 * @author Nick Malleson
 * @see Genetic
 * @see Gene
 */
public abstract class Chromosome implements Serializable{

	private static final long serialVersionUID = -1490000231265598061L;

	protected Gene[] genes = null;		// The Genes that make up this Chromosome.
	protected double fitness = -1.0;	// The fitness of this Chromosome
	private int ranking = -1;		// The position of this Chromosome in a list sorted by fitness

	// These variables are useful when trying to find parents for subsequent generations.
	protected boolean selected = false; // Whether or not the chromeosome has been selected to be a parent
	protected double prob = -1.0; // The probability of becomming a parent

	// Need unique identifiers for equal() and hashCode();
	private static int uniqueID = 0;

	// Chromosome mutations are chosen in the range [-d : 1+d], so the following sets how much genes are likely
	// to mutate by (max). See further discussion in the recombine() method.
	private static double mutate = 0.05; // Set the size of mutations.
//	private static double mutate = 0.25; // Set the size of mutations. // VALUE USED BY ALISON
		
	private int id = Chromosome.uniqueID++;

	/**
	 * Create the Genes that construct this Chromosome. Obviously this will vary depending
	 * on the application so subclasses need to override this method.
	 */
	public abstract Gene[] createGenes();

	/**
	 * Calculate the fitness of this Chromosome by running a model and comparing it
	 * to expected data.
	 *
	 * <P>This method should set the 'fitness' variable after running calculating
	 * the fitness.</P>
	 *
	 * @param info An optional GAInformation object which provides the chromosome
	 * with information about the class that is actually running it (calling the
	 * <code>calcFitness()</code> method. This can be ignored and is currently used
	 * in the Grid GA configuration.
	 * @throws GAException If there was an error, true otherwise.
	 */
	public abstract void calcFitness() throws GAException;


	/**
	 * Constructor creates a Chromosome with some default genes. Each Gene is initialised
	 * to a random value somewhere between it's min-max values.
	 */
	public Chromosome() {
		// Get the genes that make up a Chromosome
		this.genes = this.createGenes();

		// Give the Genes random values
		Random rand = new Random();
		for (Gene g : this.genes) {
			g.setValue(
					g.getMinValue() + rand.nextFloat() * (g.getMaxValue() - g.getMinValue()));
		}
	}

	/**
	 * Construct a new Chromosome from an array containing each Gene.
	 *
	 * @param genes An array of Gene objects which make up this Chromosome.
	 */
	public Chromosome(Gene[] genes) {
		this.genes = genes;
	}

	/**
	 * Sorts a list of Chromosomes by best (smallest) fitness value first using
	 * bubble sort algorithm.
	 *
	 *
	 * @param chromes The list of Chromosomes to be sorted
	 * @return A sorted array of Chromosomes.
	 */
	public static Chromosome[] sort(Chromosome[] chromes) {
		Chromosome tmp;
		for (int i = 0; i < chromes.length - 1; i++) {
			for (int j = 0; j < chromes.length - 1 - i; j++) {
				if (chromes[j + 1].getFitness() < chromes[j].getFitness()) {  /* compare the two neighbors */
					tmp = chromes[j];         /* swap a[j] and a[j+1]      */
					chromes[j] = chromes[j + 1];
					chromes[j + 1] = tmp;
				} // if
			} // for
		} // for

		// Set the position of each Chromosome in the list.
		for (int i = 0; i < chromes.length; i++) {
			chromes[i].setRanking(i);
		}

		return chromes;
	}

	/**
	 * Mutates this Chromosome.
	 *
	 */
	public void mutate() {
		double perturb;
		Random rand = new Random();
		for (int i = 0; i < genes.length; i++) {
			perturb = 2.0 * (rand.nextFloat() - 0.5) * genes[i].getMutate();
			// Set the new value for the gene, check that gene.setValue() doesn't return false
			if (genes[i].setValue(Math.min(
					genes[i].getMaxValue(), 
					Math.max(genes[i].getMinValue(), genes[i].getValue() + perturb)
			)) == false) {
				System.err.println("Error mutating chromosome:");
				this.print();
			} // if
		} // for
		// This Chromosome has just been mutated so we don't know it's fitness.
		this.fitness = -1;
	}

	/**
	 * Selects a number of chromosomes to form the parents of a new generation.
	 *
	 * <p>Linear ranking is used as the selection method so that each Chromosome has the opportunity
	 * to live on to the next generation, the fittest chromosomes have the greatest chance. This
	 * ensures the algorithm maintains diversity.
	 *
	 * <p>The fittest Chromosome is always selected (Reeves and Rowe, pg45).
	 *
	 * @param chromes the population of Chromosomes from which the parents are selected. It must be
	 * ordered by fittest chromosome first.
	 * @param numKeep the number Chromosomes to keep from the population.
	 * @return a Chromosome array containing the parents.
	 */
	public static Chromosome[] select(Chromosome[] chromes, int numKeep) {

		double S = 2.0;	// Selection pressure of 2 gives max chance of fittest Chromesome being selected.
		int N = chromes.length;	// The number of chromosomes
		int k;		// The ranking of each Chromesome (k=N is most fit, k=1 least fit)

		// An array to hold the Chromosomes selected to be parents
		ArrayList<Chromosome> parents = new ArrayList<Chromosome>(numKeep);

		// Add the fitest chromosome to the array:
		parents.add(chromes[0]);
		chromes[0].selected = true;

		// Run through the chromes array and decide if a Chromosome should survive to the next
		// generation. Keep looping until numKeep Chromosomes have been selected.

		Random rand = new Random();

		// Calculate the probabilities of each Chromosome being chosen.
		for (int i = 0; i < N; i++) {
			// Chromosome ranking is 0=best, must be converted to N=best
			k = (chromes[i].getRanking() - N) * -1;
			chromes[i].prob = (double) (2 - S + ((2 * (S - 1) * (k - 1)) / (N - 1))) / N;
		}

		// Now get a random number and run through the list, adding the probability of the current
		// Chromosome to the cumulative total. Return the current Chromosone if the cumulative total
		// exceeds the random number.

		while (parents.size() < numKeep) {

			double random = rand.nextFloat();
			double sumP = 0.0;
			// The for loop goes from 0-N but we want to run through the list from lowest ranking to
			// highest (N-0) so index keeps track of the current chromosome
			int index;

			for (int i = 0; i < N; i++) {
				index = N - i - 1;

				sumP += chromes[index].prob;

				if ((sumP > random) && (chromes[index].selected == false)) {
					chromes[index].selected = true;
					parents.add(chromes[index]);
					sumP = 0.0;
					break;
				} // if sumP

			} // for
		} // while

		// Covert the ArrayList to a Chromosome array
		Chromosome[] parentsArray = new Chromosome[parents.size()];
		for (int i = 0; i < parentsArray.length; i++) {
			parentsArray[i] = (Chromosome) parents.get(i);
		}
		return parentsArray;
	}

	/**
	 * Creates an array of child Chromosomes from an array of parents.
	 *
	 * <p>Intermediate recombination will be used. Parents to make up the new chromosomes are chosen
	 * randomly.
	 *
	 * @param parents the array containing all parent Chromosomes from which the population of
	 * children will be created.
	 * @param populationSize the total size of the population to be created (including the parents).
	 * @param clazz The new Chromosomes that are being created.
	 * @return a new population of (populationSize-parents.length) Chromosomes, the children.
	 */
	@SuppressWarnings("null")
	public static Chromosome[] recombine(Chromosome[] parents, int populationSize,
			Class<? extends Chromosome> clazz) throws GAException {

		ArrayList<Chromosome> children = new ArrayList<Chromosome>(populationSize - parents.length);

		// Each parent has an equal probability of being chosen.
		double prob = (double) 1 / parents.length;

		double sumProb = 0.0;
		Chromosome parent1 = null;
		Chromosome parent2 = null;
		int numParents = 0;	// The number of parents chosen so far.
		Random rand = new Random();
		double random;

		while (children.size() < (populationSize - parents.length)) {
			// Randomly choose two parents using same technique as that used by select() function.
			// Might have to try a few times if we get the same parent twice.
			while (numParents < 2) {

				// Choose parent 1:
				sumProb = 0.0;
				random = rand.nextFloat();
				for (int i = 0; i < parents.length; i++) {
					sumProb += prob;
					if (sumProb > random) {
						parent1 = parents[i];
						numParents = 1;
						break;
					} // if
				} // for
				// Choose parent 2:
				sumProb = 0.0;
				random = rand.nextFloat();
				for (int i = 0; i < parents.length; i++) {
					sumProb += prob;
					if ((sumProb > random) && (parent1.equals(parents[i]) == false)) {
						parent2 = parents[i];
						numParents = 2;
						break;
					} // if
				} // for
			} // while numParents < 2
			
			assert parent1 != null && parent2 != null ;

			//       System.out.println("selected two parents: "+p1+" "+p2);//parent1.print();parent2.print();

			// Now we have two parents combine their genes to form children using intermediate
			// recombination.

			// Array to store the new Genes created for each child
			//       Gene[] genes = new Gene[parents[0].getGenes().length];

			Chromosome child = null;
			try {
				child = clazz.newInstance();
			}
			catch (InstantiationException ex) {
				throw new GAException("Caught an InstantiationException trying to "
						+ "instantiate a child chromosome: " + clazz.toString() + ".", ex);
			}
			catch (IllegalAccessException ex) {
				throw new GAException("Caught an IllegalAccessException trying to "
						+ "instantiate a child chromosome: " + clazz.toString() + ".", ex);
			}
			Gene[] genes = child.getGenes();

			// a is a value uniformly distributed over the range [-d : 1+d] (where d=0 ensures the
			// offspring value is somewhere between that of it's parents, but can results in a shrinkage
			// in range of chromosomes).

			
			double a = (rand.nextFloat() * (2*Chromosome.mutate)) - Chromosome.mutate; // This makes range of a = [-0.25:1.25]
			double geneValue;

			for (int i = 0; i < parent1.getGenes().length; i++) {
				geneValue = (a * parent1.getGenes()[i].getValue()) + ((1 - a) * parent2.getGenes()[i].getValue());

				if (child.getGenes()[i].setValue(geneValue) == false) {
					//	   System.out.println("Gene out of range");
					boolean test = false;
					// Gene is out of range, set it to the max/min value
					if (geneValue > genes[i].getMaxValue()) {
						test = child.getGenes()[i].setValue(genes[i].getMaxValue());
					}
					else {
						test = child.getGenes()[i].setValue(genes[i].getMinValue());
					}
					if (test == false) {
						throw new GAException("Chromosome: recombine: Error setting gene value");
					}
				}
			} // for

			// Create a new Chromosome from the Genes and add it to the children array
			children.add(child);

			numParents = 0;
			parent1 = null;
			parent2 = null;
		}
		// while children.size

		// Convert to Chromosome array and return entire population

		Chromosome[] childrenArray = new Chromosome[children.size()];

		for (int i = 0; i < childrenArray.length; i++) {
			childrenArray[i] = (Chromosome) children.get(i);
		}
		return childrenArray;
	}

	/* ***************************** GET / SET METHODS ***************************/
	/**
	 * Get the fitness of this Chromosome. If this is -1.0 then the Chromosome's fitness has not
	 * yet been calculated
	 */
	public double getFitness() {
		return this.fitness;
	}

	/**Set the fitness of this Chromosome.
	 *
	 * <p>Fitness is automatically set by the calcFitness() method once the Model has been run. This
	 * function should only be used to set the fitness if there was an error while running the model.
	 *
	 * @param fitness the new fitness value.
	 */
	public void setFitness(double fitness) {
		this.fitness = fitness;
	}

	/**Returns the Genes that make up this Chromosome.
	 *
	 * @return An array of Genes.
	 */
	public Gene[] getGenes() {
		return this.genes;
	}

	/**Returns this Chromosomes current ranking.
	 *
	 * @return the position of this Chromosome in a list sorted by fitness.
	 */
	public int getRanking() {
		return this.ranking;
	}

	/**Set this Chromosomes ranking.
	 *
	 * @param ranking the position of this Chromosome in a list of Chromosomes sorted by fitness.
	 */
	public void setRanking(int ranking) {
		this.ranking = ranking;
	}

	/**Returns this Chromosomes selected status.
	 *
	 * @return the true if this Chromosome has been selected, false otherwise
	 */
	public boolean getSelected() {
		return this.selected;
	}

	/**Set this Chromosomes selected status.
	 *
	 * @param selected whether or not this Chromosome has been selected.
	 */
	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	// **************************** MISCELANEOUS METHODS *************************
	/**Tests to see if this Chromosome is equal to another. If the value of each Gene in the
	 * Chromosomes are the same then they are equal.
	 *
	 * @param chrome the Chromosome to compare to this chromosome
	 * @return true if they are equal.
	 */
	@Override
	public boolean equals(Object obj) {

		if (!(obj instanceof Chromosome)) {
			return false;
		}

		Chromosome chrome = (Chromosome) obj;
		if (this.getGenes().length != chrome.getGenes().length) {
			return false;
		}

		for (int i = 0; i < this.getGenes().length; i++) {
			if (this.getGenes()[i].equals(chrome.getGenes()[i])) {
				return false;
			}
		}
		return true;
	}

	@Override
	public int hashCode() {
		//		double hash = 1;
		//		for (Gene g : this.genes) {
		//			hash += (g.getValue() * 10);
		//		}
		//		return (int) hash;
		return this.id; // ID should be unique
	}

	/**Reset the fitness and the ranking of this Chromosome.
	 */
	public void reset() {
		this.fitness = -1.0;
		this.ranking = -1;
		this.selected = false;
		this.prob = -1.0;
	}

	/**Print the information about this Chromosome.
	 */
	public void print() {
		System.out.println("******************************************");
		System.out.println("Printing Chromosome information:");
		if (this.fitness != -1.0) {
			System.out.println("Fitness: " + fitness);
		}
		for (int i = 0; i < genes.length; i++) {
			System.out.println(this.genes[i].getName() + ": " + this.genes[i].getValue());
		}
		System.out.println("Ranking: " + this.ranking);
		System.out.println("******************************************");
	}

	/**Return a string with the information about this Chromosome.
	 *
	 * @return information about this Chromosome in the form of a csv line
	 */
	public String toCSVString() {
		StringBuffer string = new StringBuffer();
		string.append(fitness + ",");
		for (int i = 0; i < genes.length; i++) {
			string.append(this.genes[i].getValue() + ",");
		}
		return string.toString();
	}

	/**
	 * Return a string with the information about this Chromosome.
	 *
	 * @return information about this Chromosome
	 */
	@Override
	public String toString() {
		StringBuffer string = new StringBuffer();
		string.append("******************************************\n");
		if (this.fitness != -1.0) {
			string.append("Fitness: " + fitness + "\n");
		}
		for (int i = 0; i < genes.length; i++) {
			string.append(this.genes[i].getName() + ": " + this.genes[i].getValue() + "\n");
		}
		if (this.ranking != -1) {
			string.append("Ranking: " + this.ranking + "\n");
		}
		string.append("******************************************\n");
		return string.toString();
	}

	/**
	 * Returns a string containing information about this Chromosomes genes.
	 *
	 * @return a string formated as CSV
	 */
	public String getCSVGeneInfo() {
		StringBuffer string = new StringBuffer();
		for (int i = 0; i < genes.length; i++) {
			string.append(genes[i].getName() + ",");
		}
		return string.toString();
	}

	/**
	 * Returns a string containing information about this Chromosomes genes.
	 *
	 * @return a string.
	 */
	public String getGeneInfo() {
		StringBuffer string = new StringBuffer();
		Gene gene = null;
		for (int i = 0; i < genes.length; i++) {
			gene = genes[i];
			string.append(gene.getName() + ": " + gene.getMinValue() + "-" + gene.getMaxValue() + "\n");
		}

		return string.toString();
	}

	/**
	 * A method that can be called at the end of a run to clean up (delete temporary
	 * directories etc). Doesn't need to be overridden.
	 *
	 * @return True if successful.
	 * @throws  An exception if there was a problem.
	 *
	 */
	public boolean clean() throws Exception {
		return true;
	}

	/**
	 * Give this Chromosome the given gene values
	 */
	public void setGenes(Gene[] genes) {
		for (int i=0; i<this.genes.length; i++) {
			this.genes[i] = new Gene(genes[i]);
		}
		// Don't know fitness etc because the genes have just changed.
		this.reset();
	}

}
