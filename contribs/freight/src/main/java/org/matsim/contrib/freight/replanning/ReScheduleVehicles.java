package org.matsim.contrib.freight.replanning;

import java.util.ArrayList;
import java.util.Collection;

import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.freight.carrier.Carrier;
import org.matsim.contrib.freight.carrier.CarrierFactory;
import org.matsim.contrib.freight.carrier.CarrierPlan;
import org.matsim.contrib.freight.carrier.ScheduledTour;
import org.matsim.contrib.freight.vrp.DTWSolver;
import org.matsim.contrib.freight.vrp.algorithms.rr.RuinAndRecreateAlgorithmFactory;
import org.matsim.contrib.freight.vrp.algorithms.rr.RuinAndRecreateListener;
import org.matsim.contrib.freight.vrp.algorithms.rr.tourAgents.ServiceProviderFactory;
import org.matsim.contrib.freight.vrp.algorithms.rr.tourAgents.agentFactories.ServiceProviderFactoryFinder;
import org.matsim.contrib.freight.vrp.basics.Costs;
import org.matsim.contrib.freight.vrp.basics.VRPSchema;
import org.matsim.contrib.freight.vrp.constraints.PickORDeliveryCapacityAndTWConstraint;

public class ReScheduleVehicles implements CarrierPlanStrategyModule{

	private Network network;

	private Costs costs;
	
	public Collection<RuinAndRecreateListener> listeners = new ArrayList<RuinAndRecreateListener>();
	
	public ReScheduleVehicles(Network network, Costs costs) {
		super();
		this.network = network;
		this.costs = costs;
	}

	@Override
	public void handleActor(Carrier carrier) {	
		ServiceProviderFactory spFactory = new ServiceProviderFactoryFinder().getFactory(VRPSchema.SINGLEDEPOT_DISTRIBUTION_TIMEWINDOWS);
		DTWSolver vrpSolver = new DTWSolver(new CarrierFactory().getShipments(carrier.getContracts()), 
				new CarrierFactory().getVehicles(carrier.getCarrierCapabilities()), costs, network, carrier.getSelectedPlan());
		vrpSolver.setRuinAndRecreateFactory(new RuinAndRecreateAlgorithmFactory(spFactory));
		vrpSolver.setGlobalConstraints(new PickORDeliveryCapacityAndTWConstraint());
		vrpSolver.setnOfWarmupIterations(20);
		vrpSolver.setnOfIterations(500);
		vrpSolver.listeners.addAll(listeners);
		Collection<ScheduledTour> scheduledTours = vrpSolver.solve();
		carrier.setSelectedPlan(new CarrierPlan(scheduledTours));
	}

}
