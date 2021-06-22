package com.boswinner.largeproduct;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.bimserver.plugins.renderengine.RenderEngineException;
import org.json.simple.JSONObject;

import com.boswinner.largeproduct.geometry.IfcGeoParser;
import com.boswinner.largeproduct.geometry.IfcMeshEntity;
import com.boswinner.largeproduct.geometry.IfcParallelGeoParser;
import com.koloboke.collect.map.hash.HashObjObjMaps;

public class IfcFile {
	//IFCPROPERTYSET
	private final int EXPECTED_NUMBER_OF_FILE = 10;
	
	//private String path = "";
	private String header = "";
	private Map<String, String> data = HashObjObjMaps.<String, String>newUpdatableMap();//!!!!!!!!!!!!!!!!!new HashMap<String, String>(); // line:content的Map数据//
	
	// ifctype到line的Map；很重要，需要根据IfcType切分几何、属性获取、关系获取等
	private Map<String, List<String>> mapType2Line = new HashMap<String, List<String>>();
	
	private Properties props=new Properties();
	private String temp_folder;
	
	public IfcFile(InputStream in) throws IOException{
		init("/ifcengine.properties");
        initByInputStream(in);
	}

	public IfcFile(String path) throws IOException{
		init("/ifcengine.properties");
        InputStream in = new FileInputStream(path);
        initByInputStream(in);
        
	}
	
	private void init(String configLocation) {
		InputStream inStream=this.getClass().getResourceAsStream(configLocation);
		try{
			props.load(inStream);
			this.temp_folder=props.getProperty("ifcengine.temp.folder");
		}catch(IOException ex){
			//logger.error("Failed to load ["+configLocation+"]");
		}
	}
	
	/**
	 * 根据行号获取guid
	 * @param line	行号
	 * @return guid
	 */
	public String getGuidByLine(String line){
		if(!data.containsKey(line)){	// 不存在，理论上throws NoFoundException较好 TODO
			return "";
		}
		
		//IfcInstance ii = data.get(line);
		String possible_guid = getIfcPropertiesByLineData(data.get(line)).get(0); //ii.getProperties().get(0); // 一般第一个属性是guid
		if(possible_guid.startsWith("'") && possible_guid.endsWith("'")){	// guid由''包围
			return possible_guid.substring(1, possible_guid.length() - 1);
		}
		
		return ""; // 不存在guid值 TODO
	}
	
	/**
	 * 根据行号获取ifc type
	 * @param line	行号
	 * @return ifc type
	 */
	public String getTypeByLine(String line){
		if(!data.containsKey(line)){	// 不存在，理论上throws NoFoundException较好 TODO
			return "";
		}
		
		//IfcInstance ii = data.get(line);
		return this.getIfcTypeByLineData(data.get(line));
		//return ii.ifcType;
	}
	public List<String> getPropertiesByLine(String line){
		if(!data.containsKey(line)){	// 不存在，理论上throws NoFoundException较好 TODO
			return null;
		}

		return this.getIfcPropertiesByLineData(data.get(line));
	}

	/**
	 * 根据行号获取构件名称
	 * @param line	行号
	 * @return 构件名称
	 */
	public String getNameByLine(String line){
		if(!data.containsKey(line)){	// 不存在，理论上throws NoFoundException较好 TODO
			return "";
		}
		
		//IfcInstance ii = data.get(line);
		String possible_name = getIfcPropertiesByLineData(data.get(line)).get(2);//ii.getProperties().get(2); // 一般第一个属性是guid
		//if(possible_guid.startsWith("'") && possible_guid.endsWith("'")){	// guid由''包围
		//	return possible_guid.substring(1, possible_guid.length() - 1);
		//}
		
		return IfcStringDecoder.decode(possible_name); // 不存在guid值 TODO
	}

	/**
	 * 根据行号获取构件名称
	 * @param line	行号
	 * @return 构件名称
	 */
	public String getDescriptionByLine(String line){
		if(!data.containsKey(line)){	// 不存在，理论上throws NoFoundException较好 TODO
			return "";
		}
		
		//IfcInstance ii = data.get(line);
		String possible_desc = getIfcPropertiesByLineData(data.get(line)).get(3);//ii.getProperties().get(3); // 一般第一个属性是guid
		//if(possible_guid.startsWith("'") && possible_guid.endsWith("'")){	// guid由''包围
		//	return possible_guid.substring(1, possible_guid.length() - 1);
		//}
		
		return IfcStringDecoder.decode(possible_desc); // 不存在guid值 TODO
	}
	
