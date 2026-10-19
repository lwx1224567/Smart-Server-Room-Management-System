import java.sql.*;
import java.nio.file.*;
import java.util.*;

/** Read-only verification of the generated whole-room topology and inventory identities. */
public class VerifyRoomTopology extends SeedMonitoring {
 public static void main(String[]args)throws Exception{
  String cfg=Files.readString(Path.of("src/main/resources/application.yml"));
  try(Connection connection=DriverManager.getConnection(config(cfg,"url"),config(cfg,"username"),config(cfg,"password"))){
   db=connection;room=query("SELECT id FROM sp_room ORDER BY (record_origin='REAL') DESC,id LIMIT 1").get(0).get("id").toString();
   List<Map<String,Object>> devices=query("SELECT d.id,d.cabinet_id cabinetId,d.name,d.code,d.device_type type,d.u_start uStart,d.u_height uHeight,d.scene_model_key sceneModelKey FROM sp_device d JOIN sp_cabinet c ON c.id=d.cabinet_id JOIN sp_zone z ON z.id=c.zone_id WHERE z.room_id="+room);
   List<Map<String,Object>> cabinets=query("SELECT c.id,c.code,c.name,c.zone_id zoneId,c.capacity_u capacityU,c.scene_model_key sceneModelKey FROM sp_cabinet c JOIN sp_zone z ON z.id=c.zone_id WHERE z.room_id="+room);
   List<Map<String,Object>> zones=query("SELECT id,code,name FROM sp_zone WHERE room_id="+room);
   List<Map<String,Object>> nodes=new ArrayList<>(),links=new ArrayList<>();
   for(Map<String,Object>r:query("SELECT kind,payload FROM sp_monitor_record WHERE room_id="+room+" AND kind IN ('NODE','LINK')")){
    Map<String,Object>payload=json.readValue(r.get("payload").toString(),Map.class);("NODE".equals(r.get("kind"))?nodes:links).add(payload);
   }
   Map<String,String> inventory=new HashMap<>();for(Map<String,Object>d:devices)inventory.put(d.get("id").toString(),d.get("cabinetId").toString());
   Set<String> seenDevices=new HashSet<>(),seenCabinets=new HashSet<>();Map<String,Set<String>> graph=new HashMap<>();
   for(Map<String,Object>n:nodes){String device=n.get("deviceId").toString(),cabinet=n.get("cabinetId").toString();if(!cabinet.equals(inventory.get(device))||!seenDevices.add(device))throw new IllegalStateException("Invalid device identity");seenCabinets.add(cabinet);graph.put(n.get("id").toString(),new HashSet<>());}
   Set<String> edges=new HashSet<>();
   for(Map<String,Object>l:links){String from=l.get("from").toString(),to=l.get("to").toString();if(!graph.containsKey(from)||!graph.containsKey(to)||from.equals(to)||!edges.add(from+"->"+to))throw new IllegalStateException("Invalid link");graph.get(from).add(to);graph.get(to).add(from);}
   Set<String> visited=new HashSet<>();Deque<String> queue=new ArrayDeque<>();queue.add(graph.keySet().iterator().next());while(!queue.isEmpty()){String id=queue.remove();if(visited.add(id))queue.addAll(graph.get(id));}
   if(seenDevices.size()!=devices.size()||seenCabinets.size()!=cabinets.size()||visited.size()!=nodes.size())throw new IllegalStateException("Incomplete room coverage");
   for(Map<String,Object>n:nodes){Map p=(Map)n.get("ports");int used=((Number)p.get("used")).intValue();if(used!=graph.get(n.get("id").toString()).size()||used>((Number)p.get("total")).intValue())throw new IllegalStateException("Invalid port counts");}
   // Export read-only database responses for browser rendering checks without requiring a running server.
   for(Map<String,Object>r:devices){r.put("id",r.get("id").toString());r.put("cabinetId",r.get("cabinetId").toString());}
   for(Map<String,Object>r:cabinets){r.put("id",r.get("id").toString());r.put("zoneId",r.get("zoneId").toString());}
   for(Map<String,Object>r:zones)r.put("id",r.get("id").toString());
   Map<String,Object> scene=map("roomId",room,"cabinets",cabinets,"devices",devices,"zones",zones);
   Map<String,Object> snapshot=map("roomId",room,"nodes",nodes,"links",links,"readings",List.of(),"alarms",List.of(),"facilities",List.of(),"events",List.of(),"logs",List.of());
   Files.writeString(Path.of("target/room-topology-verification.json"),json.writeValueAsString(map("scene",scene,"monitoring",snapshot)));
   System.out.println("PASS: cabinets="+seenCabinets.size()+", devices="+seenDevices.size()+", links="+links.size()+", connectedNodes="+visited.size()+", identities and port counts valid");
  }
 }
}
