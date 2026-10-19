import cn.guat.smartpark.service.ReadableIdentifierService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Number audit records while preserving authentication, deduplication and original backup contents. */
public class BackfillReadableIdentifiers {
 static String setting(String text,String key){Matcher m=Pattern.compile("(?m)^\\s*"+key+":\\s*(.+)$").matcher(text);if(!m.find())throw new IllegalArgumentException(key);return m.group(1).trim();}
 static Map<String,List<Map<String,Object>>> original(JdbcTemplate db){
  Map<String,List<Map<String,Object>>>out=new LinkedHashMap<>();
  out.put("sessions",db.queryForList("SELECT token_hash,account_id,expires_at FROM console_session ORDER BY token_hash"));
  out.put("batches",db.queryForList("SELECT room_id,batch_id,digest,created_at FROM sp_ingest_batch ORDER BY room_id,batch_id"));
  out.put("backups",db.queryForList("SELECT migration_id,table_name,record_key,payload FROM sp_source_backup ORDER BY migration_id,table_name,record_key"));
  out.put("orders",db.queryForList("SELECT * FROM console_order ORDER BY id"));
  out.put("alarms",db.queryForList("SELECT room_id,id,payload FROM sp_monitor_record WHERE kind='ALARM' ORDER BY room_id,id"));return out;
 }
 @SuppressWarnings("unchecked") static void strip(Object node){
  if(node instanceof Map){Map<String,Object>m=(Map<String,Object>)node;m.remove("logCode");m.remove("requestCode");for(Object value:m.values())strip(value);}
  else if(node instanceof List)for(Object value:(List<?>)node)strip(value);
 }
 static List<Object> logs(JdbcTemplate db,ObjectMapper json)throws Exception{
  List<Object>out=new ArrayList<>();for(Map<String,Object>row:db.queryForList("SELECT payload FROM sp_monitor_record WHERE kind='LOG' ORDER BY room_id,id")){Object log=json.readValue(row.get("payload").toString(),Object.class);strip(log);out.add(log);}return out;
 }
 public static void main(String[]args)throws Exception{
  String cfg=Files.readString(Path.of("src/main/resources/application.yml")),local=Files.readString(Path.of("src/main/resources/application-local.yml"));
  JdbcTemplate db=new JdbcTemplate(new DriverManagerDataSource(setting(cfg,"url"),System.getenv().getOrDefault("DB_USERNAME","root"),System.getenv().getOrDefault("DB_PASSWORD",setting(local,"password"))));
  ObjectMapper json=new ObjectMapper();Map<String,List<Map<String,Object>>>before=original(db);List<Object>oldLogs=logs(db,json);
  ReadableIdentifierService ids=new ReadableIdentifierService(db,json);ids.schema();ids.backfill();
  if(!before.equals(original(db))||!oldLogs.equals(logs(db,json)))throw new IllegalStateException("Original identities or record contents changed");
  List<Map<String,Object>>mapping=db.queryForList("SELECT number_kind,identity_hash,display_code FROM sp_readable_identifier ORDER BY display_code");ids.backfill();
  if(!mapping.equals(db.queryForList("SELECT number_kind,identity_hash,display_code FROM sp_readable_identifier ORDER BY display_code")))throw new IllegalStateException("Repeated backfill changed numbers");
  for(String tableColumn:Arrays.asList("console_session:session_code","sp_ingest_batch:batch_code","sp_source_backup:backup_code")){
   String[]pair=tableColumn.split(":");if(db.queryForObject("SELECT COUNT(*) FROM "+pair[0]+" WHERE "+pair[1]+" IS NULL",Integer.class)>0)throw new IllegalStateException("Missing numbers");
  }
  if(db.queryForObject("SELECT COUNT(*) FROM sp_monitor_record WHERE kind='LOG' AND JSON_EXTRACT(payload,'$.logCode') IS NULL",Integer.class)>0)throw new IllegalStateException("Missing log codes");
  System.out.println("Summary="+json.writeValueAsString(db.queryForList("SELECT number_kind kind,COUNT(*) count,MIN(display_code) first_code,MAX(display_code) last_code FROM sp_readable_identifier GROUP BY number_kind ORDER BY number_kind")));
  System.out.println("Verified: all records numbered; original identities, authentication, backup payloads and log data preserved; repeated backfill stable.");
 }
}
