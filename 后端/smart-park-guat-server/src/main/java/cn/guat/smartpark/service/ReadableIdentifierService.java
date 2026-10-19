package cn.guat.smartpark.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Public audit numbers. Random transport identities, authentication hashes and snapshots remain intact. */
@Service
public class ReadableIdentifierService {
 private final JdbcTemplate db;
 private final ObjectMapper json;
 private final TransactionTemplate transaction;
 private static final Set<String> KINDS=new HashSet<>(Arrays.asList("RZ","QQ","PC","BF","HY"));
 public ReadableIdentifierService(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;transaction=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(db.getDataSource())));transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);}
 @PostConstruct public void schema(){
  db.execute("CREATE TABLE IF NOT EXISTS sp_readable_day_sequence (number_kind VARCHAR(8) NOT NULL,number_day DATE NOT NULL,sequence_value BIGINT NOT NULL,PRIMARY KEY(number_kind,number_day))");
  db.execute("CREATE TABLE IF NOT EXISTS sp_readable_identifier (number_kind VARCHAR(8) NOT NULL,identity_hash CHAR(64) NOT NULL,display_code VARCHAR(40) NOT NULL,PRIMARY KEY(number_kind,identity_hash),UNIQUE KEY uq_readable_code(display_code))");
 }
 private String hash(String identity){try{byte[]bytes=MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private String existing(String kind,String hash){List<String>rows=db.queryForList("SELECT display_code FROM sp_readable_identifier WHERE number_kind=? AND identity_hash=?",String.class,kind,hash);return rows.isEmpty()?null:rows.get(0);}
 public String assign(String kind,String identity,Object at){
  if(!KINDS.contains(kind)||identity==null||identity.trim().isEmpty())throw new IllegalArgumentException("Invalid public identifier");
  String key=hash(identity),saved=existing(kind,key);if(saved!=null)return saved;
  LocalDate day=AlarmNumberService.alarmDay(at instanceof Timestamp?((Timestamp)at).toInstant():at);
  return transaction.execute(status->{
   db.update("INSERT IGNORE INTO sp_readable_day_sequence(number_kind,number_day,sequence_value) VALUES(?,?,0)",kind,java.sql.Date.valueOf(day));
   Long last=db.queryForObject("SELECT sequence_value FROM sp_readable_day_sequence WHERE number_kind=? AND number_day=? FOR UPDATE",Long.class,kind,java.sql.Date.valueOf(day));
   String found=existing(kind,key);if(found!=null)return found;
   long next=Objects.requireNonNull(last)+1;String code=kind+day.format(DateTimeFormatter.BASIC_ISO_DATE)+String.format(Locale.ROOT,"%04d",next);
   db.update("UPDATE sp_readable_day_sequence SET sequence_value=? WHERE number_kind=? AND number_day=?",next,kind,java.sql.Date.valueOf(day));
   db.update("INSERT INTO sp_readable_identifier(number_kind,identity_hash,display_code) VALUES(?,?,?)",kind,key,code);return code;
  });
 }
 public String requestCode(String room,Object request,Object at){return assign("QQ",room+":"+request,at);}
 @SuppressWarnings("unchecked") public void decorateLog(String room,Map<String,Object>log){
  Object at=log.get("createdAt");if(at==null)at=log.get("observedAt");
  log.put("logCode",assign("RZ",room+":"+log.get("id"),at));
  Object request=log.get("requestId");
  if(request!=null&&!request.toString().isEmpty()){
   String code=requestCode(room,request,at);log.put("requestCode",code);
   if(log.get("response") instanceof Map)((Map<String,Object>)log.get("response")).put("requestCode",code);
  }
 }
 private boolean table(String name){return db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",Integer.class,name)>0;}
 private void column(String table,String column){
  if(!table(table))return;
  if(db.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?",Integer.class,table,column)==0)db.execute("ALTER TABLE `"+table+"` ADD COLUMN `"+column+"` VARCHAR(40) NULL, ADD UNIQUE KEY `uq_"+column+"` (`"+column+"`)");
 }
 public void batchSchema(){column("sp_ingest_batch","batch_code");}
 public void sessionSchema(){column("console_session","session_code");}
 public String batchCode(String room,String batch,Object at){
  String code=assign("PC",room+":"+batch,at);db.update("UPDATE sp_ingest_batch SET batch_code=? WHERE room_id=? AND batch_id=? AND (batch_code IS NULL OR batch_code<>?)",code,room,batch,code);return code;
 }
 public String sessionCode(String tokenHash,Object at){
  String code=assign("HY",tokenHash,at);db.update("UPDATE console_session SET session_code=? WHERE token_hash=? AND (session_code IS NULL OR session_code<>?)",code,tokenHash,code);return code;
 }
 @EventListener(ApplicationReadyEvent.class) public void backfill(){
  batchSchema();sessionSchema();column("sp_source_backup","backup_code");
  if(table("sp_monitor_record"))for(Map<String,Object>row:db.queryForList("SELECT room_id,id,payload FROM sp_monitor_record WHERE kind='LOG' ORDER BY JSON_UNQUOTE(JSON_EXTRACT(payload,'$.observedAt')),room_id,id")){
   try{Map<String,Object>log=json.readValue(row.get("payload").toString(),new TypeReference<Map<String,Object>>(){});String before=json.writeValueAsString(log);log.put("id",row.get("id").toString());decorateLog(row.get("room_id").toString(),log);String after=json.writeValueAsString(log);if(!before.equals(after))db.update("UPDATE sp_monitor_record SET payload=? WHERE room_id=? AND kind='LOG' AND id=?",after,row.get("room_id"),row.get("id"));}
   catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Invalid audit log",e);}
  }
  if(table("sp_ingest_batch"))for(Map<String,Object>row:db.queryForList("SELECT room_id,batch_id,created_at FROM sp_ingest_batch ORDER BY created_at,room_id,batch_id"))batchCode(row.get("room_id").toString(),row.get("batch_id").toString(),row.get("created_at"));
  if(table("console_session"))for(Map<String,Object>row:db.queryForList("SELECT token_hash,expires_at FROM console_session ORDER BY expires_at,token_hash"))sessionCode(row.get("token_hash").toString(),((Timestamp)row.get("expires_at")).toInstant().minusSeconds(8*3600));
  if(table("sp_source_backup"))for(Map<String,Object>row:db.queryForList("SELECT b.migration_id,b.table_name,b.record_key,m.applied_at FROM sp_source_backup b LEFT JOIN sp_source_migration m ON m.id=b.migration_id ORDER BY m.applied_at,b.migration_id,b.table_name,b.record_key")){
   try{String identity=json.writeValueAsString(Arrays.asList(row.get("migration_id"),row.get("table_name"),row.get("record_key")));String code=assign("BF",identity,row.get("applied_at"));db.update("UPDATE sp_source_backup SET backup_code=? WHERE migration_id=? AND table_name=? AND record_key=? AND (backup_code IS NULL OR backup_code<>?)",code,row.get("migration_id"),row.get("table_name"),row.get("record_key"),code);}
   catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
  }
 }
}
