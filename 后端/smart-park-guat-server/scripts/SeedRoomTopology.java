import java.sql.*;
import java.nio.file.*;
import java.util.*;

/** Generate a complete DEVELOPMENT topology from existing inventory; never physical discovery. */
public class SeedRoomTopology extends SeedMonitoring {
 public static void main(String[]args)throws Exception{
  boolean apply=Arrays.asList(args).contains("--apply");
  String cfg=Files.readString(Path.of("src/main/resources/application.yml"));
  try(Connection connection=DriverManager.getConnection(config(cfg,"url"),config(cfg,"username"),config(cfg,"password"))){
   db=connection;db.setAutoCommit(false);SOURCE="INTEGRATION_TOPOLOGY";
   try{
    room=query("SELECT id FROM sp_room ORDER BY (record_origin='REAL') DESC,id LIMIT 1").get(0).get("id").toString();
    List<Map<String,Object>> cabinets=query("SELECT c.id,c.code,z.code zone FROM sp_cabinet c JOIN sp_zone z ON z.id=c.zone_id WHERE z.room_id="+room+" ORDER BY z.code,c.code");
    List<Map<String,Object>> devices=query("SELECT d.id,d.code,d.name,d.device_type,d.cabinet_id,c.code cabinet_code,z.code zone FROM sp_device d JOIN sp_cabinet c ON c.id=d.cabinet_id JOIN sp_zone z ON z.id=c.zone_id WHERE z.room_id="+room+" ORDER BY z.code,c.code,d.id");
    List<Map<String,Object>> switches=new ArrayList<>();for(Map<String,Object>d:devices)if("SWITCH".equals(d.get("device_type")))switches.add(d);
    if(switches.size()<2)throw new IllegalStateException("Two existing switches required");
    Set<String> cores=new LinkedHashSet<>();cores.add(switches.get(0).get("id").toString());cores.add(switches.get(1).get("id").toString());
    Map<String,String> aggregations=new LinkedHashMap<>(),access=new LinkedHashMap<>(),layers=new LinkedHashMap<>();
    for(Map<String,Object>d:switches){String id=d.get("id").toString(),zone=d.get("zone").toString();if(!cores.contains(id))aggregations.putIfAbsent(zone,id);}
    for(Map<String,Object>c:cabinets)if(!aggregations.containsKey(c.get("zone").toString()))throw new IllegalStateException("Missing zone switch");
    for(Map<String,Object>d:switches){String id=d.get("id").toString();layers.put(id,cores.contains(id)?"core":aggregations.containsValue(id)?"aggregate":"access");access.putIfAbsent(d.get("cabinet_id").toString(),id);}
    List<Map<String,Object>> links=new ArrayList<>();Map<String,Integer> ports=new HashMap<>();Set<String> covered=new HashSet<>();
    String core=cores.iterator().next();int ordinal=0;
    for(Map<String,Object>d:devices){
     String id=d.get("id").toString(),cabinet=d.get("cabinet_id").toString(),zone=d.get("zone").toString();covered.add(cabinet);
     String layer=layers.getOrDefault(id,"terminal"),parent=null;
     if(cores.contains(id)){if(!core.equals(id))parent=core;}
     else if("aggregate".equals(layer))parent=core;
     else if("access".equals(layer))parent=aggregations.get(zone);
     else parent=access.getOrDefault(cabinet,aggregations.get(zone));
     if(parent!=null){boolean error=ordinal++==7;String from="room-device-"+parent,to="room-device-"+id;
      links.add(map("id","room-link-"+id,"from",from,"to",to,"status",error?"error":"normal","bandwidth","terminal".equals(layer)?1000:10000,"latency",error?12.6:.5+(ordinal%8)*.12,"loss",error?2.4:0,"developmentSimulated",true));
      ports.merge(parent,1,Integer::sum);ports.merge(id,1,Integer::sum);
     }
    }
    if(covered.size()!=cabinets.size())throw new IllegalStateException("Cabinet without inventory devices; cannot generate a device connection");
    if(links.size()!=devices.size()-1)throw new IllegalStateException("Incomplete topology");
    if(apply){
     // Replace only earlier generated topology; retain any manually registered records.
     try(PreparedStatement s=db.prepareStatement("DELETE FROM sp_monitor_record WHERE room_id=? AND kind IN ('NODE','LINK') AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.source')) IN ('INTEGRATION_SEED','INTEGRATION_TOPOLOGY')")){s.setString(1,room);s.executeUpdate();}
     for(Map<String,Object>d:devices){String id=d.get("id").toString();int used=ports.getOrDefault(id,0),total="SWITCH".equals(d.get("device_type"))?48:4;if(used>total)throw new IllegalStateException("Insufficient ports on "+id);
      record("NODE","room-device-"+id,map("name",d.get("name"),"code",d.get("code"),"deviceId",id,"cabinetId",d.get("cabinet_id").toString(),"cabinetCode",d.get("cabinet_code"),"zoneCode",d.get("zone"),"type",d.get("device_type"),"layer",layers.getOrDefault(id,"terminal"),"status","正常","ports",map("total",total,"used",used,"errors",0),"developmentSimulated",true));
     }
     for(Map<String,Object>link:links)record("LINK",link.get("id").toString(),link);
     db.commit();
    }else db.rollback();
    System.out.println("mode="+(apply?"APPLY":"INSPECT")+", room="+room+", cabinets="+covered.size()+", nodes="+devices.size()+", links="+links.size()+", source="+SOURCE);
   }catch(Exception e){db.rollback();throw e;}
  }
 }
}
