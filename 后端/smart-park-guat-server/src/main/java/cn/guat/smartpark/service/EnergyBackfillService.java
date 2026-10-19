package cn.guat.smartpark.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Persists stable simulated energy for missing days/categories; preserves existing samples. */
@Service
public class EnergyBackfillService {
 public static final String SOURCE="AUTO_SIMULATED_ENERGY";
 private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
 private static final String[] CATEGORIES={"机柜","空调","照明","监控","其他"};
 private static final double[] BASE={1450,520,110,85,65};
 private final JdbcTemplate db;
 public EnergyBackfillService(JdbcTemplate db){this.db=db;}
 @Transactional
 public void fill(String room,LocalDate today){
  // Serialize concurrent requests so the same missing day is inserted only once.
  if(db.queryForList("SELECT id FROM sp_room WHERE id=? FOR UPDATE",room).isEmpty())return;
  Timestamp end=Timestamp.from(today.plusDays(1).atStartOfDay(ZONE).toInstant());
  Timestamp earliest=db.queryForObject("SELECT MIN(observed_at) FROM sp_energy_sample WHERE room_id=? AND observed_at<? AND quality='GOOD' AND record_origin='REAL'",Timestamp.class,room,end);
  LocalDate start=today.minusDays(29);
  if(earliest!=null&&earliest.toInstant().atZone(ZONE).toLocalDate().isBefore(start))start=earliest.toInstant().atZone(ZONE).toLocalDate();
  Set<String> present=new HashSet<>();
  for(Map<String,Object> row:db.queryForList("SELECT category,observed_at FROM sp_energy_sample WHERE room_id=? AND observed_at>=? AND observed_at<? AND quality='GOOD' AND record_origin='REAL'",room,Timestamp.from(start.atStartOfDay(ZONE).toInstant()),end))present.add(((Timestamp)row.get("observed_at")).toInstant().atZone(ZONE).toLocalDate()+":"+row.get("category"));
  List<Map<String,Object>> cabinets=db.queryForList("SELECT c.id,COUNT(d.id)+1 AS weight FROM sp_cabinet c JOIN sp_zone z ON z.id=c.zone_id LEFT JOIN sp_device d ON d.cabinet_id=c.id WHERE z.room_id=? GROUP BY c.id ORDER BY c.id",room);
  double weights=cabinets.stream().mapToDouble(c->((Number)c.get("weight")).doubleValue()).sum();
  List<Object[]> inserts=new ArrayList<>();
  for(LocalDate day=start;!day.isAfter(today);day=day.plusDays(1)){
   double variation=.92+new Random(Objects.hash(room,day.toEpochDay())).nextDouble()*.16;
   double weekend=day.getDayOfWeek().getValue()>=6?.90:1;
   for(int category=0;category<CATEGORIES.length;category++){
    if(present.contains(day+":"+CATEGORIES[category]))continue;
    double daily=Math.round(BASE[category]*variation*weekend*100)/100.0;
    for(int hour=0;hour<24;hour++){
     Timestamp at=Timestamp.from(day.atTime(hour,0).atZone(ZONE).toInstant());
     // Stable hourly profile for a simulated full day, including today.
     double hourly=daily*(hour>=8&&hour<20?1.15:.85)/24;
     if(category==0&&!cabinets.isEmpty()){
      for(Map<String,Object> cabinet:cabinets)inserts.add(new Object[]{room,CATEGORIES[category],cabinet.get("id"),hourly*((Number)cabinet.get("weight")).doubleValue()/weights,at,SOURCE});
     }else inserts.add(new Object[]{room,CATEGORIES[category],null,hourly,at,SOURCE});
    }
   }
  }
  if(!inserts.isEmpty())db.batchUpdate("INSERT INTO sp_energy_sample(room_id,category,cabinet_id,kwh,observed_at,quality,record_origin,source) VALUES(?,?,?,?,?,'GOOD','REAL',?)",inserts);
 }
}
