import java.sql.*;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.regex.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Focused persistence check; restores the previous control record and removes only its own logs. */
public class VerifyCabinetControls {
 static ObjectMapper json=new ObjectMapper();static String token;
 static String config(String text,String key){Matcher m=Pattern.compile("(?m)^\\s*"+key+":\\s*(.+)$").matcher(text);if(!m.find())throw new IllegalArgumentException(key);return m.group(1).trim();}
 static Map request(String path,String method,Object body)throws Exception{HttpRequest.Builder b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:8083"+path)).header("Content-Type","application/json");if(token!=null)b.header("Authorization","Bearer "+token);b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));HttpResponse<String> r=HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());if(r.statusCode()!=200)throw new IllegalStateException("Unexpected status "+r.statusCode());return (Map)json.readValue(r.body(),Map.class).get("data");}
 public static void main(String[]args)throws Exception{
  String cfg=Files.readString(Path.of("src/main/resources/application.yml"));
  try(Connection db=DriverManager.getConnection(config(cfg,"url"),config(cfg,"username"),config(cfg,"password"))){
   try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT user_name,password FROM `user` WHERE LOWER(user_name)='admin' LIMIT 1")){r.next();token=(String)request("/api/v1/auth/login","POST",Map.of("userName",r.getString(1),"password",r.getString(2))).get("token");}
   String room,cabinet,original=null;List<String> logs=new ArrayList<>();
   try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT z.room_id,c.id FROM sp_cabinet c JOIN sp_zone z ON c.zone_id=z.id ORDER BY c.id LIMIT 1")){r.next();room=r.getString(1);cabinet=r.getString(2);}
   try(PreparedStatement s=db.prepareStatement("SELECT payload FROM sp_monitor_record WHERE room_id=? AND kind='CONTROL' AND id=?")){s.setString(1,room);s.setString(2,cabinet);try(ResultSet r=s.executeQuery()){if(r.next())original=r.getString(1);}}
   try{
    for(String key:Arrays.asList("lighting","fans","locked","power")){Map result=request("/api/v1/rooms/"+room+"/cabinets/"+cabinet+"/controls","PUT",Map.of("key",key,"value",false));logs.add((String)((Map)result.get("log")).get("id"));}
    Map snapshot=request("/api/v1/rooms/"+room+"/monitoring","GET",null);Map state=null;for(Object row:(List)snapshot.get("controls"))if(cabinet.equals(((Map)row).get("cabinetId")))state=(Map)row;
    if(state==null)throw new IllegalStateException("Controls missing");for(String key:Arrays.asList("lighting","fans","locked","power"))if(!Boolean.FALSE.equals(state.get(key)))throw new IllegalStateException("State mismatch");
    Set<String> saved=new HashSet<>();for(Object row:(List)snapshot.get("logs"))saved.add((String)((Map)row).get("id"));if(!saved.containsAll(logs))throw new IllegalStateException("Logs missing");
    System.out.println("PASS: four controls and their operation logs survive a new API read");
   }finally{
    for(String id:logs)try(PreparedStatement s=db.prepareStatement("DELETE FROM sp_monitor_record WHERE room_id=? AND kind='LOG' AND id=?")){s.setString(1,room);s.setString(2,id);s.executeUpdate();}
    if(original==null){try(PreparedStatement s=db.prepareStatement("DELETE FROM sp_monitor_record WHERE room_id=? AND kind='CONTROL' AND id=?")){s.setString(1,room);s.setString(2,cabinet);s.executeUpdate();}}
    else try(PreparedStatement s=db.prepareStatement("UPDATE sp_monitor_record SET payload=? WHERE room_id=? AND kind='CONTROL' AND id=?")){s.setString(1,original);s.setString(2,room);s.setString(3,cabinet);s.executeUpdate();}
    request("/api/v1/auth/logout","POST",Map.of());
   }
  }
 }
}
