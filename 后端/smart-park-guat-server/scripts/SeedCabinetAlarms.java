import java.sql.*;
import java.nio.file.*;
import java.util.*;

/** Targeted development alarm import. Preserves readings, energy and existing alarms. */
public class SeedCabinetAlarms extends SeedMonitoring {
 public static void main(String[] args) throws Exception {
  String cfg=Files.readString(Path.of("src/main/resources/application.yml"));
  boolean apply=Arrays.asList(args).contains("--apply");
  try(Connection connection=DriverManager.getConnection(config(cfg,"url"),config(cfg,"username"),config(cfg,"password"))) {
   db=connection;db.setAutoCommit(false);
   try {
    List<Map<String,Object>> cabinets=query("SELECT c.id,c.name,z.room_id FROM sp_cabinet c JOIN sp_zone z ON z.id=c.zone_id ORDER BY z.room_id,c.id");
    String[] levels={"critical","warning","normal","info"};
    String[] labels={"严重告警","重要告警","一般告警","提示告警"};
    String[] titles={"机柜进风温度超限","机柜功率偏高","机柜湿度偏高","机柜进风温度偏高"};
    String[] metrics={"temperature","power","humidity","temperature"},units={"℃","kW","%","℃"};
    double[] values={38,4.8,65,29},max={30,4,60,28};
    Map<String,Integer> counts=new LinkedHashMap<>();
    int inserted=0;
    for(Map<String,Object> cabinet:cabinets) {
     room=cabinet.get("room_id").toString();int index=counts.getOrDefault(room,0);
     if(index>=4)continue;
     counts.put(room,index+1);String id=cabinet.get("id").toString();
     if(apply) {
      SOURCE="MANUAL_DEVELOPMENT_SEED";
      record("ALARM",alarmId("cabinet-level-example-"+id),map("title",titles[index],"cabinetId",id,"entityType","CABINET","entityId",id,
       "severity",levels[index],"severityLabel",labels[index],"status","未处理","createdAt",now.minusSeconds(index*300L).toString(),
       "metric",metrics[index],"unit",units[index],"value",values[index],"threshold","≤ "+max[index]+" "+units[index],
       "rule",map("metric",metrics[index],"unit",units[index],"min",0,"max",max[index],"maxAgeSeconds",300,"normalSeconds",60,"maxGapSeconds",30),
       "advice","核对设备指标并检查相关设备运行状态。","dataNature","SIMULATED","recordOrigin","REAL"));
      // SeedMonitoring.record defaults to REAL; explicitly mark these examples as DEMO.
      try(PreparedStatement s=db.prepareStatement("UPDATE sp_monitor_record SET payload=JSON_SET(payload,'$.recordOrigin','DEMO') WHERE room_id=? AND kind='ALARM' AND id=?")) {
       s.setString(1,room);s.setString(2,alarmId("cabinet-level-example-"+id));s.executeUpdate();
      }
     }
     System.out.println("room="+room+", cabinet="+id+", level="+labels[index]);inserted++;
    }
    if(apply)db.commit();else db.rollback();
    System.out.println((apply?"Saved":"Planned")+" cabinet alarms="+inserted);
    if(apply)for(Map<String,Object> row:query("SELECT JSON_UNQUOTE(JSON_EXTRACT(payload,'$.severity')) AS severity,COUNT(*) AS count FROM sp_monitor_record WHERE kind='ALARM' AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.source'))='MANUAL_DEVELOPMENT_SEED' GROUP BY severity"))System.out.println(row);
   }catch(Exception e){db.rollback();throw e;}
  }
 }
}
