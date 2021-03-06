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


/**
 * Objects that are to be stored by Cristal Entities must implement this interface and be (un)marshallable by Castor
 * i.e. have a map file properly registered in the kernel. Domain implementors should not create new C2KLocalObjects
 * <p>Each object will be stored as the path /clustertype/name in most cases. Exceptions are:
 * <ul>
 * <li>Collections - /Collection/Name/Version (default 'last')
 * <li>Outcomes - /Outcome/SchemaType/SchemaVersion/EventId
 * <li>Viewpoints - /ViewPoint/SchemaType/Name
 * </ul>
 *
 * @see org.cristalise.kernel.persistency.ClusterStorage
 * @see org.cristalise.kernel.persistency.ClusterStorageManager
 *
 * @author Andrew Branson
 *
 * $Revision: 1.5 $
 * $Date: 2004/01/22 11:10:41 $
 *
 * Copyright (C) 2003 CERN - European Organization for Nuclear Research
 * All rights reserved.
 */

public interface C2KLocalObject {

	public void setName(String name);
	public String getName();

	public String getClusterType();
}
