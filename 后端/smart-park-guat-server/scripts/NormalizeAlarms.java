import java.sql.*;
import java.nio.file.*;
import java.util.*;

/** Explicit development-data repair. Dry run by default; all writes share one transaction. */
public class NormalizeAlarms extends SeedMonitoring {
 static Object references(Object value,Map<String,String> ids){
  if(value instanceof Map){Map<String,Object> out=new LinkedHashMap<>();((Map<?,?>)value).forEach((k,v)->out.put(k.toString(),references(v,ids)));return out;}
  if(value instanceof List){List<Object> out=new ArrayList<>();for(Object v:(List<?>)value)out.add(references(v,ids));return out;}
  return value instanceof String?ids.getOrDefault(value.toString(),value.toString()):value;
 }
 public static void main(String[] args)throws Exception{
  boolean apply=Arrays.asList(args).contains("--apply");String cfg=Files.readString(Path.of("src/main/resources/application.yml"));
  try(Connection connection=DriverManager.getConnection(config(cfg,"url"),config(cfg,"username"),config(cfg,"password"))){
   db=connection;db.setAutoCommit(false);
   try{
    query("SELECT id FROM sp_room ORDER BY id FOR UPDATE");
    List<Map<String,Object>> rows=query("SELECT room_id,kind,id,payload FROM sp_monitor_record ORDER BY room_id,kind,id FOR UPDATE");
    List<Map<String,Object>> orders=query("SELECT id,payload FROM console_order FOR UPDATE");
    Map<String,Map<String,String>> mappings=new HashMap<>();int renamed=0,repaired=0,linked=0;
    for(Map<String,Object> row:rows)if("ALARM".equals(row.get("kind"))){
     room=row.get("room_id").toString();String id=row.get("id").toString();
     if(!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))mappings.computeIfAbsent(room,k->new HashMap<>()).put(id,alarmId(id));
    }
    if(apply){Path backup=Path.of("target/alarm-normalization-"+System.currentTimeMillis()+".json");Files.writeString(backup,json.writeValueAsString(map("monitorRecords",rows,"orders",orders)));System.out.println("Backup: "+backup);}
    for(Map<String,Object> row:rows){
     room=row.get("room_id").toString();String old=row.get("id").toString(),kind=row.get("kind").toString();Map<String,String> ids=mappings.getOrDefault(room,Collections.emptyMap());
     Map<String,Object> p=json.readValue(row.get("payload").toString(),Map.class);String before=json.writeValueAsString(p);
     p=(Map<String,Object>)references(p,ids);String id="ALARM".equals(kind)?ids.getOrDefault(old,old):old;
     if("ALARM".equals(kind)){
      p.put("id",id);if(!old.equals(id))renamed++;
      String title=String.valueOf(p.getOrDefault("title",""));
      if(old.startsWith("cabinet-level-example-")||"测试告警".equals(p.get("metric"))){
       boolean power=title.contains("负载")||title.contains("功率"),humidity=title.contains("温湿度")||title.contains("湿度");
       String metric=power?"power":humidity?"humidity":"temperature",unit=power?"kW":humidity?"%":"℃";
       double max=power?4:humidity?60:title.contains("维护")?28:30,value=power?4.8:humidity?65:title.contains("维护")?29:38;
       p.putAll(map("title",power?"机柜功率偏高":humidity?"机柜湿度偏高":"机柜进风温度偏高","metric",metric,"unit",unit,"value",value,"threshold","≤ "+max+" "+unit,
        "rule",map("metric",metric,"unit",unit,"min",0,"max",max,"maxAgeSeconds",300,"normalSeconds",60,"maxGapSeconds",30),"advice","核对设备指标并检查相关设备运行状态。","dataNature","SIMULATED"));repaired++;
      }else if(title.contains("DEMO")||title.contains("测试数据")){
       p.put("title",title.replace("（测试数据）","").replace("（DEMO）","").replace("(DEMO)","").trim());p.put("dataNature","SIMULATED");repaired++;
      }
     }
     if(!before.equals(json.writeValueAsString(p))&&apply)try(PreparedStatement s=db.prepareStatement("UPDATE sp_monitor_record SET id=?,payload=? WHERE room_id=? AND kind=? AND id=?")){
      s.setString(1,id);s.setString(2,json.writeValueAsString(p));s.setString(3,room);s.setString(4,kind);s.setString(5,old);s.executeUpdate();
     }
    }
    for(Map<String,Object> row:orders){Map<String,Object> p=json.readValue(row.get("payload").toString(),Map.class);String before=json.writeValueAsString(p);
     Object updated=references(p,mappings.getOrDefault(String.valueOf(p.get("roomId")),Collections.emptyMap()));String after=json.writeValueAsString(updated);
     if(!before.equals(after)){linked++;if(apply)try(PreparedStatement s=db.prepareStatement("UPDATE console_order SET payload=? WHERE id=?")){s.setString(1,after);s.setString(2,row.get("id").toString());s.executeUpdate();}}
    }
    // Keep the managed rule title clean so future evaluations cannot restore its old DEMO suffix.
    if(apply)try(PreparedStatement s=db.prepareStatement("UPDATE sp_monitor_record SET payload=JSON_SET(payload,'$.title','机柜进风温度偏高') WHERE kind='RULE' AND id='demo-a01-temperature-v1'")){s.executeUpdate();}
    if(apply){
     for(Map<String,Object> row:query("SELECT payload FROM sp_monitor_record WHERE kind='ALARM'")){
      Map<String,Object> p=json.readValue(row.get("payload").toString(),Map.class);
      UUID.fromString(p.get("id").toString());
      if(!"已恢复".equals(p.get("status")))for(String field:Arrays.asList("title","metric","value","threshold"))if(p.get(field)==null||p.get(field).toString().trim().isEmpty())throw new IllegalStateException("Active alarm missing "+field);
     }
     if(!query("SELECT o.id FROM console_order o LEFT JOIN sp_monitor_record a ON a.kind='ALARM' AND a.room_id=JSON_UNQUOTE(JSON_EXTRACT(o.payload,'$.roomId')) AND a.id=JSON_UNQUOTE(JSON_EXTRACT(o.payload,'$.alarmEvent')) WHERE JSON_UNQUOTE(JSON_EXTRACT(o.payload,'$.alarmEvent')) NOT IN ('','manual','null') AND a.id IS NULL").isEmpty())throw new IllegalStateException("Dangling order alarm reference");
     db.commit();
    }else db.rollback();
    System.out.println((apply?"Applied":"Planned")+": UUIDs="+renamed+", completed alarms="+repaired+", linked orders="+linked);
   }catch(Exception e){db.rollback();throw e;}
  }
 }
}
