import java.sql.*;
import java.nio.file.*;
import java.util.*;

/** Explicit development-only history for existing simulated topology links. */
public class BackfillLinkQuality extends SeedMonitoring {
 public static void main(String[] args)throws Exception {
  String cfg=Files.readString(Path.of("src/main/resources/application.yml"));
  boolean apply=Arrays.asList(args).contains("--apply");
  try(Connection connection=DriverManager.getConnection(config(cfg,"url"),config(cfg,"username"),config(cfg,"password"))){
   db=connection;db.setAutoCommit(false);
   try {
    int links=0,points=0;
    for(Map<String,Object> row:query("SELECT room_id,id,payload FROM sp_monitor_record WHERE kind='LINK'")){
     Map<String,Object> link=json.readValue(row.get("payload").toString(),Map.class);
     if(!Boolean.TRUE.equals(link.get("developmentSimulated")))continue;
     if(link.containsKey("qualityHistory"))continue;
     double bandwidth=((Number)link.get("bandwidth")).doubleValue(),latency=((Number)link.get("latency")).doubleValue(),loss=((Number)link.get("loss")).doubleValue();
     List<Map<String,Object>> history=new ArrayList<>();
     for(int i=0;i<13;i++){
      double factor=i==12?1:1+Math.sin(i*.8+Math.floorMod(row.get("id").hashCode(),7))*.16;
      history.add(map("observedAt",now.minusSeconds((12-i)*300).toString(),"bandwidth",bandwidth,"latency",i==12?latency:Math.round(latency*factor*100)/100.0,"loss",i==12?loss:Math.round(loss*factor*100)/100.0));
     }
     link.put("qualityHistory",history);link.put("observedAt",now.toString());link.put("qualityHistorySource","DEVELOPMENT_LINK_QUALITY");
     if(apply)try(PreparedStatement s=db.prepareStatement("UPDATE sp_monitor_record SET payload=? WHERE room_id=? AND kind='LINK' AND id=?")){
      s.setString(1,json.writeValueAsString(link));s.setString(2,row.get("room_id").toString());s.setString(3,row.get("id").toString());s.executeUpdate();
     }
     links++;points+=history.size();
    }
    if(apply)db.commit();else db.rollback();
    System.out.println((apply?"APPLY":"INSPECT")+": links="+links+", historyPoints="+points);
    List<Map<String,Object>> stored=query("SELECT payload FROM sp_monitor_record WHERE kind='LINK' AND JSON_EXTRACT(payload,'$.qualityHistory') IS NOT NULL");
    for(Map<String,Object> row:stored){Map link=json.readValue(row.get("payload").toString(),Map.class);List<Map> history=(List<Map>)link.get("qualityHistory");if(history.size()!=13)throw new IllegalStateException("Incomplete history");Map last=history.get(12);for(String metric:List.of("bandwidth","latency","loss"))if(Math.abs(((Number)last.get(metric)).doubleValue()-((Number)link.get(metric)).doubleValue())>0.005)throw new IllegalStateException("Latest sample mismatch");}
    System.out.println("Verified persisted histories="+stored.size());
   }catch(Exception e){db.rollback();throw e;}
  }
 }
}
