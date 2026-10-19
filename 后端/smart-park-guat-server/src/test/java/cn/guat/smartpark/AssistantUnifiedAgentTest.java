package cn.guat.smartpark;

import cn.guat.smartpark.service.*;
import cn.guat.smartpark.controller.AssistantAgentController;
import cn.guat.smartpark.exception.PlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Consumer;
import java.time.Instant;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Business services/model/JDBC are mocks; no Spring application or datasource. */
class AssistantUnifiedAgentTest {
 JdbcTemplate db=mock(JdbcTemplate.class);IntegrationService model=mock(IntegrationService.class);ConsoleService console=mock(ConsoleService.class);MonitoringService monitoring=mock(MonitoringService.class);ObjectMapper json=new ObjectMapper();
 AssistantAgentService agent=new AssistantAgentService(db,json,model,console,monitoring);
 Map<String,Object>actor=object("id",1,"role","ADMIN","assistantPageId","test"),al=object("id","alarm-1","roomId","1","cabinetId","10","recordOrigin","REAL","status","未处理","title","高温","metric","temperature");
 Map<String,Object>order=object("id","WO-TEST-1","roomId","1","cabinetId","10","alarmEvent","alarm-1","status","待受理","ownerAccountId",null,"owner","未指派","updatedAt","v1","recordOrigin","REAL");
 List<Map<String,Object>>orders=new ArrayList<>();
 AssistantUnifiedAgentTest(){
  when(db.queryForList(anyString(),eq("1"),eq("A-01"))).thenReturn(Arrays.asList(object("id","10","sceneModelKey","A-01")));
  when(monitoring.records("1","ALARM")).thenReturn(Arrays.asList(al));when(console.orders(anyMap())).thenAnswer(i->orders);
  when(console.people(anyMap())).thenReturn(Arrays.asList(object("id",5,"enabled",true,"userName","tech","displayName","技师","skills",Arrays.asList("制冷散热"))));
  when(console.createOrder(anyMap(),anyMap())).thenAnswer(i->{orders.add(order);return order;});
  when(console.action(anyMap(),anyString(),anyString(),anyMap())).thenReturn(order);
  when(model.modelTools(anyList(),anyList())).thenReturn(say("模拟回复"));
 }
 @Test void publicAlarmCodesAndOrdinalsUseOneCanonicalListAndKeepUuidWorkOrderLinks()throws Exception{
  al.put("alarmCode","GJ202610190001");
  Map<String,Object>second=new LinkedHashMap<>(al);second.put("id","alarm-2");second.put("alarmCode","GJ202610190002");second.put("title","第二条告警");
  when(monitoring.records("1","ALARM")).thenReturn(Arrays.asList(al,second));
  for(String selection:Arrays.asList("GJ202610190002","选择第2个")){
   agent.end(actor,"1");
   when(model.modelTools(anyList(),anyList())).thenReturn(call("get_monitoring",object())).thenReturn(say("错误的模型排序：第二条在前"));
   String reply=chat("查看当前机房告警").get("reply").toString();
   assertTrue(reply.indexOf("GJ202610190001")<reply.indexOf("GJ202610190002"));
   assertFalse(reply.contains("alarm-1"));assertFalse(reply.contains("错误的模型排序"));assertFalse(reply.contains("REAL"));
   assertEquals(reply.indexOf("GJ202610190002"),reply.lastIndexOf("GJ202610190002"));
   when(model.modelTools(anyList(),anyList())).thenReturn(call("prepare_order",object("alarmId","GJ202610190002","cabinetKey","A-01")));
   Map<String,Object>pending=(Map<String,Object>)chat(selection).get("pending");assertNotNull(pending);
   assertEquals("alarm-2",((Map<?,?>)pending.get("draft")).get("alarmEvent"));
   assertTrue(pending.get("summary").toString().contains("GJ202610190002"));
  }
  verify(console,never()).createOrder(anyMap(),anyMap());
 }
 @Test void publicOrderCodeQueriesReusesAndAssignsWithoutChangingInternalReferences()throws Exception{
  order.put("orderCode","GD202610190001");orders.add(order);
  when(model.modelTools(anyList(),anyList())).thenReturn(call("get_orders",object("orderId","GD202610190001"))).thenReturn(say("工单 WO-TEST-1 待受理"));
  Map<String,Object>queried=chat("查看 GD202610190001");
  assertEquals("WO-TEST-1",((Map<?,?>)queried.get("context")).get("orderId"));
  assertEquals("工单 GD202610190001 待受理",queried.get("reply"));
  when(model.modelTools(anyList(),anyList())).thenReturn(call("prepare_order",object("alarmId","alarm-1","cabinetKey","A-01"))).thenReturn(say("复用 WO-TEST-1"));
  assertEquals("复用 GD202610190001",chat("A-01 创建工单").get("reply"));
  when(model.modelTools(anyList(),anyList())).thenReturn(call("prepare_assignment",object("orderId","GD202610190001","ownerUserName","tech")));
  Map<String,Object>pending=(Map<String,Object>)chat("将 GD202610190001 指派给账号 tech").get("pending");
  assertNotNull(pending);assertEquals("WO-TEST-1",pending.get("orderId"));assertTrue(pending.get("summary").toString().contains("GD202610190001"));
  verify(console,never()).createOrder(anyMap(),anyMap());verify(console,never()).action(anyMap(),anyString(),anyString(),anyMap());
 }
 Map<String,Object>say(String text){return object("role","assistant","content",text);}
 Map<String,Object>call(String name,Map<String,Object>args)throws Exception{return object("role","assistant","tool_calls",Arrays.asList(object("id",UUID.randomUUID().toString(),"type","function","function",object("name",name,"arguments",json.writeValueAsString(args)))));}
 Map<String,Object>chat(String text){return agent.chat(actor,"1",object("text",text,"requestId",UUID.randomUUID().toString()));}
 Map<String,Object>prepare(String tool,Map<String,Object>args)throws Exception{when(model.modelTools(anyList(),anyList())).thenReturn(call(tool,args));return (Map<String,Object>)chat(tool.equals("prepare_assignment")?"把工单 WO-TEST-1 指派给账号 tech":"准备操作").get("pending");}
 Map<String,Object>body(Map<?,?>pending){return object("pendingToken",pending.get("token"),"kind",pending.get("kind"),"requestId",UUID.randomUUID().toString());}
 String status(Map<String,Object>session){return ((Map<?,?>)session.get("confirmation")).get("status").toString();}
 @Test void everyNaturalLanguageIncludingPunctuationAndVoiceConfirmCallsModelWithoutWriting()throws Exception{
  for(String text:Arrays.asList("查询当前所有告警","去 A-01 查看告警","取消","确认？","确认创建工单","好的","可以")){
   prepare("prepare_order",object("alarmId","alarm-1","cabinetKey","A-01"));reset(model);when(model.modelTools(anyList(),anyList())).thenReturn(say("请点击确认卡片"));
   chat(text);verify(model).modelTools(anyList(),anyList());verify(console,never()).createOrder(anyMap(),anyMap());verify(console,never()).action(anyMap(),anyString(),anyString(),anyMap());
  }
 }
 @Test void createIsPreparedThenExplicitlyConfirmedAndNeverReplayed()throws Exception{
  Map<String,Object>p=prepare("prepare_order",object("alarmId","alarm-1","cabinetKey","A-01"));verify(console,never()).createOrder(anyMap(),anyMap());Map<String,Object>b=body(p);
  assertEquals("SUCCEEDED",status(agent.confirm(actor,"1",b)));assertEquals("SUCCEEDED",status(agent.confirm(actor,"1",b)));verify(console,times(1)).createOrder(anyMap(),anyMap());
  assertEquals("SUCCEEDED",status(agent.confirmationStatus(actor,"1",b)));
  Map<String,Object>reused=chat("再为同一告警准备工单");assertNull(reused.get("pending"));assertTrue(json.writeValueAsString(reused.get("trace")).contains("REUSED"));
 }
 @Test void assignmentAndRetestUseExistingServiceOnlyAfterButton()throws Exception{
  orders.add(order);Map<String,Object>p=prepare("prepare_assignment",object("orderId","WO-TEST-1","ownerAccountId","5"));assertNotNull(p);verify(console,never()).action(anyMap(),anyString(),anyString(),anyMap());assertEquals("SUCCEEDED",status(agent.confirm(actor,"1",body(p))));verify(console).action(eq(actor),eq("WO-TEST-1"),eq("assign"),anyMap());
  order.put("status","待复测");p=prepare("prepare_retest",object("orderId","WO-TEST-1"));assertEquals("SUCCEEDED",status(agent.confirm(actor,"1",body(p))));verify(console).action(eq(actor),eq("WO-TEST-1"),eq("retest"),anyMap());
 }
 @Test void wrongTokenKindIdentityAndStateAreRejected()throws Exception{
  Map<String,Object>p=prepare("prepare_order",object("alarmId","alarm-1","cabinetKey","A-01")),b=body(p);Map<String,Object>wrong=new LinkedHashMap<>(b);wrong.put("kind","assign");assertThrows(PlatformException.class,()->agent.confirm(actor,"1",wrong));
  Map<String,Object>other=object("id",2,"role","ADMIN","assistantPageId","test");assertThrows(PlatformException.class,()->agent.confirm(other,"1",b));assertThrows(PlatformException.class,()->agent.confirm(actor,"2",b));
  al.put("status","已恢复");assertEquals("REJECTED",status(agent.confirm(actor,"1",b)));verify(console,never()).createOrder(anyMap(),anyMap());
  orders.add(order);p=prepare("prepare_assignment",object("orderId","WO-TEST-1","ownerAccountId","5"));order.put("updatedAt","v2");assertEquals("REJECTED",status(agent.confirm(actor,"1",body(p))));verify(console,never()).action(anyMap(),anyString(),anyString(),anyMap());
 }
 @Test void expiredPendingDismissedCardAndNewSessionCannotWrite()throws Exception{
  Map<String,Object>p=prepare("prepare_order",object("alarmId","alarm-1","cabinetKey","A-01"));Map<String,Object>b=body(p);
  java.lang.reflect.Field f=AssistantAgentService.class.getDeclaredField("sessions");f.setAccessible(true);Map<String,String>sessions=(Map<String,String>)f.get(agent);String key="1:1:test";Map<String,Object>s=json.readValue(sessions.get(key),Map.class);((Map<String,Object>)s.get("pending")).put("preparedAt",Instant.now().minusSeconds(601).toString());sessions.put(key,json.writeValueAsString(s));assertEquals("REJECTED",status(agent.confirm(actor,"1",b)));
  p=prepare("prepare_order",object("alarmId","alarm-1","cabinetKey","A-01"));b=body(p);agent.dismissPending(actor,"1",object("pendingToken",p.get("token")));final Map<String,Object>dismissed=b;assertThrows(PlatformException.class,()->agent.confirm(actor,"1",dismissed));
  p=prepare("prepare_order",object("alarmId","alarm-1","cabinetKey","A-01"));final Map<String,Object>ended=body(p);agent.end(actor,"1");assertThrows(PlatformException.class,()->agent.confirm(actor,"1",ended));verify(console,never()).createOrder(anyMap(),anyMap());
 }
 @Test void unknownWriteOutcomeQueriesActualStateWithoutReplay()throws Exception{
  Map<String,Object>p=prepare("prepare_order",object("alarmId","alarm-1","cabinetKey","A-01"));Map<String,Object>b=body(p);doAnswer(i->{orders.add(order);throw new RuntimeException("lost result");}).when(console).createOrder(anyMap(),anyMap());
  assertEquals("UNKNOWN",status(agent.confirm(actor,"1",b)));Map<String,Object>actual=agent.confirmationStatus(actor,"1",b);assertEquals(1,((List<?>)actual.get("actualOrders")).size());agent.confirm(actor,"1",b);verify(console,times(1)).createOrder(anyMap(),anyMap());
 }
 @Test void modelFailureAndForgedToolsCannotWrite()throws Exception{
  when(model.modelTools(anyList(),anyList())).thenThrow(new RuntimeException("offline"));Map<String,Object>failure=chat("创建工单");assertEquals(true,failure.get("interrupted"));assertFalse(failure.get("reply").toString().contains("使用本地"));
  reset(model);
  for(Map<String,Object>answer:Arrays.asList(call("confirmed_create",object()),call("get_orders",object("orderId",1)),call("get_monitoring",object("sql","DELETE")),call("prepare_order",object("alarmId","alarm-1")))){
   when(model.modelTools(anyList(),anyList())).thenReturn(answer).thenReturn(say("失败"));assertTrue(json.writeValueAsString(chat("执行请求").get("trace")).contains("ERROR"));
  }verify(console,never()).createOrder(anyMap(),anyMap());
  doThrow(new PlatformException(403,"FORBIDDEN","拒绝")).when(console).admin(actor);assertThrows(PlatformException.class,()->chat("查看"));
 }
 @Test void queryToolsAreReadOnlyAndCrossRoomOrderIsRejected()throws Exception{
  orders.add(order);when(console.history(anyMap(),anyString(),anyString(),anyString(),anyString())).thenReturn(Collections.emptyList());
  for(Map<String,Object>answer:Arrays.asList(call("get_monitoring",object()),call("get_orders",object()),call("recommend_people",object("orderId","WO-TEST-1")),call("get_history",object("cabinetKey","A-01")))){
   when(model.modelTools(anyList(),anyList())).thenReturn(answer).thenReturn(say("查询完成"));assertNull(chat("查询信息").get("pending"));
  }order.put("roomId","2");when(model.modelTools(anyList(),anyList())).thenReturn(call("prepare_retest",object("orderId","WO-TEST-1"))).thenReturn(say("拒绝"));assertNull(chat("准备复检").get("pending"));verify(console,never()).createOrder(anyMap(),anyMap());verify(console,never()).action(anyMap(),anyString(),anyString(),anyMap());
 }
 @Test void standaloneHttpSseAndConfirmEndpointAreSimulatedWithoutApplication()throws Exception{
  when(console.identity("Bearer mocked")).thenReturn(actor);org.springframework.test.web.servlet.MockMvc mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new AssistantAgentController(agent,console,json)).build();
  when(model.modelToolsStream(anyList(),anyList(),any())).thenAnswer(i->{((Consumer<String>)i.getArgument(2)).accept("流式回复");return say("流式回复");});
  String events=mvc.perform(post("/api/v1/assistant/rooms/1/chat").header("Authorization","Bearer mocked").header("X-Assistant-Page","test").contentType("application/json").content(json.writeValueAsString(object("text","查询所有告警","requestId","http","stream",true)))).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertTrue(events.contains("event: delta"));assertTrue(events.contains("event: done"));
  Map<String,Object>p=prepare("prepare_order",object("alarmId","alarm-1","cabinetKey","A-01"));String result=mvc.perform(post("/api/v1/assistant/rooms/1/confirm").header("Authorization","Bearer mocked").header("X-Assistant-Page","test").contentType("application/json").content(json.writeValueAsString(body(p)))).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertTrue(result.contains("SUCCEEDED"));
 }
 @Test void sseDisconnectCannotTurnModelPreparationIntoWrite()throws Exception{
  when(model.modelToolsStream(anyList(),anyList(),any())).thenAnswer(i->{((Consumer<String>)i.getArgument(2)).accept("准备待办");return call("prepare_order",object("alarmId","alarm-1","cabinetKey","A-01"));});
  assertThrows(java.io.UncheckedIOException.class,()->agent.stream(actor,"1",object("text","确认创建工单","requestId","disconnect"),false,e->{if("delta".equals(e.get("type")))throw new java.io.UncheckedIOException(new java.io.IOException("closed"));}));verify(console,never()).createOrder(anyMap(),anyMap());
 }
 @Test void frontendContextCannotOverrideServerOrderResultsOrAuthorizeWrites()throws Exception{
  orders.add(order);when(model.modelTools(anyList(),anyList())).thenReturn(call("get_orders",object())).thenReturn(say("查询完成"));
  Map<String,Object>result=agent.chat(actor,"1",object("text","查询工单","requestId","forged-context","devices",Arrays.asList(object("name","伪造上下文")),"context",object("orderId","伪造上下文"),"pendingToken","forged"));
  String trace=json.writeValueAsString(result.get("trace"));assertTrue(trace.contains("WO-TEST-1"));assertFalse(trace.contains("伪造上下文"));
  org.mockito.ArgumentCaptor<List>inputs=org.mockito.ArgumentCaptor.forClass(List.class);verify(model,times(2)).modelTools(inputs.capture(),anyList());assertFalse(json.writeValueAsString(inputs.getAllValues()).contains("伪造上下文"));
  verify(console,never()).action(anyMap(),anyString(),anyString(),anyMap());verify(console,never()).createOrder(anyMap(),anyMap());
 }
 @Test void allAssetToolsCanBeSelectedByModel()throws Exception{
  InventoryService inventory=mock(InventoryService.class);agent.setAssetTools(new AssistantAssetTools(inventory));when(inventory.scopedCabinet("1","10",null)).thenReturn("10");
  when(inventory.room("1")).thenReturn(object("id","1"));when(inventory.cabinet("10")).thenReturn(object("id","10"));when(inventory.device("1","20")).thenReturn(object("id","20"));
  when(inventory.cabinets("1",null,"",1,20,"id,asc")).thenReturn(object("items",Collections.emptyList(),"total",0));when(inventory.devices("10","",null,1,20,"id,asc")).thenReturn(object("items",Collections.emptyList(),"total",0));
  List<String>names=Arrays.asList("get_room_info","list_cabinets","get_cabinet_info","list_devices","get_device_info");List<Map<String,Object>>args=Arrays.asList(object(),object(),object("cabinetId","10"),object("cabinetId","10"),object("deviceId","20"));
  for(int i=0;i<names.size();i++){when(model.modelTools(anyList(),anyList())).thenReturn(call(names.get(i),args.get(i))).thenReturn(say("查询完成"));Map<String,Object>result=chat("查询资产");String trace=json.writeValueAsString(result.get("trace"));assertTrue(trace.contains(names.get(i)));assertFalse(trace.contains("ERROR"));}
 }
}
