package cn.guat.smartpark.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Public GD numbers are separate from work-order keys and notification references. */
@Service
public class WorkOrderNumberService {
 private final JdbcTemplate db;
 private final TransactionTemplate transaction;
 public WorkOrderNumberService(JdbcTemplate db){this.db=db;transaction=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(db.getDataSource())));}
 @PostConstruct public void schema(){
  db.execute("CREATE TABLE IF NOT EXISTS sp_order_day_sequence (order_day DATE PRIMARY KEY,sequence_value BIGINT NOT NULL)");
  db.execute("CREATE TABLE IF NOT EXISTS sp_order_number (order_id VARCHAR(80) PRIMARY KEY,order_code VARCHAR(40) NOT NULL,UNIQUE KEY uq_order_code(order_code))");
 }
 private String existing(String id){List<String>codes=db.queryForList("SELECT order_code FROM sp_order_number WHERE order_id=?",String.class,id);return codes.isEmpty()?null:codes.get(0);}
 public String assign(Map<String,Object>order){
  String id=Objects.toString(order.get("id"),"");if(id.isEmpty())throw new IllegalArgumentException("工单缺少内部ID");
  String code=existing(id);
  if(code==null){final LocalDate day=AlarmNumberService.alarmDay(order.get("created"));code=transaction.execute(status->{
   db.update("INSERT IGNORE INTO sp_order_day_sequence(order_day,sequence_value) VALUES(?,0)",java.sql.Date.valueOf(day));
   Long last=db.queryForObject("SELECT sequence_value FROM sp_order_day_sequence WHERE order_day=? FOR UPDATE",Long.class,java.sql.Date.valueOf(day));
   String saved=existing(id);if(saved!=null)return saved;
   long next=Objects.requireNonNull(last)+1;
   String number="GD"+day.format(DateTimeFormatter.BASIC_ISO_DATE)+String.format(Locale.ROOT,"%04d",next);
   db.update("UPDATE sp_order_day_sequence SET sequence_value=? WHERE order_day=?",next,java.sql.Date.valueOf(day));
   db.update("INSERT INTO sp_order_number(order_id,order_code) VALUES(?,?)",id,number);return number;
  });}
  order.put("orderCode",code);return code;
 }
 public int backfill(ObjectMapper json){
  int count=0;
  for(Map<String,Object>row:db.queryForList("SELECT id,payload FROM console_order ORDER BY JSON_UNQUOTE(JSON_EXTRACT(payload,'$.created')),created_at,id")){
   try{Map<String,Object>order=json.readValue(row.get("payload").toString(),new TypeReference<Map<String,Object>>(){});order.put("id",row.get("id").toString());assign(order);count++;}
   catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("工单记录格式无效",e);}
  }return count;
 }
}