	//////////////////////////////////////////////////////////////////////////////////////////
	/// IFC 关系处理
	/////////////////////////////////////////////////////////////////////////////////////////
	public Map<String, List<String>> getAggregates(){
		Map<String, List<String>> mapAggregates = new HashMap<String, List<String>>();
		if(!mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELAGGREGATES)){	// 不存在
			return mapAggregates;
		}
		for(String line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELAGGREGATES)){
			//IfcInstance ii = data.get(line);
			List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));
			String key = ifcProperties.get(4);//ii.getProperties().get(4);
			String rawAgg = ifcProperties.get(5).substring(1, ifcProperties.get(5).length() - 1); // 去掉左右括号
	    	String[] aggs = rawAgg.split(",");
	    	List<String> lstTmp = new ArrayList<String>();
	    	for(String agg : aggs){
	    		lstTmp.add(agg.trim());
	    	}
			mapAggregates.put(key, lstTmp);
		}
		
		return mapAggregates;
	}
	
	public Map<String, List<String>> getContains(){
		Map<String, List<String>> mapContains = new HashMap<String, List<String>>();
		if(!mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELCONTAINEDINSPATIALSTRUCTURE)){	// 不存在
			return mapContains;
		}
		for(String line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELCONTAINEDINSPATIALSTRUCTURE)){
			List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));//IfcInstance ii = data.get(line);
			String key = ifcProperties.get(5);
			String rawCon = ifcProperties.get(4).substring(1, ifcProperties.get(4).length() - 1); // 去掉左右括号
	    	String[] cons = rawCon.split(",");
	    	List<String> lstTmp = new ArrayList<String>();
	    	for(String con : cons){
	    		lstTmp.add(con.trim());
	    	}
	    	mapContains.put(key, lstTmp);
		}
		
		return mapContains;
	}
	
	/**
	 * 获取空间上的父构件映射
	 * @return
	 */
	public Map<String, String> getSpatialParent(){
		Map<String, String> mapParent = new HashMap<String,String>();
		if(mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELAGGREGATES)){	// Aggregates关系
			for(String line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELAGGREGATES)){
				List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));//IfcInstance ii = data.get(line);
				String key = ifcProperties.get(4);
				String rawAgg = ifcProperties.get(5).substring(1, ifcProperties.get(5).length() - 1); // 去掉左右括号
		    	String[] aggs = rawAgg.split(",");
		    	for(String agg : aggs){
		    		mapParent.put(agg.trim(),key);
		    	}
			}
		}

		if(mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELCONTAINEDINSPATIALSTRUCTURE)){	// Contains 关系
			for(String line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELCONTAINEDINSPATIALSTRUCTURE)){
				List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));//IfcInstance ii = data.get(line);
				String key = ifcProperties.get(5);
				String rawCon = ifcProperties.get(4).substring(1, ifcProperties.get(4).length() - 1); // 去掉左右括号
		    	String[] cons = rawCon.split(",");
		    	for(String con : cons){
		    		mapParent.put(con.trim(), key);
		    	}
			}
		}
		
		return mapParent;
	}
	

	//////////////////////////////////////////////////////////////////////////////////////////
	/// IFC 属性处理
	/////////////////////////////////////////////////////////////////////////////////////////

    /**
     * 将ifc属性组织到内存中等待外部使用
     */
    public Map<String, Object> getAttributes() {
    	Map<String, List<String>> mapIns2Properties = getRelDefProperties();
        if (mapIns2Properties == null || mapIns2Properties.size() == 0) {
            return null;
        }
        
        Map<String, Object> mapIns2Attr = new HashMap<String, Object>();
        for (String e : mapIns2Properties.keySet()) {
            JSONObject attrDataMap = new JSONObject();
            for (String setLineNum : mapIns2Properties.get(e)) {
            	//IfcInstance ifcIns = data.get(setLineNum);
            	String ifcType = this.getIfcTypeByLineData(data.get(setLineNum));
                Map<String, Object> temp = null;
                if (IfcPropertyType.IFC_TYPE_IFCELEMENTQUANTITY.equals(ifcType)) {
                    temp = findIfcPropertySet(data.get(setLineNum));
                }else if(IfcPropertyType.IFC_TYPE_IFCPROPERTYSET.equals(ifcType)){
                    temp = findIfcPropertySet(data.get(setLineNum));
                }else if (ifcType.endsWith("TYPE")) {public Map<String, String>  getGeometrySlicesInString_TypePref()
                    temp = findIfcPropertyByTypeSet(data.get(setLineNum));
                }else{
                	// TODO: why??
                    temp = findIfcPropertySet(data.get(setLineNum));
                }

                if (temp != null) {
                    attrDataMap.putAll(temp);
                }
            }
            
            mapIns2Attr.put(e, attrDataMap);
        }
        
        return mapIns2Attr;
    }
	
    /**
     * 获取构件对应的IFCPROPERTYSET的行号
     *
     * @param lineString
     */
    private Map<String, List<String>> getRelDefProperties() {
    	Map<String, List<String>> mapIns2Properties = new HashMap<String, List<String>>();
    	
		if(mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELDEFINESBYPROPERTIES)){	// IFCRELDEFINESBYPROPERTIES 关系
			for(String line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELDEFINESBYPROPERTIES)){
				List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));//IfcInstance ii = data.get(line);
	        	String property = ifcProperties.get(5);
	        	String rawIns = ifcProperties.get(4);
	        	if(rawIns.length() > 2){
	        		rawIns = rawIns.substring(1, rawIns.length() - 1);
	        		String[] vals = rawIns.split(","); 
	        		for(String val : vals){
	        			String ins = val.trim();
	        			if(!mapIns2Properties.containsKey(ins)){
	                    	ArrayList<String> temp = new ArrayList<String>();
	                    	mapIns2Properties.put(ins, temp);
	        			}
	        			
	        			mapIns2Properties.get(ins).add(property);
	        		}
	        	}
			}
		}
		
		if(mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELDEFINESBYTYPE)){	// IFCRELDEFINESBYTYPE 关系
			for(String line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELDEFINESBYTYPE)){
				List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));//IfcInstance ii = data.get(line);
	        	String property = ifcProperties.get(5);
	        	String rawIns = ifcProperties.get(4);
	        	if(rawIns.length() > 2){
	        		rawIns = rawIns.substring(1, rawIns.length() - 1);
	        		String[] vals = rawIns.split(","); 
	        		for(String val : vals){
	        			String ins = val.trim();
	        			if(!mapIns2Properties.containsKey(ins)){
	                    	ArrayList<String> temp = new ArrayList<String>();
	                    	mapIns2Properties.put(ins, temp);
	        			}
	        			
	        			mapIns2Properties.get(ins).add(property);
	        		}
	        	}
			}
		}
		
		return mapIns2Properties;
    }

    
    /**
     * 获取ifc属性集
     *
     * @param lineString
     * @return
     */
    private Map<String, Object> findIfcPropertySet(String line) {
        //if (ifcIns == null){// || !IFC_TYPE_IFCPROPERTYSET.equals(ifcIns.ifcType)) {
        //    return null;
        //}
    	List<String> ifcProperties = this.getIfcPropertiesByLineData(line);
        Map<String, String> propertyValue = new HashMap<String, String>();
        String strSetRawName =  ifcProperties.get(2);
        String propertySetName = IfcStringDecoder.decode(strSetRawName);

        //('33RdwGpa54sAOG05AQJQW8',#29,'Pset_WindowCommon',$,(#5004,#5009))获取对应的单个属性值的行号
        String insProperties = ifcProperties.get(4);
        if (insProperties.length() > 1) {	// 去掉左右括号
        	if(insProperties.charAt(0) == '('){
        		insProperties = insProperties.substring(1);
        		if(insProperties.endsWith(")")){
        			insProperties = insProperties.substring(0, insProperties.length() - 1);
        		}
        	}
            //获取单属性数组
            String[] ret = insProperties.split(",");
            for (String e : ret) {
                Map<String, String> mapTmp = getIfcSingleValue(data.get(e.trim()));
                if (mapTmp == null) {
                    continue;
                }
                propertyValue.putAll(mapTmp);
            }
        }
        Map<String, Object> setMap = new HashMap<String, Object>();
        setMap.put(propertySetName, propertyValue);
        return setMap;
    }


    /**
     * 获取ifcType对应的属性集
     *
     * @param lineString
     * @return
     */
    private Map<String, Object> findIfcPropertyByTypeSet(String line) {
    	String ifcType = this.getIfcTypeByLineData(line);
        if (!ifcType.endsWith("TYPE(")) {
            return null;
        }
        
    	List<String> ifcProperties = this.getIfcPropertiesByLineData(line);

        Map<String, Object> singleValueMap = new HashMap<String, Object>();

        String strRawPropertySets = ifcProperties.get(5);
        if(strRawPropertySets.startsWith("(") && strRawPropertySets.endsWith(")")){
        	String[] propertySets = strRawPropertySets.substring(1, strRawPropertySets.length() - 1).split(",");

            for (String propertySet : propertySets) {
                Map<String, Object> temp = findIfcPropertySet(data.get(propertySet.trim()));
                if (temp != null) {
                    singleValueMap.putAll(temp);
                }
            }
        }

        return singleValueMap;
    }

    /**
     * 获取单个ElementQuantity的属性值
     *
     * @param dataLine
     * @return
     */
    private Map<String, String> getIfcQuantityValue(String line) {
        if (line == null ) {
            return null;
        }
        
        List<String> ifcProperties = this.getIfcPropertiesByLineData(line);
        if (ifcProperties.size() != 4) {
            return null;
        }

        Map<String, String> mapTmp = new HashMap<String, String>();
        String name = IfcStringDecoder.decode(ifcProperties.get(0));
        String value = ifcProperties.get(3);
        mapTmp.put(name, value);
        return mapTmp;
    }


    /**
     * 获取ifc单个属性
     *
     * @param dataLine
     * @return
     */
    private Map<String, String> getIfcSingleValue(String line) {
        if (line == null ) {
            return null;
        }
        
        List<String> ifcProperties = this.getIfcPropertiesByLineData(line);
        if (ifcProperties.size() != 4) {
            return null;
        }

        Map<String, String> mapTmp = new HashMap<String, String>();
        String name = IfcStringDecoder.decode(ifcProperties.get(0));
        String value = propertyValue(ifcProperties.get(2));
        mapTmp.put(name, value);
        return mapTmp;
    }

    /**
     * 将属性值关联单位
     *
     * @param dataLine
     * @return
     */
    private String propertyValue(String propertyVal) {
        if (propertyVal == null || propertyVal.equals("")) {
            return null;
        }

        String valueType = propertyVal.split("\\(")[0];
        //TODO: 提取ifc单位
        switch (valueType) {
            case IfcPropertyType.VOLUME:
                return getValueString(propertyVal);

            case IfcPropertyType.LENGTH:
                return getValueString(propertyVal);

            case IfcPropertyType.AREA:
                return getValueString(propertyVal);

            case IfcPropertyType.INTEGER:
                return getValueString(propertyVal);

            case IfcPropertyType.LOGICAL:
                return getValueString(propertyVal);

            case IfcPropertyType.REAL:
                return getValueString(propertyVal);

            case IfcPropertyType.THERMALTRANSMITTANCE:
                return getValueString(propertyVal);

            case IfcPropertyType.BOOLEAN:
                return getValueString(propertyVal);

            default:
                return getValueString(propertyVal);
        }
    }

    private String getValueString(String propertyVal) {
        if (propertyVal == null || propertyVal.length() == 0) {
            return "";
        }

        String ret = "";
        boolean isNum = false;
        for (int i = 0; i < propertyVal.length(); i++) {
            if (propertyVal.charAt(i) == ')') {
                isNum = false;
            }
            if (isNum) {
                ret += propertyVal.charAt(i);
            }
            if (propertyVal.charAt(i) == '(') {
                isNum = true;
            }
        }
        ret = ret.replace("'", "");
        return IfcStringDecoder.decode(ret);
    }

	//////////////////////////////////////////////////////////////////////////////////////////
	/// IFC 几何处理
	/////////////////////////////////////////////////////////////////////////////////////////

	long totalSize = 0;
	 public List<String> getGeometrySlices(){
//			long Time1=System.currentTimeMillis();
	    	List<String> lstGeometrySlices = new ArrayList<String>();
	    	LinkedHashSet<String> lstPossibleGeometryInstances = getPossibleGeometryInstances();  //获取contains和Aggregates
//			long Time2=System.currentTimeMillis();
//			System.out.println("获取contains和Aggregates的时间为：  "+(Time2-Time1)/(double)1000 );
			Map<String, List<String>> mapRep2Ins = new HashMap<String, List<String>>();
	    	for(String possibleIns : lstPossibleGeometryInstances){
	    		String ifcType = this.getTypeByLine(possibleIns);
	    		if(ifcType == ""       )
	    			continue;
	    		if(IfcPropertyType.IFC_TYPE_IFCPROJECT.equals(ifcType)) // IFCProject没有形状
					continue;
	    		List<String> ifcProperties = this.getPropertiesByLine(possibleIns);
	    		if(ifcProperties.size() < 7)
	    			continue;
	    		
	    		String rep = ifcProperties.get(6); 	// 获取IfcRepresentation
	    		if(rep.length() < 2)	// IfcRepresentation以#开头，长度必然不小于2；长度小于2，要么为空，要么为$；
	    			continue;
	    		
	    		if(!mapRep2Ins.containsKey(rep)){
	    			List<String> lstTmp = new ArrayList<String>();
	    			mapRep2Ins.put(rep, lstTmp);
	    		}
	    		mapRep2Ins.get(rep).add(possibleIns);
	    	}
//			long Time3=System.currentTimeMillis();
//			System.out.println("获取IfcRepresentation的时间为：  "+(Time3-Time2)/(double)1000 );
	    	// 获取每个IFCRepresentation的图
	    	Map<String, LinkedHashSet<String>> mapItems = new HashMap<String, LinkedHashSet<String>>();
			Map<String, Boolean> mapVisited =HashObjObjMaps.<String, Boolean>newUpdatableMap();//!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!new HashMap<String, Boolean>();// 
	    	//for(String rep : mapRep2Ins.keySet()){
	    	//	mapVisited.put(rep, true);
	    	//}
	    	if(mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM)){
	    		for(String insStyledItem : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM)){
	    			mapVisited.put(insStyledItem, true);
	    		}
	    	}
	    	if(mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION)){
	    		for(String insMatDefRep : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION)){
	    			mapVisited.put(insMatDefRep, true);
	    		}
	    	}
//			System.out.println("类型判断完成  ");
	    	retrieveStyledItemMap(mapItems, mapVisited);
//	    	System.out.println("retrieveStyledItemMap完成  ");
	    	retrieveMaterialRepDef(mapItems, mapVisited);
//	    	System.out.println("retrieveMaterialRepDef完成  ");
	    	retrieveRelVoidElement(mapItems, mapVisited);
//	    	System.out.println("retrieveRelVoidElement完成  ");
	    	
	    	for(String rep : mapRep2Ins.keySet()){
				retrieveSubinstances(rep, mapItems, mapVisited);
	    	}
//	    	System.out.println("retrieveRelVoidElement完成  ");
	    	List<String> lstReps = new ArrayList<String>();
	    	lstReps.addAll(mapRep2Ins.keySet()); 
	    	Map<String, Set<String>> mapGeometrySlices = new HashMap<String, Set<String>>();
//	        System.out.println("类型判断完成2  ");

			for(String m:lstReps){
				Set<String> projecthead = this.getProjectHead();
				Set<String> setTmp = getGeometryDescriptionSlices(m, mapRep2Ins.get(m), mapItems, mapVisited);  //生成每个构件的子文件
	    		projecthead.addAll(setTmp);
				mapGeometrySlices.put(m, projecthead);
	    		totalSize += setTmp.size();
			}

//			long Time4=System.currentTimeMillis();
//			System.out.println("生成每个构件IFCRepresentation图的时间为：  "+(Time4-Time3)/(double)1000 );
	    	if(mapGeometrySlices.size() == 0){
	    		// TODO: 切分不出来？？直接返回原文件
	    		//return 
	    	}

	    	Map<String, String> mapIns2Rep = new HashMap<String, String>(); // 构件 -> 表现
	    	for(String rep : mapRep2Ins.keySet()){
	    		for(String ins : mapRep2Ins.get(rep)){
	    			mapIns2Rep.put(ins, rep);
	    		}
	    	}
	    	Map<Set<String>, Set<String>> mapConnIns = getRelConnPathSets();
	    	String rep_conn;
			Set<String> connRep = new HashSet<String>();

	    	for(Set<String> connIns : mapConnIns.keySet()){
	    		for(String ins : connIns){
	    			if(mapIns2Rep.containsKey(ins)){	// 有些构件没有3D shape，神奇。
	    				connRep.add(mapIns2Rep.get(ins));
	    			}
	    		}

	    		// 生成合并后的几何对象
	    		rep_conn = String.valueOf(connIns.hashCode());
	    		Set<String> connGeometry = new HashSet<String>();
	    		for(String rep : connRep){
	    			connGeometry.addAll(mapGeometrySlices.get(rep));
	    			mapGeometrySlices.remove(rep);
	    		}

	    		// 需要加上IFCRELCONNECTSPATHELEMENTS实例
	    		connGeometry.addAll(mapConnIns.get(connIns));

	    		mapGeometrySlices.put(rep_conn, connGeometry);

	    		connRep.clear();
	    	}
//			long Time5=System.currentTimeMillis();
//			System.out.println("优化合并mapGeometrySlices的时间为：  "+(Time5-Time4)/(double)1000 );
	    	
	    	long avgSize = totalSize / EXPECTED_NUMBER_OF_FILE;// 期望文件数
	    	long thSingleSize = (long)(avgSize * 0.8); // 单文件数 
	    	Set<String> comContents = new HashSet<String>(); 
	    	
	    	for(String rep : mapGeometrySlices.keySet()){
	    		//if()
	    		Set<String> contents = mapGeometrySlices.get(rep);
	    		if(contents.size() > thSingleSize){
	    			// 切分单个构件：New
	    			List<Set<String>> prdSegs = retrieveProductSegments(rep, mapRep2Ins, mapItems, (int)(thSingleSize * 10));
	    			for(Set<String> seg : prdSegs){
	    				String path = writeSetToFile_LargeProduct(seg, rep);
	    				lstGeometrySlices.add(path);
	    			}
	    		}else{
					comContents.addAll(contents);
					if(comContents.size() > avgSize){
						String path = writeSetToFile(comContents, rep);
		    			lstGeometrySlices.add(path);
		    			comContents.clear();
					}
	    		}
	    	}
