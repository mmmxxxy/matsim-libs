/*
 * *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2018 by the members listed in the COPYING,        *
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
 * *********************************************************************** *
 */

package org.matsim.contrib.drt.util;

import org.matsim.contrib.drt.passenger.DrtRequest;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEvent;

/**
 * Utility functions to help calculation of wait/travel time violations.
 *
 * @author Michal Maciejewski (michalm)
 */
public class DrtRequestTimeConstraintViolations {
	/**
	 * @return wait time constraint violation at the moment of request insertion
	 */
	public static double getInitialWaitTimeViolation(DrtRequest request, PassengerRequestScheduledEvent event) {
		return getTimeConstraintViolation(event.getPickupTime(), request.getLatestStartTime());
	}

	/**
	 * @return travel time constraint violation at the moment of request insertion
	 */
	public static double getInitialTravelTimeViolation(DrtRequest request, PassengerRequestScheduledEvent event) {
		return getTimeConstraintViolation(event.getDropoffTime(), request.getLatestArrivalTime());
	}

	private static double getTimeConstraintViolation(double time, double maxTime) {
		return Math.max(0, time - maxTime);
	}
}
