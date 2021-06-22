package com.boswinner.ifcengine;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.ExecutionException;

import com.sun.management.OperatingSystemMXBean;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.bimserver.plugins.renderengine.RenderEngineException;
import org.json.simple.JSONObject;
import com.boswinner.ifcengine.geometry.IfcGeoParser;
import com.boswinner.ifcengine.geometry.IfcMeshEntity;
import com.boswinner.ifcengine.geometry.IfcMeshInterface;
import com.boswinner.ifcengine.geometry.IfcParallelGeoParser;

public class IfcFile {/*IFCPROPERTYSET*/
    private final int EXPECTED_NUMBER_OF_FILE = 16;
    private boolean seg = false;/*	private String path = "";*/
    private String header = "";
//    private Map<String, String> data = HashObjObjMaps.<String, String>newUpdatableMap();/*!!!!!!!!!!!!!!!!!new HashMap<String, String>(); // line:content的Map数据//*//* ifctype到line的Map；很重要，需要根据IfcType切分几何、属性获取、关系获取等*/
        private HashMap<Integer,String> data = new HashMap<>();
    private Map<String, List<Integer>> mapType2Line = new HashMap<String, List<Integer>>();
    private Properties props = new Properties();
    private String temp_folder;
    private List<Integer> componentsstatistic = new ArrayList<>();
    Map<Integer, List<Integer>> mapAggregates = new HashMap<>();
    private Map<Integer,List<Integer>> spaceBoudary = new HashMap<>();

    public IfcFile(InputStream in) throws IOException {
        init("/ifcengine.properties");
        initByInputStream(in);
    }

    public IfcFile(String path) throws IOException {
        init("/ifcengine.properties");
        InputStream in = new FileInputStream(path);
        initByInputStream(in);
    }

    private void init(String configLocation) {
        InputStream inStream = this.getClass().getResourceAsStream(configLocation);
        try {
            props.load(inStream);
            this.temp_folder = props.getProperty("ifcengine.temp.folder");
        } catch (IOException ex) {/*logger.error("Failed to load ["+configLocation+"]");*/}
    }

    /**
     * 根据行号获取guid @param line 行号 @return guid
     */
    public String getGuidByLine(Integer line) {
        if (!data.containsKey(line)) {    /* 不存在，理论上throws NoFoundException较好 TODO*/
            return "";
        }/*IfcInstance ii = data.get(line);*/
        String possible_guid = getIfcPropertiesByLineData(data.get(line)).get(0); /*ii.getProperties().get(0); // 一般第一个属性是guid*/
        if (possible_guid.startsWith("'") && possible_guid.endsWith("'")) {    /* guid由''包围*/
            return possible_guid.substring(1, possible_guid.length() - 1);
        }
        return ""; /* 不存在guid值 TODO*/
    }

    /**
     * 根据行号获取ifc type @param line 行号 @return ifc type
     */
    public String getTypeByLine(Integer line) {
        if (!data.containsKey(line)) {    /* 不存在，理论上throws NoFoundException较好 TODO*/
            return "";
        }/*IfcInstance ii = data.get(line);*/
        return this.getIfcTypeByLineData(data.get(line));/*return ii.ifcType;*/
    }

    public List<String> getPropertiesByLine(Integer line) {
        if (!data.containsKey(line)) {    /* 不存在，理论上throws NoFoundException较好 TODO*/
            return null;
        }
        return this.getIfcPropertiesByLineData(data.get(line));
    }

    /**
     * 根据行号获取构件名称 @param line 行号 @return 构件名称
     */
    public String getNameByLine(Integer line) {
        if (!data.containsKey(line)) {    /* 不存在，理论上throws NoFoundException较好 TODO*/
            return "";
        }/*IfcInstance ii = data.get(line);*/
        String possible_name = getIfcPropertiesByLineData(data.get(line)).get(2);/*ii.getProperties().get(2); // 一般第一个属性是guid if(possible_guid.startsWith("'") && possible_guid.endsWith("'")){	// guid由''包围 return possible_guid.substring(1, possible_guid.length() - 1); }*/
        return IfcStringDecoder.decode(possible_name); /* 不存在guid值 TODO*/
    }

    /**
     * 根据行号获取构件名称 @param line 行号 @return 构件名称
     */
    public String getDescriptionByLine(Integer line) {
        if (!data.containsKey(line)) {    /* 不存在，理论上throws NoFoundException较好 TODO*/
            return "";
        }/*IfcInstance ii = data.get(line);*/
        String possible_desc = getIfcPropertiesByLineData(data.get(line)).get(3);/*ii.getProperties().get(3); // 一般第一个属性是guid if(possible_guid.startsWith("'") && possible_guid.endsWith("'")){	// guid由''包围 return possible_guid.substring(1, possible_guid.length() - 1); }*/
        return IfcStringDecoder.decode(possible_desc); /* 不存在guid值 TODO*/
    }/*//////////////////////////////////////////////////////////////////////////////////////// / IFC 关系处理 ///////////////////////////////////////////////////////////////////////////////////////*/

