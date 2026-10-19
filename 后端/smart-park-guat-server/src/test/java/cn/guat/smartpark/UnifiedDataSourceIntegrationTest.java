package cn.guat.smartpark;

import cn.guat.smartpark.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.sql.Timestamp;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;

@SpringBootTest @ActiveProfiles("test") @Transactional
class UnifiedDataSourceIntegrationTest {
 @Autowired UnifiedDataSourceMigration migration;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
 @Autowired MonitoringService monitoring;@Autowired ConsoleService console;@Autowired IntegrationService integration;@Autowired InventoryService inventory;
 String room,cabinet;
 @BeforeEach void guard(){assertEquals("platform_smart_park_stage1_test_20261008",db.queryForObject("SELECT DATABASE()",String.class));room=String.valueOf(db.queryForObject("SELECT id FROM sp_room ORDER BY id LIMIT 1",Long.class));cabinet=String.valueOf(db.queryForObject("SELECT c.id FROM sp_cabinet c JOIN sp_zone z ON z.id=c.zone_id WHERE z.room_id=? ORDER BY c.id LIMIT 1",Long.class,room));}
 @Test void migrationKeepsRowsAssociationsAndOriginalSnapshots()throws Exception{
  String event="unified-"+UUID.randomUUID(),orderId="WO-UNIFIED-"+UUID.randomUUID();
  Map<String,Object>alarm=object("id",event,"roomId",room,"cabinetId",cabinet,"recordOrigin","DEMO","status","未处理","rule",object("recordOrigin","DEMO","metric","temperature"));
  db.update("INSERT INTO sp_monitor_record(room_id,kind,id,payload) VALUES(?,'ALARM',?,?)",room,event,json.writeValueAsString(alarm));
  Map<String,Object>order=object("id",orderId,"roomId",room,"cabinetId",cabinet,"alarmEvent",event,"recordOrigin","DEMO","status","待复测","cause","原故障原因","process","原维修措施","steps",Arrays.asList("原时间线"));
  db.update("INSERT INTO console_order(id,status,payload) VALUES(?,'待复测',?)",orderId,json.writeValueAsString(order));
  db.update("INSERT INTO sp_reading(room_id,entity_type,entity_id,metric,value,unit,observed_at,quality,source,record_origin) VALUES(?,'CABINET',?,'temperature',24,'℃',?,'GOOD','MANUAL_TEST','DEMO')",room,cabinet,Timestamp.from(Instant.now()));
  long samples=db.queryForObject("SELECT COUNT(*) FROM sp_reading",Long.class),orders=db.queryForObject("SELECT COUNT(*) FROM console_order",Long.class);
  db.update("UPDATE sp_source_migration SET applied=FALSE WHERE id=?",UnifiedDataSourceMigration.VERSION);
  assertTrue(migration.migrate()>=3);assertEquals(samples,db.queryForObject("SELECT COUNT(*) FROM sp_reading",Long.class));assertEquals(orders,db.queryForObject("SELECT COUNT(*) FROM console_order",Long.class));
  Map<String,Object>saved=json.readValue(db.queryForObject("SELECT payload FROM console_order WHERE id=?",String.class,orderId),Map.class);assertEquals("REAL",saved.get("recordOrigin"));assertEquals(event,saved.get("alarmEvent"));assertEquals("原故障原因",saved.get("cause"));assertEquals(Arrays.asList("原时间线"),saved.get("steps"));
  String backup=db.queryForObject("SELECT payload FROM sp_source_backup WHERE migration_id=? AND table_name='console_order' AND record_key=?",String.class,UnifiedDataSourceMigration.VERSION,orderId);assertTrue(backup.contains("DEMO"));assertTrue(backup.contains("原维修措施"));
  assertEquals(0,migration.migrate());assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM sp_reading WHERE record_origin='DEMO'",Integer.class));
 }
 @Test void oldAliasesAreNormalizedOnReadAndFutureWrites()throws Exception{
  Map<String,Object>sample=object("entityType","CABINET","entityId",cabinet,"metric","unified-test","value",12,"unit","℃","observedAt",Instant.now().minusSeconds(1).toString(),"quality","GOOD","recordOrigin","DEMO","source","TEST_COLLECTOR");
  Map<String,Object>body=object("batchId","unified-"+UUID.randomUUID(),"readings",Arrays.asList(sample));
  integration.ingest(room,body);assertEquals(true,integration.ingest(room,body).get("duplicate"));assertEquals("DEMO",sample.get("recordOrigin"));
  assertEquals("REAL",db.queryForObject("SELECT record_origin FROM sp_reading WHERE room_id=? AND metric='unified-test' ORDER BY id DESC LIMIT 1",String.class,room));
  Map<String,Object>real=(Map<String,Object>)inventory.rooms("","REAL",1,100,"id,asc"),legacy=(Map<String,Object>)inventory.rooms("","DEMO",1,100,"id,asc");assertEquals(real.get("total"),legacy.get("total"));assertEquals("REAL",((Map<?,?>)legacy.get("meta")).get("recordOrigin"));
 }
 @Test void normalizerNeverRewritesBusinessDescriptions(){Map<String,Object>v=object("recordOrigin","DEMO","cause","DEMO 开发时发现风扇故障","alarmSnapshot",object("recordOrigin","DEMO"),"evidence",Arrays.asList(object("mode","DEMO","source","ADMIN_DEMO_TEMPERATURE")));UnifiedDataSource.normalize(v);assertEquals("REAL",v.get("recordOrigin"));assertEquals("DEMO 开发时发现风扇故障",v.get("cause"));assertEquals("REAL",((Map<?,?>)v.get("alarmSnapshot")).get("recordOrigin"));assertEquals("ADMIN_DEMO_TEMPERATURE",((Map<?,?>)((List<?>)v.get("evidence")).get(0)).get("source"));}
}
