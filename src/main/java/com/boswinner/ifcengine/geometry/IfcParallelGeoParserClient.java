package com.boswinner.ifcengine.geometry;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RecursiveTask;

import org.antlr.stringtemplate.language.Cat;
import org.bimserver.geometry.Matrix;
import org.bimserver.plugins.renderengine.RenderEngineException;
import org.bimserver.plugins.renderengine.RenderEngineModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javafx.scene.input.KeyCode.T;

public class IfcParallelGeoParserClient extends RecursiveTask<List<IfcMeshEntity>> {

	private static final Logger logger = LoggerFactory.getLogger(IfcParallelGeoParserClient.class);

    private HashMap<String, Double> maxBoundary = new HashMap<>();
    private HashMap<String, Double> minBoundary = new HashMap<>();
    private String ifcOpenShellPath;
    private String ifcOpenShellVersion;
    
    private List<String> lstIfcSlices;
	private IfcMeshInterface ifcMeshInter;
	private String modelKey;
	public IfcParallelGeoParserClient(List<String> _lstIfcSlices, String ifcOpenshellPath, String ifcOpenshellVersion,IfcMeshInterface ifcMeshInterface,String key){
		ifcOpenShellPath = ifcOpenshellPath;
		ifcOpenShellVersion = ifcOpenshellVersion;
		lstIfcSlices = _lstIfcSlices;
		ifcMeshInter = ifcMeshInterface;
		modelKey = key;
	}

    /**
     * 加载ifcopenshell,并传入数据流和插件所在位置
     *
     * @param in
     * @param ifcOpenshellPath
     * @throws RenderEngineException
     * @throws IOException 
     */
    private ArrayList<IfcMeshEntity> parsingModel(String sb) throws RenderEngineException, IOException {
    	//StringsInputStream<List<String>> is = new StringsInputStream<List<String>>(sb);//  new   ByteArrayInputStream(sb.toString().getBytes());
        InputStream is = new FileInputStream(sb);
    	return parsingModel(is);
    }

    /**
     * 加载ifcopenshell,并传入数据流和插件所在位置
     *
     * @param in
     * @param ifcOpenshellPath
     * @throws RenderEngineException
     */
    private ArrayList<IfcMeshEntity> parsingModel(InputStream in) throws RenderEngineException {
//    private ArrayList<IfcMeshEntity> parsingModel(InputStream in, GeometryHandler<T> handler) throws RenderEngineException {
        ArrayList<IfcMeshEntity> lstMeshes = new ArrayList<>();
        
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
            //LOGGER.info("ifcopenshell数据组织完成。");
        } catch (Exception e) {
            //LOGGER.error(e.getMessage());
        	System.out.println("ERROR happens at: " + lstIfcSlices.get(0));
            e.printStackTrace();
        } finally {
            if (model != null) {
                lstMeshes = organizeTargetGeodata(((IfcOpenShellModel) model).getInstancesById());
                model.close();
            }
            if (ifcOpenShellEngine != null) {
                ifcOpenShellEngine.close();
            }
            

        	//System.out.println("Finished computing at: " + lstIfcSlices.get(0));
        }

//        T a = handler.processMeshEntity(lstMeshes);

        return lstMeshes;
    }

    /**
     * 从ifcopenshell中组织出目标数据
     *
     * @param geoInfoMap
     */
    private ArrayList<IfcMeshEntity> organizeTargetGeodata(HashMap<Integer, IfcOpenShellEntityInstance> geoInfoMap) {
    	//IfcMeshInterface ifcMeshInterface2 = new IfcMeshInterficeImpl();
    	ArrayList<IfcMeshEntity> lstMeshes = new ArrayList<>();
        int componetNum = 0;
        //获取构件、几何列表
        if(null != ifcMeshInter){
	        for (Integer id : geoInfoMap.keySet()) {
	            IfcOpenShellEntityInstance entityInstance = geoInfoMap.get(id);
	            if (entityInstance != null) {
	                componetNum++;
	                IfcMeshEntity entity = entityInstance.getEntity();
	                if (entity == null) {
	                    continue;
	                }
	                lstMeshes.add(entity);
					try {
						ifcMeshInter.saveGeometryInfo(entity,modelKey);
					} catch (Exception e) {
						e.printStackTrace();
					}
					//计算边界信息
	                /*for (int i = 0; i < entity.getIndices().length; i++) {
	                    computeBoundary(entity.getMatrix(), entity.getPositions(), entity.getIndices()[i] * 3);
	                }*/
	            }
	        }
        } else {
        	for (Integer id : geoInfoMap.keySet()) {
	            IfcOpenShellEntityInstance entityInstance = geoInfoMap.get(id);
	            if (entityInstance != null) {
	                componetNum++;
	                IfcMeshEntity entity = entityInstance.getEntity();
	                if (entity == null) {
	                    continue;
	                }
	                lstMeshes.add(entity);
	                //计算边界信息
	                /*for (int i = 0; i < entity.getIndices().length; i++) {
	                    computeBoundary(entity.getMatrix(), entity.getPositions(), entity.getIndices()[i] * 3);
	                }*/
	            }
	        }
        }
        return lstMeshes;
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
    
	@Override
	protected List<IfcMeshEntity> compute() {
		if(this.lstIfcSlices.size() == 1){
			try {
				return parsingModel(lstIfcSlices.get(0));
			} catch (RenderEngineException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return new ArrayList<IfcMeshEntity>();
			} catch (IOException ioe){
				// TODO Auto-generated catch block
				ioe.printStackTrace();
				return new ArrayList<IfcMeshEntity>();
			}
		}else{
			List<IfcParallelGeoParserClient> lstParser = new ArrayList<IfcParallelGeoParserClient>();
			for(String sb: this.lstIfcSlices){
				List<String> sbTmp = new ArrayList<String>();
				sbTmp.add(sb);
				IfcParallelGeoParserClient ifcParser = new IfcParallelGeoParserClient(sbTmp, this.ifcOpenShellPath, this.ifcOpenShellVersion,this.ifcMeshInter,this.modelKey);
				lstParser.add(ifcParser);
			}
			
			for(IfcParallelGeoParserClient ifcParser : lstParser){
				ifcParser.fork();
			}
			
			List<IfcMeshEntity> lstMeshes = new ArrayList<IfcMeshEntity>();
			for(IfcParallelGeoParserClient ifcParser : lstParser){
				try {
					lstMeshes.addAll(ifcParser.get());
				} catch (InterruptedException | ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			return lstMeshes;
		}
	}
}