    public Map<Integer, List<Integer>> getAggregates() {
        Map<Integer, List<Integer>> mapAggregates = new HashMap<Integer, List<Integer>>();
        if (!mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELAGGREGATES)) {    /* 不存在*/
            return mapAggregates;
        }
        for (Integer line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELAGGREGATES)) {/*IfcInstance ii = data.get(line);*/
            List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));
            String key = ifcProperties.get(4);/*ii.getProperties().get(4);*/
            String rawAgg = ifcProperties.get(5).substring(1, ifcProperties.get(5).length() - 1); /* 去掉左右括号*/
            String[] aggs = rawAgg.split(",");
            List<Integer> lstTmp = new ArrayList<Integer>();
            for (String agg : aggs) lstTmp.add(getInteger(agg));
            mapAggregates.put(getInteger(key), lstTmp);
        }
        return mapAggregates;
    }

    public Map<Integer, List<Integer>> getContains() {
        Map<Integer, List<Integer>> mapContains = new HashMap<Integer, List<Integer>>();
        if (!mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELCONTAINEDINSPATIALSTRUCTURE)) {    /* 不存在*/
            return mapContains;
        }
        for (Integer line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELCONTAINEDINSPATIALSTRUCTURE)) {
            List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));/*IfcInstance ii = data.get(line);*/
            String key = ifcProperties.get(5);
            String rawCon = ifcProperties.get(4).substring(1, ifcProperties.get(4).length() - 1); /* 去掉左右括号*/
            String[] cons = rawCon.split(",");
            List<Integer> lstTmp = new ArrayList<Integer>();
            for (String con : cons) lstTmp.add(getInteger(con));
            mapContains.put(getInteger(key), lstTmp);
        }
        return mapContains;
    }

    /**
     * 获取空间上的父构件映射 @return
     */
    public Map<Integer, Integer> getSpatialParent() {
        Map<Integer, Integer> mapParent = new HashMap<Integer, Integer>();
        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELAGGREGATES)) {    /* Aggregates关系*/
            for (Integer line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELAGGREGATES)) {
                List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));/*IfcInstance ii = data.get(line);*/
                String key = ifcProperties.get(4);
                String rawAgg = ifcProperties.get(5).substring(1, ifcProperties.get(5).length() - 1); /* 去掉左右括号*/
                String[] aggs = rawAgg.split(",");
                for (String agg : aggs) {
                    String guid = getGuidByLine(getInteger(agg));
                    mapParent.put(getInteger(agg), getInteger(key));
                }
            }
        }
        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELCONTAINEDINSPATIALSTRUCTURE)) {    /* Contains 关系*/
            for (Integer line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELCONTAINEDINSPATIALSTRUCTURE)) {
                List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));/*IfcInstance ii = data.get(line);*/
                String key = ifcProperties.get(5);
                String rawCon = ifcProperties.get(4).substring(1, ifcProperties.get(4).length() - 1); /* 去掉左右括号*/
                String[] cons = rawCon.split(",");
                for (String con : cons) {
                    String guid = getGuidByLine(getInteger(con));
                    mapParent.put(getInteger(con), getInteger(key));
                }
            }
        }
        return mapParent;
    }/*//////////////////////////////////////////////////////////////////////////////////////// / IFC 构建关系处理 ///////////////////////////////////////////////////////////////////////////////////////*/

    /**
     * 将ifc管道连接关系以HASHMAP的形式呈现
     */
    public Map<String, List<String>> getConnect() {
        Map<String, String> connectelement = this.getConnectElement();
        Map<String, List<String>> connect = new HashMap<String, List<String>>();
        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELCONNECTSPORTS)) {    /* Contains 关系*/
            for (Integer line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELCONNECTSPORTS)) {
                List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));/*IfcInstance ii = data.get(line);*/
                String key = connectelement.get(ifcProperties.get(5));
                String rawCon = connectelement.get(ifcProperties.get(4)); /* 去掉左右括号*/
                if (!connect.containsKey(rawCon)) {
                    List<String> lstTemp = new ArrayList<String>();
                    connect.put(rawCon, lstTemp);
                }
                connect.get(rawCon).add(key);
            }
        }
        return connect;
    }/*管道与之相连的所有管道信息*/

    public Map<String, List<String>> getComponentRelationship() {
        Map<String, List<String>> Componentrelationship = new HashMap<String, List<String>>();
        Map<String, List<String>> connect = this.getConnect();
        for (String key : connect.keySet())
            for (String value : connect.get(key)) {
                if (!Componentrelationship.containsKey(key)) {
                    List<String> lstTemp = new ArrayList<String>();
                    Componentrelationship.put(key, lstTemp);
                }
                Componentrelationship.get(key).add(value);
                if (!Componentrelationship.containsKey(value)) {
                    List<String> lstTemp = new ArrayList<String>();
                    Componentrelationship.put(value, lstTemp);
                }
                Componentrelationship.get(value).add(key);
            }
        return Componentrelationship;
    }

    public Map<String, String> getConnectElement() {
        Map<String, String> connectelement = new HashMap<String, String>();
        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELCONNECTSPORTTOELEMENT)) {    /* Contains 关系*/
            for (Integer line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELCONNECTSPORTTOELEMENT)) {
                List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));/*IfcInstance ii = data.get(line);*/
                String key = ifcProperties.get(5);
                String rawCon = ifcProperties.get(4); /* 去掉左右括号*/
                connectelement.put(rawCon, key);
            }
        }
        return connectelement;
    }

    public String getParameterByLine(String line) {
        return data.get(getInteger(line));
    }

    public Map<String, Map<String, Map<String, List<String>>>> getSystemListByName() {
        Map<String, Map<String, Map<String, List<String>>>> systemlist = new HashMap<String, Map<String, Map<String, List<String>>>>();
        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELASSIGNSTOGROUP)) {    /* Group 关系*/
            for (Integer line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELASSIGNSTOGROUP)) {
                List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));
                String system = ifcProperties.get(6);
                List<String> systemProperties = getIfcPropertiesByLineData(this.getParameterByLine(system));
                String systemtrunk = IfcStringDecoder.decode(systemProperties.get(4));
                String systemfeeder = IfcStringDecoder.decode(systemProperties.get(2));
                String systemcomponent = ifcProperties.get(4).substring(1, ifcProperties.get(4).length() - 1); /* 去掉左右括号*/
                String[] cons = systemcomponent.split(",");
                if (!systemlist.containsKey(systemtrunk))
                    systemlist.put(systemtrunk, new HashMap<String, Map<String, List<String>>>());
                Map<String, Map<String, List<String>>> systemFeederList = systemlist.get(systemtrunk);
                if (!systemFeederList.containsKey(systemfeeder))
                    systemFeederList.put(systemfeeder, new HashMap<String, List<String>>());
                Map<String, List<String>> systemline = systemFeederList.get(systemfeeder);
                for (String con : cons)
                    if (this.getTypeByLine(getInteger(con)).equals("IFCDISTRIBUTIONPORT")) continue;
                    else {
                        if (!systemline.containsKey(system)) {
                            List<String> lstTemp = new ArrayList<String>();
                            systemline.put(system, lstTemp);
                        }
                        systemline.get(system).add(con);
                    }
            }
        }
        return systemlist;
    }

    public Map<String, List<String>> getSystemGroupList() {
        Map<String, List<String>> systemlist = new HashMap<String, List<String>>();
        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELASSIGNSTOGROUP)) {    /* Group 关系*/
            for (Integer line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELASSIGNSTOGROUP)) {
                List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));
                String system = ifcProperties.get(6);
                String systemcomponent = ifcProperties.get(4).substring(1, ifcProperties.get(4).length() - 1); /* 去掉左右括号*/
                String[] cons = systemcomponent.split(",");
                for (String con : cons)
                    if (this.getTypeByLine(getInteger(con)).equals("IFCDISTRIBUTIONPORT")) continue;
                    else {
                        if (!systemlist.containsKey(system)) {
                            List<String> lstTemp = new ArrayList<String>();
                            systemlist.put(system, lstTemp);
                        }
                        systemlist.get(system).add(con);
                    }
            }
        }
        return systemlist;
    }

    public List<String> getSystemList() {
        List<String> systemlist = new ArrayList<>();
        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELASSIGNSTOGROUP))
            for (Integer line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELASSIGNSTOGROUP)) {
                List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));
                String system = ifcProperties.get(6);
                systemlist.add(system);
            }
        return systemlist;
    }

    private Map<String, String> systemGroup() {
        Map<String, String> systemgroup = new HashMap<>();
        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELASSIGNSTOGROUP))
            for (Integer line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELASSIGNSTOGROUP)) {
                List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));
                String system = ifcProperties.get(6);
                systemgroup.put(system, "#"+line);
            }
        return systemgroup;
    }

    public Map<String, String> getComponentToSystem() {
        Map<String, String> systemgroup = new HashMap<>();
        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELASSIGNSTOGROUP))
            for (Integer line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELASSIGNSTOGROUP)) {
                List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));
                String system = ifcProperties.get(6);
                List<String> systemProperties = getIfcPropertiesByLineData(this.getParameterByLine(system));
                String systemtrunk = IfcStringDecoder.decode(systemProperties.get(4));
                systemgroup.put(system, systemtrunk);
            }
        return systemgroup;
    }

    public Map<String, String> getComponentIncludeSystem() {
        Map<String, String> systemlist = new HashMap<String, String>();
        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELASSIGNSTOGROUP)) {    /* Group 关系*/
            for (Integer line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELASSIGNSTOGROUP)) {
                List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));
                String system =getGuidByLine(getInteger(ifcProperties.get(6))) ;
                String systemcomponent = ifcProperties.get(4).substring(1, ifcProperties.get(4).length() - 1); /* 去掉左右括号*/
                String[] cons = systemcomponent.split(",");
                for (String con : cons)
                    if (this.getTypeByLine(getInteger(con)).equals("IFCDISTRIBUTIONPORT")) continue;
                    else systemlist.put(getGuidByLine(getInteger(con)), system);
            }
        }
        return systemlist;
    }

    public List<String> getSystemAssignsByLine(String line) {
        List<String> assign = new ArrayList<>();
        Map<String, String> systemgroup = this.systemGroup();
        List<String> ifcProperties = getIfcPropertiesByLineData(data.get(systemgroup.get(line)));
        String systemcomponent = ifcProperties.get(4).substring(1, ifcProperties.get(4).length() - 1); /* 去掉左右括号*/
        String[] cons = systemcomponent.split(",");
        for (String con : cons)
            if (this.getTypeByLine(getInteger(con)).equals("IFCDISTRIBUTIONPORT")) continue;
            else assign.add(con);
        return assign;
    }

    public String getSystemTypeByLine(String line) {
        if (!data.containsKey(line)) {    /* 不存在，理论上throws NoFoundException较好 TODO*/
            return "";
        }/*IfcInstance ii = data.get(line);*/
        String possible_name = getIfcPropertiesByLineData(data.get(getInteger(line))).get(4);/*ii.getProperties().get(2); // 一般第一个属性是guid if(possible_guid.startsWith("'") && possible_guid.endsWith("'")){	// guid由''包围 return possible_guid.substring(1, possible_guid.length() - 1); }*/
        return IfcStringDecoder.decode(possible_name);
    }/*//////////////////////////////////////////////////////////////////////////////////////// / IFC 属性处理 ///////////////////////////////////////////////////////////////////////////////////////*/

    /**
     * 将ifc属性组织到内存中等待外部使用
     */
    public Map<String, Object> getAttributes() {
        Map<String, List<String>> mapIns2Properties = getRelDefProperties();
        if (mapIns2Properties == null || mapIns2Properties.size() == 0) return null;
        Map<String, Object> mapIns2Attr = new HashMap<String, Object>();
        for (String e : mapIns2Properties.keySet()) {
            JSONObject attrDataMap = new JSONObject();
            for (String setLineNum : mapIns2Properties.get(e)) {/*IfcInstance ifcIns = data.get(setLineNum);*/
                String ifcType = this.getIfcTypeByLineData(data.get(getInteger(setLineNum) ));
                Map<String, Object> temp = null;
                if (IfcPropertyType.IFC_TYPE_IFCELEMENTQUANTITY.equals(ifcType))
                    temp = findIfcPropertySet(data.get(getInteger(setLineNum)));
                else if (IfcPropertyType.IFC_TYPE_IFCPROPERTYSET.equals(ifcType))
                    temp = findIfcPropertySet(data.get(getInteger(setLineNum)));
                else if (ifcType.endsWith("TYPE")) temp = findIfcPropertyByTypeSet(data.get(getInteger(setLineNum)));
                else if (IfcPropertyType.IFC_TYPE_IFCMATERIALLAYERSET.equals(ifcType))
                    temp = findIfcMterialByMaterialSet(data.get(getInteger(setLineNum)));
                else if (IfcPropertyType.IFC_TYPE_IFCMATERIALLAYERSETUSAGE.equals(ifcType))
                    temp = findIfcMterialByMaterialSetUsage(data.get(getInteger(setLineNum)));
                else if (IfcPropertyType.IFC_TYPE_IFCMATERIAL.equals(ifcType))
                    temp = findIfcMterialByMaterial(data.get(getInteger(setLineNum)));
                else if (IfcPropertyType.IFC_TYPE_IFCMATERIALLIST.equals(ifcType))
                    temp = findIfcMterialByMaterialList(data.get(getInteger(setLineNum)));
                else {/* TODO: why??*/
                    temp = findIfcPropertySet(data.get(getInteger(setLineNum)));
                }
                if (temp != null) attrDataMap.putAll(temp);
            }
            mapIns2Attr.put(e, attrDataMap);
        }
        return mapIns2Attr;
    }

    /**
     * 获取构件对应的IFCPROPERTYSET的行号 @param lineString
     */
    private Map<String, List<String>> getRelDefProperties() {
        Map<String, List<String>> mapIns2Properties = new HashMap<String, List<String>>();
        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELDEFINESBYPROPERTIES)) {    /* IFCRELDEFINESBYPROPERTIES 关系*/
            for (Integer line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELDEFINESBYPROPERTIES)) {
                List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));/*IfcInstance ii = data.get(line);*/
                String property = ifcProperties.get(5);
                String rawIns = ifcProperties.get(4);
                if (rawIns.length() > 2) {
                    rawIns = rawIns.substring(1, rawIns.length() - 1);
                    String[] vals = rawIns.split(",");
                    for (String val : vals) {
                        String ins = val.trim();
                        if (!mapIns2Properties.containsKey(ins)) {
                            ArrayList<String> temp = new ArrayList<String>();
                            mapIns2Properties.put(ins, temp);
                        }
                        mapIns2Properties.get(ins).add(property);
                    }
                }
            }
        }
        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELDEFINESBYTYPE)) {    /* IFCRELDEFINESBYTYPE 关系*/
            for (Integer line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELDEFINESBYTYPE)) {
                List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));/*IfcInstance ii = data.get(line);*/
                String property = ifcProperties.get(5);
                String rawIns = ifcProperties.get(4);
                if (rawIns.length() > 2) {
                    rawIns = rawIns.substring(1, rawIns.length() - 1);
                    String[] vals = rawIns.split(",");
                    for (String val : vals) {
                        String ins = val.trim();
                        if (!mapIns2Properties.containsKey(ins)) {
                            ArrayList<String> temp = new ArrayList<String>();
                            mapIns2Properties.put(ins, temp);
                        }
                        mapIns2Properties.get(ins).add(property);
                    }
                }
            }
        }
        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELASSOCIATESMATERIAL)) {    /* IFCRELDEFINESBYTYPE 关系*/
            for (Integer line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELASSOCIATESMATERIAL)) {
                List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));/*IfcInstance ii = data.get(line);*/
                String property = ifcProperties.get(5);
                String rawIns = ifcProperties.get(4);
                if (rawIns.length() > 2) {
                    rawIns = rawIns.substring(1, rawIns.length() - 1);
                    String[] vals = rawIns.split(",");
                    for (String val : vals) {
                        String ins = val.trim();
                        if (!mapIns2Properties.containsKey(ins)) {
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
     * 获取ifc属性集 @param lineString @return
     */
    private Map<String, Object> findIfcPropertySet(String line) {
        try {/*if (ifcIns == null){// || !IFC_TYPE_IFCPROPERTYSET.equals(ifcIns.ifcType)) { return null; }*//*            if(line.equals("IFCPROPERTYSET('2vREN6ocHC0eaMbvRTlXwJ',#260,'Pset_LightFixtureTypeCommon','',(#1291,#1292,#1293,#1294,#1295));") ) { int a=1; }*/
            List<String> ifcProperties = this.getIfcPropertiesByLineData(line);
            Map<String, String> propertyValue = new HashMap<String, String>();
            String strSetRawName = ifcProperties.get(2);
            String propertySetName = IfcStringDecoder.decode(strSetRawName);/*('33RdwGpa54sAOG05AQJQW8',#29,'Pset_WindowCommon',$,(#5004,#5009))获取对应的单个属性值的行号*/
            String insProperties = ifcProperties.get(4);
            if (insProperties.length() > 1) {    /* 去掉左右括号*/
                if (insProperties.charAt(0) == '(') {
                    insProperties = insProperties.substring(1);
                    if (insProperties.endsWith(")"))
                        insProperties = insProperties.substring(0, insProperties.length() - 1);
                }/*获取单属性数组*/
                String[] ret = insProperties.split(",");
                for (String e : ret) {
                    Map<String, String> mapTmp = getIfcSingleValue(data.get(getInteger(e.trim())));
                    if (mapTmp == null) continue;
                    propertyValue.putAll(mapTmp);
                }
            }
            Map<String, Object> setMap = new HashMap<String, Object>();
            setMap.put(propertySetName, propertyValue);
            return setMap;
        } catch (Exception e) {
            System.out.print(line + "出现异常参数\n");
            return null;
        }
    }

    /**
     * 获取ifc属性集 @param lineString @return
     */
    private Map<String, String> findIfcMaterialSet(String line) {/*if (ifcIns == null){// || !IFC_TYPE_IFCPROPERTYSET.equals(ifcIns.ifcType)) { return null; }*/
        List<String> ifcProperties = this.getIfcPropertiesByLineData(line);
        Map<String, String> propertyValue = new HashMap<String, String>();
        String materialline = ifcProperties.get(0).trim();
        List<String> materialData = this.getIfcPropertiesByLineData(data.get(getInteger(ifcProperties.get(0).trim())));
        String strSetRawName = materialData.get(0);
        String propertySetName = IfcStringDecoder.decode(strSetRawName);
        propertyValue.put(propertySetName, ifcProperties.get(1).replace(".", "").trim());
        return propertyValue;
    }

    /**
     * 获取ifcType对应的属性集 @param lineString @return
     */
    private Map<String, Object> findIfcPropertyByTypeSet(String line) {
        String ifcType = this.getIfcTypeByLineData(line);
        if (!ifcType.endsWith("TYPE")) return null;
        List<String> ifcProperties = this.getIfcPropertiesByLineData(line);
        Map<String, Object> singleValueMap = new HashMap<String, Object>();
        String strRawPropertySets = ifcProperties.get(5);
        String typename = IfcStringDecoder.decode(ifcProperties.get(2));
        if (strRawPropertySets.startsWith("(") && strRawPropertySets.endsWith(")")) {
            String[] propertySets = strRawPropertySets.substring(1, strRawPropertySets.length() - 1).split(",");
            for (String propertySet : propertySets) {
                Map<String, Object> temp = findIfcPropertySet(data.get(getInteger(propertySet.trim())));
                if (temp != null) singleValueMap.putAll(temp);
            }
        }
        Map<String, String> typeName = new HashMap<>();
        typeName.put("Type", typename);
        if (!typename.equals("")) singleValueMap.put("Type", typeName);
        return singleValueMap;
    }

    /**
     * 获取ifcType对应的属性集 @param lineString @return
     */
    private Map<String, Object> findIfcMterialByMaterialSet(String line) {/*		String ifcType = this.getIfcTypeByLineData(line);*/
        List<String> ifcProperties = this.getIfcPropertiesByLineData(line);
        Map<String, Object> singleValueMap = new HashMap<String, Object>();
        Map<String, String> materialMap = new HashMap<>();
        String strRawPropertySets = ifcProperties.get(0);
        if (strRawPropertySets.startsWith("(") && strRawPropertySets.endsWith(")")) {
            String[] propertySets = strRawPropertySets.substring(1, strRawPropertySets.length() - 1).split(",");
            for (String propertySet : propertySets) {
                Map<String, String> temp = findIfcMaterialSet(data.get(getInteger(propertySet.trim())));
                if (temp != null) materialMap.putAll(temp);
            }
        }
        singleValueMap.put("Material", materialMap);
        return singleValueMap;
    }

    private Map<String, Object> findIfcMterialByMaterialSetUsage(String line) {
        List<String> ifcProperties = this.getIfcPropertiesByLineData(line);
        String materialline = data.get(getInteger(ifcProperties.get(0).trim()));
        Map<String, Object> singleValueMap = this.findIfcMterialByMaterialSet(materialline);
        return singleValueMap;
    }

    private Map<String, Object> findIfcMterialByMaterial(String line) {
        List<String> ifcProperties = this.getIfcPropertiesByLineData(line);
        String propertySetName = IfcStringDecoder.decode(ifcProperties.get(0).trim());
        Map<String, String> materialMap = new HashMap<String, String>();
        materialMap.put("Material", propertySetName);
        Map<String, Object> singleValueMap = new HashMap<String, Object>();
        singleValueMap.put("MaterialSet", materialMap);
        return singleValueMap;
    }

    private String getMaterialName(String line) {
        List<String> ifcProperties = this.getIfcPropertiesByLineData(data.get(getInteger(line)));
        return IfcStringDecoder.decode(ifcProperties.get(0).trim());
    }

    private Map<String, Object> findIfcMterialByMaterialList(String line) {
        List<String> ifcProperties = this.getIfcPropertiesByLineData(line);
        Map<String, Object> singleValueMap = new HashMap<String, Object>();
        List<String> materialMap = new ArrayList<>();
        Map<String, List<String>> materialList = new HashMap<>();
        String strRawPropertySets = ifcProperties.get(0);
        if (strRawPropertySets.startsWith("(") && strRawPropertySets.endsWith(")")) {
            String[] propertySets = strRawPropertySets.substring(1, strRawPropertySets.length() - 1).split(",");
            for (String propertySet : propertySets) {
                String temp = getMaterialName(propertySet.trim());
                if (temp != null) materialMap.add(temp);
            }
        }
        materialList.put("MaterialList", materialMap);
        singleValueMap.put("MaterialList", materialList);
        return singleValueMap;
    }

    /**
     * 获取单个ElementQuantity的属性值 @param dataLine @return
     */
    private Map<String, String> getIfcQuantityValue(String line) {
        if (line == null) return null;
        List<String> ifcProperties = this.getIfcPropertiesByLineData(line);
        if (ifcProperties.size() != 4) return null;
        Map<String, String> mapTmp = new HashMap<String, String>();
        String name = IfcStringDecoder.decode(ifcProperties.get(0));
        String value = ifcProperties.get(3);
        mapTmp.put(name, value);
        return mapTmp;
    }

    /**
     * 获取ifc单个属性 @param dataLine @return
     */
    private Map<String, String> getIfcSingleValue(String line) {
        if (line == null) return null;
        List<String> ifcProperties = this.getIfcPropertiesByLineData(line);
        if (ifcProperties.size() != 4) return null;
        Map<String, String> mapTmp = new HashMap<String, String>();
        String name = IfcStringDecoder.decode(ifcProperties.get(0));
        String value = propertyValue(ifcProperties.get(2));
        mapTmp.put(name, value);
        return mapTmp;
    }

    /**
     * 将属性值关联单位 @param dataLine @return
     */
    private String propertyValue(String propertyVal) {
        if (propertyVal == null || propertyVal.equals("")) return null;
        String valueType = propertyVal.split("\\(")[0];/*TODO: 提取ifc单位*/
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
        if (propertyVal == null || propertyVal.length() == 0) return "";
        String ret = "";
        boolean isNum = false;
        for (int i = 0; i < propertyVal.length(); i++) {
            if (propertyVal.charAt(i) == ')') isNum = false;
            if (isNum) ret += propertyVal.charAt(i);
            if (propertyVal.charAt(i) == '(') isNum = true;
        }
        ret = ret.replace("'", "");
        return IfcStringDecoder.decode(ret);
    }/*//////////////////////////////////////////////////////////////////////////////////////// / IFC 几何处理 ///////////////////////////////////////////////////////////////////////////////////////*/

    long totalSize = 0;

    public boolean getSeg() {
        return seg;
    }

    public void pointWrite(Map<Integer ,String> type) throws Exception {
        FileOutputStream fos = new FileOutputStream("D:\\csv\\point.csv");
        OutputStreamWriter osw = new OutputStreamWriter(fos, "GBK");
        CSVFormat csvFormat = CSVFormat.DEFAULT.withHeader("Id", "Type");
        CSVPrinter csvPrinter = new CSVPrinter(osw, csvFormat);
//        csvPrinter = CSVFormat.DEFAULT.withHeader("姓名", "年龄", "家乡").print(osw);
        for (Map.Entry<Integer,String> entry:type.entrySet()) {
                    csvPrinter.printRecord(entry.getKey(), entry.getValue());
        }
        csvPrinter.flush();
        csvPrinter.close();

    }

    public void edgeWrite(Map<Integer ,Set<Integer>> connects) throws Exception {
        FileOutputStream fos = new FileOutputStream("D:\\csv\\edge.csv");
        OutputStreamWriter osw = new OutputStreamWriter(fos, "GBK");
        CSVFormat csvFormat = CSVFormat.DEFAULT.withHeader("Source", "Target");
        CSVPrinter csvPrinter = new CSVPrinter(osw, csvFormat);
//        csvPrinter = CSVFormat.DEFAULT.withHeader("姓名", "年龄", "家乡").print(osw);
        for (Integer key:connects.keySet()) {
            if(connects.get(key).size()!=0)
            {
                for(Integer target :connects.get(key))
                csvPrinter.printRecord(key, target);
            }
        }
        csvPrinter.flush();
        csvPrinter.close();

    }
    

    /** 
    * @Description: 获取异质树 
    * @Param: [] 
    * @return: java.util.Map<java.lang.Integer,java.util.Set<java.lang.Integer>> 
    * @Author: Wang 
    * @Date: 2020/4/9 
    */ 
    
    public Map<Integer ,Set<Integer>> getHeterogeneous() throws Exception {
        Map<Integer ,Set<Integer>> connects = new HashMap<>();
        Map<Integer,String> types = new HashMap<>();
        String[] sixToFive = {IfcPropertyType.IFC_TYPE_IFCRELCONTAINEDINSPATIALSTRUCTURE,IfcPropertyType.IFC_TYPE_IFCRELASSOCIATESMATERIAL,IfcPropertyType.IFC_TYPE_IFCRELASSOCIATESCLASSIFICATION,IfcPropertyType.IFC_TYPE_IFCRELDEFINESBYPROPERTIES,IfcPropertyType.IFC_TYPE_IFCRELDEFINESBYTYPE};
        String[] fiveToSix = {IfcPropertyType.IFC_TYPE_IFCRELAGGREGATES};
        String[] fourToThree = {IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION};
        String[] threeToFour = {IfcPropertyType.IFC_TYPE_IFCARBITRARYPROFILEDEFWITHVOIDS};
        String[] oneToTwo = {IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM};
        String[] nineToEight = {IfcPropertyType.IFC_TYPE_IFCPROJECT};
        over: for (Integer line:data.keySet())
        {

            List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));
            String type = getTypeByLine(line);
            types.put(line,type);
            for(String _t:nineToEight)
                if(_t.equals(type))
                {
                    Set<Integer> lstTmp = new HashSet<>();
                    Set<Integer> lstKey = new HashSet<>();
                    Integer key = getInteger(ifcProperties.get(8)) ;
                    Integer his = getInteger(ifcProperties.get(1)) ;
                    String rawCon = ifcProperties.get(7).substring(1, ifcProperties.get(7).length() - 1); /* 去掉左右括号*/
                    String[] cons = rawCon.split(",");
                    for(String con:cons)
                    {
                        lstTmp.add(getInteger(con));
                    }
                    lstTmp.add(his);
                    lstKey.add(line);
                    addConnects(connects,line,lstTmp);
                    addConnects(connects,key,lstKey);
                    continue over;
                }
            for(String _t:sixToFive)
                if(_t.equals(type))
                {
                    Set<Integer> lstTmp = new HashSet<>();
                    Set<Integer> lstKey = new HashSet<>();
                    Integer key = getInteger(ifcProperties.get(5)) ;
                    Integer his = getInteger(ifcProperties.get(1)) ;
                    String rawCon = ifcProperties.get(4).substring(1, ifcProperties.get(4).length() - 1); /* 去掉左右括号*/
                    String[] cons = rawCon.split(",");
                    for(String con:cons)
                    {
                        lstTmp.add(getInteger(con));
                    }
                    lstTmp.add(his);
                    lstKey.add(line);
                    addConnects(connects,line,lstTmp);
                    addConnects(connects,key,lstKey);
                    continue over;
                }
            for(String _t:fiveToSix)
                if(_t.equals(type))
                {
                    Set<Integer> lstTmp = new HashSet<>();
                    Set<Integer> lstKey = new HashSet<>();
                    Integer key = getInteger(ifcProperties.get(4)) ;
                    Integer his = getInteger(ifcProperties.get(1)) ;
                    String rawCon = ifcProperties.get(5).substring(1, ifcProperties.get(5).length() - 1); /* 去掉左右括号*/
                    String[] cons = rawCon.split(",");
                    for(String con:cons)
                    {
                        lstTmp.add(getInteger(con));
                    }
                    lstTmp.add(his);
                    lstKey.add(line);
                    addConnects(connects,line,lstTmp);
                    addConnects(connects,key,lstKey);
                    continue over;
                }
            for(String _t:fourToThree)
                if(_t.equals(type))
                {
                    Set<Integer> lstTmp = new HashSet<>();
                    Set<Integer> lstKey = new HashSet<>();
                    Integer key = getInteger(ifcProperties.get(3)) ;
                    String rawCon = ifcProperties.get(2).substring(1, ifcProperties.get(2).length() - 1); /* 去掉左右括号*/
                    String[] cons = rawCon.split(",");
                    for(String con:cons)
                    {
                        lstTmp.add(getInteger(con));
                    }
                    lstKey.add(line);
                    addConnects(connects,line,lstTmp);
                    addConnects(connects,key,lstKey);
                    continue over;
                }
            for(String _t:threeToFour)
                if(_t.equals(type))
                {
                    Set<Integer> lstTmp = new HashSet<>();
                    Set<Integer> lstKey = new HashSet<>();
                    Integer key = getInteger(ifcProperties.get(2)) ;
                    String rawCon = ifcProperties.get(3).substring(1, ifcProperties.get(3).length() - 1); /* 去掉左右括号*/
                    String[] cons = rawCon.split(",");
                    for(String con:cons)
                    {
                        lstTmp.add(getInteger(con));
                    }
                    lstKey.add(line);
                    addConnects(connects,line,lstTmp);
                    addConnects(connects,key,lstKey);
                    continue over;
                }
            for(String _t:oneToTwo)
                if(_t.equals(type))
                {
                    Set<Integer> lstTmp = new HashSet<>();
                    Set<Integer> lstKey = new HashSet<>();
                    Integer key = getInteger(ifcProperties.get(0)) ;
                    if(key!=null)
                    {
                        String rawCon = ifcProperties.get(1).substring(1, ifcProperties.get(1).length() - 1); /* 去掉左右括号*/
                        String[] cons = rawCon.split(",");
                        for(String con:cons)
                        {
                            lstTmp.add(getInteger(con));
                        }
                        lstKey.add(line);
                        addConnects(connects,line,lstTmp);
                        addConnects(connects,key,lstKey);
                        continue over;
                    }else break  ;

                }
            for(String ifcProperty:ifcProperties)
            {
                Set<Integer> lstTmp = new HashSet<>();
                if(ifcProperty.startsWith("(#"))
                {
                    String rawCon = ifcProperty.substring(1, ifcProperty.length() - 1); /* 去掉左右括号*/
                    String[] cons = rawCon.split(",");
                    for(String con:cons)
                    {
                       lstTmp.add(getInteger(con));
                    }
                }if(ifcProperty.startsWith("#")){
                    lstTmp.add(getInteger(ifcProperty));
                }
                addConnects(connects,line,lstTmp);
            }
        }

        pointWrite(types);
        edgeWrite(connects);
        return connects;
    }

    /** 
    * @Description: 获取潜在关联关系数据 
    * @Param: [] 
    * @return: void 
    * @Author: Wang
    * @Date: 2020/4/9 
    */ 
    
    public void printfData()
    {
        Set<String> type = new HashSet<>();
        for (Integer line:data.keySet())
        {
            if(getTypeByLine(line).trim().equals("IFCPOSTALADDRESS"))
            {
                int a=0;
            }

            List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));
            int i =0;
            int b =0;

            for(String strRaw :ifcProperties)
            {
                if(strRaw.startsWith("#")&&b==0)
                {i++;
                b++;}
                if(strRaw.startsWith("(#"))
                {i++;}
            }
            if(i>1)
            {
                type.add(getTypeByLine(line));
                System.out.print(line+"="+data.get(line)+"\n");
            }
        }
        System.out.print(type+"\n");
        System.out.print(type.size());
    }

    private void addConnects(Map<Integer ,Set<Integer>> connects,Integer key,Set<Integer> children)
    {
        if(connects.containsKey(key))
        {
        connects.get(key).addAll(children);
        }else {
        connects.put(key,children);
        }

    }

