package uk.ac.leeds.mass.genetic_algorithm;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import mpi.MPI;
import mpi.Request;
import mpi.Status;

/**
 * An extension of the Genetic class to distribute a run on the grid or in an MPJ multi-core environment. 
 *
 * @author Nick Malleson
 * @see Chromosome
 * @see TestChromosome
 * @see Genetic
 */
public class GeneticMPJ {

	/** The number of nodes available for processing. */
	private int nodes;
	/** The unique rank of a node (assigned in ascending order from 0)*/
	private int rank;
	private final int tag = 50; // Used to identify multiple incoming messages
	private GALog log;	// Used for logging stuff from this job
	/** The <code>Chromosome</code> class used to run models. */
	private Class<? extends Chromosome> clazz;
	/** The maximum number of iterations before termination */
	private int maxIter = -1;
	/** The total number of chromosomes in each iteration, this will be set to the
	 * number of available compute nodes */
	private int numChromes = -1;
	/** The number of chromosomes to keep after each iteration. This is set to half
	 * the total number of chromosomes*/
	private int numKeep = -1;
	/** Convergence factor (size of mutation at each generation) */
	protected double converge = 0.99;
	/** The lower threshold for GoF to stop the algorithm (i.e. a perfect chromosome) */
	private double minFit = 0;
	/** The population of Chromosomes */
	private Chromosome[] chromes;
	/** A file to write csv GA results to */
	private File outFile = null;
	/** A file to write full results to (every gene in every iteration)*/
	private File outFileFull = null;
	// The bufferedWriter to write results to
	private BufferedWriter outWriter = null;
	private BufferedWriter outWriterFull = null;
	/** Used by other classes to tell a node to terminate the current model (i.e. error has occurrec)*/
//	private static boolean stopSim = false;
	/* Messages are passed as an array of objects. Element 0 is an integer telling the receiver
	 * something and remaining objects contain data (e.g. the model name a slave should execute). */
	/** Indicates that a slave should finish processing, it won't be given any other jobs */
	private static final int EXIT = 1;
	/** Tell a slave that it should run a model (it will also be passed a chromosome) */
	private static final int RUN_MODEL = 2;
	/** Tell the master the model was run successfully. */
	private static final int MODEL_SUCCESS = 3;
	/** Tell the master that something went wrong with the model */
	private static final int MODEL_FAILURE = 4;
	/** Tell the master there was a problem but not model failure */
	private static final int UNSPECIFIED_PROBLEM = 5;

	/**
	 * Initialises a new <code>GeneticMPJ</code> object and initialise MPI as
	 * well as GA requirements (genes, chromosomes etc).
	 *
	 * @param args Command line arguments are required for MPI.
	 * @param clazz The class of the <code>Chromosome</code> that make up the population.
	 * @param maxIter The number of iterations to run
	 * @param numChromosomes The number of chromosomes in the population
	 * @param numKeep The number of chromosomes to keep after each iteration
	 */
	public GeneticMPJ(String args[], Class<? extends Chromosome> clazz, int maxIter, int numChromosomes, int numKeep) {

		// Configure and initialise MPI
		MPI.Init(args);

		this.clazz = clazz;
		this.maxIter = maxIter;

		this.nodes = MPI.COMM_WORLD.Size(); // The number of available compute nodes
		this.rank = MPI.COMM_WORLD.Rank(); // The unique rank of this node

		// Set the number of chromosomes per iteration (equal to the number of slaves)
		// (Note: this is just to simplify the algorithm).
		//      this.numChromes = this.nodes - 1; // OLD
		//      this.numKeep = (int) (this.numChromes * 0.5); // Keep ~50% of the chromosomes after each iteration

		this.numChromes = numChromosomes;
		this.numKeep = numKeep;


		// Start slave/master functions
		if (this.rank == 0) { // This is the master
			this.master();
		}
		else { // This is a slave
			this.slave();
		}
		log.log("Finished, closing logs and running MPI.Finalise()");
		log.closeLog();
		MPI.Finalize();
	}

