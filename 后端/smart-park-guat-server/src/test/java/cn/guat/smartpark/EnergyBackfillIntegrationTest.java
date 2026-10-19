package cn.guat.smartpark;

import cn.guat.smartpark.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Transactional
class EnergyBackfillIntegrationTest {
 @Autowired JdbcTemplate db;
 @Autowired MonitoringService monitoring;
 String room;
 Clock original;
 @BeforeEach void setup(){
  assertEquals("platform_smart_park_stage1_test_20261008",db.queryForObject("SELECT DATABASE()",String.class));
  room=db.queryForObject("SELECT id FROM sp_room ORDER BY id LIMIT 1",String.class);
  original=(Clock)ReflectionTestUtils.getField(monitoring,"clock");
  db.update("DELETE FROM sp_energy_sample WHERE room_id=?",room);
  date("2026-10-15T04:00:00Z");
 }
 @AfterEach void restore(){ReflectionTestUtils.setField(monitoring,"clock",original);}
 void date(String instant){ReflectionTestUtils.setField(monitoring,"clock",Clock.fixed(Instant.parse(instant),ZoneOffset.UTC));}
 long count(){return db.queryForObject("SELECT COUNT(*) FROM sp_energy_sample WHERE room_id=?",Long.class,room);}
 @Test void fillsPersistsAndRollsAcrossShanghaiMidnight(){
  Map<String,Object> first=monitoring.energy(room,"7d");
  assertEquals(Arrays.asList("2026-10-09","2026-10-10","2026-10-11","2026-10-12","2026-10-13","2026-10-14","2026-10-15"),first.get("labels"));
  assertEquals(false,first.get("partial"));assertEquals(true,first.get("simulated"));
  for(Double value:(Double[])first.get("totals"))assertTrue(value>0);
  long saved=count();
  assertArrayEquals((Double[])first.get("totals"),(Double[])monitoring.energy(room,"7d").get("totals"));assertEquals(saved,count());
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM sp_energy_sample WHERE room_id=? AND observed_at>=?",Integer.class,room,Timestamp.from(Instant.parse("2026-10-15T16:00:00Z"))));
  date("2026-10-18T16:01:00Z"); // Shanghai is already October 19; missed days must also persist.
  Map<String,Object> later=monitoring.energy(room,"7d");
  assertEquals("2026-10-19",((List<?>)later.get("labels")).get(6));assertEquals(false,later.get("partial"));assertTrue(count()>saved);
  assertEquals(5,db.queryForObject("SELECT COUNT(DISTINCT category) FROM sp_energy_sample WHERE room_id=? AND observed_at>=? AND observed_at<?",Integer.class,room,Timestamp.from(Instant.parse("2026-10-15T16:00:00Z")),Timestamp.from(Instant.parse("2026-10-16T16:00:00Z"))));
 }
 @Test void preservesExistingSamplesAndFillsOnlyMissingCategories(){
  Timestamp at=Timestamp.from(Instant.parse("2026-10-14T16:00:00Z"));
  db.update("INSERT INTO sp_energy_sample(room_id,category,kwh,observed_at,quality,record_origin,source) VALUES(?,'空调',123,?,'GOOD','REAL','EXISTING')",room,at);
  Map<String,Object> result=monitoring.energy(room,"7d");
  assertEquals(false,result.get("partial"));
  assertEquals(123.0,db.queryForObject("SELECT SUM(kwh) FROM sp_energy_sample WHERE room_id=? AND category='空调' AND observed_at>=?",Double.class,room,at),.000001);
  assertEquals("EXISTING",db.queryForObject("SELECT source FROM sp_energy_sample WHERE room_id=? AND category='空调' AND observed_at>=?",String.class,room,at));
 }
}