//			long Time6=System.currentTimeMillis();
//			System.out.println("文件写出的时间为 ：  "+(Time6-Time5)/(double)1000 );

			if(comContents != null && comContents.size() > 0){
				String path = writeSetToFile(comContents, String.valueOf(comContents.hashCode()));
				lstGeometrySlices.add(path);
				comContents.clear();
	    	}
//			System.out.println("总拆分时间应为 ：  "+(Time6-Time1)/(double)1000 );
	    	//lstGeometrySlices.addAll(mapGeometrySlices.values());
	    	return lstGeometrySlices;
	    } 
	 
	 public Map<String, String>  getGeometrySlicesInString(){
//			long Time1=System.currentTimeMillis();
	    	List<String> lstGeometrySlices = new ArrayList<String>();
	    	LinkedHashSet<String> lstPossibleGeometryInstances = getPossibleGeometryInstances();  //获取contains和Aggregates
//			long Time2=System.currentTimeMillis();
//			System.out.println("获取contains和Aggregates的时间为：  "+(Time2-Time1)/(double)1000 );
			Map<String, List<String>> mapRep2Ins = new HashMap<String, List<String>>();
	    	for(String possibleIns : lstPossibleGeometryInstances){
	    		String ifcType = this.getTypeByLine(possibleIns);
	    		if(ifcType == ""       )
	    			continue;
	    		if(IfcPropertyType.IFC_TYPE_IFCPROJECT.equals(ifcType)) // IFCProject没有形状
					continue;
	    		List<String> ifcProperties = this.getPropertiesByLine(possibleIns);
	    		if(ifcProperties.size() < 7)
	    			continue;
	    		
	    		String rep = ifcProperties.get(6); 	// 获取IfcRepresentation
	    		if(rep.length() < 2)	// IfcRepresentation以#开头，长度必然不小于2；长度小于2，要么为空，要么为$；
	    			continue;
	    		
	    		if(!mapRep2Ins.containsKey(rep)){
	    			List<String> lstTmp = new ArrayList<String>();
	    			mapRep2Ins.put(rep, lstTmp);
	    		}
	    		mapRep2Ins.get(rep).add(possibleIns);
	    	}
//			long Time3=System.currentTimeMillis();
//			System.out.println("获取IfcRepresentation的时间为：  "+(Time3-Time2)/(double)1000 );
	    	// 获取每个IFCRepresentation的图
	    	Map<String, LinkedHashSet<String>> mapItems = new HashMap<String, LinkedHashSet<String>>();
			Map<String, Boolean> mapVisited =HashObjObjMaps.<String, Boolean>newUpdatableMap();//!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!new HashMap<String, Boolean>();// 
	    	//for(String rep : mapRep2Ins.keySet()){
	    	//	mapVisited.put(rep, true);
	    	//}
	    	if(mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM)){
	    		for(String insStyledItem : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM)){
	    			mapVisited.put(insStyledItem, true);
	    		}
	    	}
	    	if(mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION)){
	    		for(String insMatDefRep : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION)){
	    			mapVisited.put(insMatDefRep, true);
	    		}
	    	}
//			System.out.println("类型判断完成  ");
	    	retrieveStyledItemMap(mapItems, mapVisited);
//	    	System.out.println("retrieveStyledItemMap完成  ");
	    	retrieveMaterialRepDef(mapItems, mapVisited);
//	    	System.out.println("retrieveMaterialRepDef完成  ");
	    	retrieveRelVoidElement(mapItems, mapVisited);
//	    	System.out.println("retrieveRelVoidElement完成  ");
	    	
	    	for(String rep : mapRep2Ins.keySet()){
				retrieveSubinstances(rep, mapItems, mapVisited);
	    	}
//	    	System.out.println("retrieveRelVoidElement完成  ");
	    	List<String> lstReps = new ArrayList<String>();
	    	lstReps.addAll(mapRep2Ins.keySet()); 
	    	Map<String, Set<String>> mapGeometrySlices = new HashMap<String, Set<String>>();
//	        System.out.println("类型判断完成2  ");

			for(String m:lstReps){
				Set<String> projecthead = this.getProjectHead();
				Set<String> setTmp = getGeometryDescriptionSlices(m, mapRep2Ins.get(m), mapItems, mapVisited);  //生成每个构件的子文件
	    		projecthead.addAll(setTmp);
				mapGeometrySlices.put(m, projecthead);
	    		totalSize += setTmp.size();
			}

//			long Time4=System.currentTimeMillis();
//			System.out.println("生成每个构件IFCRepresentation图的时间为：  "+(Time4-Time3)/(double)1000 );
	    	if(mapGeometrySlices.size() == 0){
	    		// TODO: 切分不出来？？直接返回原文件
	    		//return 
	    	}

	    	Map<String, String> mapIns2Rep = new HashMap<String, String>(); // 构件 -> 表现
	    	for(String rep : mapRep2Ins.keySet()){
	    		for(String ins : mapRep2Ins.get(rep)){
	    			mapIns2Rep.put(ins, rep);
	    		}
	    	}
	    	Map<Set<String>, Set<String>> mapConnIns = getRelConnPathSets();
	    	String rep_conn;
			Set<String> connRep = new HashSet<String>();

	    	for(Set<String> connIns : mapConnIns.keySet()){
	    		for(String ins : connIns){
	    			if(mapIns2Rep.containsKey(ins)){	// 有些构件没有3D shape，神奇。
	    				connRep.add(mapIns2Rep.get(ins));
	    			}
	    		}

	    		// 生成合并后的几何对象
	    		rep_conn = String.valueOf(connIns.hashCode());
	    		Set<String> connGeometry = new HashSet<String>();
	    		for(String rep : connRep){
	    			connGeometry.addAll(mapGeometrySlices.get(rep));
	    			mapGeometrySlices.remove(rep);
	    		}

	    		// 需要加上IFCRELCONNECTSPATHELEMENTS实例
	    		connGeometry.addAll(mapConnIns.get(connIns));

	    		mapGeometrySlices.put(rep_conn, connGeometry);

	    		connRep.clear();
	    	}
//			long Time5=System.currentTimeMillis();
//			System.out.println("优化合并mapGeometrySlices的时间为：  "+(Time5-Time4)/(double)1000 );
	    	
	    	long avgSize = totalSize / EXPECTED_NUMBER_OF_FILE;// 期望文件数
	    	long thSingleSize = (long)(avgSize * 0.8); // 单文件数 
	    	Set<String> comContents = new HashSet<String>(); 

	    	int max_len = 0, max_size = 0, min_size = data.size(), 
	    			min_len = Integer.MAX_VALUE, total_size=0, _total_size = 0,
	    			final_size = 0, splits =0;
	    	Map<String, String> mapSlices = new HashMap<String, String>();
	    	for(String rep : mapGeometrySlices.keySet()){
	    		//if()
	    		Set<String> contents = mapGeometrySlices.get(rep);
	    		_total_size = _total_size + contents.size();
	    		if(contents.size() > thSingleSize){
		    		if(contents.size() > max_size)
		    			max_size = contents.size();
		    		if(contents.size() < min_size)
		    			min_size = contents.size();
		    		splits=splits+1;
		    		total_size = total_size+contents.size();
		    		
	    			StringBuilder sb = new StringBuilder();
	        		sb.append(getHeader4GeometrySlice());
	        		for(String writtenItem : mapGeometrySlices.get(rep)){
	        			sb.append(writtenItem);
	        			sb.append("=");
	        			sb.append(data.get(writtenItem));
	        			sb.append("\n");
	        		}
	        		sb.append(this.getEnd4GeometrySlice());
	        		mapSlices.put(rep, sb.toString());
	    		//	}
	    		}else{
					comContents.addAll(contents);
					if(comContents.size() > avgSize){

			    		splits=splits+1;
			    		total_size = total_size+comContents.size();
			    		if(comContents.size() > max_size)
			    			max_size = comContents.size();
			    		if(comContents.size() < min_size)
			    			min_size = comContents.size();
			    		
		    			StringBuilder sb = new StringBuilder();
		        		sb.append(getHeader4GeometrySlice());
		        		for(String writtenItem : comContents){
		        			sb.append(writtenItem);
		        			sb.append("=");
		        			sb.append(data.get(writtenItem));
		        			sb.append("\n");
		        		}
		        		sb.append(this.getEnd4GeometrySlice());
		        		mapSlices.put(rep, sb.toString());
		    			comContents.clear();
					}
	    		}
	    	}
//			long Time6=System.currentTimeMillis();
//			System.out.println("文件写出的时间为 ：  "+(Time6-Time5)/(double)1000 );

			if(comContents != null && comContents.size() > 0){
				final_size = comContents.size();
	    		total_size = total_size+comContents.size();
	    		splits=splits+1;
    			StringBuilder sb = new StringBuilder();
        		sb.append(getHeader4GeometrySlice());
        		for(String writtenItem : comContents){
        			sb.append(writtenItem);
        			sb.append("=");
        			sb.append(data.get(writtenItem));
        			sb.append("\n");
        		}
        		sb.append(this.getEnd4GeometrySlice());
        		mapSlices.put("lft", sb.toString());
				comContents.clear();
	    	}

	    	System.out.println("Max Size: " + max_size + ", Min Size: " + min_size
	    			+ ", Total Size: " + total_size + "(" + _total_size + ")" + ", Final Size:" + final_size  
	    			+ ", Splits: " + splits);