	/**
	 * This function is called when this node is classified as a master. It creates
	 * chromosomes and assigns them to nodes.
	 */
	private void master() {

		try {
			double startTime = System.currentTimeMillis();

			// Inititlise the GALog
			this.log = new GALog(this.rank, GALog.findNewLogDir(this.rank));

			log.log("Master is starting with " + this.nodes + " nodes in total.");

			// Initialise the Chromosomes
			chromes = new Chromosome[numChromes];
			for (int c = 0; c < this.chromes.length; c++) {
				this.chromes[c] = this.clazz.newInstance();
			}

			// Create an output file to write results of this run
			this.createOutputFiles();

			// Send messages as arrays of objects
			Object[] output = new Object[2]; // For sent messages (instruction and chromosome)
			Object[][] input = new Object[this.nodes - 1][]; // For received messages (one array for each slave)

			// See if there are more nodes than chromosomes
			if (this.numChromes < this.nodes - 1) { // nodes-1 because one is reserved for master
				log.log("More nodes than chromosomes, will kill some processes");
				output[0] = EXIT; // Send an exit message to the unrequired nodes
				output[1] = "";
				for (int i = this.numChromes + 1; i < this.nodes; i++) {
					log.log("Sending exit message to node " + i + ": " + translateMessage(EXIT));
					MPI.COMM_WORLD.Isend(output, 0, output.length, MPI.OBJECT, i, tag);
				}
				this.nodes = this.numChromes + 1;
			}

			/*
			 * Loop until the max number of iterations has been reached (of the best fit
			 * hits a threshold.
			 */

			double bestFit = Double.MAX_VALUE; // The best fit of all models
			int iter = 0; // Count the number of GA iterations
			while (iter < this.maxIter && (bestFit > minFit)) {

				// Need to index jobs, also records the number that have been sent. Start at 1 because index 0 is master.
				int jobsSent = 0;
				int jobsCompleted = 0;
				Request[] recvRequests = new Request[this.nodes - 1]; // Array to store responses from slaves
				int[] chromeNodeLink = new int[this.nodes - 1]; // Array to remember which chromosome each node is running

				// Send the first batch of jobs to the slaves(calculate the fitnesses of each chromosome).
				for (int node = 1; node < this.nodes; node++) { // Loop over all slave nodes (0 is master)
//					int chrome = i-1; // The chromosome number
					int chrome = jobsSent; // The chromosome number
					assert jobsSent == (node-1) && jobsSent == chrome;
					// Send slave the RUN_MODEL instruction and a chromosome.
					output[0] = RUN_MODEL;
					output[1] = chromes[chrome];
					log.log("Sending first batch messgae: '" + translateMessage(RUN_MODEL) + "' with chromosome "+chrome+" to node " + node + "...");
					MPI.COMM_WORLD.Isend(output, 0, output.length, MPI.OBJECT, node, tag);
					// Listen for responses
					input[node - 1] = new Object[2]; // Two elements: message and maybe a model fitness
					recvRequests[node - 1] = MPI.COMM_WORLD.Irecv(input[chrome], 0, input[chrome].length, MPI.OBJECT, node, tag);
					chromeNodeLink[node-1] = chrome; // Remember which chromosome was sent to the node
//					log.log("Sent '" + output[0].toString() + "' to " + node);
//					double[] fitarray= new double[this.chromes.length];
//					for (int i=0; i<this.chromes.length; i++) {
//						fitarray[i] = chromes[i].getFitness();
//					}
//					log.log(Arrays.toString(chromeNodeLink)+" - "+Arrays.toString(fitarray));

					jobsSent++;
				} // for first batch
				

				log.log("Sent initial batch, now farming out remaining jobs as existing ones complete");

				// Now wait for responses (wont start next GA iteration until all jobs have returned.
				while (jobsCompleted < this.numChromes) {
					Status status = Request.Waitany(recvRequests); // Block until a request has completed
					// If get here then have received a message from one of the dispatched jobs
					int source = status.source; // The source of the recieved message
					int message = (Integer) input[source - 1][0];
					int chrome = chromeNodeLink[source-1]; // Find out which chromosome was submitted to source
					log.log("Received message '" + translateMessage(message) + "' from: "
							+ source + " which processsed chromosome "+ chrome +" (jobs completed/sent/total: " + 
							jobsCompleted + "," + jobsSent + "," + this.numChromes +")");
//					double[] fitarray = new double[this.chromes.length];
//					for (int i=0; i<this.chromes.length; i++) {
//						fitarray[i] = chromes[i].getFitness();
//					}
//					log.log(Arrays.toString(chromeNodeLink)+" - "+Arrays.toString(fitarray));


					if (message == MODEL_SUCCESS) {
						// If model was successfull the slave will have sent the fitness of its chromosome
						double fit = (Double) input[source-1][1];
						log.log("Received model fitness of "+fit);
						if (fit < bestFit) {
							bestFit = fit;
							log.log("\t ******* Best fitness so far ******* ("+fit+")");
						}
						chromes[chrome].setFitness(fit);
					}
					else if (message == MODEL_FAILURE) {
						// Failure, just set a high fitness so the chromosome doesn't affect results.
						log.log("Message was MODEL_FAILURE.");
						chromes[chrome].setFitness(9999);
					}
					else if (message == UNSPECIFIED_PROBLEM) {
						log.log("Message was UNSPECIFIED_PROBLEM.");
						chromes[chrome].setFitness(9999);
					}
					else {
						log.log("Didn't understand the slave's response (" + message + "), ignoring it.");
					}

					jobsCompleted++;

					// Need to send another job?
					if (jobsSent < this.numChromes) {
						
//						assert jobsSent == (node-1) && jobsSent == chrome;
						int newChrome = jobsSent; // The new chromosome to be submitted
						
						// Send slave the RUN_MODEL instruction and a chromosome.
						output[0] = RUN_MODEL;
						output[1] = chromes[newChrome];
						log.log("Sending runmodel messgae: '" + translateMessage(RUN_MODEL) + "' with chromosome "+newChrome+" to node " + source+ "...");
						MPI.COMM_WORLD.Isend(output, 0, output.length, MPI.OBJECT, source, tag);
						// Listen for responses
						input[source-1] = new Object[2]; // Two elements: message and maybe a model fitness
						recvRequests[source - 1] = MPI.COMM_WORLD.Irecv(input[source-1], 0, input[source-1].length, MPI.OBJECT, source, tag);
						chromeNodeLink[source - 1] = newChrome; // Remember which chromosome was sent to the node
						log.log("Sent '" + output[0].toString() + "' to " + source+ " with chromosome " + newChrome);
//						fitarray = new double[this.chromes.length];
//						for (int i=0; i<this.chromes.length; i++) {
//							fitarray[i] = chromes[i].getFitness();
//						}
//						log.log(Arrays.toString(chromeNodeLink)+" - "+Arrays.toString(fitarray));
						jobsSent++;
					}

				}

				/* Have received results from all jobs. */
				
				// Check that all chromosomes have a fitness
				for (int i=0; i<this.chromes.length; i++) {
					assert chromes[i].getFitness()!=-1.0 : 
						"Internal error, chromosome "+i+" has not had fitness calculated.";
				}
				
				
				 /* Create the new population for the next iteration. */
				chromes = Chromosome.sort(chromes);	// Sort by fitness

				// Write best chromosome info to file (in csv form)
				outWriter.write(iter - 1 + "," + chromes[0].toCSVString() + "\n");
				outWriter.flush();

				// Write *all* the chromosomes to the full results file
				for (Chromosome c:chromes) {
					outWriterFull.write(iter - 1 + "," + c.toCSVString() + "\n");
				}
				outWriter.flush();

				log.log("Sorted Chromosomes. Best are:");
				log.log(chromes[0].toString());
				log.log(chromes[1].toString());

				log.log("Creating new population of chromosome by producing children and mutating.");

				// Select parents to form the new generation:
				Chromosome[] parents = Chromosome.select(chromes, numKeep);
				
				

				// Breed some children chromosomes. The parents will form the first numKeep chromosomes, the
				// rest are construced from combining parent genes.
				Chromosome[] children = Chromosome.recombine(parents, chromes.length, this.clazz);

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

				//            // To optimse the best fit so far, create a mutated copy of the best chromosome
				//            // (and replace one of the newly created children - last one in list)
				Chromosome mutatedBest = this.clazz.newInstance();
				mutatedBest.setGenes(chromes[0].getGenes());
				mutatedBest.mutate();
				chromes[chromes.length-1] = mutatedBest;

				// Reduce the size of mutations for the next iteration (must change every gene on every
				// Chromosome!)
				for (int i = 0; i < chromes.length; i++) {
					for (int j = 0; j < chromes[i].getGenes().length; j++) {
						Gene gene = chromes[i].getGenes()[j];
						chromes[i].getGenes()[j].setMutate(gene.getMutate() * converge);
					}
				}

				log.log("Finished iteration " + iter + ". Starting next iteration.");
				iter++;
			}

			/*
			 * If we get here then the algorithm has finished successfully.
			 */
			outWriter.close();
			outWriterFull.close();
			log.log("Received all reponses, telling slaves to exit.");
			output = new Object[1];
			output[0] = EXIT;
			for (int i = 1; i < this.nodes; i++) {
				output[0] = EXIT;
				MPI.COMM_WORLD.Isend(output, 0, 1, MPI.OBJECT, i, tag);
			}
			log.log("Sent exit messages, finished. Run tim was "+
					((System.currentTimeMillis()-startTime)/60000)+" mins.");

		}
		catch (Exception e) {
			System.out.println("XXXX"+e.getMessage());
			e.printStackTrace();
			log.log("Master caught an exception, can't continue. Message: '"
					+ (e.getMessage() == null ? "null" : e.getMessage())
					+ "'." + " Cause: '"
					+ (e.getCause() == null ? "null" : e.getCause().toString())
					+ "'" + ". Logging stack trace.");
			log.log(e.getStackTrace());
			// Tell all slaves to exit
			Object[] output = new Object[2];
			output[0] = EXIT;
			for (int i = 1; i < this.nodes; i++) {
				MPI.COMM_WORLD.Isend(output, 0, output.length, MPI.OBJECT, i, tag);
			}
			MPI.Finalize();
		}
		finally { // Try to clean up (deleting the temporary directory that chromosomes
			// might have created to store results.
			try {
				// Try to clean up (deleting the temporary directory that chromosomes
				// might have created to store results.
				this.clazz.newInstance().clean();
				log.log("Master has cleaned up (successfully deleted temporary directories)");
			}
			catch (Exception ex) {
				System.out.println(ex.getMessage());
				ex.printStackTrace();
				log.log("Warning, could not clean up: "+ex.getMessage());
			}


		}
	}

