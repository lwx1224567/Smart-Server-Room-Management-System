import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

/** Explicit, opt-in stage 1 migration tool. Never loads repository legacy dumps. */
public class Stage1Database {
  static final String TEST_DB="platform_smart_park_stage1_test_20261008";
  static final String[] BUSINESS={"sp_park","sp_room","sp_zone","sp_cabinet","sp_device"};
  static long count(Connection c,String table) throws Exception {
    try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getLong(1);}
  }
  static int script(Connection c,Path path) throws Exception {
    String sql=new String(Files.readAllBytes(path),StandardCharsets.UTF_8);
    if(Pattern.compile("(?i)\\b(DROP|TRUNCATE|REPLACE)\\b|INSERT\\s+IGNORE").matcher(sql).find())throw new IllegalStateException("Forbidden migration statement");
    int inserted=0;
    for(String part:sql.split(";")) if(!part.trim().isEmpty())try(Statement s=c.createStatement()){s.execute(part);if(s.getUpdateCount()>0)inserted+=s.getUpdateCount();}
    return inserted;
  }
  static List<String> snapshot(Connection c) throws Exception {
    List<String> out=new ArrayList<>();
    String[] queries={
      "SELECT code,name,record_origin,source,source_updated_at FROM sp_park ORDER BY code",
      "SELECT p.code,r.code,r.name,r.location,r.manager_display_name,r.scene_model_key,r.record_origin,r.source,r.source_updated_at FROM sp_room r JOIN sp_park p ON p.id=r.park_id ORDER BY r.code",
      "SELECT r.code,z.code,z.name,z.display_order,z.record_origin,z.source,z.source_updated_at FROM sp_zone z JOIN sp_room r ON r.id=z.room_id ORDER BY z.code",
      "SELECT z.code,c.code,c.name,c.serial_number,c.capacity_u,c.scene_model_key,c.grid_row,c.grid_column,c.position_x,c.position_y,c.position_z,c.registration_status,c.record_origin,c.source,c.source_updated_at FROM sp_cabinet c JOIN sp_zone z ON z.id=c.zone_id ORDER BY c.code",
      "SELECT c.code,d.code,d.name,d.device_type,d.scene_model_key,d.legacy_key,d.registration_status,d.u_start,d.u_height,d.network_layer,d.record_origin,d.source,d.source_updated_at FROM sp_device d JOIN sp_cabinet c ON c.id=d.cabinet_id ORDER BY d.code"};
    for(String q:queries)try(Statement s=c.createStatement();ResultSet r=s.executeQuery(q)){while(r.next()){StringBuilder b=new StringBuilder();for(int i=1;i<=r.getMetaData().getColumnCount();i++)b.append(r.getString(i)).append('|');out.add(b.toString());}}
    return out;
  }
  static void validate(Connection c) throws Exception {
    long[] expected={1,1,5,42,9};for(int i=0;i<BUSINESS.length;i++)if(count(c,BUSINESS[i])!=expected[i])throw new IllegalStateException("Unexpected inventory count: "+BUSINESS[i]);
    for(String t:BUSINESS)try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM "+t+" WHERE record_origin<>'DEMO' OR source<>'MOCK_IMPORT' OR source_updated_at IS NOT NULL")){r.next();if(r.getLong(1)!=0)throw new IllegalStateException("Invalid demo provenance");}
    Map<String,Integer> zones=new TreeMap<>();
    try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT z.code,c.code,c.scene_model_key,c.grid_column,c.grid_row,c.position_x,c.position_y,c.position_z FROM sp_cabinet c JOIN sp_zone z ON z.id=c.zone_id ORDER BY c.grid_column,c.grid_row")){
      int index=0;while(r.next()){int col=index/7,row=index%7;String z=row<4?(col<2?"A":col<4?"B":"C"):(col<3?"D":"E");int n=zones.getOrDefault(z,0)+1;zones.put(z,n);String key=String.format("%s-%02d",z,n);
        if(!z.equals(r.getString(1))||!key.equals(r.getString(2))||!key.equals(r.getString(3))||col!=r.getInt(4)||row!=r.getInt(5)||Math.abs(r.getDouble(6)-(-6+col*2.4))>0.00001||r.getDouble(7)!=0||Math.abs(r.getDouble(8)-(-4.8+row*1.45))>0.00001)throw new IllegalStateException("Cabinet mapping mismatch");index++;}}
    String[] keys={"core","agg","sw1","sw2","srv1","srv2","nas","srv3","srv4"};Set<String> remaining=new HashSet<>(Arrays.asList(keys));
    try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT d.scene_model_key,c.code,d.u_start,d.u_height FROM sp_device d JOIN sp_cabinet c ON c.id=d.cabinet_id")){while(r.next()){String key=r.getString(1);String cab=Arrays.asList("sw2","srv3","srv4").contains(key)?"A-02":"A-01";if(!remaining.remove(key)||!cab.equals(r.getString(2))||r.getObject(3)!=null||r.getObject(4)!=null)throw new IllegalStateException("Device mapping mismatch");}}
    if(!remaining.isEmpty())throw new IllegalStateException("Missing devices");
    try(PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=?")){s.setString(1,c.getCatalog());try(ResultSet r=s.executeQuery()){r.next();if(r.getInt(1)!=4)throw new IllegalStateException("Foreign key mismatch");}}
    System.out.println("VALIDATED counts=1/1/5/42/9 coordinates=42 deviceMappings=9 foreignKeys=4");
  }
  static String setting(String text,String key) {
    Matcher m=Pattern.compile("(?m)^\\s*"+key+":\\s*(.+?)\\s*$").matcher(text);
    if(!m.find()) throw new IllegalStateException("Missing connection setting");
    return m.group(1);
  }
  static String userFingerprint(Connection c) throws Exception {
    MessageDigest digest=MessageDigest.getInstance("SHA-256");
    try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT * FROM user ORDER BY id")) {
      while(r.next()) for(int i=1;i<=r.getMetaData().getColumnCount();i++) {
        String v=r.getString(i); digest.update((v==null?"NULL":v).getBytes(StandardCharsets.UTF_8));digest.update((byte)0);
      }
    }
    StringBuilder b=new StringBuilder();for(byte v:digest.digest()) b.append(String.format("%02x",v));return b.toString();
  }
  static List<String> tables(Connection c) throws Exception {
    List<String> out=new ArrayList<>();try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SHOW TABLES")){while(r.next())out.add(r.getString(1));}return out;
  }
  public static void main(String[] args) throws Exception {
    Path root=Paths.get(args[0]);String config=new String(Files.readAllBytes(root.resolve("src/main/resources/application.yml")),StandardCharsets.UTF_8);
    String url=setting(config,"url"),user=setting(config,"username"),password=setting(config,"password");
    String action=args.length>1?args[1]:"audit";
    try(Connection c=DriverManager.getConnection(url,user,password)) {
      if(action.equals("audit")) {
        c.setReadOnly(true);c.setAutoCommit(false);
        System.out.println("TARGET_TABLES="+tables(c));
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT VERSION(),(SELECT COUNT(*) FROM user)")){r.next();System.out.println("MYSQL="+r.getString(1)+" USER_COUNT="+r.getLong(2));}
        System.out.println("USER_FINGERPRINT="+userFingerprint(c));
        c.rollback();return;
      }
      if(action.equals("verify")||action.equals("seed-check")) {
        List<String> expectedTables=new ArrayList<>(Arrays.asList(BUSINESS));expectedTables.add("user");Collections.sort(expectedTables);
        List<String> actual=tables(c);Collections.sort(actual);if(!actual.equals(expectedTables))throw new IllegalStateException("Target structure changed; stopped");
        if(count(c,"user")!=3||!userFingerprint(c).equals("a2e89aec1cbcab95c875663a1681cad7ba76803cc64a5577f935c78c5c1190a9"))throw new IllegalStateException("User baseline changed; stopped");
        if(action.equals("verify")){c.setReadOnly(true);c.setAutoCommit(false);validate(c);System.out.println("USER_UNCHANGED="+userFingerprint(c));c.rollback();return;}
        validate(c);List<String> before=snapshot(c);c.setAutoCommit(false);
        try{int inserted=script(c,root.resolve("database/migrations/V002__inventory_demo_seed.sql"));if(inserted!=0||!before.equals(snapshot(c)))throw new IllegalStateException("Repeat seed changed target");c.commit();System.out.println("REPEAT_INSERTED="+inserted);}catch(Exception e){c.rollback();throw e;}return;
      }
      if(!action.equals("test")&&!action.equals("migrate"))throw new IllegalArgumentException("Unsupported action");
      if(!tables(c).equals(Arrays.asList("user")))throw new IllegalStateException("Target structure changed; migration stopped");
      if(count(c,"user")!=3 || !userFingerprint(c).equals("a2e89aec1cbcab95c875663a1681cad7ba76803cc64a5577f935c78c5c1190a9"))throw new IllegalStateException("Target user baseline changed; migration stopped");
      String testUrl=url.replace("/platform_smart_park?","/"+TEST_DB+"?");
      if(action.equals("test")) {
        try(PreparedStatement s=c.prepareStatement("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=?")){s.setString(1,TEST_DB);try(ResultSet r=s.executeQuery()){r.next();if(r.getInt(1)!=0)throw new IllegalStateException("Test database already exists; stopped to protect data");}}
        try(Statement s=c.createStatement()){s.execute("CREATE DATABASE "+TEST_DB+" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");}
        try(Connection t=DriverManager.getConnection(testUrl,user,password)) {
          script(t,root.resolve("database/migrations/V001__inventory_schema.sql"));t.setAutoCommit(false);
          script(t,root.resolve("database/migrations/V002__inventory_demo_seed.sql"));validate(t);List<String> before=snapshot(t);t.commit();
          script(t,root.resolve("database/migrations/V002__inventory_demo_seed.sql"));validate(t);if(!before.equals(snapshot(t)))throw new IllegalStateException("Repeat seed changed data");t.commit();
          System.out.println("TEST_REPEAT_INSERTED=0 TARGET_UNCHANGED="+userFingerprint(c));
        }return;
      }
      List<String> expected;try(Connection t=DriverManager.getConnection(testUrl,user,password)){t.setReadOnly(true);validate(t);expected=snapshot(t);}
      script(c,root.resolve("database/migrations/V001__inventory_schema.sql"));c.setAutoCommit(false);
      try {script(c,root.resolve("database/migrations/V002__inventory_demo_seed.sql"));validate(c);if(!expected.equals(snapshot(c)))throw new IllegalStateException("Test/development inventory mismatch");if(!userFingerprint(c).equals("a2e89aec1cbcab95c875663a1681cad7ba76803cc64a5577f935c78c5c1190a9"))throw new IllegalStateException("User changed");c.commit();}catch(Exception e){c.rollback();throw e;}
      script(c,root.resolve("database/migrations/V002__inventory_demo_seed.sql"));validate(c);if(!expected.equals(snapshot(c)))throw new IllegalStateException("Repeat seed changed target");c.commit();
      System.out.println("DEVELOPMENT_MIGRATED=58 REPEAT_INSERTED=0 USER_UNCHANGED="+userFingerprint(c));
    } catch(SQLException e) { throw new IllegalStateException("Database operation failed: SQLState="+e.getSQLState()+", vendorCode="+e.getErrorCode()); }
  }
}