//			System.out.println("总拆分时间应为 ：  "+(Time6-Time1)/(double)1000 );
	    	//lstGeometrySlices.addAll(mapGeometrySlices.values());
	    	return mapSlices;
	    }
	 public Map<String, String>  getGeometrySlicesInString_TypePref(){
//			long Time1=System.currentTimeMillis();
	    	List<String> lstGeometrySlices = new ArrayList<String>();
	    	LinkedHashSet<String> lstPossibleGeometryInstances = getPossibleGeometryInstances();  //获取contains和Aggregates
//			long Time2=System.currentTimeMillis();
//			System.out.println("获取contains和Aggregates的时间为：  "+(Time2-Time1)/(double)1000 );
			Map<String, List<String>> mapRep2Ins = new HashMap<String, List<String>>();
	        Map<String, Set<String>> mapProdTypeToRep = new HashMap<String, Set<String>>();
	        Map<String, String> mapRepToProdType = new HashMap<String, String>();
	    	for(String possibleIns : lstPossibleGeometryInstances){
	    		String ifcType = this.getTypeByLine(possibleIns);
	    		if(ifcType == ""       )
	    			continue;
	    		if(IfcPropertyType.IFC_TYPE_IFCPROJECT.equals(ifcType)) // IFCProject没有形状
					continue;
	    		List<String> ifcProperties = this.getPropertiesByLine(possibleIns);
	    		if(ifcProperties.size() < 7)
	    			continue;
	    		
	    		String rep = ifcProperties.get(6); 	// 获取IfcRepresentation
	    		if(rep.length() < 2)	// IfcRepresentation以#开头，长度必然不小于2；长度小于2，要么为空，要么为$；
	    			continue;
	    		
	    		if(!mapRep2Ins.containsKey(rep)){
	    			List<String> lstTmp = new ArrayList<String>();
	    			mapRep2Ins.put(rep, lstTmp);
	    		}
	    		mapRep2Ins.get(rep).add(possibleIns);
	    		

	            String typeName = getTypeByLine(possibleIns);
	            if(!mapProdTypeToRep.containsKey(typeName))
	            {
	                Set<String> lstTmp = new HashSet<String>();
	                mapProdTypeToRep.put(typeName, lstTmp);
	            }
	            mapProdTypeToRep.get(typeName).add(rep);
	            mapRepToProdType.put(rep, typeName);
	    	}
//			long Time3=System.currentTimeMillis();
//			System.out.println("获取IfcRepresentation的时间为：  "+(Time3-Time2)/(double)1000 );
	    	// 获取每个IFCRepresentation的图
	    	Map<String, LinkedHashSet<String>> mapItems = new HashMap<String, LinkedHashSet<String>>();
			Map<String, Boolean> mapVisited =HashObjObjMaps.<String, Boolean>newUpdatableMap();//!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!new HashMap<String, Boolean>();// 
	    	//for(String rep : mapRep2Ins.keySet()){
	    	//	mapVisited.put(rep, true);
	    	//}
	    	if(mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM)){
	    		for(String insStyledItem : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM)){
	    			mapVisited.put(insStyledItem, true);
	    		}
	    	}
	    	if(mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION)){
	    		for(String insMatDefRep : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION)){
	    			mapVisited.put(insMatDefRep, true);
	    		}
	    	}
//			System.out.println("类型判断完成  ");
	    	retrieveStyledItemMap(mapItems, mapVisited);
//	    	System.out.println("retrieveStyledItemMap完成  ");
	    	retrieveMaterialRepDef(mapItems, mapVisited);
//	    	System.out.println("retrieveMaterialRepDef完成  ");
	    	retrieveRelVoidElement(mapItems, mapVisited);
//	    	System.out.println("retrieveRelVoidElement完成  ");
	    	
	    	for(String rep : mapRep2Ins.keySet()){
				retrieveSubinstances(rep, mapItems, mapVisited);
	    	}
//	    	System.out.println("retrieveRelVoidElement完成  ");
	    	List<String> lstReps = new ArrayList<String>();
	    	lstReps.addAll(mapRep2Ins.keySet()); 
	    	Map<String, Set<String>> mapGeometrySlices = new HashMap<String, Set<String>>();
//	        System.out.println("类型判断完成2  ");

			for(String m:lstReps){
				Set<String> projecthead = new HashSet<String>();//this.getProjectHead();
				Set<String> setTmp = getGeometryDescriptionSlices(m, mapRep2Ins.get(m), mapItems, mapVisited);  //生成每个构件的子文件
	    		projecthead.addAll(setTmp);
				mapGeometrySlices.put(m, projecthead);
	    		totalSize += setTmp.size();
			}

//			long Time4=System.currentTimeMillis();
//			System.out.println("生成每个构件IFCRepresentation图的时间为：  "+(Time4-Time3)/(double)1000 );
	    	if(mapGeometrySlices.size() == 0){
	    		// TODO: 切分不出来？？直接返回原文件
	    		//return 
	    	}

	    	Map<String, String> mapIns2Rep = new HashMap<String, String>(); // 构件 -> 表现
	    	for(String rep : mapRep2Ins.keySet()){
	    		for(String ins : mapRep2Ins.get(rep)){
	    			mapIns2Rep.put(ins, rep);
	    		}
	    	}
	    	Map<Set<String>, Set<String>> mapConnIns = getRelConnPathSets();
	    	String rep_conn;
			Set<String> connRep = new HashSet<String>();

	    	for(Set<String> connIns : mapConnIns.keySet()){
	    		for(String ins : connIns){
	    			if(mapIns2Rep.containsKey(ins)){	// 有些构件没有3D shape，神奇。
	    				connRep.add(mapIns2Rep.get(ins));
	    			}
	    		}

	    		// 生成合并后的几何对象
	    		rep_conn = String.valueOf(connIns.hashCode());
	    		Set<String> connGeometry = new HashSet<String>();
	    		for(String rep : connRep){
	    			connGeometry.addAll(mapGeometrySlices.get(rep));
	    			mapGeometrySlices.remove(rep);
	    		}

	    		// 需要加上IFCRELCONNECTSPATHELEMENTS实例
	    		connGeometry.addAll(mapConnIns.get(connIns));

	    		mapGeometrySlices.put(rep_conn, connGeometry);

	    		connRep.clear();
	    	}
//			long Time5=System.currentTimeMillis();
//			System.out.println("优化合并mapGeometrySlices的时间为：  "+(Time5-Time4)/(double)1000 );
	    	

	    	int max_len = 0, max_size = 0, min_size = data.size(), 
	    			min_len = Integer.MAX_VALUE, total_size=0, _total_size = 0,
	    			final_size = 0, splits =0;
	    	Map<String, String> mapSlices = new HashMap<String, String>();

	    	Map<String, Boolean> mapTr = new HashMap<String, Boolean>();

	    	Map<Integer, Set<String>> _mapSlices = new HashMap<Integer, Set<String>>();
	    	for(int i=0; i< this.EXPECTED_NUMBER_OF_FILE; i++){
	    		Set<String> _set = new HashSet<String>();
	    		_mapSlices.put(i, _set);
	    	}

	    	int empty_index = 0;
	    	for(String rep : mapGeometrySlices.keySet()){
	    		Set<String> contents = mapGeometrySlices.get(rep);
	    		
	    		if(empty_index == 0){
	    			_mapSlices.get(empty_index).addAll(contents);
	    			empty_index = 1;
	    			continue;
	    		}
	    		
	    		int candidate = 0, max_interaction = 0, candidate_min = 0;
	    		for(int i = 0; i< empty_index; i++){
	    			Set<String> _set = new HashSet<String>();
	    			_set.addAll(contents);
	    			_set.addAll(_mapSlices.get(i));
	    			int interaction = contents.size() + _mapSlices.get(i).size() - _set.size();
	    			
	    			if (interaction > max_interaction){
	    				max_interaction = interaction;
	    				candidate = i;
	    			}else if(interaction == max_interaction){
	    				if(_mapSlices.get(i).size() < _mapSlices.get(candidate).size()){
	    					candidate = i;
	    				}
	    			}
	    			
	    			if(_mapSlices.get(i).size() < _mapSlices.get(candidate_min).size()){
	    				candidate_min = i;
	    			}
	    		}
	    		
	    		if(max_interaction > 0.8 * contents.size()){
	    			_mapSlices.get(candidate).addAll(contents);
	    		}else{
	    			if(empty_index < this.EXPECTED_NUMBER_OF_FILE){
		    			_mapSlices.get(empty_index).addAll(contents);
		    			empty_index = empty_index + 1;
	    			}else{
		    			_mapSlices.get(candidate_min).addAll(contents);
	    			}
	    		}
	    	}

			Set<String> setProj = this.getProjectHead();
	    	for(int i=0; i < empty_index; i++){
 			StringBuilder sb = new StringBuilder();
     		sb.append(getHeader4GeometrySlice());
     		for(String writtenItem : setProj){
     			sb.append(writtenItem);
     			sb.append("=");
     			sb.append(data.get(writtenItem));
     			sb.append("\n");
     		}
     		for(String writtenItem : _mapSlices.get(i)){
     			sb.append(writtenItem);
     			sb.append("=");
     			sb.append(data.get(writtenItem));
     			sb.append("\n");
     		}
     		sb.append(this.getEnd4GeometrySlice());
     		mapSlices.put(String.valueOf(i), sb.toString());

	    		total_size = total_size+_mapSlices.get(i).size();
	    		if(_mapSlices.get(i).size() > max_size)
	    			max_size = _mapSlices.get(i).size();
	    		if(_mapSlices.get(i).size() < min_size)
	    			min_size = _mapSlices.get(i).size();
	    	}
	    	
	    	max_size = max_size + setProj.size();
	    	min_size = min_size + setProj.size();

	    	System.out.println("Max Size (Proj size): " + max_size + "("+setProj.size()+")"+ ", Min Size: " + min_size
	    			+ ", Actual Total Size (Total): " + total_size + "(" + _total_size + ")" + ", Final Size:" + final_size  
	    			+ ", Splits: " + empty_index);
//			System.out.println("总拆分时间应为 ：  "+(Time6-Time1)/(double)1000 );
	    	//lstGeometrySlices.addAll(mapGeometrySlices.values());
	    	return mapSlices;
	    }

    public Map<String, String> getSlicesInProductLevel(){
    	List<String> lstGeometrySlices = new ArrayList<String>();
    	LinkedHashSet<String> lstPossibleGeometryInstances = getPossibleGeometryInstances();  //获取contains和Aggregates
//		long Time2=System.currentTimeMillis();
//		System.out.println("获取contains和Aggregates的时间为：  "+(Time2-Time1)/(double)1000 );
		Map<String, List<String>> mapRep2Ins = new HashMap<String, List<String>>();
    	for(String possibleIns : lstPossibleGeometryInstances){
    		String ifcType = this.getTypeByLine(possibleIns);
    		if(ifcType == ""       )
    			continue;
    		if(IfcPropertyType.IFC_TYPE_IFCPROJECT.equals(ifcType)) // IFCProject没有形状
				continue;
    		List<String> ifcProperties = this.getPropertiesByLine(possibleIns);
    		if(ifcProperties.size() < 7)
    			continue;
    		
    		String rep = ifcProperties.get(6); 	// 获取IfcRepresentation
    		if(rep.length() < 2)	// IfcRepresentation以#开头，长度必然不小于2；长度小于2，要么为空，要么为$；
    			continue;
    		
    		if(!mapRep2Ins.containsKey(rep)){
    			List<String> lstTmp = new ArrayList<String>();
    			mapRep2Ins.put(rep, lstTmp);
    		}
    		mapRep2Ins.get(rep).add(possibleIns);
    	}
//		long Time3=System.currentTimeMillis();
//		System.out.println("获取IfcRepresentation的时间为：  "+(Time3-Time2)/(double)1000 );
    	// 获取每个IFCRepresentation的图
    	Map<String, LinkedHashSet<String>> mapItems = new HashMap<String, LinkedHashSet<String>>();
		Map<String, Boolean> mapVisited =HashObjObjMaps.<String, Boolean>newUpdatableMap();//!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!new HashMap<String, Boolean>();// 
    	//for(String rep : mapRep2Ins.keySet()){
    	//	mapVisited.put(rep, true);
    	//}
    	if(mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM)){
    		for(String insStyledItem : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM)){
    			mapVisited.put(insStyledItem, true);
    		}
    	}
    	if(mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION)){
    		for(String insMatDefRep : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION)){
    			mapVisited.put(insMatDefRep, true);
    		}
    	}
//		System.out.println("类型判断完成  ");
    	retrieveStyledItemMap(mapItems, mapVisited);
//    	System.out.println("retrieveStyledItemMap完成  ");
    	retrieveMaterialRepDef(mapItems, mapVisited);
