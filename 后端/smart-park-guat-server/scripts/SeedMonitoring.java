import java.sql.*;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Explicitly run development dataset import; never an automatic production seed. */
public class SeedMonitoring {
 static String SOURCE="INTEGRATION_SEED";
 static double cabinetHumidity(int index){return Math.round((44.0+index%8*.4)*10)/10.0;}
 static final ObjectMapper json=new ObjectMapper();
 static Connection db; static String room; static Instant now=Instant.now();
 static String alarmId(String key){return UUID.nameUUIDFromBytes(("alarm:"+room+":"+key).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();}
 static Map<String,Object> map(Object...args){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<args.length;i+=2)m.put((String)args[i],args[i+1]);return m;}
 static String config(String text,String key){Matcher m=Pattern.compile("(?m)^\\s*"+key+":\\s*(.+)$").matcher(text);if(!m.find())throw new IllegalArgumentException("Missing "+key);return m.group(1).trim();}
 static List<Map<String,Object>> query(String sql)throws Exception{List<Map<String,Object>> rows=new ArrayList<>();try(Statement s=db.createStatement();ResultSet r=s.executeQuery(sql)){while(r.next()){Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));rows.add(row);}}return rows;}
 static void record(String kind,String id,Map<String,Object> payload)throws Exception{payload.putAll(map("id",id,"roomId",room,"source",SOURCE,"recordOrigin","REAL","observedAt",now.toString()));try(PreparedStatement s=db.prepareStatement("INSERT INTO sp_monitor_record(room_id,kind,id,payload) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE payload=VALUES(payload)")){s.setString(1,room);s.setString(2,kind);s.setString(3,id);s.setString(4,json.writeValueAsString(payload));s.executeUpdate();}}
 static void reading(String type,String id,String metric,double value,String unit,Instant at)throws Exception{try(PreparedStatement s=db.prepareStatement("INSERT INTO sp_reading(room_id,entity_type,entity_id,metric,value,unit,observed_at,quality,source,record_origin) VALUES(?,?,?,?,?,?,?,'GOOD',?,'REAL')")){s.setString(1,room);s.setString(2,type);s.setString(3,id);s.setString(4,metric);s.setDouble(5,value);s.setString(6,unit);s.setTimestamp(7,Timestamp.from(at));s.setString(8,SOURCE);s.executeUpdate();}}
 static void energy(String category,String cabinet,double value,Instant at)throws Exception{try(PreparedStatement s=db.prepareStatement("INSERT INTO sp_energy_sample(room_id,category,cabinet_id,kwh,observed_at,quality,record_origin,source) VALUES(?,?,?,?,?,'GOOD','REAL',?)")){s.setString(1,room);s.setString(2,category);s.setString(3,cabinet);s.setDouble(4,value);s.setTimestamp(5,Timestamp.from(at));s.setString(6,SOURCE);s.executeUpdate();}}
 public static void main(String[] args)throws Exception{
  String config=Files.readString(Path.of("src/main/resources/application.yml"));
  try(Connection connection=DriverManager.getConnection(config(config,"url"),config(config,"username"),config(config,"password"))){db=connection;db.setAutoCommit(false);try{
   room=String.valueOf(query("SELECT id FROM sp_room ORDER BY (record_origin='REAL') DESC,id LIMIT 1").get(0).get("id"));
   if(args.length>0 && "--environment-history".equals(args[0])){
    List<Map<String,Object>> samples=query("SELECT metric,value,unit,observed_at FROM sp_reading WHERE room_id="+room+" AND entity_type='ROOM' AND source='INTEGRATION_SEED' AND metric IN ('temperature','humidity','aqi','smoke','water') ORDER BY observed_at DESC");
    SOURCE="INTEGRATION_HISTORY";
    try(PreparedStatement s=db.prepareStatement("DELETE FROM sp_reading WHERE room_id=? AND entity_type='ROOM' AND source=?")){s.setString(1,room);s.setString(2,SOURCE);s.executeUpdate();}
    Set<String> seen=new HashSet<>();int added=0;
    double[] variation={-.5,-.3,-.4,-.2,-.1,.1,-.1,.2,.1};
    for(Map<String,Object> sample:samples){String metric=sample.get("metric").toString();if(!seen.add(metric))continue;double base=((Number)sample.get("value")).doubleValue();Instant at=((Timestamp)sample.get("observed_at")).toInstant();
     for(int i=0;i<9;i++){double value=base;if("temperature".equals(metric))value+=variation[i];if("humidity".equals(metric))value+=variation[i]*3;if("aqi".equals(metric))value+=Math.round(variation[i]*4);reading("ROOM",room,metric,Math.round(value*10)/10.0,String.valueOf(sample.get("unit")),at.minusSeconds((9-i)*300));added++;}
    }
    db.commit();System.out.println("Environment history inserted: "+added);return;
   }
   List<Map<String,Object>> cabinets=query("SELECT c.id,c.code,c.name,c.scene_model_key FROM sp_cabinet c JOIN sp_zone z ON c.zone_id=z.id WHERE z.room_id="+room+" ORDER BY c.id");
   // Targeted development backfill: append missing cabinet humidity only.
   // Keep the existing development-origin convention; source identifies generated data.
   if(args.length>0 && "--cabinet-humidity".equals(args[0])){
    int added=0;
    for(int i=0;i<cabinets.size();i++){
     String id=cabinets.get(i).get("id").toString();
     try(PreparedStatement s=db.prepareStatement("SELECT COUNT(*) FROM sp_reading WHERE room_id=? AND entity_type='CABINET' AND entity_id=? AND metric='humidity' AND quality='GOOD' AND record_origin='REAL'")){
      s.setString(1,room);s.setString(2,id);
      try(ResultSet r=s.executeQuery()){r.next();if(r.getInt(1)>0)continue;}
     }
     reading("CABINET",id,"humidity",cabinetHumidity(i),"%",now);added++;
    }
    db.commit();System.out.println("Development cabinet humidity inserted: "+added+", source="+SOURCE);return;
   }
   List<Map<String,Object>> devices=query("SELECT d.id,d.name,d.device_type,d.cabinet_id FROM sp_device d JOIN sp_cabinet c ON c.id=d.cabinet_id JOIN sp_zone z ON z.id=c.zone_id WHERE z.room_id="+room+" ORDER BY (d.device_type='SWITCH') DESC,d.id");
   for(String table:Arrays.asList("sp_reading","sp_energy_sample"))try(PreparedStatement s=db.prepareStatement("DELETE FROM "+table+" WHERE room_id=? AND source=?")){s.setString(1,room);s.setString(2,SOURCE);s.executeUpdate();}
   String[] env={"temperature","humidity","aqi","smoke","water","power","peakPower","itPower","pue","todayPue","monthPue"};double[] vals={24.3,45.2,28,0,0,82.5,96.8,59.8,1.38,1.41,1.43};String[] units={"℃","%","AQI","","","kW","kW","kW","","",""};
   for(int i=0;i<env.length;i++)reading("ROOM",room,env[i],vals[i],units[i],now);
   for(int i=0;i<cabinets.size();i++){String id=cabinets.get(i).get("id").toString();reading("CABINET",id,"temperature",i==1?31.8:23.4+i%8*.2,"℃",now);reading("CABINET",id,"power",1.2+i%10*.13,"kW",now);for(String metric:Arrays.asList("online","powerState","networkState"))reading("CABINET",id,metric,1,"",now);record("LOG","seed-log-"+id,map("cabinetId",id,"action","设备状态检查完成","user","运维管理员","createdAt",now.minusSeconds(120+i*60).toString()));}
   for(int i=0;i<cabinets.size();i++)reading("CABINET",cabinets.get(i).get("id").toString(),"humidity",cabinetHumidity(i),"%",now);
   for(int i=0;i<devices.size();i++){String id=devices.get(i).get("id").toString();reading("DEVICE",id,"temperature",25.2+i%9*.3,"℃",now);reading("DEVICE",id,"power",.12+i%6*.04,"kW",now);reading("DEVICE",id,"online",1,"",now);}
   Map<String,Double> cabinetWeights=new LinkedHashMap<>();for(Map<String,Object> cabinet:cabinets)cabinetWeights.put(cabinet.get("id").toString(),.035);
   for(int i=0;i<devices.size();i++){Map<String,Object> device=devices.get(i);String cabinet=device.get("cabinet_id").toString();double load=.65+Math.floorMod(device.get("id").toString().hashCode(),11)*.035;cabinetWeights.put(cabinet,cabinetWeights.get(cabinet)+(.12+i%6*.04)*load);}
   for(Map.Entry<String,Double> entry:cabinetWeights.entrySet())entry.setValue(entry.getValue()*(.8+Math.floorMod(entry.getKey().hashCode(),97)*.004));
   double cabinetWeightTotal=cabinetWeights.values().stream().mapToDouble(Double::doubleValue).sum();
   ZoneId zone=ZoneId.of("Asia/Shanghai");ZonedDateTime local=now.atZone(zone);double todayKwh=0,monthKwh=0;
   for(int day=29;day>=0;day--){int hours=day==0?local.getHour()+1:24;for(int hour=0;hour<hours;hour++){Instant at=local.toLocalDate().minusDays(day).atTime(hour,0).atZone(zone).toInstant();double base=38+hour%6*1.8+day%7*.4;double total=0;for(int c=0;c<cabinets.size();c++){double kwh=base*cabinetWeights.get(cabinets.get(c).get("id").toString())/cabinetWeightTotal;energy("机柜",cabinets.get(c).get("id").toString(),kwh,at);total+=kwh;}String[] names={"空调","照明","监控","其他"};double[] ratios={.38,.07,.04,.06};for(int i=0;i<names.length;i++){energy(names[i],null,base*ratios[i],at);total+=base*ratios[i];}if(day==0)todayKwh+=total;if(local.toLocalDate().minusDays(day).getMonth()==local.getMonth())monthKwh+=total;}}
   reading("ROOM",room,"todayKwh",todayKwh,"kWh",now);reading("ROOM",room,"monthKwh",monthKwh,"kWh",now);
   for(int i=0;i<10;i++)record("FACILITY","seed-ac-"+(i+1),map("type","AC","name",String.format("精密空调 %02d",i+1),"sceneModelKey","ac-"+(i+1),"status","正常"));
   String[] cameraKeys={"zone-a","zone-b","zone-c","zone-d","camera-b"};for(int i=0;i<5;i++)record("FACILITY","seed-camera-"+(i+1),map("type","CAMERA","name",String.format("监控摄像头 %02d",i+1),"sceneModelKey",cameraKeys[i],"status","在线"));
   List<Map<String,Object>> network=new ArrayList<>();for(Map<String,Object>d:devices)if("SWITCH".equals(d.get("device_type"))&&network.size()<4)network.add(d);for(Map<String,Object>d:devices)if(!"SWITCH".equals(d.get("device_type"))&&network.size()<12)network.add(d);int count=network.size();for(int i=0;i<count;i++){Map<String,Object>d=network.get(i);String layer=i==0?"core":i==1?"aggregate":i<4?"access":"terminal";record("NODE","seed-node-"+i,map("name",d.get("name"),"deviceId",d.get("id").toString(),"cabinetId",d.get("cabinet_id").toString(),"layer",layer,"status","正常","ports",map("total",i<4?48:4,"used",i<4?26+i*3:2,"errors",0)));if(i>0)record("LINK","seed-link-"+i,map("from","seed-node-"+(i==1?0:i<4?1:2+i%2),"to","seed-node-"+i,"status",i==7?"error":"normal","bandwidth",i<4?10000:1000,"latency",i==7?12.6:.5+i*.12,"loss",i==7?2.4:0));}
   for(int i=0;i<4;i++){Map<String,Object>c=cabinets.get((1+i*7)%cabinets.size());record("ALARM",alarmId("seed-alarm-"+i),map("title",i==0?"机柜进风温度偏高":i==1?"上联端口丢包偏高":i==2?"机柜功率偏高":"空调能耗偏高","cabinetId",c.get("id").toString(),"entityType",i>=2?"POWER":"CABINET","entityId",c.get("id").toString(),"metric",i==0?"温度":i==1?"丢包率":"功率","value",i==0?"31.8 ℃":i==1?"2.4 %":"4.8 kW","threshold",i==0?"≤ 28 ℃":i==1?"≤ 1 %":"≤ 4 kW","severity",i==0?"critical":"warning","status","未处理","createdAt",now.minusSeconds(600+i*300).toString(),"advice",i==0?"检查冷通道送风与机柜进风口，核对温度读数。":"检查相关设备状态与历史指标，记录处理过程。","rule",map("metric",i==0?"temperature":i==1?"loss":"power","unit",i==0?"℃":i==1?"%":"kW","min",0,"max",i==0?28:i==1?1:4,"maxAgeSeconds",300,"normalSeconds",60,"maxGapSeconds",30)));}
   for(int i=0;i<12;i++)record("EVENT","seed-event-"+i,map("title",i%3==0?"例行巡检完成":i%3==1?"设备指标更新":"网络链路状态检查","description","机房运行记录已保存","createdAt",now.minusSeconds(i*900).toString()));
   for(int i=1;i<=30;i++){Map<String,Object>c=cabinets.get(i%cabinets.size());record("ALARM",alarmId("seed-history-"+i),map("title","设备温度提醒","cabinetId",c.get("id").toString(),"entityType","CABINET","entityId",c.get("id").toString(),"severity","info","status","已恢复","createdAt",now.minusSeconds(i*86400L).toString()));}
   db.commit();System.out.println(json.writeValueAsString(map("roomId",room,"cabinets",cabinets.size(),"devices",devices.size(),"nodes",count,"links",count-1,"activeAlarms",4,"facilities",15,"source",SOURCE)));
  }catch(Exception e){db.rollback();throw e;}}
 }
}



