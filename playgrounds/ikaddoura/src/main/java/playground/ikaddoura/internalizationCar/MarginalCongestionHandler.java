/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
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

/**
 * 
 */
package playground.ikaddoura.internalizationCar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.api.experimental.events.AgentArrivalEvent;
import org.matsim.core.api.experimental.events.AgentDepartureEvent;
import org.matsim.core.api.experimental.events.AgentStuckEvent;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.experimental.events.LinkEnterEvent;
import org.matsim.core.api.experimental.events.LinkLeaveEvent;
import org.matsim.core.api.experimental.events.TransitDriverStartsEvent;
import org.matsim.core.api.experimental.events.handler.AgentArrivalEventHandler;
import org.matsim.core.api.experimental.events.handler.AgentDepartureEventHandler;
import org.matsim.core.api.experimental.events.handler.AgentStuckEventHandler;
import org.matsim.core.api.experimental.events.handler.LinkEnterEventHandler;
import org.matsim.core.api.experimental.events.handler.LinkLeaveEventHandler;
import org.matsim.core.events.handler.TransitDriverStartsEventHandler;
import org.matsim.core.network.NetworkImpl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioImpl;

/**
 * TODO: Adjust for other modes than car and mixed modes. (Adjust for different effective cell sizes than 7.5 meters.)
 * 
 * @author ikaddoura
 *
 */
public class MarginalCongestionHandler implements LinkEnterEventHandler, LinkLeaveEventHandler, TransitDriverStartsEventHandler, AgentDepartureEventHandler, AgentArrivalEventHandler, AgentStuckEventHandler {
	private final static Logger log = Logger.getLogger(MarginalCongestionHandler.class);
	
	private final boolean allowForStorageCapacityConstraint = true; // Runtime Exception if storage capacity active
	private final boolean calculateStorageCapacityConstraints = true;
	private double delayNotInternalized = 0.;

	private final ScenarioImpl scenario;
	private final EventsManager events;
	private final List<Id> ptVehicleIDs = new ArrayList<Id>();
	private final List<Id> ptDriverIDs = new ArrayList<Id>();
	private final Map<Id, LinkCongestionInfo> linkId2congestionInfo = new HashMap<Id, LinkCongestionInfo>();
	
	public MarginalCongestionHandler(EventsManager events, ScenarioImpl scenario) {
		this.events = events;
		this.scenario = scenario;
				
		if (this.scenario.getNetwork().getCapacityPeriod() != 3600.) {
			throw new RuntimeException("Expecting a capacity period of 1h. Aborting...");
		}
		
		if (this.scenario.getConfig().getQSimConfigGroup().getFlowCapFactor() != 1.0) {
			log.warn("Flow capacity factor unequal 1.0 not tested.");
		}
		
		if (this.scenario.getConfig().getQSimConfigGroup().getStorageCapFactor() != 1.0) {
			log.warn("Storage capacity factor unequal 1.0 not tested.");
		}
		
		if (this.scenario.getConfig().getQSimConfigGroup().isInsertingWaitingVehiclesBeforeDrivingVehicles() != true) {
			throw new RuntimeException("Expecting the qSim to insert waiting vehicles before driving vehicles. Aborting...");
		}
	}

	@Override
	public void reset(int iteration) {
		this.linkId2congestionInfo.clear();
		this.ptDriverIDs.clear();
		this.ptVehicleIDs.clear();
	}

	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
				
		if (!this.ptVehicleIDs.contains(event.getVehicleId())){
			this.ptVehicleIDs.add(event.getVehicleId());
		}
		
