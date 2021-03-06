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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import org.apache.xerces.impl.dv.util.Base64;
import org.cristalise.kernel.common.ObjectNotFoundException;
import org.cristalise.kernel.common.SystemKey;
import org.cristalise.kernel.process.Gateway;



/**
* Extends Path to enforce SystemKey structure and support int form
*
* @version $Revision: 1.12 $ $Date: 2005/10/13 08:15:00 $
* @author  $Author: abranson $
**/
public class AgentPath extends ItemPath
{

	private String mAgentName=null;
	private String mPassword=null;

	public AgentPath(SystemKey syskey) throws InvalidAgentPathException, InvalidItemPathException {
		super(syskey);
		try {
			findAgentName();
		} catch (ObjectNotFoundException e) {
			throw new InvalidAgentPathException();
		}
	}
	protected AgentPath(UUID uuid) throws InvalidAgentPathException, InvalidItemPathException {
        super(uuid);
		try {
			findAgentName();
		} catch (ObjectNotFoundException e) {
			throw new InvalidAgentPathException();
		}        
    }
    
    public AgentPath(ItemPath itemPath) throws InvalidAgentPathException {
        super(itemPath.mUUID);
		try {
			findAgentName();
		} catch (ObjectNotFoundException e) {
			throw new InvalidAgentPathException();
		}
    }
    
    public AgentPath(ItemPath itemPath, String agentName) {
    	super(itemPath.mUUID);
    	mAgentName = agentName;
    }
    
    public AgentPath(String path) throws InvalidItemPathException {
    	super(path);
		try {
			findAgentName();
		} catch (ObjectNotFoundException e) {
			throw new InvalidAgentPathException();
		}
    }

    public void setAgentName(String agentID)
    {
    	mAgentName = agentID;
    }

    public String getAgentName()
    {
    	if (mAgentName==null)
			try {
				findAgentName();
			} catch (ObjectNotFoundException e) {
				return null;
			}
		return mAgentName;
    }
    
    private void findAgentName() throws ObjectNotFoundException {
		mAgentName = Gateway.getLookup().getAgentName(this);
    }

    public RolePath[] getRoles()
    {
  		return Gateway.getLookup().getRoles(this);
    }

    public boolean hasRole(RolePath role) {
        return Gateway.getLookup().hasRole(this, role);
    }

    public boolean hasRole(String role) {
        try {
            return hasRole(Gateway.getLookup().getRolePath(role));
        } catch (ObjectNotFoundException ex) {
            return false;
        }
    }

    public void setPassword(String passwd)
    {
 		mPassword = passwd;
    }

    public String getPassword()
    {
    	return mPassword;
    }

    @Override
	public String dump() {
        return super.dump()+
                "\n        agentID="+
                mAgentName;
    }

    public static String generateUserPassword(String pass, String algo) throws NoSuchAlgorithmException {
        MessageDigest sha = MessageDigest.getInstance(algo);
        sha.reset();
        sha.update(pass.getBytes());
        byte hash[] = sha.digest();
        StringBuffer digest = new StringBuffer("{").append(algo).append("}");
        digest.append(Base64.encode(hash));
        return digest.toString();
    }
    
    public static AgentPath fromUUIDString(String uuid) throws InvalidAgentPathException {
    	try {
    		return new AgentPath(new ItemPath(uuid));
    	} catch (InvalidItemPathException ex) {
    		throw new InvalidAgentPathException(ex.getMessage());
    	}
    }

}

