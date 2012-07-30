package repastcity3.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.python.modules.synchronize;

import repast.simphony.space.gis.House;
import repastcity3.agent.DefaultAgent;
import repastcity3.agent.IAgent;
import repastcity3.environment.Building;
import repastcity3.environment.Community;
import repastcity3.exceptions.NoIdentifierException;
import uk.ac.leeds.mass.fmf.fit_statistics.GOF_TEST;

/**
 * Class is used to organise and output the results of the simulation. 
 * 
 * The generateResults() method is scheduled by the <code>ContextManager</code> to be called at the end of the simulation. 
 * 
 * When agents commit a burglary they tell this class.
 *  
 * @author Nick Malleson
 *
 */
public class Results {
	
	Logger LOGGER = Logger.getLogger(Results.class.getName());
	
	private List<BurglaryInfo> burglaries ;
	
	public Results() {
		
		this.burglaries = new ArrayList<BurglaryInfo>();
			
	}
	
	/**
	 * Method scheduled by the <code>ContextManager</code>to run at the end of the simulation and 
	 * generate some results and other info.
	 * 
	 * @throws NoIdentifierException
	 * @throws IOException 
	 */
	public void generateResults() throws NoIdentifierException, IOException {
		if (ContextManager.error) {
			LOGGER.info("An error occurred so Results is not printing results.");
			return;
		}
		
		// Results will be put into a directory which has this model's name. It will have already been created
		// by the context creator.
		String modelDir = ContextManager.modelName+"/";
		File dir = new File(modelDir); 
		if (! (dir.exists() && dir.isDirectory()) ) {
			throw new IOException("The directory to store results in ("+dir.getAbsolutePath()+") does not exist.");
		}
		LOGGER.fine("Results will go into the directory "+modelDir);
		
		/* Print information about individual houses */
		File f = new File(modelDir+ContextManager.getProperty(GlobalVars.HouseData));
		BufferedWriter bw = new BufferedWriter(new FileWriter(f));
		LOGGER.info(" ***** GENERATING RESULTS ***** ");
		LOGGER.info("\tInformation about houses to file "+f.getAbsolutePath());
		
		StringBuilder info = new StringBuilder();
		info.append("BuildingID,TimesPassed,NumBurgD,Lon,Lat,XCoord,YCoord\n");
		for (Building b : ContextManager.buildingContext.getObjects(Building.class)) {
			info.append(
					b.getIdentifier()).append(",").
					append(b.getTimesPassed()).append(",").
					append(b.getNumBurglaries()).append(",").
					append(ContextManager.buildingProjection.getGeometry(b).getCentroid().getX()).append(",").
					append(ContextManager.buildingProjection.getGeometry(b).getCentroid().getY()).append(",").
					append(b.getCoords().x).append(",").
					append(b.getCoords().y).
					append("\n"); 
		}
		bw.write(info.toString());
		bw.close();
		
		/* Print information about communities */
		f = new File(modelDir+ContextManager.getProperty(GlobalVars.CommunityData));
		bw = new BufferedWriter(new FileWriter(f));
		LOGGER.info("\tInformation about communities to file "+f.getAbsolutePath());
		
		info = new StringBuilder();
		info.append("ComID,TimesPassed,NumBurgD,Attract,AvgDist,NumBuilds,CE,Lon,Lat,XCoord,YCoord\n");
		int burgd = 0, timesPassed = 0; // Count the number of burglaries and times passed in each community
		for (Community c : ContextManager.communityContext.getObjects(Community.class)) {
			burgd = 0;
			timesPassed = 0;
			for (Building b:c.getBuildings()) {
				burgd += b.getNumBurglaries();
				timesPassed += b.getTimesPassed();
			}
			info.append(
					c.getIdentifier()).append(",").
					append(timesPassed).append(",").
					append(burgd).append(",").
					append(c.getAttractiveness()).append(",").
					append(c.getAverageDistance()).append(",").
					append(c.getBuildings().size()).append(",").
					append(c.getCollectiveEfficacy()).append(",").
					append(ContextManager.communityProjection.getGeometry(c).getCentroid().getX()).append(",").
					append(ContextManager.communityProjection.getGeometry(c).getCentroid().getY()).append(",").
					append(c.getCoords().x).append(",").
					append(c.getCoords().y).
					append("\n"); 
		}
		bw.write(info.toString());
		bw.close();
	
		
		/* Print individual burglary points */
		
		f = new File(modelDir+ContextManager.getProperty(GlobalVars.BurglaryPoints));
		bw = new BufferedWriter(new FileWriter(f));
		LOGGER.info("\tInformation about burglaries to file "+f.getAbsolutePath());
		
		info = new StringBuilder();
		info.append("BurgdID,Burglar,Ticks,RealTime,House,Community,Lon,Lat,XCoord,YCoord\n");
		for (BurglaryInfo bi : this.burglaries) {
			info.
					append(bi.id).append(",").
					append(bi.agent.getID()).append(",").
					append(bi.ticks).append(",").
					append(bi.time).append(",").
					append(bi.building.getIdentifier()).append(",").
					append(bi.building.getCommunity().getIdentifier()).append(",").
					append(ContextManager.buildingProjection.getGeometry(bi.building).getCentroid().getX()).append(",").
					append(ContextManager.buildingProjection.getGeometry(bi.building).getCentroid().getY()).append(",").
					append(bi.building.getCoords().x).append(",").
					append(bi.building.getCoords().y).
					append("\n"); 
		}
		bw.write(info.toString());
		bw.close();
		
		
		/* Print info about agent's homes (the points where they live)*/
		f = new File(modelDir+ContextManager.getProperty(GlobalVars.AgentHomes));
		bw = new BufferedWriter(new FileWriter(f));
		LOGGER.info("\tInformation about burglar's homes to file "+f.getAbsolutePath());
		
		info = new StringBuilder();
		info.append("Burglar,Building,Community,Lon,Lat,XCoord,YCoord\n");
		for (IAgent a:ContextManager.agentContext.getObjects(DefaultAgent.class)) {
			info.
				append(a.getID()).append(",").
				append(a.getHome().getIdentifier()).append(",").
				append(a.getHome().getCommunity().getIdentifier()).append(",").
				append(ContextManager.buildingProjection.getGeometry(a.getHome()).getCentroid().getX()).append(",").
				append(ContextManager.buildingProjection.getGeometry(a.getHome()).getCentroid().getY()).append(",").
				append(a.getHome().getCoords().x).append(",").
				append(a.getHome().getCoords().y).
				append("\n"); 
		}
		bw.write(info.toString());
		bw.close();
		
		LOGGER.info(" ***** FINISHED RESULTS ***** ");
		
	}
	
	
	/**
	 * 
	 * @param test
	 * @param expectedData
	 * @return
	 */
	public double calculateError(GOF_TEST test, Map<String,Double> expectedData) {
		
		return -1;
	}
	
	
	/**
	 * Tell this <code>Results</code> object to remember a burglary. This should be called each time a burglary
	 * happens. The method is synchronised to be thread safe.x
	 * @param agent The agent who committed the burglary
	 * @param ticks The tick count when it occurred
	 * @param time The real time (e.g. 2:30pm = 14.5)
	 * @param building The building that the burglary occurred in
	 */
	public synchronized void saveBurglaryInfo(IAgent agent, int ticks, double time, Building building) {
		this.burglaries.add(new BurglaryInfo(agent, ticks, time, building));
	}


}

/** Simple class to store information about a single burglary for writing results */
class BurglaryInfo {
	
	 IAgent agent; // The agent who committed it
	 int ticks; // The number of ticks when it occurred
	 double time; // The real-time it occurred
	 Building building; // The building it occurred in
	 
	 // Give a unique id to each burglary
	 private static int uniqueID = 0;
	 int id;
	 
	public BurglaryInfo(IAgent agent, int ticks, double time, Building building) {
		super();
		this.agent = agent;
		this.ticks = ticks;
		this.time = time;
		this.building = building;
		
		this.id = BurglaryInfo.uniqueID++;
	}
	
	
	
}
