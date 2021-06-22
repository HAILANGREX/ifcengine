/*******************************************************************************
*                                                                              *
* This file is part of IfcOpenShell.                                           *
*                                                                              *
* IfcOpenShell is free software: you can redistribute it and/or modify         *
* it under the terms of the Lesser GNU General Public License as published by  *
* the Free Software Foundation, either version 3.0 of the License, or          *
* (at your option) any later version.                                          *
*                                                                              *
* IfcOpenShell is distributed in the hope that it will be useful,              *
* but WITHOUT ANY WARRANTY; without even the implied warranty of               *
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the                 *
* Lesser GNU General Public License for more details.                          *
*                                                                              *
* You should have received a copy of the Lesser GNU General Public License     *
* along with this program. If not, see <http://www.gnu.org/licenses/>.         *
*                                                                              *
********************************************************************************/

package com.boswinner.ifcengine.geometry;

/******************************************************************************
 * Copyright (C) 2009-2018  BIMserver.org
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see {@literal<http://www.gnu.org/licenses/>}.
 *****************************************************************************/

import org.bimserver.plugins.renderengine.RenderEngineException;
import org.bimserver.plugins.renderengine.RenderEngineGeometry;
import org.bimserver.plugins.renderengine.RenderEngineInstance;

public class IfcOpenShellEntityInstance implements RenderEngineInstance {
	private IfcMeshEntity entity;

	public IfcOpenShellEntityInstance(IfcMeshEntity entity) {
		this.entity = entity;
	}

	@Override
	public double[] getTransformationMatrix() {
		return entity.getMatrix();
	}

	@Override
	public RenderEngineGeometry generateGeometry() {
		if (entity == null) {
			return null;
		}
		return new RenderEngineGeometry(entity.getIndices(), entity.getPositions(), entity.getNormals(), entity.getColors(), entity.getMaterialIndices());
	}

	@Override
	public double getArea() throws RenderEngineException {
		if (entity.getType().equalsIgnoreCase("IfcSpace")) {
			return entity.getExtendedDataAsFloat("WALKABLE_SURFACE_AREA");
		} else {
			return entity.getExtendedDataAsFloat("TOTAL_SURFACE_AREA");
		}
	}
	
	@Override
	public double getVolume() throws RenderEngineException {
		return entity.getExtendedDataAsFloat("TOTAL_SHAPE_VOLUME");
	}

	public IfcMeshEntity getEntity() {
		return entity;
	}
}
