package cn.guat.smartpark;

import cn.guat.smartpark.service.*;
import cn.guat.smartpark.exception.PlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import java.util.function.Consumer;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AssistantAgentIntegrationTest {
 @Autowired AssistantAgentService agent;@Autowired ConsoleService console;@Autowired MonitoringService monitoring;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
 @MockBean IntegrationService model;
 @Autowired MockMvc mvc;
 Map<String,Object>admin,person;String room,cabinet,key,event;
 @BeforeEach void fixture(){
  assertEquals("platform_smart_park_stage1_test_20261008",db.queryForObject("SELECT DATABASE()",String.class));
  String suffix=UUID.randomUUID().toString();admin=object("id",9000001L,"role","ADMIN","displayName","测试管理员","assistantPageId",suffix);
  room=String.valueOf(db.queryForObject("SELECT id FROM sp_room ORDER BY id LIMIT 1",Long.class));
  Map<String,Object>c=db.queryForList("SELECT c.id,c.scene_model_key AS sceneKey FROM sp_cabinet c JOIN sp_zone z ON z.id=c.zone_id WHERE z.room_id=? ORDER BY c.id LIMIT 1",room).get(0);cabinet=String.valueOf(c.get("id"));key=String.valueOf(c.get("sceneKey"));
  // Test rows only; existing room data is hidden transactionally and rolled back.
  db.update("DELETE FROM sp_monitor_record WHERE room_id=? AND kind='ALARM'",room);
  event="agent-"+suffix;monitoring.saveRecord(room,"ALARM",event,object("id",event,"roomId",room,"cabinetId",cabinet,"title","温度告警","recordOrigin","DEMO","status","未处理","rule",object("metric","temperature")));
  String user="agent-"+suffix;console.savePerson(admin,object("userName",user,"displayName","散热技师","password","AgentTest123!","skills",Arrays.asList("制冷散热")),null);
  person=console.people(admin).stream().filter(p->user.equals(p.get("userName"))).findFirst().get();
 }
 // Stage 5 only: requires an explicitly authorized isolated database; never run in no-DB checks.
 private Map<String,Object>chat(String text){return agent.chat(admin,room,object("text",text,"requestId",UUID.randomUUID().toString()));}
 private Map<String,Object>invokeTool(String text,String name,Map<String,Object>args){when(model.modelTools(anyList(),anyList())).thenReturn(call(name,args),say("工具查询完成"));return chat(text);}
 private Map<String,Object>query(String cabinetKey){return invokeTool("查询告警","get_monitoring",cabinetKey==null?object():object("cabinetKey",cabinetKey));}
 private Map<String,Object>toolResult(Map<String,Object>s,String name){Map<String,Object>found=null;for(Object t:(List<?>)s.get("trace")){Map<?,?>trace=(Map<?,?>)t;Object tool=trace.get("tool");if(tool instanceof Map&&name.equals(((Map<?,?>)tool).get("name")))found=(Map<String,Object>)trace.get("result");}assertNotNull(found,"missing tool: "+name);return found;}
 private Map<String,Object>confirmationBody(Map<String,Object>s){Map<?,?>p=(Map<?,?>)s.get("pending");assertNotNull(p);return object("pendingToken",p.get("token"),"kind",p.get("kind"),"requestId",UUID.randomUUID().toString());}
 private Map<String,Object>confirm(Map<String,Object>s){return agent.confirm(admin,room,confirmationBody(s));}
 private String confirmationStatus(Map<String,Object>s){return ((Map<?,?>)s.get("confirmation")).get("status").toString();}
 private Map<String,Object>prepareOrder(){return invokeTool("为 "+key+" 创建工单","prepare_order",object("cabinetKey",key,"alarmId",event));}
 private Map<String,Object>walk(){return invokeTool("前往 "+key,"scene_action",object("action","visit","cabinetKey",key));}
 private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new RuntimeException(e);}}
 private Map<String,Object>call(String name,Map<String,Object>args){try{return object("role","assistant","content",null,"tool_calls",Arrays.asList(object("id",UUID.randomUUID().toString(),"type","function","function",object("name",name,"arguments",json.writeValueAsString(args)))));}catch(Exception e){throw new RuntimeException(e);}}
 private Map<String,Object>say(String text){return object("role","assistant","content",text);}
 private long orderCount(){return console.orders(admin).stream().filter(o->event.equals(o.get("alarmEvent"))).count();}
 @Test void multiStepCreateConfirmRecommendAssignAndRestore(){
  Map<String,Object>prepared=prepareOrder();assertNotNull(prepared.get("pending"));assertEquals(0,orderCount());
  Map<String,Object>body=confirmationBody(prepared);assertEquals("SUCCEEDED",confirmationStatus(agent.confirm(admin,room,body)));agent.confirm(admin,room,body);assertEquals(1,orderCount());
  String id=console.orders(admin).stream().filter(o->event.equals(o.get("alarmEvent"))).findFirst().get().get("id").toString();
  Map<String,Object>recommended=invokeTool("为工单 "+id+" 推荐人员","recommend_people",object("orderId",id));assertTrue(encode(toolResult(recommended,"recommend_people")).contains(person.get("userName").toString()));
  Map<String,Object>assignment=invokeTool("把工单 "+id+" 指派给"+person.get("userName"),"prepare_assignment",object("orderId",id,"ownerUserName",person.get("userName")));assertNotNull(assignment.get("pending"));
  AssistantAgentService restarted=new AssistantAgentService(db,json,model,console,monitoring);assertNull(restarted.current(admin,room).get("pending"));assertTrue(((List<?>)restarted.current(admin,room).get("messages")).isEmpty());
  assertEquals("SUCCEEDED",confirmationStatus(confirm(assignment)));assertEquals(String.valueOf(person.get("id")),String.valueOf(console.orders(admin).stream().filter(o->id.equals(o.get("id"))).findFirst().get().get("ownerAccountId")));
 }
 @Test void cancelAndFailedModelNeverCreate(){
  Map<String,Object>prepared=prepareOrder();Map<String,Object>body=confirmationBody(prepared);agent.dismissPending(admin,room,object("pendingToken",body.get("pendingToken")));assertThrows(PlatformException.class,()->agent.confirm(admin,room,body));
  when(model.modelTools(anyList(),anyList())).thenReturn(say("请点击确认卡片"));assertNull(chat("确认").get("pending"));assertEquals(0,orderCount());
  when(model.modelTools(anyList(),anyList())).thenThrow(new PlatformException(502,"MODEL","offline"));assertEquals(true,chat("解释告警原因").get("interrupted"));assertEquals(0,orderCount());
 }
 @Test void visitQueriesOnlyTargetAfterMatchingArrival(){
  monitoring.saveRecord(room,"ALARM",event+"-other",object("id",event+"-other","title","未映射告警","status","未处理"));
  Map<String,Object>moving=walk();Map<?,?>action=(Map<?,?>)moving.get("clientAction");assertNotNull(action);assertEquals("visit",action.get("type"));assertFalse(action.containsKey("queryAfterArrival"));
  when(model.modelTools(anyList(),anyList())).thenReturn(call("get_monitoring",object("cabinetKey",key)),say("到达后已查询工具"));
  Map<String,Object>arrived=agent.sceneResult(admin,room,object("token",action.get("token"),"type","visit","status","SUCCEEDED","result","已到达","arrived",true,"cabinetSceneModelKey",key));
  String data=encode(toolResult(arrived,"get_monitoring").get("alarms"));assertTrue(data.contains(event));assertFalse(data.contains(event+"-other"));
  assertThrows(PlatformException.class,()->agent.sceneResult(admin,room,object("token",action.get("token"),"arrived",true,"cabinetSceneModelKey",key)));
 }
 @Test void failedOrWrongArrivalNeverQueriesAlarms(){
  for(boolean arrived:Arrays.asList(false,true)){
   Map<?,?>action=(Map<?,?>)walk().get("clientAction");clearInvocations(model);
   Map<String,Object>receipt=object("token",action.get("token"),"type","visit","status",arrived?"SUCCEEDED":"FAILED","result","模拟回执","arrived",arrived,"cabinetSceneModelKey",arrived?"wrong":key);
   if(arrived)assertThrows(PlatformException.class,()->agent.sceneResult(admin,room,receipt));else assertTrue(agent.sceneResult(admin,room,receipt).get("reply").toString().contains("未确认完成"));verifyNoInteractions(model);
  }
 }
 @Test void cancelInvalidatesPendingWalkReceipt(){
  Map<?,?>action=(Map<?,?>)walk().get("clientAction");when(model.modelTools(anyList(),anyList())).thenReturn(say("取消后续请求"));chat("取消");clearInvocations(model);
  assertThrows(PlatformException.class,()->agent.sceneResult(admin,room,object("token",action.get("token"),"type","visit","status","SUCCEEDED","result","已到达","arrived",true,"cabinetSceneModelKey",key)));verifyNoInteractions(model);
 }
 @Test void currentAlarmListRefreshesSameSessionAfterNewAlarmAndRecovery(){
  assertTrue(encode(toolResult(query(null),"get_monitoring").get("alarms")).contains(event));String added=event+"-new";
  monitoring.saveRecord(room,"ALARM",added,object("id",added,"cabinetId",cabinet,"title","新温度告警","status","未处理","severity","critical","metric","temperature","value","35 ℃","threshold","≤ 28 ℃"));
  Map<String,Object>latest=toolResult(query(null),"get_monitoring");assertEquals(2,((List<?>)latest.get("alarms")).size());assertTrue(encode(latest).contains("35 ℃"));assertTrue(encode(latest).contains("≤ 28 ℃"));
  Map<String,Object>alarm=monitoring.records(room,"ALARM").stream().filter(v->added.equals(v.get("id"))).findFirst().get();alarm.put("status","已恢复");monitoring.saveRecord(room,"ALARM",added,alarm);
  Map<String,Object>recovered=toolResult(query(null),"get_monitoring");assertEquals(1,((List<?>)recovered.get("alarms")).size());assertFalse(encode(recovered.get("alarms")).contains(added));verify(model,atLeast(3)).modelTools(anyList(),anyList());
 }
 @Test void allAlarmsToolScopeOverridesSelectedSceneHint(){
  query(key);monitoring.saveRecord(room,"ALARM",event+"-unassigned",object("id",event+"-unassigned","title","机房告警","status","处理中"));
  when(model.modelTools(anyList(),anyList())).thenReturn(call("get_monitoring",object()),say("查询当前机房"));
  Map<String,Object>result=agent.chat(admin,room,object("text","查询当前所有告警","requestId",UUID.randomUUID().toString(),"sceneCabinet",key));
  assertTrue(encode(toolResult(result,"get_monitoring").get("alarms")).contains(event+"-unassigned"));assertEquals(2,((List<?>)toolResult(result,"get_monitoring").get("alarms")).size());
 }
 @Test void failedFreshQueryDoesNotReturnPreviousAlarmList(){
  query(null);MonitoringService unavailable=mock(MonitoringService.class);when(unavailable.records(room,"ALARM")).thenThrow(new IllegalStateException("database unavailable"));
  AssistantAgentService failed=new AssistantAgentService(db,json,model,console,unavailable);when(model.modelTools(anyList(),anyList())).thenReturn(call("get_monitoring",object()),say("工具查询失败"));
  Map<String,Object>result=failed.chat(admin,room,object("text","查询当前所有告警","requestId",UUID.randomUUID().toString()));Map<String,Object>tool=toolResult(result,"get_monitoring");assertEquals("ERROR",tool.get("status"));assertFalse(tool.containsKey("alarms"));assertFalse(encode(tool).contains(event));
 }
 @Test void explicitCabinetAllAlarmsKeepsScopeAndEmptyListClearsChoices(){
  monitoring.saveRecord(room,"ALARM",event+"-room",object("id",event+"-room","title","机房告警","status","未处理"));
  Map<String,Object>scoped=toolResult(query(key),"get_monitoring");assertEquals(1,((List<?>)scoped.get("alarms")).size());assertFalse(encode(scoped.get("alarms")).contains(event+"-room"));
  db.update("DELETE FROM sp_monitor_record WHERE room_id=? AND kind='ALARM'",room);Map<String,Object>empty=query(null);assertTrue(((List<?>)toolResult(empty,"get_monitoring").get("alarms")).isEmpty());assertTrue(((List<?>)((Map<?,?>)empty.get("context")).get("choices")).isEmpty());
 }
 @Test void alarmToolPreservesDataAndSelectionMapsToStableId(){
  String second=event+"-second";monitoring.saveRecord(room,"ALARM",second,object("id",second,"cabinetId",cabinet,"title","温度 | 异常","status","未处理","severity","critical","metric","temperature","value",38.0,"threshold","0 ~ 30","rule",object("unit","℃")));
  List<Map<String,Object>>alarms=(List<Map<String,Object>>)toolResult(query(key),"get_monitoring").get("alarms");assertEquals(2,alarms.size());assertEquals(2,alarms.stream().map(v->v.get("id")).distinct().count());
  Map<String,Object>data=alarms.stream().filter(v->second.equals(v.get("id"))).findFirst().get();assertEquals("温度 | 异常",data.get("title"));assertEquals("critical",data.get("severity"));assertEquals("0 ~ 30",data.get("threshold"));assertEquals("℃",((Map<?,?>)data.get("rule")).get("unit"));
  int index=0;for(int i=0;i<alarms.size();i++)if(second.equals(alarms.get(i).get("id")))index=i+1;
  when(model.modelTools(anyList(),anyList())).thenReturn(call("prepare_order",object("cabinetKey",key,"alarmId",second)));Map<String,Object>selected=chat("选择第"+index+"个");assertEquals(second,((Map<?,?>)((Map<?,?>)selected.get("pending")).get("draft")).get("alarmEvent"));
 }
 @Test void changedAlarmInvalidatesConfirmation(){
  Map<String,Object>prepared=prepareOrder();Map<String,Object>alarm=monitoring.records(room,"ALARM").get(0);alarm.put("status","已恢复");monitoring.saveRecord(room,"ALARM",event,alarm);
  assertEquals("REJECTED",confirmationStatus(confirm(prepared)));assertEquals(0,orderCount());
 }
 @Test void ambiguityRequiresSelectionAndDoesNotPickFirst(){
  monitoring.saveRecord(room,"ALARM",event+"-other",object("id",event+"-other","roomId",room,"cabinetId",cabinet,"title","另一温度告警","recordOrigin","DEMO","status","未处理"));
  when(model.modelTools(anyList(),anyList())).thenReturn(call("get_monitoring",object("cabinetKey",key)),call("prepare_order",object("cabinetKey",key,"alarmId",event)));
  Map<String,Object>result=chat("检查 "+key+" 并建单");assertNull(result.get("pending"));assertTrue(result.get("reply").toString().contains("多个候选"));assertEquals(0,orderCount());
  when(model.modelTools(anyList(),anyList())).thenReturn(call("prepare_order",object("cabinetKey",key,"alarmId",event)));
  Map<String,Object>chosen=chat("选择"+event);assertNotNull(chosen.get("pending"),chosen.toString());
  when(model.modelTools(anyList(),anyList())).thenReturn(call("prepare_order",object("cabinetKey",key,"alarmId",event+"-other")));
  Map<String,Object>second=chat("选择第2个");assertNotNull(second.get("pending"),second.toString());
  assertEquals(event+"-other",((Map<?,?>)((Map<?,?>)second.get("pending")).get("draft")).get("alarmEvent"));
 }
 @Test void crossRoomOrderAndUnauthorizedUserAreRejected(){
  assertThrows(PlatformException.class,()->agent.current(object("id",3,"role","MAINTAINER"),room));
  when(model.modelTools(anyList(),anyList())).thenReturn(call("prepare_assignment",object("orderId","WO-UNKNOWN","ownerAccountId",String.valueOf(person.get("id")))),say("该工单不存在。"));
  assertNull(chat("指派该工单").get("pending"));
 }
 @Test void boundedLoopAndSceneReceiptArePersisted(){
  when(model.modelTools(anyList(),anyList())).thenAnswer(inv->call("get_orders",object()));Map<String,Object>limited=chat("查询工单");assertTrue(limited.get("reply").toString().contains("上限"));verify(model,times(6)).modelTools(anyList(),anyList());
  when(model.modelTools(anyList(),anyList())).thenReturn(call("scene_action",object("action","visit","cabinetKey",key)));
  Map<String,Object>scene=chat("前往 "+key);Map<String,Object>action=(Map<String,Object>)scene.get("clientAction");assertNotNull(action);assertTrue(scene.get("reply").toString().contains("尚未确认"));
  when(model.modelTools(anyList(),anyList())).thenReturn(say("场景尚未接通，本次未到达。"));
  assertTrue(agent.sceneResult(admin,room,object("token",action.get("token"),"type","visit","status","FAILED","cabinetSceneModelKey",key,"arrived",false,"result","场景未接通，未执行巡检")).get("reply").toString().contains("未确认完成"));
  assertThrows(PlatformException.class,()->agent.sceneResult(admin,room,object("token",action.get("token"),"result","重复回执")));
 }
 @Test void staleConfirmationTokenAndOtherAccountCannotReusePending(){
  Map<String,Object>prepared=prepareOrder();Map<String,Object>old=confirmationBody(prepared);agent.dismissPending(admin,room,object("pendingToken",old.get("pendingToken")));prepareOrder();assertThrows(PlatformException.class,()->agent.confirm(admin,room,old));assertEquals(0,orderCount());
  Map<String,Object>otherActor=object("id",9000002L,"role","ADMIN");Map<String,Object>other=agent.current(otherActor,room);assertNull(other.get("pending"));assertTrue(((List<?>)other.get("messages")).isEmpty());assertThrows(PlatformException.class,()->agent.confirm(otherActor,room,old));
 }
 @Test void changedOwnerAndDisabledPersonInvalidateAssignment(){
  Map<String,Object>o=console.createOrder(admin,object("title","温度告警","device",key,"roomId",room,"alarmEvent",event));String id=o.get("id").toString();
  Map<String,Object>p=invokeTool("把工单 "+id+" 指派给"+person.get("userName"),"prepare_assignment",object("orderId",id,"ownerUserName",person.get("userName")));assertNotNull(p.get("pending"));console.action(admin,id,"assign",object("ownerAccountId",person.get("id")));assertEquals("REJECTED",confirmationStatus(confirm(p)));
  p=invokeTool("把工单 "+id+" 再指派给"+person.get("userName"),"prepare_assignment",object("orderId",id,"ownerUserName",person.get("userName")));assertNotNull(p.get("pending"));console.savePerson(admin,object("displayName","停用技师","enabled",false),((Number)person.get("id")).longValue());assertEquals("REJECTED",confirmationStatus(confirm(p)));
 }
 @Test void retestUsesExistingRulesAndHistoryIsReadOnly(){
  Map<String,Object>o=console.createOrder(admin,object("title","温度告警","device",key,"roomId",room,"alarmEvent",event));String id=o.get("id").toString();console.action(admin,id,"assign",object("ownerAccountId",person.get("id")));console.action(person,id,"start",object());console.action(person,id,"submit",object("cause","待验证原因","process","检查风道","result","申请验收"));
  Map<String,Object>prepared=invokeTool("为工单 "+id+" 准备复检","prepare_retest",object("orderId",id));assertNotNull(prepared.get("pending"));assertEquals("SUCCEEDED",confirmationStatus(confirm(prepared)));
  Map<String,Object>fresh=console.orders(admin).stream().filter(v->id.equals(v.get("id"))).findFirst().get();assertEquals("待复测",fresh.get("status"));assertNotNull(fresh.get("retestNote"));
  Map<String,Object>history=invokeTool("查询维修历史","get_history",object("cabinetKey",key));assertNotNull(toolResult(history,"get_history").get("history"));assertEquals(1,orderCount());assertEquals("待复测",console.orders(admin).stream().filter(v->id.equals(v.get("id"))).findFirst().get().get("status"));
 }
 @Test void changedSceneTargetCannotConfirmOldOrder(){
  Map<String,Object>prepared=prepareOrder();Map<String,Object>body=confirmationBody(prepared);String other=db.queryForObject("SELECT c.scene_model_key FROM sp_cabinet c JOIN sp_zone z ON z.id=c.zone_id WHERE z.room_id=? AND c.id<>? ORDER BY c.id LIMIT 1",String.class,room,cabinet);
  when(model.modelTools(anyList(),anyList())).thenReturn(say("文字确认不执行写入"));Map<String,Object>changed=agent.chat(admin,room,object("text","确认","requestId",UUID.randomUUID().toString(),"sceneCabinet",other));assertNull(changed.get("pending"));assertThrows(PlatformException.class,()->agent.confirm(admin,room,body));assertEquals(0,orderCount());
 }
 @Test void pageRefreshStartsEmptyAndOldConfirmationCannotBeUsed(){
  Map<String,Object>prepared=prepareOrder();Map<String,Object>body=confirmationBody(prepared);Map<String,Object>freshPage=new LinkedHashMap<>(admin);freshPage.put("assistantPageId",UUID.randomUUID().toString());assertTrue(((List<?>)agent.current(freshPage,room).get("messages")).isEmpty());assertNull(agent.current(freshPage,room).get("pending"));assertThrows(PlatformException.class,()->agent.confirm(freshPage,room,body));assertEquals(0,orderCount());
  assertNotNull(agent.current(admin,room).get("pending"));agent.end(admin,room);assertTrue(((List<?>)agent.current(admin,room).get("messages")).isEmpty());assertNull(agent.current(admin,room).get("pending"));assertThrows(PlatformException.class,()->agent.confirm(admin,room,body));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM console_assistant_session WHERE account_id=? AND room_id=?",Integer.class,admin.get("id"),room));
 }
 @Test void crossOriginPageSessionHeaderIsAllowed()throws Exception{
  org.springframework.mock.web.MockHttpServletResponse response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/assistant/rooms/"+room+"/session")
   .header("Origin","http://localhost:5174").header("Access-Control-Request-Method","GET").header("Access-Control-Request-Headers","authorization,content-type,x-assistant-page")).andReturn().getResponse();
  assertEquals(204,response.getStatus());assertTrue(response.getHeader("Access-Control-Allow-Headers").toLowerCase().contains("x-assistant-page"));
 }
 @Test void streamingEndpointSendsDeltasAndKeepsOneFinalReplyInMemory()throws Exception{
  String user="stream-"+UUID.randomUUID();console.savePerson(admin,object("userName",user,"displayName","流式测试","password","AgentTest123!"),null);db.update("UPDATE console_account SET role='ADMIN' WHERE user_name=?",user);
  Map<String,Object>login=console.login(object("userName",user,"password","AgentTest123!"));
  when(model.modelToolsStream(anyList(),anyList(),any())).thenAnswer(inv->{Consumer<String>sink=inv.getArgument(2);sink.accept("第一段。");sink.accept("第二段。");return say("第一段。第二段。");});
  String body=json.writeValueAsString(object("text","介绍巡检流程","requestId",UUID.randomUUID().toString(),"stream",true));
  org.springframework.mock.web.MockHttpServletResponse response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/assistant/rooms/"+room+"/chat").header("Authorization","Bearer "+login.get("token")).contentType("application/json").content(body)).andReturn().getResponse();
  assertEquals(200,response.getStatus());assertTrue(response.getContentType().startsWith("text/event-stream"));String events=response.getContentAsString(StandardCharsets.UTF_8);
  assertTrue(events.indexOf("第一段。")<events.indexOf("第二段。"));assertTrue(events.contains("event: delta"));assertTrue(events.contains("event: done"));
  List<Map<String,Object>>visible=(List<Map<String,Object>>)agent.current((Map<String,Object>)login.get("user"),room).get("messages");assertEquals(1,visible.stream().filter(m->"第一段。第二段。".equals(m.get("text"))).count());
 }
 @Test void interruptedStreamIsMarkedAndDisconnectStopsToolExecution(){
  when(model.modelToolsStream(anyList(),anyList(),any())).thenAnswer(inv->{((Consumer<String>)inv.getArgument(2)).accept("回答的一部分");throw new PlatformException(502,"STREAM","broken");});
  Map<String,Object>result=agent.stream(admin,room,object("text","解释流程","requestId",UUID.randomUUID().toString()),false,event->{});
  assertEquals(true,result.get("interrupted"));assertTrue(result.get("reply").toString().contains("回答的一部分"));assertTrue(result.get("reply").toString().contains("回答中断"));
  reset(model);
  when(model.modelToolsStream(anyList(),anyList(),any())).thenAnswer(inv->{((Consumer<String>)inv.getArgument(2)).accept("准备查询");return call("prepare_order",object("alarmId",event,"cabinetKey",key));});
  assertThrows(UncheckedIOException.class,()->agent.stream(admin,room,object("text","查询告警并准备工单","requestId",UUID.randomUUID().toString()),false,e->{if("delta".equals(e.get("type")))throw new UncheckedIOException(new IOException("closed"));}));
  assertEquals(0,orderCount());assertNull(agent.current(admin,room).get("pending"));
  reset(model);when(model.modelToolsStream(anyList(),anyList(),any())).thenReturn(call("prepare_order",object("alarmId",event,"cabinetKey",key)));
  assertThrows(UncheckedIOException.class,()->agent.stream(admin,room,object("text","查询告警并准备工单","requestId",UUID.randomUUID().toString()),false,e->{if("status".equals(e.get("type"))&&((Map<?,?>)e.get("data")).get("message").toString().contains("重复工单"))throw new UncheckedIOException(new IOException("closed"));}));
  assertNull(agent.current(admin,room).get("pending"));
 }
}
