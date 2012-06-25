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

package repastcity3.agent;

import java.util.logging.Level;
import java.util.logging.Logger;

import repastcity3.main.ContextManager;

/**
 * This class can be used to step agents in different threads simulataneously.
 * If the <code>ContextManager</code> determines that this is a good idea (e.g.
 * if there will be no inter-agent communication) then, rather than using Repast
 * to schedule each agent's step() method directly, it will schedule the
 * agentStep() method (below) instead. This method is then responsible for
 * making the agents step by delegating the work do different threads depending
 * on how many CPU cores are free. As you can imagine, this leads to massive
 * decreases in computation time on multi-core computers.
 * 
 * <p>
 * It is important to note that there will be other side-effects from using
 * multiple threads, particularly agents simultaneously trying to access
 * Building methods or trying to write output data. So care needs to be taken
 * with the rest of the model to prevent problems. The (fairly) naive way that
 * I've tackled this is basically with the liberal use of
 * <code>synchronized</code>
 * </p>
 * 
 * @author Nick Malleson
 * @see ContextManager
 * @see ThreadController
 * @see BurglarThread
 */
public class ThreadedAgentScheduler {
	
	private static Logger LOGGER = Logger.getLogger(ThreadedAgentScheduler.class.getName());

	private boolean burglarsFinishedStepping;

	/**
	 * This is called once per iteration and goes through each burglar calling
	 * their step method. This is done (instead of using Repast scheduler) to
	 * allow multi-threading (each step method can be executed on a free core).
	 * This method actually just starts a ThreadController thread (which handles
	 * spawning threads to step burglars) and waits for it to finish
	 */
	public synchronized void agentStep() {

		this.burglarsFinishedStepping = false;
		(new Thread(new ThreadController(this))).start();
		while (!this.burglarsFinishedStepping) {
			try {
				this.wait(); // Wait for the ThreadController to call setBurglarsFinishedStepping().
			} catch (InterruptedException e) {
				LOGGER.log(Level.SEVERE, "", e);
				ContextManager.stopSim(e, ThreadedAgentScheduler.class);
			}// Wait until the thread controller has finished
		}
	}

	/**
	 * Used to tell the ContextCreator that all burglars have finished their
	 * step methods and it can continue doing whatever it was doing (it will be
	 * waiting while burglars are stepping).
	 */
	public synchronized void setBurglarsFinishedStepping() {
		this.burglarsFinishedStepping = true;
		this.notifyAll();
	}
}

/** Controls the allocation of <code>BurglarThread</code>s to free CPUs */
class ThreadController implements Runnable {
	
	private static Logger LOGGER = Logger.getLogger(ThreadController.class.getName());

	// A pointer to the scheduler, used to inform it when it can wake up
	private ThreadedAgentScheduler cc;

	private int numCPUs; // The number of CPUs which can be utilised
	private boolean[] cpuStatus; // Record which cpus are free (true) or busy
									// (false)

	public ThreadController(ThreadedAgentScheduler cc) {
		this.cc = cc;
		this.numCPUs = Runtime.getRuntime().availableProcessors();
		// Set all CPU status to 'free'
		this.cpuStatus = new boolean[this.numCPUs];
		for (int i = 0; i < this.numCPUs; i++) {
			this.cpuStatus[i] = true;
		}
		// System.out.println("ThreadController found "+this.numCPUs+" CPUs");
	}

