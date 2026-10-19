package cn.guat.smartpark.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.guat.smartpark.exception.PlatformException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;

/** Queries stored monitoring records; explicit demo retests keep simulated evidence on the order only. */
@Service
public class MonitoringService {
 private final JdbcTemplate db;
 private final ObjectMapper json;
 @Autowired private DemoTemperaturePolicy demoPolicy;
 @Autowired private EnergyBackfillService energyBackfill;
 @Autowired private AlarmNumberService alarmNumbers;
 @Autowired private ReadableIdentifierService readableIds;
 @Autowired(required=false) private Clock clock=Clock.systemUTC();
 private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
 public MonitoringService(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
 @PostConstruct public void schema(){
  db.execute("CREATE TABLE IF NOT EXISTS sp_monitor_record (room_id BIGINT NOT NULL,kind VARCHAR(24) NOT NULL,id VARCHAR(100) NOT NULL,payload LONGTEXT NOT NULL,PRIMARY KEY(room_id,kind,id))");
  db.execute("CREATE TABLE IF NOT EXISTS sp_reading (id BIGINT PRIMARY KEY AUTO_INCREMENT,room_id BIGINT NOT NULL,entity_type VARCHAR(16) NOT NULL,entity_id VARCHAR(100) NOT NULL,metric VARCHAR(64) NOT NULL,value DOUBLE NOT NULL,unit VARCHAR(24) NOT NULL,observed_at TIMESTAMP(3) NOT NULL,quality VARCHAR(16) NOT NULL,source VARCHAR(100) NOT NULL,record_origin VARCHAR(8) NOT NULL,KEY ix_reading(room_id,entity_type,entity_id,metric,observed_at))");
  db.execute("CREATE TABLE IF NOT EXISTS sp_energy_sample (id BIGINT PRIMARY KEY AUTO_INCREMENT,room_id BIGINT NOT NULL,category VARCHAR(24) NOT NULL,cabinet_id BIGINT NULL,kwh DOUBLE NOT NULL,observed_at TIMESTAMP(3) NOT NULL,quality VARCHAR(16) NOT NULL,record_origin VARCHAR(8) NOT NULL,source VARCHAR(100) NOT NULL,KEY ix_energy(room_id,observed_at))");
  if(alarmNumbers!=null)alarmNumbers.backfill(json);
 }
 public void room(String id){if(!id.matches("[1-9][0-9]*")||db.queryForObject("SELECT COUNT(*) FROM sp_room WHERE id=?",Integer.class,id)==0)throw new PlatformException(404,"NOT_FOUND","机房不存在");}
 public void lockRoom(String id){if(id==null||!id.matches("[1-9][0-9]*")||db.queryForList("SELECT id FROM sp_room WHERE id=? FOR UPDATE",id).isEmpty())throw new PlatformException(404,"NOT_FOUND","机房不存在");}
 public List<Map<String,Object>> records(String room,String kind){
  room(room);List<Map<String,Object>> result=new ArrayList<>();
  for(String payload:db.queryForList("SELECT payload FROM sp_monitor_record WHERE room_id=? AND kind=? ORDER BY id",String.class,room,kind)){
   try{Map<String,Object> item=json.readValue(payload,new TypeReference<Map<String,Object>>(){});item.put("roomId",room);if("ALARM".equals(kind)&&alarmNumbers!=null)alarmNumbers.assign(room,item);if("LOG".equals(kind)&&readableIds!=null)readableIds.decorateLog(room,item);result.add(UnifiedDataSource.normalize(item));}catch(Exception e){throw new IllegalStateException("监测记录格式无效",e);}
  }return result;
 }
 public List<Map<String,Object>> readings(String room){room(room);List<Map<String,Object>> result=db.queryForList("SELECT entity_type AS entityType,entity_id AS entityId,metric,value,unit,observed_at AS observedAt,quality,source,record_origin AS recordOrigin FROM sp_reading WHERE room_id=? ORDER BY observed_at DESC,id DESC",room);for(Map<String,Object>r:result){r.put("observedAt",((Timestamp)r.get("observedAt")).toInstant().toString());UnifiedDataSource.normalize(r);}return result;}
 public Map<String,Object> snapshot(String room){return object("roomId",room,"generatedAt",Instant.now().toString(),"readings",readings(room),"alarms",records(room,"ALARM"),"facilities",records(room,"FACILITY"),"events",records(room,"EVENT"),"nodes",records(room,"NODE"),"links",records(room,"LINK"),"logs",records(room,"LOG"),"controls",records(room,"CONTROL"));}
 @Transactional public Map<String,Object> control(String room,String cabinet,Map<String,Object> body,Map<String,Object> actor){
  room(room);String key=String.valueOf(body.get("key"));Map<String,String> names=new LinkedHashMap<>();names.put("lighting","机柜照明");names.put("fans","散热风扇");names.put("locked","柜门门禁");names.put("power","设备电源");
  if(!names.containsKey(key)||!(body.get("value") instanceof Boolean))throw new PlatformException(400,"INVALID_ARGUMENT","控制参数无效");
  if(db.queryForList("SELECT c.id FROM sp_cabinet c JOIN sp_zone z ON c.zone_id=z.id WHERE c.id=? AND z.room_id=? FOR UPDATE",cabinet,room).isEmpty())throw new PlatformException(404,"NOT_FOUND","机柜不存在");
  Map<String,Object> state=object("id",cabinet,"cabinetId",cabinet,"roomId",room,"lighting",true,"fans",true,"locked",false,"power",true);
  for(Map<String,Object> record:records(room,"CONTROL"))if(cabinet.equals(String.valueOf(record.get("cabinetId"))))state.putAll(record);
  state.put(key,body.get("value"));state.put("updatedAt",Instant.now().toString());
  Map<String,Object> log=object("id",UUID.randomUUID().toString(),"roomId",room,"cabinetId",cabinet,"action",("locked".equals(key)?(Boolean.TRUE.equals(body.get("value"))?"锁定":"解锁"):(Boolean.TRUE.equals(body.get("value"))?"开启":"关闭"))+names.get(key),"user",actor.get("displayName"),"accountId",actor.get("id"),"createdAt",Instant.now().toString());
  try{for(Map<String,Object> record:Arrays.asList(state,log))db.update("INSERT INTO sp_monitor_record(room_id,kind,id,payload) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE payload=VALUES(payload)",room,record==state?"CONTROL":"LOG",record.get("id"),json.writeValueAsString(record));}catch(Exception e){throw new IllegalStateException("保存控制操作失败",e);}
  return object("controls",state,"log",log);
 }
 public Map<String,Object> reading(String room,String type,String entity,String metric,String unit){
  List<Map<String,Object>> rows=db.queryForList("SELECT value,unit,observed_at AS observedAt,quality,source,record_origin AS recordOrigin FROM sp_reading WHERE room_id=? AND entity_type=? AND entity_id=? AND metric=? AND quality='GOOD' AND record_origin IN ('REAL','DEMO') ORDER BY observed_at DESC,id DESC LIMIT 1",room,type,entity,metric);
  if(rows.isEmpty())return object("value",null,"unit",unit,"observedAt",null,"quality","MISSING");Map<String,Object> r=UnifiedDataSource.normalize(rows.get(0));r.put("observedAt",((Timestamp)r.get("observedAt")).toInstant().toString());return r;
 }
 public Map<String,Object> reading(String room,String type,String entity,String metric,String unit,String origin,String source){
  List<Map<String,Object>> rows=db.queryForList("SELECT id,value,unit,observed_at AS observedAt,quality,source,record_origin AS recordOrigin FROM sp_reading WHERE room_id=? AND entity_type=? AND entity_id=? AND metric=? AND unit=? AND quality='GOOD' AND record_origin IN ('REAL','DEMO') AND observed_at<=?"+(source==null?"":" AND source=?")+" ORDER BY observed_at DESC,id DESC LIMIT 1",source==null?new Object[]{room,type,entity,metric,unit,Timestamp.from(Instant.now())}:new Object[]{room,type,entity,metric,unit,Timestamp.from(Instant.now()),source});
  if(rows.isEmpty())return object("value",null,"unit",unit,"observedAt",null,"quality","MISSING");Map<String,Object> r=UnifiedDataSource.normalize(rows.get(0));r.put("observedAt",((Timestamp)r.get("observedAt")).toInstant().toString());return r;
 }
 public Map<String,Object> energy(String room,String period){
  room(room);if(!Arrays.asList("today","7d","30d").contains(period))throw new PlatformException(400,"INVALID_ARGUMENT","能耗范围无效");
  LocalDate today=LocalDate.now(clock.withZone(ZONE));int count="today".equals(period)?24:"7d".equals(period)?7:30;
  energyBackfill.fill(room,today);
  Instant start=today.minusDays("today".equals(period)?0:count-1).atStartOfDay(ZONE).toInstant(),end=today.plusDays(1).atStartOfDay(ZONE).toInstant();
  List<Map<String,Object>> rows=db.queryForList("SELECT category,cabinet_id AS cabinetId,kwh,observed_at AS observedAt,source FROM sp_energy_sample WHERE room_id=? AND observed_at>=? AND observed_at<? AND quality='GOOD' AND record_origin='REAL' ORDER BY observed_at",room,Timestamp.from(start),Timestamp.from(end));
  List<String> labels=new ArrayList<>();String[] names={"机柜","空调","照明","监控","其他"};String[] colors={"#39aaff","#7b83ff","#45d9a5","#ffbc68","#889aa5"};
  for(int i=0;i<count;i++)labels.add("today".equals(period)?String.format("%02d:00",i):today.minusDays(count-i-1).toString());
  List<Map<String,Object>> shares=new ArrayList<>();Double[] totals=new Double[count];
  for(int c=0;c<names.length;c++){Double[] values=new Double[count];for(Map<String,Object>r:rows){if(!names[c].equals(r.get("category")))continue;ZonedDateTime at=((Timestamp)r.get("observedAt")).toInstant().atZone(ZONE);int index="today".equals(period)?at.getHour():(int)java.time.temporal.ChronoUnit.DAYS.between(today.minusDays(count-1),at.toLocalDate());double value=((Number)r.get("kwh")).doubleValue();values[index]=(values[index]==null?0:values[index])+value;}
   Double sum=null;for(int i=0;i<count;i++)if(values[i]!=null)sum=(sum==null?0:sum)+values[i];shares.add(object("name",names[c],"color",colors[c],"values",values,"consumption",sum));
  }
  // Missing categories/buckets remain null, never silently treated as zero.
  Double total=null;for(int i=0;i<count;i++){boolean complete=true;double sum=0;for(Map<String,Object>s:shares){Double value=((Double[])s.get("values"))[i];if(value==null)complete=false;else sum+=value;}if(complete){totals[i]=sum;total=(total==null?0:total)+sum;}}
  boolean completeCategories=shares.stream().allMatch(s->s.get("consumption")!=null);
  total=completeCategories?shares.stream().mapToDouble(s->((Number)s.get("consumption")).doubleValue()).sum():null;
  for(Map<String,Object>s:shares)s.put("value",total!=null&&total>0&&s.get("consumption")!=null?((Double)s.get("consumption"))/total*100:null);
  List<Map<String,Object>> ranks=db.queryForList("SELECT c.name,SUM(e.kwh) AS value FROM sp_energy_sample e JOIN sp_cabinet c ON c.id=e.cabinet_id WHERE e.room_id=? AND e.category='机柜' AND e.observed_at>=? AND e.observed_at<? AND e.quality='GOOD' AND e.record_origin='REAL' GROUP BY c.id,c.name ORDER BY value DESC,c.id LIMIT 5",room,Timestamp.from(start),Timestamp.from(end));
  return object("labels",labels,"totals",totals,"total",total,"shares",shares,"ranks",ranks,"available",!rows.isEmpty(),"recordOrigin","REAL","simulated",rows.stream().anyMatch(r->EnergyBackfillService.SOURCE.equals(r.get("source"))),"partial",Arrays.asList(totals).contains(null));
 }
 @Transactional(isolation=Isolation.READ_COMMITTED) public Map<String,Object> acknowledge(String room,String id){lockRoom(room);for(Map<String,Object>a:records(room,"ALARM"))if(id.equals(a.get("id"))){if("已恢复".equals(a.get("status")))throw new PlatformException(409,"INVALID_STATE","已恢复告警不能重新确认");a.put("status","处理中");a.putIfAbsent("acknowledgedAt",Instant.now().toString());saveRecord(room,"ALARM",id,a);return a;}throw new PlatformException(404,"NOT_FOUND","告警不存在");}
 public void saveRecord(String room,String kind,String id,Map<String,Object> payload){UnifiedDataSource.normalize(payload);if("ALARM".equals(kind)&&alarmNumbers!=null)alarmNumbers.assign(room,payload);if("LOG".equals(kind)&&readableIds!=null){payload.put("id",id);readableIds.decorateLog(room,payload);}try{db.update("INSERT INTO sp_monitor_record(room_id,kind,id,payload) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE payload=VALUES(payload)",room,kind,id,json.writeValueAsString(payload));}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}}
 public Map<String,Object> demoRetest(Map<String,Object> order,String outcome){throw new PlatformException(409,"INVALID_STATE","请在监测页面上报维修后的新数据，再执行统一复检");}
 public Map<String,Object> retest(Map<String,Object> order){
  String room=String.valueOf(order.get("roomId")),alarmId=String.valueOf(order.get("alarmEvent"));
  if(order.get("roomId")==null||room.isEmpty())return object("passed",null,"note","工单缺少机房归属，无法复测");
  Map<String,Object> alarm=records(room,"ALARM").stream().filter(a->alarmId.equals(a.get("id"))).findFirst().orElse(null);
  if(alarm==null)return object("passed",null,"note","缺少真实关联告警，保持待复测");
  UnifiedDataSource.normalize(order);UnifiedDataSource.normalize(alarm);String origin="REAL";
  if(!(alarm.get("rule") instanceof Map))return object("passed",null,"note","未配置恢复规则，保持待复测");
  Map<String,Object> rule=(Map<String,Object>)alarm.get("rule");
  if(!(rule.get("min") instanceof Number)||!(rule.get("max") instanceof Number)||!(rule.get("maxAgeSeconds") instanceof Number)||!(rule.get("normalSeconds") instanceof Number)||!(rule.get("maxGapSeconds") instanceof Number))return object("passed",null,"note","恢复阈值、有效期、持续时间或采样间隔未配置");
  Instant submitted=Instant.parse(String.valueOf(order.get("submittedAt"))),now=Instant.now(clock);
  List<Object> args=new ArrayList<>(Arrays.asList(room,alarm.get("entityType"),alarm.get("entityId"),rule.get("metric"),rule.get("unit"),Timestamp.from(submitted),Timestamp.from(now)));
  List<Map<String,Object>> points=db.queryForList("SELECT id,entity_type AS entityType,entity_id AS entityId,metric,value,unit,observed_at AS observedAt,quality,source,record_origin AS recordOrigin FROM sp_reading WHERE room_id=? AND entity_type=? AND entity_id=? AND metric=? AND unit=? AND record_origin IN ('REAL','DEMO') AND quality='GOOD' AND observed_at>? AND observed_at<=?"+" ORDER BY observed_at DESC,id DESC",args.toArray());
  for(Map<String,Object> point:points){point.put("observedAt",((Timestamp)point.get("observedAt")).toInstant().toString());UnifiedDataSource.normalize(point);}
  points.removeIf(point->!Instant.parse((String)point.get("observedAt")).isAfter(submitted));
  if(points.isEmpty())return object("passed",null,"mode",origin,"path","DATABASE_READING","note","未收到本次提交后的对应对象有效监测数据");
  Instant latest=Instant.parse((String)points.get(0).get("observedAt"));long maxAge=((Number)rule.get("maxAgeSeconds")).longValue(),normal=((Number)rule.get("normalSeconds")).longValue(),gap=((Number)rule.get("maxGapSeconds")).longValue();
  if(maxAge<=0||normal<0||gap<=0)return object("passed",null,"note","恢复规则参数无效");
  if(Duration.between(latest,now).getSeconds()>maxAge)return object("passed",null,"note","采集数据已过期，保持待复测");
  double min=((Number)rule.get("min")).doubleValue(),max=((Number)rule.get("max")).doubleValue();Instant previous=latest;List<Map<String,Object>> evidence=new ArrayList<>();
  for(Map<String,Object>r:points){Instant at=Instant.parse((String)r.get("observedAt"));if(Duration.between(at,previous).getSeconds()>gap)break;double v=((Number)r.get("value")).doubleValue();evidence.add(r);if(v<min||v>max)return object("passed",false,"mode",origin,"path","DATABASE_READING","note","对应指标仍异常，返回处理中","evidence",evidence,"rule",rule,"checkedAt",now.toString());if(Duration.between(at,latest).getSeconds()>=normal)return object("passed",true,"mode",origin,"path","DATABASE_READING","note","监测数据满足恢复阈值与持续时间，已归档","evidence",evidence,"rule",rule,"checkedAt",now.toString());previous=at;}
  return object("passed",null,"note","正常数据持续时间不足或采样中断，保持待复测","evidence",evidence,"rule",rule,"checkedAt",now.toString());
 }
}
