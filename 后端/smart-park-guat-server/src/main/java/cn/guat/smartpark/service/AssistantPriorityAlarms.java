package cn.guat.smartpark.service;

import cn.guat.smartpark.entity.Cabinet;
import cn.guat.smartpark.entity.Device;
import cn.guat.smartpark.mapper.InventoryMapper;
import cn.guat.smartpark.exception.PlatformException;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;

/** Read-only priority policy; equal severity never implies an operational ordering. */
@Service
public class AssistantPriorityAlarms {
 private final MonitoringService monitoring;
 private final InventoryMapper inventory;
 private static final List<String> LEVELS=Arrays.asList("critical","warning","normal","info");
 public AssistantPriorityAlarms(MonitoringService monitoring,InventoryMapper inventory){this.monitoring=monitoring;this.inventory=inventory;}
 private PlatformException bad(String message){return new PlatformException(400,"INVALID_ARGUMENT",message);}
 private String str(Map<?,?>m,String key){Object v=m.get(key);return v==null?"":v.toString();}
 public Map<String,Object> execute(String room,Map<String,Object>args){
  for(String key:args.keySet())if(!Arrays.asList("cabinetKey","limit").contains(key))throw bad("未知优先告警参数："+key);
  String requested="";if(args.containsKey("cabinetKey")){if(!(args.get("cabinetKey") instanceof String)||((String)args.get("cabinetKey")).trim().isEmpty())throw bad("cabinetKey 必须为非空字符串");requested=((String)args.get("cabinetKey")).trim();}
  int limit=20;if(args.containsKey("limit")){Object value=args.get("limit");if(!(value instanceof Number))throw bad("limit 必须为整数");try{limit=new java.math.BigDecimal(value.toString()).intValueExact();}catch(ArithmeticException|NumberFormatException e){throw bad("limit 必须为整数");}if(limit<1||limit>100)throw bad("limit 范围为1至100");}
  monitoring.room(room);long roomId;try{roomId=Long.parseLong(room);}catch(NumberFormatException e){throw bad("机房ID无效");}
  List<Cabinet> cabinets=inventory.cabinets(roomId);List<Device>devices=inventory.devices(roomId);
  Map<String,Cabinet>byId=new HashMap<>();Map<String,Long>keyCounts=new HashMap<>();
  for(Cabinet c:cabinets){byId.put(c.getId().toString(),c);if(c.getSceneModelKey()!=null&&!c.getSceneModelKey().trim().isEmpty())keyCounts.merge(c.getSceneModelKey(),1L,Long::sum);}
  String filter="";if(!requested.isEmpty()){final String key=requested;List<Cabinet>matches=cabinets.stream().filter(c->key.equals(c.getSceneModelKey())).collect(Collectors.toList());if(matches.size()!=1)throw bad("机柜不属于当前机房或映射不唯一");filter=matches.get(0).getId().toString();}
  Map<String,String>deviceCabinet=new HashMap<>();for(Device d:devices)deviceCabinet.put(d.getId().toString(),String.valueOf(d.getCabinetId()));
  List<Map<String,Object>>items=new ArrayList<>();Set<String>ids=new HashSet<>();
  for(Map<String,Object>a:monitoring.records(room,"ALARM")){
   if(!room.equals(str(a,"roomId")))throw bad("告警返回了跨机房数据，已拒绝本次查询");
   if("已恢复".equals(a.get("status")))continue;
   String id=str(a,"id");if(id.isEmpty()||!ids.add(id))throw bad("告警缺少唯一稳定ID");
   String cabinetId=str(a,"cabinetId"),deviceId=str(a,"deviceId"),mappedDevice=deviceCabinet.get(deviceId);
   boolean mismatch=!deviceId.isEmpty()&&(mappedDevice==null||(!cabinetId.isEmpty()&&!cabinetId.equals(mappedDevice)));
   if(cabinetId.isEmpty()&&mappedDevice!=null)cabinetId=mappedDevice;
   if(!filter.isEmpty()&&!filter.equals(cabinetId))continue;
   Cabinet cabinet=byId.get(cabinetId);String sceneKey=cabinet==null?null:cabinet.getSceneModelKey();
   boolean inspectable=!mismatch&&sceneKey!=null&&!sceneKey.trim().isEmpty()&&Long.valueOf(1).equals(keyCounts.get(sceneKey));
   String severity=str(a,"severity");int index=LEVELS.indexOf(severity),rank=index<0?4:index;
   Map<String,Object>item=object("id",id,"roomId",room,"title",a.get("title"),"status",a.get("status"),"severity",a.get("severity"),"severityLabel",Arrays.asList("严重告警","重要告警","一般告警","提示告警","未标明等级").get(rank),"priorityRank",rank,"rankingBasis","仅依据告警等级；同等级按稳定ID展示，不代表优先顺序","cabinetId",cabinet==null?null:cabinetId,"deviceId",deviceId.isEmpty()?null:deviceId,"cabinetKey",inspectable?sceneKey:null,"cabinetName",cabinet==null?null:cabinet.getName(),"inspectable",inspectable,"sceneTarget",inspectable?object("cabinetSceneModelKey",sceneKey):null,"mappingReason",inspectable?"LINKED":mismatch?"DEVICE_CABINET_MISMATCH":"NO_UNIQUE_CABINET_MAPPING","recordOrigin",a.get("recordOrigin"),"source",a.get("source"),"observedAt",a.get("observedAt"));
   item.put("alarmCode",a.get("alarmCode"));items.add(item);
  }
  items.sort(Comparator.<Map<String,Object>>comparingInt(v->((Number)v.get("priorityRank")).intValue()).thenComparing(v->str(v,"id")));
  int total=items.size(),top=items.isEmpty()?-1:((Number)items.get(0).get("priorityRank")).intValue();long ties=items.stream().filter(v->((Number)v.get("priorityRank")).intValue()==top).count();
  return object("roomId",room,"items",new ArrayList<>(items.subList(0,Math.min(limit,total))),"total",total,"returned",Math.min(limit,total),"limit",limit,"hasMore",total>limit,"highestPriorityRank",top<0?null:top,"highestPriorityCount",ties,"requiresSelection",ties>1,"policy","critical → warning → normal → info → 未知；并列需用户选择，不比较跨指标数值或臆测故障影响","missingValueMeaning","null 表示来源字段缺失或无有效机柜映射，不能生成定位目标");
 }
 public Map<String,Object> definition(){return object("type","function","function",object("name","get_priority_alarms","description","只读查询当前机房未恢复告警的等级优先级。最高等级并列必须展示候选并澄清，不默认第一条；无有效映射不可巡检；不建单、不指派、不修改告警。limit 截断不消除并列。","parameters",object("type","object","properties",object("cabinetKey",object("type","string","minLength",1),"limit",object("type","integer","minimum",1,"maximum",100)),"required",Collections.emptyList(),"additionalProperties",false)));}
}
