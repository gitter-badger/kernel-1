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

import org.cristalise.kernel.common.AccessRightsException;
import org.cristalise.kernel.common.CannotManageException;
import org.cristalise.kernel.common.InvalidCollectionModification;
import org.cristalise.kernel.common.InvalidDataException;
import org.cristalise.kernel.common.InvalidTransitionException;
import org.cristalise.kernel.common.ObjectAlreadyExistsException;
import org.cristalise.kernel.common.ObjectCannotBeUpdated;
import org.cristalise.kernel.common.ObjectNotFoundException;
import org.cristalise.kernel.common.PersistencyException;
import org.cristalise.kernel.entity.agent.Job;
import org.cristalise.kernel.graph.model.GraphModel;
import org.cristalise.kernel.graph.model.GraphPoint;
import org.cristalise.kernel.graph.model.GraphableVertex;
import org.cristalise.kernel.lookup.AgentPath;
import org.cristalise.kernel.lookup.InvalidAgentPathException;
import org.cristalise.kernel.lookup.ItemPath;
import org.cristalise.kernel.utils.Logger;


/**
 * @version $Revision: 1.86 $ $Date: 2005/10/05 07:39:37 $
 * @author $Author: abranson $
 */
public class CompositeActivity extends Activity
{
    /*
     * --------------------------------------------
     * ----------------CONSTRUCTOR-----------------
     * --------------------------------------------
     */
    public CompositeActivity()
    {
        super();
        setChildrenGraphModel(new GraphModel(new WfVertexOutlineCreator()));
        setIsComposite(true);
    }

    // State machine
	public static final int START = 0;
	public static final int COMPLETE = 1;
	@Override
	protected String getDefaultSMName() {
		return "CompositeActivity";
	}

	@Override
	public void setChildrenGraphModel(GraphModel childrenGraph) {
		super.setChildrenGraphModel(childrenGraph);
		childrenGraph.setVertexOutlineCreator(new WfVertexOutlineCreator());
	}
    /**
     * @see org.cristalise.kernel.lifecycle.instance.WfVertex#verify()
     */
    /*
     * -------------------------------------------- --------------Other
     * Functions--------------- --------------------------------------------
     */
    /** launch the verification of the subprocess() */
    @Override
	public boolean verify()
    {
        boolean err = super.verify();
        GraphableVertex[] vChildren = getChildren();
        for (int i = 0; i < vChildren.length; i++)
        {
            if (!((WfVertex) vChildren[i]).verify())
            {
                mErrors.add("error in children");
                return false;
            }
        }
        return err;
    }

    /**
     * Initialize Activity and attach to the current activity
     *
     * @param act
     * @param first if true, the Waiting state will be one of the first launched by the parent activity
     * @param point
     */
    public void initChild(Activity act, boolean first, GraphPoint point)
    {
        safeAddChild(act, point);

        if (first) {
            getChildrenGraphModel().setStartVertexId(act.getID());
            Logger.msg(5, "org.cristalise.kernel.lifecycle.CompositeActivity::initChild() " + getName() + ":" + getID() + " was set to be first");
        }
    }

    /**
     * Adds vertex to graph cloning GraphPoint first (NPE safe)
     * 
     * @param v
     * @param g
     */
    private void safeAddChild(GraphableVertex v, GraphPoint g) {
        GraphPoint p = null;
        if(g != null) p = new GraphPoint(g.x, g.y);
        addChild(v, p);
    }

    /**
     * Method newChild.
     *
     * @param Name
     * @param Type
     * @param point
     * @return WfVertex
     */
    public WfVertex newExistingChild(Activity child, String Name, GraphPoint point)
    {
        child.setName(Name);
        safeAddChild(child, point);
        return child;
    }

    /**
     * Method newChild.
     *
     * @param Name
     * @param Type
     * @param point
     * @return WfVertex
     */
    public WfVertex newChild(String Name, String Type, GraphPoint point)
    {
        WfVertex v = newChild(Type, point);
        v.setName(Name);
        return v;
    }

