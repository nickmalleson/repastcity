# Implementing a clock #

At the moment, it is possible to find out how many iterations the model has been running for by using a special Repast method:

```
RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
```

However, we want a way for our agents to know what the real time is, in hours and minutes. To do this we can add some code to the `ContextManager` class which will maintain a `realTime` variable. This variable will be `public` so that other classes can access it and we will also create a method that updates the value of the timer at each iteration. To do this, open the `ContextManager` source file which is part of the `repastcity3.main` package. The following code can be added anywhere inside the class, but lets put it at the bottom of the source file (after the `getAgentGeography()` method but before the very last closing brace):

```
/* Variables to represent the real time in decimal hours (e.g. 14.5 means 2:30pm) and a method, called at every
 * iteration, to update the variable. */
public static double realTime = 8.0; // (start at 8am)
public static int numberOfDays = 0; // It is also useful to count the number of days.

@ScheduledMethod(start=1, interval=1, priority=10)
public void updateRealTime() {
        realTime += (1.0/60.0); // Increase the time by one minute (a 60th of an hour)
        if (realTime >= 24.0) { // If it's the end of a day then reset the time
                realTime = 0.0;
                numberOfDays++; // Also increment our day counter
                LOGGER.log(Level.INFO, "Simulating day "+numberOfDays);
        }
}
```

The `updateRealTime()` method itself just uses some simple maths to work out what the time is (in decimal hours) assuming that each model tick is a minute of real time. However, the line:

```
@ScheduledMethod(start=1, interval=1, priority=10)
```

probably needs some explanation. This is a way of scheduling a function in Repast using **Java annotations**. It tells the scheduler that we want the method to be called first at iteration 1 (`start=1`), and then at every subsequent iteration (`interval=1`). The `priority` argument tell Repast how important the function is. If there are lots of methods that can be called at the same iteration, Repast chooses the ones with the highest priority first. If lots of methods have the same priority then Repast chooses their order randomly.

It is possible to have a more fine grained control over when methods are scheduled and in what order they should be called (more on this later), but annotations are nice because they are simple and they appear close to the code that is actually being scheduled. See the [scheduling documentation](http://repast.sourceforge.net/docs/reference/SIM/Working%20with%20the%20scheduler.html) for more information.

After these changes, the end of your `ContextManager` class should look something like the image below.

![http://repastcity.googlecode.com/svn/wiki/images/bankers/timer_code.jpg](http://repastcity.googlecode.com/svn/wiki/images/bankers/timer_code.jpg)

Now that the agents know that the time is, the next stage will make them [do some work](BankersMakeWork.md).