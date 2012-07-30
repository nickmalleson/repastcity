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

import java.io.File;
import java.io.FileWriter;

/**
 * The Genetic class implements a genetic algorithm (GA) to optimise a model.
 * Models should subclass the Chromosome class.
 *
 * <p><B>Important: </B>the algorithm assumes that a lower fitness value
 * means a better model. If this is not the case for your model (e.g. using something
 * like R2 to measure error) then the resulting model fitness will have to be
 * reversed so that lower fitness means a better model. Note that the setMinFit()
 * value can be used to set the value of a 'perfect' model; upon reaching this value
 * the algorithm will terminate.</p>
 *
 * <p>To use the Genetic Algorithm classes, simply subclass the <code>Chromosome</code>
 * class and override the required methods. These are responsible for running a model
 * and calculating the fitness of the model. Then create an instance of a
 * <code>Genetic</code> object, passing the <code>Chromosome</code> constructor.
 * See the main method of this class for an exmaple of how to use it.</p>
 *
 * @author Nick Malleson
 * @see Chromosome
 * @see TestChromosome
 */
public class Genetic {

   /* These variables must be configured */
   protected File outFile = null;
   protected int maxIter ;		// Max number of iterations to terminate
   protected int numChromes ;		// The number of chromosomes
   protected int numKeep = 25;		// The number of chromosomes to keep after each iteration

   /* These variables can be configured but don't necessarily need to be */
   protected double converge = 0.99;	// Convergence factor (size of mutation at each generation)
   protected double minFit = 1.0;		// The minimum fitness to terminate the algorithm

   // Private variables used to run the algorithm.
   protected int iterations = 0;
   protected FileWriter outWriter = null;
   
   protected double bestFit = 99999;	// The current fitness of the best chromosome
   
   protected Chromosome[] chromes = null;	// An array to store all the Chromosomes
   protected Class<? extends Chromosome> clazz = null;

   /** Do not use this constructor, it is only provided so tha the class can be
    * supclassed.
    * @see GeneticMPJ
    */
   public Genetic(){}

   /**
    * * Create an instance of a genetic algorithm object and subsequently call
    * <code>run()</code> to start the algorithm.
    * @param chromosomeClass The class of the chromosomes which make up the GA
    * @param outputFile A file to write output to
    * @param maxIter The number of iterations to run
    * @param numChromosomes The number of chromosomes in the population
    * @param numKeep The number of chromosomes to keep after each iteration
    * @throws GAException
    */
   public Genetic (Class<? extends Chromosome> chromosomeClass, File outputFile, int maxIter,
           int numChromosomes, int numKeep) throws GAException {

      this.clazz = chromosomeClass;
      this.outFile = outputFile;
      this.maxIter = maxIter;
      this.numChromes = numChromosomes;
      this.numKeep = numKeep;
      if (numChromosomes < numKeep) {
         throw new GAException("Cannot create Genetic object because the number of chromosomes " +
                 "to keep after each iteration ("+numKeep+")is less than the number of " +
                 "chromosomes ("+numChromosomes+")");
      }
      System.out.println("Initialising Chromosomes...");
      init();
      System.out.println("\nFinished initialising.");
   }

  
   protected void init() throws GAException {
      // Create chromosomes with random initial values and calculate their fitness.
      chromes = new Chromosome[numChromes];
      double minFitness = 3;


      for (int i = 0; i < numChromes; i++) {
         try {
            chromes[i] = clazz.newInstance();
            chromes[i].calcFitness();
            if (chromes[i].getFitness() < minFitness) {
               minFitness = chromes[i].getFitness();
               //	chromes[i].print();
            }
            System.out.print(i + "...");
         }
         catch (InstantiationException ex) {
            throw new GAException("Caught an InstantiationException trying to "
                    + "instantiate a child chromosome: " + clazz.toString() + ".", ex);
         }
         catch (IllegalAccessException ex) {
            throw new GAException("Caught an IllegalAccessException trying to "
                    + "instantiate a child chromosome: " + clazz.toString() + 
                    ". Is your Chromosome class public and does it have a default " +
                    "public constructor?", ex);
         }
      }
      
      iterations = 1;

   }

