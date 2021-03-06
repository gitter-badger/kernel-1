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
package org.cristalise.kernel.lifecycle.instance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import org.cristalise.kernel.common.AccessRightsException;
import org.cristalise.kernel.common.CannotManageException;
import org.cristalise.kernel.common.GTimeStamp;
import org.cristalise.kernel.common.InvalidCollectionModification;
import org.cristalise.kernel.common.InvalidDataException;
import org.cristalise.kernel.common.InvalidTransitionException;
import org.cristalise.kernel.common.ObjectAlreadyExistsException;
import org.cristalise.kernel.common.ObjectCannotBeUpdated;
import org.cristalise.kernel.common.ObjectNotFoundException;
import org.cristalise.kernel.common.PersistencyException;
import org.cristalise.kernel.entity.agent.Job;
import org.cristalise.kernel.events.Event;
import org.cristalise.kernel.events.History;
import org.cristalise.kernel.graph.model.Vertex;
import org.cristalise.kernel.lifecycle.WfCastorHashMap;
import org.cristalise.kernel.lifecycle.instance.stateMachine.State;
import org.cristalise.kernel.lifecycle.instance.stateMachine.StateMachine;
import org.cristalise.kernel.lifecycle.instance.stateMachine.Transition;
import org.cristalise.kernel.lookup.AgentPath;
import org.cristalise.kernel.lookup.InvalidAgentPathException;
import org.cristalise.kernel.lookup.ItemPath;
import org.cristalise.kernel.lookup.Path;
import org.cristalise.kernel.lookup.RolePath;
import org.cristalise.kernel.persistency.outcome.Outcome;
import org.cristalise.kernel.persistency.outcome.Schema;
import org.cristalise.kernel.persistency.outcome.Viewpoint;
import org.cristalise.kernel.process.Gateway;
import org.cristalise.kernel.utils.DateUtility;
import org.cristalise.kernel.utils.LocalObjectLoader;
import org.cristalise.kernel.utils.Logger;

/**
 * @version $Revision: 1.222 $ $Date: 2005/10/05 07:39:37 $
 * @author $Author: abranson $
 */
public class Activity extends WfVertex
{
	/**
	 * vector of errors (Strings) that is constructed each time verify() is launched
	 */
	protected Vector<String> mErrors;
	/** @associates a State machine engine */
	private StateMachine machine;
	protected int state = -1;
	/** true is available to be executed */
	public boolean active = false;
	/** used in verify() */
	private boolean loopTested;
	private GTimeStamp mStateDate;
	private String mType;
	
	public Activity()
	{
		super();
		setProperties(new WfCastorHashMap());
		getProperties().put("StateMachineName", getDefaultSMName());
		mErrors = new Vector<String>(0, 1);
		mStateDate = new GTimeStamp();
		DateUtility.setToNow(mStateDate);
	}

	protected String getDefaultSMName() {
		return "Default";
	}

	/** add the activity which id is idNext as next of the current one */
	Next addNext(String idNext)
	{
		return addNext((WfVertex) getParent().search(idNext));
	}
	/**
	 * adds a New link between the current Activity and the WfVertex passed in param
	 */
	@Override
	public Next addNext(WfVertex vertex)
	{
		return new Next(this, vertex);
	}
	
	public StateMachine getStateMachine() throws InvalidDataException {
		if (machine == null) {
			String name = (String)getProperties().get("StateMachineName");
			int version = getVersionNumberProperty("StateMachineVersion");
			try {
				machine = LocalObjectLoader.getStateMachine(name, version);
			} catch (ObjectNotFoundException ex) {
				if (name.equals(getDefaultSMName()) && version == 0) { // default state machine not imported yet. Fake it.
					try {
						String marshalledSM = Gateway.getResource().getTextResource(null, "boot/SM/"+getDefaultSMName()+".xml");
						StateMachine bootstrap = (StateMachine)Gateway.getMarshaller().unmarshall(marshalledSM);
						bootstrap.validate();
						machine = bootstrap;
						return bootstrap;
					} catch (Exception ex2) {
						Logger.error(ex2);
						throw new InvalidDataException("Could not bootstrap default state machine from resources.");
					}
				}
				Logger.error(ex);
				throw new InvalidDataException("Error loading state machine '"+name+"' v"+version);
			}
		} 
		return machine;
	}
	
	/** return the current State of the State machine (Used in Serialisation) */
	public int getState() throws InvalidDataException
	{
		if (state == -1)
			state = getStateMachine().getInitialStateCode();
		return state;
	}
	public String getStateName() throws InvalidDataException
	{
		return getStateMachine().getState(getState()).getName();
	}
	
	/** Sets a new State */
	public void setState(int state)
	{
		this.state = state;
	}
	
