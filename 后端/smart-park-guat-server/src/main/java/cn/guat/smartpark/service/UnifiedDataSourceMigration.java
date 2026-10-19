package cn.guat.smartpark.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** One-time additive migration: keep row snapshots, IDs, counts and all business associations. */
@Component @Order(0)
public class UnifiedDataSourceMigration implements ApplicationRunner {
 public static final String VERSION="V008_UNIFIED_SOURCE";
 private final JdbcTemplate db;private final ObjectMapper json;private final TransactionTemplate tx;
 public UnifiedDataSourceMigration(JdbcTemplate db,ObjectMapper json,PlatformTransactionManager manager){this.db=db;this.json=json;this.tx=new TransactionTemplate(manager);}
 public void run(ApplicationArguments args){schema();migrate();}
 public void schema(){
  db.execute("CREATE TABLE IF NOT EXISTS sp_source_migration(id VARCHAR(80) PRIMARY KEY,applied BOOLEAN NOT NULL DEFAULT FALSE,changed_rows BIGINT NOT NULL DEFAULT 0,applied_at TIMESTAMP(3) NULL)");
  db.execute("CREATE TABLE IF NOT EXISTS sp_source_backup(migration_id VARCHAR(80) NOT NULL,table_name VARCHAR(80) NOT NULL,record_key VARCHAR(255) NOT NULL,payload LONGTEXT NOT NULL,PRIMARY KEY(migration_id,table_name,record_key))");
 }
 private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("无法保存来源迁移快照",e);}}
 private void backup(String table,String key,Map<String,Object>row){db.update("INSERT INTO sp_source_backup(migration_id,table_name,record_key,payload) VALUES(?,?,?,?)",VERSION,table,key,encode(row));}
 private Object decoded(Object raw){try{return json.readValue(String.valueOf(raw),new TypeReference<Object>(){});}catch(Exception e){throw new IllegalStateException("来源迁移发现无效 JSON，已停止且回滚业务修改",e);}}
 public long migrate(){
  return tx.execute(status->{
   db.update("INSERT IGNORE INTO sp_source_migration(id) VALUES(?)",VERSION);
   Map<String,Object>version=db.queryForMap("SELECT * FROM sp_source_migration WHERE id=? FOR UPDATE",VERSION);if(Boolean.TRUE.equals(version.get("applied"))||"1".equals(String.valueOf(version.get("applied"))))return 0L;
   db.queryForList("SELECT id FROM sp_room ORDER BY id FOR UPDATE");long changed=0;
   for(String table:Arrays.asList("sp_park","sp_room","sp_zone","sp_cabinet","sp_device","sp_reading","sp_energy_sample")){
    long count=db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);
    for(Map<String,Object>row:db.queryForList("SELECT * FROM "+table+" WHERE record_origin='DEMO' FOR UPDATE")){backup(table,String.valueOf(row.get("id")),row);changed+=db.update("UPDATE "+table+" SET record_origin='REAL' WHERE id=?",row.get("id"));}
    if(count!=db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class))throw new IllegalStateException("来源迁移行数核对失败");
   }
   for(String table:Arrays.asList("sp_monitor_record","console_order","console_assistant_session")){
    long count=db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);
    for(Map<String,Object>row:db.queryForList("SELECT * FROM "+table+" FOR UPDATE")){
     Object before=decoded(row.get("payload"));String old=encode(before);UnifiedDataSource.normalize(before);if(table.equals("console_assistant_session")&&before instanceof Map){((Map<?,?>)before).remove("pending");((Map<?,?>)before).remove("clientAction");}String now=encode(before);if(old.equals(now))continue;
     String key=table.equals("sp_monitor_record")?row.get("room_id")+":"+row.get("kind")+":"+row.get("id"):table.equals("console_assistant_session")?row.get("account_id")+":"+row.get("room_id"):String.valueOf(row.get("id"));backup(table,key,row);
     if(table.equals("sp_monitor_record"))changed+=db.update("UPDATE sp_monitor_record SET payload=? WHERE room_id=? AND kind=? AND id=?",now,row.get("room_id"),row.get("kind"),row.get("id"));
     else if(table.equals("console_assistant_session"))changed+=db.update("UPDATE console_assistant_session SET payload=? WHERE account_id=? AND room_id=?",now,row.get("account_id"),row.get("room_id"));
     else changed+=db.update("UPDATE console_order SET payload=? WHERE id=?",now,row.get("id"));
    }
    if(count!=db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class))throw new IllegalStateException("来源迁移行数核对失败");
   }
   db.update("UPDATE sp_source_migration SET applied=TRUE,changed_rows=?,applied_at=CURRENT_TIMESTAMP(3) WHERE id=?",changed,VERSION);return changed;
  });
 }
}
