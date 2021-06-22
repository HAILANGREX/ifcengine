package com.boswinner.ifcengine.geometry;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.bimserver.geometry.Matrix;
import org.bimserver.plugins.renderengine.RenderEngineException;
import org.bimserver.plugins.renderengine.RenderEngineModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IfcGeoParser {

	private static final Logger logger = LoggerFactory.getLogger(IfcGeoParser.class);
    private List<IfcMeshEntity> geomServerClientEntities = new ArrayList<IfcMeshEntity>();
    private HashMap<String, Double> maxBoundary = new HashMap<>();
    private HashMap<String, Double> minBoundary = new HashMap<>();
	Properties props=new Properties();
    private String ifcOpenShellPath;
    private String ifcOpenShellVersion;

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

    public IfcGeoParser(InputStream in) throws RenderEngineException {
		this("/ifcengine.properties", in);
    }
	
    public IfcGeoParser(String configLocation, InputStream in) throws RenderEngineException {
    	init(configLocation);
    	parsingModel(in);
    }

    /**
     * 加载ifcopenshell,并传入数据流和插件所在位置
     *
     * @param in
     * @param ifcOpenshellPath
     * @throws RenderEngineException
     */
    private void parsingModel(InputStream in) throws RenderEngineException {
        IfcOpenShellEngine ifcOpenShellEngine = null;
        RenderEngineModel model = null;
        //动态配置ifcopenshell版本信息，便于更换新的插件。
        //这里读取不到配置，暂时写成固定的
        // IfcGeomServerClient.VERSION = openshellConf.getProcessVersion();
        IfcGeomServerClient.VERSION = ifcOpenShellVersion;//new String("IfcOpenShell-0.5.0-dev-2");
        try {
            //LOGGER.info("开始加载ifcopenshell，程序路径在: " + ifcOpenshellPath);
            ifcOpenShellEngine = new IfcOpenShellEngine(ifcOpenShellPath);
            ifcOpenShellEngine.init();
            model = ifcOpenShellEngine.openModel(in);
            //LOGGER.info("加载完ifcopenshell并获取到几何数据，开始组织成目标数据。");
            model.generateGeneralGeometry();
            //this.organizeTargetGeodata(((IfcOpenShellModel) model).getInstancesById());
            //LOGGER.info("ifcopenshell数据组织完成。");
        } catch (Exception e) {
            //LOGGER.error(e.getMessage());
            e.printStackTrace();
        } finally {
            if (model != null) {
                this.organizeTargetGeodata(((IfcOpenShellModel) model).getInstancesById());
                model.close();
            }
            if (ifcOpenShellEngine != null) {
                ifcOpenShellEngine.close();
            }
        }
    }

    /**
     * 从ifcopenshell中组织出目标数据
     *
     * @param geoInfoMap
     */
    private void organizeTargetGeodata(HashMap<Integer, IfcOpenShellEntityInstance> geoInfoMap) {
        //LOGGER.info("开始组织并计算边界信息。");
        int componetNum = 0;
        geomServerClientEntities.clear();
        //获取构件、几何列表
        for (Integer id : geoInfoMap.keySet()) {
            IfcOpenShellEntityInstance entityInstance = geoInfoMap.get(id);
            if (entityInstance != null) {
                componetNum++;
                IfcMeshEntity entity = entityInstance.getEntity();
                if (entity == null) {
                    continue;
                }
                geomServerClientEntities.add(entity);
                //计算边界信息
                /*for (int i = 0; i < entity.getIndices().length; i++) {
                    computeBoundary(entity.getMatrix(), entity.getPositions(), entity.getIndices()[i] * 3);
                }*/
            }
        }

        //LOGGER.info("组织计算边界信息完成。");
    }
    

    /**
     * 计算模型AABB包围和
     *
     * @param matrix
     * @param vertices
     * @param index
     */
    private void computeBoundary(double[] matrix, float[] vertices, int index) {
        double x = vertices[index];
        double y = vertices[index + 1];
        double z = vertices[index + 2];

        double[] result = new double[4];

        Matrix.multiplyMV(result, 0, matrix, 0, new double[]{x, y, z, 1}, 0);
        x = result[0];
        y = result[1];
        z = result[2];

        if (this.maxBoundary.isEmpty() || this.minBoundary.isEmpty()) {
            this.maxBoundary.put("x", x);
            this.maxBoundary.put("y", y);
            this.maxBoundary.put("z", z);
            this.minBoundary.put("x", x);
            this.minBoundary.put("y", y);
            this.minBoundary.put("z", z);
        } else {
            this.maxBoundary.put("x", Math.max(x, maxBoundary.get("x")));
            this.maxBoundary.put("y", Math.max(y, maxBoundary.get("y")));
            this.maxBoundary.put("z", Math.max(z, maxBoundary.get("z")));
            this.minBoundary.put("x", Math.min(x, minBoundary.get("x")));
            this.minBoundary.put("y", Math.min(y, minBoundary.get("y")));
            this.minBoundary.put("z", Math.min(z, minBoundary.get("z")));
        }
    }
    public List<IfcMeshEntity> getGeomServerClientEntities() {
        return geomServerClientEntities;
    }
}
