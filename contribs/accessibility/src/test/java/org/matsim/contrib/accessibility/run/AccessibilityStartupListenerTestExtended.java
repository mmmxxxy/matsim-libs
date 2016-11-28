/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package org.matsim.contrib.accessibility.run;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.accessibility.AccessibilityCalculator;
import org.matsim.contrib.accessibility.AccessibilityContributionCalculator;
import org.matsim.contrib.accessibility.GridBasedAccessibilityShutdownListenerV3;
import org.matsim.contrib.accessibility.gis.GridUtils;
import org.matsim.contrib.accessibility.run.AccessibilityIntegrationTest.EvaluateTestResults;
import org.matsim.contrib.accessibility.utils.AccessibilityUtils;
import org.matsim.contrib.accessibility.utils.GeoserverUpdater;
import org.matsim.contrib.matrixbasedptrouter.PtMatrix;
import org.matsim.core.controler.ControlerListenerManager;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacilitiesImpl;

import com.google.inject.Inject;
import com.vividsolutions.jts.geom.Envelope;

/**
 * @author mzilske, dziemke, knagel
 */
public final class AccessibilityStartupListenerTestExtended implements StartupListener {
	@Inject Scenario scenario;
	@Inject(optional = true) PtMatrix ptMatrix = null; // Downstream code knows how to handle a null PtMatrix
	@Inject ControlerListenerManager controlerListenerManager;
	@Inject Map<String, AccessibilityContributionCalculator> calculators;

	final List<String> activityTypes;
	final ActivityFacilities densityFacilities;
	private final String crs;
	private final String runId;
	Envelope envelope;
	Double cellSize;
	boolean push2Geoserver;
	
	private static final Logger log = Logger.getLogger(AccessibilityStartupListenerTestExtended.class);

	
//	@Inject EvaluateTestResults etr;
	

	public AccessibilityStartupListenerTestExtended(List<String> activityTypes, ActivityFacilities densityFacilities, String crs, String runId, Envelope envelope, Double cellSize, boolean push2Geoserver) {
		this.activityTypes = activityTypes;
		this.densityFacilities = densityFacilities;
		this.crs = crs;
		this.runId = runId;
		this.envelope = envelope;
		this.cellSize = cellSize;
		this.push2Geoserver = push2Geoserver;
	}

	
	@Override
	public void notifyStartup(StartupEvent event) {
		// yyyyyy do we still need this as a startup listener when we are solving many of the dependencies through guice now?  kai, nov'16
		
		log.error("----------------------------- Statup Listener ------------------------");
		for (final String activityType : activityTypes) {
			
//			Config config = scenario.getConfig();
			if (cellSize <= 0) {
				throw new IllegalArgumentException("Cell Size needs to be assigned a value greater than zero.");
			}
			ActivityFacilitiesImpl measuringPoints = GridUtils.createGridLayerByGridSizeByBoundingBoxV2(envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY(), cellSize);
			AccessibilityCalculator accessibilityCalculator = new AccessibilityCalculator(scenario, measuringPoints);
			for (Entry<String, AccessibilityContributionCalculator> entry : calculators.entrySet()) {
				accessibilityCalculator.putAccessibilityContributionCalculator(entry.getKey(), entry.getValue());
			}
			ActivityFacilities activityFacilities = AccessibilityUtils.collectActivityFacilitiesWithOptionOfType(scenario, activityType);
			GridBasedAccessibilityShutdownListenerV3 listener = new GridBasedAccessibilityShutdownListenerV3(accessibilityCalculator, activityFacilities, 
					ptMatrix, scenario, envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY(), cellSize);
			listener.addAdditionalFacilityData(densityFacilities);
			listener.writeToSubdirectoryWithName(activityType);
			if (push2Geoserver == true) {
				accessibilityCalculator.addFacilityDataExchangeListener(new GeoserverUpdater(crs, runId + "_" + activityType));
			}
			
			// for tests
			EvaluateTestResults etr = new EvaluateTestResults(calculators.keySet());
			listener.addSpatialGridDataExchangeListener(etr);
			System.out.println("################################################# EvaluateTestResults hinzugefügt ######################################");
			//

			controlerListenerManager.addControlerListener(listener);
		}
		
		
	}
}