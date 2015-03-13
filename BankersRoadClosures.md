# Austerity Road Closures #

In these times of austerity, the Council have decided that they need to close some roads to save money. They have identified the main road linking our bankers' suburb with the city centre as a good one to close, so in this section we will implement this change. Recall that, in Repast Simphony, relationships between objects can be represented by a `NetworkProjection`. These are commonly used to represent social networks but can also be used to build a road network by defining which roads are linked to each other. A **node** in the network represents an intersection (the junction where two roads meet) and **edges** in the network represent the roads themselves. Each edge has a weight which, in this case, is the length of the road section between two intersections. Therefore when the `Route` class builds a route for an agent it uses the network to find the shortest path.

To implement the road closures, we will add some code to the `ContextManager` class to:

  1. look through all the roads
  1. find the ones that are to be closed
  1. increase their weight value so that it looks like they are incredibly long.


Afterwards the `Route` class will stop using the closed roads in favour of a different route that appears shorter. Alternatively, we could also simply delete the roads from our input GIS data. This is probably a more sensible approach but, for the sake of this tutorial, we will manipulate the road weights because it demonstrates how to work with the road network.

Open the `ContextManager` class (in the `repastcity3.main` package) if it is not open already, find the `build()` method and scroll down to the end (near line number 230). Then insert this code directly after the '`// INSERT ROAD CLOSURE CODE HERE`' (the code will be explained in more detail below):

```
// This array holds the unique identifiers for the roads that are going to be closed (these can be
// found by looking through the GIS data)
List<String> roadsToClose = Arrays.asList(new String[]{"4000000010901474", "4000000010901576",
                "4000000010901602", "4000000010901475", "4000000010901753", "4000000010901834", "4000000010901836",
                "4000000011243306", "4000000011255522", "4000000010901758", "4000000010901835", "4000000010901864",
                "4000000010901750" });

// Iterate over all edges in the road network
for (RepastEdge e:ContextManager.roadNetwork.getEdges()) {
        NetworkEdge edge = (NetworkEdge) e; // Cast to our own edge implementation
        try {
                // See if the edge is one of the ones to be closed
                String roadID = edge.getRoad().getIdentifier();
                if (roadsToClose.contains(roadID)) {
                        System.out.println("Increasing weight of road "+roadID);
                        edge.setWeight(100000);
                }
        } catch (NoIdentifierException e1) {
                // This only happens if the a road in the input data doesn't have a unique value in the 'identifier' column
                LOGGER.log(Level.SEVERE, "Internal error, could not find a road identifier.");
        }
}
```

It is worth explaining the above code in slightly more detail as it says a lot about how Repast Simphony works. The line:

```
for (RepastEdge e:ContextManager.roadNetwork.getEdges()) {
```

iterates over all the edges in the road network and returns objects of type `RepastEdge`. However, when the network is being build for this model, it actually creates edges of the `NetworkEdge` type (in the `repastcity3.environment` package). These edges have a method called `getRoad()` which returns the original road that was used to create the edge. Hence we can cast the edges:

```
NetworkEdge edge = (NetworkEdge) e; // Cast to our own edge implementation
```

and then get the underlying road and its identifier:

```
String roadID = edge.getRoad().getIdentifier();
```

The `try-catch` block is necessary because `Road` objects check that they have unique identifiers. This is mainly tell the developer if there is something wrong with the input GIS data: the road data must have a text column called 'identifier' and the road objects must have a unique string in this field.

Finally, if the road identifier matches one of the ID's of the roads that we want to close, simply increase the weight of the road so that it will never be used when the agents create new routes:

```
if (roadsToClose.contains(roadID)) {
        edge.setWeight(100000);
}
```

Once you have finished adding the new code, the `ContextManager` class should look similar to below:

Try running the model again and seeing what happens. You should see the bankers take a different route to work, now that their original route has been closed.

![http://repastcity.googlecode.com/svn/wiki/images/bankers/road_closure_code.jpg](http://repastcity.googlecode.com/svn/wiki/images/bankers/road_closure_code.jpg)

In the final stage, we will look at the [final results](BankersFinalResults.md) from before and after the road closures.