//    	System.out.println("retrieveMaterialRepDef完成  ");
    	retrieveRelVoidElement(mapItems, mapVisited);
//    	System.out.println("retrieveRelVoidElement完成  ");
    	
    	for(String rep : mapRep2Ins.keySet()){
			retrieveSubinstances(rep, mapItems, mapVisited);
    	}
//    	System.out.println("retrieveRelVoidElement完成  ");
    	List<String> lstReps = new ArrayList<String>();
    	lstReps.addAll(mapRep2Ins.keySet()); 
    	Map<String, Set<String>> mapGeometrySlices = new HashMap<String, Set<String>>();
//        System.out.println("类型判断完成2  ");

		for(String m:lstReps){
			Set<String> projecthead = this.getProjectHead();
			Set<String> setTmp = getGeometryDescriptionSlices(m, mapRep2Ins.get(m), mapItems, mapVisited);  //生成每个构件的子文件
    		projecthead.addAll(setTmp);
			mapGeometrySlices.put(m, projecthead);
    		totalSize += setTmp.size();
		}

//		long Time4=System.currentTimeMillis();
//		System.out.println("生成每个构件IFCRepresentation图的时间为：  "+(Time4-Time3)/(double)1000 );
    	if(mapGeometrySlices.size() == 0){
    		// TODO: 切分不出来？？直接返回原文件
    		//return 
    	}

    	Map<String, String> mapIns2Rep = new HashMap<String, String>(); // 构件 -> 表现
    	for(String rep : mapRep2Ins.keySet()){
    		for(String ins : mapRep2Ins.get(rep)){
    			mapIns2Rep.put(ins, rep);
    		}
    	}
    	Map<Set<String>, Set<String>> mapConnIns = getRelConnPathSets();
    	String rep_conn;
		Set<String> connRep = new HashSet<String>();

    	for(Set<String> connIns : mapConnIns.keySet()){
    		for(String ins : connIns){
    			if(mapIns2Rep.containsKey(ins)){	// 有些构件没有3D shape，神奇。
    				connRep.add(mapIns2Rep.get(ins));
    			}
    		}

    		// 生成合并后的几何对象
    		rep_conn = String.valueOf(connIns.hashCode());
    		Set<String> connGeometry = new HashSet<String>();
    		for(String rep : connRep){
    			connGeometry.addAll(mapGeometrySlices.get(rep));
    			mapGeometrySlices.remove(rep);
    		}

    		// 需要加上IFCRELCONNECTSPATHELEMENTS实例
    		connGeometry.addAll(mapConnIns.get(connIns));

    		mapGeometrySlices.put(rep_conn, connGeometry);

    		connRep.clear();
    	}
    	
    	Map<String, String> mapSlices = new HashMap<String, String>();

    	int max_len = 0, max_size = 0, min_size = data.size(), min_len = Integer.MAX_VALUE;
    	for(String rep : mapGeometrySlices.keySet()){
    		StringBuilder sb = new StringBuilder();
    		sb.append(getHeader4GeometrySlice());
    		for(String writtenItem : mapGeometrySlices.get(rep)){
    			sb.append(writtenItem);
    			sb.append("=");
    			sb.append(data.get(writtenItem));
    			sb.append("\n");
    		}
    		sb.append(getEnd4GeometrySlice());
    		mapSlices.put(rep, sb.toString());

    		if(mapGeometrySlices.get(rep).size() > max_size)
    			max_size = mapGeometrySlices.get(rep).size();
    		if(mapGeometrySlices.get(rep).size() < min_size)
    			min_size = mapGeometrySlices.get(rep).size();

    		if(sb.length() > max_len)
    			max_len = sb.length();
    		if(sb.length() < min_len)
    			min_len = sb.length();
    	}
    	
    	System.out.println("Max Len: " + max_len +", Min Len: " + min_len
    			+ ", Max Size: " + max_size + ", Min Size: " + min_size
    			+ ", Total Size: " + data.size());

    	return mapSlices;
    }
    
    public Map<String, String> getSlicesInFloorLevel(){
    	List<String> lstGeometrySlices = new ArrayList<String>();
    	LinkedHashSet<String> lstPossibleGeometryInstances = getPossibleGeometryInstances();  //获取contains和Aggregates
//		long Time2=System.currentTimeMillis();
//		System.out.println("获取contains和Aggregates的时间为：  "+(Time2-Time1)/(double)1000 );
		Map<String, List<String>> mapRep2Ins = new HashMap<String, List<String>>();
		//int perfect_size = 0;
    	for(String possibleIns : lstPossibleGeometryInstances){
    		String ifcType = this.getTypeByLine(possibleIns);
    		if(ifcType == ""       )
    			continue;
    		if(IfcPropertyType.IFC_TYPE_IFCPROJECT.equals(ifcType)) // IFCProject没有形状
				continue;
    		List<String> ifcProperties = this.getPropertiesByLine(possibleIns);
    		if(ifcProperties.size() < 7)
    			continue;
    		
    		String rep = ifcProperties.get(6); 	// 获取IfcRepresentation
    		if(rep.length() < 2)	// IfcRepresentation以#开头，长度必然不小于2；长度小于2，要么为空，要么为$；
    			continue;
    		
    		if(!mapRep2Ins.containsKey(rep)){
    			List<String> lstTmp = new ArrayList<String>();
    			mapRep2Ins.put(rep, lstTmp);
    		}
    		mapRep2Ins.get(rep).add(possibleIns);
    	}
//		long Time3=System.currentTimeMillis();
//		System.out.println("获取IfcRepresentation的时间为：  "+(Time3-Time2)/(double)1000 );
    	// 获取每个IFCRepresentation的图
    	Map<String, LinkedHashSet<String>> mapItems = new HashMap<String, LinkedHashSet<String>>();
		Map<String, Boolean> mapVisited =HashObjObjMaps.<String, Boolean>newUpdatableMap();//!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!new HashMap<String, Boolean>();// 
    	//for(String rep : mapRep2Ins.keySet()){
    	//	mapVisited.put(rep, true);
    	//}
    	if(mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM)){
    		for(String insStyledItem : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM)){
    			mapVisited.put(insStyledItem, true);
    		}
    	}
    	if(mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION)){
    		for(String insMatDefRep : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION)){
    			mapVisited.put(insMatDefRep, true);
    		}
    	}
//		System.out.println("类型判断完成  ");
    	retrieveStyledItemMap(mapItems, mapVisited);
//    	System.out.println("retrieveStyledItemMap完成  ");
    	retrieveMaterialRepDef(mapItems, mapVisited);
//    	System.out.println("retrieveMaterialRepDef完成  ");
    	retrieveRelVoidElement(mapItems, mapVisited);
//    	System.out.println("retrieveRelVoidElement完成  ");
    	
    	for(String rep : mapRep2Ins.keySet()){
			retrieveSubinstances(rep, mapItems, mapVisited);
    	}
//    	System.out.println("retrieveRelVoidElement完成  ");
    	List<String> lstReps = new ArrayList<String>();
    	lstReps.addAll(mapRep2Ins.keySet()); 
    	Map<String, Set<String>> mapGeometrySlices = new HashMap<String, Set<String>>();
//        System.out.println("类型判断完成2  ");

		for(String m:lstReps){
			Set<String> projecthead = this.getProjectHead();
			Set<String> setTmp = getGeometryDescriptionSlices(m, mapRep2Ins.get(m), mapItems, mapVisited);  //生成每个构件的子文件
    		projecthead.addAll(setTmp);
    		//perfect_size = perfect_size + setTmp.size();
			mapGeometrySlices.put(m, projecthead);
    		totalSize += setTmp.size();
		}

