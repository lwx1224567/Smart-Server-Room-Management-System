import cn.guat.smartpark.service.AlarmNumberService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.util.regex.*;

/** Assign public numbers without changing alarm payloads or work-order UUID references. */
public class BackfillAlarmNumbers {
 static String setting(String text,String key){Matcher m=Pattern.compile("(?m)^\\s*"+key+":\\s*(.+)$").matcher(text);if(!m.find())throw new IllegalArgumentException(key);return m.group(1).trim();}
 public static void main(String[]args)throws Exception{
  String config=Files.readString(Path.of("src/main/resources/application.yml")),local=Files.readString(Path.of("src/main/resources/application-local.yml"));
  DriverManagerDataSource source=new DriverManagerDataSource(setting(config,"url"),System.getenv().getOrDefault("DB_USERNAME","root"),System.getenv().getOrDefault("DB_PASSWORD",setting(local,"password")));
  JdbcTemplate db=new JdbcTemplate(source);AlarmNumberService numbers=new AlarmNumberService(db);numbers.schema();
  System.out.println("Numbered alarms="+numbers.backfill(new ObjectMapper()));
  System.out.println(db.queryForList("SELECT room_id,alarm_code FROM sp_alarm_number ORDER BY alarm_code"));
  int before=db.queryForObject("SELECT COUNT(*) FROM sp_alarm_number",Integer.class);numbers.backfill(new ObjectMapper());
  if(before!=db.queryForObject("SELECT COUNT(*) FROM sp_alarm_number",Integer.class))throw new IllegalStateException("Repeated backfill changed count");
  if(db.queryForObject("SELECT COUNT(*) FROM sp_monitor_record r LEFT JOIN sp_alarm_number n ON n.room_id=r.room_id AND n.alarm_id=r.id WHERE r.kind='ALARM' AND n.alarm_code IS NULL",Integer.class)!=0)throw new IllegalStateException("Missing numbers");
  System.out.println("Verified: every alarm numbered; repeated backfill adds zero rows.");
 }
}
