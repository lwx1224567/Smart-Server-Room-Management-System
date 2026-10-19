package cn.guat.smartpark;

import cn.guat.smartpark.entity.*;
import cn.guat.smartpark.mapper.InventoryMapper;
import cn.guat.smartpark.service.*;
import cn.guat.smartpark.exception.PlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import java.util.*;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** No Spring context, database connection, network or lifecycle callbacks. */
class AssistantPriorityAlarmsTest {
 MonitoringService monitoring=mock(MonitoringService.class);InventoryMapper inventory=mock(InventoryMapper.class);
 AssistantPriorityAlarms priority=new AssistantPriorityAlarms(monitoring,inventory);
 AssistantPriorityAlarmsTest(){Cabinet c=new Cabinet();c.setId(10L);c.setName("机柜一");c.setSceneModelKey("A-01");when(inventory.cabinets(1L)).thenReturn(Arrays.asList(c));when(inventory.devices(1L)).thenReturn(Collections.emptyList());}
 Map<String,Object>alarm(String id,String level){return object("id",id,"roomId","1","cabinetId","10","severity",level,"title",id,"status","未处理","recordOrigin","REAL","source","collector");}
 void records(Map<String,Object>...values){when(monitoring.records("1","ALARM")).thenReturn(Arrays.asList(values));}
 List<Map<String,Object>>items(Map<String,Object>result){return (List<Map<String,Object>>)result.get("items");}
 @Test void severityOrderAndSourcesMatchExistingPolicy(){
  records(alarm("z","unknown"),alarm("i","info"),alarm("n","normal"),alarm("w","warning"),alarm("c","critical"));
  Map<String,Object>result=priority.execute("1",object());List<Map<String,Object>>out=items(result);
  assertEquals(Arrays.asList("c","w","n","i","z"),Arrays.asList(out.get(0).get("id"),out.get(1).get("id"),out.get(2).get("id"),out.get(3).get("id"),out.get(4).get("id")));
  assertEquals("严重告警",out.get(0).get("severityLabel"));assertEquals("collector",out.get(0).get("source"));assertEquals("REAL",out.get(0).get("recordOrigin"));assertEquals(false,result.get("requiresSelection"));
 }
 @Test void tiesRemainAmbiguousEvenWhenLimitIsOne(){
  records(alarm("b","critical"),alarm("a","critical"));Map<String,Object>result=priority.execute("1",object("limit",1));
  assertEquals("a",items(result).get(0).get("id"));assertEquals(2L,result.get("highestPriorityCount"));assertEquals(true,result.get("requiresSelection"));assertEquals(true,result.get("hasMore"));
 }
 @Test void rejectsForeignScopeAndInvalidArguments(){
  for(Map<String,Object>args:Arrays.asList(object("roomId","2"),object("limit",0),object("limit",101),object("limit",1.2),object("limit","1"),object("limit",null),object("cabinetKey",123),object("cabinetKey","")))assertThrows(PlatformException.class,()->priority.execute("1",args));
  assertThrows(PlatformException.class,()->priority.execute("1",object("cabinetKey","B-01")));
  Map<String,Object>foreign=alarm("x","critical");foreign.put("roomId","2");records(foreign);assertThrows(PlatformException.class,()->priority.execute("1",object()));
 }
 @Test void unmappedAndInconsistentDeviceNeverProduceSceneTargets(){
  Map<String,Object>missing=alarm("missing","critical");missing.put("cabinetId","99");Map<String,Object>inconsistent=alarm("bad-device","warning");inconsistent.put("deviceId","999");records(missing,inconsistent);
  for(Map<String,Object>item:items(priority.execute("1",object()))){assertEquals(false,item.get("inspectable"));assertNull(item.get("sceneTarget"));assertNull(item.get("cabinetKey"));}
 }
 @Test void deviceOnlyAlarmResolvesThroughCurrentRoomInventory(){
  Device d=new Device();d.setId(20L);d.setCabinetId(10L);when(inventory.devices(1L)).thenReturn(Arrays.asList(d));Map<String,Object>a=alarm("device-alarm","warning");a.remove("cabinetId");a.put("deviceId","20");records(a);
  Map<String,Object>item=items(priority.execute("1",object("cabinetKey","A-01"))).get(0);assertEquals("10",item.get("cabinetId"));assertEquals(true,item.get("inspectable"));
 }
 @Test void emptyRecoveredAndDuplicateMappingsAreSafe(){
  records();assertEquals(0,priority.execute("1",object()).get("total"));Map<String,Object>recovered=alarm("old","critical");recovered.put("status","已恢复");records(recovered);assertEquals(0,priority.execute("1",object()).get("total"));
  Cabinet duplicate=new Cabinet();duplicate.setId(11L);duplicate.setSceneModelKey("A-01");List<Cabinet>cs=new ArrayList<>(inventory.cabinets(1L));cs.add(duplicate);when(inventory.cabinets(1L)).thenReturn(cs);records(alarm("active","critical"));assertNull(items(priority.execute("1",object())).get(0).get("sceneTarget"));
 }
 Map<String,Object>call(String name,Map<String,Object>args)throws Exception{return object("role","assistant","tool_calls",Arrays.asList(object("id",UUID.randomUUID().toString(),"type","function","function",object("name",name,"arguments",new ObjectMapper().writeValueAsString(args)))));}
 @Test void simulatedModelHandsUniquePriorityToExistingSceneTool()throws Exception{
  records(alarm("one","critical"));JdbcTemplate db=mock(JdbcTemplate.class);ConsoleService console=mock(ConsoleService.class);IntegrationService model=mock(IntegrationService.class);AssistantAgentService agent=new AssistantAgentService(db,new ObjectMapper(),model,console,monitoring);agent.setPriorityAlarms(priority);
  when(db.queryForList(anyString(),eq("1"),eq("A-01"))).thenReturn(Arrays.asList(object("id","10","sceneModelKey","A-01")));
  when(model.modelTools(anyList(),anyList())).thenReturn(call("get_priority_alarms",object())).thenReturn(call("scene_action",object("action","visit","cabinetKey","A-01")));
  Map<String,Object>result=agent.chat(object("id",1,"role","ADMIN"),"1",object("text","优先检查最紧急的异常","requestId","unique"));Map<?,?>action=(Map<?,?>)result.get("clientAction");assertEquals("visit",action.get("type"));assertEquals("A-01",action.get("cabinetSceneModelKey"));assertNotNull(action.get("token"));
  verify(console,never()).createOrder(anyMap(),anyMap());verify(console,never()).action(anyMap(),anyString(),anyString(),anyMap());verify(monitoring,never()).acknowledge(anyString(),anyString());assertEquals(9,agent.tools().size());
 }
 @Test void simulatedTieStopsModelAndRejectsUnselectedPatrol()throws Exception{
  records(alarm("one","critical"),alarm("two","critical"));JdbcTemplate db=mock(JdbcTemplate.class);IntegrationService model=mock(IntegrationService.class);AssistantAgentService agent=new AssistantAgentService(db,new ObjectMapper(),model,mock(ConsoleService.class),monitoring);agent.setPriorityAlarms(priority);Map<String,Object>actor=object("id",1,"role","ADMIN");
  when(model.modelTools(anyList(),anyList())).thenReturn(call("get_priority_alarms",object("limit",1)));
  Map<String,Object>first=agent.chat(actor,"1",object("text","优先巡检异常","requestId","ties"));assertNull(first.get("clientAction"));assertTrue(first.get("reply").toString().contains("并列"));verify(model,times(1)).modelTools(anyList(),anyList());
  when(db.queryForList(anyString(),eq("1"),eq("A-01"))).thenReturn(Arrays.asList(object("id","10","sceneModelKey","A-01")));
  when(model.modelTools(anyList(),anyList())).thenReturn(call("scene_action",object("action","visit","cabinetKey","A-01")));
  assertNull(agent.chat(actor,"1",object("text","继续","requestId","unselected")).get("clientAction"));
  assertNotNull(agent.chat(actor,"1",object("text","选择one","requestId","selected")).get("clientAction"));
 }
 @Test void modelCannotInventTargetForUnmappedPriorityAlarm()throws Exception{
  Map<String,Object>a=alarm("unmapped","critical");a.put("cabinetId","99");records(a);
  JdbcTemplate db=mock(JdbcTemplate.class);IntegrationService model=mock(IntegrationService.class);AssistantAgentService agent=new AssistantAgentService(db,new ObjectMapper(),model,mock(ConsoleService.class),monitoring);agent.setPriorityAlarms(priority);
  when(db.queryForList(anyString(),eq("1"),eq("A-01"))).thenReturn(Arrays.asList(object("id","10","sceneModelKey","A-01")));
  when(model.modelTools(anyList(),anyList())).thenReturn(call("get_priority_alarms",object())).thenReturn(call("scene_action",object("action","visit","cabinetKey","A-01"))).thenReturn(object("role","assistant","content","该告警无有效场景映射，不能巡检。"));
  Map<String,Object>result=agent.chat(object("id",1,"role","ADMIN"),"1",object("text","优先巡检异常","requestId","unmapped"));assertNull(result.get("clientAction"));assertTrue(new ObjectMapper().writeValueAsString(result.get("trace")).contains("优先告警没有有效巡检映射"));
 }
}