//		long Time4=System.currentTimeMillis();
//		System.out.println("生成每个构件IFCRepresentation图的时间为：  "+(Time4-Time3)/(double)1000 );
    	if(mapGeometrySlices.size() == 0){
    		// TODO: 切分不出来？？直接返回原文件
    		//return 
    	}

    	int intGeoProd = 0;
    	Map<String, String> mapIns2Rep = new HashMap<String, String>(); // 构件 -> 表现
    	for(String rep : mapRep2Ins.keySet()){
    		for(String ins : mapRep2Ins.get(rep)){
    			intGeoProd = intGeoProd + 1;
    			mapIns2Rep.put(ins, rep);
    		}
    	}
    	Map<Set<String>, Set<String>> mapConnIns = getRelConnPathSets();
    	String rep_conn;
		Set<String> connRep = new HashSet<String>();
		
		Map<String, String> mapConnRep2New = new HashMap<String, String>();

    	for(Set<String> connIns : mapConnIns.keySet()){
    		for(String ins : connIns){
    			if(mapIns2Rep.containsKey(ins)){	// 有些构件没有3D shape，神奇。
    				connRep.add(mapIns2Rep.get(ins));
    			}
    		}

    		// 生成合并后的几何对象
    		rep_conn = String.valueOf(connIns.hashCode());
    		Set<String> connGeometry = new HashSet<String>();
    		for(String rep : connRep){
    			connGeometry.addAll(mapGeometrySlices.get(rep));
    			mapGeometrySlices.remove(rep);
    			mapConnRep2New.put(rep, rep_conn);
    		}

    		// 需要加上IFCRELCONNECTSPATHELEMENTS实例
    		connGeometry.addAll(mapConnIns.get(connIns));

    		mapGeometrySlices.put(rep_conn, connGeometry);

    		connRep.clear();
    	}
    	
    	int max_len = 0, max_size = 0, min_size = data.size(), min_len = Integer.MAX_VALUE,
    			total_size=0, splits=0, intGeoPar = 0;
    	Map<String, String> mapSlices = new HashMap<String, String>();
    	List<String> lstFloors = getMapType2Line().get(IfcPropertyType.IFC_TYPE_IFCBUILDINGSTOREY);
    	for(String floor : lstFloors) {
    		int _size = 0;
    		StringBuilder sb = new StringBuilder();
    		sb.append(getHeader4GeometrySlice());
    		Map<String, List<String>> mapChildren = this.getSpatialChildren();
    		List<String> lstFloorProducts = mapChildren.get(floor);
    		Set<String> setFloorProducts = new HashSet<String>();
			setFloorProducts.addAll(lstFloorProducts);
    		while(true){
    			for(String prod: lstFloorProducts){
    				if(mapChildren.containsKey(prod)){
        				setFloorProducts.addAll(mapChildren.get(prod));
    				}
    			}
    			
    			if(setFloorProducts.size() == lstFloorProducts.size())
    				break;
    			
    			lstFloorProducts.clear();
    			lstFloorProducts.addAll(setFloorProducts);
    		}
    		Map<String, Boolean> mapAddedRep = new HashMap<String, Boolean>();
    		Set<String> setFloorData = new HashSet<String>();
    		for(String prod : lstFloorProducts) {
    			if(prod.equals("#2957")){
    				System.out.println("#2957..");
    			}
    			String rep = mapIns2Rep.get(prod);
    			intGeoPar = intGeoPar + 1;
    			if(mapAddedRep.containsKey(rep))
    				continue;

    			if(mapConnRep2New.containsKey(rep)){
    				rep = mapConnRep2New.get(rep);
    			}
    			if(mapGeometrySlices.containsKey(rep)) {
    				setFloorData.addAll(mapGeometrySlices.get(rep));
    			}
    			
    			mapAddedRep.put(rep, true);
    		}

			_size = _size + setFloorData.size();
    		for(String writtenItem : setFloorData){
    			sb.append(writtenItem);
    			sb.append("=");
    			sb.append(data.get(writtenItem));
    			sb.append("\n");
    		}
		
    		
    		total_size = total_size + _size;
    		splits= splits + 1;

    		sb.append(getEnd4GeometrySlice());
    		//System.out.println(sb.toString());
    		mapSlices.put(floor, sb.toString());

    		if(_size > max_size)
    			max_size = _size;
    		if(_size < min_size)
    			min_size = _size;

    		if(sb.length() > max_len)
    			max_len = sb.length();
    		if(sb.length() < min_len)
    			min_len = sb.length();
    	}
    	
    	
    	
    	System.out.println("Max Len: " + max_len +", Min Len: " + min_len
    			+ ", Max Size: " + max_size + ", Min Size: " + min_size
    			+ ", Total Size: " + data.size() + "(" + total_size + ")" 
    			+ ", split=" + splits + ", Geo=" + intGeoProd + "(" + intGeoPar + ")");

    	return mapSlices;
    }

    public List<IfcMeshEntity> getGeometryInTriangles(List<String> lstSlices) throws RenderEngineException{
    	/*List<IfcMeshEntity> lstIfcGeom = new ArrayList<IfcMeshEntity>();
    	for(StringBuilder sb : lstSlices){
			InputStream is =  new   ByteArrayInputStream(sb.toString().getBytes());
			IfcGeoParser ifcGeoParser = new IfcGeoParser(is);
			lstIfcGeom.addAll(ifcGeoParser.getGeomServerClientEntities());
    	}
    	
    	return lstIfcGeom;*/
    	List<IfcMeshEntity> lstMeshes;
    	try {
    		lstMeshes = new IfcParallelGeoParser(lstSlices).getGeomServerClientEntities();
		} catch (InterruptedException | ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}finally{
			for(String s : lstSlices){
				File file=new File(s);
				file.delete();
			}
		}
    	
    	return lstMeshes;
    }
    
    public List<IfcMeshEntity> getGeometryInTriangles(String ifcContent) throws RenderEngineException{
		InputStream is =  new ByteArrayInputStream(ifcContent.getBytes());
		IfcGeoParser ifcGeoParser = new IfcGeoParser(is);
		return ifcGeoParser.getGeomServerClientEntities();
    }
    
    private List<Set<String>> retrieveProductSegments(String rep, Map<String, List<String>> mapRep2Ins,
    			Map<String, LinkedHashSet<String>> mapItems, int thr){
		Set<String> projecthead = this.getProjectHead();
		
		List<Set<String>> lstSegments = retrieveSegments(rep, mapItems, thr);
		for(Set<String> setTmp : lstSegments){
			setTmp.addAll(projecthead);
		}
		
		List<String> inses = mapRep2Ins.get(rep);
		if(inses != null) {
    	for(String product: inses){
    		String location = this.getIfcPropertiesByLineData(data.get(product)).get(5);
    		Set<String> setLocation = retrieveSubChild(location, mapItems);
    		
    		for(Set<String> setTmp : lstSegments){
    			setTmp.add(product);
    			setTmp.addAll(setLocation);
    		}
    	}
		}
    	
    	return lstSegments;
    }
    
    private List<Set<String>> retrieveSegments(
			String rep, Map<String, LinkedHashSet<String>> mapItems, int thr){

		// 从上而下建立层次关系
		Map<Integer, Set<String>> mapLayer2Ins = new HashMap<Integer, Set<String>>();
		int index = 1;
		Set<String> setRep = new HashSet<String>();
		setRep.add(rep);
		mapLayer2Ins.put(index, setRep);
		while(true){
			Set<String> setTmp = new HashSet<String>();
			for(String ins : mapLayer2Ins.get(index)){
				if(mapItems.containsKey(ins)) // 非叶子节点
					setTmp.addAll(mapItems.get(ins));
			}
			
			if(setTmp.isEmpty() || setTmp.size() == 0)
				break;
			
			index++;
			mapLayer2Ins.put(index, setTmp);
		}
		
    	Map<String, NodeAttr> mapIns2SizeAndSeg = retrieveSizeAndSegmentability(mapLayer2Ins, rep, mapItems);
    	index = 1;
    	Set<String> setNodeCnt = new HashSet<String>();
    	List<Set<String>> lstSegments = retrieveSubSegments(mapIns2SizeAndSeg, setNodeCnt, rep, mapItems, thr);
    	
    	return lstSegments;
    }
    
    private List<Set<String>> retrieveSubSegments(Map<String, NodeAttr> mapIns2SizeAndSeg, 
    		Set<String> setIns, String ins, Map<String, LinkedHashSet<String>> mapItems, int thr){
    	List<Set<String>> lstSegments = new ArrayList<Set<String>>();
    	
		if(mapIns2SizeAndSeg.get(ins).segmentability && mapIns2SizeAndSeg.get(ins).size > thr){
			if(mapIns2SizeAndSeg.get(ins).self_seg){ // 本身可分割
				Set<String> setNodeCnt = new HashSet<String>();
				setNodeCnt.addAll(setIns);
				setNodeCnt.add(ins);
				String ref = mapIns2SizeAndSeg.get(ins).ref;
				if(mapItems.containsKey(ref)){
					setNodeCnt.addAll(retrieveSubChild(ref, mapItems));
				}
				
				int thr_new = thr + setIns.size() - setNodeCnt.size();
				Set<String> setCombination = new HashSet<String>();
				for(String _ins : mapItems.get(ins)){
					if(_ins.equals(ref))
						continue;
					if(mapIns2SizeAndSeg.get(_ins).size > thr_new){
						lstSegments.addAll(retrieveSubSegments(mapIns2SizeAndSeg, setNodeCnt, _ins, mapItems, thr_new));
					}else{
						setCombination.add(_ins);
					}
				}
				
				int thr_tmp = thr_new;
				Set<String> setTmp = new HashSet<String>();
				for(String _ins : setCombination){
					if(thr_tmp > mapIns2SizeAndSeg.get(_ins).size){
						thr_tmp = thr_tmp - mapIns2SizeAndSeg.get(_ins).size;
						setTmp.add(_ins);
					}else{
						Set<String> setTmpContent = new HashSet<String>();
						setTmpContent.addAll(setNodeCnt);
						for(String _i : setTmp){
							setTmpContent.addAll(retrieveSubChild(_i, mapItems));
						}
						lstSegments.add(setTmpContent);
						thr_tmp =  thr_new;
						setTmp.clear();
					}
				}
			}else{
				String seg_child = mapIns2SizeAndSeg.get(ins).seg_child;
				Set<String> setNodeCnt = new HashSet<String>();
				setNodeCnt.addAll(setIns);
				setNodeCnt.add(ins);
				for(String _ins : mapItems.get(ins)){
					if(_ins.equals(seg_child))
						continue;
					setNodeCnt.addAll(retrieveSubChild(_ins,mapItems));
				}
				
				lstSegments = retrieveSubSegments(mapIns2SizeAndSeg, setNodeCnt, 
							seg_child, mapItems, thr + setIns.size() - setNodeCnt.size());
			}
		}else{ // 不可分，或 大小符合阈值要求，直接返回所有子节点
			Set<String> setTmp = retrieveSubChild(ins, mapItems);
			setTmp.addAll(setIns);
			lstSegments.add(setTmp);
		}
		
		return lstSegments;
    }
    
    private Set<String> retrieveSubChild(String ins, Map<String, LinkedHashSet<String>> mapItems){
		Set<String> setTmp = new HashSet<String>();
		setTmp.add(ins);
		if(mapItems.containsKey(ins)){ // 若有子节点，则添加子节点
			for(String _ins : mapItems.get(ins)){
				setTmp.addAll(retrieveSubChild(_ins, mapItems));
			}
		}
		
		return setTmp;
    }
    
    class NodeAttr{
    	public int size = 0;
    	public boolean segmentability = false;
    	public boolean self_seg = false;
    	public String seg_child = "";
    	public String ref = "";
    	
    	public NodeAttr(int _size){
    		size = _size;
    	}
    	
    	public NodeAttr(int _size, boolean _s, boolean _self_seg, String _seg_child, String _ref){
    		size = _size;
    		segmentability = _s;
    		self_seg = _self_seg;
    		seg_child = _seg_child;
    		ref = _ref;
    	}
    }
    private Map<String, NodeAttr> retrieveSizeAndSegmentability(Map<Integer, Set<String>> mapLayer2Ins, 
    						String rep, Map<String, LinkedHashSet<String>> mapItems){

    	String[] decompositions = {"ifcgeometricset", "ifcconnectedfaceset",
    				 "ifcclosedshell", "ifcopenshell"};
    	String associations[] = {"ifcstyleditem", "IFCSHAPEREPRESENTATION"};
    	
		Map<String, NodeAttr> mapIns2Size = new HashMap<String, NodeAttr>();

		int index = mapLayer2Ins.size();
		// 从下而上建立建立size
		while(index > 0){
			for(String ins : mapLayer2Ins.get(index)){
				if(mapIns2Size.containsKey(ins))
					continue;
				
				int size = 1;
				boolean _seg = false, _self_seg = false;
				String seg_child = "", ref="";
				String type = this.getTypeByLine(ins);
				for(String _t : decompositions){
					if(_t.toLowerCase().equals(type.toLowerCase())){
						_seg = true;
						_self_seg = true;
						break;
					}
				}
				for(String _t : associations){
					if(_t.toLowerCase().equals(type.toLowerCase())){
						List<String> insProperties = this.getIfcPropertiesByLineData(data.get(ins));
						_seg = true;
						_self_seg = true;
						String item = insProperties.get(0);
						ref = item;
						break;
					}
				}
				
				if(mapItems.containsKey(ins)){
					for(String child_ins : mapItems.get(ins)){
						size += mapIns2Size.get(child_ins).size;
						if(mapIns2Size.get(child_ins).segmentability){
							_seg = true;
							seg_child = child_ins;
						}
					}
				}else{
					size = 1;
				}
				
				mapIns2Size.put(ins, new NodeAttr(size, _seg, _self_seg, seg_child, ref));
			}
			
			index--;
		}
		
		return mapIns2Size;
    }
    
    // 获取因为IFCRELCONNECTSPATHELEMENTS 需要合在一起的构件集
    private Map<Set<String>, Set<String>> getRelConnPathSets(){
    	// 需要把IFC_TYPE_IFCRELCONNECTSPATHELEMENTS的构件放在一起
    	Map<Set<String>, Set<String>> mapConnCombined = new HashMap<Set<String>, Set<String>>();
    	if(mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELCONNECTSPATHELEMENTS)){
    		List<Set<String>> lstTmpConnSlice = new ArrayList<Set<String>>();
    		for(String line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELCONNECTSPATHELEMENTS)){
    			String content = data.get(line);
    			List<String> ifcProperties = getIfcPropertiesByLineData(content);
    			String strRelatingElement = ifcProperties.get(5);
    			String strRelatedElement = ifcProperties.get(6);
    			if(!data.containsKey(strRelatingElement) || !data.containsKey(strRelatedElement)){
    				// TODO: 有个实例不存在？ why？？
    				continue;
    			}
    	    	for(Set<String> rawSlice : mapConnCombined.keySet()){
    	    		if(rawSlice.contains(strRelatingElement) || rawSlice.contains(strRelatedElement)){
    	    			lstTmpConnSlice.add(rawSlice);
    	    		}
    	    	}
    	    	
    	    	if(lstTmpConnSlice.size() > 0){	// 两个set合并
    	    		Set<String> tmp = new HashSet<String>();
    	    		Set<String> tmp_rel = new HashSet<String>();
    	    		for(Set<String> oriConn : lstTmpConnSlice){
    	    			tmp.addAll(oriConn);
    	    			tmp_rel.addAll(mapConnCombined.get(oriConn));
    	    			mapConnCombined.remove(oriConn);
    	    		}
    	    		tmp.add(strRelatingElement);
    	    		tmp.add(strRelatedElement);
    	    		tmp_rel.add(line);
    	    		mapConnCombined.put(tmp, tmp_rel);
    	    	}else{	// 生成新的set
    	    		Set<String> tmp = new HashSet<String>();
    	    		tmp.add(strRelatingElement);
    	    		tmp.add(strRelatedElement);

    	    		Set<String> tmp_rel = new HashSet<String>();
    	    		tmp_rel.add(line);
    	    		mapConnCombined.put(tmp, tmp_rel);
    	    	}
	    		lstTmpConnSlice.clear();
    		}
    	}
    	return mapConnCombined;
    }
    
    private String writeSetToFile(Set<String> set, String rep){
    	String path = temp_folder + rep + "_" + set.hashCode() + ".ifc";
    	try
    	{
    		BufferedWriter out=new BufferedWriter(new FileWriter(path));
    		out.write(getHeader4GeometrySlice());
        	for(String writtenItem : set){
        		out.write(writtenItem);
        		out.write("=");
        		out.write(data.get(writtenItem));
        		out.write("\n");
        	}
        	out.write(getEnd4GeometrySlice());
    		out.close();
    	} catch (IOException e)
    	{
    		// TODO Auto-generated catch block
    		e.printStackTrace();
    		return null;
    	}
    	
    	return path;
    }

    private String writeSetToFile_LargeProduct(Set<String> set, String rep){
    	String path = temp_folder + rep + "_" + set.hashCode() + ".ifc";
    	try
    	{
    		BufferedWriter out=new BufferedWriter(new FileWriter(path));
    		out.write(getHeader4GeometrySlice());
        	for(String writtenItem : set){
        		String ifcType = this.getTypeByLine(writtenItem);
        		if("IFCSHAPEREPRESENTATION".equals(ifcType.toUpperCase())){// ifcshaperepresentation特殊处理
            		List<String> insProperties = this.getIfcPropertiesByLineData(data.get(writtenItem));

	        		out.write(writtenItem);
	        		out.write("=IFCSHAPEREPRESENTATION(");
	        		out.write(insProperties.get(0)+ "," + insProperties.get(1) + "," + insProperties.get(2) + "," );
	        		String reps = insProperties.get(3);
	        		if(reps.length() > 2 && reps.startsWith("(") && reps.endsWith(")")){	// styles的长度大于2，则有可能有styles
	        			String[] _reps = reps.substring(1, reps.length() - 1).split(",");
	        			reps = "(";
	        			for(String _rep : _reps){
	        				if(set.contains(_rep)){
	        					reps = reps + _rep + ",";
	        				}
	        			}
	        			
	        			reps = reps.substring(0, reps.length() - 1);
	        			reps = reps + ")";
	        		}
	        		out.write(reps + ");");
	        		out.write("\n");
        		}else{
	        		out.write(writtenItem);
	        		out.write("=");
	        		out.write(data.get(writtenItem) + "");
	        		out.write("\n");
        		}
        	}
        	out.write(getEnd4GeometrySlice());
    		out.close();
    	} catch (IOException e)
    	{
    		// TODO Auto-generated catch block
    		e.printStackTrace();
    		return null;
    	}
    	
    	return path;
    }

	public Map<String, List<String>> getSpatialChildren(){
		Map<String, List<String>> mapContains = getContains();
		Map<String, List<String>> mapAggregates = getAggregates();
		
		for(String key : mapAggregates.keySet()){
			if(mapContains.containsKey(key)){
				mapContains.get(key).addAll(mapAggregates.get(key));
			}else{
				mapContains.put(key, mapAggregates.get(key));
			}
		}
		//mapContains.putAll(mapAggregates);

		return mapContains;
	}

   
    
    private Set<String> getGeometryDescriptionSlices(String rep, List<String> products,
    						Map<String, LinkedHashSet<String>> mapItems, Map<String, Boolean> mapVisited){
    	//List<String> lstGeometry = new ArrayList<String>(); // 初始5k
    	//lstGeometry.add(getHeader4GeometrySlice());
    	
    	Map<String, Boolean> mapWritten =HashObjObjMaps.<String, Boolean>newUpdatableMap();//!!!!!!!!!!!!!!!!!!!!!!!！！！！！！！！！！！！！new HashMap<String, Boolean>();//
    	for(String product: products){
    		// 1 append i.e. opening
    		appendItemRepresentationIteratively(null, product, mapItems, mapWritten, mapVisited);
    		mapWritten.put(product, true);
    		// 1 append product
    		//sbGeometry.append(product);
    		//sbGeometry.append("=");
    		//sbGeometry.append(data.get(product).toString());
    		//sbGeometry.append(";\n");
    		//lstGeometry.add(product + "=" + data.get(product) + "\n");
    		// 2 append location
    		String location = this.getIfcPropertiesByLineData(data.get(product)).get(5);
    		appendItemRepresentationIteratively(null, location, mapItems, mapWritten, mapVisited);
    		
    		//sbGeometry.append(getItemRepresentationIteratively(location, mapItems, mapWritten, mapVisited));
    	}
    	// 3 append shape
    	appendItemRepresentationIteratively(null, rep, mapItems, mapWritten, mapVisited);
    	    	
    	return mapWritten.keySet();
    	/*StringBuilder sb = new StringBuilder();
    	for(String writtenItem : mapWritten.keySet()){
    		sb.append(writtenItem);
    		sb.append("=");
    		sb.append(data.get(writtenItem));
    		sb.append("\n");
    		//lstGeometry.add(sb.toString());
    		//sb.delete(0, sb.length());
    	}*/
    	//lstGeometry.add(sb.toString());
    	//lstGeometry.add(getEnd4GeometrySlice()); 

    	//return lstGeometry;//.toString();//String.join("\n", lstGeometry);
    }
    
    private void appendItemRepresentationIteratively(List<String> lstGeometry, String item, 
    					Map<String, LinkedHashSet<String>> mapItems, 
    					Map<String, Boolean> mapWritten, Map<String, Boolean> mapVisited){
    	//StringBuilder sb = new StringBuilder();
    	if(mapWritten.containsKey(item))
    		return;
    	
    	mapWritten.put(item, true);
    	/*sb.append(item);
    	sb.append("=");
    	sb.append(data.get(item).toString());
    	sb.append(";\n");*/
    	//lstGeometry.add(item + "=" + data.get(item).toString() + "\n");
    	if(!mapItems.containsKey(item)){
    		retrieveSubinstances(item, mapItems, mapVisited);
    	}

    	if(mapItems.containsKey(item)){	// 排除叶子节点
        	for(String sub : mapItems.get(item)){
        		appendItemRepresentationIteratively(lstGeometry, sub, mapItems,mapWritten, mapVisited);
        	}
    	}
    	//return sb;
    }
    
    private String getHeader4GeometrySlice(){
    	//StringBuilder sbHeader = new StringBuilder();

    	//sbHeader.append("ISO-10303-21;\n");
    	//sbHeader.append("HEADER;\n");
    	//sbHeader.append(this.header + "\n");
    	//sbHeader.append("ENDSEC;\n");
    	//sbHeader.append("DATA;\n");
		
    	//return sbHeader;
    	
    	return "ISO-10303-21;\nHEADER;\nENDSEC;\nDATA;\n";
    }
    private String getEnd4GeometrySlice(){
    	return "ENDSEC;\nEND-ISO-10303-21;";
    }
    
    private LinkedHashSet<String> getPossibleGeometryInstances(){
    	LinkedHashSet<String> lstPossibleGeometryInstances = new LinkedHashSet<String>();
    	Map<String, List<String>> mapAggregates = this.getAggregates();
    	
    	// 所有的几何构件都会包含在Aggregates和Contains两种关系中（含key和value）
    	for(String ins_p : mapAggregates.keySet()){
    		lstPossibleGeometryInstances.addAll(mapAggregates.get(ins_p));
    		lstPossibleGeometryInstances.add(ins_p);
    	}
    	
    	Map<String, List<String>> mapContains = this.getContains();
    	for(String ins_p : mapContains.keySet()){
    		lstPossibleGeometryInstances.addAll(mapContains.get(ins_p));
    		lstPossibleGeometryInstances.add(ins_p);
    	}
    	
    	return lstPossibleGeometryInstances;
    }
     
    private void retrieveStyledItemMap(Map<String, LinkedHashSet<String>> mapItems,
    												Map<String, Boolean> mapVisited){
    	if(!this.mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM))
    		return;
    	
    	for(String insStyledItem : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM)){
			mapVisited.put(insStyledItem, true);
    		List<String> insProperties = this.getIfcPropertiesByLineData(data.get(insStyledItem)); //this.data.get(insStyledItem).getProperties();
    		String item = insProperties.get(0);
    		if(!data.containsKey(item))
    			continue;
    		
    		String styles = insProperties.get(1);
    		if(styles.length() > 2 && styles.startsWith("(") && styles.endsWith(")")){	// styles的长度大于2，则有可能有styles
    			String[] _styles = styles.substring(1, styles.length() - 1).split(",");
    			if(!mapItems.containsKey(item)){
    				LinkedHashSet<String> lstTmp = new LinkedHashSet<String>();
    				mapItems.put(item, lstTmp);
    			}

    			mapItems.get(item).add(insStyledItem);
    			
    			if(_styles.length > 0){
        			if(!mapItems.containsKey(insStyledItem)){
        				LinkedHashSet<String> lstTmp = new LinkedHashSet<String>();
        				mapItems.put(insStyledItem, lstTmp);
        			}
	    			for(String style : _styles){
	    				mapItems.get(insStyledItem).add(style);
	    				
	    				retrieveSubinstances(style, mapItems, mapVisited);
	    			}
    			}
    		}
    	}
    }
    
    private void retrieveMaterialRepDef(Map<String, LinkedHashSet<String>> mapItems,
			Map<String, Boolean> mapVisited){
    	if(!this.mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION))
    		return;
    	
    	for(String insMatRepDef : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION)){
			mapVisited.put(insMatRepDef, true);
    		List<String> insProperties = getIfcPropertiesByLineData(data.get(insMatRepDef)); //this.data.get(insMatRepDef).getProperties();
    		String material = insProperties.get(3);
    		if(!data.containsKey(material))
    			continue;
    		
    		String reps = insProperties.get(2);
    		if(reps.length() > 2 && reps.startsWith("(") && reps.endsWith(")")){	// styles的长度大于2，则有可能有styles
    			String[] _reps = reps.substring(1, reps.length() - 1).split(",");
    			if(!mapItems.containsKey(insMatRepDef)){
    				LinkedHashSet<String> lstTmp = new LinkedHashSet<String>();
    				mapItems.put(insMatRepDef, lstTmp);
    			}

    			mapItems.get(insMatRepDef).add(material);
    			for(String _rep : _reps){
        			if(!mapItems.containsKey(_rep)){
        				LinkedHashSet<String> lstTmp = new LinkedHashSet<String>();
        				mapItems.put(_rep, lstTmp);
        			}
    				    				
    				mapItems.get(_rep).add(insMatRepDef);
    			}
				
				retrieveSubinstances(material, mapItems, mapVisited);
    		}
    	}
    }

    private void retrieveRelVoidElement(Map<String, LinkedHashSet<String>> mapItems,
			Map<String, Boolean> mapVisited){
    	if(!this.mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELVOIDSELEMENT))
    		return;
    	
    	for(String insRelVoidEle : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELVOIDSELEMENT)){
			mapVisited.put(insRelVoidEle, true);
    		List<String> insProperties = getIfcPropertiesByLineData(data.get(insRelVoidEle)); //this.data.get(insMatRepDef).getProperties();
    		String insSolid = insProperties.get(4);
    		if(!data.containsKey(insSolid))
    			continue;
    		
    		String insOpening = insProperties.get(5);
    		if(data.containsKey(insOpening)){
    			if(!mapItems.containsKey(insRelVoidEle)){
    				LinkedHashSet<String> lstTmp = new LinkedHashSet<String>();
    				mapItems.put(insRelVoidEle, lstTmp);
    			}
    			mapItems.get(insRelVoidEle).add(insOpening);
    			
    			if(!mapItems.containsKey(insSolid)){
    				LinkedHashSet<String> lstTmp = new LinkedHashSet<String>();
    				mapItems.put(insSolid, lstTmp);
    			}
    			mapItems.get(insSolid).add(insRelVoidEle);
    			
				retrieveSubinstances(insOpening, mapItems, mapVisited);
    		}
    	}
    }
    
    private void retrieveSubinstances(String ins, 
    		Map<String, LinkedHashSet<String>> mapItems, Map<String, Boolean> mapVisited){
    	if(mapVisited.containsKey(ins))
    		return;
    	
    	List<String> ifcProperties = getIfcPropertiesByLineData(data.get(ins)); //data.get(ins).getProperties();
    	for(String property : ifcProperties){
    		if(property.length() < 2 || property.startsWith("'"))	// 找实例，实例形如#23，所以，长度不小于2
    			continue;
    		
    		mapVisited.put(ins, true);
    		if(property.startsWith("#") && data.containsKey(property)){
    			if(!mapItems.containsKey(ins)){
    				LinkedHashSet<String> tmp = new LinkedHashSet<String>();
    				mapItems.put(ins, tmp);
    			}
    			mapItems.get(ins).add(property);
    			
    			// 递归寻找
    			retrieveSubinstances(property, mapItems, mapVisited);
    		}else if(property.startsWith("(") && property.endsWith(")")){
    			// TODO: maybe sth unresolved
    			String raw_ins = property.substring(1, property.length() - 1);
    			String[] sub_ins = raw_ins.split(",");
    			if(sub_ins.length > 0){
    				if(!sub_ins[0].startsWith("#")) // 说明为非实例
    					continue; 
    				
        			if(!mapItems.containsKey(ins)){
        				LinkedHashSet<String> tmp = new LinkedHashSet<String>();
        				mapItems.put(ins, tmp);
        			}
	    			for(String sub : sub_ins){
	        			mapItems.get(ins).add(sub);
	        			
	        			// 递归寻找
	        			retrieveSubinstances(sub, mapItems, mapVisited);
	    			}
    			}
    		}
    	}
    }
    


	//////////////////////////////////////////////////////////////////////////////////////////
	/// 初始化
	///////////////////////////////////////////////////////////////////////////////////////// 
    
	/**
	 * 根据Ifc文件流读取、初始化Ifc数据
	 * @param in	Ifc输入文件流
	 * @throws IOException	Ifc文件读取错误异常
	 */
	private void initByInputStream(InputStream in) throws IOException{
        String encoding = "US-ASCII";
        InputStreamReader reader = new InputStreamReader(in, encoding);
        BufferedReader lineReader = new BufferedReader(reader);
        String _lineTemp = null;
        StringBuilder sbObject = new StringBuilder();
        boolean isHeader = false, isData = false;
        
        while ((_lineTemp = lineReader.readLine()) != null) {
        	_lineTemp = _lineTemp.trim();

        	if(isHeader){	// IFC的header部分
        		
        		if(IfcPropertyType.IFC_ENDSEC.equals(_lineTemp.toUpperCase())){	// header 结束
            		isHeader = false;
        			header = sbObject.toString();
        			sbObject.delete(0, sbObject.length());// = new StringBuilder();
        			continue;
        		}else{
            		sbObject.append(_lineTemp);
            		continue;
        		}
        	}else if(isData){		// IFC的data部分
            	if(!_lineTemp.endsWith(";")){	// 非;结尾，instance描述未结束，继续读取
            		sbObject.append(_lineTemp);
            		continue;
            	}

        		if(IfcPropertyType.IFC_ENDSEC.equals(_lineTemp.toUpperCase())){	// data结束
        			isData = false;
        			continue;
        		}

        		sbObject.append(_lineTemp);
            	if(sbObject.charAt(0)!='#'){	// data部分非#开头，似乎有问题
            		sbObject.delete(0, sbObject.length());// = new StringBuilder();
            		continue;
            		// TODO: log what happen here?
            	}
            	
            	int indEql = sbObject.indexOf("=");
            	if(indEql > 0){
            		String line = sbObject.substring(0, indEql).trim();
            		String content = sbObject.substring(indEql + 1).trim();
            		//IfcInstance ii = getIfcInstanceByLineData(content);
            		String ifcType = getIfcTypeByLineData(content);
            		data.put(line, content);//new IfcInstance(content, ifcType));
            		
            		if(!mapType2Line.containsKey(ifcType)){
            			List<String> lstTemp = new ArrayList<String>();
            			mapType2Line.put(ifcType, lstTemp);
            		}
            		mapType2Line.get(ifcType).add(line);
            		sbObject.delete(0, sbObject.length());// = new StringBuilder();
            	}else{	// 实例没有=号
            		// TODO: log what happen here
            	}
        		
        	} else if(IfcPropertyType.IFC_HEADER.equals(_lineTemp.trim().toUpperCase())){
        		isHeader = true;
        	} else if(IfcPropertyType.IFC_DATA.equals(_lineTemp.trim().toUpperCase())){
        		isData = true;
        	}
        }
        
        lineReader.close();
        reader.close();
	}
	
	private String getIfcTypeByLineData(String content){
        	int ind1stBracket = content.indexOf('(');
        	int indLastBracket = content.lastIndexOf(')');
        	List<String> lstProperties = new ArrayList<String>();
        	String ifcType = content.substring(0, ind1stBracket).trim().toUpperCase();
        	
        	return ifcType;
	}

	private String[] getIfcUnitAssignment(String assignment){
		List<String> child = this.getPropertiesByLine(assignment);
		String rawAgg = child.get(0).substring(1, child.get(0).length() - 1); // 去掉左右括号
		String[] children = rawAgg.split(",");
		return children;
	}

	/**
	 * 根据一行完整ifc数据，解析形成IfcInstance
	 * @param content 一行完整Ifc数据（不含行号），数据格式形如：
	 * 		IFCPROJECT('3MD_HkJ6X2EwpfIbCFm0g_', #2, 'Default Project', 'Description of Default Project', $, $, $, (#20), #7);
	 * @return IfcInstance，包括IfcType和属性列表
	 */
    private List<String> getIfcPropertiesByLineData(String content){
    	int ind1stBracket = content.indexOf('(');
    	int indLastBracket = content.lastIndexOf(')');
    	List<String> lstProperties = new ArrayList<String>();
    	String ifcType = content.substring(0, ind1stBracket).trim().toUpperCase();

    	String[] strRawProperties = content.substring(ind1stBracket+1, indLastBracket).trim().split(",");
    	for(int i = 0; i < strRawProperties.length; i++){
    		String strRawProperty = strRawProperties[i].trim();
    		if(strRawProperty.startsWith("'")){ // 以'开头，则必须以'结尾，以处理,出现在''之间的情况
    			while(!(strRawProperty.endsWith("'") && !strRawProperty.endsWith("\\'"))){
    				if(strRawProperty.endsWith("\\X0\\'")){	// 解决以\X0\'结尾的问题
    					break;
    				}
    				
					i++;

					strRawProperty += "," + strRawProperties[i].trim();
    			}
    		}else if(strRawProperty.startsWith("(")){	// 以(开头，则必须以)结尾，以处理,出现在()之间的情况
    			while(!(strRawProperty.endsWith(")") && !strRawProperty.endsWith("\\"))){
					i++;
					strRawProperty += "," + strRawProperties[i].trim();
    			}
    		}else if(strRawProperty.contains("('")){ // 内含(',形如 IFCText('ab, cde')，要以 ')结束
    			while(!(strRawProperty.endsWith("')"))){
    				i++;
					strRawProperty += "," + strRawProperties[i].trim();
    			}
    		}

			lstProperties.add(strRawProperty);
    	}
    	
    	return lstProperties; //new IfcInstance(ifcType, lstProperties);
    }
	
	public String getHeader() {
		return header;
	}

	public void setHeader(String header) {
		this.header = header;
	}

	public Map<String, String> getData() {
		return data;
	}

	public String getProject(){
		String project = null;
		if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCPROJECT)) {
			for (String insMatDefRep : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCPROJECT)) {
				 project = insMatDefRep;
				break;
			}
		}
    	return project;
	}

	public void setData(Map<String, String> data) {
		this.data = data;
	}

	public Map<String, List<String>> getMapType2Line() {
		return mapType2Line;
	}

	public void setMapType2Line(Map<String, List<String>> mapType2Line) {
		this.mapType2Line = mapType2Line;
	}

	public  Set<String> getProjectHead() {

			Set<String> projecthead = new HashSet<String>();
			String project = getProject();
			projecthead.add(project);
			String assignment = this.getPropertiesByLine(project).get(8);
			projecthead.add(assignment);
			String[] children=getIfcUnitAssignment(assignment);
			for (String child : children) {
				projecthead.add(child);
			}
			return projecthead;
    }




	class IfcInstance {
    	private String raw;
    	private String ifcType;
    	//private List<String> ifcProperties;
    	   
    	public List<String> getProperties(){
    		return getIfcPropertiesByLineData(raw);
    	}
    	
    	public IfcInstance(String _raw, String _ifcType){
    		//ifcProperties = new ArrayList<String>();
    		this.raw = _raw;
    		this.ifcType = _ifcType;
    	}
    	
    	//public IfcInstance(String _raw){
    		//this.raw = _raw;
    	//	ifcProperties = new ArrayList<String>();
    	//}
    	
    	public IfcInstance(String _ifcType, List<String> _ifcProperties){
    		//this.raw = _raw;
    		//this.ifcType = _ifcType;
    		//this.ifcProperties = _ifcProperties;
    	}


    	public String toString(){
    		/*StringBuilder sb = new StringBuilder();
    		sb.append(ifcType);
    		sb.append("(");
    		for(String pro : ifcProperties){
    			sb.append(pro);
    			sb.append(",");
    		}
    		sb.deleteCharAt(sb.length() - 1);
    		sb.append(");");
    		
    		return sb.toString();*/
    		return raw;
    	}
    	

    }
}
