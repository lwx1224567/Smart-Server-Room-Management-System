package cn.guat.smartpark;
import cn.guat.smartpark.service.ConsoleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ConsoleIntegrationTest {
 @Autowired ConsoleService service; @Autowired JdbcTemplate db; @Autowired MockMvc mvc; @Autowired ObjectMapper json;
 @Autowired cn.guat.smartpark.service.MonitoringService monitoring;
 Map<String,Object> admin,maint,other;String adminToken,maintToken,otherToken;
 private final String password="ConsoleTest123!";
 private Map<String,Object> account(String role){String name="console_test_"+UUID.randomUUID().toString().substring(0,8);service.savePerson(object("role","ADMIN"),object("userName",name,"displayName",name,"password",password,"enabled",true),null);db.update("UPDATE console_account SET role=? WHERE user_name=?",role,name);return service.login(object("userName",name,"password",password));}
 @BeforeEach void accounts(){Map<String,Object>a=account("ADMIN"),m=account("MAINTAINER"),o=account("MAINTAINER");adminToken="Bearer "+a.get("token");maintToken="Bearer "+m.get("token");otherToken="Bearer "+o.get("token");admin=(Map)a.get("user");maint=(Map)m.get("user");other=(Map)o.get("user");}
 @Test void versionedContractsAndResourcePermissions()throws Exception{
  mvc.perform(get("/api/v1/auth/me").header("Authorization",maintToken)).andExpect(status().isOk()).andExpect(jsonPath("$.requestId").isNotEmpty()).andExpect(jsonPath("$.serverTime").isNotEmpty()).andExpect(jsonPath("$.error").isEmpty());
  mvc.perform(get("/api/v1/users").header("Authorization",maintToken)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.type").isNotEmpty()).andExpect(jsonPath("$.requestId").isNotEmpty());
  mvc.perform(get("/api/v1/work-orders").header("Authorization",maintToken)).andExpect(status().isOk());
  mvc.perform(get("/api/v1/notifications").header("Authorization",maintToken)).andExpect(status().isOk());
  mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.type").value("INVALID_ARGUMENT"));
  mvc.perform(get("/api/console/me").header("Authorization",adminToken)).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/inventory/rooms").header("Authorization",adminToken)).andExpect(status().isNotFound());
 }
 @Test void managementDtoAndFocusedMonitoringMatchAggregate()throws Exception{
  String room=String.valueOf(db.queryForObject("SELECT id FROM sp_room ORDER BY id LIMIT 1",Long.class));
  mvc.perform(get("/api/v1/admin/rooms").header("Authorization",adminToken).param("pageSize","1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].parkId").exists()).andExpect(jsonPath("$.data.items[0].park_id").doesNotExist()).andExpect(jsonPath("$.data.pageSize").value(1));
  mvc.perform(get("/api/v1/admin/rooms").header("Authorization",maintToken)).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/admin/rooms").header("Authorization",adminToken).param("page","0")).andExpect(status().isBadRequest());
  Map<String,Object> snapshot=monitoring.snapshot(room);
  System.out.println("API payload measurement (UTF-8 data only): snapshot="+json.writeValueAsBytes(snapshot).length+", alarms="+json.writeValueAsBytes(snapshot.get("alarms")).length+" bytes");
  String[] routes={"readings","alarms","facilities","operation-logs"};String[] keys={"readings","alarms","facilities","logs"};
  for(int i=0;i<routes.length;i++){
   String response=mvc.perform(get("/api/v1/rooms/"+room+"/"+routes[i]).header("Authorization",adminToken)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
   assertEquals(json.valueToTree(snapshot.get(keys[i])),json.readTree(response).get("data"));
  }
  mvc.perform(get("/api/v1/rooms/"+room+"/network/topology").header("Authorization",adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data.roomId").value(room)).andExpect(jsonPath("$.data.nodes").isArray()).andExpect(jsonPath("$.data.links").isArray());
  mvc.perform(get("/api/v1/rooms/0/alarms").header("Authorization",adminToken)).andExpect(status().isNotFound());
 }
 @Test void explicitProcessingRecordPreservesOwnershipAndConflicts()throws Exception{
  String room=String.valueOf(db.queryForObject("SELECT id FROM sp_room ORDER BY id LIMIT 1",Long.class));
  Map<String,Object> order=service.createOrder(admin,object("title","API处理记录","device","A-01","roomId",room,"alarmEvent","manual"));String id=(String)order.get("id");
  service.action(admin,id,"assign",object("ownerAccountId",maint.get("id")));service.action(maint,id,"start",object());
  String route="/api/v1/work-orders/"+id;
  mvc.perform(patch(route+"/processing-record").header("Authorization",otherToken).contentType("application/json").content("{\"process\":\"检查\"}")).andExpect(status().isForbidden());
  mvc.perform(patch(route+"/processing-record").header("Authorization",maintToken).contentType("application/json").content("{\"process\":\"检查风扇\",\"cause\":\"故障\",\"result\":\"已处理\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("处理中")).andExpect(jsonPath("$.data.process").value("检查风扇"));
  mvc.perform(patch(route+"/processing-record").header("Authorization",maintToken).contentType("application/json").content("{\"process\":\"覆盖\",\"expectedState\":{\"status\":\"已接单\"}}")).andExpect(status().isConflict());
  mvc.perform(post(route+"/save").header("Authorization",maintToken).contentType("application/json").content("{}")).andExpect(status().isNotFound());
  mvc.perform(post(route+"/anything").header("Authorization",maintToken).contentType("application/json").content("{}")).andExpect(status().isNotFound());
  mvc.perform(post(route+"/submit").header("Authorization",maintToken).contentType("application/json").content("{\"process\":\"检查风扇\",\"cause\":\"故障\",\"result\":\"已处理\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("待复测"));
 }
 @Test void loginPermissionsAndRevocableLogout()throws Exception{
  mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(object("userName",admin.get("userName"),"password","wrong")))).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/auth/me").header("Authorization",maintToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("MAINTAINER")).andExpect(jsonPath("$.data.password_hash").doesNotExist());
  mvc.perform(get("/api/v1/users").header("Authorization",maintToken)).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/rooms").header("Authorization",maintToken)).andExpect(status().isForbidden());
  mvc.perform(get("/user/list").header("Authorization",adminToken)).andExpect(status().isNotFound());
  mvc.perform(post("/user/login").contentType("application/json").content("{}")).andExpect(status().isNotFound());
  mvc.perform(post("/user/add").header("Authorization",adminToken).contentType("application/json").content("{}")).andExpect(status().isNotFound());
  mvc.perform(put("/user/edit").header("Authorization",adminToken).contentType("application/json").content("{}")).andExpect(status().isNotFound());
  mvc.perform(delete("/user/del/1").header("Authorization",adminToken)).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/auth/logout").header("Authorization",maintToken)).andExpect(status().isOk());
  mvc.perform(get("/api/v1/auth/me").header("Authorization",maintToken)).andExpect(status().isUnauthorized());
 }
 @Test void profilesPersistAndHistoryUsesStableIdentity()throws Exception{
  assertEquals("platform_smart_park_stage1_test_20261008",db.queryForObject("SELECT DATABASE()",String.class));
  assertEquals(Collections.emptyList(),maint.get("skills"));
  service.savePerson(admin,object("displayName","散热技师","biography","熟悉机柜维护","skills",Arrays.asList("制冷散热","设备硬件")),((Number)maint.get("id")).longValue());
  Map<String,Object> fresh=service.identity(maintToken);
  assertEquals("熟悉机柜维护",fresh.get("biography"));assertEquals(Arrays.asList("制冷散热","设备硬件"),fresh.get("skills"));
  assertThrows(cn.guat.smartpark.exception.PlatformException.class,()->service.savePerson(admin,object("displayName","技师","skills",Arrays.asList("未知技能")),((Number)maint.get("id")).longValue()));
  String room=String.valueOf(db.queryForObject("SELECT id FROM sp_room ORDER BY id LIMIT 1",Long.class));
  Map<String,Object> done=service.createOrder(admin,object("title","测试历史","device","同名机柜","roomId",room,"alarmEvent","manual","cabinetId","stable-test-a","recordOrigin","DEMO"));
  done.put("status","已完成");done.put("cause","风扇故障");done.put("process","更换风扇");done.put("result","恢复");
  db.update("UPDATE console_order SET status='已完成',payload=? WHERE id=?",json.writeValueAsString(done),done.get("id"));
  assertEquals(done.get("id"),service.history(admin,room,"stable-test-a",null,null).get(0).get("id"));
  assertTrue(service.history(admin,room,"stable-test-b",null,null).isEmpty());
  assertEquals(1,service.history(admin,room,"stable-test-a",null,"REAL").size());assertEquals(1,service.history(admin,room,"stable-test-a",null,"DEMO").size());
  assertThrows(cn.guat.smartpark.exception.PlatformException.class,()->service.history(maint,room,"stable-test-a",null,null));
  mvc.perform(get("/api/v1/work-orders/history").header("Authorization",adminToken).param("roomId",room).param("cabinetId","stable-test-a")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].cause").value("风扇故障"));
 }
 @Test void orderOwnershipSubmissionAndNotices()throws Exception{
  String room=String.valueOf(db.queryForObject("SELECT id FROM sp_room ORDER BY (record_origin='REAL') DESC,id LIMIT 1",Long.class));
  monitoring.saveRecord(room,"ALARM","test-event",object("id","test-event","recordOrigin","DEMO","status","未处理","title","温度告警"));
  Map<String,Object> order=service.createOrder(admin,object("title","温度告警","device","A-02","alarmEvent","test-event","recordOrigin","DEMO"));String id=(String)order.get("id");
  assertEquals(id,service.createOrder(admin,object("title","温度告警","device","A-02","alarmEvent","test-event")).get("id"));
  mvc.perform(post("/api/v1/work-orders/"+id+"/claim").header("Authorization",maintToken).contentType("application/json").content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("已接单"));
  mvc.perform(post("/api/v1/work-orders/"+id+"/claim").header("Authorization",otherToken).contentType("application/json").content("{}")).andExpect(status().isConflict());
  assertTrue(service.orders(other).isEmpty());
  mvc.perform(post("/api/v1/work-orders/"+id+"/start").header("Authorization",otherToken).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  service.action(maint,id,"start",object());
  mvc.perform(post("/api/v1/work-orders/"+id+"/submit").header("Authorization",maintToken).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
  Map<String,Object> submitted=service.action(maint,id,"submit",object("cause","滤网堵塞","process","清理滤网","result","处理完成"));assertEquals("待复测",submitted.get("status"));assertFalse((Boolean)submitted.get("verified"));
  mvc.perform(post("/api/v1/work-orders/"+id+"/retest").header("Authorization",adminToken).contentType("application/json").content("{}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("待复测")).andExpect(jsonPath("$.data.verified").value(false));
  assertFalse(service.notices(admin).isEmpty());List<Map<String,Object>>notices=service.notices(maint);assertFalse(notices.isEmpty());
  Long notice=((Number)notices.get(0).get("id")).longValue();service.readNotice(other,notice);assertFalse((Boolean)service.notices(maint).get(0).get("isRead"));
  service.createOrder(admin,object("title","第二条","device","A-01"));service.readNotice(maint,notice);service.deleteRead(maint);assertEquals(1,service.notices(maint).size());
  mvc.perform(delete("/api/v1/users/"+maint.get("id")).header("Authorization",adminToken)).andExpect(status().isConflict());
 }
 @Test void passwordAndDisabledAccountInvalidateSessions()throws Exception{
  mvc.perform(put("/api/v1/auth/password").header("Authorization",maintToken).contentType("application/json").content(json.writeValueAsString(object("oldPassword","wrong","newPassword","NewPassword123!")))).andExpect(status().isBadRequest());
  service.changePassword(maint,object("oldPassword",password,"newPassword","NewPassword123!"));
  mvc.perform(get("/api/v1/auth/me").header("Authorization",maintToken)).andExpect(status().isUnauthorized());
  Map<String,Object> newLogin=service.login(object("userName",maint.get("userName"),"password","NewPassword123!"));String token="Bearer "+newLogin.get("token");
  service.savePerson(admin,object("displayName","停用账号","enabled",false),((Number)maint.get("id")).longValue());
  mvc.perform(get("/api/v1/auth/me").header("Authorization",token)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(object("userName",maint.get("userName"),"password","NewPassword123!")))).andExpect(status().isForbidden());
 }
 @Test void personnelCrudNeverReturnsSecrets()throws Exception{
  mvc.perform(get("/api/v1/users").header("Authorization",adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].password").doesNotExist()).andExpect(jsonPath("$.data[0].password_hash").doesNotExist());
  service.savePerson(admin,object("displayName","新的姓名","phone","13800000000","email","demo@example.com","enabled",true),((Number)other.get("id")).longValue());
  assertEquals("新的姓名",service.identity(otherToken).get("displayName"));
  service.deletePerson(admin,((Number)other.get("id")).longValue());
  mvc.perform(get("/api/v1/auth/me").header("Authorization",otherToken)).andExpect(status().isUnauthorized());
 }
 @Test void administratorAssignmentControlsVisibility()throws Exception{
  Map<String,Object>order=service.createOrder(admin,object("title","指派测试","device","A-01"));String id=(String)order.get("id");
  mvc.perform(post("/api/v1/work-orders/"+id+"/assign").header("Authorization",maintToken).contentType("application/json").content(json.writeValueAsString(object("ownerAccountId",other.get("id"))))).andExpect(status().isForbidden());
  assertThrows(cn.guat.smartpark.exception.PlatformException.class,()->service.action(admin,id,"assign",object("ownerAccountId",admin.get("id"))));
  Map<String,Object>assigned=service.action(admin,id,"assign",object("ownerAccountId",maint.get("id")));
  assertEquals("已接单",assigned.get("status"));assertEquals(maint.get("displayName"),assigned.get("owner"));
  assertTrue(service.orders(maint).stream().anyMatch(o->id.equals(o.get("id"))));assertFalse(service.orders(other).stream().anyMatch(o->id.equals(o.get("id"))));
  service.savePerson(admin,object("displayName","停用","enabled",false),((Number)other.get("id")).longValue());
  assertThrows(cn.guat.smartpark.exception.PlatformException.class,()->service.action(admin,id,"assign",object("ownerAccountId",other.get("id"))));
 }
 @Test void oldDirectEvidenceButtonCannotBypassUnifiedMonitoring(){
  String room=String.valueOf(db.queryForObject("SELECT id FROM sp_room ORDER BY id LIMIT 1",Long.class));
  for(String origin:Arrays.asList("DEMO","REAL")){String event="legacy-"+UUID.randomUUID();monitoring.saveRecord(room,"ALARM",event,object("id",event,"title","温度告警","recordOrigin",origin,"status","未处理"));String id=(String)service.createOrder(admin,object("title","温度告警","device","A-01","roomId",room,"alarmEvent",event)).get("id");service.action(admin,id,"assign",object("ownerAccountId",maint.get("id")));service.action(maint,id,"start",object());service.action(maint,id,"submit",object("cause","检查","process","处理","result","申请验收"));assertThrows(cn.guat.smartpark.exception.PlatformException.class,()->service.action(admin,id,"demo-retest",object("outcome","normal")));assertEquals("待复测",service.orders(admin).stream().filter(o->id.equals(o.get("id"))).findFirst().get().get("status"));}
 }
 @Test void automaticRetestWaitsThenArchivesOnlyLinkedAlarm()throws Exception{automaticRetestCase(24,"已完成","已恢复");}
 @Test void automaticRetestReturnsAbnormalOrderToProcessing()throws Exception{automaticRetestCase(38,"处理中","未处理");}
 private void automaticRetestCase(double value,String expected,String alarmStatus)throws Exception{
  String room=String.valueOf(db.queryForObject("SELECT id FROM sp_room ORDER BY (record_origin='REAL') DESC,id LIMIT 1",Long.class));String event="closure-"+UUID.randomUUID(),entity="test-"+UUID.randomUUID();
  monitoring.saveRecord(room,"ALARM",event,object("id",event,"title","温度异常","recordOrigin","REAL","entityType","CABINET","entityId",entity,"status","未处理","rule",object("metric","temperature","unit","℃","min",0,"max",28,"maxAgeSeconds",300,"normalSeconds",0,"maxGapSeconds",30)));
  String unrelated="other-"+UUID.randomUUID();monitoring.saveRecord(room,"ALARM",unrelated,object("id",unrelated,"recordOrigin","REAL","status","未处理"));
  String id=(String)service.createOrder(admin,object("title","温度异常","device","A-01","roomId",room,"alarmEvent",event)).get("id");
  service.action(admin,id,"assign",object("ownerAccountId",maint.get("id")));service.action(maint,id,"start",object());
  assertThrows(cn.guat.smartpark.exception.PlatformException.class,()->service.action(maint,id,"submit",object("process","清理","result","完成")));
  Map<String,Object>submitted=service.action(maint,id,"submit",object("cause","滤网堵塞","process","清理滤网","result","已修复"));
  service.automaticRetest(id);assertEquals("待复测",service.orders(admin).stream().filter(o->id.equals(o.get("id"))).findFirst().get().get("status"));
  java.time.Instant submittedAt=java.time.Instant.parse(submitted.get("submittedAt").toString());
  while(!java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).isAfter(submittedAt))Thread.sleep(2);
  java.time.Instant at=java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
  db.update("INSERT INTO sp_reading(room_id,entity_type,entity_id,metric,value,unit,observed_at,quality,source,record_origin) VALUES(?,'CABINET',?,'temperature',?,'℃',?,'GOOD','test-collector','REAL')",room,entity,value,java.sql.Timestamp.from(at));
  service.automaticRetest(id);Map<String,Object>updated=service.orders(admin).stream().filter(o->id.equals(o.get("id"))).findFirst().get();assertEquals(expected,updated.get("status"));
  assertEquals(alarmStatus,monitoring.records(room,"ALARM").stream().filter(o->event.equals(o.get("id"))).findFirst().get().get("status"));
  assertEquals("未处理",monitoring.records(room,"ALARM").stream().filter(o->unrelated.equals(o.get("id"))).findFirst().get().get("status"));
 }
}