	public boolean isFinished() throws InvalidDataException {
		return getStateMachine().getState(getState()).isFinished();
	}
	

	/** cf Item request 
	 * @throws ObjectNotFoundException 
	 * @throws PersistencyException 
	 * @throws ObjectAlreadyExistsException 
	 * @throws ObjectCannotBeUpdated 
	 * @throws CannotManageException 
	 * @throws InvalidCollectionModification */
	public String request(AgentPath agent, ItemPath itemPath, int transitionID, String requestData) throws AccessRightsException, InvalidTransitionException, InvalidDataException, ObjectNotFoundException, PersistencyException, ObjectAlreadyExistsException, ObjectCannotBeUpdated, CannotManageException, InvalidCollectionModification
	{

		// Find requested transition
		Transition transition = getStateMachine().getTransition(transitionID);
		
		// Check if the transition is possible
		String usedRole = transition.getPerformingRole(this, agent);
		
		// Verify outcome
		Schema schema = null;
		String viewName = null;
		boolean storeOutcome = false;
		if (transition.hasOutcome(getProperties())) {
			schema = transition.getSchema(getProperties());
			viewName = (String)getProperties().get("Viewpoint");
			if (requestData != null && requestData.length()>0)
				storeOutcome = true;
			else if (transition.getOutcome().isRequired()) 
				throw new InvalidDataException("Transition requires outcome data, but none was given");
		}
		
		// Get new state
		State oldState = getStateMachine().getState(this.state);
		State newState = getStateMachine().traverse(this, transition, agent);
		
		// Run extra logic in predefined steps here
		String outcome = runActivityLogic(agent, itemPath, transitionID, requestData);

		// set new state and reservation
		setState(newState.getId());
		getProperties().put("Agent Name", transition.getReservation(this, agent));

		// store new event
		Event newEvent = null;
		try {
			History hist = getWf().getHistory();
			if (storeOutcome)
				newEvent = hist.addEvent(agent, usedRole, getName(), getPath(), getType(), schema.docType, schema.docVersion, 
						getStateMachine().getName(), getStateMachine().getVersion(), transition, viewName);
			else 
				newEvent = hist.addEvent(agent, usedRole, getName(), getPath(), getType(), 
						getStateMachine().getName(), getStateMachine().getVersion(), transition);
			
			Logger.msg(7, "Activity::auditEvent() - Event:" + newEvent.getName() + " was added to the AuditTrail");
	
			if (storeOutcome) {
				Outcome newOutcome = new Outcome(newEvent.getID(), outcome, schema.docType, schema.docVersion);
				Gateway.getStorage().put(itemPath, newOutcome, getWf());
				
				// update specific view if defined
				if (viewName != null && !viewName.equals("")) {
					Viewpoint currentView = new Viewpoint(itemPath, schema.docType, viewName, schema.docVersion, newEvent.getID());
					Gateway.getStorage().put(itemPath, currentView, getWf());
				} 
				// update last view
				Viewpoint currentView = new Viewpoint(itemPath, schema.docType, "last", schema.docVersion, newEvent.getID());
				Gateway.getStorage().put(itemPath, currentView, getWf());
			}
			Gateway.getStorage().commit(getWf());
		} catch (PersistencyException ex) {
			Logger.error(ex);
			Gateway.getStorage().abort(getWf());
			throw ex;
		}

		if (newState.isFinished()) {
			if (!(getProperties().get("Breakpoint").equals(Boolean.TRUE) && !oldState.isFinished()))
				runNext(agent, itemPath);
		}
		
		DateUtility.setToNow(mStateDate);
		
		//refresh all the job lists
		String agentRole = getCurrentAgentRole();
		if (agentRole != null && agentRole.length()>0) {
	        try {
	        	RolePath myRole = Gateway.getLookup().getRolePath(agentRole);
	        	pushJobsToAgents(itemPath, myRole);
	        } catch (ObjectNotFoundException ex) { // non-existent role
	    		Logger.msg(7, "Activity.pushJobsToAgents() - Activity role '"+agentRole+" not found.");
	    	}
		}
		
		
		return outcome;
	}

	protected String runActivityLogic(AgentPath agent, ItemPath itemPath,
			int transitionID, String requestData) throws 
			InvalidDataException, 
			InvalidCollectionModification, 
			ObjectAlreadyExistsException, 
			ObjectCannotBeUpdated,
			ObjectNotFoundException, 
			PersistencyException, 
			CannotManageException 
	{
		// Overriden in predefined steps
		return requestData;
	}