	/**
	 * Start the ThreadController. Iterate over all burglars, starting
	 * <code>BurglarThread</code>s on free CPUs. If no free CPUs then wait for a
	 * BurglarThread to finish.
	 */
	public void run() {

		for (IAgent b : ContextManager.getAllAgents()) {

			// Find a free cpu to exectue on
			boolean foundFreeCPU = false; // Determine if there are no free CPUs
											// so thread can wait for one to
											// become free
			while (!foundFreeCPU) {
				synchronized (this) {
					// System.out.println("ThreadController looking for free cpu for burglar "+b.toString()+", "+Arrays.toString(cpuStatus));
					cpus: for (int i = 0; i < this.numCPUs; i++) {
						if (this.cpuStatus[i]) {
							// Start a new thread on the free CPU and set it's
							// status to false
							// System.out.println("ThreadController running burglar "+b.toString()+" on cpu "+i+". ");
							foundFreeCPU = true;
							this.cpuStatus[i] = false;
							(new Thread(new BurglarThread(this, i, b))).start();
							break cpus; // Stop looping over CPUs, have found a
										// free one for this burglar
						}
					} // for cpus
					if (!foundFreeCPU) {
						this.waitForBurglarThread();
					} // if !foundFreeCPU
				}
			} // while !freeCPU
		} // for burglars

		// System.out.println("ThreadController finished looping burglars");

		// Have started stepping over all burglars, now wait for all to finish.
		boolean allFinished = false;
		while (!allFinished) {
			allFinished = true;
			synchronized (this) {
				// System.out.println("ThreadController checking CPU status: "+Arrays.toString(cpuStatus));
				cpus: for (int i = 0; i < this.cpuStatus.length; i++) {
					if (!this.cpuStatus[i]) {
						allFinished = false;
						break cpus;
					}
				} // for cpus
				if (!allFinished) {
					this.waitForBurglarThread();
				}
			}
		} // while !allFinished
			// Finished, tell the context creator to start up again.
			// System.out.println("ThreadController finished stepping all burglars (iteration "+GlobalVars.getIteration()+")"+Arrays.toString(cpuStatus));
		this.cc.setBurglarsFinishedStepping();
	}

	/**
	 * Causes the ThreadController to wait for a BurglarThred to notify it that
	 * it has finished and a CPU has become free.
	 */
	private synchronized void waitForBurglarThread() {
		try {
			// System.out.println("ThreadController got no free cpus, waiting "+Arrays.toString(cpuStatus));
			this.wait();
			// System.out.println("NOTIFIED");
		} catch (InterruptedException e) {
			LOGGER.log(Level.SEVERE, "", e);
			ContextManager.stopSim(e, ThreadedAgentScheduler.class);
		}// Wait until the thread controller has finished

	}

	/**
	 * Tell this <code>ThreadController</code> that one of the CPUs is no free
	 * and it can stop waiting
	 * 
	 * @param cpuNumber
	 *            The CPU which is now free
	 */
	public synchronized void setCPUFree(int cpuNumber) {
		// System.out.println("ThreadController has been notified that CPU "+cpuNumber+" is now free");
		this.cpuStatus[cpuNumber] = true;
		this.notifyAll();
	}

}

/** Single thread to call a Burglar's step method */
class BurglarThread implements Runnable {
	
	private static Logger LOGGER = Logger.getLogger(BurglarThread.class.getName());

	private IAgent theburglar; // The burglar to step
	private ThreadController tc;
	private int cpuNumber; // The cpu that the thread is running on, used so
							// that ThreadController

	// private static int uniqueID = 0;
	// private int id;

	public BurglarThread(ThreadController tc, int cpuNumber, IAgent b) {
		this.tc = tc;
		this.cpuNumber = cpuNumber;
		this.theburglar = b;
		// this.id = BurglarThread.uniqueID++;
	}

	public void run() {
		// System.out.println("BurglarThread "+id+" stepping burglar "+this.theburglar.toString()+" on CPU "+this.cpuNumber);
		try {
			this.theburglar.step();
		} catch (Exception ex) {
			LOGGER.log(Level.SEVERE, "ThreadedAgentScheduler caught an error, telling model to stop", ex);
			ContextManager.stopSim(ex, this.getClass());
		}
		// Tell the ThreadController that this thread has finished
		tc.setCPUFree(this.cpuNumber); // Tell the ThreadController that this
										// thread has finished
	}

}
