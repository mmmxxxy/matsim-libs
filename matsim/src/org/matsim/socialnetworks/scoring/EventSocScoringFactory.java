package org.matsim.socialnetworks.scoring;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.matsim.core.api.population.Activity;
import org.matsim.core.api.population.Plan;
import org.matsim.scoring.ScoringFunction;
import org.matsim.scoring.ScoringFunctionFactory;



public class EventSocScoringFactory implements ScoringFunctionFactory {

	private String factype;
	private LinkedHashMap<Activity,ArrayList<Double>> actStats;
	private ScoringFunctionFactory factory;

	public EventSocScoringFactory(String factype, ScoringFunctionFactory sf, LinkedHashMap<Activity,ArrayList<Double>> actStats) {
		this.factype=factype;
		this.actStats=actStats;
		this.factory=sf;

	}

	public ScoringFunction getNewScoringFunction(final Plan plan) {
//		return new SNScoringMaxFriendFoeRatio(plan, this.factype, this.scorer);
		return new EventSocScoringFunction(plan, this.factory.getNewScoringFunction(plan), factype, actStats);
	}


}