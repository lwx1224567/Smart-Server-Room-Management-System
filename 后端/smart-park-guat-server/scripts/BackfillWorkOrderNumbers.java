import cn.guat.smartpark.service.WorkOrderNumberService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Assign public GD numbers; verify orders and notification associations are unchanged. */
public class BackfillWorkOrderNumbers {
 static String setting(String text,String key){Matcher m=Pattern.compile("(?m)^\\s*"+key+":\\s*(.+)$").matcher(text);if(!m.find())throw new IllegalArgumentException(key);return m.group(1).trim();}
 public static void main(String[]args)throws Exception{
  String cfg=Files.readString(Path.of("src/main/resources/application.yml")),local=Files.readString(Path.of("src/main/resources/application-local.yml"));
  JdbcTemplate db=new JdbcTemplate(new DriverManagerDataSource(setting(cfg,"url"),System.getenv().getOrDefault("DB_USERNAME","root"),System.getenv().getOrDefault("DB_PASSWORD",setting(local,"password"))));
  List<Map<String,Object>>orders=db.queryForList("SELECT * FROM console_order ORDER BY id"),notices=db.queryForList("SELECT id,order_id FROM console_notice ORDER BY id");
  WorkOrderNumberService numbers=new WorkOrderNumberService(db);numbers.schema();ObjectMapper json=new ObjectMapper();
  System.out.println("Numbered orders="+numbers.backfill(json));
  List<Map<String,Object>>mapping=db.queryForList("SELECT order_id,order_code FROM sp_order_number ORDER BY order_code");System.out.println("Mapping="+json.writeValueAsString(mapping));
  numbers.backfill(json);
  if(!mapping.equals(db.queryForList("SELECT order_id,order_code FROM sp_order_number ORDER BY order_code")))throw new IllegalStateException("Repeated assignment changed numbers");
  if(!orders.equals(db.queryForList("SELECT * FROM console_order ORDER BY id"))||!notices.equals(db.queryForList("SELECT id,order_id FROM console_notice ORDER BY id")))throw new IllegalStateException("Existing orders/associations changed");
  if(db.queryForObject("SELECT COUNT(*) FROM console_order o LEFT JOIN sp_order_number n ON n.order_id=o.id WHERE n.order_code IS NULL",Integer.class)!=0)throw new IllegalStateException("Missing order numbers");
  System.out.println("Verified: public numbers stable; original orders and notice references unchanged.");
 }
}
