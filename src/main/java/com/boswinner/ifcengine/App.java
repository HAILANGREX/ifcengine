package com.boswinner.ifcengine;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

import com.google.gson.Gson;
import netscape.javascript.JSObject;
import org.bimserver.plugins.renderengine.RenderEngineException;

import com.boswinner.ifcengine.geometry.IfcGeoParser;
import com.boswinner.ifcengine.geometry.IfcMeshEntity;
import org.json.simple.JSONObject;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;


/**
 * Hello world!
 */
public class App {
    public static int[] FILENUMBER = {50, 50, 75, 100, 150, 200};
    public static int EXPECTED_NUMBER_OF_FILE = 120;

    public static void main(String[] args) throws Exception {

        System.gc();
        EXPECTED_NUMBER_OF_FILE = FILENUMBER[0];
//    	String path = "D:\\案例\\su模型\\新建文件夹 (2)\\办公楼模型.ifc";  //设置path
        String path = "D:\\案例\\整体广场-1.1.ifc";  //设置path
        String aa = IfcStringDecoder.decode("/X/14");
        long start = System.currentTimeMillis();
        System.out.println("FILE NUMBER:  " + EXPECTED_NUMBER_OF_FILE);
        IfcFile f = new IfcFile(path);
//        int b = f.getComponentsAmount();                //获取构件数量预测
//        f.printfData();//输出潜在关联关系数据
//        Map<Integer ,Set<Integer>> conne =  f.getHeterogeneous();// 输出异质树
        long startTime = System.currentTimeMillis();
//        System.out.println("文件读入时间为：  "+(startTime-start)/(double)1000);
//


//        Map<String, Object> mo = f.getAttributes();                                     //属性解析部分
        Map<Integer,Integer > bbab = f.getSpatialParent();  //获取父节点
        Map<Integer,List<Integer> > bbbc = f.getSpatialChildren();  //获取子节点
//        Map<String,String > map =  f.getComponentIncludeSystem();  //获取系统构建所属族群
//        Map<String,List<String> > maps  = f.getSystemGroupList();  //获取系统群下所有构件
//        List<String> name = new ArrayList<>();
//        for (Integer names : bbab.keySet())
//        {
//            name.add(f.getNameByLine(names)) ;
//        }
////
        List<String> lstSlices = f.getGeometrySlices();// 5M                             //ifc拆分部分
        Boolean a= f.getSeg();
        long endTime = System.currentTimeMillis();
		System.out.println("完成文件拆分，开始解析数据,拆分时间为：  "+(endTime-startTime)/(double)1000 +"文件个数为：  "+lstSlices.size());
//        long enddTime = System.currentTimeMillis();
//        System.out.println("构件数量预测时间为：  " + (enddTime - endTime) / (double) 1000);
        List<IfcMeshEntity> lstMeshes = f.getGeometryInTriangles(lstSlices,null,null);   //分布式解析部分
        long Geotime = System.currentTimeMillis();
////        System.out.println(" 完成解析数据,开始写数据,解析时间为： "+(Geotime-endTime)/(double)1000+" \n 估值：  " + "构件个数为：  " +lstMeshes.size());

//        List<String> cont = new ArrayList<>();
//
//        for(IfcMeshEntity ifcMeshEntity:lstMeshes)
//        {
//            if(cont.contains(ifcMeshEntity.getGuid()))
//            {
//                System.out.print(ifcMeshEntity.getGuid()+"\n");
//            }else
//            {
//                cont.add(ifcMeshEntity.getGuid());
//            }
//        }
//        System.out.println(": 完成属性数据。");
//
        /**
         * @Description: 原始解析
         * @Param: [args]
         * @return: void
         * @Author: Wang
         * @Date: 2019/9/5
         */
        System.out.println("准备开始原始对比解析，可能需要较长时间请耐心等待....");
        long Geotime22 = System.currentTimeMillis();
        InputStream in = new FileInputStream(path);
        IfcGeoParser ifcGeoParser = new IfcGeoParser(in);
        List<IfcMeshEntity> lstMeshesfull = ifcGeoParser.getGeomServerClientEntities();
        long Geo2time = System.currentTimeMillis();
        System.out.println(" 原始解析时间为： " + (Geo2time - Geotime22) / (double) 1000);
        

        Map<String,String > mapssss =   f.getComponentToSystem();
        Map<String,List<String> > mapsss = f.getSystemGroupList();
        Map<String,List<String> > mapss =  f.getComponentRelationship();  //获取系统构建所属族群
//        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");      //设置日期格式

        String unit = f.getUnit();    //获取单位
        Map<String,String > bbb = f.getHeaderMap(); //获取头文件
//        Map<String,String > bbab = f.getSpatialParent();  //获取父节点
//        Map<String,List<String> > bbbc = f.getSpatialChildren();  //获取子节点
//        Map<String,String> map1 = f.getGeometrySlicesInString_TypePref();//新文件分组
//        boolean seg = f.getseg();  //获取切分状态
//
//



////      几何面数量比对
//        Map<String, IfcMeshEntity> list = new HashMap<>();
//        int i = 0;
//        for (IfcMeshEntity ifcMeshEntity:lstMeshes)
//        {
//
//            i=i+ifcMeshEntity.getMaterialIndices().length; //获取模型几何面的数量
////            if (list.containsKey(ifcMeshEntity.getGuid()))
////            {
//////                IfcMeshEntity ifcMeshEntity1 = list.get(ifcMeshEntity.getGuid());
//////                System.out.print(ifcMeshEntity.getGuid()+"\n");
////
////            }
////           else
////                list.put(ifcMeshEntity.getGuid(),ifcMeshEntity);
//        }



//        if (EXPECTED_NUMBER_OF_FILE == 50) {
//      输出二进制文件
//            List<Map<String, Object>> geos = new ArrayList<>();
//            int comId = 0;
//            for (IfcMeshEntity geoEntity:lstMeshes)
//            {
//                Map<String, Object> geoMap = new HashMap<>();
//
//                geoMap.put("positions", geoEntity.getPositions());
//                geoMap.put("indices", geoEntity.getIndices());
//                geoMap.put("normals", geoEntity.getNormals());
//                geoMap.put("colors", geoEntity.getColors());
//                geoMap.put("materialIndices", geoEntity.getMaterialIndices());
//                geoMap.put("geoId", comId);
//                comId++;
//                geos.add(geoMap);
//            }
//            for (int i = 0; i < geos.size(); i++) {
//                Object aas = geos.get(i).get("positions");
//                JSONArray json = (JSONArray) JSON.toJSON(aas);
//                ArrayList<Float> bosFiles = (ArrayList) JSONArray.parseArray(json.toString()).toJavaList(Float.class);
//
//                float[] array = new float[bosFiles.size()];
//                for (int ic = 0; ic < bosFiles.size(); ic++) {
//                    array[ic] = (float) bosFiles.get(ic);
//
//                }
//
//                @SuppressWarnings("unchecked")
//                Float[] a = ((Float[]) geos.get(i).get("positions"));
//                Integer b = ((ArrayList<Double>) geos.get(i).get("positions")).size();
//            }


            //原始解析
//        	System.out.println("准备开始原始对比解析，可能需要较长时间请耐心等待....");
//            InputStream in = new FileInputStream(path);
////      long startTime=System.currentTimeMillis();
//            IfcGeoParser ifcGeoParser = new IfcGeoParser(in);
//            List<IfcMeshEntity> lstMeshesfull = ifcGeoParser.getGeomServerClientEntities();
//
//            long Geo2time = System.currentTimeMillis();
//
//            System.out.println(" 原始解析时间为： "+(Geo2time-Geotime)/(double)1000);
//            List<String> list2 = new ArrayList<>();
//            for (IfcMeshEntity ifcMeshEntity:lstMeshesfull)
//            {
//                if (list2.contains(ifcMeshEntity.getGuid()))
//                {
//                    System.out.print(ifcMeshEntity.getGuid()+"\n");
//                }else
//                    list2.add(ifcMeshEntity.getGuid());
//            }
//
//
//            lstMeshesfull.contains("1");
//                  long endTime=System.currentTimeMillis();
//            		System.out.println("完成文件拆分，开始解析数据,拆分时间为：  "+(endTime-startTime)/(double)1000 );
//            Map<Integer,IfcMeshEntity> list2=new HashMap<Integer, IfcMeshEntity>();
//			Map<Integer,IfcMeshEntity> list1=new HashMap<Integer,IfcMeshEntity>();
//			for(int a=0 ;a < lstMeshes.size();a++){
//				list1.put(lstMeshes.get(a).getId(),lstMeshes.get(a));
//			}
//			for(int a=0 ;a < lstMeshesfull.size();a++){
//				list2.put(lstMeshesfull.get(a).getId(),lstMeshesfull.get(a));
//			}
//            int deficiencyNumber = 0;
//			boolean full = true;
//            for (Integer id:list2.keySet()){
//				boolean contains = list1.containsKey(id);
//
//						if(contains){
//							if(!Arrays.equals(list1.get(id).getMatrix(),list2.get(id).getMatrix())){
//								System.out.println( "矩阵值不一致 , component ID:  "+ list2.get(id).getId());     //矩阵测试
//								full = false;
//							}
//
//							if(list1.get(id).getPositions().length != list2.get(id).getPositions().length){
//								System.out.println( "几何信息不一致, component ID:  "+ list2.get(id).getId());   //几何信息测试
//								full = false;
//						if(list1.get(id).getPositions().length > list2.get(id).getPositions().length)
//							System.out.println( "切分的构件点数更多了！");
//						else
//							System.out.println( "切分后缺失了点信息");
//					}
//					if(!Arrays.equals(list1.get(id).getColors(),list2.get(id).getColors())){
//						System.out.println( "颜色不一致, component ID:  "+ list2.get(id).getId());      //颜色测试
//						full = false;
//					}
//				}else
//					{
//					System.out.println("构件缺失! , component ID:  " + list2.get(id).getId());
//					deficiencyNumber+=1;
//				}
//			}
//			if(full){
//				System.out.println("完美解析，无任何信息缺失");
//			}
//			else {
//			System.out.println("构件缺失总数量为:  " + deficiencyNumber +"\n正确的构件个数应该为: "+list2.size());
//            }
//        }
        System.out.println("\n ");
    }
}
