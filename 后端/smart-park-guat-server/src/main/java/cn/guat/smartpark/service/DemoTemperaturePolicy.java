package cn.guat.smartpark.service;

import cn.guat.smartpark.exception.PlatformException;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** Scope is opt-in. No rules or samples are created on application startup. */
@Component
public class DemoTemperaturePolicy {
 public static final String RULE_ID="demo-a01-temperature-v1", SOURCE="ADMIN_DEMO_TEMPERATURE", UNIT="℃";
 private final JdbcTemplate db; private final Environment env;
 public DemoTemperaturePolicy(JdbcTemplate db,Environment env){this.db=db;this.env=env;}
 public String cabinet(String room){
  if(!room.equals(env.getProperty("console.demo.room-id","")))throw bad(409,"未启用该机房的 A-01 演示监测");
  List<String> ids=db.queryForList("SELECT CAST(c.id AS CHAR) FROM sp_cabinet c JOIN sp_zone z ON z.id=c.zone_id WHERE z.room_id=? AND (c.code='A-01' OR c.scene_model_key='A-01')",String.class,room);
  if(ids.size()!=1)throw bad(409,"A-01 台账映射缺失或不唯一");return ids.get(0);
 }
 public static String origin(Map<String,Object> rule){return UnifiedDataSource.origin(rule.getOrDefault("recordOrigin","REAL"));}
 public static boolean managed(Map<String,Object> alarm){return RULE_ID.equals(alarm.get("ruleId"));}
 public void validateRule(String room,String id,Map<String,Object> rule){
  String origin=origin(rule);if(!Arrays.asList("REAL","DEMO").contains(origin))throw bad(400,"规则来源必须为 REAL 或 DEMO");
  if(!RULE_ID.equals(id))return;
  if(!RULE_ID.equals(id)||!"CABINET".equals(rule.get("entityType"))||!cabinet(room).equals(rule.get("entityId"))||!"temperature".equals(rule.get("metric"))||!UNIT.equals(rule.get("unit"))||!SOURCE.equals(rule.get("sampleSource")))throw bad(400,"仅支持指定机房 A-01 的手动温度规则");
  String[] keys={"min","max","maxAgeSeconds","normalSeconds","maxGapSeconds"};double[] values={0,30,300,0,30};
  for(int i=0;i<keys.length;i++)if(!(rule.get(keys[i]) instanceof Number)||((Number)rule.get(keys[i])).doubleValue()!=values[i])throw bad(400,"演示规则必须使用 0～30℃、300/0/30 秒配置");
 }
 public static PlatformException bad(int code,String message){return new PlatformException(code,"DEMO_TEMPERATURE_ERROR",message);}
}
