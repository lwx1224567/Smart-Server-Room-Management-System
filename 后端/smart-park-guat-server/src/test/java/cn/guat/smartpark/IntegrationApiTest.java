package cn.guat.smartpark;

import cn.guat.smartpark.service.*;
import cn.guat.smartpark.exception.PlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.*;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class IntegrationApiTest {
 @Autowired IntegrationService integration;@Autowired InventoryMaintenanceService inventory;@Autowired MonitoringService monitor;@Autowired JdbcTemplate db;@Autowired MockMvc mvc;@Autowired ObjectMapper json;
 @MockBean ConsoleService console;
 String room,cabinet;Map<String,Object> device;
 @BeforeEach void fixture(){
  when(console.identity(org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(object("role","ADMIN","id",1,"displayName","测试管理员"));
  String suffix=UUID.randomUUID().toString().substring(0,8);
  Map<String,Object> park=inventory.save("parks",null,object("code","it-"+suffix,"name","接口测试园区"));
  Map<String,Object> r=inventory.save("rooms",null,object("parkId",park.get("id"),"code","it-"+suffix,"name","接口测试机房","location","测试区域","sceneModelKey","room-"+suffix));room=r.get("id").toString();
  Map<String,Object> zone=inventory.save("zones",null,object("roomId",r.get("id"),"code","T","name","测试区","displayOrder",0));
  Map<String,Object> c=inventory.save("cabinets",null,object("zoneId",zone.get("id"),"code","rack-"+suffix,"name","测试机柜","serialNumber","serial-"+suffix,"capacityU",42,"sceneModelKey","rack-"+suffix,"gridRow",0,"gridColumn",0,"positionX",0,"positionY",0,"positionZ",0,"registrationStatus","ACTIVE"));cabinet=c.get("id").toString();
  device=object("cabinetId",c.get("id"),"code","dev-"+suffix,"name","测试设备","type","SERVER","sceneModelKey","dev-"+suffix,"legacyKey","dev-"+suffix,"registrationStatus","ACTIVE","uStart",1,"uHeight",2);
  // Maintenance API mirrors database fields in camelCase.
  device.put("deviceType",device.remove("type"));
 }
 private Map<String,Object> sample(double value){return object("entityType","CABINET","entityId",cabinet,"metric","temperature","value",value,"unit","℃","observedAt",Instant.now().minusSeconds(1).toString(),"quality","GOOD","recordOrigin","REAL","source","TEST_COLLECTOR");}
 private Map<String,Object> rule(){return object("entityType","CABINET","entityId",cabinet,"metric","temperature","unit","℃","title","温度异常","severity","warning","min",0,"max",28,"maxAgeSeconds",300,"normalSeconds",0,"maxGapSeconds",30,"enabled",true);}
 @Test void inventoryCrudAndUPositionValidation(){Map<String,Object> saved=inventory.save("devices",null,device);assertEquals("测试设备",saved.get("name"));inventory.save("devices",saved.get("id").toString(),object("name","修改后的设备"));assertEquals("修改后的设备",inventory.get("devices",saved.get("id").toString()).get("name"));Map<String,Object> overlap=new LinkedHashMap<>(device);overlap.put("code",device.get("code")+"2");overlap.put("sceneModelKey",device.get("sceneModelKey")+"2");assertEquals(409,assertThrows(PlatformException.class,()->inventory.save("devices",null,overlap)).status);assertEquals(409,assertThrows(PlatformException.class,()->inventory.delete("cabinets",cabinet)).status);inventory.delete("devices",saved.get("id").toString());inventory.delete("cabinets",cabinet);}
 @Test void ingestDeduplicationAndGeneratedAlarm(){integration.saveRule(room,"temp",rule());Map<String,Object>batch=object("batchId","batch-1","readings",Arrays.asList(sample(32)));assertEquals(false,integration.ingest(room,batch).get("duplicate"));assertEquals(true,integration.ingest(room,batch).get("duplicate"));assertEquals(1,monitor.readings(room).size());List<Map<String,Object>> alarms=monitor.records(room,"ALARM");assertEquals(1,alarms.size());String alarm=alarms.get(0).get("id").toString();monitor.acknowledge(room,alarm);assertEquals("处理中",monitor.records(room,"ALARM").get(0).get("status"));Map<String,Object>different=object("batchId","batch-1","readings",Arrays.asList(sample(34)));assertEquals(409,assertThrows(PlatformException.class,()->integration.ingest(room,different)).status);}
 @Test void energyAggregationAndHistory(){List<Map<String,Object>> energy=new ArrayList<>();for(String category:Arrays.asList("机柜","空调","照明","监控","其他"))energy.add(object("category",category,"cabinetId",category.equals("机柜")?cabinet:null,"kwh",2,"observedAt",Instant.now().minusSeconds(1).toString(),"quality","GOOD","recordOrigin","REAL","source","TEST_COLLECTOR"));integration.ingest(room,object("batchId","energy-1","energy",energy,"readings",Arrays.asList(sample(25))));assertEquals(10.0,((Number)monitor.energy(room,"today").get("total")).doubleValue());assertEquals(10.0,((Number)monitor.reading(room,"ROOM",room,"todayKwh","kWh").get("value")).doubleValue());assertEquals(1,integration.history(room,"CABINET",cabinet,"temperature",Instant.now().minusSeconds(60).toString(),Instant.now().plusSeconds(1).toString()).size());}
 @Test void videoConfigurationAndUnconfiguredServices()throws Exception{monitor.saveRecord(room,"FACILITY","cam",object("id","cam","type","CAMERA","sceneModelKey","zone-a"));integration.videoConfig(room,"cam",object("mode","DIRECT","url","https://video.example/live.mp4","protocol","MP4"));assertEquals(true,integration.video(room,"cam").get("live"));mvc.perform(get("/api/v1/rooms/"+room+"/cameras/cam/video")).andExpect(status().isOk()).andExpect(jsonPath("$.data.url").value("https://video.example/live.mp4"));mvc.perform(post("/api/v1/assistant/chat").contentType("application/json").content("{\"text\":\"查看状态\"}")).andExpect(status().isNotFound());mvc.perform(post("/api/v1/rooms/"+room+"/cabinets/"+cabinet+"/commands").header("Authorization","Bearer test").contentType("application/json").content("{\"key\":\"power\",\"value\":false}")).andExpect(status().isServiceUnavailable());assertTrue(monitor.records(room,"COMMAND").isEmpty());}
 @Test void routesRejectInvalidReferencesAndRules()throws Exception{mvc.perform(post("/api/v1/rooms/"+room+"/ingest").contentType("application/json").content(json.writeValueAsString(object("batchId","invalid","readings",Arrays.asList(object("entityType","ROOM","entityId","0","metric","temperature","value",12)))))).andExpect(status().isBadRequest());Map<String,Object>invalid=rule();invalid.put("max",-1);mvc.perform(put("/api/v1/rooms/"+room+"/alarm-rules/invalid").contentType("application/json").content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());}
 @Test void normalEvidenceRecoversStandaloneAlarm(){integration.saveRule(room,"temp",rule());Map<String,Object>hot=sample(32);hot.put("observedAt",Instant.now().minusSeconds(3).toString());integration.ingest(room,object("batchId","hot","readings",Arrays.asList(hot)));Map<String,Object>normal=sample(25);normal.put("observedAt",Instant.now().minusSeconds(1).toString());integration.ingest(room,object("batchId","normal","readings",Arrays.asList(normal)));Map<String,Object>alarm=monitor.records(room,"ALARM").get(0);assertEquals("已恢复",alarm.get("status"));assertTrue(alarm.containsKey("recoveryEvidence"));assertEquals(409,assertThrows(PlatformException.class,()->monitor.acknowledge(room,alarm.get("id").toString())).status);}
}
