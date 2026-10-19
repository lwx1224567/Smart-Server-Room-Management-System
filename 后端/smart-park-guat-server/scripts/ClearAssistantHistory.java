import java.sql.*;
import java.nio.file.*;
import java.util.regex.*;

/** Remove only legacy assistant conversations, including their migration snapshots. */
public class ClearAssistantHistory {
 static String config(String text,String key){Matcher m=Pattern.compile("(?m)^\\s*"+key+":\\s*(.+)$").matcher(text);if(!m.find())throw new IllegalArgumentException(key);return m.group(1).trim();}
 static boolean exists(Connection db,String table)throws Exception{try(PreparedStatement s=db.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?")){s.setString(1,table);try(ResultSet r=s.executeQuery()){r.next();return r.getInt(1)>0;}}}
 public static void main(String[]args)throws Exception{
  String cfg=Files.readString(Path.of("src/main/resources/application.yml"));
  try(Connection db=DriverManager.getConnection(config(cfg,"url"),config(cfg,"username"),config(cfg,"password"))){
   if(!"platform_smart_park".equals(db.getCatalog()))throw new IllegalStateException("Unexpected database");
   db.setAutoCommit(false);
   try(Statement s=db.createStatement()){
    int sessions=exists(db,"console_assistant_session")?s.executeUpdate("DELETE FROM console_assistant_session"):0;
    int snapshots=exists(db,"sp_source_backup")?s.executeUpdate("DELETE FROM sp_source_backup WHERE table_name='console_assistant_session'"):0;
    db.commit();System.out.println("Removed assistant conversations: "+sessions+", legacy conversation snapshots: "+snapshots);
   }catch(Exception e){db.rollback();throw e;}
  }
 }
}
