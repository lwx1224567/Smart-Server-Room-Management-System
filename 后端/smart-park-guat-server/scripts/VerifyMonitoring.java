import java.sql.*;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.regex.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Focused live API check; never logs credentials or tokens. */
public class VerifyMonitoring {
 static ObjectMapper json=new ObjectMapper();static HttpClient client=HttpClient.newHttpClient();static String base,token;
 static String config(String text,String key){Matcher m=Pattern.compile("(?m)^\\s*"+key+":\\s*(.+)$").matcher(text);if(!m.find())throw new IllegalArgumentException(key);return m.group(1).trim();}
 static Object request(String path,String method,Object body)throws Exception{HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(base+path)).header("Content-Type","application/json");if(token!=null)b.header("Authorization","Bearer "+token);b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));HttpResponse<String>r=client.send(b.build(),HttpResponse.BodyHandlers.ofString());Map result=json.readValue(r.body(),Map.class);if(r.statusCode()!=200)throw new IllegalStateException("API "+path+" status "+r.statusCode());return result.get("data");}
 public static void main(String[]args)throws Exception{base=args.length==0?"http://127.0.0.1:8082":args[0];String cfg=Files.readString(Path.of("src/main/resources/application.yml"));try(Connection db=DriverManager.getConnection(config(cfg,"url"),config(cfg,"username"),config(cfg,"password"));Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT user_name,password FROM `user` WHERE LOWER(user_name)='admin' LIMIT 1")){if(!r.next())throw new IllegalStateException("No existing admin");Map login=(Map)request("/api/v1/auth/login","POST",Map.of("userName",r.getString(1),"password",r.getString(2)));token=(String)login.get("token");}
  try{Map rooms=(Map)request("/api/v1/rooms?recordOrigin=REAL","GET",null);List items=(List)rooms.get("items");if(items.isEmpty()){rooms=(Map)request("/api/v1/rooms?recordOrigin=DEMO","GET",null);items=(List)rooms.get("items");}String room=(String)((Map)items.get(0)).get("id");Map data=(Map)request("/api/v1/rooms/"+room+"/monitoring","GET",null);Map<String,Object>counts=new LinkedHashMap<>();for(String key:Arrays.asList("nodes","links","facilities","alarms","readings","events","logs"))counts.put(key,((List)data.get(key)).size());for(String period:Arrays.asList("today","7d","30d")){Map energy=(Map)request("/api/v1/rooms/"+room+"/energy?period="+period,"GET",null);if(!Boolean.TRUE.equals(energy.get("available"))||energy.get("total")==null)throw new IllegalStateException("No energy "+period);counts.put(period+"Kwh",energy.get("total"));counts.put(period+"Top5",energy.get("ranks"));}System.out.println(json.writeValueAsString(counts));}finally{request("/api/v1/auth/logout","POST",Map.of());}
 }
}


