# Making the Agents Work #

Now that we have a clock it is possible to change the agents' behaviour so that they go to work at 9am and then go home again at 5pm. Open the `DefaultAgent` class again and find the `step()` method. This method was scheduled in the `ContextManager` code to be called at each iteration. (If you choose to look at how this is done by the `createSchedule()` method in `ContextManager` you'll notice that the scheduling method is much more complicated than that of our real time counter. This is because the agents are actually executed simultaneously in different threads which makes the simulation run much more quickly on multi-core computers).

At the moment, the code in the agents' `step()` method simply makes the agent travel from home to a randomly chosen building and back again forever. The first thing to do is either delete or comment-out all of the code in the `DefaultAgent.step()` method. Then add the following to make them go to work between at 9am and home at 5pm:

```
// See what the time is, this will determine what the agent should be doing. The BigDecimal stuff
// is just to round the time to 5 decimal places, otherwise it will never be exactly 9.0 or 17.0.
double theTime = BigDecimal.valueOf(ContextManager.realTime).
        round(new MathContext(5,RoundingMode.HALF_UP)).doubleValue();

if (theTime == 9.0) { // 9am, Agent should be working
        this.route = new Route(this, this.workplace.getCoords(), this.workplace); // Create a route to work
}
else if (theTime == 17.0) { // 5pm, agent should go home
        this.route = new Route(this, this.home.getCoords(), this.home); // Create a route home
}

if (this.route == null) {
        // Don't do anything if a route hasn't been created.
} else if (this.route.atDestination()) {
        // Have reached our destination, lets delete the old route (more efficient).
        this.route = null;
}
else {
        // Otherwise travel towards the destination
        this.route.travel();
}
```

Now if you run the model again you should see the agents travel to the banking heartland of York and then home again in the evening after making an invaluable contribution to society. Note that, since we have changed the model source code, you have to restart Repast in order to run the new behaviour. To do this, close the Repast GUI and click on the green arrow in Eclipse to re-run the model.

In the next section we will alter their behaviour in more [mischievous ways](BankersBurglary.md).