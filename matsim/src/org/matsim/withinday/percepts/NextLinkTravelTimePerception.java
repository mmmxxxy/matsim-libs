/* *********************************************************************** *
 * project: org.matsim.*
 * NextLinkTravelTimePerception.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package org.matsim.withinday.percepts;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.matsim.core.api.network.Link;
import org.matsim.core.api.network.Node;
import org.matsim.router.util.TravelTime;
import org.matsim.trafficmonitoring.LinkTravelTimeCounter;


/**
 * @author dgrether
 *
 */
public class NextLinkTravelTimePerception implements TravelTime, AgentPercepts {


	Map<Link, Double> linkMap;

	private int sightDistance;


	public NextLinkTravelTimePerception(final int agentVisibilityRange) {
		this.linkMap = new HashMap<Link, Double>();
		this.sightDistance = agentVisibilityRange;
	}

	/**
	 * @see org.matsim.router.util.TravelTime#getLinkTravelTime(org.matsim.network.LinkImpl, double)
	 */
	public double getLinkTravelTime(final Link link, final double time) {
		Double travelTime = this.linkMap.get(link);
		if (travelTime != null) {
			return travelTime.doubleValue();
		}
		return 0;
	}

	public void updatedPercepts(final Node currentNode) {
		this.linkMap.clear();
		Node current;
		List<Node> nodes = new LinkedList<Node>();
		nodes.add(currentNode);
		int depth = 0;
		while (!nodes.isEmpty()) {
			current = nodes.remove(0);
			for (Link l : current.getOutLinks().values()) {
				this.linkMap.put(l, LinkTravelTimeCounter.getInstance().getLastLinkTravelTime(l.getId().toString()));
				if (depth < this.sightDistance) {
					nodes.add(l.getToNode());
				}
			}
			depth++;
		}
	}

}
