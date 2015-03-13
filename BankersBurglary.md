# Topping Up their Bonuses with Burglary #

In these tough economic times, our banker agents have been hit harder than others and decide to resort to burglary to supplement their meagre bonuses. There are a huge number of factors that could be used to realistically model burglary, but in this simple application the agents will just examine each house that they pass and throw a dice to decide whether or not to burgle.

We will add this functionality immediately after the agent has finished travelling. So, open `DefaultAgent` and find the `step()` method that we edited earlier. Then, replace the last `else` statement

```
} else {
	// Otherwise travel towards the destination
	this.route.travel();
}
```

with the following:

```
} else {
	// Otherwise travel towards the destination
	this.route.travel();
	for (Building b : this.route.getPassedBuildings()) {
		if (b.getType() == 1) { // Only burgle houses (not banks too)
		// Roll a dice to see if this house should be burgled (1 in 100 chance)
		double random;
		synchronized (ContextManager.randomLock) {
			// This synchronized block ensures that only one agent at a time can access RandomHelper
			random = RandomHelper.nextDouble();
		}
		if (random >= 0.99) {
			b.burgled(); // Tell the building that it has been burgled
			LOGGER.log(Level.INFO, "Agent " + this.toString() + " has burgled building "
                                        + b.getIdentifier() + "Total: " + b.getNumBurglaries() + 
					". Random value: " + random);
			}
		}
	} // for passed buildings
} // else
```

The `Route` object allows the agents to retrieve a list of all the buildings that they passed the last time they travelled (`route.getPassedBuildings()`). So, the above code (which is called each time an agent moves) loops through all the buildings they have just passed, checks if they are houses, and has a 1% change of burgling each house. Note that the `RandomHelper` class is used to generate the random number. This is a Simphony class and means that if we configure the random number generation properly it should be possible to make a simulation repeatable. We need to have the `random = RandomHelper.nextDouble();` call in a `synchronized` block to make sure that two agents don't try to get a random number at the same time (for some reason Simphony doesn't like this!).

Afterwards, the whole `step()` function should look like this:

```
public void step() throws Exception {

        // See what the time is, this will determine what the agent should be doing. The BigDecimal stuff
        // is just to round the time to 5 decimal places, otherwise it will never be exactly 9.0 or 17.0.
        double theTime = BigDecimal.valueOf(ContextManager.realTime).round(new MathContext(5, RoundingMode.HALF_UP))
                        .doubleValue();

        if (theTime == 9.0) { // 9am, Agent should be working
                this.route = new Route(this, this.workplace.getCoords(), this.workplace); // Create a route to work
        } else if (theTime == 17.0) { // 5pm, agent should go home
                this.route = new Route(this, this.home.getCoords(), this.home); // Create a route home
        }

        if (this.route == null) {
                // Don't do anything if a route hasn't been created.
        } else if (this.route.atDestination()) {
                // Have reached our destination, lets delete the old route (more efficient).
                this.route = null;
        } else {
                // Otherwise travel towards the destination
                this.route.travel();
                for (Building b : this.route.getPassedBuildings()) {
                        if (b.getType() == 1) { // Only burgle houses (not banks too)
                                // Roll a dice to see if this house should be burgled (1 in 100 chance)
                                double random;
                                synchronized (ContextManager.randomLock) {
                                        // This synchronized block ensures that only one agent at a time can access RandomHelper
                                        random = RandomHelper.nextDouble();
                                }
                                if (random >= 0.99) {
                                        b.burgled(); // Tell the building that it has been burgled
                                        LOGGER.log(Level.INFO, "Agent " + this.toString() + " has burgled building "
                                                        + b.getIdentifier() + "Total: " + b.getNumBurglaries() + ". Random value: " + random);
                                }
                        }
                } // for passed buildings
        } // else
} // step()
```

As it stands, when a burglary happens a message is printed to the Eclipse console, but there is no other indication that a burglary has taken place. I haven't worked out how to do this with Repast, but in the next section we will go over a way of [outputting the burglary data](BankersResults.md) at the end of the simulation.