	@Override
	public boolean verify()
	{
		mErrors.removeAllElements();
		int nbInEdgres = getInEdges().length;
		int nbOutEdges = getOutEdges().length;
		if (nbInEdgres == 0 && this.getID() != getParent().getChildrenGraphModel().getStartVertexId())
		{
			mErrors.add("Unreachable");
			return false;
		}
		else if (nbInEdgres > 1)
		{
			mErrors.add("Bad nb of previous");
			return false;
		}
		else if (nbOutEdges > 1)
		{
			mErrors.add("too many next");
			return false;
		}
		else if (nbOutEdges == 0)
		{
			if (!((CompositeActivity) getParent()).hasGoodNumberOfActivity())
			{
				mErrors.add("too many endpoints");
				return false;
			}
		}
//		else
//		{
//			Vertex[] outV = getOutGraphables();
//			Vertex[] anteVertices = GraphTraversal.getTraversal(getParent().getChildrenGraphModel(), this, GraphTraversal.kUp, false);
//			boolean errInLoop = false;
//			for (int i = 0; i < outV.length; i++)
//			{
//				for (int j = 0; j < anteVertices.length; j++)
//					if (!loop() && outV[i].getID() == anteVertices[j].getID())
//						errInLoop = true;
//			}
//			if (errInLoop)
//			{
//				mErrors.add("Error In Loop");
//				return false;
//			}
//		}
		return true;
	}
	/** Used in verify() */
	@Override
	public boolean loop()
	{
		boolean loop2 = false;
		if (!loopTested)
		{
			loopTested = true;
			if (getOutGraphables().length != 0)
				loop2 = ((WfVertex) getOutGraphables()[0]).loop();
		}
		loopTested = false;
		return loop2;
	}
	/** sets the next activity available if possible 
	 * @throws ObjectNotFoundException 
	 * @throws AccessRightsException 
	 * @throws InvalidTransitionException 
	 * @throws PersistencyException 
	 * @throws ObjectAlreadyExistsException 
	 * @throws ObjectCannotBeUpdated */
	@Override
	public void runNext(AgentPath agent, ItemPath itemPath) throws InvalidDataException
	{
		setActive(false);
		try
		{
			Vertex[] outVertices = getOutGraphables();
			Vertex[] outVertices2 = getOutGraphables();
			boolean hasNoNext = false;
			boolean out = false;
			while (!out)
				if (outVertices2.length > 0)
				{
					if (outVertices2[0] instanceof Join)
						outVertices2 = ((WfVertex) outVertices2[0]).getOutGraphables();
					else
						out = true;
				}
				else
				{
					hasNoNext = true;
					out = true;
				}
			Logger.debug(8, Arrays.toString(outVertices) + " " + Arrays.toString(outVertices2));
			if (!hasNoNext)
				 ((WfVertex) outVertices[0]).run(agent, itemPath);
			else
			{
				if (getParent() != null && getParent().getName().equals("domain")) // workflow
					// finished
					setActive(true);
				else
				{
					CompositeActivity parent = (CompositeActivity) getParent();
					if (parent != null)
						parent.runNext(agent, itemPath);
				}
			}
		}
		catch (InvalidDataException s)
		{
			setActive(true);
			throw s;
		}
	}
	/** @return the only Next of the Activity */
	public Next getNext()
	{
		if (getOutEdges().length > 0)
			return (Next) getOutEdges()[0];
		else
			return null;
	}
	/** reinitialises the Activity and propagate (for Loop) 
	 * @throws InvalidDataException 
	 * @throws ObjectNotFoundException */
	@Override
	public void reinit(int idLoop) throws InvalidDataException
	{
		Vertex[] outVertices = getOutGraphables();
		setState(getStateMachine().getInitialState().getId());
		if (outVertices.length > 0)
		{
			WfVertex nextAct = (WfVertex) outVertices[0];
			nextAct.reinit(idLoop);
		}
	}
	/** return the String that identifies the errors found in th activity */
	@Override
	public String getErrors()
	{
		if (mErrors.size() == 0)
			return "No error";
		return mErrors.elementAt(0);
	}
	/**
	 * called by precedent Activity runNext() for setting the activity able to be executed
	 * @throws InvalidDataException 
	 * @throws ObjectAlreadyExistsException 
	 * @throws AccessRightsException 
	 * @throws InvalidTransitionException 
	 * @throws ObjectNotFoundException 
	 * @throws PersistencyException 
	 * @throws ObjectCannotBeUpdated 
	 */
	@Override
	public void run(AgentPath agent, ItemPath itemPath) throws InvalidDataException
	{
		Logger.debug(8, "Activity::run() path:" + getPath() + " state:" + getState());

		if (!getActive()) setActive(true);
		boolean finished = getStateMachine().getState(getState()).isFinished();
		if (finished)
		{
			runNext(agent, itemPath);
		}
		else
		{
			DateUtility.setToNow(mStateDate);
			pushJobsToAgents(itemPath);
		}
	}
	/**
	 * sets the activity available to be executed on start of Workflow or composite activity (when it is the first one of the (sub)process
	 * @throws InvalidDataException 
	 * @throws ObjectAlreadyExistsException 
	 * @throws ObjectNotFoundException 
	 * @throws AccessRightsException 
	 * @throws InvalidTransitionException 
	 * @throws PersistencyException 
	 * @throws ObjectCannotBeUpdated 
	 */
	@Override
	public void runFirst(AgentPath agent, ItemPath itemPath) throws InvalidDataException
	{
		Logger.debug(8, getPath() + " runfirst");
		run(agent, itemPath);
	}
	/** @return the current ability to be executed */
	public boolean getActive()
	{
		return active;
	}
	/** sets the ability to be executed */
	public void setActive(boolean acti)
	{
		active = acti;
	}
	/** @return the Description field of properties */
	public String getDescription()
	{
		if (getProperties().containsKey("Description"))
			return (String) (getProperties().get("Description"));
		return "No description";
	}
	public String getCurrentAgentName()
	{
		return (String) getProperties().get("Agent Name");
	}
	public String getCurrentAgentRole()
	{
		return (String) getProperties().get("Agent Role");
	}

