import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Manually backfilled readings using existing room measurements; inspect unless --apply is set. */
public class BackfillPueWeek {
 static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
 static String setting(String text,String key){Matcher m=Pattern.compile("(?m)^\\s*"+key+":\\s*(.+)$").matcher(text);if(!m.find())throw new IllegalArgumentException(key);return m.group(1).trim();}
 static String resolve(String value){Matcher m=Pattern.compile("\\$\\{([^:}]+):([^}]*)}").matcher(value);return m.matches()?System.getenv().getOrDefault(m.group(1),m.group(2)):value;}
 static Timestamp stamp(LocalDate date){return Timestamp.from(date.atStartOfDay(ZONE).toInstant());}
 static Double latest(Connection db,String room,String metric,LocalDate from,LocalDate to)throws Exception{
  String sql="SELECT value FROM sp_reading WHERE room_id=? AND entity_type='ROOM' AND entity_id=? AND metric=? AND quality='GOOD' AND record_origin='REAL' AND observed_at<?"+(from==null?"":" AND observed_at>=?")+" ORDER BY observed_at DESC,id DESC LIMIT 1";
  try(PreparedStatement q=db.prepareStatement(sql)){q.setString(1,room);q.setString(2,room);q.setString(3,metric);q.setTimestamp(4,stamp(to));if(from!=null)q.setTimestamp(5,stamp(from));try(ResultSet r=q.executeQuery()){return r.next()?r.getDouble(1):null;}}
 }
 public static void main(String[] args)throws Exception{
  LocalDate today=LocalDate.parse(args[0]);boolean apply=Arrays.asList(args).contains("--apply");
  String cfg=Files.readString(Path.of("src/main/resources/application.yml"));
  String local=Files.readString(Path.of("src/main/resources/application-local.yml"));
  String password=System.getenv().getOrDefault("DB_PASSWORD",setting(local,"password"));
  try(Connection db=DriverManager.getConnection(setting(cfg,"url"),resolve(setting(cfg,"username")),password)){
   db.setAutoCommit(false);
   try{
    List<String> rooms=new ArrayList<>();try(Statement q=db.createStatement();ResultSet r=q.executeQuery("SELECT id FROM sp_room ORDER BY id")){while(r.next())rooms.add(r.getString(1));}
    int added=0;
    for(String room:rooms){
     Double basePower=latest(db,room,"power",null,today.plusDays(1)),baseIt=latest(db,room,"itPower",null,today.plusDays(1));
     System.out.println("room="+room+" latest power="+basePower+" itPower="+baseIt);
     if(basePower==null||baseIt==null||baseIt<=0||basePower<baseIt){System.out.println("Skipped: no valid historical power pair");continue;}
     for(LocalDate day=today.minusDays(6);!day.isAfter(today);day=day.plusDays(1)){
      Double power=latest(db,room,"power",day,day.plusDays(1)),it=latest(db,room,"itPower",day,day.plusDays(1)),pue=latest(db,room,"pue",day,day.plusDays(1));
      System.out.println(day+" before power="+power+" itPower="+it+" pue="+pue);
      double watts=power==null?basePower:power,itWatts=it==null?baseIt:it;
      if(itWatts<=0||watts<itWatts)throw new IllegalStateException("Invalid power pair");
      String[] metrics={"power","itPower","pue"};Double[] present={power,it,pue};double[] values={watts,itWatts,Math.round(watts/itWatts*10000.0)/10000.0};
      for(int i=0;i<metrics.length;i++)if(present[i]==null){
       if(apply)try(PreparedStatement insert=db.prepareStatement("INSERT INTO sp_reading(room_id,entity_type,entity_id,metric,value,unit,observed_at,quality,source,record_origin) VALUES(?,'ROOM',?,?,?,?,?,'GOOD','INTEGRATION_PUE_BACKFILL','REAL')")){
        insert.setString(1,room);insert.setString(2,room);insert.setString(3,metrics[i]);insert.setDouble(4,values[i]);insert.setString(5,i==2?"":"kW");insert.setTimestamp(6,Timestamp.from(day.atStartOfDay(ZONE).toInstant()));insert.executeUpdate();
       }added++;
      }
     }
    }
    if(apply)db.commit();else db.rollback();System.out.println((apply?"Inserted=":"Missing=")+added);
   }catch(Exception e){db.rollback();throw e;}
  }
 }
}
