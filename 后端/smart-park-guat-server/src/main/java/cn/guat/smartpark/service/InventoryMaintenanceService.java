package cn.guat.smartpark.service;

import cn.guat.smartpark.exception.PlatformException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;

@Service
public class InventoryMaintenanceService {
 private final JdbcTemplate db;
 private static final Map<String,String[]> FIELDS=new LinkedHashMap<>();
 static {
  FIELDS.put("parks",new String[]{"code","name"});
  FIELDS.put("rooms",new String[]{"park_id","code","name","location","manager_display_name","scene_model_key"});
  FIELDS.put("zones",new String[]{"room_id","code","name","display_order"});
  FIELDS.put("cabinets",new String[]{"zone_id","code","name","serial_number","capacity_u","scene_model_key","grid_row","grid_column","position_x","position_y","position_z","registration_status"});
  FIELDS.put("devices",new String[]{"cabinet_id","code","name","device_type","scene_model_key","legacy_key","registration_status","u_start","u_height","network_layer"});
 }
 public InventoryMaintenanceService(JdbcTemplate db){this.db=db;}
 private String table(String kind){if(!FIELDS.containsKey(kind))throw bad(400,"台账类型无效");return "sp_"+kind.substring(0,kind.length()-1);}
 private PlatformException bad(int status,String message){return new PlatformException(status,"INVENTORY_ERROR",message);}
 public List<Map<String,Object>> list(String kind){return db.queryForList("SELECT * FROM "+table(kind)+" ORDER BY id");}
 public Map<String,Object> get(String kind,String id){List<Map<String,Object>> rows=db.queryForList("SELECT * FROM "+table(kind)+" WHERE id=?",id);if(rows.isEmpty())throw bad(404,"台账记录不存在");return rows.get(0);}
 private String camel(String field){StringBuilder b=new StringBuilder();boolean upper=false;for(char c:field.toCharArray()){if(c=='_'){upper=true;continue;}b.append(upper?Character.toUpperCase(c):c);upper=false;}return b.toString();}
 private int integer(Object value){if(!(value instanceof Number)||((Number)value).doubleValue()!=((Number)value).intValue())throw bad(400,"需要整数参数");return ((Number)value).intValue();}
 private void validate(String kind,Map<String,Object> data,String id){
  for(String f:Arrays.asList("code","name"))if(data.get(f)==null||data.get(f).toString().trim().isEmpty()||data.get(f).toString().length()>(f.equals("code")?64:100))throw bad(400,"编号或名称无效");
  String origin=String.valueOf(data.get("record_origin"));if(!Arrays.asList("REAL","DEMO").contains(origin))throw bad(400,"数据来源类型无效");
  if(kind.equals("rooms"))get("parks",String.valueOf(data.get("park_id")));
  if(kind.equals("zones")){get("rooms",String.valueOf(data.get("room_id")));if(integer(data.get("display_order"))<0)throw bad(400,"区域排序无效");}
  if(kind.equals("cabinets")){
   get("zones",String.valueOf(data.get("zone_id")));int capacity=integer(data.get("capacity_u"));if(capacity<1||integer(data.get("grid_row"))<0||integer(data.get("grid_column"))<0)throw bad(400,"机柜容量或位置无效");
   if(id!=null&&db.queryForObject("SELECT COUNT(*) FROM sp_device WHERE cabinet_id=? AND u_start+u_height-1>?",Integer.class,id,capacity)>0)throw bad(409,"容量小于已登记设备的 U 位");
  }
  if(kind.equals("devices")){
   Map<String,Object> cabinet=get("cabinets",String.valueOf(data.get("cabinet_id")));
   db.queryForList("SELECT id FROM sp_cabinet WHERE id=? FOR UPDATE",data.get("cabinet_id"));
   if(!Arrays.asList("SERVER","SWITCH","STORAGE","OTHER").contains(data.get("device_type")))throw bad(400,"设备类型无效");
   if(data.get("network_layer")!=null&&!Arrays.asList("CORE","AGGREGATE","ACCESS","TERMINAL").contains(data.get("network_layer")))throw bad(400,"网络层级无效");
   Object start=data.get("u_start"),height=data.get("u_height");if((start==null)!=(height==null))throw bad(400,"起始 U 位与高度必须同时填写");
   if(start!=null){int s=integer(start),h=integer(height);if(s<1||h<1||s+h-1>integer(cabinet.get("capacity_u")))throw bad(400,"设备 U 位超出容量");if(db.queryForObject("SELECT COUNT(*) FROM sp_device WHERE cabinet_id=? AND id<>? AND u_start IS NOT NULL AND u_start<? AND u_start+u_height>?",Integer.class,data.get("cabinet_id"),id==null?"0":id,s+h,s)>0)throw bad(409,"设备 U 位重叠");}
  }
  if(data.containsKey("registration_status")&&!Arrays.asList("ACTIVE","INACTIVE").contains(data.get("registration_status")))throw bad(400,"登记状态无效");
  for(String f:FIELDS.get(kind))if(!Arrays.asList("manager_display_name","u_start","u_height","network_layer").contains(f)&&data.get(f)==null)throw bad(400,"缺少字段："+camel(f));
 }
 @Transactional public Map<String,Object> save(String kind,String id,Map<String,Object> body){
  String table=table(kind);Map<String,Object> data=id==null?new LinkedHashMap<>():new LinkedHashMap<>(get(kind,id));
  for(String key:body.keySet()){boolean allowed=key.equals("recordOrigin");for(String f:FIELDS.get(kind))allowed|=key.equals(camel(f));if(!allowed)throw bad(400,"不支持的字段："+key);}
  for(String f:FIELDS.get(kind))if(body.containsKey(camel(f)))data.put(f,body.get(camel(f)));
  data.put("record_origin",UnifiedDataSource.origin(body.getOrDefault("recordOrigin",data.getOrDefault("record_origin","REAL"))));
  validate(kind,data,id);List<String> fields=new ArrayList<>(Arrays.asList(FIELDS.get(kind)));fields.add("record_origin");List<Object> values=new ArrayList<>();for(String f:fields)values.add(data.get(f));
  try{
   if(id==null){List<String> slots=new ArrayList<>();for(String f:fields)slots.add("?");db.update("INSERT INTO "+table+" ("+String.join(",",fields)+",source,created_at,updated_at,source_updated_at) VALUES ("+String.join(",",slots)+",'MANUAL',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",values.toArray());return db.queryForMap("SELECT * FROM "+table+" WHERE code=? ORDER BY id DESC LIMIT 1",data.get("code"));}
   List<String> sets=new ArrayList<>();for(String f:fields)sets.add(f+"=?");values.add(id);db.update("UPDATE "+table+" SET "+String.join(",",sets)+",source='MANUAL',updated_at=UTC_TIMESTAMP(3),source_updated_at=UTC_TIMESTAMP(3) WHERE id=?",values.toArray());return get(kind,id);
  }catch(org.springframework.dao.DataIntegrityViolationException e){throw bad(409,"编号、场景标识或关联资料冲突");}
 }
 @Transactional public void delete(String kind,String id){
  get(kind,id);
  if(kind.equals("rooms")){
   int samples=db.queryForObject("SELECT COUNT(*) FROM sp_reading WHERE room_id=?",Integer.class,id)+db.queryForObject("SELECT COUNT(*) FROM sp_energy_sample WHERE room_id=?",Integer.class,id)+db.queryForObject("SELECT COUNT(*) FROM sp_monitor_record WHERE room_id=?",Integer.class,id);
   if(samples>0)throw bad(409,"机房存在监测历史或配置，请保留台账关联");
  }
  if(kind.equals("cabinets")||kind.equals("devices")){
   String type=kind.equals("cabinets")?"CABINET":"DEVICE",field=kind.equals("cabinets")?"cabinetId":"deviceId";
   int references=db.queryForObject("SELECT COUNT(*) FROM sp_reading WHERE entity_type=? AND entity_id=?",Integer.class,type,id)+db.queryForObject("SELECT COUNT(*) FROM sp_monitor_record WHERE JSON_UNQUOTE(JSON_EXTRACT(payload,'$."+field+"'))=? OR (JSON_UNQUOTE(JSON_EXTRACT(payload,'$.entityType'))=? AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.entityId'))=?)",Integer.class,id,type,id)+db.queryForObject("SELECT COUNT(*) FROM console_order WHERE JSON_UNQUOTE(JSON_EXTRACT(payload,'$."+field+"'))=?",Integer.class,id);
   if(kind.equals("cabinets"))references+=db.queryForObject("SELECT COUNT(*) FROM sp_energy_sample WHERE cabinet_id=?",Integer.class,id);
   if(references>0)throw bad(409,"存在监测历史、规则或工单，请改为停用台账");
  }
  try{db.update("DELETE FROM "+table(kind)+" WHERE id=?",id);}catch(org.springframework.dao.DataIntegrityViolationException e){throw bad(409,"存在下级台账，请先移除关联记录");}
 }
}
