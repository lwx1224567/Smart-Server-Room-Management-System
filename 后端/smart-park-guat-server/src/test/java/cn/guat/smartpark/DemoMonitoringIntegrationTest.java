package cn.guat.smartpark;

import cn.guat.smartpark.service.*;
import cn.guat.smartpark.exception.PlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;
import static cn.guat.smartpark.service.DemoTemperaturePolicy.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MySQL + production services. All fixture writes target an explicitly guarded test DB. */
@SpringBootTest(properties="console.orders.retest-interval-ms=15000")
@AutoConfigureMockMvc @ActiveProfiles("test")
class DemoMonitoringIntegrationTest {
 @Autowired IntegrationService integration;@Autowired InventoryMaintenanceService inventory;@Autowired ConsoleService console;
 @SpyBean MonitoringService monitor;@Autowired DemoMonitoringService demo;
 @SpyBean JdbcTemplate db;@Autowired ObjectMapper json;@Autowired MockMvc mvc;@Autowired ConfigurableApplicationContext context;
 @MockBean Clock clock;
 String room,cabinet,zone,park,adminToken,maintToken;Map<String,Object>admin,maint;
 @BeforeEach void fixture(){
  assertEquals("platform_smart_park_stage1_test_20261008",db.queryForObject("SELECT DATABASE()",String.class));
  when(clock.instant()).thenAnswer(inv->Instant.now());
  String suffix=UUID.randomUUID().toString().substring(0,8);
  Map<String,Object>p=inventory.save("parks",null,object("code","demo-a-"+suffix,"name","阶段A测试园区"));park=p.get("id").toString();
  Map<String,Object>r=inventory.save("rooms",null,object("parkId",p.get("id"),"code","demo-a-"+suffix,"name","阶段A测试机房","location","独立测试","sceneModelKey","demo-room-"+suffix));room=r.get("id").toString();
  Map<String,Object>z=inventory.save("zones",null,object("roomId",r.get("id"),"code","A","name","测试区","displayOrder",0));zone=z.get("id").toString();
  Map<String,Object>c=inventory.save("cabinets",null,object("zoneId",z.get("id"),"code","A-01","name","测试 A-01","serialNumber","demo-serial-"+suffix,"capacityU",42,"sceneModelKey","demo-cabinet-"+suffix,"gridRow",0,"gridColumn",0,"positionX",0,"positionY",0,"positionZ",0,"registrationStatus","ACTIVE"));cabinet=c.get("id").toString();
  TestPropertyValues.of("console.demo.room-id="+room).applyTo(context);
  admin=account("ADMIN",suffix+"a");maint=account("MAINTAINER",suffix+"m");
  integration.saveRule(room,RULE_ID,rule("DEMO"));
 }
 private Map<String,Object> account(String role,String suffix){
  String name="stage_a_"+suffix,password=UUID.randomUUID().toString();
  console.savePerson(object("role","ADMIN"),object("userName",name,"displayName",name,"password",password,"enabled",true),null);
  db.update("UPDATE console_account SET role=? WHERE user_name=?",role,name);
  Map<String,Object>login=console.login(object("userName",name,"password",password));
  assertTrue(login.get("sessionCode").toString().matches("HY[0-9]{12,}"));
  if("ADMIN".equals(role))adminToken="Bearer "+login.get("token");else maintToken="Bearer "+login.get("token");return (Map<String,Object>)login.get("user");
 }
 @AfterEach void cleanup(){
  reset(monitor,db);when(clock.instant()).thenAnswer(inv->Instant.now());
  // This room/account graph was created by this test; no existing fixture or development row is removed.
  if(room!=null){
   for(Map<String,Object>o:console.orders(admin))if(room.equals(o.get("roomId")))db.update("DELETE FROM console_notice WHERE order_id=?",o.get("id"));
   db.update("DELETE FROM console_order WHERE JSON_UNQUOTE(JSON_EXTRACT(payload,'$.roomId'))=?",room);
   for(String table:Arrays.asList("sp_reading","sp_monitor_record","sp_ingest_batch","sp_energy_sample"))db.update("DELETE FROM "+table+" WHERE room_id=?",room);
   db.update("DELETE FROM sp_cabinet WHERE zone_id=?",zone);db.update("DELETE FROM sp_zone WHERE id=?",zone);db.update("DELETE FROM sp_room WHERE id=?",room);db.update("DELETE FROM sp_park WHERE id=?",park);
  }
  for(Map<String,Object>person:Arrays.asList(admin,maint))if(person!=null){db.update("DELETE FROM console_session WHERE account_id=?",person.get("id"));db.update("DELETE FROM console_notice WHERE account_id=?",person.get("id"));db.update("DELETE FROM console_account WHERE id=?",person.get("id"));}
 }
 private Map<String,Object> rule(String origin){Map<String,Object>r=object("entityType","CABINET","entityId",cabinet,"metric","temperature","unit",UNIT,"title","阶段A温度告警","severity","critical","min",0,"max",30,"maxAgeSeconds",300,"normalSeconds",0,"maxGapSeconds",30,"enabled",true,"recordOrigin",origin);if("DEMO".equals(origin))r.put("sampleSource",SOURCE);return r;}
 private Map<String,Object> send(double value)throws Exception{
  String response=mvc.perform(post("/api/v1/rooms/"+room+"/demo-monitoring/temperature").header("Authorization",adminToken).contentType("application/json").content(json.writeValueAsString(object("cabinetId",cabinet,"value",value,"requestId",UUID.randomUUID().toString())))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return (Map<String,Object>)json.readValue(response,Map.class).get("data");
 }
 private Map<String,Object> orderFor(String event){return console.createOrder(admin,object("roomId",room,"alarmEvent",event,"device","A-01","title","阶段A温度告警","priority","紧急"));}
 private String openOrder()throws Exception{Map<String,Object> hot=send(38);String event=(String)((List<?>)hot.get("alarmIds")).get(0);String id=(String)orderFor(event).get("id");console.action(admin,id,"assign",object("ownerAccountId",maint.get("id")));console.action(maint,id,"start",object());return id;}
 private Map<String,Object> submit(String id){return console.action(maint,id,"submit",object("cause","测试风道堵塞","process","测试清理风道","result","现场处理完成，请读取监测数据复检"));}
 private Map<String,Object> order(String id){return console.orders(admin).stream().filter(o->id.equals(o.get("id"))).findFirst().get();}
 private void afterSubmission(Map<String,Object>o)throws Exception{Instant submitted=Instant.parse(o.get("submittedAt").toString());while(!Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).isAfter(submitted))Thread.sleep(2);}
 @Test void hotSampleCreatesDemoAlarmAndPostSubmissionNormalCompletes()throws Exception{
  String id=openOrder();Map<String,Object>submitted=submit(id);afterSubmission(submitted);Map<String,Object>normal=send(24);
  assertEquals("待复测",order(id).get("status"));console.automaticRetest(id);Map<String,Object>done=order(id);
  assertEquals("已完成",done.get("status"));assertEquals(true,done.get("verified"));assertNotNull(done.get("archivedAt"));
  Map<String,Object>proof=(Map<String,Object>)done.get("retestEvidence");assertEquals("REAL",proof.get("mode"));assertEquals("DATABASE_READING",proof.get("path"));
  Map<String,Object>point=(Map<String,Object>)((List<?>)proof.get("evidence")).get(0);assertEquals(normal.get("readingId").toString(),point.get("id").toString());assertEquals(SOURCE,point.get("source"));assertTrue(Instant.parse(point.get("observedAt").toString()).isAfter(Instant.parse(submitted.get("submittedAt").toString())));
  assertEquals("已恢复",monitor.records(room,"ALARM").get(0).get("status"));assertEquals(2,monitor.readings(room).size());
 }
 @Test void latestAbnormalCannotBeHiddenByOlderNormal()throws Exception{String id=openOrder();Map<String,Object>submitted=submit(id);afterSubmission(submitted);send(24);send(38);console.automaticRetest(id);assertEquals("处理中",order(id).get("status"));assertEquals("未处理",monitor.records(room,"ALARM").get(0).get("status"));}
 @Test void noNewOrPreSubmissionNormalStaysPending()throws Exception{String id=openOrder();send(24);submit(id);console.automaticRetest(id);assertEquals("待复测",order(id).get("status"));}
 @Test void expiredNormalStaysPendingWithoutChangingSubmissionTime()throws Exception{String id=openOrder();Map<String,Object>submitted=submit(id);afterSubmission(submitted);send(24);when(clock.instant()).thenReturn(Instant.now().plusSeconds(301));console.automaticRetest(id);assertEquals("待复测",order(id).get("status"));assertTrue(order(id).get("retestNote").toString().contains("过期"));assertEquals(submitted.get("submittedAt"),order(id).get("submittedAt"));}
 @Test void requestIdIsIdempotentAndConflictsAreRejected()throws Exception{
  Map<String,Object>b=object("cabinetId",cabinet,"value",38,"requestId","retry-1");Map<String,Object>first=demo.submit(room,b,admin);Thread.sleep(3);Map<String,Object>second=demo.submit(room,b,admin);
  assertEquals(true,second.get("duplicate"));assertEquals(first.get("readingId"),second.get("readingId"));assertEquals(first.get("observedAt"),second.get("observedAt"));assertEquals(1,monitor.readings(room).size());
  assertTrue(first.get("requestCode").toString().matches("QQ[0-9]{12,}"));
  assertTrue(first.get("batchCode").toString().matches("PC[0-9]{12,}"));
  assertEquals(first.get("requestCode"),second.get("requestCode"));assertEquals(first.get("batchCode"),second.get("batchCode"));
  Map<String,Object>log=monitor.records(room,"LOG").get(0);assertTrue(log.get("logCode").toString().matches("RZ[0-9]{12,}"));assertEquals("retry-1",log.get("requestId"));assertEquals(first.get("requestCode"),log.get("requestCode"));
  b.put("value",24);assertEquals(409,assertThrows(PlatformException.class,()->demo.submit(room,b,admin)).status);
 }
 @Test void retryOfPreSubmissionSampleCannotBecomeNewEvidence()throws Exception{
  String id=openOrder();Map<String,Object>body=object("cabinetId",cabinet,"value",24,"requestId","before-maintenance");Map<String,Object>first=demo.submit(room,body,admin);
  Map<String,Object>submitted=submit(id);afterSubmission(submitted);Map<String,Object>retry=demo.submit(room,body,admin);
  assertEquals(first.get("observedAt"),retry.get("observedAt"));assertEquals(true,retry.get("duplicate"));console.automaticRetest(id);assertEquals("待复测",order(id).get("status"));assertEquals(2,monitor.readings(room).size());
 }
 @Test void missingRuleOrDisabledScopeCannotWriteSamples(){
  integration.deleteRule(room,RULE_ID);assertEquals(409,assertThrows(PlatformException.class,()->demo.submit(room,object("cabinetId",cabinet,"value",38,"requestId","missing-rule"),admin)).status);
  integration.saveRule(room,RULE_ID,rule("DEMO"));TestPropertyValues.of("console.demo.room-id=").applyTo(context);
  assertEquals(409,assertThrows(PlatformException.class,()->demo.submit(room,object("cabinetId",cabinet,"value",38,"requestId","disabled-scope"),admin)).status);assertTrue(monitor.readings(room).isEmpty());
 }
 @Test void demoRuleCannotChangeOriginOrScope(){
  Map<String,Object> unified=rule("REAL");unified.put("sampleSource",SOURCE);assertEquals("REAL",integration.saveRule(room,RULE_ID,unified).get("recordOrigin"));
  for(String field:Arrays.asList("entityType","metric","unit","entityId","sampleSource")){Map<String,Object>invalid=rule("DEMO");invalid.put(field,"invalid");assertThrows(PlatformException.class,()->integration.saveRule(room,RULE_ID,invalid));}
  Map<String,Object>invalid=rule("DEMO");invalid.put("max",31);assertThrows(PlatformException.class,()->integration.saveRule(room,RULE_ID,invalid));assertEquals("REAL",monitor.records(room,"RULE").get(0).get("recordOrigin"));
 }
 @Test void auditFailureRollsBackSampleBatchAndGeneratedAlarm(){
  doThrow(new IllegalStateException("test audit persistence fault")).when(monitor).saveRecord(eq(room),eq("LOG"),eq("demo-temperature:audit-failure"),anyMap());
  assertThrows(IllegalStateException.class,()->demo.submit(room,object("cabinetId",cabinet,"value",38,"requestId","audit-failure"),admin));assertTrue(monitor.readings(room).isEmpty());assertTrue(monitor.records(room,"ALARM").isEmpty());assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM sp_ingest_batch WHERE room_id=?",Integer.class,room));
 }
 @Test void permissionsAndRestrictedContractRejectForeignFields()throws Exception{
  String path="/api/v1/rooms/"+room+"/demo-monitoring/temperature";Map<String,Object>b=object("cabinetId",cabinet,"value",38,"requestId","contract");
  mvc.perform(post(path).contentType("application/json").content(json.writeValueAsString(b))).andExpect(status().isUnauthorized());
  mvc.perform(post(path).header("Authorization",maintToken).contentType("application/json").content(json.writeValueAsString(b))).andExpect(status().isForbidden());
  Map<String,Object>foreign=object("entityType","DEVICE","metric","humidity","unit","C","quality","BAD","recordOrigin","REAL","source","forged","observedAt",Instant.now().toString(),"status","已完成");
  for(String key:foreign.keySet()){
   Map<String,Object>bad=new LinkedHashMap<>(b);bad.put(key,foreign.get(key));mvc.perform(post(path).header("Authorization",adminToken).contentType("application/json").content(json.writeValueAsString(bad))).andExpect(status().isBadRequest());
  }
  Map<String,Object>missingQuality=new LinkedHashMap<>(b);missingQuality.put("quality","MISSING");mvc.perform(post(path).header("Authorization",adminToken).contentType("application/json").content(json.writeValueAsString(missingQuality))).andExpect(status().isBadRequest());
  Map<String,Object>a2=inventory.save("cabinets",null,object("zoneId",Long.valueOf(zone),"code","A-02","name","测试 A-02","serialNumber",UUID.randomUUID().toString(),"capacityU",42,"sceneModelKey",UUID.randomUUID().toString(),"gridRow",0,"gridColumn",1,"positionX",2,"positionY",0,"positionZ",0,"registrationStatus","ACTIVE"));
  b.put("cabinetId",a2.get("id").toString());assertEquals(400,assertThrows(PlatformException.class,()->demo.submit(room,b,admin)).status);assertTrue(monitor.readings(room).isEmpty());
 }
 private void genericSample(String origin,double value,String type,String metric,String unit,String quality)throws Exception{
  integration.ingest(room,object("batchId",UUID.randomUUID().toString(),"readings",Arrays.asList(object("entityType",type,"entityId",cabinet,"metric",metric,"value",value,"unit",unit,"observedAt",Instant.now().toString(),"quality",quality,"recordOrigin",origin,"source","TEST_COLLECTOR"))));
 }
 @Test void legacyAndCurrentOriginsUseOneRetestFlow()throws Exception{
  String id=openOrder();Map<String,Object>submitted=submit(id);afterSubmission(submitted);genericSample("REAL",24,"CABINET","temperature",UNIT,"GOOD");console.automaticRetest(id);assertEquals("已完成",order(id).get("status"));assertEquals("REAL",order(id).get("recordOrigin"));
  integration.saveRule(room,"another-temperature",rule("REAL"));genericSample("DEMO",38,"CABINET","temperature",UNIT,"GOOD");Map<String,Object>linked=monitor.records(room,"ALARM").stream().filter(v->"another-temperature".equals(v.get("ruleId"))&& !"已恢复".equals(v.get("status"))).findFirst().get();String second=(String)orderFor((String)linked.get("id")).get("id");console.action(admin,second,"assign",object("ownerAccountId",maint.get("id")));console.action(maint,second,"start",object());Map<String,Object>again=submit(second);afterSubmission(again);send(24);console.automaticRetest(second);assertEquals("已完成",order(second).get("status"));
 }
 @Test void wrongQualityUnitMetricAndUntrustedSourceCannotComplete()throws Exception{
  String id=openOrder();Map<String,Object>submitted=submit(id);afterSubmission(submitted);
  for(String quality:Arrays.asList("BAD","MISSING"))genericSample("DEMO",24,"CABINET","temperature",UNIT,quality);
  genericSample("DEMO",24,"CABINET","humidity",UNIT,"GOOD");genericSample("DEMO",24,"CABINET","temperature","C","GOOD");console.automaticRetest(id);assertEquals("待复测",order(id).get("status"));
  assertThrows(PlatformException.class,()->integration.ingest(room,object("batchId","spoof","readings",Arrays.asList(object("entityType","CABINET","entityId",cabinet,"metric","temperature","unit",UNIT,"value",24,"observedAt",Instant.now().toString(),"quality","GOOD","recordOrigin","DEMO","source",SOURCE)))));
 }
 @Test void managedOrderCannotUseOldButtonAndDuplicateOrdersReuseEvent()throws Exception{String id=openOrder();Map<String,Object>created=order(id);assertEquals(id,orderFor((String)created.get("alarmEvent")).get("id"));submit(id);assertEquals(409,assertThrows(PlatformException.class,()->console.action(admin,id,"demo-retest",object("outcome","normal"))).status);assertEquals("待复测",order(id).get("status"));}
 @Test void concurrentRequestsAndRetestsAreSerialized()throws Exception{
  Map<String,Object>b=object("cabinetId",cabinet,"value",38,"requestId","parallel");ExecutorService pool=Executors.newFixedThreadPool(2);
  try{Future<Map<String,Object>>a=pool.submit(()->demo.submit(room,b,admin)),other=pool.submit(()->demo.submit(room,b,admin));Map<String,Object>first=a.get(10,TimeUnit.SECONDS),second=other.get(10,TimeUnit.SECONDS);assertEquals(first.get("readingId"),second.get("readingId"));assertEquals(1,monitor.readings(room).size());
   String event=(String)((List<?>)first.get("alarmIds")).get(0);Future<Map<String,Object>>o1=pool.submit(()->orderFor(event)),o2=pool.submit(()->orderFor(event));String id=(String)o1.get(10,TimeUnit.SECONDS).get("id");assertEquals(id,o2.get(10,TimeUnit.SECONDS).get("id"));console.action(admin,id,"assign",object("ownerAccountId",maint.get("id")));console.action(maint,id,"start",object());Map<String,Object>submitted=submit(id);afterSubmission(submitted);send(24);
   Future<?>r1=pool.submit(()->console.automaticRetest(id)),r2=pool.submit(()->console.automaticRetest(id));r1.get(10,TimeUnit.SECONDS);r2.get(10,TimeUnit.SECONDS);assertEquals("已完成",order(id).get("status"));assertEquals(1,((List<?>)order(id).get("steps")).stream().filter(v->v.toString().contains("小维自动复检")).count());
  }finally{pool.shutdownNow();}
 }
 @Test void retestPersistenceFailureRollsBackAlarmAndOrder()throws Exception{
  String id=openOrder();Map<String,Object>submitted=submit(id);afterSubmission(submitted);
  doThrow(new IllegalStateException("test notification persistence fault")).when(db).update(eq("INSERT INTO console_notice(account_id,title,content,order_id) VALUES(?,?,?,?)"),any(),any(),any(),any());
  send(24);assertThrows(IllegalStateException.class,()->console.automaticRetest(id));
  assertEquals("待复测",order(id).get("status"));assertEquals("未处理",monitor.records(room,"ALARM").get(0).get("status"));assertNull(order(id).get("archivedAt"));
 }
 @Test void realSchedulerCompletesWithoutBrowserOrActiveSessions()throws Exception{
  String id=openOrder();Map<String,Object>submitted=submit(id);afterSubmission(submitted);send(24);console.logout(adminToken);console.logout(maintToken);
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);
  while(!"已完成".equals(order(id).get("status"))&&System.nanoTime()<deadline)Thread.sleep(200);
  assertEquals("已完成",order(id).get("status"));
  assertEquals("已恢复",monitor.records(room,"ALARM").get(0).get("status"));
 }
}
