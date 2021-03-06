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

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.cristalise.kernel.common.AccessRightsException;
import org.cristalise.kernel.common.InvalidDataException;
import org.cristalise.kernel.common.ObjectNotFoundException;
import org.cristalise.kernel.lifecycle.instance.Activity;
import org.cristalise.kernel.lookup.AgentPath;
import org.cristalise.kernel.lookup.RolePath;
import org.cristalise.kernel.persistency.outcome.Schema;
import org.cristalise.kernel.process.Gateway;
import org.cristalise.kernel.utils.CastorHashMap;
import org.cristalise.kernel.utils.LocalObjectLoader;
import org.cristalise.kernel.utils.Logger;


public class Transition {

	int id;
	String name;
	
	int originStateId;
	int targetStateId;
	State originState;
	State targetState;
	String reservation;
	
	String enabledProp; // Boolean property that permits this transition e.g. 'Skippable'
	
	// activation properties
	boolean requiresActive = true; // Whether the activity must be active for this transition to be available
	boolean finishing; // whether the target state is a finishing state;
	
	// permissions
	String roleOverride;
	
	TransitionOutcome outcome;
	TransitionScript script;
	
	public Transition() {
	}
	
	
	public Transition(int id, String name, int originStateId, int targetStateId) {
		super();
		this.id = id;
		this.name = name;
		this.originStateId = originStateId;
		this.targetStateId = targetStateId;
	}


	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public State getOriginState() {
		return originState;
	}

	public void setOriginState(State originState) {
		this.originState = originState;
	}

	public State getTargetState() {
		return targetState;
	}

	public void setTargetState(State targetState) {
		this.targetState = targetState;
		finishing = targetState.finished;
	}

	public String getEnabledProp() {
		return enabledProp;
	}

	public void setEnabledProp(String enabledProp) {
		this.enabledProp = enabledProp;
	}

	public boolean isRequiresActive() {
		return requiresActive;
	}
	
	public boolean isFinishing() {
		return finishing;
	}

	public void setRequiresActive(boolean requiresActive) {
		this.requiresActive = requiresActive;
	}

	public String getRoleOverride() {
		return roleOverride;
	}

	public void setRoleOverride(String roleOverride) {
		this.roleOverride = roleOverride;
	}

	public TransitionOutcome getOutcome() {
		return outcome;
	}

	public void setOutcome(TransitionOutcome outcome) {
		this.outcome = outcome;
	}

	public TransitionScript getScript() {
		return script;
	}

	public void setScript(TransitionScript script) {
		this.script = script;
	}
	
	public String getReservation() {
		return reservation;
	}

	public void setReservation(String reservation) {
		this.reservation = reservation;
	}
	
	protected boolean resolveStates(HashMap<Integer, State> states) {
		boolean allFound = true;
		if (states.keySet().contains(originStateId)) {
			setOriginState(states.get(originStateId));
			originState.addPossibleTransition(this);
		}
		else
			allFound = false;
		if (states.keySet().contains(targetStateId))
			setTargetState(states.get(targetStateId));
		else
			allFound = false;
		return allFound;
	}
	
	public int getOriginStateId() {
		return originStateId;
	}

