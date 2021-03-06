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
package org.cristalise.kernel.lookup;

import java.util.Iterator;

import org.cristalise.kernel.common.CannotManageException;
import org.cristalise.kernel.common.ObjectCannotBeUpdated;
import org.cristalise.kernel.common.ObjectNotFoundException;
import org.cristalise.kernel.process.Gateway;




/**
* @version $Revision: 1.7 $ $Date: 2005/04/26 06:48:12 $
* @author  $Author: abranson $
**/
public class RolePath extends DomainPath
{
    /**
     *
     */

    private boolean hasJobList = false;
    
    public RolePath() {
    	super("agent");
    }

    @Override
	public RolePath getParent() {
    	try {
    		if (mPath.length > 2) 
    			return Gateway.getLookup().getRolePath(mPath[mPath.length-2]);
    	} catch (ObjectNotFoundException ex) { }
    	return null;
	}

	public RolePath(RolePath parent, String roleName) {
        super(parent, roleName);
    }
    
    public RolePath(String[] path, boolean jobList) {
        super(path);
        hasJobList = jobList;
    }

    public RolePath(RolePath parent, String roleName, boolean jobList) {
        this(parent, roleName);
        hasJobList = jobList;
    }

    /**
     * @return Returns the hasJobList.
     */
    public boolean hasJobList() {
        return hasJobList;
    }
    /**
     * @param hasJobList The hasJobList to set.
     * @throws ObjectCannotBeUpdated 
     * @throws ObjectNotFoundException 
     * @throws CannotManageException 
     */
    public void setHasJobList(boolean hasJobList) throws ObjectNotFoundException, ObjectCannotBeUpdated, CannotManageException {
        this.hasJobList = hasJobList;
    }


    @Override
	protected void checkType() {
        mType = CONTEXT;
    }
    
    public Iterator<Path> getChildren() {
    	return Gateway.getLookup().getChildren(this);
    }

    @Override
	public String dump() {
        StringBuffer comp = new StringBuffer("Components: { ");
        for (String element : mPath)
			comp.append("'").append(element).append("' ");

        return "Path - dump(): "+
                comp.toString()+
                "}\n        string="+
                toString()+
                "\n        type="+
                mType+
                "\n        name="+
                getName()+
                "\n        ";
    }

}