    /**
     * Method newChild.
     *
     * @param vertexTypeId
     * @param point
     * @return WfVertex
     * @throws InvalidDataException 
     */
    public WfVertex newChild(String vertexTypeId, GraphPoint point)
    {
        return newChild(Types.valueOf(vertexTypeId), "False id", false, point);
    }

    /**
     * 
     * @param type
     * @param name
     * @param first
     * @param point
     * @return
     */
    public WfVertex newChild(Types type, String name, boolean first, GraphPoint point) {
        switch (type) {
            case Atomic:    return newAtomChild(name, first, point);
            case Composite: return newCompChild(name, first, point);
            case OrSplit:   return newSplitChild("Or", point);
            case XOrSplit:  return newSplitChild("XOr", point);
            case AndSplit:  return newSplitChild("And", point);
            case LoopSplit: return newSplitChild("Loop", point);
            case Join:      return newJoinChild(point);
            case Route:     return newRouteChild(point);
    
            default:
                throw new IllegalArgumentException("Unhandled enum value of WfVertex.Type:" + type.name());
        }
    }

    /**
     * Method newCompChild.
     *
     * @param id
     * @param first
     * @param point
     * @return CompositeActivity Create an initialize a composite Activity
     *         attached to the current activity
     */
    public CompositeActivity newCompChild(String id, boolean first, GraphPoint point)
    {
        CompositeActivity act = new CompositeActivity();
        initChild(act, first, point);
        act.setName(id);
        return act;
    }

    /**
     * Method newAtomChild.
     *
     * @param id
     * @param first
     * @param point
     * @return Activity Create an initialize an Atomic Activity attached to the
     *         current activity
     *
     */
    public Activity newAtomChild(String id, boolean first, GraphPoint point)
    {
        Activity act = new Activity();
        initChild(act, first, point);
        act.setName(id);
        return act;
    }

    /**
     * Method newSplitChild.
     *
     * @param Type
     * @param point
     * @return Split
     */
    public Split newSplitChild(String Type, GraphPoint point)
    {
        Split split = null;

        if      (Type.equals("Or"))   { split = new OrSplit(); } 
        else if (Type.equals("XOr"))  { split = new XOrSplit(); }
        else if (Type.equals("Loop")) { split = new Loop(); }
        else                          { split = new AndSplit(); }

        safeAddChild(split, point);

        return split;
    }

    /**
     * Method newJoinChild.
     *
     * @param point
     * @return Join
     */
    public Join newJoinChild(GraphPoint point)
    {
        Join join = new Join();
        join.getProperties().put("Type", "Join");
        safeAddChild(join, point);
        return join;
    }

    public Join newRouteChild(GraphPoint point)
    {
        Join join = new Join();
        join.getProperties().put("Type", "Route");
        safeAddChild(join, point);
        return join;
    }

    /**
     * None recursive search by id
     * 
     * @param id
     * @return WfVertex
     */
    WfVertex search(int id)
    {
        return (WfVertex)getGraphModel().resolveVertex(id);
    }

    /**
     * @throws InvalidDataException 
     * @throws ObjectNotFoundException 
     * @throws AccessRightsException 
     * @throws InvalidTransitionException 
     * @throws PersistencyException 
     * @throws ObjectAlreadyExistsException 
     * @throws ObjectCannotBeUpdated 
     * @see org.cristalise.kernel.lifecycle.instance.WfVertex#run()
     */
    @Override
	public void run(AgentPath agent, ItemPath itemPath) throws InvalidDataException
    {
        Logger.debug(8, getPath() + "CompisiteActivity::run() state: " + getState());

        super.run(agent, itemPath);
        if (getChildrenGraphModel().getStartVertex() != null && !getStateMachine().getState(state).isFinished())
        {
            WfVertex first = (WfVertex) getChildrenGraphModel().getStartVertex();
            first.run(agent, itemPath);
        }
    }

    @Override
	public void runNext(AgentPath agent, ItemPath itemPath) throws InvalidDataException 
    {
        if (!getStateMachine().getState(state).isFinished())
			try {
				request(agent, itemPath, CompositeActivity.COMPLETE, null);
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) { 
				Logger.error(e); // current agent couldn't complete the composite, so leave it
			} 
        super.runNext(agent, itemPath);
    }


