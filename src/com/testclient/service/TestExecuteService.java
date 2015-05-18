package ctripwireless.testclient.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import shelper.iffixture.HTTPFacade;
import ctripwireless.testclient.enums.ActionFileName;
import ctripwireless.testclient.enums.CheckPointResult;
import ctripwireless.testclient.enums.CheckPointType;
import ctripwireless.testclient.enums.HistoryFolderName;
import ctripwireless.testclient.enums.LoopParameterNameInForm;
import ctripwireless.testclient.enums.PreConfigType;
import ctripwireless.testclient.enums.SeperatorDefinition;
import ctripwireless.testclient.enums.TestStatus;
import ctripwireless.testclient.factory.JsonObjectMapperFactory;
import ctripwireless.testclient.httpmodel.CheckPointItem;
import ctripwireless.testclient.httpmodel.Json;
import ctripwireless.testclient.httpmodel.PreConfigItem;
import ctripwireless.testclient.httpmodel.ServerItem;
import ctripwireless.testclient.httpmodel.ServiceBoundDataItem;
import ctripwireless.testclient.httpmodel.SqlEntity;
import ctripwireless.testclient.httpmodel.TestResultItem;
import ctripwireless.testclient.model.CheckPointContianer;
import ctripwireless.testclient.model.HttpTarget;
import ctripwireless.testclient.model.InvokeRequest;
import ctripwireless.testclient.model.InvokeResponse;
import ctripwireless.testclient.model.KeyValue;
import ctripwireless.testclient.model.Parameter;
import ctripwireless.testclient.model.PreConfigContainer;
import ctripwireless.testclient.model.SocketTarget;
import ctripwireless.testclient.model.SqlQueryReturn;
import ctripwireless.testclient.utils.Auto;
import ctripwireless.testclient.utils.FileNameUtils;
import ctripwireless.testclient.utils.JdbcUtils;
import ctripwireless.testclient.utils.MyFileUtils;
import ctripwireless.testclient.utils.SocketOperationUtils;
import ctripwireless.testclient.utils.TemplateUtils;

@Service("testExecuteService")
public class TestExecuteService {
	private static final Logger logger = Logger.getLogger(TestExecuteService.class);
	@Autowired
	OutputParameterService outputParameterService;
	@Autowired
	SocketOperationUtils socketOperationUtils;
	
	public Json executeTest(Map<String, Object> request) {
		Json j=new Json();
		String path = request.get("__HiddenView_path").toString();
		Map<String, Object> datamap=getParametersFromPreConfigFile(path,request);
		request.putAll(datamap);
		request=getRequestParameterMap(request);
		TestResultItem testresult = getTestResultItem(path,request);
		if(!testresult.getResult().equals(TestStatus.exception)){
			getCheckpointsAndResultFromFile(path, request, testresult.getResponseInfo(),testresult);
			j.setSuccess(true);
		}else{
			j.setMsg("执行异常：\n" + testresult.getComment());
			j.setSuccess(false);
		}
		j.setObj(testresult);
		return j;
	}
	
	public void setupAction(String testPath){
		executeSqlAction(testPath,ActionFileName.setup);
		executeServiceAction(testPath,ActionFileName.setup);
	}
	
	public void teardownAction(String testPath,Map requestParas,String response){
		executeSqlAction(testPath,ActionFileName.teardown,requestParas,response);
		executeServiceAction(testPath,ActionFileName.teardown);
	}
	
	private void executeServiceAction(String testPath, String actionType){
		try {
			File f=new File(testPath+"/"+actionType+"2");
			if(f.exists()){
				String serviceCalled=FileUtils.readFileToString(f);
				Map request = getRequestParameterMap(serviceCalled);
				executeServiceRequest(serviceCalled,request);
			}
		} catch (IOException e) {
			logger.error(e.getClass()+e.getMessage());
		}
	}
	
