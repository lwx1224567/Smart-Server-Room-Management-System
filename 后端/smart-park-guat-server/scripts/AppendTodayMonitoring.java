import java.sql.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Append today's development samples; preserve existing records. Default is inspection only. */
public class AppendTodayMonitoring extends SeedMonitoring {
 public static void main(String[] args) throws Exception {
  String cfg=Files.readString(Path.of("src/main/resources/application.yml"));
  ZoneId zone=ZoneId.of("Asia/Shanghai");
  LocalDate today=LocalDate.now(zone); Instant start=today.atStartOfDay(zone).toInstant(),end=today.plusDays(1).atStartOfDay(zone).toInstant();
  boolean apply=Arrays.asList(args).contains("--apply"); SOURCE="INTEGRATION_TODAY";
  try(Connection connection=DriverManager.getConnection(config(cfg,"url"),config(cfg,"username"),config(cfg,"password"))) {
   db=connection; db.setAutoCommit(false);
   try {
    room=String.valueOf(query("SELECT id FROM sp_room ORDER BY (record_origin='REAL') DESC,id LIMIT 1").get(0).get("id"));
    List<Map<String,Object>> history=query("SELECT category,cabinet_id,kwh,observed_at FROM sp_energy_sample WHERE room_id="+room+" AND quality='GOOD' AND record_origin='REAL' AND source LIKE 'INTEGRATION_%' ORDER BY observed_at DESC");
    System.out.println("Date="+today+", room="+room+", historicalEnergyRows="+history.size());
    try(PreparedStatement s=db.prepareStatement("SELECT category,COUNT(*) count,SUM(kwh) kwh FROM sp_energy_sample WHERE room_id=? AND observed_at>=? AND observed_at<? GROUP BY category")) {
     s.setString(1,room);s.setTimestamp(2,Timestamp.from(start));s.setTimestamp(3,Timestamp.from(end));
     try(ResultSet r=s.executeQuery()){while(r.next())System.out.println("Before: "+r.getString(1)+" rows="+r.getInt(2)+" kWh="+r.getDouble(3));}
    }
    if(!apply){db.rollback();return;}
    if(history.isEmpty())throw new IllegalStateException("No development energy history to copy");
    Set<String> existing=new HashSet<>();
    try(PreparedStatement s=db.prepareStatement("SELECT category,cabinet_id,observed_at FROM sp_energy_sample WHERE room_id=? AND observed_at>=? AND observed_at<? AND quality='GOOD' AND record_origin='REAL'")) {
     s.setString(1,room);s.setTimestamp(2,Timestamp.from(start));s.setTimestamp(3,Timestamp.from(end));
     try(ResultSet r=s.executeQuery()){while(r.next())existing.add(r.getString(1)+"/"+r.getString(2)+"/"+r.getTimestamp(3).toInstant().atZone(zone).getHour());}
    }
    // Use the most recent historical sample for each category/cabinet/hour.
    Map<String,Map<String,Object>> templates=new LinkedHashMap<>();
    for(Map<String,Object> row:history){ZonedDateTime at=((Timestamp)row.get("observed_at")).toInstant().atZone(zone);if(!at.toLocalDate().isBefore(today))continue;String key=row.get("category")+"/"+row.get("cabinet_id")+"/"+at.getHour();templates.putIfAbsent(key,row);}
    int energyAdded=0,readingsAdded=0;int hour=Instant.now().atZone(zone).getHour();
    for(Map.Entry<String,Map<String,Object>> entry:templates.entrySet()) {
     Map<String,Object> row=entry.getValue();int h=((Timestamp)row.get("observed_at")).toInstant().atZone(zone).getHour();
     if(h>hour||existing.contains(entry.getKey()))continue;
     energy(row.get("category").toString(),row.get("cabinet_id")==null?null:row.get("cabinet_id").toString(),((Number)row.get("kwh")).doubleValue(),today.atTime(h,0).atZone(zone).toInstant());energyAdded++;
    }
    List<Map<String,Object>> readings=query("SELECT entity_type,entity_id,metric,value,unit,observed_at FROM sp_reading WHERE room_id="+room+" AND quality='GOOD' AND record_origin='REAL' ORDER BY observed_at DESC,id DESC");
    Set<String> seen=new HashSet<>();
    for(Map<String,Object> row:readings){String metric=row.get("metric").toString(),key=row.get("entity_type")+"/"+row.get("entity_id")+"/"+metric;if(!seen.add(key))continue;if(((Timestamp)row.get("observed_at")).toInstant().compareTo(start)>=0)continue;if(metric.equals("todayKwh")||metric.equals("monthKwh"))continue;reading(row.get("entity_type").toString(),row.get("entity_id").toString(),metric,((Number)row.get("value")).doubleValue(),row.get("unit").toString(),Instant.now());readingsAdded++;}
    for(String metric:Arrays.asList("todayKwh","monthKwh")) {
     Instant from=metric.equals("todayKwh")?start:today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
     try(PreparedStatement s=db.prepareStatement("SELECT SUM(kwh) FROM sp_energy_sample WHERE room_id=? AND observed_at>=? AND observed_at<? AND quality='GOOD' AND record_origin='REAL'")) {
      s.setString(1,room);s.setTimestamp(2,Timestamp.from(from));s.setTimestamp(3,Timestamp.from(end));try(ResultSet r=s.executeQuery()){r.next();double total=r.getDouble(1);if(r.wasNull())throw new IllegalStateException("No energy total");if(energyAdded>0||readings.stream().noneMatch(v->metric.equals(v.get("metric"))&&((Timestamp)v.get("observed_at")).toInstant().compareTo(start)>=0)){reading("ROOM",room,metric,total,"kWh",Instant.now());readingsAdded++;}System.out.println(metric+"="+total);}
     }
    }
    db.commit();System.out.println("Appended energy="+energyAdded+", readings="+readingsAdded+", source="+SOURCE);
   }catch(Exception e){db.rollback();throw e;}
  }
 }
}