    /**
     * @throws InvalidDataException 
     * @throws ObjectNotFoundException 
     * @throws InvalidAgentPathException 
     * @see org.cristalise.kernel.lifecycle.instance.Activity#calculateJobs()
     */
    @Override
	public ArrayList<Job> calculateJobs(AgentPath agent, ItemPath itemPath, boolean recurse) throws InvalidAgentPathException, ObjectNotFoundException, InvalidDataException 
    {
        ArrayList<Job> jobs = new ArrayList<Job>();
        boolean childActive = false;
        if (recurse)
            for (int i = 0; i < getChildren().length; i++)
                if (getChildren()[i] instanceof Activity)
                {
                    Activity child = (Activity) getChildren()[i];
                    jobs.addAll(child.calculateJobs(agent, itemPath, recurse));
                    childActive |= child.active;
                }
        if (!childActive)
            jobs.addAll(super.calculateJobs(agent, itemPath, recurse));
        return jobs;
    }

    @Override
	public ArrayList<Job> calculateAllJobs(AgentPath agent, ItemPath itemPath, boolean recurse) throws InvalidAgentPathException, ObjectNotFoundException, InvalidDataException 
    {
        ArrayList<Job> jobs = new ArrayList<Job>();
        if (recurse)
            for (int i = 0; i < getChildren().length; i++)
                if (getChildren()[i] instanceof Activity)
                {
                    Activity child = (Activity) getChildren()[i];
                    jobs.addAll(child.calculateAllJobs(agent, itemPath, recurse));
                }
        jobs.addAll(super.calculateAllJobs(agent, itemPath, recurse));
        return jobs;
    }

    /**
     * Method addNext.
     *
     * @param origin
     * @param terminus
     * @return Next
     */
    public Next addNext(WfVertex origin, WfVertex terminus)
    {
        return new Next(origin, terminus);
    }

    /**
     * Method addNext.
     *
     * @param originID
     * @param terminusID
     * @return Next
     */
    public Next addNext(int originID, int terminusID)
    {
        return addNext(search(originID), search(terminusID));
    }

    /**
     * Method hasGoodNumberOfActivity.
     *
     * @return boolean
     */
    public boolean hasGoodNumberOfActivity()
    {
        int endingAct = 0;
        for (int i = 0; i < getChildren().length; i++)
        {
            WfVertex vertex = (WfVertex) getChildren()[i];
            if (getChildrenGraphModel().getOutEdges(vertex).length == 0)
                endingAct++;
        }
        if (endingAct > 1)
            return false;
        return true;
    }

    /**
     * @see org.cristalise.kernel.lifecycle.instance.Activity#getType()
     */
    @Override
	public String getType()
    {
        return super.getType();
    }

    /**
     * @throws InvalidDataException 
     *
     */
    @Override
	public void reinit(int idLoop) throws InvalidDataException
    {
        super.reinit(idLoop);
        if (getChildrenGraphModel().getStartVertex() != null && !getStateMachine().getState(state).isFinished())
            ((WfVertex) getChildrenGraphModel().getStartVertex()).reinit(idLoop);
    }

    @Override
	public String request(AgentPath agent, ItemPath itemPath, int transitionID, String requestData) throws AccessRightsException, InvalidTransitionException, InvalidDataException, ObjectNotFoundException, PersistencyException, ObjectAlreadyExistsException, ObjectCannotBeUpdated, CannotManageException, InvalidCollectionModification
    {
        if (getChildrenGraphModel().getStartVertex() != null && !getStateMachine().getState(state).isFinished() && transitionID == CompositeActivity.START)
        	((WfVertex) getChildrenGraphModel().getStartVertex()).run(agent, itemPath);

        return super.request(agent, itemPath, transitionID, requestData);
    }
    
	public void refreshJobs(ItemPath itemPath)
    {
        GraphableVertex[] children = getChildren();
        for (GraphableVertex element : children)
			if (element instanceof CompositeActivity)
                ((CompositeActivity) element).refreshJobs(itemPath);
            else if (element instanceof Activity)
                ((Activity) element).pushJobsToAgents(itemPath);
    }
}