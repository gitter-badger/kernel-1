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
package org.cristalise.kernel.lifecycle.instance.predefined.item;

import org.cristalise.kernel.lifecycle.instance.predefined.PredefinedStepContainer;


/**************************************************************************
 *
 * $Revision: 1.2 $
 * $Date: 2005/06/02 10:19:33 $
 *
 * Copyright (C) 2003 CERN - European Organization for Nuclear Research
 * All rights reserved.
 **************************************************************************/

public class ItemPredefinedStepContainer extends PredefinedStepContainer {


    @Override
	public void createChildren()
    {
        super.createChildren();
        predInit("CreateItemFromDescription", "Create a new item using this item as its description", new CreateItemFromDescription());
        predInit("Erase", "Deletes all objects and domain paths for this item.", new Erase());
    }
}
