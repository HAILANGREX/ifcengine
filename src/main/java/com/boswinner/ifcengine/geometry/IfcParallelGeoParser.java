package com.boswinner.ifcengine.geometry;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IfcParallelGeoParser {

	private static final Logger logger = LoggerFactory.getLogger(IfcParallelGeoParser.class);
	Properties props=new Properties();
    private String ifcOpenShellPath;
    private String ifcOpenShellVersion;
    private List<IfcMeshEntity> lstAllIfcMeshes = new ArrayList<>();
    private HashMap<String, Double> maxBoundary = new HashMap<>();
    private HashMap<String, Double> minBoundary = new HashMap<>();
    
    private List<String> lstIfcSlices;

	public void init(String configLocation) {
		InputStream inStream=this.getClass().getResourceAsStream(configLocation);
		try{
			props.load(inStream);
			this.ifcOpenShellPath=props.getProperty("ifcengine.path");
			this.ifcOpenShellVersion=props.getProperty("ifcengine.version");
		}catch(IOException ex){
			logger.error("Failed to load ["+configLocation+"]");
		}
	}
	

	public IfcParallelGeoParser(List<String> _lstIfcSlices,IfcMeshInterface ifcMeshInterface,String modelKey) throws InterruptedException, ExecutionException{
		this("/ifcengine.properties", _lstIfcSlices,ifcMeshInterface,modelKey);
	}
	
	public IfcParallelGeoParser(String configLocation, List<String> _lstIfcSlices,IfcMeshInterface ifcMeshInterface,String modelKey) throws InterruptedException, ExecutionException{
		init(configLocation);
		lstIfcSlices = _lstIfcSlices;
		lstAllIfcMeshes = computeMeshes(ifcMeshInterface,modelKey);
	} 
	
    public List<IfcMeshEntity> getGeomServerClientEntities() {
        return lstAllIfcMeshes;
    }
	
	private List<IfcMeshEntity> computeMeshes(IfcMeshInterface ifcMeshInterface,String modelKey) throws InterruptedException, ExecutionException{
		 // 1. 创建任务
		IfcParallelGeoParserClient parsingTask = new IfcParallelGeoParserClient(
								lstIfcSlices, ifcOpenShellPath, ifcOpenShellVersion,ifcMeshInterface,modelKey);

        long begin = System.currentTimeMillis();

        // 2. 创建线程池
        ForkJoinPool forkJoinPool = new ForkJoinPool(10);

        // 3. 提交任务到线程池
        forkJoinPool.submit(parsingTask);

        // 4. 获取结果
        List<IfcMeshEntity> result = parsingTask.get();

        return result;
	}
}
