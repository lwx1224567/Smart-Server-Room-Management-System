import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Only adds generated inventory; retains existing rows, geometry and user data. */
public class ExtendCabinetInventory {
 static int seed(Connection c) throws Exception {
  List<String> cabinets=new ArrayList<>();
  try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT code FROM sp_cabinet WHERE code NOT IN ('A-01','A-02') ORDER BY code")){while(r.next())cabinets.add(r.getString(1));}
  if(cabinets.size()!=40)throw new IllegalStateException("Expected 40 additional cabinets");
  int added=0;
  String sql="INSERT INTO sp_device(cabinet_id,code,name,device_type,scene_model_key,legacy_key,registration_status,u_start,u_height,network_layer,record_origin,source,source_updated_at,created_at,updated_at) SELECT c.id,?,?,?,?,?,'ACTIVE',NULL,NULL,?,'DEMO','SIMULATOR',NULL,NOW(3),NOW(3) FROM sp_cabinet c WHERE c.code=? AND NOT EXISTS(SELECT 1 FROM sp_device d WHERE d.code=?)";
  for(int i=0;i<cabinets.size();i++)for(int slot=1;slot<=3;slot++){
   String cabinet=cabinets.get(i),type=i%3==0?"SERVER":i%3==1?"SWITCH":slot==3?"SWITCH":"SERVER";
   String code="GEN-"+cabinet+"-"+slot,key="generated-"+cabinet+"-"+slot;
   try(PreparedStatement s=c.prepareStatement(sql)){s.setString(1,code);s.setString(2,cabinet+" "+(type.equals("SERVER")?"服务器":"交换机")+" "+slot);s.setString(3,type);s.setString(4,key);s.setString(5,key);s.setString(6,type.equals("SERVER")?"TERMINAL":"ACCESS");s.setString(7,cabinet);s.setString(8,code);added+=s.executeUpdate();}
  }
  return added;
 }
 static void validate(Connection c) throws Exception {
  if(Stage1Database.count(c,"sp_device")!=129||Stage1Database.count(c,"sp_cabinet")!=42||Stage1Database.count(c,"sp_zone")!=5)throw new IllegalStateException("Invalid counts");
  try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT c.code,COUNT(d.id) FROM sp_cabinet c LEFT JOIN sp_device d ON d.cabinet_id=c.id GROUP BY c.id,c.code")){int n=0;while(r.next()){if(r.getInt(2)!=(r.getString(1).equals("A-01")?6:3))throw new IllegalStateException("Invalid cabinet ownership");n++;}if(n!=42)throw new IllegalStateException("Invalid cabinet count");}
 }
 public static void main(String[] args) throws Exception {
  String config=Files.readString(Paths.get(args[0],"src/main/resources/application.yml"));
  String url=Stage1Database.setting(config,"url"),user=Stage1Database.setting(config,"username"),password=Stage1Database.setting(config,"password");
  // Validate in the independent test database before changing the target.
  for(String dbUrl:Arrays.asList(url.replace("/platform_smart_park?","/"+Stage1Database.TEST_DB+"?"),url))try(Connection c=DriverManager.getConnection(dbUrl,user,password)){
   long before=Stage1Database.count(c,"sp_device");if(before!=9&&before!=129)throw new IllegalStateException("Unexpected baseline; stopped");
   if(before==9)Stage1Database.validate(c);else validate(c);
   String fingerprint=dbUrl.equals(url)?Stage1Database.userFingerprint(c):null;
   c.setAutoCommit(false);
   try{int added=seed(c);validate(c);int repeat=seed(c);if(repeat!=0)throw new IllegalStateException("Repeat inserted rows");if(fingerprint!=null&&!fingerprint.equals(Stage1Database.userFingerprint(c)))throw new IllegalStateException("User changed");c.commit();System.out.println((dbUrl.equals(url)?"TARGET":"TEST")+" added="+added+" repeat="+repeat+" zones=5 cabinets=42 devices=129 userUnchanged="+(fingerprint!=null));}catch(Exception e){c.rollback();throw e;}
  }catch(SQLException e){throw new IllegalStateException("SQLState="+e.getSQLState()+" code="+e.getErrorCode());}
 }
}
