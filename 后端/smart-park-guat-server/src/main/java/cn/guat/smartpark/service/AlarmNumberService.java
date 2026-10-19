package cn.guat.smartpark.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import javax.annotation.PostConstruct;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Persistent public numbers; UUIDs remain the internal alarm/work-order identities. */
@Service
public class AlarmNumberService {
 private final JdbcTemplate db;
 private final TransactionTemplate transaction;
 private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
 public AlarmNumberService(JdbcTemplate db){this.db=db;this.transaction=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(db.getDataSource())));}
 @PostConstruct public void schema(){
  db.execute("CREATE TABLE IF NOT EXISTS sp_alarm_day_sequence (alarm_day DATE PRIMARY KEY,sequence_value BIGINT NOT NULL)");
  db.execute("CREATE TABLE IF NOT EXISTS sp_alarm_number (room_id BIGINT NOT NULL,alarm_id VARCHAR(100) NOT NULL,alarm_code VARCHAR(40) NOT NULL,PRIMARY KEY(room_id,alarm_id),UNIQUE KEY uq_alarm_code(alarm_code))");
 }
 public static LocalDate alarmDay(Object createdAt){
  String value=createdAt==null?"":createdAt.toString();
  try{return Instant.parse(value).atZone(ZONE).toLocalDate();}catch(RuntimeException ignored){}
  try{return LocalDateTime.parse(value).toLocalDate();}catch(RuntimeException ignored){}
  return LocalDate.now(ZONE);
 }
 private String existing(String room,String id){List<String>codes=db.queryForList("SELECT alarm_code FROM sp_alarm_number WHERE room_id=? AND alarm_id=?",String.class,room,id);return codes.isEmpty()?null:codes.get(0);}
 public String assign(String room,Map<String,Object>alarm){
  String id=Objects.toString(alarm.get("id"),"");if(id.isEmpty())throw new IllegalArgumentException("告警缺少内部ID");
  String code=existing(room,id);
  if(code==null){final LocalDate day=alarmDay(alarm.get("createdAt"));code=transaction.execute(status->{
   db.update("INSERT IGNORE INTO sp_alarm_day_sequence(alarm_day,sequence_value) VALUES(?,0)",java.sql.Date.valueOf(day));
   Long last=db.queryForObject("SELECT sequence_value FROM sp_alarm_day_sequence WHERE alarm_day=? FOR UPDATE",Long.class,java.sql.Date.valueOf(day));
   String saved=existing(room,id);if(saved!=null)return saved;
   long next=Objects.requireNonNull(last)+1;
   String number="GJ"+day.format(DateTimeFormatter.BASIC_ISO_DATE)+String.format(Locale.ROOT,"%04d",next);
   db.update("UPDATE sp_alarm_day_sequence SET sequence_value=? WHERE alarm_day=?",next,java.sql.Date.valueOf(day));
   db.update("INSERT INTO sp_alarm_number(room_id,alarm_id,alarm_code) VALUES(?,?,?)",room,id,number);
   return number;
  });}
  alarm.put("alarmCode",code);return code;
 }
 public int backfill(ObjectMapper json){
  int count=0;
  for(Map<String,Object>row:db.queryForList("SELECT room_id,id,payload FROM sp_monitor_record WHERE kind='ALARM' ORDER BY JSON_UNQUOTE(JSON_EXTRACT(payload,'$.createdAt')),room_id,id")){
   try{Map<String,Object>alarm=json.readValue(row.get("payload").toString(),new TypeReference<Map<String,Object>>(){});alarm.put("id",row.get("id").toString());assign(row.get("room_id").toString(),alarm);count++;}
   catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("告警记录格式无效",e);}
  }return count;
 }
}
