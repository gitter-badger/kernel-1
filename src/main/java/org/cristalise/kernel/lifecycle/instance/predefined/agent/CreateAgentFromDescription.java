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
package org.cristalise.kernel.lifecycle.instance.predefined.agent;

import java.util.Arrays;

import org.cristalise.kernel.common.CannotManageException;
import org.cristalise.kernel.common.InvalidDataException;
import org.cristalise.kernel.common.ObjectAlreadyExistsException;
import org.cristalise.kernel.common.ObjectCannotBeUpdated;
import org.cristalise.kernel.common.ObjectNotFoundException;
import org.cristalise.kernel.common.PersistencyException;
import org.cristalise.kernel.entity.CorbaServer;
import org.cristalise.kernel.entity.agent.ActiveEntity;
import org.cristalise.kernel.lifecycle.instance.predefined.item.CreateItemFromDescription;
import org.cristalise.kernel.lookup.AgentPath;
import org.cristalise.kernel.lookup.ItemPath;
import org.cristalise.kernel.lookup.RolePath;
import org.cristalise.kernel.process.Gateway;
import org.cristalise.kernel.property.PropertyArrayList;
import org.cristalise.kernel.utils.Logger;


/**************************************************************************
 *
 * @author $Author: abranson $ $Date: 2005/10/13 08:13:58 $
 * @version $Revision: 1.47 $
 **************************************************************************/
public class CreateAgentFromDescription extends CreateItemFromDescription
{
	public CreateAgentFromDescription()
	{
		super();
	}

	/**
	 * Params:
	 * <ol><li>New Agent name</li>
	 * <li>Description version to use</li>
	 * <li>Comma-delimited Role names to assign to the agent. Must already exist.</li>
	 * <li>Initial properties to set in the new Agent</li>
	 * </ol>
	 * @throws ObjectNotFoundException 
	 * @throws InvalidDataException The input parameters were incorrect
	 * @throws ObjectAlreadyExistsException The Agent already exists
	 * @throws CannotManageException The Agent could not be created
	 * @throws ObjectCannotBeUpdated The addition of the new entries into the LookupManager failed
	 * @see org.cristalise.kernel.lifecycle.instance.predefined.item.CreateItemFromDescription#runActivityLogic(org.cristalise.kernel.lookup.AgentPath, int, int, java.lang.String)
	 */
	@Override
	protected String runActivityLogic(AgentPath agent, ItemPath item,
			int transitionID, String requestData) throws ObjectNotFoundException, InvalidDataException, ObjectAlreadyExistsException, CannotManageException, ObjectCannotBeUpdated {
		
		String[] params = getDataList(requestData);
		if (Logger.doLog(3)) Logger.msg(3, "CreateAgentFromDescription: called by "+agent+" on "+item+" with parameters "+Arrays.toString(params));
		if (params.length < 3 || params.length > 4) 
			throw new InvalidDataException("CreateAgentFromDescription: Invalid parameters "+Arrays.toString(params));
		
		String newName = params[0];
		String descVer = params[1];
		String roles = params[2];
		PropertyArrayList initProps = 
				params.length > 3 ? getInitProperties(params[3]):new PropertyArrayList();
				
		Logger.msg(1, "CreateAgentFromDescription::request() - Starting.");

    	// check if given roles exist
    	String[] roleArr = roles.split(",");
    	for(int i=0; i<roleArr.length; i++) {
        	RolePath thisRole = Gateway.getLookup().getRolePath(roleArr[i]);
        }
    	
        // check if the path is already taken
    	try {
    		Gateway.getLookup().getAgentPath(newName);
    		throw new ObjectAlreadyExistsException("The agent name " +newName+ " exists already.");
    	} catch (ObjectNotFoundException ex) { }

        // generate new system key
        Logger.msg(6, "CreateAgentFromDescription - Requesting new agent path");
        AgentPath newAgentPath = new AgentPath(new ItemPath(), newName);

        // create the Item object
        Logger.msg(3, "CreateAgentFromDescription - Creating Agent");
        CorbaServer factory = Gateway.getCorbaServer();
        if (factory == null) throw new CannotManageException("This process cannot create new Items");
        ActiveEntity newAgent = factory.createAgent(newAgentPath);
        Gateway.getLookupManager().add(newAgentPath);

        // initialise it with its properties and workflow

        Logger.msg(3, "CreateAgentFromDescription - Initializing Agent");

        try {
			newAgent.initialise(
			    agent.getSystemKey(),
				Gateway.getMarshaller().marshall(getNewProperties(item, descVer, initProps, newName, agent)),
				Gateway.getMarshaller().marshall(getNewWorkflow(item, descVer)),
				Gateway.getMarshaller().marshall(getNewCollections(item, descVer))
				);
		} catch (PersistencyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			throw new InvalidDataException("CreateAgentFromDescription: Problem initializing new Agent. See log: "+e.getMessage());
		}
        
        // add roles if given
        
        for(int i=1; i<roleArr.length; i++) {
        	newAgent.addRole(roleArr[i]);
        }

        return requestData;
	}
}
