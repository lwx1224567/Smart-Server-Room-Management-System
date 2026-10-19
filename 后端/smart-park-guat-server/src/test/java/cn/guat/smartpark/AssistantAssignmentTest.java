package cn.guat.smartpark;

import cn.guat.smartpark.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import java.util.*;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pure mocks. Never starts Spring or runs schema initialization. */
class AssistantAssignmentTest {
 final JdbcTemplate db=mock(JdbcTemplate.class);
 final IntegrationService model=mock(IntegrationService.class);
 final ConsoleService console=mock(ConsoleService.class);
 final MonitoringService monitoring=mock(MonitoringService.class);
 final ObjectMapper json=new ObjectMapper();
 final AssistantAgentService agent=new AssistantAgentService(db,json,model,console,monitoring);
 final Map<String,Object> actor=object("id",1,"role","ADMIN","assistantPageId","assign-test");
 final String id="WO-2CD26BC2-377";
 final Map<String,Object> order=object("id",id,"roomId","1","alarmEvent","alarm-1","status","待受理","updatedAt","v1");
 final List<Map<String,Object>> people=new ArrayList<>(Arrays.asList(person(5,"user1","李四"),person(6,"user10","王五")));
 AssistantAssignmentTest(){
  when(console.orders(anyMap())).thenReturn(Arrays.asList(order,object("id","WO-OTHER","roomId","1","status","待受理")));
  when(console.people(anyMap())).thenAnswer(i->people);
  when(monitoring.records("1","ALARM")).thenReturn(Arrays.asList(object("id","alarm-1","status","未处理","metric","temperature")));
  when(console.action(anyMap(),anyString(),anyString(),anyMap())).thenReturn(order);
 }
 static Map<String,Object> person(int id,String account,String name){return object("id",id,"userName",account,"displayName",name,"enabled",true,"role","MAINTAINER","skills",Arrays.asList("制冷散热"));}
 Map<String,Object> turn(String text,String tool,Map<String,Object>args)throws Exception{
  Map<String,Object>call=object("role","assistant","tool_calls",Arrays.asList(object("id",UUID.randomUUID().toString(),"type","function","function",object("name",tool,"arguments",json.writeValueAsString(args)))));
  when(model.modelTools(anyList(),anyList())).thenReturn(call).thenReturn(object("role","assistant","content","查询完成"));
  return agent.chat(actor,"1",object("text",text,"requestId",UUID.randomUUID().toString()));
 }
 void recommend()throws Exception{turn("查看工单","get_orders",object());turn("为工单 "+id+" 推荐人员","recommend_people",object("orderId",id));}
 Map<String,Object> assign(String text,Map<String,Object>fields)throws Exception{Map<String,Object>a=object("orderId",id);a.putAll(fields);return turn(text,"prepare_assignment",a);}
 void noWrites(){verify(console,never()).action(anyMap(),anyString(),anyString(),anyMap());verify(console,never()).createOrder(anyMap(),anyMap());verifyNoInteractions(db);}
 @Test void exactAccountInsideSentenceWinsOverPrefixAndOldOrderCandidates()throws Exception{
  recommend();Map<String,Object>r=assign("把工单 "+id+" 指派给user1去处理",object("ownerAccountId","5"));
  assertNotNull(r.get("pending"),json.writeValueAsString(r.get("trace")));assertTrue(r.get("reply").toString().contains("李四"));noWrites();
 }
 @Test void embeddedOrdinalUsesOrderedRecommendations()throws Exception{
  recommend();Map<String,Object>r=assign("把工单 "+id+" 指派给第一个去处理",object("ownerAccountId","5"));
  assertNotNull(r.get("pending"),json.writeValueAsString(r.get("trace")));noWrites();
 }
 @Test void usernameAndUniqueDisplayNameAndStableIdAreExact()throws Exception{
  for(Map<String,Object>fields:Arrays.asList(object("ownerUserName","user1"),object("ownerDisplayName","李四"),object("ownerAccountId","5"))){
   String name=fields.containsKey("ownerUserName")?"user1":fields.containsKey("ownerDisplayName")?"李四":"用户ID 5";
   assertNotNull(assign("把工单 "+id+" 指派给"+name+"去处理",fields).get("pending"));
  }noWrites();
 }
 @Test void duplicatesUnknownAccountsAndInventedIdsNeedClarification()throws Exception{
  people.get(1).put("displayName","李四");
  Map<String,Object>r=assign("把工单 "+id+" 指派给李四",object("ownerDisplayName","李四"));assertNull(r.get("pending"));assertTrue(r.get("reply").toString().contains("user10"));
  assertNull(assign("把工单 "+id+" 指派给missing",object("ownerUserName","missing")).get("pending"));
  assertNull(assign("把工单 "+id+" 指派给user1",object("ownerAccountId","999")).get("pending"));
  assertNotNull(assign("把工单 "+id+" 指派给user1",object("ownerUserName","user1")).get("pending"));noWrites();
 }
 @Test void disabledAndWrongRoleAndClosedOrderCannotPrepare()throws Exception{
  people.get(0).put("enabled",false);assertNull(assign("把工单 "+id+" 指派给user1",object("ownerUserName","user1")).get("pending"));
  people.get(0).put("enabled",true);people.get(0).put("role","ADMIN");assertNull(assign("把工单 "+id+" 指派给user1",object("ownerUserName","user1")).get("pending"));
  people.get(0).put("role","MAINTAINER");order.put("status","已完成");assertNull(assign("把工单 "+id+" 指派给user1",object("ownerUserName","user1")).get("pending"));noWrites();
 }
 @Test void ordinalNeedsFreshSameOrderSamePageAndUnchangedState()throws Exception{
  assertNull(assign("把工单 "+id+" 指派给第一个",object("recommendationIndex",1)).get("pending"));recommend();
  order.put("updatedAt","v2");assertNull(assign("把工单 "+id+" 指派给第一个",object("recommendationIndex",1)).get("pending"));recommend();
  turn("查看 WO-OTHER","get_orders",object("orderId","WO-OTHER"));
  assertNull(assign("把工单 "+id+" 指派给第一个",object("recommendationIndex",1)).get("pending"));recommend();
  actor.put("assistantPageId","other-page");assertNull(assign("把工单 "+id+" 指派给第一个",object("recommendationIndex",1)).get("pending"));noWrites();
 }
 @Test void expiredOrdinalCannotBeUsed()throws Exception{
  recommend();java.lang.reflect.Field field=AssistantAgentService.class.getDeclaredField("sessions");field.setAccessible(true);
  Map<String,String>sessions=(Map<String,String>)field.get(agent);String key="1:1:assign-test";Map<String,Object>s=json.readValue(sessions.get(key),Map.class);
  ((Map<String,Object>)s.get("context")).put("personPreparedAt",java.time.Instant.now().minusSeconds(601).toString());sessions.put(key,json.writeValueAsString(s));
  assertNull(assign("把工单 "+id+" 指派给第一个",object("recommendationIndex",1)).get("pending"));noWrites();
 }
 @Test void missingSelectionAndConflictingSelectorsDoNotGuess()throws Exception{
  recommend();assertNull(assign("把工单 "+id+" 指派出去",object()).get("pending"));
  assertNull(assign("把工单 "+id+" 指派给user1",object("ownerUserName","user10")).get("pending"));
  assertNull(assign("把工单 "+id+" 指派给第一个",object("recommendationIndex",2)).get("pending"));noWrites();
  assertNull(assign("把工单 "+id+" 指派给user1也就是第二个",object("recommendationIndex",2)).get("pending"));noWrites();
 }
 @Test void staleOrderCandidatesDoNotBlockExplicitUniqueOrder()throws Exception{
  turn("查看工单","get_orders",object());turn("推荐人员","recommend_people",object("orderId",id));
  assertNotNull(assign("把工单 "+id+" 指派给user1",object("ownerUserName","user1")).get("pending"));
  assertNull(assign("把工单 WO-OTHER 指派给user1",object("ownerUserName","user1")).get("pending"));noWrites();
 }
 @Test void repeatedPreparationAndVoiceConfirmNeverWriteAndButtonReplaysOnce()throws Exception{
  Map<String,Object>r=assign("把工单 "+id+" 指派给user1",object("ownerUserName","user1"));assertNotNull(r.get("pending"));
  Map<String,Object>repeated=assign("把工单 "+id+" 指派给user1",object("ownerUserName","user1"));assertNotNull(repeated.get("pending"));noWrites();
  when(model.modelTools(anyList(),anyList())).thenReturn(object("role","assistant","content","请点击确认卡片"));agent.chat(actor,"1",object("text","确认","requestId","voice-confirm"));noWrites();
  r=assign("把工单 "+id+" 指派给user1",object("ownerUserName","user1"));Map<?,?>p=(Map<?,?>)r.get("pending");
  Map<String,Object>b=object("pendingToken",p.get("token"),"kind","assign","requestId","button");agent.confirm(actor,"1",b);agent.confirm(actor,"1",b);
  verify(console,times(1)).action(eq(actor),eq(id),eq("assign"),anyMap());
 }
}
