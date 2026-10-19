package cn.guat.smartpark;

import cn.guat.smartpark.service.*;
import cn.guat.smartpark.mapper.InventoryMapper;
import cn.guat.smartpark.entity.*;
import cn.guat.smartpark.exception.PlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import java.util.*;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** No Spring context, datasource, network or lifecycle callbacks. */
class AssistantAssetToolsTest {
 @Test void validatesArgumentsBeforeCallingInventory(){
  InventoryService inv=mock(InventoryService.class);AssistantAssetTools tools=new AssistantAssetTools(inv);
  for(Map<String,Object>a:Arrays.asList(object("roomId","2"),object("page",0),object("page",1.5),object("page","1"),object("pageSize",101),object("page",null),object("zoneId","0"),object("q",String.join("",Collections.nCopies(101,"a")))))assertThrows(PlatformException.class,()->tools.execute("1","list_cabinets",a));
  assertThrows(PlatformException.class,()->tools.execute("1","get_cabinet_info",object()));
  assertThrows(PlatformException.class,()->tools.execute("1","get_cabinet_info",object("cabinetId","1","cabinetKey","A-01")));
  assertThrows(PlatformException.class,()->tools.execute("1","get_device_info",object("deviceId","9223372036854775808")));
  assertThrows(PlatformException.class,()->tools.execute("1","list_devices",object("cabinetId","1","type","SQL")));
  verifyNoInteractions(inv);
 }
 @Test void delegatesAllFiveToolsAndReportsPartialPage(){
  InventoryService inv=mock(InventoryService.class);AssistantAssetTools tools=new AssistantAssetTools(inv);
  when(inv.room("1")).thenReturn(object("id","1","location",null));
  when(inv.scopedCabinet("1",null,"A-01")).thenReturn("10");
  when(inv.cabinet("10")).thenReturn(object("id","10"));when(inv.device("1","20")).thenReturn(object("id","20","uStart",null));
  when(inv.cabinets("1",null,"",2,20,"id,asc")).thenReturn(object("items",Arrays.asList(object("id","10")),"total",42));
  when(inv.devices("10","",null,1,20,"id,asc")).thenReturn(object("items",Collections.emptyList(),"total",0));
  assertNotNull(tools.execute("1","get_room_info",object()).get("data"));
  assertNotNull(tools.execute("1","get_cabinet_info",object("cabinetKey","A-01")).get("data"));
  assertNotNull(tools.execute("1","get_device_info",object("deviceId","20")).get("data"));
  Map<?,?>range=(Map<?,?>)tools.execute("1","list_cabinets",object("page",2)).get("range");assertEquals(42L,range.get("total"));assertEquals(21L,range.get("from"));assertEquals(true,range.get("hasMore"));
  assertEquals(0L,((Map<?,?>)tools.execute("1","list_devices",object("cabinetKey","A-01","q","")).get("range")).get("from"));
  assertEquals(5,tools.definitions().size());
 }
 @Test void realServiceRejectsForeignResourcesUsingMockMapper(){
  InventoryMapper mapper=mock(InventoryMapper.class);Room room=new Room();room.setId(1L);when(mapper.rooms()).thenReturn(Arrays.asList(room));when(mapper.cabinets(1L)).thenReturn(Collections.emptyList());when(mapper.devices(1L)).thenReturn(Collections.emptyList());
  InventoryService inv=new InventoryService(mapper,mock(MonitoringService.class));
  assertThrows(PlatformException.class,()->inv.scopedCabinet("1","99",null));assertThrows(PlatformException.class,()->inv.scopedCabinet("1",null,"B-01"));assertThrows(PlatformException.class,()->inv.device("1","99"));
  verify(mapper,never()).cabinet(anyLong());
  Zone foreign=new Zone();foreign.setRoomId(2L);when(mapper.zone(99L)).thenReturn(foreign);
  assertThrows(PlatformException.class,()->inv.cabinets("1","99","",1,20,"id,asc"));
 }
 @Test void deviceDetailPreservesMissingModelFields(){
  InventoryMapper mapper=mock(InventoryMapper.class);Room room=new Room();room.setId(1L);Device d=new Device();d.setId(20L);d.setCabinetId(10L);d.setRecordOrigin("REAL");when(mapper.rooms()).thenReturn(Arrays.asList(room));when(mapper.devices(1L)).thenReturn(Arrays.asList(d));
  Map<?,?>data=(Map<?,?>)new InventoryService(mapper,mock(MonitoringService.class)).device("1","20");assertEquals("20",data.get("id"));assertTrue(data.containsKey("uStart"));assertNull(data.get("uStart"));assertFalse(data.containsKey("manufacturer"));
 }
 @Test void modelToolLoopExecutesAssetToolWithoutDatabase(){
  JdbcTemplate db=mock(JdbcTemplate.class);ConsoleService console=mock(ConsoleService.class);MonitoringService monitoring=mock(MonitoringService.class);IntegrationService model=mock(IntegrationService.class);InventoryService inv=mock(InventoryService.class);
  when(inv.room("1")).thenReturn(object("id","1","registeredDeviceCount",3));
  AssistantAgentService agent=new AssistantAgentService(db,new ObjectMapper(),model,console,monitoring);agent.setAssetTools(new AssistantAssetTools(inv));
  when(model.modelTools(anyList(),anyList())).thenReturn(object("role","assistant","tool_calls",Arrays.asList(object("id","call-1","type","function","function",object("name","get_room_info","arguments","{}"))))).thenReturn(object("role","assistant","content","当前机房登记设备3台"));
  Map<String,Object>result=agent.chat(object("id",1,"role","ADMIN"),"1",object("text","介绍机房资产","requestId","asset-test"));assertEquals("当前机房登记设备3台",result.get("reply"));verify(inv).room("1");verifyNoInteractions(db);verify(console,never()).createOrder(anyMap(),anyMap());assertEquals(13,agent.tools().size());
 }
 @Test void unauthorizedActorCannotReachAssetTools(){
  ConsoleService console=mock(ConsoleService.class);Map<String,Object>actor=object("id",2,"role","MAINTAINER");doThrow(new PlatformException(403,"FORBIDDEN","仅管理员可以操作")).when(console).admin(actor);
  InventoryService inv=mock(InventoryService.class);IntegrationService model=mock(IntegrationService.class);AssistantAgentService agent=new AssistantAgentService(mock(JdbcTemplate.class),new ObjectMapper(),model,console,mock(MonitoringService.class));agent.setAssetTools(new AssistantAssetTools(inv));
  assertThrows(PlatformException.class,()->agent.chat(actor,"1",object("text","查询资产","requestId","denied")));verifyNoInteractions(inv,model);
 }
}
