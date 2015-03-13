# RepastCity - A demo virtual city (version 3) #

## Introduction ##

RepastCity is a small [Repast Simphony](http://repast.sourceforge.net/) program which demonstrates how to create a virtual city and move some agents around a road network. Although the code to move around a road network is quite complicated, most of the details can be ignored and developers can concentrate on implementing agent behaviours. This document (as well as the [ExtendingRepastCity3](ExtendingRepastCity3.md) and [RC3ModelStructure](RC3ModelStructure.md) documents) briefly describe how the program works and explains how it can be adapted for different applications (my own research interest is [crime simulation](http://crimesim.blogspot.com/)).

People who are new to Repast Simphony definitely need to work through some tutorials first. The ones on the [Repast Simphony](http://repast.sourceforge.net/) website are excellent (in particular I would recommend the [Java tutorial](http://repast.sourceforge.net/docs/RepastJavaGettingStarted.pdf)). Repast Simphony has quite a steep learning curve but is well worth learning if you need to build agent-based models, it's an excellent tool.

## Referencing and Editing ##

The code is released under the 'GNU GPL v3'_licence so you're welcome to use the code however you would like to but please reference me ([Nick Malleson](http://www.geog.leeds.ac.uk/people/n.malleson)) or this page somewhere. If you improve or extend the code please let me know and I'll update it on this site._

## Getting the Program and Running It ##

The program is available as a single compressed [file](http://repastcity.googlecode.com/files/repastcity3.zip) which contains the entire Eclipse project. Download the file and extract it somewhere (the location is not important).

To run the program you need to download [Repast Simphony version 2.0](http://repast.sourceforge.net/download.html) (it comes bundled with the Eclipse development environment) and then follow these instructions:

  1. Start Repast (Eclipse)
  1. Go to File -> Import
  1. Under 'General' select 'Existing Projects Into Workspace'.
  1. Go to 'Select root directory' click on browse and select the extracted project.
  1. Click on 'Finish'. If you left the 'copy projects into workspace' box ticked you can now delete the zip file and the extracted directory because all the code will be copied into your Eclipse workspace.
  1. Finally click on the 'Run' button (green) at the top of Eclipse and select 'Run RepastCity3 Model'. (If there is not option to run the model try closing the project and then re-opening it).


There is a picture below which shows what the program should look like.

![http://repastcity.googlecode.com/svn/wiki/images/gui.jpg](http://repastcity.googlecode.com/svn/wiki/images/gui.jpg)

## Next? ##

To adapt the model for other uses, you can refer to [RC3ModelStructure](RC3ModelStructure.md) for information about the structure of the model or [ExtendingRepastCity3](ExtendingRepastCity3.md) for brief instructions about how to extend it.

On my part, forthcoming work will be to incorporate a behavioural framework to make it easier create intelligent agent behaviour.