	public void setOriginStateId(int originStateId) {
		this.originStateId = originStateId;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getTargetStateId() {
		return targetStateId;
	}

	public void setTargetStateId(int targetStateId) {
		this.targetStateId = targetStateId;
	}
	
	public String getPerformingRole(Activity act, AgentPath agent) throws ObjectNotFoundException, AccessRightsException {
		
		// check available
		if (!isEnabled(act.getProperties()))
			throw new AccessRightsException("Transition '"+name+"' is disabled by the '"+enabledProp+"' property.");
		
		// check active
		if (isRequiresActive() && !act.getActive()) 
			throw new AccessRightsException("Activity must be active to perform this transition");
		
		RolePath role = null;
		String overridingRole = resolveValue(roleOverride, act.getProperties());
		boolean override = overridingRole != null;
		boolean isOwner = false, isOwned = true;
		
		// Check agent name
		String agentName = act.getCurrentAgentName();
		if (agentName != null && agentName.length() >0) {
			if (agent.getAgentName().equals(agentName))
				isOwner = true;
		}
		else isOwned = false;
		
		// determine transition role
		if (override) {
			role = Gateway.getLookup().getRolePath(overridingRole);
		}
		else {
			String actRole = act.getCurrentAgentRole();
			if (actRole != null && actRole.length() > 0)
				role = Gateway.getLookup().getRolePath(actRole);
		}
		
		// Decide the access
		if (isOwned && !override && !isOwner) 
			throw new AccessRightsException("Agent '"+agent.getAgentName()
					+"' cannot perform this transition because the activity '"+act.getName()+"' is currently owned by "+agentName);
		
		if (role != null) {
			if (agent.hasRole(role))
				return role.getName();
			else if (agent.hasRole("Admin"))
				return "Admin";
			else
				throw new AccessRightsException("Agent '"+agent.getAgentName()
						+"' does not hold a suitable role '"+role.getName()+"' for the activity "+act.getName());
		}
		else
			return null;
	}
	
	public String getReservation(Activity act, AgentPath agent) {
		if (reservation == null || reservation.length() == 0)
			reservation = targetState.finished?"clear":"set";
		
		String reservedAgent = act.getCurrentAgentName();
		if (reservation.equals("set"))
			reservedAgent = agent.getAgentName();
		else if (reservation.equals("clear"))
			reservedAgent = "";
		return reservedAgent;
			
	}
	
	private static String resolveValue(String key, CastorHashMap props) {
		if (key==null) return null;
		String result = key;
		Pattern propField = Pattern.compile("\\$\\{(.+?)\\}");
		Matcher propMatcher = propField.matcher(result);
		while (propMatcher.find()) {			
			String propName = propMatcher.group(1);
			Object propValue = props.get(propName);
			Logger.msg(8, "Replacing Property "+propName+" as "+propValue);
			String propValString = propValue==null?"":propValue.toString();
			result = result.replace("${"+propName+"}", propValString);
		}
		return result;
	}
	
	public boolean isEnabled(CastorHashMap props) {
		if (enabledProp == null)
			return true;
		return (Boolean)props.get(enabledProp);
	}

	public boolean hasOutcome(CastorHashMap actProps) {
		if (outcome == null || actProps == null) return false;
		String outcomeName = resolveValue(outcome.schemaName, actProps);
		if (outcomeName == null || outcomeName.length() == 0)
			return false;
		String outcomeVersion = resolveValue(outcome.schemaVersion, actProps);
		if (outcomeVersion == null || outcomeVersion.length() == 0)
			return false;
		return true;
	}

	public Schema getSchema(CastorHashMap actProps) throws InvalidDataException, ObjectNotFoundException {
		if (hasOutcome(actProps))
			try {
				return LocalObjectLoader.getSchema(resolveValue(outcome.schemaName, actProps), 
					Integer.parseInt(resolveValue(outcome.schemaVersion, actProps)));
			} catch (NumberFormatException ex) {
				throw new InvalidDataException("Bad schema version number: "+outcome.schemaVersion+" ("+resolveValue(outcome.schemaVersion, actProps)+")");
			}
		else
			return null;
	}
	
	public String getScriptName(CastorHashMap actProps) {
		return resolveValue(script.scriptName, actProps);
	}
	
	public int getScriptVersion(CastorHashMap actProps) throws InvalidDataException {
		try {
			return Integer.parseInt(resolveValue(script.scriptVersion, actProps));
		} catch (NumberFormatException ex) {
			throw new InvalidDataException("Bad Script version number: "+script.scriptVersion+" ("+resolveValue(script.scriptVersion, actProps)+")");
		}
	}
	
	public boolean hasScript(CastorHashMap actProps) {
		if (script == null || actProps == null) return false;
		String scriptName = getScriptName(actProps);
		if (scriptName == null || scriptName.length() == 0)
			return false;
		String scriptVersion = resolveValue(script.scriptVersion, actProps);
		if (scriptVersion == null || scriptVersion.length() == 0)
			return false;
		return true;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
		return result;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Transition other = (Transition) obj;
		if (id != other.id)
			return false;
		return true;
	}
}