	/**
	 * This function is used when this node is a slave. It is sent a chromosome by the
	 * master node and is responsible for running the model and returning a result
	 * (the fitness of the chromosome in this case).
	 */
	private void slave() {

		boolean finished = false;
		while (!finished) {
			Object[] output = new Object[2]; // For sent messages
			Object[] input = new Object[2]; // For received messages (instruction and chromosome)
			try {
				MPI.COMM_WORLD.Recv(input, 0, input.length, MPI.OBJECT, 0, tag); // blocking receive
				if (this.log == null) {
					this.log = new GALog(this.rank, GALog.findNewLogDir(this.rank));
				}

				// Work out what the message from the master was
				int message = -1;
				message = (Integer) input[0];

				log.log("Received message: " + translateMessage(message));
				if (message == EXIT) { // The message from the master is to quit
					log.log("Master sent EXIT message so exiting...");
					finished = true;
				}
				else if (message == RUN_MODEL) {
					log.log("Master sent RUN_MODEL");
					// Along with the message, the master should have sent a chromosome
					Chromosome chrome = (Chromosome) input[1];
					log.log("Have received a chromosome: \n"+chrome.toString());

					// RUN THE MODEL, and tell the chromosome which slave is running it.
					chrome.calcFitness();

					double fit = chrome.getFitness();
					log.log("Calculated fitness: "+fit);

					// Finished working, send result (or at least an acknowledgement) to master
					output[0] = MODEL_SUCCESS;
					// Send the chromosome fitness back
					output[1] = fit;
					MPI.COMM_WORLD.Send(output, 0, output.length, MPI.OBJECT, 0, tag);
					log.log("Sent response " + translateMessage(MODEL_SUCCESS) + "to master.");
				}
				else { // Didn't understand message, tell the master
					log.log("Didnt understand the message from the master: " + input[0].toString());
					output[0] = UNSPECIFIED_PROBLEM;
					MPI.COMM_WORLD.Send(output, 0, output.length, MPI.OBJECT, 0, tag);
				} // else
			} // try
			catch (GAException ex) {
				log.log("Error running the chromosome: " + ex.getMessage());
				log.log(ex.getStackTrace());
				output[0] = MODEL_FAILURE;
				MPI.COMM_WORLD.Send(output, 0, output.length, MPI.OBJECT, 0, tag);
			}
			catch (NumberFormatException ex) {
				log.log("Could not translate the message from the master into an "
						+ "integer: " + input[0].toString());
				output[0] = UNSPECIFIED_PROBLEM;
				MPI.COMM_WORLD.Send(output, 0, output.length, MPI.OBJECT, 0, tag);
			}
			catch (Exception e) {
				log.log("Caught an exception. Message: '"
						+ (e.getMessage() == null ? "null" : e.getMessage())
						+ "'." + " Cause: '"
						+ (e.getCause() == null ? "null" : e.getCause().toString())
						+ "'" + ". Logging stack trace.");
				log.log(e.getStackTrace());
				output[0] = UNSPECIFIED_PROBLEM;
				MPI.COMM_WORLD.Send(output, 0, output.length, MPI.OBJECT, 0, tag);
			}
		} // while !finished
	}

