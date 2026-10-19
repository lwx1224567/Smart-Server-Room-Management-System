package cn.guat.smartpark.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;
import static cn.guat.smartpark.service.DemoTemperaturePolicy.*;

@Service
public class DemoMonitoringService {
 @org.springframework.beans.factory.annotation.Autowired(required=false) private java.time.Clock clock=java.time.Clock.systemUTC();
 private final JdbcTemplate db;private final MonitoringService monitor;private final IntegrationService integration;private final DemoTemperaturePolicy policy;private final ConsoleService console;private final ObjectMapper json;
 public DemoMonitoringService(JdbcTemplate db,MonitoringService monitor,IntegrationService integration,DemoTemperaturePolicy policy,ConsoleService console,ObjectMapper json){this.db=db;this.monitor=monitor;this.integration=integration;this.policy=policy;this.console=console;this.json=json;}
 @Transactional(isolation=Isolation.READ_COMMITTED)
 public Map<String,Object> submit(String room,Map<String,Object> body,Map<String,Object> actor){
  console.admin(actor);
  if(!body.keySet().equals(new HashSet<>(Arrays.asList("cabinetId","value","requestId"))))throw bad(400,"仅接受 cabinetId、value、requestId");
  Object raw=body.get("value");if(!(raw instanceof Number)||!Double.isFinite(((Number)raw).doubleValue())||((Number)raw).doubleValue()<0||((Number)raw).doubleValue()>100)throw bad(400,"演示温度必须为 0～100℃ 的数值");
  Object request=body.get("requestId");if(!(request instanceof String)||!request.toString().matches("[A-Za-z0-9_-]{1,64}"))throw bad(400,"requestId 必须为 1～64 位字母数字、下划线或短横线");
  monitor.lockRoom(room);String cabinet=policy.cabinet(room);
  if(!cabinet.equals(body.get("cabinetId")))throw bad(400,"只能上报该机房 A-01，cabinetId 必须为数据库 ID 字符串");
  String auditId="demo-temperature:"+request;double value=((Number)raw).doubleValue();
  List<String> saved=db.queryForList("SELECT payload FROM sp_monitor_record WHERE room_id=? AND kind='LOG' AND id=? FOR UPDATE",String.class,room,auditId);
  if(!saved.isEmpty()){
   try{Map<String,Object> audit=json.readValue(saved.get(0),new TypeReference<Map<String,Object>>(){});
    if(!SOURCE.equals(audit.get("source"))||!cabinet.equals(audit.get("cabinetId"))||((Number)audit.get("value")).doubleValue()!=value||!actor.get("id").toString().equals(String.valueOf(audit.get("accountId"))))throw bad(409,"同 requestId 的内容或操作者不同");
    Map<String,Object> response=UnifiedDataSource.normalize(new LinkedHashMap<>((Map<String,Object>)audit.get("response")));response.put("duplicate",true);return response;
   }catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("演示审计格式无效",e);}
  }
  Map<String,Object> rule=monitor.records(room,"RULE").stream().filter(r->RULE_ID.equals(r.get("id"))).findFirst().orElseThrow(()->bad(409,"请先由管理员配置 A-01 温度规则"));
  policy.validateRule(room,RULE_ID,rule);if(!Boolean.TRUE.equals(rule.get("enabled")))throw bad(409,"演示规则未启用");
  String at=Instant.now(clock).truncatedTo(ChronoUnit.MILLIS).toString();
  Map<String,Object> point=object("entityType","CABINET","entityId",cabinet,"metric","temperature","unit",UNIT,"value",value,"observedAt",at,"quality","GOOD","recordOrigin","REAL","source",SOURCE);
  String batchId="demo-temperature:"+request;
  Map<String,Object> ingested=integration.ingestDemo(room,object("batchId",batchId,"readings",Arrays.asList(point)));
  if(Boolean.TRUE.equals(ingested.get("duplicate")))throw bad(409,"批次已存在但缺少对应演示审计，不能重新生成采集时间");
  Long readingId=db.queryForObject("SELECT id FROM sp_reading WHERE room_id=? AND entity_type='CABINET' AND entity_id=? AND metric='temperature' AND source=? AND record_origin IN ('REAL','DEMO') ORDER BY id DESC LIMIT 1",Long.class,room,cabinet,SOURCE);
  List<Map<String,Object>> alarms=new ArrayList<>();for(Map<String,Object>a:monitor.records(room,"ALARM"))if(managed(a)&&"REAL".equals(a.get("recordOrigin"))&&!"已恢复".equals(a.get("status")))alarms.add(a);
  Map<String,Object> response=object("duplicate",false,"requestId",request,"roomId",room,"cabinetId",cabinet,"readingId",readingId.toString(),"value",value,"unit",UNIT,"observedAt",at,"quality","GOOD","recordOrigin","REAL","source",SOURCE,"ruleId",RULE_ID,"alarmIds",alarms.stream().map(a->a.get("id")).collect(java.util.stream.Collectors.toList()));
  if(ingested.get("batchCode")!=null)response.put("batchCode",ingested.get("batchCode"));
  monitor.saveRecord(room,"LOG",auditId,object("id",auditId,"action","管理员模拟上报温度","requestId",request,"cabinetId",cabinet,"value",value,"observedAt",at,"createdAt",at,"recordOrigin","REAL","source",SOURCE,"accountId",actor.get("id"),"user",actor.get("displayName"),"response",response));return response;
 }
}
