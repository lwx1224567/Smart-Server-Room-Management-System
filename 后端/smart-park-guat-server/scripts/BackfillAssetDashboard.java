import java.sql.*;
import java.nio.file.*;
import java.util.*;

/** Explicit development backfill; default mode inspects without changing records. */
public class BackfillAssetDashboard extends SeedMonitoring {
 public static void main(String[] args)throws Exception{
  boolean apply=Arrays.asList(args).contains("--apply");
  String cfg=Files.readString(Path.of("src/main/resources/application.yml"));
  try(Connection connection=DriverManager.getConnection(config(cfg,"url"),config(cfg,"username"),config(cfg,"password"))){
   db=connection;db.setAutoCommit(false);
   try{
    room=String.valueOf(query("SELECT id FROM sp_room ORDER BY (record_origin='REAL') DESC,id LIMIT 1").get(0).get("id"));
    List<Map<String,Object>> cabinets=query("SELECT c.id,c.capacity_u FROM sp_cabinet c JOIN sp_zone z ON z.id=c.zone_id WHERE z.room_id="+room+" ORDER BY c.id");
    int positions=0,statuses=0,total=0,used=0,normal=0,abnormal=0,unknown=0;
    for(Map<String,Object> cabinet:cabinets){
     String cabinetId=cabinet.get("id").toString();int capacity=((Number)cabinet.get("capacity_u")).intValue();total+=capacity;
     boolean[] occupied=new boolean[capacity+1];
     List<Map<String,Object>> devices=query("SELECT id,device_type,u_start,u_height,source FROM sp_device WHERE cabinet_id="+cabinetId+" ORDER BY id");
     for(Map<String,Object> device:devices){
      if(device.get("u_start")==null||device.get("u_height")==null)continue;
      int start=((Number)device.get("u_start")).intValue(),height=((Number)device.get("u_height")).intValue();
      if(start<1||height<1||start+height-1>capacity)throw new IllegalStateException("Existing U position outside cabinet "+cabinetId);
      for(int u=start;u<start+height;u++){if(occupied[u])throw new IllegalStateException("Existing overlapping U positions in cabinet "+cabinetId);occupied[u]=true;}
     }
     for(Map<String,Object> device:devices){
      String id=device.get("id").toString();
      if(device.get("u_start")==null||device.get("u_height")==null){
       int height=device.get("u_height")==null?("STORAGE".equals(device.get("device_type"))?4:"SERVER".equals(device.get("device_type"))?2:1):((Number)device.get("u_height")).intValue();
       int start=device.get("u_start")==null?1:((Number)device.get("u_start")).intValue();
       for(;start+height-1<=capacity;start++){boolean free=true;for(int u=start;u<start+height;u++)if(occupied[u])free=false;if(free)break;if(device.get("u_start")!=null)throw new IllegalStateException("Partial U position overlaps");}
       if(start<1||height<1||start+height-1>capacity)throw new IllegalStateException("Insufficient cabinet capacity "+cabinetId);
       for(int u=start;u<start+height;u++)occupied[u]=true;
       device.put("u_start",start);device.put("u_height",height);positions++;
       if(apply)try(PreparedStatement s=db.prepareStatement("UPDATE sp_device SET u_start=?,u_height=?,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND (u_start IS NULL OR u_height IS NULL)")){s.setInt(1,start);s.setInt(2,height);s.setString(3,id);s.executeUpdate();}
      }
      used+=((Number)device.get("u_height")).intValue();
      List<Map<String,Object>> online=query("SELECT value FROM sp_reading WHERE room_id="+room+" AND entity_type='DEVICE' AND entity_id='"+id+"' AND metric='online' AND quality='GOOD' AND record_origin='REAL' ORDER BY observed_at DESC,id DESC LIMIT 1");
      if(online.isEmpty()){statuses++;if(apply)reading("DEVICE",id,"online",1,"",now);else unknown++;if(apply)normal++;}
      else if(((Number)online.get(0).get("value")).doubleValue()==1)normal++;else abnormal++;
     }
    }
    if(apply)db.commit();else db.rollback();
    System.out.println("mode="+(apply?"APPLY":"INSPECT")+", room="+room+", cabinets="+cabinets.size()+", U positions filled="+positions+", online readings filled="+statuses);
    System.out.println("normal="+normal+", abnormal="+abnormal+", unknown="+unknown+", usedU="+used+", freeU="+(total-used)+", utilization="+String.format(Locale.ROOT,"%.1f",used*100.0/total)+"%");
   }catch(Exception e){db.rollback();throw e;}
  }
 }
}