	private int executeSqlAction(String testPath, String actionType){
		File f=new File(testPath+"/"+actionType);
		if(f.exists()){
			try {
				String sqlactionstr = FileUtils.readFileToString(f, "UTF-8");
				if(sqlactionstr.contains("[[") && sqlactionstr.contains("]]"))
					sqlactionstr=processEnv(loadEnv(testPath),sqlactionstr);
				ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
				SqlEntity e = mapper.readValue(sqlactionstr, SqlEntity.class);
				String source=e.getSource();
				String server=e.getServer();
				String port=e.getPort();
				String username=e.getUsername();
				String password=e.getPassword();
				String database=e.getDatabase();
				String sql=e.getSql();
				return new JdbcUtils(source, server, port, username, password, database).executeSqlAction(sql);
			} catch (JsonParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JsonMappingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return 0;
	}
	
	private int executeSqlAction(String testPath, String actionType,Map reqParas,String response){
		File f=new File(testPath+"/"+actionType);
		if(f.exists()){
			try {
				String sqlactionstr = FileUtils.readFileToString(f, "UTF-8");
				if(sqlactionstr.contains("[[") && sqlactionstr.contains("]]"))
					sqlactionstr=processEnv(loadEnv(testPath),sqlactionstr);
				ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
				SqlEntity e = mapper.readValue(sqlactionstr, SqlEntity.class);
				String source=e.getSource();
				String server=e.getServer();
				String port=e.getPort();
				String username=e.getUsername();
				String password=e.getPassword();
				String database=e.getDatabase();
				String sql=e.getSql();
				//resolve string if contains parameter
				sql = TemplateUtils.getString(sql, reqParas);
				if(actionType.equalsIgnoreCase(ActionFileName.teardown)){
					sql=processOutputParameter(testPath, response, sql);
				}
				return new JdbcUtils(source, server, port, username, password, database).executeSqlAction(sql);
			} catch (JsonParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JsonMappingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return 0;
	}
	
	public Set<CheckPointItem> getCheckpointsAndResultFromFile(String foldername,Map parameters, String responseinfo, TestResultItem testresult){
		try{
			File checkpoint=new File(FileNameUtils.getCheckPointsFilePath(foldername));
			if(checkpoint.exists()){
				ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
				String ckstr = FileUtils.readFileToString(checkpoint, "UTF-8");
				CheckPointContianer c;
				if(ckstr.indexOf("[[")>0 && ckstr.indexOf("]]")>ckstr.indexOf("[[")){
					ckstr=processEnv(loadEnv(foldername),ckstr);
					c = mapper.readValue(ckstr, CheckPointContianer.class);
				}else
					c = mapper.readValue(checkpoint, CheckPointContianer.class);
				testresult.setResult(TestStatus.pass);
				if(c.getCheckPoint().entrySet().size()==0){
					testresult.setResult(TestStatus.invalid);
					return testresult.getCheckPoint();
				}
				//String responsebody=responseinfo.substring(responseinfo.indexOf("[body]:")+1);
				for(Entry<String,CheckPointItem> entry:c.getCheckPoint().entrySet()){
					CheckPointItem item = new CheckPointItem();
					item=entry.getValue();
					String checktype=item.getType();
					//resolve string if contains parameter
					String checkInfo = TemplateUtils.getString(item.getCheckInfo(),parameters);
					item.setCheckInfo(checkInfo);
					if(checktype.equals(CheckPointType.CheckSql)){
						checkInfo=this.processOutputParameter(foldername, responseinfo, checkInfo);
						addCheckPointItemsForDBVerification(testresult,checkInfo,responseinfo);
					}else if(checktype.equals(CheckPointType.CheckJsExp)){
						String[] arr=checkInfo.split(SeperatorDefinition.checkInfoSeperator);
						String objtext=responseinfo.replaceAll("\\n","").replaceAll("\\r","");
						objtext=StringUtils.substringAfter(objtext, arr[0]);
						if(!arr[1].isEmpty()){
							objtext=StringUtils.substringBefore(objtext, arr[1]);
						}
						addCheckPointItemsForJsExpVerification(testresult,arr[2].split("`"),objtext.trim());
					}else{
						boolean r=false;
						if(checktype.equals(CheckPointType.CheckContain)){
							r = responseinfo.contains(checkInfo);
						}else if(checktype.equals(CheckPointType.CheckPattern)){
							try{
								r = responseinfo.replaceAll("\\n","").replaceAll("\\r","").matches(checkInfo);
							}catch(Exception e){
								r=false;
								item.setCheckInfo(checkInfo+"\n"+"正则表达式异常："+e.getMessage());
							}
						}
						if(r){
							item.setResult(CheckPointResult.pass);
						}else{
							item.setResult(CheckPointResult.fail);
							if(testresult.getResult().equalsIgnoreCase(CheckPointResult.pass))
								testresult.setResult(TestStatus.fail);
						}
						testresult.getCheckPoint().add(item);
					}
				}
			}
			else
				testresult.setResult(TestStatus.invalid);
		}catch(Exception e){
			logger.error("test execute error",e);
		}
		return testresult.getCheckPoint();
	}
	
	private void addCheckPointItemsForDBVerification(TestResultItem testresult, String setting, String response){
		String[] arr=setting.split(SeperatorDefinition.checkInfoSeperator);
		if(arr.length==8){
			String source=arr[0];
			String server=arr[1];
			String port=arr[2];
			String username=arr[3];
			String password=arr[4];
			String db=arr[5];
			String sql=arr[6];
			String data=arr[7];
			SqlQueryReturn sqr= new JdbcUtils(source,server,port,username,password,db).getReturnedColumnsAndRows(sql);
			for(String item : data.split(SeperatorDefinition.verifiedDataRow)){
				String[] a=item.split(SeperatorDefinition.verifiedDataItem);
				String column=a[0];
				String rowIndex=a[1];
				String comparedType=a[2];
				String expectedValue=a[3].trim();
				String actualValue=new JdbcUtils(source,server,port,username,password,db).getValueByColumnAndRowIndex(sqr,column,rowIndex);
				actualValue=actualValue.trim();
				boolean res=false;
				if(comparedType.equalsIgnoreCase("equal")){
					res=expectedValue.equalsIgnoreCase(actualValue);
				}else if(comparedType.equalsIgnoreCase("contain")){
					res=expectedValue.contains(actualValue);
				}else if(comparedType.equalsIgnoreCase("pattern")){
					res=actualValue.matches(expectedValue);
				}else if(comparedType.equalsIgnoreCase("equalFromResponse")){
					String[] str = expectedValue.split(SeperatorDefinition.shrinkResponseSeperator);
					expectedValue=getParaValueFromResponse(response,str[0],str[1],Integer.parseInt(str[2]));
					res=expectedValue.equalsIgnoreCase(actualValue);
				}
				CheckPointItem cp=new CheckPointItem();
				cp.setName("Verify Column: "+column+" in DB: "+db);
				cp.setType("sql "+comparedType);
				cp.setCheckInfo("Expected: "+expectedValue+"; Actual: "+actualValue);
				cp.setResult(res ? CheckPointResult.pass : CheckPointResult.fail);
				testresult.getCheckPoint().add(cp);
				if(testresult.getResult().equalsIgnoreCase(CheckPointResult.pass)){
					if(!res)
						testresult.setResult(CheckPointResult.fail);
				}
			}
		}
	}
	
	//需要modejs支持
	private void addCheckPointItemsForJsExpVerification(TestResultItem testresult, String[] exps, String objtext){
		String objDef="";
		String res="";
		if(!objtext.isEmpty()){
			if(objtext.indexOf("{")>-1 & objtext.indexOf("{")<objtext.indexOf("}")){
				objDef="var obj=JSON.parse('"+objtext.replace(" ", "").replace("'", "\"")+"');";
			}
			//环境安装：xmldom npm包
			else if(objtext.indexOf("<")>-1 & objtext.indexOf("<")<objtext.indexOf(">")){
				objDef="var DOMParser = require('xmldom').DOMParser1;var obj=new DOMParser().parseFromString('"+objtext.replace("'", "\"")+"','text/xml');";
			}
			for(int i=0;i<exps.length;i++){
				objDef+="console.info("+exps[i]+");";
			}
			
			String filename=new Date().getTime()+".js";
			File f=new File(filename);
			try{
				f.createNewFile();
				FileUtils.writeStringToFile(f, objDef);
				Runtime runtime = Runtime.getRuntime();
				Process p = runtime.exec("cmd /k node "+f.getAbsolutePath());
				InputStream err = p.getErrorStream();
				InputStream is = p.getInputStream();
				p.getOutputStream().close(); 
				res = IOUtils.toString(err,"gbk");
				res += IOUtils.toString(is,"gbk");	
				res = StringUtils.substringBetween(res, "", "\n\r");
				int exitVal = p.waitFor();   
			}catch(Exception e){
				res=e.getMessage();
			}finally{
				if(f.exists())
					f.delete();
			}
		}
		String[] result=res.split("\n");
		for(int i=0;i<exps.length;i++){
			CheckPointItem cp=new CheckPointItem();
			cp.setName("Verify content by js expression "+(i+1));
			cp.setType("js expression");
			cp.setCheckInfo(exps[i]);
			boolean r=Boolean.parseBoolean(result.length==exps.length ? result[i] : "false");
			cp.setResult(r ? CheckPointResult.pass : CheckPointResult.fail);
			testresult.getCheckPoint().add(cp);
			if(testresult.getResult().equalsIgnoreCase(CheckPointResult.pass)){
				if(!r)
					testresult.setResult(CheckPointResult.fail);
			}
		}
		
	}
	
	
	private String getParameterValueAfterRequest(String extraInfo){
		String val="";
		String[] parainfo=extraInfo.split(SeperatorDefinition.paraForReferencedService);
		String path=parainfo[0];
		String lb=parainfo[1];
		String rb=parainfo[2];
		return getParaValueAfterRequest(path,lb,rb,1);
	}
	
	private String getParaValueAfterRequest(String path,String lb,String rb,int times){
		Map paras = getRequestParameterMap(path);
		String res = getTestResultItem(path,paras).getResponseInfo();
		int startpos = res.indexOf(lb);
		if(startpos>0){
			while(times-->0){
				if(startpos>0)
					res = res.substring(startpos+lb.length());
				startpos = res.indexOf(lb);
			}
			int endpos = res.indexOf(rb);
			if(endpos>0){
				res = res.substring(0, endpos);
			}else
				res="";
		}else
			res="";
		return res;
	}
	
	private String getParaValueFromResponse(String response,String lb,String rb,int times){
		int startpos = response.indexOf(lb);
		if(startpos>0){
			while(times-->0){
				if(startpos>0)
					response = response.substring(startpos+lb.length());
				startpos = response.indexOf(lb);
			}
			int endpos = response.indexOf(rb);
			if(endpos>0){
				response = response.substring(0, endpos);
			}else
				response="";
		}else
			response="";
		return response;
	}
	
	//for back-end test execution usage
	public Map<String,Object> getRequestParameterMap(String path){
		Map<String,Object> request=new HashMap<String,Object>();
		try {
			request.put("auto", new Auto());
			ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
			String targetjson="";
			Map<String, Parameter> paras = new HashMap<String, Parameter>();
			if(path.endsWith("-leaf")){
				targetjson=FileNameUtils.getHttpTarget(path);
				HttpTarget target = mapper.readValue(new File(targetjson), HttpTarget.class);
				paras=target.getParameters();
			}else if(path.endsWith("-t")){
				targetjson=FileNameUtils.getSocketTestAbsPath(path);
				SocketTarget target = mapper.readValue(new File(targetjson), SocketTarget.class);
				paras=target.getParameters();
			}
			for(Parameter p : paras.values()){
				String val=p.getDefaultValue();
				//if defaultvalue is returned function of Auto class.
				val = TemplateUtils.getString(val, request);
				if(p.getType().equalsIgnoreCase("loop")){
					p.setName(LoopParameterNameInForm.name);
				}
				else if(p.getType().equalsIgnoreCase("service")){
					val=getParameterValueAfterRequest(p.getExtraInfo());
				}
				request.put(p.getName(), val);
			}
			Map<String, Object> datamap=getParametersFromPreConfigFile(path,request);
			request.putAll(datamap);
		}catch (Exception e) {
			// TODO Auto-generated catch block
			logger.error(e.getClass().toString()+": "+e.getMessage());
		}
		return request;
	}
	
	//for submit form usage
	private Map<String,Object> getRequestParameterMap(Map<String, Object> request){
		try{
			for(Entry<String, Object> e : request.entrySet()){
				String val=e.getValue().toString();
				if(val!=null && !val.isEmpty()){
					if(val.contains(SeperatorDefinition.paraForReferencedService)){
						val=getParameterValueAfterRequest(val);
						request.put(e.getKey(), val);
						break;
					}
				}	
			}
		}catch(Exception e){
			logger.error(e.getClass().toString()+": "+e.getMessage());
		}
		return request;
	}
	
	private Map<String,Object> getParametersFromPreConfigFile(String testPath,Map<String,Object> request){
		Map<String,Object> para=new HashMap<String,Object>();
		try {
			File f=new File(FileNameUtils.getPreConfigFilePath(testPath));	
			if(f.exists()){
				String preconfigstr = FileUtils.readFileToString(f, "UTF-8");
				if(preconfigstr.contains("[[") && preconfigstr.contains("]]"))
					preconfigstr=processEnv(loadEnv(testPath),preconfigstr);
				ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
				PreConfigContainer c = mapper.readValue(preconfigstr, PreConfigContainer.class);
				for(Entry<String,PreConfigItem> entry:c.getPreConfig().entrySet()){
					String type=entry.getValue().getType();
					String setting=entry.getValue().getSetting();
					if(type.equalsIgnoreCase(PreConfigType.service)){
						String[] arr=setting.split(SeperatorDefinition.paraForReferencedService);
						String path=arr[0];
						String[] configs=arr[1].split(SeperatorDefinition.queryBoundRow);
						for(String item : configs){
							String[] info=item.split(SeperatorDefinition.queryBoundItem);
							String lb=info[1];
							String rb=info[2];
							int times=Integer.parseInt(info[3]);
							String value=getParaValueAfterRequest(path,lb,rb,times);				
							para.put(info[0], value);
						}
					}else if(type.equalsIgnoreCase(PreConfigType.query)){
						String[] arr=setting.split(SeperatorDefinition.paraForReferencedService);
						String datasource=arr[0];
						String server=arr[1];
						String port=arr[2];
						String username=arr[3];
						String password=arr[4];
						String db=arr[5];
						String sql=arr[6];
						if(sql.contains("${")){
							try {
								sql = TemplateUtils.getString(sql, request);
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
//						Map<String,String> evnmap=loadEnv(testPath);
//						if(server.startsWith("[["))
//							server=processVariableInEnv(evnmap,server);
//						if(port.startsWith("[["))
//							port=processVariableInEnv(evnmap,port);
//						if(username.startsWith("[["))
//							username=processVariableInEnv(evnmap,username);
//						if(password.startsWith("[["))
//							password=processVariableInEnv(evnmap,password);
//						if(db.startsWith("[["))
//							db=processVariableInEnv(evnmap,db);
						String[] configs=arr[7].split(SeperatorDefinition.queryBoundRow);
						SqlQueryReturn sqr= new JdbcUtils(datasource,server,port,username,password,db).getReturnedColumnsAndRows(sql);
						for(String item : configs){
							String[] info=item.split(SeperatorDefinition.queryBoundItem);
							String columnLabel=info[1];
							String rowIndex=info[2];
							String value=new JdbcUtils(datasource,server,port,username,password,db).getValueByColumnAndRowIndex(sqr,columnLabel,rowIndex);
							para.put(info[0], value);	
						}
					}
				}
			}
		}catch (IOException e) {
			logger.error(e.getClass()+e.getMessage());
		}
		return para;
	}
	
	
	public void generateHistoryFile(String foldername, TestResultItem testresult) {
		try{
			String folder = foldername + "/"+HistoryFolderName.folderName;
			String filename = FileNameUtils.getResultFile(testresult.getTime(), testresult.getDuration(),testresult.getResult());
			File dir=new File(folder);
			if(!dir.exists()){
				dir.mkdirs();
			}
			File file=new File(dir,filename);
			if(!file.exists()){
				file.createNewFile();
			}
			ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
			mapper.writeValue(file, testresult);
		} catch (JsonGenerationException e) {
			logger.error("生成历史记录文件失败", e);
		} catch (JsonMappingException e) {
			logger.error("生成历史记录文件失败", e);
		} catch (IOException e) {
			logger.error("生成历史记录文件失败", e);
		}
	}
	
	private int executeHttpServiceRequest(String path, Map request){
		int reponsestatus=0;
		try{
			Map<String,String> evnmap=loadEnv(path);
			ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
			String httptargetjson=FileNameUtils.getHttpTarget(path);
			HttpTarget target = mapper.readValue(new File(httptargetjson), HttpTarget.class);
			String url=processEnv(evnmap,target.getPath());
			url=TemplateUtils.getString(url, request);
			HTTPFacade hf=new HTTPFacade();
			hf.setRequesttimeout(600*1000);
			hf.setUrl(url);
			String body=processEnv(evnmap,target.getRequestBody());
			body=TemplateUtils.getString(body, request);
			Set<KeyValue> headset=target.getHeads();
			for(KeyValue kv:headset){
				hf.addHeaderValue(kv.getKey(), kv.getValue());
			}
			if(body==null || body.trim().equals("")){
				hf.get();
			}else{
				for(Object e : request.entrySet()){
					Object v=((Entry<String,String>)e).getValue();
					if(v instanceof String){
						String k=((Entry<String,String>)e).getKey();
						hf.addParamValue(k, v.toString());
					}
				}
				hf.addRequestBody(body);
				hf.postWithQueryStrInUrl();
			}
			reponsestatus= hf.getStatus();
		}catch(Exception e){
			logger.error(e.getClass()+e.getMessage());
		}
		return reponsestatus;
	}
	
	private void executeSocketServiceRequest(String path, Map request){
		try{
			ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
			String socketconfig=FileNameUtils.getSocketTestAbsPath(path);
			SocketTarget target = mapper.readValue(new File(socketconfig), SocketTarget.class);
			String head=retrieveString(target.getHead(),path,request);
			String body=retrieveString(target.getBody(),path,request);
			String name=target.getServer().getName();
			String ip=retrieveString(target.getServer().getIp(),path,request);
			String port=retrieveString(target.getServer().getPort(),path,request);
			String protocol=target.getServer().getProtocol();
			String code=target.getCode();
			int dataversion=target.getDatagramVersion();
			boolean indented=target.isIndented();
			InvokeRequest req = new InvokeRequest();
			ServerItem si=new ServerItem();
			si.setName(name);
			si.setIp(ip);
			si.setPort(port);
			si.setProtocol(protocol);
			req.setServer(si);
			req.setCode(code);
			req.setHead(head);
			req.setBody(body);
			req.setDatagramVersion(dataversion);
			req.setIndented(indented);
			socketOperationUtils.invokeService(req);
		}catch(Exception e){
			logger.error(e.getClass()+e.getMessage());
		}
	}
	
	private void executeServiceRequest(String path, Map request){
		if(path.endsWith("-leaf")){
			executeHttpServiceRequest(path,request);
		}else if(path.endsWith("-t")){
			executeSocketServiceRequest(path,request);
		}
	}
	
	public TestResultItem getTestResultItem(String folderName, Map request){
		TestResultItem testresult=new TestResultItem();
		if(folderName.endsWith("-leaf")){
			testresult=getHttpTestResultItem(folderName,request);
		}else if(folderName.endsWith("-t")){
			testresult=getSocketTestResultItem(folderName,request);
		}
		return testresult;
	}
	
	private TestResultItem getHttpTestResultItem(String path, Map request){
		TestResultItem testresult=new TestResultItem();
		try{	
			String requestinfo="";
			String resopnseinfo="";
			Set<String> callbackset=new HashSet<String>();
			Map<String,String> evnmap=loadEnv(path);
			ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
			String httptargetjson=FileNameUtils.getHttpTarget(path);
			HttpTarget target = mapper.readValue(new File(httptargetjson), HttpTarget.class);
			String url=retrieveString(target.getPath(),path, request).trim();
			boolean ishttps=url.startsWith("https") ? true : false;
			HTTPFacade hf=new HTTPFacade(ishttps);
			hf.setRequesttimeout(600*1000);
			hf.setUrl(url);
			String body=retrieveString(target.getRequestBody(),path, request);
			requestinfo="[url]:\n"+url+"\n[request headers]:\n";	
			Set<KeyValue> headset=target.getHeads();
			for(KeyValue kv:headset){
				String k=retrieveString(kv.getKey(),path, request);
				String v=retrieveString(kv.getValue(),path, request);
				hf.addHeaderValue(k, v);
				requestinfo+=k + ":"+v+"\n";
			}
			requestinfo+="[request body]:\n"+body;
			
			long start = System.currentTimeMillis();
			if(body==null || body.trim().equals("")){
				hf.get();
			}else{
				for(Object e : request.entrySet()){
					Object v=((Entry<String,String>)e).getValue();
					if(v instanceof String){
						String k=((Entry<String,String>)e).getKey();
						hf.addParamValue(k, v.toString());
					}
				}
				hf.addRequestBody(body);
				hf.postWithQueryStrInUrl();
			}
			long end = System.currentTimeMillis();
			long duration = end - start;
			testresult.setDuration(String.valueOf(duration));
			
			String responsebody=hf.getResponseBody();
			int responsestatus=hf.getStatus();
			String responseheader="";
			if(!responsebody.isEmpty()){
				responseheader=hf.getResponseheaders();
			}
			logger.info("REQUEST finish with status:"+responsestatus+"\nresponse body:"+responsebody+"\n reponse heads:"+responseheader);
			resopnseinfo="[status]:\n" + responsestatus + "\n" ;
			resopnseinfo+="[response headers]:\n" + responseheader + "\n" ;
			resopnseinfo+="[body]:\n" + responsebody;
			if(responsestatus==200){
				if(request.get("testcallback")!=null && request.get("testcallback").equals("callPayRedirction")){
					callbackset.add(callBack(request,responsebody));
				}else if(request.get("testcallback")!=null && request.get("testcallback").equals("callPayRedirction4CreateTicketOrder")){
					callbackset.add(callBack4CreateTicketOrder(request,responsebody));
				}
			}
			if(responsestatus!=0){
				requestinfo=requestinfo.replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&apos;", "'").replaceAll("&quot;","\"").replaceAll("&amp;", "&");
				resopnseinfo=resopnseinfo.replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&apos;", "'").replaceAll("&quot;","\"").replaceAll("&amp;", "&");
				testresult.setRequestInfo(requestinfo);
				testresult.setResponseInfo(resopnseinfo);
				testresult.setCallback(callbackset);
				
			}else{
				testresult.setResult(TestStatus.exception);
				testresult.setComment("communication failure! response status:"+responsestatus);
			}
		}catch(Exception e){
			testresult.setResult(TestStatus.exception);
			testresult.setComment(e.getClass().toString()+": "+e.getMessage());
		}
		return testresult;
	}
	
	private String retrieveString(String content,String folderName, Map request){
		try {
			Map<String,String> evnmap=loadEnv(folderName);
			content=processEnv(evnmap,content);
			content=TemplateUtils.getString(content, request);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return content;
	}
	
	private TestResultItem getSocketTestResultItem(String path, Map request){
		TestResultItem testresult=new TestResultItem();
		try{	
			String requestinfo="";
			String resopnseinfo="";
			Set<String> callbackset=new HashSet<String>();
			ObjectMapper mapper = JsonObjectMapperFactory.getObjectMapper();
			String socketconfig=FileNameUtils.getSocketTestAbsPath(path);
			SocketTarget target = mapper.readValue(new File(socketconfig), SocketTarget.class);
			String head=retrieveString(target.getHead(),path,request);
			String body=retrieveString(target.getBody(),path,request);
			String name=target.getServer().getName();
			String ip=retrieveString(target.getServer().getIp(),path,request);
			String port=retrieveString(target.getServer().getPort(),path,request);
			String protocol=target.getServer().getProtocol();
			String code=target.getCode();
			String service=target.getServiceDescription();
			int dataversion=target.getDatagramVersion();
			boolean indented=target.isIndented();
			requestinfo="[Server]:\n"+name+" "+ip+":"+port+" "+protocol+"\n";	
			requestinfo+="[Service]:\n"+service+"\n";
			requestinfo+="[Request Head]:\n"+head+"\n";
			requestinfo+="[Request Body]:\n"+body+"\n";
			InvokeRequest req = new InvokeRequest();
			ServerItem si=new ServerItem();
			si.setName(name);
			si.setIp(ip);
			si.setPort(port);
			si.setProtocol(protocol);
			req.setServer(si);
			req.setCode(code);
			req.setHead(head);
			req.setBody(body);
			req.setDatagramVersion(dataversion);
			req.setIndented(indented);
			InvokeResponse response=socketOperationUtils.invokeService(req);
			testresult.setDuration(response.getTimespan());
			
			String responsebody=response.getBody();
			logger.info("REQUEST finish with response body:"+responsebody+"\n");
			resopnseinfo="[Response Body]:\n" + responsebody;
			String error=response.getError();
			if(error==null || error.trim().isEmpty()){
				testresult.setRequestInfo(requestinfo);
				testresult.setResponseInfo(resopnseinfo);
				testresult.setCallback(callbackset);
			}else{
				testresult.setResult(TestStatus.exception);
				testresult.setComment("Error: "+error);
			}
		}catch(Exception e){
			testresult.setResult(TestStatus.exception);
			testresult.setComment(e.getClass().toString()+": "+e.getMessage());
		}
		return testresult;
	}
	
	public String callBack(Map<String,String> map,String response){	
		String orderamount=map.get("OrderAmount");
		String callbackhost =map.get("callbackhost");
		String paymentdomain =map.get("paymentdomain");
		String paymenttitle =map.get("paymenttitle");
		String auth=StringUtils.substringBetween(response, "\"auth\":\"", "\"");
		String onum=StringUtils.substringBetween(response, "\"odnum\":\"", "\"");
		return "Ext.getCmp('Base').PayAction(\""+auth+"\",\""+onum+"\",\""+paymenttitle+"\","+orderamount+",\""+callbackhost+"\",\""+paymentdomain+"\",2001)";
	}

	public String callBack4CreateTicketOrder(Map<String,String> map,String response){
		String orderamount=map.get("OrderAmount");
		String callbackhost =map.get("callbackhost");
		String paymentdomain =map.get("paymentdomain");
		String paymenttitle =map.get("paymenttitle");
		String auth=StringUtils.substringBetween(response, "\"auth\":\"", "\"");
		String onum=StringUtils.substringBetween(response, "\"oid\":", ",");
		return "Ext.getCmp('Base').PayAction(\""+auth+"\",\""+onum+"\",\""+paymenttitle+"\","+orderamount+",\""+callbackhost+"\",\""+paymentdomain+"\",7)";
	}
	
	public Map<String,String> loadEnv(String foldername){
		Map<String,String> m=new HashMap<String,String>();
		File f=new File(FileNameUtils.getEnvFilePath(foldername));
		while(true){
			if(f.exists()){
				try {
					String fs=FileUtils.readFileToString(f);
					if(!fs.isEmpty()){
						String[] fa=fs.split("\n");
						for(String s:fa){
							String[] kv=s.split("=");
							if(kv.length==2){
								if(!m.containsKey(kv[0])){
									m.put(kv[0], kv[1]);
								}
							}else
							if(kv.length==1){
								if(!m.containsKey(kv[0])){
									m.put(kv[0], "");
								}
							}else if(kv.length>2){
								if(!m.containsKey(kv[0])){
									m.put(kv[0],StringUtils.substringAfter(s, kv[0]+"="));
								}
							}
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			String parentFileName=f.getParentFile().getName();
			if(!parentFileName.endsWith("-leaf") && !parentFileName.endsWith("-t") && !parentFileName.endsWith("-dir")){
				break;
			}else
				f=new File(FileNameUtils.getEnvFilePath(f.getParentFile().getParent()));
		}
		return m;
	}
	
	public String processEnv(Map<String,String> m,String content){
		String result=content;
		if(content.contains("[[") && content.contains("]]")){
			for(Entry<String, String> e:m.entrySet()){
				result=result.replace("[["+e.getKey()+"]]", e.getValue());
			}
		}
		return result;
	}
	
	public String processVariableInEnv(Map<String,String> m,String variable){
		variable=variable.replace("[[", "").replace("]]", "");
		return m.get(variable);
	}
	
	private List<ServiceBoundDataItem> loadOutputParameter(String testPath){
		return outputParameterService.getOutputParameterDataItems(testPath).getRows();
	}
	
	public String processOutputParameter(String testPath,String responseInfo,String content){
		int pos1=content.indexOf("{{");
		int pos2=content.indexOf("}}");
		while(pos1>=0 && pos1<pos2){
			List<ServiceBoundDataItem> parameters = loadOutputParameter(testPath);
			String name=content.substring(pos1+2, pos2);
			String value="";
			for(ServiceBoundDataItem p : parameters){
				if(name.equalsIgnoreCase(p.getName())){
					String lb=p.getLb();
					String rb=p.getRb();
					String times=p.getTimes();
					value=getParaValueFromResponse(responseInfo,lb,rb,Integer.parseInt(times));
					break;
				}
			}
			content=content.replace("{{"+name+"}}", value);
			pos1=content.indexOf("{{");
			pos2=content.indexOf("}}");
		}
		return content;
	}
	
	public String processOutputParameter(String testPath,String content){
		int pos1=content.indexOf("{{");
		int pos2=content.indexOf("}}");
		String responseinfo="";
		if(pos2>pos1 && pos1>0){
			Map request = getRequestParameterMap(testPath);
			TestResultItem item = getTestResultItem(testPath,request);
			if(!item.getResult().equals(TestStatus.exception)){
				responseinfo=item.getResponseInfo();
			}
		}
		while(pos1>=0 && pos1<pos2){
			List<ServiceBoundDataItem> parameters = loadOutputParameter(testPath);
			String name=content.substring(pos1+2, pos2);
			String value="";
			for(ServiceBoundDataItem p : parameters){
				if(name.equalsIgnoreCase(p.getName())){
					String lb=p.getLb();
					String rb=p.getRb();
					String times=p.getTimes();
					if(!responseinfo.isEmpty())
						value=getParaValueFromResponse(responseinfo,lb,rb,Integer.parseInt(times));
					break;
				}
			}
			content=content.replace("{{"+name+"}}", value);
			pos1=content.indexOf("{{");
			pos2=content.indexOf("}}");
		}
		return content;
	}
	
	public static void main(String args[]){
		String key="";
		StringUtils.substringAfter("qqwww123","q");
		String exp="JSON.parse(\"{\\\\\"id\\\\\":1}\").id==1";
		String filename=new Date().getTime()+".js";
		File f=new File(filename);
		try{
			f.createNewFile();
			FileUtils.writeStringToFile(f, "console.log(eval(\""+exp.replace("\"", "\\\"")+"\"))");
			Runtime runtime = Runtime.getRuntime();
			Process p = runtime.exec("cmd /k node "+f.getAbsolutePath());
			InputStream is = p.getInputStream();
			OutputStream os = p.getOutputStream();
			os.close();
			key = IOUtils.toString(is,"gbk");
			key=StringUtils.substringBetween(key, "", "\n\r");
		}catch(Exception e){
			key=e.getMessage();
			
		}finally{
			if(f.exists())
				f.delete();
		}
		System.out.println(key);
		
	}
	
	
}