		if (!this.ptDriverIDs.contains(event.getDriverId())){
			this.ptDriverIDs.add(event.getDriverId());
		}
	}
	
	@Override
	public void handleEvent(AgentStuckEvent event) {
//		log.warn("Agent stuck event. No garantee for right calculation of marginal congestion effects: " + event.toString());
		throw new RuntimeException("Agent stuck event. No garantee for right calculation of marginal congestion effects: " + event.toString());
	}

	@Override
	public void handleEvent(AgentDepartureEvent event) {
		if (event.getLegMode().toString().equals(TransportMode.car.toString())){
			// car!
			
			if (this.linkId2congestionInfo.get(event.getLinkId()) == null){
				// no one entered or left this link before
				collectLinkInfos(event.getLinkId());
			}
						
			LinkCongestionInfo linkInfo = this.linkId2congestionInfo.get(event.getLinkId());
			linkInfo.getAgentsOnLink().add(event.getPersonId());
			updateLinkInfo_agentEntersLink(event.getTime(), event.getPersonId(), event.getLinkId());
			linkInfo.getPersonId2freeSpeedLeaveTime().put(event.getPersonId(), event.getTime() + 1);

		} else {			
			log.warn("Not tested for other modes than car.");
		}
	}

	@Override
	public void handleEvent(LinkEnterEvent event) {
		if (this.ptVehicleIDs.contains(event.getVehicleId())){
			log.warn("Not tested for pt.");
		
		} else {
			// car!
			
			if (this.linkId2congestionInfo.get(event.getLinkId()) == null){
				// no one entered or left this link before
				collectLinkInfos(event.getLinkId());
			}
						
			LinkCongestionInfo linkInfo = this.linkId2congestionInfo.get(event.getLinkId());
			linkInfo.getAgentsOnLink().add(event.getPersonId());
			updateLinkInfo_agentEntersLink(event.getTime(), event.getPersonId(), event.getLinkId());
			linkInfo.getPersonId2freeSpeedLeaveTime().put(event.getPersonId(), event.getTime() + linkInfo.getFreeTravelTime() + 1.0);
		}	
	}
	
	@Override
	public void handleEvent(AgentArrivalEvent event) {
		if (event.getLegMode().toString().equals(TransportMode.car.toString())){
			// car!
			LinkCongestionInfo linkInfo = this.linkId2congestionInfo.get(event.getLinkId());
			
			linkInfo.getAgentsOnLink().remove(event.getPersonId());
			updateLinkInfo_agentLeavesLink(event.getTime(), event.getPersonId(), event.getLinkId());

		} else {			
			log.warn("Not tested for other modes than car.");
		}
	}


	@Override
	public void handleEvent(LinkLeaveEvent event) {
		
		if (this.ptVehicleIDs.contains(event.getVehicleId())){
			log.warn("Not tested for pt.");
		
		} else {
			// car!
			if (this.linkId2congestionInfo.get(event.getLinkId()) == null){
				// no one left this link before
				collectLinkInfos(event.getLinkId());
			}
			
			LinkCongestionInfo linkInfo = this.linkId2congestionInfo.get(event.getLinkId());
			
			linkInfo.getAgentsOnLink().remove(event.getPersonId());
			updateLinkInfo_agentLeavesLink(event.getTime(), event.getPersonId(), event.getLinkId());
			
//			checkQSimBehavior(event);
			clearTrackingMarginalDelays1(event);
			calculateCongestion(event);
			clearTrackingMarginalDelays2(event);
			trackMarginalDelay(event);
			
			linkInfo.getPersonId2freeSpeedLeaveTime().remove(event.getPersonId());
		}
	}
	
	// --------------------------------------------------------------------------------------------------------------------------------------------
	// --------------------------------------------------------------------------------------------------------------------------------------------
	
