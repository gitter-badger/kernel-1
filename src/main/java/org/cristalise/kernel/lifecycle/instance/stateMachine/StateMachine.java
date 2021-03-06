/**
 * This file is part of the CRISTAL-iSE kernel.
 * Copyright (c) 2001-2014 The CRISTAL Consortium. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 3 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; with out even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 *
 * http://www.fsf.org/licensing/licenses/lgpl.html
 */
package org.cristalise.kernel.lifecycle.instance.stateMachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.cristalise.kernel.common.AccessRightsException;
import org.cristalise.kernel.common.InvalidDataException;
import org.cristalise.kernel.common.InvalidTransitionException;
import org.cristalise.kernel.common.ObjectNotFoundException;
import org.cristalise.kernel.lifecycle.instance.Activity;
import org.cristalise.kernel.lookup.AgentPath;
import org.cristalise.kernel.utils.DescriptionObject;
import org.cristalise.kernel.utils.Logger;


public class StateMachine implements DescriptionObject
{
	public String name;
	public int version;

	private ArrayList<State> states;
	private ArrayList<Transition> transitions;
	private final HashMap<Integer, State> stateCodes;
	private final HashMap<Integer, Transition> transitionCodes;
	
	State initialState;
	int initialStateCode;
	boolean isCoherent = false;
	
	public StateMachine() {
		states = new ArrayList<State>();
		transitions = new ArrayList<Transition>();
		stateCodes = new HashMap<Integer, State>();
		transitionCodes = new HashMap<Integer, Transition>();
	}
	
	public void setStates(ArrayList<State> newStates) {
		this.states = newStates;
		validate();
	}
	
	public void setTransitions(ArrayList<Transition> newTransitions) {
		this.transitions = newTransitions;
		validate();
	}
	
	public void validate() {
		stateCodes.clear();		
		transitionCodes.clear();
		isCoherent = true;
		
		for (State state : states) {
			Logger.debug(6, "State "+state.id+": "+state.name);
			stateCodes.put(state.getId(), state);
		}

		if (stateCodes.containsKey(initialStateCode))
			initialState = stateCodes.get(initialStateCode);
		else
			isCoherent = false;
		
		for (Transition trans : transitions) {
			Logger.debug(6, "Transition "+trans.id+": "+trans.name);
			transitionCodes.put(trans.getId(), trans);
			isCoherent = isCoherent && trans.resolveStates(stateCodes);
		}
		
	}
	
	public ArrayList<State> getStates() {
		return states;
	}
	
	public ArrayList<Transition> getTransitions() {
		return transitions;
	}
	
	public State getInitialState() {
		return initialState;
	}

	public void setInitialState(State initialState) {
		this.initialState = initialState;
		initialStateCode = initialState.getId();
	}

	public int getInitialStateCode() {
		return initialStateCode;
	}

	public void setInitialStateCode(int initialStateCode) {
		this.initialStateCode = initialStateCode;
		initialState = stateCodes.get(initialStateCode);
		if (initialState == null) isCoherent = false;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int getVersion() {
		return version;
	}
	
	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void setVersion(int version) {
		this.version = version;
	}

	public Transition getTransition(int transitionID) {
		return transitionCodes.get(transitionID);
	}
	
	public State getState(int stateID) {
		return stateCodes.get(stateID);
	}
	
	public Map<Transition, String> getPossibleTransitions(Activity act, AgentPath agent) throws ObjectNotFoundException, InvalidDataException {
		HashMap<Transition, String> returnList = new HashMap<Transition, String>();
		State currentState = getState(act.getState());
		for (Integer transCode : currentState.getPossibleTransitionIds()) {
			Transition possTrans = currentState.getPossibleTransitions().get(transCode);
			try {
				String role = possTrans.getPerformingRole(act, agent);
				returnList.put(possTrans, role);
			} catch (AccessRightsException ex) { 
				if (Logger.doLog(5))
					Logger.msg(5, "Transition '"+possTrans+"' not possible for "+agent.getAgentName()+": "+ex.getMessage());
			}
		}
		return returnList;
	}

	public State traverse(Activity act, Transition transition, AgentPath agent) throws InvalidTransitionException, AccessRightsException, ObjectNotFoundException, InvalidDataException {
		State currentState = getState(act.getState());
		if (transition.originState.equals(currentState)) {
			transition.getPerformingRole(act, agent);
			return transition.targetState;
		}
		else
			throw new InvalidTransitionException("Transition '"+transition.getName()+"' not valid from state '"+currentState.getName());
			
	}
	
	public boolean isCoherent() {
		return isCoherent;
	}
	
	
}