//    public List<String> getGeometrySlices() {
//        List<String> lstGeometrySlices = new ArrayList<String>();
//        String path = writeSourceToFile("source");
//            lstGeometrySlices.add(path);
//            return lstGeometrySlices;
//    }
    public List<String> getGeometrySlices() {
        Date date =new Date();
//        /**
//        * @Description:  内存监控
//        * @Param: []
//        * @return: java.util.List<java.lang.String>
//        * @Author: Wang
//        * @Date: 2019/12/30
//        */
//        OperatingSystemMXBean mem = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
//        long star = mem.getFreePhysicalMemorySize();
//        setSpaceBoudary();
        List<String> lstGeometrySlices = new ArrayList<String>();
        LinkedHashSet<Integer> lstPossibleGeometryInstances = getPossibleGeometryInstances();  /*获取contains和Aggregates*/
        Map<Integer, List<Integer>> mapRep2Ins = new HashMap<Integer, List<Integer>>();/*        Map<String, List<String>> mapTypeToLine = new HashMap<String, List<String>>();*/
        for (Integer possibleIns : lstPossibleGeometryInstances) {
            String ifcType = this.getTypeByLine(possibleIns);
            if (ifcType == "") continue;
            if (IfcPropertyType.IFC_TYPE_IFCPROJECT.equals(ifcType)) /* IFCProject没有形状*/ continue;
            List<String> ifcProperties = this.getPropertiesByLine(possibleIns);
            if (ifcProperties.size() < 7) continue;
            String repStr = ifcProperties.get(6);    /* 获取IfcRepresentation*/
            if (repStr.length() < 2)    /* IfcRepresentation以#开头，长度必然不小于2；长度小于2，要么为空，要么为$；*/ continue;
            Integer rep = getInteger(repStr);
            if (!mapRep2Ins.containsKey(rep)) {
                List<Integer> lstTmp = new ArrayList<Integer>();
                mapRep2Ins.put(rep, lstTmp);
            }
            mapRep2Ins.get(rep).add(possibleIns);
            /*            String typeName = getTypeByLine(possibleIns); if(!mapTypeToLine.containsKey(typeName)) { List<String> lstTmp = new ArrayList<String>(); mapTypeToLine.put(typeName, lstTmp); } mapTypeToLine.get(typeName).add(rep);*/
        }/*		long Time3=System.currentTimeMillis(); System.out.println("获取IfcRepresentation的时间为：  "+(Time3-Time2)/(double)1000 ); 获取每个IFCRepresentation的图*/
        Map<Integer, LinkedHashSet<Integer>> mapItems = new HashMap<Integer, LinkedHashSet<Integer>>();

//        System.out.println(date.toString()+ "  1\n");

        Map<Integer, Boolean> mapVisited =  new HashMap<Integer, Boolean>();/*!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!new HashMap<String, Boolean>();// for(String rep : mapRep2Ins.keySet()){ mapVisited.put(rep, true); }*/
//        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM))
//            for (String insStyledItem : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM))
//                mapVisited.put(insStyledItem, true);
//        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION))
//            for (String insMatDefRep : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION))
//                mapVisited.put(insMatDefRep, true);
        retrieveStyledItemMap(mapItems, mapVisited);
        retrieveMaterialRepDef(mapItems, mapVisited);
        retrieveRelVoidElement(mapItems, mapVisited);
        Map<Integer, Integer> mapIns2Rep = new HashMap<Integer, Integer>(); /* 构件 -> 表现*/

//        System.out.println(date.toString()+"2\n");

        for (Integer rep : mapRep2Ins.keySet())
        {
            retrieveSubinstances(rep, mapItems, mapVisited);
            for (Integer ins : mapRep2Ins.get(rep))
                mapIns2Rep.put(ins, rep);
        }
        List<Integer> lstReps = new ArrayList<Integer>();
        lstReps.addAll(mapRep2Ins.keySet());
        Map<Integer, Set<Integer>> mapGeometrySlices = new HashMap<Integer, Set<Integer>>();

        for (Integer m : lstReps) {
            Set<Integer> projecthead = this.getProjectHead();
            Set<Integer> setTmp = getGeometryDescriptionSlices(m, mapRep2Ins.get(m), mapItems, mapVisited);  /*生成每个构件的子文件*/

//            Set<Integer> spaceBoudaryLines = getSpaceBoundary(mapRep2Ins.get(m),mapItems,mapVisited); //spaceboundary
            projecthead.addAll(setTmp);
//            projecthead.addAll(spaceBoudaryLines);
            mapGeometrySlices.put(m, projecthead);
            totalSize += projecthead.size();
        }
        if (mapGeometrySlices.size() == 0) {
            String path = writeSourceToFile("source");
            lstGeometrySlices.add(path);
            return lstGeometrySlices;
        }
        Map<Set<Integer>, Set<Integer>> mapConnIns = getRelConnPathSets();
        Integer rep_conn;
        Set<Integer> connRep = new HashSet<Integer>();
        for (Set<Integer> connIns : mapConnIns.keySet()) {
            for (Integer ins : connIns)
                if (mapIns2Rep.containsKey(ins)) {    /* 有些构件没有3D shape，神奇。*/
                    connRep.add(mapIns2Rep.get(ins));
                }/* 生成合并后的几何对象*/
            rep_conn = connIns.hashCode();
            Set<Integer> connGeometry = new HashSet<Integer>();
            for (Integer rep : connRep) {
                connGeometry.addAll(mapGeometrySlices.get(rep));
                mapGeometrySlices.remove(rep);
            }/* 需要加上IFCRELCONNECTSPATHELEMENTS实例*/
            connGeometry.addAll(mapConnIns.get(connIns));
            mapGeometrySlices.put(rep_conn, connGeometry);
            connRep.clear();
        }
        long avgSize = totalSize / EXPECTED_NUMBER_OF_FILE;/* 期望文件数*/
        long thSingleSize = (long) (avgSize * 2); /* 单文件数*/
        Set<Integer> comContents = new HashSet<Integer>();
        /** @Description: 文件分组 @Param: [] @return: java.util.List<java.lang.String> @Author: Wang @Date: 2019/9/10 */
        List<Set<Integer>> sets = new ArrayList<>();
        int max_len = 0, max_size = 0, min_size = data.size(), min_len = Integer.MAX_VALUE, total_size = 0, _total_size = 0, final_size = 0, splits = 0;
        Map<String, String> mapSlices = new HashMap<String, String>();
        Map<String, Boolean> mapTr = new HashMap<String, Boolean>();
        Map<Integer, Set<Integer>> _mapSlices = new HashMap<Integer, Set<Integer>>();
        for (int i = 0; i < this.EXPECTED_NUMBER_OF_FILE; i++) {
            Set<Integer> _set = new HashSet<Integer>();
            _mapSlices.put(i, _set);
        }
        int empty_index = 0;
        for (Integer rep : mapGeometrySlices.keySet()) {
            Set<Integer> contents = mapGeometrySlices.get(rep);
//            if (contents.size() > thSingleSize) {
//                seg = true;
//                /** @Description: 构件拆分代码 @Param: [] @return: java.util.List<java.lang.String> @Author: Wang @Date: 2019/6/14 */
//                List<Set<Integer>> prdSegs = retrieveProductSegments(rep, mapRep2Ins, mapItems, 50000);
//                sets.addAll(prdSegs);
//            } else
         sets.add(contents);
        }
        long start = System.currentTimeMillis();

        for (Set<Integer> contents : sets) {
            if (empty_index == 0) {
                _mapSlices.get(empty_index).addAll(contents);
                empty_index = 1;
                continue;
            }
            int candidate_min = 0;
            for (int i = 0; i < empty_index; i++)
                if (_mapSlices.get(i).size() < _mapSlices.get(candidate_min).size()) candidate_min = i;
            if (empty_index < this.EXPECTED_NUMBER_OF_FILE) {
                _mapSlices.get(empty_index).addAll(contents);
                empty_index = empty_index + 1;
            } else _mapSlices.get(candidate_min).addAll(contents);
        }
        long startTime = System.currentTimeMillis();
//        System.out.println("文件分组时间：  "+(startTime-start)/(double)1000);
//        getMemInfo(star,"占用内存大小：");

//        Set<Integer> aDEFINES =getDEFINES(mapItems,mapVisited);  //associates
//        Set<Integer> associates = getAggre(mapItems,mapVisited);  //aggre
//        _mapSlices.get(0).addAll(associates);
//        _mapSlices.get(0).addAll(aDEFINES);

//        if (seg == false)
            for (int i = 0; i < empty_index; i++)
            {
                Set<Integer> writeOut = _mapSlices.get(i);
//                writeOut.addAll(associates);
                lstGeometrySlices.add(writeSetToFile(writeOut, i));
            }
//        else for (int i = 0; i < empty_index; i++)
//        {
//            Set<Integer> writeOut = _mapSlices.get(i);
////            writeOut.addAll(associates);
//            lstGeometrySlices.add(writeSetToFile_LargeProduct(writeOut, i));
//        }


        return lstGeometrySlices;
    }

    private Set<Integer> getAggre(Map<Integer, LinkedHashSet<Integer>> mapItems, Map<Integer, Boolean> mapVisited){
        List<Integer>spaceBoudnaryLine =  mapType2Line.get("IFCRELAGGREGATES");
        Map<Integer, Boolean> mapWritten = new HashMap<>();
        for(Integer lineId:spaceBoudnaryLine)
        {
            appendItemRepresentationIteratively(null, lineId, mapItems, mapWritten, mapVisited);
            mapWritten.put(lineId,true);
        }
        return mapWritten.keySet();
    }
    private Set<Integer> getDEFINES(Map<Integer, LinkedHashSet<Integer>> mapItems, Map<Integer, Boolean> mapVisited){
        List<Integer>spaceBoudnaryLine =  mapType2Line.get("IFCRELDEFINESBYPROPERTIES");
        Map<Integer, Boolean> mapWritten = new HashMap<>();
        for(Integer lineId:spaceBoudnaryLine)
        {
            appendItemRepresentationIteratively(null, lineId, mapItems, mapWritten, mapVisited);
            mapWritten.put(lineId,true);
        }
        return mapWritten.keySet();
    }

    private void setSpaceBoudary(){
        List<Integer>spaceBoudnaryLine =  mapType2Line.get("IFCRELSPACEBOUNDARY");
        for (Integer line : spaceBoudnaryLine)
        {
            List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));/*IfcInstance ii = data.get(line);*/
            String key = ifcProperties.get(5);
            if(spaceBoudary.containsKey(getInteger(key)))
            {
                spaceBoudary.get(getInteger(key)).add(line);
            }else
            {
                List<Integer> boundaryLine = new ArrayList<>();
                boundaryLine.add(line);
                spaceBoudary.put(getInteger(key),boundaryLine);
            }

        }
    }

    private Set<Integer> getSpaceBoundary (List<Integer> lines, Map<Integer, LinkedHashSet<Integer>> mapItems, Map<Integer, Boolean> mapVisited){
        Map<Integer, Boolean> mapWritten = new HashMap<>();
        for(Integer line:lines)
        {
            if(spaceBoudary.containsKey(line))
            {
                for(Integer lineId:spaceBoudary.get(line))
                {
                    List<String> ifcProperties = getIfcPropertiesByLineData(data.get(lineId));/*IfcInstance ii = data.get(line);*/
                    if(getInteger(ifcProperties.get(4))!=null)
                    {
                        appendItemRepresentationIteratively(null, getInteger(ifcProperties.get(4)), mapItems, mapWritten, mapVisited);
                    }
                    if(getInteger(ifcProperties.get(6))!=null)
                    {
                        appendItemRepresentationIteratively(null, getInteger(ifcProperties.get(6)), mapItems, mapWritten, mapVisited);
                    }

                    mapWritten.put(lineId,true);
                }
            }


        }
        return mapWritten.keySet();
    }

