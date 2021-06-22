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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bimserver.geometry.Matrix;
import org.bimserver.plugins.renderengine.RenderEngineException;

public class IfcMeshEntity {
	private int id;
	private String guid;
	private String name;
	private String type;
	private int parentId;
	private double[] matrix;
	private int repId;
	private float[] positions;
	private float[] normals;
	private int[] indices;
	private float[] colors;
	private int[] materialIndices;
	private JsonObject extendedData;

	public void setId(int id) {
		this.id = id;
	}

	public void setGuid(String guid) {
		this.guid = guid;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setType(String type) {
		this.type = type;
	}

	public void setParentId(int parentId) {
		this.parentId = parentId;
	}

	public void setMatrix(double[] matrix) {
		this.matrix = matrix;
	}

	public void setRepId(int repId) {
		this.repId = repId;
	}

	public void setPositions(float[] positions) {
		this.positions = positions;
	}

	public void setNormals(float[] normals) {
		this.normals = normals;
	}

	public void setIndices(int[] indices) {
		this.indices = indices;
	}

	public void setColors(float[] colors) {
		this.colors = colors;
	}

	public void setMaterialIndices(int[] materialIndices) {
		this.materialIndices = materialIndices;
	}

	public void setExtendedData(JsonObject extendedData) {
		this.extendedData = extendedData;
	}


	public IfcMeshEntity(int id, String guid, String name,
			String type, int parentId, double[] matrix, int repId,
			float[] positions, float[] normals, int[] indices, float[] colors,
			int[] materialIndices, String messageRemainder) {
		super();
		this.id = id;
		this.guid = guid;
		this.name = name;
		this.type = type;
		this.parentId = parentId;
		this.matrix = Matrix.changeOrientation(matrix);
		this.repId = repId;
		this.positions = positions;
		this.normals = normals;
		this.indices = indices;
		this.colors = colors;
		this.materialIndices = materialIndices;
		
		this.extendedData = null;
		if (messageRemainder != null && messageRemainder.length() > 0) {
			// un-pad string
			this.extendedData = new JsonParser().parse(messageRemainder.trim()).getAsJsonObject();
		}
	}
	private String intArr2String(int[] arr) {
		if (arr == null || arr.length == 0) {
			return "";
		}
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < arr.length; i++) {
			sb.append(i == 0 ? "[" : "")
					.append(arr[i])
					.append(i != arr.length - 1 ? "," : "]");
		}
		return sb.toString();
	}
	private String floatArr2String(float[] arr) {
		if (arr == null || arr.length == 0) {
			return "";
		}
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < arr.length; i++) {
			sb.append(i == 0 ? "[" : "")
					.append(String.valueOf(arr[i]))
					.append(i != arr.length - 1 ? "," : "]");
		}
		return sb.toString();
	}
	public int geoHashCode() {
		StringBuffer sb = new StringBuffer();
		sb.append(this.floatArr2String(this.positions) + ",")
				.append(this.floatArr2String(this.normals) + ",")
				.append(this.intArr2String(this.indices) + ",")
				.append(this.floatArr2String(this.colors) + ",")
				.append(this.intArr2String(this.materialIndices));
		return sb.toString().hashCode();
	}

	public IfcMeshEntity(){}
	
	public int getId() {
		return id;
	}

	public String getGuid() {
		return guid;
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

	public int getParentId() {
		return parentId;
	}

	public double[] getMatrix() {
		return matrix;
	}

	public int getRepId() {
		return repId;
	}

	public float[] getPositions() {
		return positions;
	}

	public float[] getNormals() {
		return normals;
	}

	public int[] getIndices() {
		return indices;
	}

	public float[] getColors() {
		return colors;
	}

	public int[] getMaterialIndices() {
		return materialIndices;
	}
	
	public int getNumberOfPrimitives() {
		return indices.length / 3;
	}
	
	public int getNumberOfColors() {
		return colors.length / 4;
	}
	
	public float getExtendedDataAsFloat(String name) throws RenderEngineException {
		if (this.extendedData == null) {
			throw new RenderEngineException("No extended data for Entity " + this.guid);
		}
		JsonElement elem = extendedData.get(name);
		if (elem == null) {
			throw new RenderEngineException("No extended data entry found for " + name);
		}
		return elem.getAsFloat();
	}
}