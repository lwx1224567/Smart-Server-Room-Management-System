import cn.guat.smartpark.service.EnergyBackfillService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.time.*;
import java.util.regex.*;

/** Initial backfill using the same transactional implementation as energy queries. */
public class BackfillDailyEnergy {
 static String config(String text,String key){Matcher m=Pattern.compile("(?m)^\\s*"+key+":\\s*(.+)$").matcher(text);if(!m.find())throw new IllegalArgumentException(key);return m.group(1).trim();}
 public static void main(String[] args)throws Exception{
  String cfg=Files.readString(Path.of("src/main/resources/application.yml"));
  DriverManagerDataSource source=new DriverManagerDataSource(config(cfg,"url"),config(cfg,"username"),config(cfg,"password"));
  JdbcTemplate db=new JdbcTemplate(source);
  TransactionTemplate tx=new TransactionTemplate(new DataSourceTransactionManager(source));
  EnergyBackfillService service=new EnergyBackfillService(db);
  LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
  for(String room:db.queryForList("SELECT id FROM sp_room ORDER BY id",String.class)){
   tx.execute(status->{service.fill(room,today);return null;});
   System.out.println("room="+room+" saved through "+today);
   System.out.println(db.queryForList("SELECT DATE(observed_at) day,ROUND(SUM(kwh),2) kwh FROM sp_energy_sample WHERE room_id=? AND observed_at>=? AND observed_at<? AND quality='GOOD' AND record_origin='REAL' GROUP BY DATE(observed_at) ORDER BY day",room,java.sql.Timestamp.from(today.minusDays(6).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()),java.sql.Timestamp.from(today.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant())));
  }
 }
}