   /**
    * Starts the Genetic Algorithm running
    */
   public void run() throws GAException {

      double startTime = System.currentTimeMillis();

      // Prepare the output file
      try {
         this.outWriter = new FileWriter(this.outFile);
         outWriter.write("Genetic Algorithm using following values:\n");
         outWriter.write(chromes[0].getGeneInfo());
         this.outWriter.write("iterations,fitness," + this.chromes[0].getCSVGeneInfo() + "\n");
      }
      catch (java.io.IOException e) {
         throw new GAException("Error writing to file "+this.outFile.getAbsolutePath(), e);
      }

      Chromosome[] parents;
      Chromosome[] children;

//    for (int m=0; m<4; m++) {
      while ((bestFit > minFit) && (iterations < maxIter)) {

         chromes = Chromosome.sort(chromes);	// Sort by fitness

         System.out.println("Sorted Chromosomes. Besy two are are:");
//         for (Chromosome c:chromes) {
//            c.print();
//         }
         chromes[0].print();
         chromes[1].print();

         // Select parents to form the new generation:
         System.out.print(iterations + " Selecting parents...");
         parents = Chromosome.select(chromes, numKeep);
         System.out.println("finished.");

//      for (int i=0; i<parents.length; i++)
//	parents[i].print();

         // Breed some children chromosomes. The parents will form the first numKeep chromosomes, the
         // rest are construced from combining parent genes.
         System.out.print(iterations + " Breeding children...");
         children = Chromosome.recombine(parents, chromes.length, this.clazz);
         System.out.println("finished.");
//      for (int i=0; i<children.length; i++)
//	children[i].print();

         // Mutate the children
         for (int i = 0; i < children.length; i++) {
            children[i].mutate();
         }

         // The new population is constructed from the parents and the children with default values
         for (int i = 0; i < parents.length; i++) {
            chromes[i] = parents[i];
            chromes[i].reset();
         }
         for (int i = 0; i < children.length; i++) {
            chromes[i + numKeep] = children[i];
            chromes[i + numKeep].reset();
         }

//         // To optimse the best fit so far, create a mutated copy of the best chromosome
//         // (and replace one of the newly created children - last one in list)
//         Chromosome mutatedBest;
//         try {
//            mutatedBest = this.clazz.newInstance();
//            mutatedBest.setGenes(chromes[0].getGenes());
//            mutatedBest.mutate();
//            chromes[chromes.length - 1] = mutatedBest;
//         }
//         catch (InstantiationException ex) {
//            throw new GAException("Caught an InstantiationException trying to "
//                    + "instantiate a child chromosome: " + clazz.toString() + ".", ex);
//         }
//         catch (IllegalAccessException ex) {
//            throw new GAException("Caught an IllegalAccessException trying to "
//                    + "instantiate a child chromosome: " + clazz.toString() +
//                    ". Is your Chromosome class public and does it have a default " +
//                    "public constructor?", ex);
//         }


         // Calculate the fitness of each chromosome, catching errors so that the GA can
         // continue running even if a single model fails.
         System.out.print(iterations + " Calculating fitness...");
         for (int i = 0; i < chromes.length; i++) {
            try {
               chromes[i].calcFitness();
            }
            catch (GAException ex) {
               System.err.println("Error running chromosome " + chromes[i].toString());
               chromes[i].setFitness(9999);
            }
            System.out.print(i + "...");
         }
         System.out.println("finished");

         // Reduce the size of mutations for the next iteration (must change every gene on every
         // Chromosome!)
         for (int i = 0; i < chromes.length; i++) {
            for (int j = 0; j < chromes[i].getGenes().length; j++) {
               Gene gene = chromes[i].getGenes()[j];
               chromes[i].getGenes()[j].setMutate(gene.getMutate() * converge);
            }
         }

         chromes = Chromosome.sort(chromes);

         iterations++;
         System.out.println();
         System.out.println("Iteration: " + iterations);

         // Write best chromosome info to file (in csv form)
         try {
            outWriter.write(iterations - 1 + "," + chromes[0].toCSVString() + "\n");
            outWriter.flush();
         }
         catch (java.io.IOException e) {
            System.err.println("Error writing to file");
         }

      } // while

      
      System.out.println("Finished! Run time was " +
              ((System.currentTimeMillis()-startTime) / 60000 )+ " mins.\n"+
              "Best Chromosomes were:");
      chromes[0].print();
      chromes[1].print();
      chromes[2].print();
      chromes[3].print();
      bestFit = chromes[0].getFitness();


      try {
         outWriter.close();
      }
      catch (java.io.IOException e) {
         throw new GAException("Error writing to file "+this.outFile.getAbsolutePath(), e);
      }
   }

   public void printChromes() {
      for (int i = 0; i < numChromes; i++) {
         chromes[i].print();
      }
   }

   /**
    * Set the convergence factor (size of mutation at each generation).
    */
   public void setConverge(double converge) {
      this.converge = converge;
   }


   /**
    * Set the minimum fitness that the algorithm is trying to reach (i.e. a perfect fit).
    * Default is 1.0.
    * @param minFit
    */
   public void setMinFit(double minFit) {
      this.minFit = minFit;
   }


   public static void main(String[] args) {
      if (args.length < 1) {
         System.err.println("Usage: java Genetic output_file_name");
      }
      else {
         String outFileName = args[0];
         File f = new File(outFileName);
         System.out.println("Will write output to file: " + f.getAbsolutePath());
         try {
            Genetic gen = new Genetic(TestChromosome.class, f, 100, 20, 10);
            gen.run();
         }
         catch (GAException ex) {
            System.err.println("Error running GA: " + ex.getMessage() + ". Cause is: "
                    + ex.getCause() == null ? "null" : ex.getCause().getClass() + ", "
                    + ex.getCause().getMessage() + ".");
         }
      }
   }

}
