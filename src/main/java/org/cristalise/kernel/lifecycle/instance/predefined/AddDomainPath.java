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
package org.cristalise.kernel.lifecycle.instance.predefined;




import java.util.Arrays;

import org.cristalise.kernel.common.CannotManageException;
import org.cristalise.kernel.common.InvalidDataException;
import org.cristalise.kernel.common.ObjectAlreadyExistsException;
import org.cristalise.kernel.common.ObjectCannotBeUpdated;
import org.cristalise.kernel.lookup.AgentPath;
import org.cristalise.kernel.lookup.DomainPath;
import org.cristalise.kernel.lookup.ItemPath;
import org.cristalise.kernel.lookup.LookupManager;
import org.cristalise.kernel.process.Gateway;
import org.cristalise.kernel.utils.Logger;


public class AddDomainPath extends PredefinedStep
{
    public AddDomainPath()
    {
        super();
    }

	//requestdata is xmlstring
    @Override
	protected String runActivityLogic(AgentPath agent, ItemPath item,
			int transitionID, String requestData) throws InvalidDataException, ObjectCannotBeUpdated, ObjectAlreadyExistsException, CannotManageException {
    	
    	String[] params = getDataList(requestData);
        if (Logger.doLog(3)) Logger.msg(3, "AddDomainPath: called by "+agent+" on "+item+" with parameters "+Arrays.toString(params));
        if (params.length != 1) throw new InvalidDataException("AddDomainPath: Invalid parameters "+Arrays.toString(params));

        LookupManager lookupManager = Gateway.getLookupManager();

        DomainPath domainPath = new DomainPath(params[0], item);
        lookupManager.add(domainPath);
        return requestData;
    }
}
