# Loading the Tutorial and the Repast GUI #

The tutorial is part of a larger project called [RepastCity](http://code.google.com/p/repastcity/) which is basically a re-design of my PhD burglary model. If you have an existing Repast Simphony installation (which is basically a plug-in for the Eclipse IDE) you can download the bankers tutorial from [here](http://code.google.com/p/repastcity/downloads/detail?name=repastcity_bankers.zip&can=2&q=), unzip it and then import it with Eclipse using ''File -> Import -> General -> Existing Projects into Workspace''. However, for this tutorial I have created a pre-packed version of Eclipse with Repast Simphony and the bankers tutorial already loaded. It is available here:

```
N:\Earth&Environment\Geography\TransferArea\NickMalleson\RepastSimphony-2.0-beta.zip
```

To start the tutorial:

  1. Copy the `RepastSimphony-2.0-beta.zip` directory (above) to somewhere on your PC (the Desktop is fine). It's quite big (~400Mb) so this might take a few mins. I wouldn't copy it to your `M:` drive because it will probably use all of your quota.
  1. Extract the zip file, which should create a single new directory called `RepastSimphony-2.0-beta/`
  1. Go into the `RepastSimphony-2.0-beta/eclipse` directory and run the `eclipse.exe` file. This will start Eclipse.
  1. You will be prompted to choose a workspace. Choose the `RepastSimphony-2.0-beta/workspace` (i.e. a subdirectory of the folder you just downloaded). Make sure that you choose the correct directory as occasionally Eclipse creates a new directory called `workspace` in the `RepastSimphony-2.0-beta/eclipse` directory (i.e. `RepastSimphony-2.0-beta/eclipse/workspace`). Don't use this one, you want to choose `RepastSimphony-2.0-beta/workspace`.
  1. As Eclipse loads you might get an error about `Java Tooling`. You can ignore this, I'm not sure what causes it.
  1. Once Eclipse has finished loading you should see the something like the image below. The left window shows all the projects in your workspace (at the moment there is only `repastcity_bankers`) and the right window will be where the Java code can be editted.
  1. To make sure the project is displayed properly, you need to change to the 'Java perspective' by clicking on the top-right button (circled on the image below). This changes how Eclipse windows and buttons behave; by default you start in 'ReLogo' mode which is for models written in Repast's version of the Logo language (which, by the way, is very good, it's worth doing the [ReLogo Tutorial](http://repast.sourceforge.net/docs/ReLogoGettingStarted.pdf) if you have a spare hour).
  1. Finally we can run the model by clicking on the down arrow next to the green button (circled on the image below) and choosing 'Run Bankers Model'


![http://repastcity.googlecode.com/svn/wiki/images/bankers/eclipse_start.jpg](http://repastcity.googlecode.com/svn/wiki/images/bankers/eclipse_start.jpg)

You will now be presented with the Repast Simphony GUI. This has all the controls for the simulation, such as starting or pausing it, changing parameters and adding displays. To initialise the simulation (read the GIS data and create the agents/environment) press the 'initialise' button and to start it press 'play' (These are indicated below). You should see the agents move around randomly from house to house. In the next stage we will start to improve their behaviour by giving the agents [somewhere to work](BankersFindWork.md).

![http://repastcity.googlecode.com/svn/wiki/images/bankers/repast_gui.jpg](http://repastcity.googlecode.com/svn/wiki/images/bankers/repast_gui.jpg)