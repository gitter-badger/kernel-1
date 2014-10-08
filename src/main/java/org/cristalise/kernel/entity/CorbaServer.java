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
package org.cristalise.kernel.entity;

import java.util.Map;

import org.cristalise.kernel.common.CannotManageException;
import org.cristalise.kernel.common.InvalidDataException;
import org.cristalise.kernel.common.ObjectAlreadyExistsException;
import org.cristalise.kernel.common.ObjectNotFoundException;
import org.cristalise.kernel.entity.AgentHelper;
import org.cristalise.kernel.entity.ItemHelper;
import org.cristalise.kernel.entity.agent.ActiveEntity;
import org.cristalise.kernel.entity.agent.ActiveLocator;
import org.cristalise.kernel.lookup.AgentPath;
import org.cristalise.kernel.lookup.InvalidAgentPathException;
import org.cristalise.kernel.lookup.ItemPath;
import org.cristalise.kernel.process.Gateway;
import org.cristalise.kernel.utils.Logger;
import org.cristalise.kernel.utils.SoftCache;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAManager;
import org.omg.PortableServer.Servant;
import org.omg.PortableServer.POAManagerPackage.AdapterInactive;


/**************************************************************************
 *
 * $Revision: 1.8 $
 * $Date: 2005/10/13 08:13:44 $
 *
 * Copyright (C) 2003 CERN - European Organization for Nuclear Research
 * All rights reserved.
 **************************************************************************/


public class CorbaServer {
    private final Map<ItemPath, Servant>         mItemCache;
    private POA         mRootPOA;
    private POA         mItemPOA;
    private POA         mAgentPOA;
    private POAManager  mPOAManager;

    public CorbaServer() throws InvalidDataException {
        mItemCache   = new SoftCache<ItemPath, Servant>(50);

        // init POA
        try {
            setupPOA();
            mPOAManager.activate();
        } catch (Exception ex) {
            Logger.error(ex);
            throw new InvalidDataException("Error initialising POA");
        }

        new Thread(new Runnable() {
            @Override
			public void run() {
                Thread.currentThread().setName("ORB Invoker");
                Gateway.getORB().run();
            }
        }).start();
    }

    public void close() {
        try {
            mPOAManager.deactivate(true, true);
        } catch (AdapterInactive ex) {
            Logger.error(ex);
        }
    }

    /**************************************************************************
     * Initialises the C2KRootPOA with policies which are suitable for Factory objects
     **************************************************************************/
    public void setupPOA() throws Exception {

        //Initialise the RootPOA
        mRootPOA = org.omg.PortableServer.POAHelper.narrow(
                              Gateway.getORB().resolve_initial_references("RootPOA"));

        //Initilaise the default POAManager

        mPOAManager = mRootPOA.the_POAManager();

        // Create POA for use by the entities
        org.omg.CORBA.Policy[] policies = new org.omg.CORBA.Policy[6];

        policies[0] = mRootPOA.create_id_assignment_policy(
            org.omg.PortableServer.IdAssignmentPolicyValue.USER_ID);
        policies[1] = mRootPOA.create_lifespan_policy(
            org.omg.PortableServer.LifespanPolicyValue.PERSISTENT);
        policies[2] = mRootPOA.create_servant_retention_policy(
            org.omg.PortableServer.ServantRetentionPolicyValue.NON_RETAIN);
        policies[3] = mRootPOA.create_id_uniqueness_policy(
            org.omg.PortableServer.IdUniquenessPolicyValue.UNIQUE_ID);
        policies[4] = mRootPOA.create_request_processing_policy(
            org.omg.PortableServer.RequestProcessingPolicyValue.
            USE_SERVANT_MANAGER);
        policies[5] = mRootPOA.create_implicit_activation_policy(
            org.omg.PortableServer.ImplicitActivationPolicyValue.
            NO_IMPLICIT_ACTIVATION);

        mItemPOA = mRootPOA.create_POA( "Item",
                mRootPOA.the_POAManager(),
                                        policies );
        mAgentPOA = mRootPOA.create_POA( "Agent",
                mRootPOA.the_POAManager(),
                                        policies );

        //Create the locators
        TraceableLocator itemLocator = new TraceableLocator();
        mItemPOA.set_servant_manager( itemLocator._this( Gateway.getORB() ) );

        ActiveLocator agentLocator = new ActiveLocator();
        mAgentPOA.set_servant_manager( agentLocator._this( Gateway.getORB() ) );

    }


    /**************************************************************************
     * Returns a CORBA servant for a pre-existing entity
     * @throws ObjectNotFoundException 
     **************************************************************************/
    public TraceableEntity getItem(ItemPath itemPath) throws ObjectNotFoundException {
        Servant item = null;
        if (!itemPath.exists()) throw new ObjectNotFoundException(itemPath+" does not exist");
        synchronized (mItemCache) {
            item = mItemCache.get(itemPath);
            if (item == null) {
                Logger.msg(7, "Creating new servant for "+itemPath);
                item = new TraceableEntity(itemPath, mItemPOA);
                mItemCache.put(itemPath, item);
            }
        }
        return (TraceableEntity)item;
    }

    /**************************************************************************
     * Returns a CORBA servant for a pre-existing entity
     **************************************************************************/
    public ActiveEntity getAgent(AgentPath agentPath) throws InvalidAgentPathException, ObjectNotFoundException {
        Servant agent = null;
        if (!agentPath.exists()) throw new ObjectNotFoundException(agentPath+" does not exist");
        synchronized (mItemCache) {
        	agent = mItemCache.get(agentPath);
            if (agent == null) {
                Logger.msg(7, "Creating new servant for "+agentPath);
                agent = new ActiveEntity(agentPath, mAgentPOA);
                mItemCache.put(agentPath, agent);
            }
            else if (!(agent instanceof ActiveEntity))
            	throw new InvalidAgentPathException("Item "+agentPath+" was not an agent");
        }
        return (ActiveEntity)agent;
    }

    /**
     * @param itemPath
     * @return
     */
    public TraceableEntity createItem(ItemPath itemPath) throws CannotManageException, ObjectAlreadyExistsException {

    	if (itemPath.exists()) throw new ObjectAlreadyExistsException();
        org.omg.CORBA.Object obj = mItemPOA.create_reference_with_id(itemPath.getOID(), ItemHelper.id());
        itemPath.setIOR(obj);
        TraceableEntity item = new TraceableEntity(itemPath, mItemPOA);
        synchronized (mItemCache) {
            mItemCache.put(itemPath, item);
        }
        return item;
    }
    
    public ActiveEntity createAgent(AgentPath agentPath) throws CannotManageException, ObjectAlreadyExistsException {
    	if (agentPath.exists()) throw new ObjectAlreadyExistsException();
        org.omg.CORBA.Object obj = mAgentPOA.create_reference_with_id(agentPath.getOID(), AgentHelper.id());
        agentPath.setIOR(obj);
        ActiveEntity agent;
		agent = new ActiveEntity(agentPath, mAgentPOA);
        synchronized (mItemCache) {
            mItemCache.put(agentPath, agent);
        }
        return agent;
    	
    }
}