//	private void checkQSimBehavior(LinkLeaveEvent event) {
//		LinkCongestionInfo linkInfo = this.linkId2congestionInfo.get(event.getLinkId());
//		
//		double lastLeavingFromThatLink = getLastLeavingTime(linkInfo.getPersonId2linkLeaveTime());
//		double earliestLeaveTime = lastLeavingFromThatLink + linkInfo.getMarginalDelayPerLeavingVehicle_sec();
//			
//		if (event.getTime() >= earliestLeaveTime){
//			// expected			
//			
//		} else {
//			throw new RuntimeException("Agent leaves link earlier than flow capacity would allow. Aborting...");
//		}
//	}

	private void clearTrackingMarginalDelays2(LinkLeaveEvent event) {
		LinkCongestionInfo linkInfo = this.linkId2congestionInfo.get(event.getLinkId());
		
		if (linkInfo.getLeavingAgents().size() == 0) {
			// no agent is being tracked for that link
			
		} else {
			// clear trackings of persons leaving that link previously
			double lastLeavingFromThatLink = getLastLeavingTime(linkInfo.getPersonId2linkLeaveTime());
			double earliestLeaveTime = lastLeavingFromThatLink + linkInfo.getMarginalDelayPerLeavingVehicle_sec();

			if (event.getTime() > earliestLeaveTime + 1.0){
//				System.out.println("Flow congestion has disappeared on link " + event.getLinkId() + ". Delete agents leaving previously that link: " + linkInfo.getLeavingAgents().toString());
				linkInfo.getLeavingAgents().clear();
				linkInfo.getPersonId2linkLeaveTime().clear();
				
			}
		}
		
	}

	private void calculateCongestion(LinkLeaveEvent event) {
		LinkCongestionInfo linkInfo = this.linkId2congestionInfo.get(event.getLinkId());
		
		double totalDelay = event.getTime() - linkInfo.getPersonId2freeSpeedLeaveTime().get(event.getPersonId());
		
//		System.out.println(event.toString());
//		System.out.println("free travel time: " + linkInfo.getFreeTravelTime() + " // marginal flow delay: " + linkInfo.getMarginalDelayPerLeavingVehicle_sec());
//		System.out.println("relevant agents (previously leaving the link): " + linkInfo.getLeavingAgents());
//		System.out.println("total delay: " + totalDelay);
//		
		if (totalDelay <= 0.) {
			// person was leaving that link without delay
		
		} else {
			
			double remainingDelay = calculateFlowCongestion(totalDelay, event);
			
			if (remainingDelay == 0) {
				// no storage delay
			
			} else if (remainingDelay > 0) {
				
				if (this.allowForStorageCapacityConstraint) {
					if (this.calculateStorageCapacityConstraints) {
						// look who has to pay additionally for the left over delay due to the storage capacity constraint.
						calculateStorageCongestion(event, remainingDelay);
					} else {
						this.delayNotInternalized = this.delayNotInternalized + remainingDelay;
						log.warn("Delay which is not internalized: " + this.delayNotInternalized);
					}
					
				} else {
					throw new RuntimeException("Delay due to storage capacity on link " + event.getLinkId() + ": " + remainingDelay + ". Aborting...");
				}
			
			} else {
				log.warn("Oups, storage delay: " + remainingDelay);
			}
			
		}
	}
	
	private double calculateFlowCongestion(double totalDelay, LinkLeaveEvent event) {
		LinkCongestionInfo linkInfo = this.linkId2congestionInfo.get(event.getLinkId());

		// Search for agents causing the delay on that link and throw delayEffects for the causing agents.
		List<Id> reverseList = new ArrayList<Id>();
		reverseList.addAll(linkInfo.getLeavingAgents());
		Collections.reverse(reverseList);
		
		double delayToPayFor = totalDelay;
		for (Id id : reverseList){
			if (delayToPayFor > linkInfo.getMarginalDelayPerLeavingVehicle_sec()) {
				
//				System.out.println("	Person " + id.toString() + " --> Marginal delay: " + linkInfo.getMarginalDelayPerLeavingVehicle_sec() + " linkLeaveTime: " + linkInfo.getPersonId2linkLeaveTime().get(id));
				MarginalCongestionEvent congestionEvent = new MarginalCongestionEvent(event.getTime(), "flowCapacity", id, event.getPersonId(), linkInfo.getMarginalDelayPerLeavingVehicle_sec(), event.getLinkId());
				this.events.processEvent(congestionEvent);	
				
				delayToPayFor = delayToPayFor - linkInfo.getMarginalDelayPerLeavingVehicle_sec();
				
			} else {
				if (delayToPayFor > 0) {
					
//					System.out.println("	Person " + id + " --> Marginal delay: " + delayToPayFor + " linkLeaveTime: " + linkInfo.getPersonId2linkLeaveTime().get(id));
					MarginalCongestionEvent congestionEvent = new MarginalCongestionEvent(event.getTime(), "flowCapacity", id, event.getPersonId(), delayToPayFor, event.getLinkId());
					this.events.processEvent(congestionEvent);
					
					delayToPayFor = 0;
				}
			}
		}
		
		if (delayToPayFor == 1.) {
			log.warn("Remaining delay of 1.0 sec may result from rounding errors. Setting the remaining delay to 0.0 sec.");
			delayToPayFor = 0.;
		}
		return delayToPayFor;
	}

	private void calculateStorageCongestion(LinkLeaveEvent event, double remainingDelay) {
			
		// Find the last agent blocking the next link at the relevant time step.
		// Relevant time step: The free flow travel time.
			
		double relevantTimeStep = this.linkId2congestionInfo.get(event.getLinkId()).getPersonId2freeSpeedLeaveTime().get(event.getPersonId());
		
		Id causingAgent = null;
		
		// Get the last agent blocking the next link
		Id nextLinkId = getNextLinkId(event.getPersonId(), event.getLinkId(), event.getTime());
		causingAgent = getCausingAgentNextLink(nextLinkId, relevantTimeStep);
		
		if (causingAgent == null){
			throw new RuntimeException("No agent identified who is causing the delay due to storage capacity. Aborting...");
		}
		
		MarginalCongestionEvent congestionEvent = new MarginalCongestionEvent(event.getTime(), "storageCapacity", causingAgent, event.getPersonId(), remainingDelay, event.getLinkId());
		this.events.processEvent(congestionEvent);
	}
	
	private Id getCausingAgentNextLink(Id nextLinkId, double relevantTimeStep) {
		Id causingAgent = null;
		LinkCongestionInfo linkInfo = this.linkId2congestionInfo.get(nextLinkId);
			
		int agentsOnNextLink = 0;
		double lastLinkEnterTime = Double.NEGATIVE_INFINITY;
		
		for (LinkEnterLeaveInfo info : linkInfo.getPersonEnterLeaveInfos()) {
			
			if ((info.getLinkEnterTime() <= relevantTimeStep && info.getLinkLeaveTime() > relevantTimeStep) || (info.getLinkEnterTime() <= relevantTimeStep && info.getLinkLeaveTime() == 0)){
				// person at relevant time (flowLeaveTime) on next link
				agentsOnNextLink++;
				
				if (info.getLinkEnterTime() > lastLinkEnterTime) {
					// person entering this link after previously identified agents
					causingAgent = info.getPersonId();
					lastLinkEnterTime = info.getLinkEnterTime();
				} else {
					// person entering the link earlier, thus not relevant
				}
				
			} else {
				// person was not on the link at the linkLeaveTime
			}
		}

		if (agentsOnNextLink < linkInfo.getStorageCapacity_cars()){
			log.warn("Number of agents on next link (" + nextLinkId + "): " + agentsOnNextLink + " at time step " + relevantTimeStep + ". But: storage capacity: " + linkInfo.getStorageCapacity_cars());
		}
		
//		if only looking at the current time step:
//		double causingAgentFreeSpeedLeaveTime = Double.NEGATIVE_INFINITY;
//
//		// all agents who are currently on that link
//		for (Id id : linkInfo.getAgentsOnLink()){
//
//			// get last agent in queue
//			if (linkInfo.getPersonId2freeSpeedLeaveTime().get(id) > causingAgentFreeSpeedLeaveTime) {
//				causingAgentFreeSpeedLeaveTime = linkInfo.getPersonId2freeSpeedLeaveTime().get(id);
//				causingAgent = id;
//			}
//		}
		
		return causingAgent;
	}

	private Id getNextLinkId(Id affectedAgent, Id linkId, double time) {
		Id nextLinkId = null;
		
		List<Id> currentRouteLinkIDs = null;
		
		Plan selectedPlan = this.scenario.getPopulation().getPersons().get(affectedAgent).getSelectedPlan();
		for (PlanElement pE : selectedPlan.getPlanElements()) {
			if (pE instanceof Activity){
				Activity act = (Activity) pE;
				if (act.getEndTime() < 0.) {
					// act has no endtime
					
				} else if (time >= act.getEndTime()) {
					int nextLegIndex = selectedPlan.getPlanElements().indexOf(pE) + 1;
					if (selectedPlan.getPlanElements().size() <= nextLegIndex) {
						// last activity
					} else {
						if (selectedPlan.getPlanElements().get(nextLegIndex) instanceof Leg) {
							Leg leg = (Leg) selectedPlan.getPlanElements().get(nextLegIndex);
							List<Id> linkIDs = new ArrayList<Id>();
							NetworkRoute route = (NetworkRoute) leg.getRoute();
							linkIDs.add(route.getStartLinkId());
							linkIDs.addAll(route.getLinkIds());
							linkIDs.add(route.getEndLinkId()); // assuming this link to be the last link where the storage capacity plays a role.
							if (linkIDs.contains(linkId)){
								// probably current route
								currentRouteLinkIDs = linkIDs;
							}
							
						} else {
							throw new RuntimeException("Plan element behind activity not instance of Leg. Aborting...");
						}
					}
				}
			}
		}
		
		// get all following linkIDs
		boolean linkAfterCurrentLink = false;
		List<Id> linkIDsAfterCurrentLink = new ArrayList<Id>();
		for (Id id : currentRouteLinkIDs){
			if (linkAfterCurrentLink){
				linkIDsAfterCurrentLink.add(id);
			}
			if (linkId.toString().equals(id.toString())){
				linkAfterCurrentLink = true;
			}
		}
		
		nextLinkId = linkIDsAfterCurrentLink.get(0);
		return nextLinkId;
	}
	
	private void clearTrackingMarginalDelays1(LinkLeaveEvent event) {
		LinkCongestionInfo linkInfo = this.linkId2congestionInfo.get(event.getLinkId());
		
		if (linkInfo.getLeavingAgents().size() == 0) {
			// no agent is being tracked for that link
			
		} else {
			// clear trackings of persons leaving that link previously
			double lastLeavingFromThatLink = getLastLeavingTime(linkInfo.getPersonId2linkLeaveTime());
			double earliestLeaveTime = lastLeavingFromThatLink + linkInfo.getMarginalDelayPerLeavingVehicle_sec();
			double freeSpeedLeaveTime = linkInfo.getPersonId2freeSpeedLeaveTime().get(event.getPersonId());
//			System.out.println("earliestLeaveTime: " + earliestLeaveTime);
//			System.out.println("freeSpeedLeaveTime: " + freeSpeedLeaveTime);

			if (freeSpeedLeaveTime > earliestLeaveTime + 1.0){
//				System.out.println("Flow congestion has disappeared on link " + event.getLinkId() + ". Delete agents leaving previously that link: " + linkInfo.getLeavingAgents().toString());
				linkInfo.getLeavingAgents().clear();
				linkInfo.getPersonId2linkLeaveTime().clear();
				
			}  else {

				
			}
		}
	}
	
	private void trackMarginalDelay(LinkLeaveEvent event) {
		LinkCongestionInfo linkInfo = this.linkId2congestionInfo.get(event.getLinkId());
		
		// start tracking delays caused by that agent leaving the link	
		if (linkInfo.getLeavingAgents().contains(event.getPersonId())){
			throw new RuntimeException(" Person already in List (leavingAgents). Aborting...");
		}
		if (linkInfo.getPersonId2linkLeaveTime().containsKey(event.getPersonId())){
			throw new RuntimeException(" Person already in Map (personId2linkLeaveTime). Aborting...");
		}
		linkInfo.getLeavingAgents().add(event.getPersonId());
		linkInfo.getPersonId2linkLeaveTime().put(event.getPersonId(), event.getTime());
	}

	private void collectLinkInfos(Id linkId) {
		LinkCongestionInfo linkInfo = new LinkCongestionInfo();	

		NetworkImpl network = (NetworkImpl) this.scenario.getNetwork();
		Link link = network.getLinks().get(linkId);
		linkInfo.setLinkId(link.getId());
		linkInfo.setFreeTravelTime(Math.ceil(link.getLength() / link.getFreespeed()));
		
		double flowCapacity_hour = link.getCapacity() * this.scenario.getConfig().getQSimConfigGroup().getFlowCapFactor();
		double marginalDelay_sec = Math.floor((1 / (flowCapacity_hour / this.scenario.getNetwork().getCapacityPeriod()) ) );
		linkInfo.setMarginalDelayPerLeavingVehicle(marginalDelay_sec);
		
		int storageCapacity_cars = (int) (Math.ceil((link.getLength() * link.getNumberOfLanes()) / network.getEffectiveCellSize()) * this.scenario.getConfig().getQSimConfigGroup().getStorageCapFactor() );
		linkInfo.setStorageCapacity_cars(storageCapacity_cars);
		
		this.linkId2congestionInfo.put(link.getId(), linkInfo);
	}
	
	private void updateLinkInfo_agentEntersLink(double time, Id personId, Id linkId) {
		if (this.linkId2congestionInfo.get(linkId).getPersonEnterLeaveInfos() == null) {
			List<LinkEnterLeaveInfo> enterLeaveInfos = new ArrayList<LinkEnterLeaveInfo>();
			LinkEnterLeaveInfo linkEnterLeaveInfo = new LinkEnterLeaveInfo();
			linkEnterLeaveInfo.setPersonId(personId);
			linkEnterLeaveInfo.setLinkEnterTime(time);
			linkEnterLeaveInfo.setLinkLeaveTime(0.);
			
			enterLeaveInfos.add(linkEnterLeaveInfo);
			linkId2congestionInfo.get(linkId).setPersonEnterLeaveInfos(enterLeaveInfos);
		} else {
			List<LinkEnterLeaveInfo> enterLeaveInfos = this.linkId2congestionInfo.get(linkId).getPersonEnterLeaveInfos();			
			LinkEnterLeaveInfo linkEnterLeaveInfo = new LinkEnterLeaveInfo();
			linkEnterLeaveInfo.setPersonId(personId);
			linkEnterLeaveInfo.setLinkEnterTime(time);
			linkEnterLeaveInfo.setLinkLeaveTime(0.);
			
			enterLeaveInfos.add(linkEnterLeaveInfo);
			linkId2congestionInfo.get(linkId).setPersonEnterLeaveInfos(enterLeaveInfos);
		}
	}

	private void updateLinkInfo_agentLeavesLink(double time, Id personId, Id linkId) {
		
		if (this.linkId2congestionInfo.get(linkId).getPersonEnterLeaveInfos() == null) {
			List<LinkEnterLeaveInfo> personId2enterLeaveInfo = new ArrayList<LinkEnterLeaveInfo>();
			LinkEnterLeaveInfo linkEnterLeaveInfo = new LinkEnterLeaveInfo();
			linkEnterLeaveInfo.setPersonId(personId);
			linkEnterLeaveInfo.setLinkLeaveTime(time);
			
			personId2enterLeaveInfo.add(linkEnterLeaveInfo);
			linkId2congestionInfo.get(linkId).setPersonEnterLeaveInfos(personId2enterLeaveInfo);
		
		} else {
			List<LinkEnterLeaveInfo> personId2enterLeaveInfo = this.linkId2congestionInfo.get(linkId).getPersonEnterLeaveInfos();
			for (LinkEnterLeaveInfo info : personId2enterLeaveInfo) {
				if (info.getPersonId().toString().equals(personId.toString())){
					if (info.getLinkLeaveTime() == 0.){
						// EnterLeaveInfo with not yet set leaving time
						info.setLinkLeaveTime(time);
					} else {
						// completed EnterLeaveInfo
					}
				}
			}
		}
	}
	
	private double getLastLeavingTime(Map<Id, Double> personId2LinkLeaveTime) {
		
		double lastLeavingFromThatLink = Double.NEGATIVE_INFINITY;
		for (Id id : personId2LinkLeaveTime.keySet()){
			if (personId2LinkLeaveTime.get(id) > lastLeavingFromThatLink) {
				lastLeavingFromThatLink = personId2LinkLeaveTime.get(id);
			}
		}
		return lastLeavingFromThatLink;
	}
	
}
