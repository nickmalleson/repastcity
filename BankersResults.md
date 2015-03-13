# Getting Some Results #

Repast Simphony has an inbuilt mechanism that uses **Data Sets** to collect information about the agents and **Outputters** to export the data to files etc. Outputters and Data Sets are created on a particular context (e.g. the **AgentContext**) using the Repast GUI. For more information, there are Simphony tutorials about [data sets](http://repast.sourceforge.net/docs/reference/SIM/Using%20the%20DataSet%20Wizard%20and%20Editor.html) and [outputters](http://repast.sourceforge.net/docs/tutorial/SIM/2-07%20File%20Outputters.html).

For this tutorial, however, we will write our own small function that collects the number of burglaries in each house at the end of the simulation and writes it out to a csv file. To do this, open the `ContextManager` class and create a function called `outputBurglaryData`. It doesn't matter where the code actually goes, but for clarity (and so that your screen will look like my images below) add it directly after the `build()` function and before the start of `createSchedule()` (around line 240). Add the following code:

```
/** This function runs through each building in the model and writes the number of burglaries */
public void outputBurglaryData() throws NoIdentifierException, IOException {
        StringBuilder dataToWrite = new StringBuilder(); // Build a string so all data can be written at once.
        dataToWrite.append("HouseIdentifier, NumBurglaries\n"); // This is the header for the csv file
        // Now iterate over all the houses
        for (Building b : ContextManager.buildingContext.getObjects(Building.class)) {
                if (b.getType() == 1) { // Ignore buildings that aren't houses (type 1)
                        // Write the number of burglaries for this house
                        dataToWrite.append(b.getIdentifier() + ", " + b.getNumBurglaries() + "\n");
                } // if
        } // for
        // Now write this data to a file
        BufferedWriter bw = new BufferedWriter(new FileWriter(new File("results.csv")));
        bw.write(dataToWrite.toString());
        bw.close();
        // And log the data as well so we can see it on the console.
        LOGGER.info(dataToWrite.toString());
}
```

Finally, we need to schedule the function to be called when the simulation finishes. Unlike the `updateRealTime()` method that we scheduler earlier, `outputBurglaryData()` cannot be scheduled with annotations because we don't know, at compile time, when the simulation will finish. So we have to schedule the method manually. To do this, first find the `createSchedule()` method. The first line of the function should be:

```
ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();
```

This gets the current schedule object. We can now tell the scheduler to call our new function when the simulation finishes by adding this just below the `ISchedule` line:

```
// Schedule the outputBurglaryData() function to be called at the end of the simulation
ScheduleParameters params = ScheduleParameters.createAtEnd(ScheduleParameters.LAST_PRIORITY);
schedule.schedule(params, this, "outputBurglaryData");
```

The first line creates a `ScheduleParameters` object to tell the scheduler when the function should be called - at the end of the simulation and, if there are lots of functions called simultaneously, it should be the last one. Then the second line tells the scheduler to use those parameters to schedule a function called `outputBurglaryData`. The `this` argument tells the scheduler which class the method could be found in; if we wanted to schedule a method in another class we could pass a different object here.

After implementing these changes you can run the model again and should see a load of house information printed at the end of the simulation. A file called `results.csv` will also be created in the root project directory. If you can't see this immediately, right click on the 'repastcity\_bankers' project folder (at the top of the right-hand Eclipse window) and click on 'Refresh'. This file can be joined to the original spatial data using a GIS to explore the burglary patterns (there will be an example of this later). In the meantime, the next section will [close some roads](BankersRoadClosures.md) to see what influence this has over our Bankers travel arrangements and the new burglary patterns.