	/**
	 * returns the lists of jobs for the activity and children (cf org.cristalise.kernel.entity.Job)
	 * @throws InvalidDataException 
	 * @throws ObjectNotFoundException 
	 * @throws InvalidAgentPathException 
	 */
	public ArrayList<Job> calculateJobs(AgentPath agent, ItemPath itemPath, boolean recurse) throws InvalidAgentPathException, ObjectNotFoundException, InvalidDataException
	{
		return calculateJobsBase(agent, itemPath, false);
	} //
	public ArrayList<Job> calculateAllJobs(AgentPath agent, ItemPath itemPath, boolean recurse) throws InvalidAgentPathException, ObjectNotFoundException, InvalidDataException
	{
		return calculateJobsBase(agent, itemPath, true);
	}
	private ArrayList<Job> calculateJobsBase(AgentPath agent, ItemPath itemPath, boolean includeInactive) throws ObjectNotFoundException, InvalidDataException, InvalidAgentPathException
	{
		Logger.msg(7, "calculateJobs - " + getPath());
		ArrayList<Job> jobs = new ArrayList<Job>();
		Map<Transition, String> transitions;
		if ((includeInactive || getActive()) && !getName().equals("domain")) {
			transitions = getStateMachine().getPossibleTransitions(this, agent);
			Logger.msg(7, "Activity.calculateJobs() - Got " + transitions.size() + " transitions.");
			for (Transition transition : transitions.keySet()) {
				Logger.msg(7, "Creating Job object for transition " + transition);
				jobs.add(new Job(this, itemPath, transition, agent, transitions.get(transition)));
			}
		}
		return jobs;
	}


	public void pushJobsToAgents(ItemPath itemPath) {
		String agentRole = getCurrentAgentRole();
		if (agentRole != null && agentRole.length()>0) {
	        try {
	        	RolePath myRole = Gateway.getLookup().getRolePath(agentRole);
	        	pushJobsToAgents(itemPath, myRole);
	        } catch (ObjectNotFoundException ex) { // non-existent role
	    		Logger.msg(7, "Activity.pushJobsToAgents() - Activity role '"+agentRole+" not found.");
	    	}
		}
	}
	
	public void pushJobsToAgents(ItemPath itemPath, RolePath role)
	{
		if (role.hasJobList())
			new JobPusher(this, itemPath, role).start();
		Iterator<Path> childRoles = role.getChildren();
		while (childRoles.hasNext()) {
			RolePath childRole = (RolePath)childRoles.next();
			pushJobsToAgents(itemPath, childRole);
		}
    }
	
	/**
	   * Returns the startDate.
	   *
	   * @return GTimeStamp
	   */
	public GTimeStamp getStateDate()
	{
		return mStateDate;
	} 
	public void setStateDate(GTimeStamp startDate)
	{
		mStateDate = startDate;
	}
	
	@Deprecated
	public void setActiveDate(GTimeStamp date)
	{ } 
	@Deprecated
	public void setStartDate(GTimeStamp date)
	{ 
		setStateDate(date);
	} 
	
 /**
	   * Returns the type.
	   *
	   * @return String
	   */
	public String getType()
	{
		return mType;
	} /**
	   * Sets the type.
	   *
	   * @param type
	   *            The type to set
	   */
	public void setType(String type)
	{
		mType = type;
	}

}