//    public Map<String, String> getGeometrySlicesInString_TypePref() {/*			long Time1=System.currentTimeMillis();*/
//        List<String> lstGeometrySlices = new ArrayList<String>();
//        LinkedHashSet<String> lstPossibleGeometryInstances = getPossibleGeometryInstances();  /*获取contains和Aggregates long Time2=System.currentTimeMillis(); System.out.println("获取contains和Aggregates的时间为：  "+(Time2-Time1)/(double)1000 );*/
//        Map<String, List<String>> mapRep2Ins = new HashMap<String, List<String>>();
//        Map<String, Set<String>> mapProdTypeToRep = new HashMap<String, Set<String>>();
//        Map<String, String> mapRepToProdType = new HashMap<String, String>();
//        for (String possibleIns : lstPossibleGeometryInstances) {
//            String ifcType = this.getTypeByLine(possibleIns);
//            if (ifcType == "") continue;
//            if (IfcPropertyType.IFC_TYPE_IFCPROJECT.equals(ifcType)) /* IFCProject没有形状*/ continue;
//            List<String> ifcProperties = this.getPropertiesByLine(possibleIns);
//            if (ifcProperties.size() < 7) continue;
//            String rep = ifcProperties.get(6);    /* 获取IfcRepresentation*/
//            if (rep.length() < 2)    /* IfcRepresentation以#开头，长度必然不小于2；长度小于2，要么为空，要么为$；*/ continue;
//            if (!mapRep2Ins.containsKey(rep)) {
//                List<String> lstTmp = new ArrayList<String>();
//                mapRep2Ins.put(rep, lstTmp);
//            }
//            mapRep2Ins.get(rep).add(possibleIns);
//            String typeName = getTypeByLine(possibleIns);
//            if (!mapProdTypeToRep.containsKey(typeName)) {
//                Set<String> lstTmp = new HashSet<String>();
//                mapProdTypeToRep.put(typeName, lstTmp);
//            }
//            mapProdTypeToRep.get(typeName).add(rep);
//            mapRepToProdType.put(rep, typeName);
//        }/*			long Time3=System.currentTimeMillis(); System.out.println("获取IfcRepresentation的时间为：  "+(Time3-Time2)/(double)1000 ); 获取每个IFCRepresentation的图*/
//        Map<String, LinkedHashSet<String>> mapItems = new HashMap<String, LinkedHashSet<String>>();
//        Map<String, Boolean> mapVisited = HashObjObjMaps.<String, Boolean>newUpdatableMap();/*!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!new HashMap<String, Boolean>();// for(String rep : mapRep2Ins.keySet()){ mapVisited.put(rep, true); }*/
//        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM))
//            for (Integer insStyledItem : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM))
//                mapVisited.put(insStyledItem, true);
//        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION))
//            for (Integer insMatDefRep : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION))
//                mapVisited.put(insMatDefRep, true);/*			System.out.println("类型判断完成  ");*/
//        retrieveStyledItemMap(mapItems, mapVisited);/*	    	System.out.println("retrieveStyledItemMap完成  ");*/
//        retrieveMaterialRepDef(mapItems, mapVisited);/*	    	System.out.println("retrieveMaterialRepDef完成  ");*/
//        retrieveRelVoidElement(mapItems, mapVisited);/*	    	System.out.println("retrieveRelVoidElement完成  ");*/
//        for (String rep : mapRep2Ins.keySet())
//            retrieveSubinstances(rep, mapItems, mapVisited);/*	    	System.out.println("retrieveRelVoidElement完成  ");*/
//        List<String> lstReps = new ArrayList<String>();
//        lstReps.addAll(mapRep2Ins.keySet());
//        Map<String, Set<String>> mapGeometrySlices = new HashMap<String, Set<String>>();/*	        System.out.println("类型判断完成2  ");*/
//
//
//        Set<Integer> head =this.getProjectHead();
//        for (String m : lstReps) {
//            Set<String> projecthead = new HashSet<String>();
//            Set<String> setTmp = getGeometryDescriptionSlices(m, mapRep2Ins.get(m), mapItems, mapVisited);  /*生成每个构件的子文件*/
//            projecthead.addAll(setTmp);
//            projecthead.addAll(head);
//            mapGeometrySlices.put(m, projecthead);
//            totalSize += setTmp.size();
//        }/*			long Time4=System.currentTimeMillis(); System.out.println("生成每个构件IFCRepresentation图的时间为：  "+(Time4-Time3)/(double)1000 );*/
//        if (mapGeometrySlices.size() == 0) {/* TODO: 切分不出来？？直接返回原文件 return*/}
//        Map<String, String> mapIns2Rep = new HashMap<String, String>(); /* 构件 -> 表现*/
//        for (String rep : mapRep2Ins.keySet()) for (String ins : mapRep2Ins.get(rep)) mapIns2Rep.put(ins, rep);
//        Map<Set<Integer>, Set<Integer>> mapConnIns = getRelConnPathSets();
//        String rep_conn;
//        Set<String> connRep = new HashSet<String>();
//        for (Set<Integer> connIns : mapConnIns.keySet()) {
//            for (Integer ins : connIns)
//                if (mapIns2Rep.containsKey(ins)) {    /* 有些构件没有3D shape，神奇。*/
//                    connRep.add(mapIns2Rep.get(ins));
//                }/* 生成合并后的几何对象*/
//            rep_conn = String.valueOf(connIns.hashCode());
//            Set<String> connGeometry = new HashSet<String>();
//            for (String rep : connRep) {
//                connGeometry.addAll(mapGeometrySlices.get(rep));
//                mapGeometrySlices.remove(rep);
//            }/* 需要加上IFCRELCONNECTSPATHELEMENTS实例*/
//            connGeometry.addAll(mapConnIns.get(connIns));
//            mapGeometrySlices.put(rep_conn, connGeometry);
//            connRep.clear();
//        }/*			long Time5=System.currentTimeMillis(); System.out.println("优化合并mapGeometrySlices的时间为：  "+(Time5-Time4)/(double)1000 );*/
//        int max_len = 0, max_size = 0, min_size = data.size(), min_len = Integer.MAX_VALUE, total_size = 0, _total_size = 0, final_size = 0, splits = 0;
//        Map<String, String> mapSlices = new HashMap<String, String>();
//        Map<String, Boolean> mapTr = new HashMap<String, Boolean>();
//        Map<Integer, Set<String>> _mapSlices = new HashMap<Integer, Set<String>>();
//        for (int i = 0; i < this.EXPECTED_NUMBER_OF_FILE; i++) {
//            Set<String> _set = new HashSet<String>();
//            _mapSlices.put(i, _set);
//        }
//        int empty_index = 0;
//        for (String rep : mapGeometrySlices.keySet()) {
//            Set<String> contents = mapGeometrySlices.get(rep);
//            if (empty_index == 0) {
//                _mapSlices.get(empty_index).addAll(contents);
//                empty_index = 1;
//                continue;
//            }
//            int candidate = 0, max_interaction = 0, candidate_min = 0;
//            for (int i = 0; i < empty_index; i++) {
//                Set<String> _set = new HashSet<String>();
//                _set.addAll(contents);
//                _set.addAll(_mapSlices.get(i));
//                int interaction = contents.size() + _mapSlices.get(i).size() - _set.size();
//                if (interaction > max_interaction) {
//                    max_interaction = interaction;
//                    candidate = i;
//                } else if (interaction == max_interaction)
//                    if (_mapSlices.get(i).size() < _mapSlices.get(candidate).size()) candidate = i;
//                if (_mapSlices.get(i).size() < _mapSlices.get(candidate_min).size()) candidate_min = i;
//            }
//            if (max_interaction > 0.8 * contents.size()) _mapSlices.get(candidate).addAll(contents);
//            else if (empty_index < this.EXPECTED_NUMBER_OF_FILE) {
//                _mapSlices.get(empty_index).addAll(contents);
//                empty_index = empty_index + 1;
//            } else _mapSlices.get(candidate_min).addAll(contents);
//        }
//        Set<Integer> setProj = this.getProjectHead();
//        for (int i = 0; i < empty_index; i++) {
//            StringBuilder sb = new StringBuilder();
//            sb.append(getHeader4GeometrySlice());
//            for (Integer writtenItem : setProj) {
//                sb.append(writtenItem);
//                sb.append("=");
//                sb.append(data.get(writtenItem));
//                sb.append("\n");
//            }
//            for (String writtenItem : _mapSlices.get(i)) {
//                sb.append(writtenItem);
//                sb.append("=");
//                sb.append(data.get(writtenItem));
//                sb.append("\n");
//            }
//            sb.append(this.getEnd4GeometrySlice());
//            mapSlices.put(String.valueOf(i), sb.toString());
//            total_size = total_size + _mapSlices.get(i).size();
//            if (_mapSlices.get(i).size() > max_size) max_size = _mapSlices.get(i).size();
//            if (_mapSlices.get(i).size() < min_size) min_size = _mapSlices.get(i).size();
//        }
//        max_size = max_size + setProj.size();
//        min_size = min_size + setProj.size();
//        System.out.println("Max Size (Proj size): " + max_size + "(" + setProj.size() + ")" + ", Min Size: " + min_size + ", Actual Total Size (Total): " + total_size + "(" + _total_size + ")" + ", Final Size:" + final_size + ", Splits: " + empty_index);/*			System.out.println("总拆分时间应为 ：  "+(Time6-Time1)/(double)1000 ); lstGeometrySlices.addAll(mapGeometrySlices.values());*/
//        return mapSlices;
//    }

    public String getUnit() {
        List<String> unitList = new ArrayList<>();
        String unit = null;
        if (mapType2Line.containsKey("IFCUNITASSIGNMENT")) for (Integer line : mapType2Line.get("IFCUNITASSIGNMENT")) {
            String content = data.get(line);
            List<String> ifcProperties = getIfcPropertiesByLineData(content);
            String strRelatingElement = this.getmessageByLine(line, 0);
            String[] units = strRelatingElement.replace("(", "").replace(")", "").split(",");
            for (String unitline : units) {
                String type = getmessageByLine(getInteger(unitline) , 1);
                if (type.equals(".LENGTHUNIT.")) {
                    String lengthunit = getmessageByLine(getInteger(unitline), 2);
                    unitList.add(lengthunit);
                }
            }
        }
        if (unitList.contains(".MILLI.")) return "1mm";
        else if (unitList.contains(".CENTI.")) return "1cm";
        else if (unitList.contains("'FOOT'")) return "1ft";
        else if (unitList.contains("'INCH'")) return "1inch";
        else return "1m";/*TODO*/
    }

    private String getmessageByLine(Integer line, Integer num) {
        String content = data.get(line);
        List<String> ifcProperties = getIfcPropertiesByLineData(content);
        if (ifcProperties.size() >= num + 1) {
            String strRelatingElement = ifcProperties.get(num);
            return strRelatingElement;
        } else return "";
    }/* 获取因为IFCRELCONNECTSPATHELEMENTS 需要合在一起的构件集*/

    private Map<Set<Integer>, Set<Integer>> getRelConnPathSets() {/* 需要把IFC_TYPE_IFCRELCONNECTSPATHELEMENTS的构件放在一起*/
        Map<Set<Integer>, Set<Integer>> mapConnCombined = new HashMap<Set<Integer>, Set<Integer>>();
        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELCONNECTSPATHELEMENTS)) {
            List<Set<Integer>> lstTmpConnSlice = new ArrayList<Set<Integer>>();
            for (Integer line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELCONNECTSPATHELEMENTS)) {
                String content = data.get(line);
                List<String> ifcProperties = getIfcPropertiesByLineData(content);
                Integer strRelatingElement = getInteger(ifcProperties.get(5)) ;
                Integer strRelatedElement = getInteger(ifcProperties.get(6));
                if (!data.containsKey(strRelatingElement) || !data.containsKey(strRelatedElement)) {/* TODO: 有个实例不存在？ why？？*/
                    continue;
                }
                for (Set<Integer> rawSlice : mapConnCombined.keySet())
                    if (rawSlice.contains(strRelatingElement) || rawSlice.contains(strRelatedElement))
                        lstTmpConnSlice.add(rawSlice);
                if (lstTmpConnSlice.size() > 0) {    /* 两个set合并*/
                    Set<Integer> tmp = new HashSet<Integer>();
                    Set<Integer> tmp_rel = new HashSet<Integer>();
                    for (Set<Integer> oriConn : lstTmpConnSlice) {
                        tmp.addAll(oriConn);
                        tmp_rel.addAll(mapConnCombined.get(oriConn));
                        mapConnCombined.remove(oriConn);
                    }
                    tmp.add(strRelatingElement);
                    tmp.add(strRelatedElement);
                    tmp_rel.add(line);
                    mapConnCombined.put(tmp, tmp_rel);
                } else {    /* 生成新的set*/
                    Set<Integer> tmp = new HashSet<Integer>();
                    tmp.add(strRelatingElement);
                    tmp.add(strRelatedElement);
                    Set<Integer> tmp_rel = new HashSet<Integer>();
                    tmp_rel.add(line);
                    mapConnCombined.put(tmp, tmp_rel);
                }
                lstTmpConnSlice.clear();
            }
        }
        return mapConnCombined;
    }

    private String writeSetToFile(Set<Integer> set, Integer rep) {
        String path = temp_folder + rep + ".ifc";
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(path));
            out.write(getHeader4GeometrySlice());
            for (Integer writtenItem : set)
                if (data.get(writtenItem) != null) {
                    out.write("#" + writtenItem);
                    out.write("=");
                    out.write(data.get(writtenItem));
                    out.write("\n");
                }
            out.write(getEnd4GeometrySlice());
            out.close();
        } catch (IOException e) {/* TODO Auto-generated catch block*/
            e.printStackTrace();
            return null;
        }
        return path;
    }

    private String writeSourceToFile(String rep) {
        String path = temp_folder + rep + ".ifc";
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(path));
            out.write(getHeader4GeometrySlice());
            for (Integer writtenItem : data.keySet()) {
                out.write("#"+writtenItem);
                out.write("=");
                out.write(data.get(writtenItem));
                out.write("\n");
            }
            out.write(getEnd4GeometrySlice());
            out.close();
        } catch (IOException e) {/* TODO Auto-generated catch block*/
            e.printStackTrace();
            return null;
        }
        return path;
    }/*    public List<IfcMeshEntity> getGeometryInTriangles() throws RenderEngineException{ //List<IfcMeshEntity> lstIfcGeom = new ArrayList<IfcMeshEntity>(); List<String> lstSlices = getGeometrySlices(); /*for(StringBuilder sb : lstSlices){ InputStream is =  new   ByteArrayInputStream(sb.toString().getBytes()); IfcGeoParser ifcGeoParser = new IfcGeoParser(is); lstIfcGeom.addAll(ifcGeoParser.getGeomServerClientEntities()); }*//*	public List<IfcMeshEntity><T> getGeometryInTriangles(GeometryHandler<T> handler) throws RenderEngineException{ //List<IfcMeshEntity> lstIfcGeom = new ArrayList<IfcMeshEntity>(); List<String> lstSlices = getGeometrySlices(); /*for(StringBuilder sb : lstSlices){ InputStream is =  new   ByteArrayInputStream(sb.toString().getBytes()); IfcGeoParser ifcGeoParser = new IfcGeoParser(is); lstIfcGeom.addAll(ifcGeoParser.getGeomServerClientEntities()); }*/

    public Map<Integer, List<Integer>> getSpatialChildren() {
        Map<Integer, List<Integer>> mapContains = getContains();
        Map<Integer, List<Integer>> mapAggregates = getAggregates();
        for (Integer par : mapAggregates.keySet())
            if (mapContains.containsKey(par)) {
                mapContains.get(par).removeAll(mapAggregates.get(par));
                mapContains.get(par).addAll(mapAggregates.get(par));
            } else mapContains.put(par, mapAggregates.get(par));
        Map<Integer, List<Integer>> childmap = new HashMap<>();
        for (Integer key : mapContains.keySet()) {
            List<Integer> children = new ArrayList<>();
            List<Integer> childs = new ArrayList<>(mapContains.get(key));
            for (Integer child : childs) children.add(child);
            childmap.put(key, children);
        }/* mapContains.putAll(mapAggregates);*/
        return childmap;
    }

    public Map<String, List<String>> getPortConnect() {
        Map<String, String> connectelement = this.getConnectElement();
        Map<String, List<String>> connect = new HashMap<String, List<String>>();
        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELCONNECTSPORTS)) {
            for (Integer line : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELCONNECTSPORTS)) {
                List<String> ifcProperties = getIfcPropertiesByLineData(data.get(line));
                List<String> portProperties = getIfcPropertiesByLineData(data.get(getInteger(ifcProperties.get(4)))); data.get(line);

                if (!portProperties.get(2).equals('$')){
                    if (portProperties.get(2).toLowerCase().contains("inport")){
                        String key = getGuidByLine(getInteger(connectelement.get(ifcProperties.get(4)))) ;
                        String rawCon = getGuidByLine(getInteger(connectelement.get(ifcProperties.get(5))));
                        if (!connect.containsKey(rawCon)) {
                            List<String> lstTemp = new ArrayList<String>();
                            connect.put(rawCon, lstTemp);
                        }
                        connect.get(rawCon).add(key);
                    }
                    if (portProperties.get(2).toLowerCase().contains("outport")){
                        String key = getGuidByLine(getInteger(connectelement.get(ifcProperties.get(4)))) ;
                        String rawCon = getGuidByLine(getInteger(connectelement.get(ifcProperties.get(5)))); /* 去掉左右括号*/
                        if (!connect.containsKey(key)) {
                            List<String> lstTemp = new ArrayList<String>();
                            connect.put(key, lstTemp);
                        }
                        connect.get(key).add(rawCon);
                    }

                }

            }
        }
        return connect;
    }

    public List<IfcMeshEntity> getGeometryInTriangles(List<String> lstSlices, IfcMeshInterface ifcMeshInterface, String modelKey) throws RenderEngineException {/*List<IfcMeshEntity> lstIfcGeom = new ArrayList<IfcMeshEntity>(); for(StringBuilder sb : lstSlices){ InputStream is =  new   ByteArrayInputStream(sb.toString().getBytes()); IfcGeoParser ifcGeoParser = new IfcGeoParser(is); lstIfcGeom.addAll(ifcGeoParser.getGeomServerClientEntities()); } return lstIfcGeom;*/
        List<IfcMeshEntity> lstMeshes;
        try {
            lstMeshes = new IfcParallelGeoParser(lstSlices, ifcMeshInterface, modelKey).getGeomServerClientEntities();
        } catch (InterruptedException | ExecutionException e) {/* TODO Auto-generated catch block*/
            e.printStackTrace();
            return null;
        } finally {
            for (String s : lstSlices) {
                File file = new File(s);
                file.delete();
            }
        }
        if(seg==true)
        {
            lstMeshes = geoMerge(lstMeshes);
        }
        return lstMeshes;
    }

    public List<IfcMeshEntity> geoMerge(List<IfcMeshEntity> lstMeshes){
        List<IfcMeshEntity> comLstMeshe = new ArrayList<>();
        Map<String,List<IfcMeshEntity>> meshEntityMap = new HashMap<>();

        for (IfcMeshEntity ifcMeshEntity:lstMeshes)
        {
            if (meshEntityMap.containsKey(ifcMeshEntity.getGuid()))
            {
                meshEntityMap.get(ifcMeshEntity.getGuid()).add(ifcMeshEntity);
            }
            else
            {
                List<IfcMeshEntity> ifcMeshEntities = new ArrayList<>();
                ifcMeshEntities.add(ifcMeshEntity);
                meshEntityMap.put(ifcMeshEntity.getGuid(),ifcMeshEntities);
            }

        }
        for (Map.Entry<String,List<IfcMeshEntity>> entry:meshEntityMap.entrySet())
        {
            IfcMeshEntity comGeo = new IfcMeshEntity();
            comGeo.setPositions(new float[0]);
            comGeo.setNormals(new float[0]);
            comGeo.setIndices(new int[0]);
            comGeo.setColors(new float[0]);
            comGeo.setMaterialIndices(new int[0]);
            List<IfcMeshEntity> list = entry.getValue();
            for (IfcMeshEntity ifcMeshEntity:list)
            {
                entityCom(comGeo,ifcMeshEntity);
            }
            comLstMeshe.add(comGeo);
        }

        return comLstMeshe;

    }

    public void entityCom (IfcMeshEntity synthetics,IfcMeshEntity seasoning)
    {
        if (synthetics.getId() == 0)
        {
            synthetics.setId(seasoning.getId());
        }
        if (synthetics.getGuid () == null)
        {
            synthetics.setGuid(seasoning.getGuid());
        }
        if (synthetics.getName() == null)
        {
            synthetics.setName(seasoning.getName());
        }
        if (synthetics.getType() == null)
        {
            synthetics.setType(seasoning.getType());
        }
        if (synthetics.getParentId() == 0)
        {
            synthetics.setParentId(seasoning.getParentId());
        }
        if (synthetics.getMatrix() == null)
        {
            synthetics.setMatrix(seasoning.getMatrix());
        }
        if (synthetics.getRepId() == 0)
        {
            synthetics.setRepId(seasoning.getRepId());
        }

        if (synthetics.getPositions().length ==0)
        {
            synthetics.setPositions(seasoning.getPositions());
        }else{
            float position[] =  synthetics.getPositions();
            float positionSea[] = seasoning.getPositions();
            float comPosition[] = new float[position.length+positionSea.length];
            for (int i =0 ;i<position.length;++i)
            {
                comPosition[i] = position[i];
            }
            for (int i =0 ;i<positionSea.length;++i)
            {
                comPosition[position.length+i] = positionSea[i];
            }
            synthetics.setPositions(comPosition);
        }

        if (synthetics.getNormals().length ==0)
        {
            synthetics.setNormals(seasoning.getNormals());
        }else{
            float syFloat[] =  synthetics.getNormals();
            float seaFloat[] = seasoning.getNormals();
            float comFloat[] = new float[syFloat.length+seaFloat.length];
            for (int i =0 ;i<syFloat.length;++i)
            {
                comFloat[i] = syFloat[i];
            }
            for (int i =0 ;i<seaFloat.length;++i)
            {
                comFloat[syFloat.length+i] = seaFloat[i];
            }
            synthetics.setNormals(comFloat);
        }

        if (synthetics.getIndices().length ==0)
        {
            synthetics.setIndices(seasoning.getIndices());
        }else{
            int syInt[] =  synthetics.getIndices();
            int seaInt[] = seasoning.getIndices();
            int comInt[] = new int[syInt.length+seaInt.length];
            for (int i =0 ;i<syInt.length;++i)
            {
                comInt[i] = syInt[i];
            }
            for (int i =0 ;i<seaInt.length;++i)
            {
                comInt[syInt.length+i] = seaInt[i]+synthetics.getPositions().length/3;
            }
            synthetics.setIndices(comInt);
        }

        if (synthetics.getColors().length ==0)
        {
            synthetics.setColors(seasoning.getColors());
        }else{
            float syFloat[] =  synthetics.getColors();
            float seaFloat[] = seasoning.getColors();
            float comFloat[] = new float[syFloat.length+seaFloat.length];
            for (int i =0 ;i<syFloat.length;++i)
            {
                comFloat[i] = syFloat[i];
            }
            for (int i =0 ;i<seaFloat.length;++i)
            {
                comFloat[syFloat.length+i] = seaFloat[i];
            }
            synthetics.setColors(comFloat);
        }

        if (synthetics.getMaterialIndices().length ==0)
        {
            synthetics.setMaterialIndices(seasoning.getMaterialIndices());
        }else{
            int syInt[] =  synthetics.getMaterialIndices();
            int seaInt[] = seasoning.getMaterialIndices();
            int comInt[] = new int[syInt.length+seaInt.length];
            for (int i =0 ;i<syInt.length;++i)
            {
                comInt[i] = syInt[i];
            }
            for (int i =0 ;i<seaInt.length;++i)
            {
                comInt[syInt.length+i] = seaInt[i]+synthetics.getColors().length;
            }
            synthetics.setMaterialIndices(comInt);
        }


    }

    public List<IfcMeshEntity> getGeometryInTriangles(String ifcContent) throws RenderEngineException {
        InputStream is = new ByteArrayInputStream(ifcContent.getBytes());
        IfcGeoParser ifcGeoParser = new IfcGeoParser(is);
        return ifcGeoParser.getGeomServerClientEntities();
    }

    private Set<Integer> getGeometryDescriptionSlices(Integer rep, List<Integer> products, Map<Integer, LinkedHashSet<Integer>> mapItems, Map<Integer, Boolean> mapVisited) {/*List<String> lstGeometry = new ArrayList<String>(); // 初始5k lstGeometry.add(getHeader4GeometrySlice());*/
        Map<Integer, Boolean> mapWritten = new HashMap<>();/*!!!!!!!!!!!!!!!!!!!!!!!！！！！！！！！！！！！！new HashMap<String, Boolean>();//*/
        for (Integer product : products) {/* 1 append i.e. opening*/
            appendItemRepresentationIteratively(null, product, mapItems, mapWritten, mapVisited);
            mapWritten.put(product, true);/* 1 append product sbGeometry.append(product); sbGeometry.append("="); sbGeometry.append(data.get(product).toString()); sbGeometry.append(";\n"); lstGeometry.add(product + "=" + data.get(product) + "\n"); 2 append location*/
            Integer location = getInteger(this.getIfcPropertiesByLineData(data.get(product)).get(5)) ;
            appendItemRepresentationIteratively(null, location, mapItems, mapWritten, mapVisited);/*sbGeometry.append(getItemRepresentationIteratively(location, mapItems, mapWritten, mapVisited));*/
        }/* 3 append shape*/
        appendItemRepresentationIteratively(null, rep, mapItems, mapWritten, mapVisited);
        return mapWritten.keySet();/*StringBuilder sb = new StringBuilder(); for(String writtenItem : mapWritten.keySet()){ sb.append(writtenItem); sb.append("="); sb.append(data.get(writtenItem)); sb.append("\n"); //lstGeometry.add(sb.toString()); //sb.delete(0, sb.length()); }*//*lstGeometry.add(sb.toString()); lstGeometry.add(getEnd4GeometrySlice());*//*return lstGeometry;//.toString();//String.join("\n", lstGeometry);*/
    }

    private void appendItemRepresentationIteratively(List<Integer> lstGeometry, Integer item, Map<Integer, LinkedHashSet<Integer>> mapItems, Map<Integer, Boolean> mapWritten, Map<Integer, Boolean> mapVisited) {/*StringBuilder sb = new StringBuilder();*/
        if (mapWritten.containsKey(item)) return;
        mapWritten.put(item, true);/*sb.append(item); sb.append("="); sb.append(data.get(item).toString()); sb.append(";\n");*//*lstGeometry.add(item + "=" + data.get(item).toString() + "\n");*/
        if (!mapItems.containsKey(item)) retrieveSubinstances(item, mapItems, mapVisited);
        if (mapItems.containsKey(item)) {    /* 排除叶子节点*/
            for (Integer sub : mapItems.get(item))
                appendItemRepresentationIteratively(lstGeometry, sub, mapItems, mapWritten, mapVisited);
        }/*return sb;*/
    }

    public void printfsize() {
        try {
            File file = new File(temp_folder + "size.txt");
            if (file.exists()) {
            } else file.createNewFile();
            BufferedWriter output = new BufferedWriter(new FileWriter(file, true));
            output.write("参数行数为：" + data.size());
            output.write("\r\n");
            output.flush();
            output.close();
        } catch (IOException e) {
        }
    }

    private String getHeader4GeometrySlice() {/*StringBuilder sbHeader = new StringBuilder();*//*sbHeader.append("ISO-10303-21;\n"); sbHeader.append("HEADER;\n"); sbHeader.append(this.header + "\n"); sbHeader.append("ENDSEC;\n"); sbHeader.append("DATA;\n");*//*return sbHeader;*/
        return "ISO-10303-21;\nHEADER;\nENDSEC;\nDATA;\n";
    }

    private String getEnd4GeometrySlice() {
        return "ENDSEC;\nEND-ISO-10303-21;";
    }

    private LinkedHashSet<Integer> getPossibleGeometryInstances() {
        LinkedHashSet<Integer> lstPossibleGeometryInstances = new LinkedHashSet<Integer>();
        mapAggregates = this.getAggregates();/* 所有的几何构件都会包含在Aggregates和Contains两种关系中（含key和value）*/
        for (Integer ins_p : mapAggregates.keySet()) {
            lstPossibleGeometryInstances.addAll(mapAggregates.get(ins_p));
            lstPossibleGeometryInstances.add(ins_p);
        }
        Map<Integer, List<Integer>> mapContains = this.getContains();
        for (Integer ins_p : mapContains.keySet()) {
            componentsstatistic.addAll(mapContains.get(ins_p));
            lstPossibleGeometryInstances.addAll(mapContains.get(ins_p));
            lstPossibleGeometryInstances.add(ins_p);
        }
        return lstPossibleGeometryInstances;
    }

    public Integer getComponentsAmount() {
        List<Integer> amount = new ArrayList<>(componentsstatistic);
        for (Integer line : componentsstatistic)
            if (mapAggregates.containsKey(line)) {
                amount.remove(line);
                amount.addAll(mapAggregates.get(line));
            }
        if (mapType2Line.containsKey("IFCRELVOIDSELEMENT")) amount.addAll(mapType2Line.get("IFCRELVOIDSELEMENT"));
        return amount.size();
    }

    private void retrieveStyledItemMap(Map<Integer, LinkedHashSet<Integer>> mapItems, Map<Integer, Boolean> mapVisited) {
        if (!this.mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM)) return;
        for (Integer insStyledItem : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCSTYLEDITEM)) {
            mapVisited.put(insStyledItem, true);
            List<String> insProperties = this.getIfcPropertiesByLineData(data.get(insStyledItem)); /*this.data.get(insStyledItem).getProperties();*/
            Integer item = getInteger(insProperties.get(0)) ;
            if (!data.containsKey(item)) continue;
            String styles = insProperties.get(1);
            if (styles.length() > 2 && styles.startsWith("(") && styles.endsWith(")")) {    /* styles的长度大于2，则有可能有styles*/
                String[] _styles = styles.substring(1, styles.length() - 1).split(",");
                if (!mapItems.containsKey(item)) {
                    LinkedHashSet<Integer> lstTmp = new LinkedHashSet<Integer>();
                    mapItems.put(item, lstTmp);
                }
                mapItems.get(item).add(insStyledItem);
                if (_styles.length > 0) {
                    if (!mapItems.containsKey(insStyledItem)) {
                        LinkedHashSet<Integer> lstTmp = new LinkedHashSet<Integer>();
                        mapItems.put(insStyledItem, lstTmp);
                    }
                    for (String style : _styles) {
                        mapItems.get(insStyledItem).add(getInteger(style));
                        retrieveSubinstances(getInteger(style), mapItems, mapVisited);
                    }
                }
            }
        }
    }

    private void retrieveMaterialRepDef(Map<Integer, LinkedHashSet<Integer>> mapItems, Map<Integer, Boolean> mapVisited) {
        if (!this.mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION)) return;
        for (Integer insMatRepDef : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCMATERIALDEFINITIONREPRESENTATION)) {
            mapVisited.put(insMatRepDef, true);
            List<String> insProperties = getIfcPropertiesByLineData(data.get(insMatRepDef)); /*this.data.get(insMatRepDef).getProperties();*/
            Integer material = getInteger(insProperties.get(3)) ;
            if (!data.containsKey(material)) continue;
            String reps = insProperties.get(2);
            if (reps.length() > 2 && reps.startsWith("(") && reps.endsWith(")")) {    /* styles的长度大于2，则有可能有styles*/
                String[] _reps = reps.substring(1, reps.length() - 1).split(",");
                if (!mapItems.containsKey(insMatRepDef)) {
                    LinkedHashSet<Integer> lstTmp = new LinkedHashSet<Integer>();
                    mapItems.put(insMatRepDef, lstTmp);
                }
                mapItems.get(insMatRepDef).add(material);
                for (String _repStr : _reps) {
                    Integer _rep = getInteger(_repStr);
                    if (!mapItems.containsKey(_rep)) {
                        LinkedHashSet<Integer> lstTmp = new LinkedHashSet<Integer>();
                        mapItems.put(_rep, lstTmp);
                    }
                    mapItems.get(_rep).add(insMatRepDef);
                }
                retrieveSubinstances(material, mapItems, mapVisited);
            }
        }
    }

    private void retrieveRelVoidElement(Map<Integer, LinkedHashSet<Integer>> mapItems, Map<Integer, Boolean> mapVisited) {
        if (!this.mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCRELVOIDSELEMENT)) return;
        for (Integer insRelVoidEle : mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCRELVOIDSELEMENT)) {
            mapVisited.put(insRelVoidEle, true);
            List<String> insProperties = getIfcPropertiesByLineData(data.get(insRelVoidEle)); /*this.data.get(insMatRepDef).getProperties();*/
            Integer insSolid = getInteger(insProperties.get(4)) ;
            if (!data.containsKey(insSolid)) continue;
            Integer insOpening = getInteger(insProperties.get(5)) ;
            if (data.containsKey(insOpening)) {
                if (!mapItems.containsKey(insRelVoidEle)) {
                    LinkedHashSet<Integer> lstTmp = new LinkedHashSet<Integer>();
                    mapItems.put(insRelVoidEle, lstTmp);
                }
                mapItems.get(insRelVoidEle).add(insOpening);
                if (!mapItems.containsKey(insSolid)) {
                    LinkedHashSet<Integer> lstTmp = new LinkedHashSet<Integer>();
                    mapItems.put(insSolid, lstTmp);
                }
                mapItems.get(insSolid).add(insRelVoidEle);
                retrieveSubinstances(insOpening, mapItems, mapVisited);
            }
        }
    }

    private void retrieveSubinstances(Integer ins, Map<Integer, LinkedHashSet<Integer>> mapItems, Map<Integer, Boolean> mapVisited) {
        try {
            if (mapVisited.containsKey(ins)) return;
            List<String> ifcProperties = getIfcPropertiesByLineData(data.get(ins)); /*data.get(ins).getProperties(); if (ifcProperties == null) return;*/
            for (String property : ifcProperties) {
                if (property.length() < 2 || property.startsWith("'"))    /* 找实例，实例形如#23，所以，长度不小于2*/ continue;
                mapVisited.put(ins, true);
                if (property.startsWith("#") && data.containsKey(getInteger(property))) {
                    if (!mapItems.containsKey(ins)) {
                        LinkedHashSet<Integer> tmp = new LinkedHashSet<Integer>();
                        mapItems.put(ins, tmp);
                    }
                    mapItems.get(ins).add(getInteger(property));/* 递归寻找*/
                    retrieveSubinstances(getInteger(property), mapItems, mapVisited);
                } else if (property.startsWith("(") && property.endsWith(")")) {/* TODO: maybe sth unresolved*/
                    String raw_ins = property.substring(1, property.length() - 1);
                    String[] sub_ins = raw_ins.split(",");
                    if (sub_ins.length > 0) {
                        if (!sub_ins[0].startsWith("#")) /* 说明为非实例*/ continue;
                        if (!mapItems.containsKey(ins)) {
                            LinkedHashSet<Integer> tmp = new LinkedHashSet<Integer>();
                            mapItems.put(ins, tmp);
                        }
                        for (String sub : sub_ins) {
                            mapItems.get(ins).add(getInteger(sub));/* 递归寻找*/
                            retrieveSubinstances(getInteger(sub), mapItems, mapVisited);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.print(ins + "\n");
        }
    }/*//////////////////////////////////////////////////////////////////////////////////////// / 初始化 ///////////////////////////////////////////////////////////////////////////////////////*/

    /**
     * 根据Ifc文件流读取、初始化Ifc数据 @param in Ifc输入文件流 @throws IOException Ifc文件读取错误异常
     */
    private void initByInputStream(InputStream in) throws IOException {
        String encoding = "US-ASCII";
        InputStreamReader reader = new InputStreamReader(in, encoding);
        BufferedReader lineReader = new BufferedReader(reader);

//        Scanner scan = new Scanner(in);

        String _lineTemp = null;
        StringBuilder sbObject = new StringBuilder();
        boolean isHeader = false, isData = false;
        while ((_lineTemp = lineReader.readLine()) != null) try {
//        while (scan.hasNextLine()) try {
//                _lineTemp = scan.nextLine();
                _lineTemp = _lineTemp.trim();
                if (isHeader) {    /* IFC的header部分*/
                    if (IfcPropertyType.IFC_ENDSEC.equals(_lineTemp.toUpperCase())) {    /* header 结束*/
                        isHeader = false;
                        header = sbObject.toString();
                        sbObject.delete(0, sbObject.length());/* = new StringBuilder();*/
                        continue;
                    } else {
                        sbObject.append(_lineTemp);
                        continue;
                    }
                } else if (isData) {        /* IFC的data部分*/
                    if (!_lineTemp.endsWith(";")) {    /* 非;结尾，instance描述未结束，继续读取*/
                        sbObject.append(_lineTemp);
                        continue;
                    }
                    if (IfcPropertyType.IFC_ENDSEC.equals(_lineTemp.toUpperCase())) {    /* data结束*/
                        isData = false;
                        continue;
                    }
                    sbObject.append(_lineTemp);
                    if (sbObject.charAt(0) != '#') {    /* data部分非#开头，似乎有问题*/
                        sbObject.delete(0, sbObject.length());/* = new StringBuilder();*/
                        continue;/* TODO: log what happen here?*/
                    }
                    int indEql = sbObject.indexOf("=");
                    if (indEql > 0) {
                        Integer line = Integer.parseInt(sbObject.substring(1, indEql).trim()) ;
                        String content = sbObject.substring(indEql + 1).trim();/*IfcInstance ii = getIfcInstanceByLineData(content);*/
                        String ifcType = getIfcTypeByLineData(content);
                        data.put(line, content);/*new IfcInstance(content, ifcType));*/
                        if (!mapType2Line.containsKey(ifcType)) {
                            List<Integer> lstTemp = new ArrayList<Integer>();
                            mapType2Line.put(ifcType, lstTemp);
                        }
                        mapType2Line.get(ifcType).add(line);
                        sbObject.delete(0, sbObject.length());/* = new StringBuilder();*/
                    } else {    /* 实例没有=号 TODO: log what happen here*/}
                } else if (IfcPropertyType.IFC_HEADER.equals(_lineTemp.trim().toUpperCase())) isHeader = true;
                else if (IfcPropertyType.IFC_DATA.equals(_lineTemp.trim().toUpperCase())) isData = true;

        } catch (Exception e) {
            System.out.print("出现异常： " + _lineTemp);
            e.printStackTrace();
            continue;
        }
        in.close();
        lineReader.close();
        reader.close();
    }

    private String getIfcTypeByLineData(String content) {
        int ind1stBracket = content.indexOf('(');
        int indLastBracket = content.lastIndexOf(')');
        List<String> lstProperties = new ArrayList<String>();
        String ifcType = content.substring(0, ind1stBracket).trim().toUpperCase();
        return ifcType;
    }

    private String[] getIfcUnitAssignment(Integer assignment) {
        List<String> child = this.getPropertiesByLine(assignment);
        String rawAgg = child.get(0).substring(1, child.get(0).length() - 1); /* 去掉左右括号*/
        String[] children = rawAgg.split(",");
        return children;
    }

    /**
     * 根据一行完整ifc数据，解析形成IfcInstance @param content 一行完整Ifc数据（不含行号），数据格式形如： IFCPROJECT('3MD_HkJ6X2EwpfIbCFm0g_', #2, 'Default Project', 'Description of Default Project', $, $, $, (#20), #7); @return IfcInstance，包括IfcType和属性列表
     */
    private List<String> getIfcPropertiesByLineData(String content) {
        int ind1stBracket = 0;
        int indLastBracket = 0;
        try {
            ind1stBracket = content.indexOf('(');
            indLastBracket = content.lastIndexOf(')');
            List<String> lstProperties = new ArrayList<String>();
            String ifcType = content.substring(0, ind1stBracket).trim().toUpperCase();
            String[] strRawProperties = content.substring(ind1stBracket + 1, indLastBracket).trim().split(",");
            for (int i = 0; i < strRawProperties.length; i++) {
                String strRawProperty = strRawProperties[i].trim();
                if (strRawProperty.startsWith("'")) { /* 以'开头，则必须以'结尾，以处理,出现在''之间的情况*/
                    while (!(strRawProperty.endsWith("'") && !strRawProperty.endsWith("\\'"))) {
                        if (strRawProperty.endsWith("\\X0\\'")) {    /* 解决以\X0\'结尾的问题*/
                            break;
                        }
                        i++;
                        strRawProperty += "," + strRawProperties[i].trim();
                    }
                } else if (strRawProperty.startsWith("(")&&!strRawProperty.startsWith("('")) {    /* 以(开头，则必须以)结尾，以处理,出现在()之间的情况*/
//                } else if (strRawProperty.startsWith("(")) {    /* 以(开头，则必须以)结尾，以处理,出现在()之间的情况*/
                    while (!(strRawProperty.endsWith(")") && !strRawProperty.endsWith("\\") && !strRawProperty.endsWith("')"))) {
                        i++;
                        strRawProperty += "," + strRawProperties[i].trim();
                    }
                } else if (strRawProperty.contains("('")) { /* 内含(',形如 IFCText('ab, cde')，要以 ')结束*/
                    while (!(strRawProperty.endsWith("')"))) {
                        i++;
                        strRawProperty += "," + strRawProperties[i].trim();
                    }
                }
                lstProperties.add(strRawProperty);
            }
            return lstProperties; /*new IfcInstance(ifcType, lstProperties);*/
        } catch (Exception e) {
            System.out.print("文件参数异常，异常参数为:" + content + "\n");
            return null;
        }
    }

    public String getHeader() {
        return header;
    }

    private String getHeaderMid() {
        String a = "\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*\\*";
        String heards[] = header.split(a);
        if (heards.length == 3) return heards[1];
        else return null;
    }

    private String[] heardmessage(String head) {
        if (head == null) return null;
        String[] heads = head.split("\\*");
        return heads;
    }

    private Map<String, String> headMap(String[] header) {
        if (header.length != 17) return null;
        Map<String, String> headerMap = new HashMap<>();
        for (int i = 1; i < header.length; i++)
            headerMap.put(header[i].substring(0, 33).trim(), header[i].substring(33, header[i].length()).trim());
        return headerMap;
    }

    /**
     * @Description: 获取headermap @Param: [] @return: java.util.Map<java.lang.String   ,   java.lang.String> @Author: Wang @Date: 2019/3/26
     */
    public Map<String, String> getHeaderMap() {
        String[] headermessage = this.heardmessage(this.getHeaderMid());
        if (headermessage == null) return null;
        else return this.headMap(headermessage);
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public Map<Integer, String> getData() {
        return data;
    }

    public Integer getProject() {
        Integer project = null;
        if (mapType2Line.containsKey(IfcPropertyType.IFC_TYPE_IFCPROJECT))
        {
            return mapType2Line.get(IfcPropertyType.IFC_TYPE_IFCPROJECT).get(0);
        }
        return project;
    }

    public Map<String, List<Integer>> getMapType2Line() {
        return mapType2Line;
    }

    public void setMapType2Line(Map<String, List<Integer>> mapType2Line) {
        this.mapType2Line = mapType2Line;
    }

    public Set<Integer> getProjectHead() {
        HashSet<Integer> projecthead = new HashSet<Integer>();
        Integer project = getProject();
        projecthead.add(project);
        String assignment = this.getPropertiesByLine(project).get(8);
        projecthead.add(getInteger(assignment) );
        String[] children = getIfcUnitAssignment(getInteger(assignment));
        for(String string:children)
        {
            projecthead.add(getInteger(string));
        }
        return projecthead;
    }

    public Integer getInteger(String string){
        try {
            return Integer.parseInt(string.substring(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    class IfcInstance {
        private String raw;
        private String ifcType;/*private List<String> ifcProperties;*/

        public List<String> getProperties() {
            return getIfcPropertiesByLineData(raw);
        }

        public IfcInstance(String _raw, String _ifcType) {/*ifcProperties = new ArrayList<String>();*/
            this.raw = _raw;
            this.ifcType = _ifcType;
        }/*public IfcInstance(String _raw){ this.raw = _raw; ifcProperties = new ArrayList<String>(); }*/

        public IfcInstance(String _ifcType, List<String> _ifcProperties) {/*this.raw = _raw; this.ifcType = _ifcType; this.ifcProperties = _ifcProperties;*/}

        public String toString() {/*StringBuilder sb = new StringBuilder(); sb.append(ifcType); sb.append("("); for(String pro : ifcProperties){ sb.append(pro); sb.append(","); } sb.deleteCharAt(sb.length() - 1); sb.append(");"); return sb.toString();*/
            return raw;
        }
    }

    private List<Set<Integer>> retrieveProductSegments(Integer rep, Map<Integer, List<Integer>> mapRep2Ins, Map<Integer, LinkedHashSet<Integer>> mapItems, int thr) {
        Set<Integer> projecthead = this.getProjectHead();
        List<Set<Integer>> lstSegments = retrieveSegments(rep, mapItems, thr);
        for (Set<Integer> setTmp : lstSegments) setTmp.addAll(projecthead);
        for (Integer product : mapRep2Ins.get(rep)) {
            String location = this.getIfcPropertiesByLineData(data.get(product)).get(5);
            Set<Integer> setLocation = retrieveSubChild(getInteger(location), mapItems);
            for (Set<Integer> setTmp : lstSegments) {
                setTmp.add(product);
                setTmp.addAll(setLocation);
            }
        }
        return lstSegments;
    }

    private List<Set<Integer>> retrieveSegments(Integer rep, Map<Integer, LinkedHashSet<Integer>> mapItems, int thr) {/* 从上而下建立层次关系*/
        Map<Integer, Set<Integer>> mapLayer2Ins = new HashMap<Integer, Set<Integer>>();
        int index = 1;
        Set<Integer> setRep = new HashSet<Integer>();
        setRep.add(rep);
        mapLayer2Ins.put(index, setRep);
        while (true) {
            Set<Integer> setTmp = new HashSet<Integer>();
            for (Integer ins : mapLayer2Ins.get(index))
                if (mapItems.containsKey(ins)) /* 非叶子节点*/ setTmp.addAll(mapItems.get(ins));
            if (setTmp.isEmpty() || setTmp.size() == 0) break;
            index++;
            mapLayer2Ins.put(index, setTmp);
        }
        Map<Integer, NodeAttr> mapIns2SizeAndSeg = retrieveSizeAndSegmentability(mapLayer2Ins, rep, mapItems);
        index = 1;
        Set<Integer> setNodeCnt = new HashSet<Integer>();
        List<Set<Integer>> lstSegments = retrieveSubSegments(mapIns2SizeAndSeg, setNodeCnt, rep, mapItems, thr);
        return lstSegments;
    }

    private List<Set<Integer>> retrieveSubSegments(Map<Integer, NodeAttr> mapIns2SizeAndSeg, Set<Integer> setIns, Integer ins, Map<Integer, LinkedHashSet<Integer>> mapItems, int thr) {
        List<Set<Integer>> lstSegments = new ArrayList<Set<Integer>>();
        NodeAttr nodeAttr = mapIns2SizeAndSeg.get(ins);
        if (mapIns2SizeAndSeg.get(ins).segmentability && mapIns2SizeAndSeg.get(ins).size > thr)
            if (mapIns2SizeAndSeg.get(ins).self_seg) { /* 本身可分割*/
                Set<Integer> setNodeCnt = new HashSet<Integer>();
                setNodeCnt.addAll(setIns);
                setNodeCnt.add(ins);
                Integer ref = mapIns2SizeAndSeg.get(ins).ref;
                if (mapItems.containsKey(ref)) setNodeCnt.addAll(retrieveSubChild(ref, mapItems));
                int thr_new = thr + setIns.size() - setNodeCnt.size();
                Set<Integer> setCombination = new HashSet<Integer>();
                for (Integer _ins : mapItems.get(ins)) {
                    if (_ins.equals(ref)) continue;
                    if (mapIns2SizeAndSeg.get(_ins).size > thr_new)
                        lstSegments.addAll(retrieveSubSegments(mapIns2SizeAndSeg, setNodeCnt, _ins, mapItems, thr_new));
                    else setCombination.add(_ins);
                }
                int thr_tmp = thr_new;
                Set<Integer> setTmp = new HashSet<Integer>();
                int i = 1;
                int size = setCombination.size();
                for (Integer _ins : setCombination) {
                    if (thr_tmp > mapIns2SizeAndSeg.get(_ins).size && i < size) {
                        thr_tmp = thr_tmp - mapIns2SizeAndSeg.get(_ins).size;
                        setTmp.add(_ins);
                    } else {
                        setTmp.add(_ins);
                        Set<Integer> setTmpContent = new HashSet<Integer>();
                        setTmpContent.addAll(setNodeCnt);
                        for (Integer _i : setTmp) setTmpContent.addAll(retrieveSubChild(_i, mapItems));
                        lstSegments.add(setTmpContent);
                        thr_tmp = thr_new;
                        setTmp.clear();
                    }
                    i++;
                }
            } else {
                Integer seg_child = mapIns2SizeAndSeg.get(ins).seg_child;
                Set<Integer> setNodeCnt = new HashSet<Integer>();
                setNodeCnt.addAll(setIns);
                setNodeCnt.add(ins);
                for (Integer _ins : mapItems.get(ins)) {
                    if (_ins.equals(seg_child)) continue;
                    setNodeCnt.addAll(retrieveSubChild(_ins, mapItems));
                }
                lstSegments = retrieveSubSegments(mapIns2SizeAndSeg, setNodeCnt, seg_child, mapItems, thr + setIns.size() - setNodeCnt.size());
            }
        else { /* 不可分，或 大小符合阈值要求，直接返回所有子节点*/
            Set<Integer> setTmp = retrieveSubChild(ins, mapItems);
            setTmp.addAll(setIns);
            lstSegments.add(setTmp);
        }
        return lstSegments;
    }

    private Set<Integer> retrieveSubChild(Integer ins, Map<Integer, LinkedHashSet<Integer>> mapItems) {
        Set<Integer> setTmp = new HashSet<Integer>();
        setTmp.add(ins);
        if (mapItems.containsKey(ins)) { /* 若有子节点，则添加子节点*/
            for (Integer _ins : mapItems.get(ins)) setTmp.addAll(retrieveSubChild(_ins, mapItems));
        }
        return setTmp;
    }

    class NodeAttr {
        public int size = 0;
        public boolean segmentability = false;
        public boolean self_seg = false;
        public Integer seg_child = null;
        public Integer ref = null;

        public NodeAttr(int _size) {
            size = _size;
        }

        public NodeAttr(int _size, boolean _s, boolean _self_seg, Integer _seg_child, Integer _ref) {
            size = _size;
            segmentability = _s;
            self_seg = _self_seg;
            seg_child = _seg_child;
            ref = _ref;
        }
    }

    private Map<Integer, NodeAttr> retrieveSizeAndSegmentability(Map<Integer, Set<Integer>> mapLayer2Ins, Integer rep, Map<Integer, LinkedHashSet<Integer>> mapItems) {
        String[] decompositions = {"ifcgeometricset", "ifcconnectedfaceset", "ifcopenshell"}; /* "ifcclosedshell",*/
        String associations[] = {"ifcstyleditem", "IFCSHAPEREPRESENTATION", "IFCPRODUCTDEFINITIONSHAPE"};
        Map<Integer, NodeAttr> mapIns2Size = new HashMap<Integer, NodeAttr>();
        int index = mapLayer2Ins.size();/* 从下而上建立建立size*/
        while (index > 0) {
            for (Integer ins : mapLayer2Ins.get(index)) {
                if (mapIns2Size.containsKey(ins)) continue;
                int size = 1;
                boolean _seg = false, _self_seg = false;
                Integer seg_child = null, ref = null;
                String type = this.getTypeByLine(ins);
                for (String _t : decompositions)
                    if (_t.toLowerCase().equals(type.toLowerCase())) {
                        _seg = true;
                        _self_seg = true;
                        break;
                    }
                for (String _t : associations)
                    if (_t.toLowerCase().equals(type.toLowerCase())) {
                        List<String> insProperties = this.getIfcPropertiesByLineData(data.get(ins));
                        _seg = true;
                        _self_seg = true;
                        Integer item = getInteger(insProperties.get(0)) ;
                        ref = item;
                        break;
                    }
                if (mapItems.containsKey(ins)) for (Integer child_ins : mapItems.get(ins)) {
                    size += mapIns2Size.get(child_ins).size;
                    if (mapIns2Size.get(child_ins).segmentability) {
                        _seg = true;
                        seg_child = child_ins;
                    }
                }
                else size = 1;
                mapIns2Size.put(ins, new NodeAttr(size, _seg, _self_seg, seg_child, ref));
            }
            index--;
        }
        return mapIns2Size;
    }

    private String writeSetToFile_LargeProduct(Set<Integer> set, Integer rep) {
        String path = temp_folder + rep + ".ifc";
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(path));
            out.write(getHeader4GeometrySlice());
            for (Integer writtenItem : set) {
                String ifcType = this.getTypeByLine(writtenItem);
                if ("IFCSHAPEREPRESENTATION".equals(ifcType.toUpperCase())) {/* ifcshaperepresentation特殊处理*/
                    List<String> insProperties = this.getIfcPropertiesByLineData(data.get(writtenItem));
                    out.write("#"+writtenItem);
                    out.write("=IFCSHAPEREPRESENTATION(");
                    out.write(insProperties.get(0) + "," + insProperties.get(1) + "," + insProperties.get(2) + ",");
                    String reps = insProperties.get(3);
                    if (reps.length() > 2 && reps.startsWith("(") && reps.endsWith(")")) {    /* styles的长度大于2，则有可能有styles*/
                        String[] _reps = reps.substring(1, reps.length() - 1).split(",");
                        reps = "(";
                        for (String _rep : _reps) if (set.contains(getInteger(_rep))) reps = reps + _rep + ",";
                        reps = reps.substring(0, reps.length() - 1);
                        reps = reps + ")";
                    }
                    out.write(reps + ");");
                    out.write("\n");
                } else if ("IFCPRODUCTDEFINITIONSHAPE".equals(ifcType.toUpperCase())) {/* IFCPRODUCTDEFINITIONSHAPE特殊处理*/
                    List<String> insProperties = this.getIfcPropertiesByLineData(data.get(writtenItem));
                    out.write("#"+writtenItem);
                    out.write("=IFCPRODUCTDEFINITIONSHAPE(");
                    out.write(insProperties.get(0) + "," + insProperties.get(1) + ",");
                    String reps = insProperties.get(2);
                    if (reps.length() > 2 && reps.startsWith("(") && reps.endsWith(")")) {    /* styles的长度大于2，则有可能有styles*/
                        String[] _reps = reps.substring(1, reps.length() - 1).split(",");
                        reps = "(";
                        for (String _rep : _reps) if (set.contains(getInteger(_rep))) reps = reps + _rep + ",";
                        reps = reps.substring(0, reps.length() - 1);
                        reps = reps + ")";
                    }
                    out.write(reps + ");");
                    out.write("\n");
                }/*                 else if("IFCCLOSEDSHELL".equals(ifcType.toUpperCase())) { List<String> insProperties = this.getIfcPropertiesByLineData(data.get(writtenItem)); out.write(writtenItem); out.write("=IFCCLOSEDSHELL("); String reps = insProperties.get(0); if(reps.length() > 2 && reps.startsWith("(") && reps.endsWith(")")){ String[] _reps = reps.substring(1, reps.length() - 1).split(","); reps = "("; for(String _rep : _reps){ if(set.contains(_rep)){ reps = reps + _rep + ","; } } reps = reps.substring(0, reps.length() - 1); reps = reps + ")"; } out.write(reps + ");"); out.write("\n"); }*/ else {
                    out.write("#"+writtenItem);
                    out.write("=");
                    out.write(data.get(writtenItem));
                    out.write("\n");
                }
            }
            out.write(getEnd4GeometrySlice());
            out.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }

        return path;
    }

    /**
     * @Description: 获取墙体空洞门窗连接关系
     * @Param:
     * @return:  Map<String,Map<String,String>> 门->空洞->门窗;type类型
     * @Author: Wang
     * @Date: 2019/12/23
     */
    public Map<String,Map<String,String>> getElementRelation()
    {
        Map<String,Map<String,String>> relation = new HashMap<>();
        List<Integer> wallWithElement = mapType2Line.get("IFCRELVOIDSELEMENT");
        List<Integer> othersWithElement = mapType2Line.get("IFCRELFILLSELEMENT");
        Map<String,String> elementToWall = new HashMap<>();
        if(wallWithElement!=null)
        {
            for (Integer line : wallWithElement)
            {
                List<String> proper = getPropertiesByLine(line);
                elementToWall.put(getGuidByLine(getInteger(proper.get(5))),getGuidByLine(getInteger(proper.get(4))));

            }
        }

        Map<String,String> elementToOthers = new HashMap<>();
        if(othersWithElement!=null)
        {
            for (Integer line : othersWithElement)
            {
                List<String> proper = getPropertiesByLine(line);
                elementToOthers.put(getGuidByLine(getInteger(proper.get(4))),getGuidByLine(getInteger(proper.get(5)))+";"+getTypeByLine(getInteger(proper.get(5))));
            }

        }

        for(Map.Entry<String,String> entry:elementToWall.entrySet())
        {
            if(!relation.containsKey(entry.getValue()))
            {
                Map<String,String> relationship = new HashMap<>();
                if(elementToOthers.containsKey(entry.getKey()))
                {
                    relationship.put(entry.getKey(),elementToOthers.get(entry.getKey()));
                    relation.put(entry.getValue(),relationship);
                }else
                {
                    relationship.put(entry.getKey(),null);
                    relation.put(entry.getValue(),relationship);
                }
            }else
            {
                if(elementToOthers.containsKey(entry.getKey()))
                {
                    relation.get(entry.getValue()).put(entry.getKey(),elementToOthers.get(entry.getKey()));
                }else
                {
                    relation.get(entry.getValue()).put(entry.getKey(),null);
                }
            }

        }

        return relation;
    }

    private static void getMemInfo(long AvaMemory,String string)
    {
        OperatingSystemMXBean mem = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
//        System.out.println("Total RAM：" + mem.getTotalPhysicalMemorySize() / 1024 / 1024 + "MB"); //获取总内存大小
        System.out.println(string + (AvaMemory - mem.getFreePhysicalMemorySize()) / 1024 / 1024 + "MB");
    }

}
