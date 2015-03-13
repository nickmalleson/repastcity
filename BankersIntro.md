# RepastCity _Criminal Bankers_ Tutorial #

**IMPORTANT (29th Jan): This tutorial is specifically for students at the University of Leeds, and although the code is all open source you wont be able to get hold of the Ordnance Survey MasterMap data. I'm in the process of putting it into a repository so others can use it too and will update the tutorial with proper instructions when I've finished...**

# Introduction #

This tutorial is based on a small Repast Simphony model called _RepastCity_. The virtual city consists of houses, roads and agents.
In the basic model, all agents start with a home, they then choose a random building to travel to, go there, and then travel home again; repeating this process forever.

This tutorial will enhance the model to make the behaviour a bit more interesting by turning the agents into criminal bankers (bankers who, in these tough economic times, need to commit burglary to top up their bonuses). We can then explore some interesting city dynamics.

This tutorial is quite high level, so to really understand what is going on you will need to have done a basic Repast tutorial first. The ones on the [Repast Simphony](http://repast.sourceforge.net/) website are excellent; I would recommend the [Java tutorial](http://repast.sourceforge.net/docs/RepastJavaGettingStarted.pdf) in particular. Or, if you're not familiar with Java, the [ReLogo tutorial](http://repast.sourceforge.net/docs/ReLogoGettingStarted.pdf) is also excellent.

The following changes will be made to the basic model:

  1. Give the agents a 'workplace' - a bank that they can use for their legitimate city jobs.
  1. Implement a clock so that agents know what the time is, not just how many time steps ('iterations') have occurred.
  1. Change the agent's behaviour so that they go to work between 9am - 5pm and spend the rest of their time at home (or travelling).
  1. Further update the agents' behaviour so that they randomly commit burglary if they pass a suitable house while they are travelling.
  1. Remove a few key roads from the road network to see what affect this will have on patterns of burglary.


# The GIS Data #

The data for the tutorial cover part of the city of York. It is real GIS data produced by [Ordnance Survey](http://www.ordnancesurvey.co.uk/oswebsite/products/os-mastermap/index.html) but, for the sake of this tutorial, the data have been cut down by removing a large number of the houses and roads. The image below illustrates the study area; agents live in a suburban area towards the north of the city and travel into the centre for work. There are a small number of major roads which offer the quickest routes into the city but, due to budget cuts, the council are going to have to close these and the bankers will have to find new routes. This might lead to interesting effects on travel patterns and on burglary rates.

![http://repastcity.googlecode.com/svn/wiki/images/bankers/york_data.jpg](http://repastcity.googlecode.com/svn/wiki/images/bankers/york_data.jpg)

# Important: Don't Use Internet Explorer! #

For some reason, when you source copy code from this tutorial using Internet Explorer all the line breaks disappear so you end up with gibberish. This isn't a problem with [Firefox](http://www.mozilla.org/en-US/firefox).

# Model Organisation #

Before starting the tutorial it is helpful to understand how Repast Simphony models are organised. In particular, agents are organised into collections called **Contexts**. A context is basically a bucket that can be used to hold agents. Contexts are arranged hierarchically and can contain sub-contexts. Agents who exist in a sub-context also exist in the parent context, but the reverse is not necessarily true. For example, in a **School** context there might be **Teacher** and **Student** sub-contexts (and even further **Class** sub-contexts).

**Projections** are used to give the agents a space and can define their relationships. For example, 'GIS' projections gives each agent an (x, y) spatial location and 'Network' projections allow relationships between agents to be defined (e.g. a social network). Projections are created for specific contexts and will automatically contain every agent within the context (so if an agent is added to a context it is also added to any projections which have been created on that context).

The figure below illustrates the organisation of the RepastCity model. Each context has an associated GIS projection to store the spatial locations of the objects.

![http://repastcity.googlecode.com/svn/wiki/images/contexts_projections.png](http://repastcity.googlecode.com/svn/wiki/images/contexts_projections.png)

The **JunctionContext** is at the central means of routing agents. A Junction object is defined as the point at which two roads cross (e.g. an intersection). Therefore the **JunctionGeogaraphy** GIS projection is used to hold the locations of all the road intersections, and **RoadNetwork** is a network projection that contains the links between different junctions. Hence agents use the network to work out how to move from one place to another along the road network.

A fuller description of the routing algorithm itself is available on the [crimesim blog](http://crimesim.blogspot.com/2008/05/using-repast-to-move-agents-along-road.html).

That's enough of an introduction, now go to the next page to **[load the tutorial](BankersLoading.md)**.

For reference, here's a table of contents for the tutorial:

  1. BankersIntro - this page.
  1. BankersLoading - how to load the tutorial.
  1. BankersFindWork - assigning the agents places to work.
  1. BankersClock - implementing a clock so that the agents know what the time is.
  1. BankersMakeWork - changing the behaviour so that the agents work between 9am and 5pm.
  1. BankersBurglary - adding to their behaviour so that the agents start to burgle as well.
  1. BankersResults - getting some results from the simulation (the number of burglaries per house in this case).
  1. BankersRoadClosures - closing some roads to see how this will impact the travel behaviour of the bankers.
  1. BankersFinalResults - getting some final results to compare burglary rates before and after the road closures.