	private static String twoDigit(int number) {
		if (number < 10) {
			return "0" + number;
		}
		else {
			return "" + number;
		}
	}

//	private static String threeDigit(int number) {
//		if (number < 10) {
//			return "00" + number;
//		}
//		else if (number < 100) {
//			return "0" + number;
//		}
//		else {
//			return "" + number;
//		}
//	}

	/** Translate a numerical message (passed between master and slaves) into textual meaning */
	private static String translateMessage(int message) {
		if (message == EXIT) {
			return "EXIT";
		}
		else if (message == RUN_MODEL) {
			return "RUN_MODEL";
		}
		else if (message == MODEL_SUCCESS) {
			return "MODEL_SUCCESS";
		}
		else if (message == MODEL_FAILURE) {
			return "MODEL_FAILURE";
		}
		else if (message == UNSPECIFIED_PROBLEM) {
			return "UNSPECIFIED_PROBLEM";
		}
		else {
			return "UNTRANSLATABLE MESSAGE";
		}
	}

	private void createOutputFiles() throws GAException {
		// Create a file to write the output graph to (make sure it is unique)
		File f = new File("./ga_output.csv");
		File f2 = new File("./ga_output_full.csv");
		int counter = 0;
		while (f.exists()) {
			counter++;
			f = new File("./ga_output" + twoDigit(counter) + ".csv");
			f2 = new File("./ga_output_full" + twoDigit(counter) + ".csv");
		}
		log.log("GeneticMPJ.main Will write output to files: " + f.getAbsolutePath()+
				"and "+f2.getAbsolutePath());
		this.outFile = f;
		this.outFileFull = f2;

		try {
			this.outWriter = new BufferedWriter(new FileWriter(this.outFile));
			outWriter.write("Genetic Algorithm using following values:\n");
			outWriter.write(chromes[0].getGeneInfo());
			this.outWriter.write("iterations,fitness," + this.chromes[0].getCSVGeneInfo() + "\n");

			this.outWriterFull = new BufferedWriter(new FileWriter(this.outFileFull));
			outWriterFull.write("Genetic Algorithm (full results) using following values:\n");
			outWriterFull.write(chromes[0].getGeneInfo());
			this.outWriterFull.write("iterations,fitness," + this.chromes[0].getCSVGeneInfo() + "\n");
		}
		catch (java.io.IOException e) {
			throw new GAException("Error writing to file " + 
					e.getMessage() == null ? "(no message)" : e.getMessage(), e);
		}
	}

	public static void main(String args[]) {

		new GeneticMPJ(args, TestChromosome.class, 500, 100, 50);
	}

}
