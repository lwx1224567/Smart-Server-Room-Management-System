import java.sql.*;
import java.nio.file.*;
import java.util.*;

/** Live read-only smoke check; login/logout only, no business data mutations. */
public class VerifyIntegrationReads extends VerifyMonitoring {
 public static void main(String[] args)throws Exception {
  base=args.length==0?"http://127.0.0.1:8082":args[0];String cfg=Files.readString(Path.of("src/main/resources/application.yml"));
  try(Connection db=DriverManager.getConnection(config(cfg,"url"),config(cfg,"username"),config(cfg,"password"));Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT user_name,password FROM `user` WHERE LOWER(user_name)='admin' LIMIT 1")){if(!r.next())throw new IllegalStateException("No existing admin");token=(String)((Map)request("/api/v1/auth/login","POST",Map.of("userName",r.getString(1),"password",r.getString(2)))).get("token");}
  try {
   Map<String,Object> result=new LinkedHashMap<>();
   List<Map<String,Object>> rooms=(List)request("/api/v1/admin/rooms","GET",null);if(rooms.isEmpty())throw new IllegalStateException("No rooms");String room=rooms.get(0).get("id").toString();
   result.put("capabilities",request("/api/v1/integrations","GET",null));result.put("collector",request("/api/v1/integrations/collector","GET",null));
   for(String kind:Arrays.asList("parks","rooms","zones","cabinets","devices"))result.put(kind,((List)request("/api/v1/admin/"+kind,"GET",null)).size());
   result.put("alarmRules",((List)request("/api/v1/rooms/"+room+"/alarm-rules","GET",null)).size());
   System.out.println(json.writeValueAsString(result));
  }finally{request("/api/v1/auth/logout","POST",Map.of());}
 }
}
