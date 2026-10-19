package cn.guat.smartpark.service;
import com.fasterxml.jackson.core.type.TypeReference;

import cn.guat.smartpark.exception.PlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.client.RestTemplate;
import javax.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;

@Service
public class IntegrationService {
 private final JdbcTemplate db;private final MonitoringService monitor;private final ObjectMapper json;private final Environment env;
 @org.springframework.beans.factory.annotation.Autowired private ReadableIdentifierService readableIds;
 private volatile Map<String,Object> lastCollection=Collections.emptyMap();
 private final DemoTemperaturePolicy demoPolicy;
 public IntegrationService(JdbcTemplate db,MonitoringService monitor,ObjectMapper json,Environment env){this.db=db;this.monitor=monitor;this.json=json;this.env=env;this.demoPolicy=new DemoTemperaturePolicy(db,env);}
 @PostConstruct public void schema(){
  db.execute("CREATE TABLE IF NOT EXISTS sp_ingest_batch(room_id BIGINT NOT NULL,batch_id VARCHAR(100) NOT NULL,digest VARCHAR(64) NOT NULL,created_at TIMESTAMP(3) NOT NULL,PRIMARY KEY(room_id,batch_id))");
  if(readableIds!=null)readableIds.batchSchema();
 }
 private PlatformException bad(int status,String message){return new PlatformException(status,"INTEGRATION_ERROR",message);}
 public String text(Map<String,Object> b,String key){Object v=b.get(key);if(!(v instanceof String)||((String)v).trim().isEmpty()||((String)v).length()>1000)throw bad(400,"字段无效："+key);return ((String)v).trim();}
 private double number(Map<String,Object>b,String key){Object v=b.get(key);if(!(v instanceof Number)||!Double.isFinite(((Number)v).doubleValue()))throw bad(400,"数值无效："+key);return ((Number)v).doubleValue();}
 private Instant time(Map<String,Object>b,String key){try{Instant t=Instant.parse(text(b,key));if(t.isAfter(Instant.now().plusSeconds(60)))throw bad(400,"采集时间不能在未来");return t;}catch(java.time.format.DateTimeParseException e){throw bad(400,"时间必须为 ISO 8601："+key);}}
 private Map<String,Object> record(String room,String kind,String id){return monitor.records(room,kind).stream().filter(v->id.equals(String.valueOf(v.get("id")))).findFirst().orElseThrow(()->bad(404,"记录不存在"));}
 public void entity(String room,String type,String id){monitor.room(room);boolean exists=false;
  if(type.equals("ROOM"))exists=room.equals(id);
  if(type.equals("CABINET"))exists=!db.queryForList("SELECT c.id FROM sp_cabinet c JOIN sp_zone z ON z.id=c.zone_id WHERE c.id=? AND z.room_id=?",id,room).isEmpty();
  if(type.equals("DEVICE"))exists=!db.queryForList("SELECT d.id FROM sp_device d JOIN sp_cabinet c ON c.id=d.cabinet_id JOIN sp_zone z ON z.id=c.zone_id WHERE d.id=? AND z.room_id=?",id,room).isEmpty();
  if(!exists)throw bad(400,"对象不属于当前机房");
 }
 public List<Map<String,Object>> rules(String room){return monitor.records(room,"RULE");}
 @Transactional(isolation=Isolation.READ_COMMITTED) public Map<String,Object> saveRule(String room,String id,Map<String,Object>b){
  monitor.lockRoom(room);String type=text(b,"entityType"),entity=text(b,"entityId");entity(room,type,entity);
  text(b,"metric");text(b,"title");if(!(b.get("unit") instanceof String))throw bad(400,"缺少指标单位");
  double min=number(b,"min"),max=number(b,"max");if(min>max||number(b,"maxAgeSeconds")<=0||number(b,"normalSeconds")<0||number(b,"maxGapSeconds")<=0)throw bad(400,"告警阈值或恢复时间无效");
  for(String key:Arrays.asList("maxAgeSeconds","normalSeconds","maxGapSeconds")){double value=number(b,key);if(value!=Math.floor(value)||value>Integer.MAX_VALUE)throw bad(400,"恢复时间必须为整数秒");}
  if(!Arrays.asList("critical","warning","normal","info").contains(b.get("severity")))throw bad(400,"告警等级无效");
  if(!(b.get("enabled") instanceof Boolean))throw bad(400,"enabled 必须为布尔值");
  demoPolicy.validateRule(room,id,b);String origin=DemoTemperaturePolicy.origin(b);
  for(Map<String,Object> previous:rules(room))if(id.equals(previous.get("id"))&&!origin.equals(DemoTemperaturePolicy.origin(previous)))throw bad(409,"不能覆盖另一来源的规则");
  Map<String,Object> rule=new LinkedHashMap<>(b);rule.put("recordOrigin",origin);rule.put("id",id);rule.put("roomId",room);rule.put("updatedAt",Instant.now().toString());monitor.saveRecord(room,"RULE",id,rule);return rule;
 }
 @Transactional(isolation=Isolation.READ_COMMITTED) public void deleteRule(String room,String id){monitor.lockRoom(room);record(room,"RULE",id);db.update("DELETE FROM sp_monitor_record WHERE room_id=? AND kind='RULE' AND id=?",room,id);}
 @SuppressWarnings("unchecked") private List<Map<String,Object>> array(Map<String,Object>b,String key){Object value=b.getOrDefault(key,Collections.emptyList());if(!(value instanceof List)||((List<?>)value).size()>2000)throw bad(400,"批量数据格式无效或超过 2000 条");for(Object item:(List<?>)value)if(!(item instanceof Map))throw bad(400,"批量数据项必须为对象");return (List<Map<String,Object>>)value;}
 private void reading(String room,Map<String,Object>r){r=UnifiedDataSource.normalize(new LinkedHashMap<>(r));String type=text(r,"entityType"),id=text(r,"entityId");entity(room,type,id);String metric=text(r,"metric"),source=text(r,"source");if(metric.length()>64||source.length()>100)throw bad(400,"指标或来源过长");double value=number(r,"value");String quality=text(r,"quality"),origin=text(r,"recordOrigin");if(!Arrays.asList("GOOD","BAD","MISSING").contains(quality)||!Arrays.asList("REAL","DEMO").contains(origin)||!(r.get("unit") instanceof String)||r.get("unit").toString().length()>24)throw bad(400,"质量、来源类型或单位无效");db.update("INSERT INTO sp_reading(room_id,entity_type,entity_id,metric,value,unit,observed_at,quality,source,record_origin) VALUES(?,?,?,?,?,?,?,?,?,?)",room,type,id,metric,value,r.get("unit"),Timestamp.from(time(r,"observedAt")),quality,source,origin);}
 private void energy(String room,Map<String,Object>r){r=UnifiedDataSource.normalize(new LinkedHashMap<>(r));String category=text(r,"category");if(!Arrays.asList("机柜","空调","照明","监控","其他").contains(category)||number(r,"kwh")<0)throw bad(400,"分类或增量用电量无效");if(category.equals("机柜"))entity(room,"CABINET",String.valueOf(r.get("cabinetId")));else if(r.get("cabinetId")!=null)throw bad(400,"非机柜分类不能填写 cabinetId");if(!Arrays.asList("GOOD","BAD","MISSING").contains(r.get("quality"))||!Arrays.asList("REAL","DEMO").contains(r.get("recordOrigin")))throw bad(400,"质量或来源类型无效");String source=text(r,"source");if(source.length()>100)throw bad(400,"来源过长");db.update("INSERT INTO sp_energy_sample(room_id,category,cabinet_id,kwh,observed_at,quality,record_origin,source) VALUES(?,?,?,?,?,?,?,?)",room,category,r.get("cabinetId"),number(r,"kwh"),Timestamp.from(time(r,"observedAt")),r.get("quality"),r.get("recordOrigin"),source);}
 @Transactional(isolation=Isolation.READ_COMMITTED) public Map<String,Object> ingest(String room,Map<String,Object>b){return ingestLocked(room,b,false);}
 @Transactional(isolation=Isolation.READ_COMMITTED) Map<String,Object> ingestDemo(String room,Map<String,Object>b){return ingestLocked(room,b,true);}
 private Map<String,Object> ingestLocked(String room,Map<String,Object>b,boolean trustedDemo){
  monitor.lockRoom(room);String batch=text(b,"batchId");if(batch.length()>100)throw bad(400,"批次编号过长");
  if(!trustedDemo&&batch.startsWith("demo-temperature:"))throw bad(403,"演示批次编号由受控温度接口保留");String digest;
  try{byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(b));StringBuilder hex=new StringBuilder();for(byte v:bytes)hex.append(String.format("%02x",v));digest=hex.toString();}catch(Exception e){throw new IllegalStateException(e);}
  try{db.update("INSERT INTO sp_ingest_batch(room_id,batch_id,digest,created_at) VALUES(?,?,?,CURRENT_TIMESTAMP(3))",room,batch,digest);}catch(org.springframework.dao.DuplicateKeyException e){String saved=db.queryForObject("SELECT digest FROM sp_ingest_batch WHERE room_id=? AND batch_id=?",String.class,room,batch);if(!digest.equals(saved))throw bad(409,"同一批次编号的内容不同");return object("duplicate",true,"batchId",batch,"batchCode",readableIds==null?null:readableIds.batchCode(room,batch,Instant.now()));}
  List<Map<String,Object>> readings=array(b,"readings"),energy=array(b,"energy"),records=array(b,"records");
  if(!trustedDemo)for(Map<String,Object> entry:readings)if(DemoTemperaturePolicy.SOURCE.equals(entry.get("source")))throw bad(403,"受控模拟来源只能通过演示温度接口上报");
  for(Map<String,Object> entry:records)if(String.valueOf(entry.get("id")).startsWith("demo-temperature:"))throw bad(403,"演示审计编号保留");
  if(readings.isEmpty()&&energy.isEmpty()&&records.isEmpty())throw bad(400,"采集批次不能为空");
  for(Map<String,Object>r:readings)reading(room,r);for(Map<String,Object>r:energy)energy(room,r);
  for(Map<String,Object>rawRecord:records){Map<String,Object>r=UnifiedDataSource.normalize(new LinkedHashMap<>(rawRecord));String kind=text(r,"kind"),id=text(r,"id");if(!Arrays.asList("FACILITY","EVENT","NODE","LINK","LOG").contains(kind)||id.length()>100)throw bad(400,"采集记录类型或编号无效");time(r,"observedAt");text(r,"source");if(!Arrays.asList("REAL","DEMO").contains(r.get("recordOrigin")))throw bad(400,"记录来源类型无效");if(r.get("cabinetId")!=null)entity(room,"CABINET",r.get("cabinetId").toString());if(r.get("deviceId")!=null)entity(room,"DEVICE",r.get("deviceId").toString());monitor.saveRecord(room,kind,id,r);}
  if(!energy.isEmpty())refreshEnergy(room);
  Set<String> origins=new HashSet<>();for(List<Map<String,Object>> entries:Arrays.asList(readings,energy,records))for(Map<String,Object> entry:entries)origins.add(UnifiedDataSource.origin(entry.get("recordOrigin")));
  int changed=0;for(String origin:origins)changed+=evaluate(room,origin);
  return object("duplicate",false,"batchId",batch,"batchCode",readableIds==null?null:readableIds.batchCode(room,batch,Instant.now()),"readings",readings.size(),"energy",energy.size(),"records",records.size(),"alarms",changed);
 }
 private void refreshEnergy(String room){LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));for(String metric:Arrays.asList("todayKwh","monthKwh")){Instant start=(metric.equals("todayKwh")?today:today.withDayOfMonth(1)).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),end=today.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();List<Map<String,Object>> rows=db.queryForList("SELECT category,SUM(kwh) value FROM sp_energy_sample WHERE room_id=? AND observed_at>=? AND observed_at<? AND quality='GOOD' AND record_origin='REAL' GROUP BY category",room,Timestamp.from(start),Timestamp.from(end));if(rows.size()!=5)continue;double total=rows.stream().mapToDouble(v->((Number)v.get("value")).doubleValue()).sum();reading(room,object("entityType","ROOM","entityId",room,"metric",metric,"value",total,"unit","kWh","observedAt",Instant.now().toString(),"quality","GOOD","recordOrigin","REAL","source","ENERGY_AGGREGATE"));}}
 @Transactional(isolation=Isolation.READ_COMMITTED) public int evaluate(String room){return evaluate(room,"REAL");}
 @Transactional(isolation=Isolation.READ_COMMITTED) public int evaluate(String room,String origin){
  if(!Arrays.asList("REAL","DEMO").contains(origin))throw bad(400,"规则评估来源无效");origin="REAL";monitor.lockRoom(room);int changed=0;
  for(Map<String,Object>rule:rules(room)){
   if(!Boolean.TRUE.equals(rule.get("enabled"))||!origin.equals(DemoTemperaturePolicy.origin(rule)))continue;
   if(DemoTemperaturePolicy.RULE_ID.equals(rule.get("id")))demoPolicy.validateRule(room,String.valueOf(rule.get("id")),rule);
   Map<String,Object>r=monitor.reading(room,rule.get("entityType").toString(),rule.get("entityId").toString(),rule.get("metric").toString(),rule.get("unit").toString());
   if(r.get("value")==null||!Objects.equals(r.get("unit"),rule.get("unit"))||Duration.between(Instant.parse(r.get("observedAt").toString()),Instant.now()).getSeconds()>((Number)rule.get("maxAgeSeconds")).longValue())continue;
   double v=((Number)r.get("value")).doubleValue();List<Map<String,Object>> active=new ArrayList<>();for(Map<String,Object>a:monitor.records(room,"ALARM"))if(Objects.equals(a.get("ruleId"),rule.get("id"))&&origin.equals(a.get("recordOrigin"))&&Objects.equals(rule.get("entityType"),a.get("entityType"))&&Objects.equals(rule.get("entityId"),a.get("entityId"))&&Objects.equals(rule.get("metric"),a.get("metric"))&&!"已恢复".equals(a.get("status")))active.add(a);
   if(v<((Number)rule.get("min")).doubleValue()||v>((Number)rule.get("max")).doubleValue()){
    Map<String,Object>a=active.isEmpty()?object("id",UUID.randomUUID().toString(),"createdAt",r.get("observedAt"),"status","未处理"):active.get(0);
    a.putAll(object("ruleId",rule.get("id"),"title",rule.get("title"),"severity",rule.get("severity"),"entityType",rule.get("entityType"),"entityId",rule.get("entityId"),"metric",rule.get("metric"),"value",v,"threshold",rule.get("min")+" ~ "+rule.get("max"),"advice",rule.getOrDefault("advice","检查设备及历史指标"),"rule",new LinkedHashMap<>(rule),"observedAt",r.get("observedAt"),"recordOrigin",origin,"source",r.get("source")));
    if("CABINET".equals(rule.get("entityType")))a.put("cabinetId",rule.get("entityId"));if("DEVICE".equals(rule.get("entityType"))){a.put("deviceId",rule.get("entityId"));a.put("cabinetId",String.valueOf(db.queryForObject("SELECT cabinet_id FROM sp_device WHERE id=?",Long.class,rule.get("entityId"))));}
    monitor.saveRecord(room,"ALARM",a.get("id").toString(),a);changed++;
   }else {
    for(Map<String,Object>a:active){
     // Associated work orders must still pass their existing submission/retest workflow.
     int orders=db.queryForObject("SELECT COUNT(*) FROM console_order WHERE status<>'已完成' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.roomId'))=? AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.alarmEvent'))=?",Integer.class,room,a.get("id"));
     if(orders>0)continue;
     Map<String,Object>check=monitor.retest(object("roomId",room,"alarmEvent",a.get("id"),"recordOrigin",origin,"submittedAt",a.get("createdAt")));
     if(Boolean.TRUE.equals(check.get("passed"))){a.put("status","已恢复");a.put("recoveredAt",Instant.now().toString());a.put("recoveryEvidence",check);monitor.saveRecord(room,"ALARM",a.get("id").toString(),a);changed++;}
    }
   }
  }return changed;
 }
 public List<Map<String,Object>> history(String room,String type,String id,String metric,String from,String to){entity(room,type,id);Instant a,b;try{a=Instant.parse(from);b=Instant.parse(to);}catch(Exception e){throw bad(400,"历史时间格式无效");}if(!a.isBefore(b)||Duration.between(a,b).toDays()>31)throw bad(400,"历史查询范围必须在 31 天内");List<Map<String,Object>>rows=db.queryForList("SELECT metric,value,unit,observed_at observedAt,quality,record_origin recordOrigin,source FROM sp_reading WHERE room_id=? AND entity_type=? AND entity_id=? AND metric=? AND observed_at>=? AND observed_at<? ORDER BY observed_at,id LIMIT 2000",room,type,id,metric,Timestamp.from(a),Timestamp.from(b));for(Map<String,Object>r:rows)r.put("observedAt",((Timestamp)r.get("observedAt")).toInstant().toString());return rows;}
 private RestTemplate http(){SimpleClientHttpRequestFactory f=new SimpleClientHttpRequestFactory();f.setConnectTimeout(5000);f.setReadTimeout(15000);return new RestTemplate(f);}
 private String property(String key){return env.getProperty(key,"").trim();}
 private String endpoint(String url){try{java.net.URI u=java.net.URI.create(url);if(!Arrays.asList("http","https").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null)throw new IllegalArgumentException();return url;}catch(Exception e){throw bad(400,"服务地址必须为 HTTP(S)，凭据使用请求头配置");}}
 private Map<String,Object> post(String url,String token,Object body){HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);if(!token.isEmpty())headers.setBearerAuth(token);try{Map<String,Object>result=http().postForObject(endpoint(url),new HttpEntity<>(body,headers),Map.class);if(result==null)throw bad(502,"外部服务返回空结果");return result;}catch(org.springframework.web.client.RestClientException e){throw bad(502,"外部服务连接或执行失败");}}
 public Map<String,Object> capabilities(){return object("agentEnabled",true,"modelConfigured",!property("integration.model.url").isEmpty()&&!property("integration.model.name").isEmpty(),"videoGatewayConfigured",!property("integration.video.url").isEmpty(),"controlGatewayConfigured",!property("integration.control.url").isEmpty());}
 /** Native tool calls. Only the backend supplies tools and executes their arguments. */
 public Map<String,Object> modelTools(List<Map<String,Object>> messages,List<Map<String,Object>> tools){
  String url=property("integration.model.url"),name=property("integration.model.name");
  if(url.isEmpty()||name.isEmpty())throw bad(503,"未配置模型服务");
  Map<String,Object> response=post(url,property("integration.model.key"),object("model",name,"messages",messages,"tools",tools,"thinking",object("type","disabled")));
  try{
   List<?> choices=(List<?>)response.get("choices");Map<?,?> upstream=(Map<?,?>)((Map<?,?>)choices.get(0)).get("message");
   Map<String,Object> message=object("role","assistant","content",upstream.get("content"));
   if(upstream.get("reasoning_content") instanceof String)message.put("reasoning_content",upstream.get("reasoning_content"));
   if(upstream.get("tool_calls") instanceof List&&!((List<?>)upstream.get("tool_calls")).isEmpty())message.put("tool_calls",upstream.get("tool_calls"));
   else if(!(upstream.get("content") instanceof String)||upstream.get("content").toString().trim().isEmpty())throw new IllegalArgumentException();
   return message;
  }catch(Exception e){throw bad(502,"模型工具响应格式无效");}
 }
 /** Consume upstream SSE as it arrives; tool argument fragments never reach the UI. */
 public Map<String,Object> modelToolsStream(List<Map<String,Object>> messages,List<Map<String,Object>> tools,Consumer<String> onText){
  String url=property("integration.model.url"),name=property("integration.model.name");
  if(url.isEmpty()||name.isEmpty())throw bad(503,"未配置模型服务");
  Object body=object("model",name,"messages",messages,"tools",tools,"stream",true,"thinking",object("type","disabled"));
  try{return http().execute(endpoint(url),HttpMethod.POST,request->{
   request.getHeaders().setContentType(MediaType.APPLICATION_JSON);request.getHeaders().setAccept(Arrays.asList(MediaType.TEXT_EVENT_STREAM));
   String key=property("integration.model.key");if(!key.isEmpty())request.getHeaders().setBearerAuth(key);
   json.writeValue(request.getBody(),body);
  },response->{
   StreamAnswer answer=new StreamAnswer(onText);StringBuilder data=new StringBuilder();
   try(BufferedReader reader=new BufferedReader(new InputStreamReader(response.getBody(),StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null){
    if(line.isEmpty()){if(data.length()>0){answer.accept(data.toString());data.setLength(0);if(answer.done)break;}}
    else if(line.startsWith("data:")){if(data.length()>0)data.append('\n');data.append(line.substring(5).replaceFirst("^ ",""));}
   }if(data.length()>0)answer.accept(data.toString());}
   return answer.result();
  });}catch(org.springframework.web.client.RestClientException e){throw bad(502,"模型流式连接或读取失败");}
 }
 private class StreamAnswer {
  final Consumer<String> onText;final StringBuilder content=new StringBuilder(),reasoning=new StringBuilder();final SortedMap<Integer,Map<String,Object>>calls=new TreeMap<>();
  boolean done;String finish="";
  StreamAnswer(Consumer<String>onText){this.onText=onText;}
  void accept(String data)throws IOException{
   if("[DONE]".equals(data.trim())){done=true;return;}
   Map<String,Object>chunk=json.readValue(data,new TypeReference<Map<String,Object>>(){});if(chunk.containsKey("error"))throw new IOException("Upstream stream error");
   Object choices=chunk.get("choices");if(!(choices instanceof List))throw new IOException("Missing choices");if(((List<?>)choices).isEmpty())return;
   Map<?,?>choice=(Map<?,?>)((List<?>)choices).get(0);if(choice.get("index") instanceof Number&&((Number)choice.get("index")).intValue()!=0)return;
   if(choice.get("finish_reason") instanceof String)finish=choice.get("finish_reason").toString();
   if(!(choice.get("delta") instanceof Map))return;Map<?,?>delta=(Map<?,?>)choice.get("delta");
   if(delta.get("content") instanceof String){String part=(String)delta.get("content");content.append(part);if(!part.isEmpty())onText.accept(part);}
   if(delta.get("reasoning_content") instanceof String)reasoning.append(delta.get("reasoning_content"));
   if(delta.get("tool_calls") instanceof List)for(Object raw:(List<?>)delta.get("tool_calls")){
    Map<?,?>fragment=(Map<?,?>)raw;if(!(fragment.get("index") instanceof Number))throw new IOException("Missing tool index");int index=((Number)fragment.get("index")).intValue();if(index<0||index>=128)throw new IOException("Invalid tool index");
    Map<String,Object>call=calls.computeIfAbsent(index,k->object("id","","type","function","function",object("name","","arguments","")));
    append(call,"id",fragment.get("id"));if(fragment.get("function") instanceof Map){Map<?,?>fn=(Map<?,?>)fragment.get("function");Map<String,Object>target=(Map<String,Object>)call.get("function");append(target,"name",fn.get("name"));append(target,"arguments",fn.get("arguments"));}
   }
  }
  void append(Map<String,Object>map,String key,Object part){if(part instanceof String)map.put(key,map.get(key).toString()+part);}
  Map<String,Object>result(){
   if(!done||!("stop".equals(finish)||"tool_calls".equals(finish)))throw bad(502,"模型输出中断或未正常结束");
   if(calls.isEmpty()&&content.toString().trim().isEmpty())throw bad(502,"模型未返回有效内容");
   Map<String,Object>out=object("role","assistant","content",content.length()==0?null:content.toString());if(!calls.isEmpty())out.put("tool_calls",new ArrayList<>(calls.values()));if(reasoning.length()>0)out.put("reasoning_content",reasoning.toString());return out;
  }
 }
 public Map<String,Object> collectorStatus(){return object("configured",!property("integration.collector.url").isEmpty(),"lastRun",lastCollection);}
 @Transactional public Map<String,Object> collect(){String url=property("integration.collector.url");if(url.isEmpty())throw bad(503,"未配置自动采集服务");try{Map<String,Object>batch=post(url,property("integration.collector.key"),object("requestedAt",Instant.now().toString()));String room=text(batch,"roomId");Map<String,Object>result=ingest(room,batch);lastCollection=object("success",true,"checkedAt",Instant.now().toString(),"result",result);return result;}catch(RuntimeException e){lastCollection=object("success",false,"checkedAt",Instant.now().toString(),"message","采集连接或数据校验失败");throw e;}}
 @Transactional public Map<String,Object> videoConfig(String room,String id,Map<String,Object>b){Map<String,Object>camera=record(room,"FACILITY",id);if(!"CAMERA".equals(camera.get("type")))throw bad(400,"对象不是摄像头");String mode=text(b,"mode");if(!Arrays.asList("DIRECT","GATEWAY").contains(mode))throw bad(400,"视频模式无效");if(mode.equals("DIRECT"))endpoint(text(b,"url"));if(mode.equals("GATEWAY"))text(b,"channelId");if(!Arrays.asList("MP4","HLS").contains(b.get("protocol")))throw bad(400,"播放协议无效");Map<String,Object>config=new LinkedHashMap<>(b);config.put("id",id);monitor.saveRecord(room,"VIDEO",id,config);return config;}
 public Map<String,Object> video(String room,String id){record(room,"FACILITY",id);Map<String,Object>config=record(room,"VIDEO",id);if("DIRECT".equals(config.get("mode")))return object("url",config.get("url"),"protocol",config.get("protocol"),"live",true);String url=property("integration.video.url");if(url.isEmpty())throw bad(503,"未配置视频网关");Map<String,Object>result=post(url,property("integration.video.key"),object("roomId",room,"cameraId",id,"channelId",config.get("channelId")));endpoint(text(result,"url"));result.put("protocol",config.get("protocol"));result.put("live",true);return result;}
 @Transactional public Map<String,Object> command(String room,String cabinet,Map<String,Object>b,Map<String,Object>actor){entity(room,"CABINET",cabinet);String key=text(b,"key");if(!Arrays.asList("lighting","fans","locked","power","doorOpen").contains(key)||!(b.get("value") instanceof Boolean))throw bad(400,"设备命令无效");String url=property("integration.control.url");if(url.isEmpty())throw bad(503,"未配置设备控制网关");String id=UUID.randomUUID().toString();Map<String,Object>command=object("id",id,"roomId",room,"cabinetId",cabinet,"key",key,"value",b.get("value"),"status","PENDING","accountId",actor.get("id"),"createdAt",Instant.now().toString());
  try{Map<String,Object>result=post(url,property("integration.control.key"),command);if(!Boolean.TRUE.equals(result.get("success"))||!Objects.equals(result.get("commandId"),id)||!(result.get("actualValue") instanceof Boolean))throw bad(502,"设备未返回有效执行结果");command.put("actualValue",result.get("actualValue"));command.put("status",Objects.equals(b.get("value"),result.get("actualValue"))?"SUCCEEDED":"FAILED");}catch(PlatformException e){command.put("status","FAILED");command.put("message",e.getMessage());}
  command.put("completedAt",Instant.now().toString());monitor.saveRecord(room,"COMMAND",id,command);
  if("SUCCEEDED".equals(command.get("status"))){if(!key.equals("doorOpen")){monitor.control(room,cabinet,object("key",key,"value",command.get("actualValue")),actor);}reading(room,object("entityType","CABINET","entityId",cabinet,"metric",key,"value",Boolean.TRUE.equals(command.get("actualValue"))?1:0,"unit","","observedAt",Instant.now().toString(),"quality","GOOD","recordOrigin","REAL","source","CONTROL_GATEWAY"));}return command;
 }
 public List<Map<String,Object>> commands(String room,String cabinet){entity(room,"CABINET",cabinet);List<Map<String,Object>>result=new ArrayList<>();for(Map<String,Object>c:monitor.records(room,"COMMAND"))if(cabinet.equals(c.get("cabinetId")))result.add(c);return result;}
}
