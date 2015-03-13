#summary Finding a bank for the Bankers to work in

# Giving Agents Somewhere to Work #

In the basic model, agents are instances of the `DefaultAgent` class which lives in the `repastcity3.agent` package. To make the first change to the agents, we need to open this source file:

  1. In the left hand window (the 'Package Explorer'), expand the **repastcity\_bankers** folder so that you can see the contents of the model.
  1. Expand the `src` folder to see all the source files and then expand```repastcity3.agent``` package.
  1. Open the `DefaultAgent.java` class. It is possible to create a new class for our banker agents (called `Banker` for example) but for now we'll just keep the boring `DefaultAgent`.
  1. Scroll down to find the `step()` method.

![http://repastcity.googlecode.com/svn/wiki/images/bankers/agent_step.jpg](http://repastcity.googlecode.com/svn/wiki/images/bankers/agent_step.jpg)

The code in the `step()` method might look cryptic, but it is reasonably easy to understand. It simply makes the agents travel from their home to a randomly chosen building and back again for ever.

  * The first `if` statement on line 53 checks to see if the agent has planned a route. If `route==null` then they haven't planned a route, so they choose a building at random and plan a route there.
  * The second `if` statement (line 59) just checks to see if the agent has reached their destination. If they haven't then they continue travelling (`this.route.travel()`).
  * If they're not travelling, then the `if` and `else` statements on lines 63 and 67 either make them either go home (if they're not at home) or choose another random building to travel to (if they are at home).

The task for this stage is to find a bank for the agents to work in and set their `workplace` variable accordingly. The `ContextManager` class is responsible for creating agents and generally configuring the simulation and it is this class that ensures each agent has a home. We will see how this class works later, but in the meantime we will add some code to the agent's default constructor to set their `workplace` variable.

Repast Simphony organises objects into collections called **Contexts**. The model works by reading all the buildings in the GIS data and adding them into their own context called `buildingContext`. So, to find a bank we need to iterate over all buildings in the model until we find a bank, then tell the agent that the building we have just found is where they will work. To do this, find the constructor near the top of the `DefatultAgent` source file and replace the text that is there already:

```
public DefaultAgent() {
        this.id = uniqueID++;
}
```

with the following:

```
public DefaultAgent() {
        this.id = uniqueID++;
        // Find a building that agents can use as their workplace. First, iterate over all buildings in the model
        for (Building b:ContextManager.buildingContext.getRandomObjects(Building.class, 10000)) {
                // See if the building is a bank (they will have type==2).
                if (b.getType()==2) {
                        this.workplace = b;
                        break; // Have found a bank, stop searching.
                }
        }
}
```

This code is relatively easy to understand, except for the command:

```
ContextManager.buildingContext.getRandomObjects(Building.class, 10000)
```

This returns an `Iterable` that can iterate over all the buildings in the `buildingContext`. We need to pass in the argument `Building.class` because it is possible for a context to hold more than one type of object. For example, our building context could hold houses, schools, railway stations etc. The other argument (`10000`) defines the number of objects that we would like to return; there are fewer than 10,000 buildings in the model so we will get all of them. The Simphony [JavaDoc](http://repast.sourceforge.net/docs/api.zip) explains these functions in more detail, but if you hold your mouse over the function in Eclipse you should also be able to see the documentation.

Now, when each agent is generated they will also be assigned a randomly-chosen workplace. Next, we will [create a clock](BankersClock.md) to monitor the time.