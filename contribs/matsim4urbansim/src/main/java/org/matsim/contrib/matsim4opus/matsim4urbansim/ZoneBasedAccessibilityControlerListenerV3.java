package org.matsim.contrib.matsim4opus.matsim4urbansim;

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.matsim4opus.gis.Zone;
import org.matsim.contrib.matsim4opus.gis.ZoneLayer;
import org.matsim.contrib.matsim4opus.improvedpseudopt.PtMatrix;
import org.matsim.contrib.matsim4opus.interfaces.MATSim4UrbanSimInterface;
import org.matsim.contrib.matsim4opus.matsim4urbansim.costcalculators.TravelDistanceCalculator;
import org.matsim.contrib.matsim4opus.utils.LeastCostPathTreeExtended;
import org.matsim.contrib.matsim4opus.utils.helperObjects.Benchmark;
import org.matsim.contrib.matsim4opus.utils.io.writer.UrbanSimZoneCSVWriterV2;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.facilities.ActivityFacilitiesImpl;
import org.matsim.core.network.NetworkImpl;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.roadpricing.RoadPricingSchemeImpl;
import org.matsim.utils.LeastCostPathTree;

/**
 *  improvements feb'12
 *  - distance between zone centroid and nearest node on road network is considered in the accessibility computation
 *  as walk time of the euclidian distance between both (centroid and nearest node). This walk time is added as an offset 
 *  to each measured travel times
 *  - using walk travel times instead of travel distances. This is because of the betas that are utils/time unit. The walk time
 *  corresponds to distances since this is also linear.
 * 
 * This works for UrbanSim Zone and Parcel Applications !!! (march'12)
 * 
 *  improvements april'12
 *  - accessibility calculation uses configurable betas (coming from UrbanSim) for car/walk travel times, -distances and -costs
 *  
 * improvements / changes july'12 
 * - fixed error: used pre-factor (1/beta scale) in deterrence function instead of beta scale (fixed now!)
 * 
 * todo (sep'12):
 * - set external costs to opportunities within the same zone ...
 * 
 * improvements jan'13
 * - added pt for accessibility calculation
 * 
 * improvements april'13
 * - congested car modes uses TravelDisutility from MATSim
 * - taking disutilites directly from MATSim (controler.createTravelCostCalculator()), this 
 * also activates road pricing ...
 * 
 * improvements june'13
 * - removed zones as argument to ZoneBasedAccessibilityControlerListenerV3
 * - providing opportunity facilities (e.g. workplaces)
 * 
 * @author thomas
 *
 */
public class ZoneBasedAccessibilityControlerListenerV3 extends AccessibilityControlerListenerImpl implements ShutdownListener{
	
	private static final Logger log = Logger.getLogger(ZoneBasedAccessibilityControlerListenerV3.class);
	

	// ////////////////////////////////////////////////////////////////////
	// constructors
	// ////////////////////////////////////////////////////////////////////
	
	public ZoneBasedAccessibilityControlerListenerV3(ZoneLayer<Id>  startZones,
												   ActivityFacilitiesImpl opportunities,
												   PtMatrix ptMatrix,
												   Benchmark benchmark,
												   Scenario scenario){
		
		log.info("Initializing ZoneBasedAccessibilityControlerListenerV3 ...");
		
		assert(startZones != null);
		this.measuringPointsZone = startZones;
		this.ptMatrix = ptMatrix; // this could be zero of no input files for pseudo pt are given ...
		assert(benchmark != null);
		this.benchmark = benchmark;
		assert(scenario != null);

		// writing accessibility measures continuously into "zone.csv"-file. Naming of this 
		// files is given by the UrbanSim convention importing a csv file into a identically named 
		// data set table. THIS PRODUCES URBANSIM INPUT
		UrbanSimZoneCSVWriterV2.initUrbanSimZoneWriter();
		initAccessibilityParameter(scenario);
		// aggregating facilities to their nearest node on the road network
		this.aggregatedFacilities = aggregatedOpportunities(opportunities, (NetworkImpl)scenario.getNetwork());
		
		log.info(".. done initializing ZoneBasedAccessibilityControlerListenerV3");
	}
	
	@Override
	public void notifyShutdown(ShutdownEvent event) {
		log.info("Entering notifyShutdown ..." );
		
		// get the controller and scenario
		Controler controler = event.getControler();
		NetworkImpl network = (NetworkImpl) controler.getNetwork();

		int benchmarkID = this.benchmark.addMeasure("zone-based accessibility computation");

		
		// get the free-speed car travel times (in seconds)
		TravelTime ttf = new FreeSpeedTravelTime() ;
		TravelDisutility tdFree = controler.getTravelDisutilityFactory().createTravelDisutility(ttf, controler.getConfig().planCalcScore() ) ;
		LeastCostPathTreeExtended lcptExtFreeSpeedCarTrvelTime = new LeastCostPathTreeExtended( ttf, tdFree, controler.getScenario().getScenarioElement(RoadPricingSchemeImpl.class) ) ;

		// get the congested car travel time (in seconds)
		TravelTime ttc = controler.getLinkTravelTimes(); // congested
		TravelDisutility tdCongested = controler.getTravelDisutilityFactory().createTravelDisutility(ttc, controler.getConfig().planCalcScore() ) ;
		LeastCostPathTreeExtended  lcptExtCongestedCarTravelTime = new LeastCostPathTreeExtended(ttc, tdCongested, controler.getScenario().getScenarioElement(RoadPricingSchemeImpl.class) ) ;

		// get travel distance (in meter)
		LeastCostPathTree lcptTravelDistance		 = new LeastCostPathTree( ttf, new TravelDistanceCalculator());
		
		this.scheme = controler.getScenario().getScenarioElement(RoadPricingSchemeImpl.class);

		try{
			log.info("Computing and writing zone based accessibility measures ..." );
			// printParameterSettings(); // use only for debugging (settings are printed as part of config dump)
			
			Iterator<Zone<Id>> measuringPointIterator = measuringPointsZone.getZones().iterator();
			log.info(measuringPointsZone.getZones().size() + "  measurement points are now processing ...");
			
			accessibilityComputation(ttc, 
					lcptExtFreeSpeedCarTrvelTime,
					lcptExtCongestedCarTravelTime, 
					lcptTravelDistance, 
					ptMatrix, 
					network,
					measuringPointIterator, 
					measuringPointsZone.getZones().size(),
					ZONE_BASED,
					controler);
			
			System.out.println();
			// finalizing/closing csv file containing accessibility measures
			UrbanSimZoneCSVWriterV2.close();
			
			if (this.benchmark != null && benchmarkID > 0) {
				this.benchmark.stoppMeasurement(benchmarkID);
				log.info("Accessibility computation with " 
						+ measuringPointsZone.getZones().size()
						+ " zones (origins) and "
						+ this.aggregatedFacilities.length
						+ " destinations (opportunities) took "
						+ this.benchmark.getDurationInSeconds(benchmarkID)
						+ " seconds ("
						+ this.benchmark.getDurationInSeconds(benchmarkID)
						/ 60. + " minutes).");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void writeCSVData(
			Zone<Id> measurePoint, Coord coordFromZone,
			Node fromNode, double freeSpeedAccessibility,
			double carAccessibility, double bikeAccessibility,
			double walkAccessibility, double ptAccessibility) {
		// writing accessibility measures of current node in csv format (UrbanSim input)
		UrbanSimZoneCSVWriterV2.write(measurePoint,
									  freeSpeedAccessibility,
									  carAccessibility,
									  bikeAccessibility,
									  walkAccessibility, 
									  ptAccessibility);
	}
}
