package cn.guat.smartpark.service;

import cn.guat.smartpark.exception.PlatformException;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;

/** Read-only adapter. The authenticated room is never accepted from model arguments. */
@Service
public class AssistantAssetTools {
 private final InventoryService inventory;
 private static final Set<String> NAMES=new HashSet<>(Arrays.asList("get_room_info","list_cabinets","get_cabinet_info","list_devices","get_device_info"));
 public AssistantAssetTools(InventoryService inventory){this.inventory=inventory;}
 public boolean supports(String name){return NAMES.contains(name);}
 private PlatformException bad(String message){return new PlatformException(400,"INVALID_ARGUMENT",message);}
 private String text(Map<String,Object>a,String key,String fallback){if(!a.containsKey(key))return fallback;Object v=a.get(key);if(!(v instanceof String)||((String)v).trim().isEmpty())throw bad(key+" 必须为非空字符串");return ((String)v).trim();}
 private String id(Map<String,Object>a,String key){String v=text(a,key,null);if(v==null||!v.matches("[1-9][0-9]*"))throw bad(key+" 必须为稳定数字ID");try{Long.parseLong(v);}catch(NumberFormatException e){throw bad(key+" 超出范围");}return v;}
 private int number(Map<String,Object>a,String key,int fallback,int max){if(!a.containsKey(key))return fallback;Object v=a.get(key);if(!(v instanceof Number))throw bad(key+" 必须为整数");try{int n=new java.math.BigDecimal(v.toString()).intValueExact();if(n<1||n>max)throw bad(key+" 超出范围");return n;}catch(ArithmeticException|NumberFormatException e){throw bad(key+" 必须为有效整数");}}
 private String cabinet(String room,Map<String,Object>a){if(a.containsKey("cabinetId")==a.containsKey("cabinetKey"))throw bad("cabinetId 与 cabinetKey 必须且只能提供一个");return inventory.scopedCabinet(room,a.containsKey("cabinetId")?id(a,"cabinetId"):null,a.containsKey("cabinetKey")?text(a,"cabinetKey",null):null);}
 public Map<String,Object> execute(String room,String name,Map<String,Object>a){
  if(!supports(name))throw bad("未知资产工具");
  Set<String>allowed=new HashSet<>();
  if(name.equals("get_device_info"))allowed.add("deviceId");
  if(name.equals("get_cabinet_info")||name.equals("list_devices"))allowed.addAll(Arrays.asList("cabinetId","cabinetKey"));
  boolean paged=name.equals("list_cabinets")||name.equals("list_devices");
  if(paged)allowed.addAll(Arrays.asList("q","page","pageSize"));
  if(name.equals("list_cabinets"))allowed.add("zoneId");if(name.equals("list_devices"))allowed.add("type");
  for(String key:a.keySet())if(!allowed.contains(key))throw bad("不允许的参数："+key);
  int page=1,size=20;String q="";
  if(paged){page=number(a,"page",1,Integer.MAX_VALUE);size=number(a,"pageSize",20,100);if(a.containsKey("q")){if(!(a.get("q") instanceof String))throw bad("q 必须为字符串");q=((String)a.get("q")).trim();}if(q.length()>100)throw bad("q 最多100字");}
  Object data;
  switch(name){
   case "get_room_info":data=inventory.room(room);break;
   case "list_cabinets":data=inventory.cabinets(room,a.containsKey("zoneId")?id(a,"zoneId"):null,q,page,size,"id,asc");break;
   case "get_cabinet_info":data=inventory.cabinet(cabinet(room,a));break;
   case "list_devices":String type=text(a,"type",null);if(type!=null&&!Arrays.asList("SWITCH","SERVER","STORAGE","OTHER").contains(type))throw bad("设备类型无效");data=inventory.devices(cabinet(room,a),q,type,page,size,"id,asc");break;
   default:data=inventory.device(room,id(a,"deviceId"));
  }
  Map<String,Object>result=object("roomId",room,"data",data,"missingValueMeaning","null 表示台账未提供或监测缺失；UNKNOWN 表示状态未知，不代表正常");
  if(paged){Map<?,?>p=(Map<?,?>)data;long total=((Number)p.get("total")).longValue(),start=((long)page-1)*size;int count=((List<?>)p.get("items")).size();result.put("range",object("page",page,"pageSize",size,"total",total,"returned",count,"from",count==0?0:start+1,"to",count==0?0:start+count,"hasMore",start+count<total,"scope","当前筛选条件的分页结果，不代表完整台账"));}
  return result;
 }
 public List<Map<String,Object>> definitions(){
  List<Map<String,Object>>out=new ArrayList<>();for(String name:Arrays.asList("get_room_info","list_cabinets","get_cabinet_info","list_devices","get_device_info")){
   Map<String,Object>props=object();List<String>required=new ArrayList<>();
   if(name.equals("get_device_info")){props.put("deviceId",field("设备稳定ID"));required.add("deviceId");}
   if(name.equals("get_cabinet_info")||name.equals("list_devices")){props.put("cabinetId",field("机柜稳定ID，与 cabinetKey 二选一"));props.put("cabinetKey",field("当前机房场景编号，与 cabinetId 二选一"));}
   if(name.startsWith("list_")){props.put("q",object("type","string","maxLength",100));props.put("page",object("type","integer","minimum",1,"maximum",Integer.MAX_VALUE));props.put("pageSize",object("type","integer","minimum",1,"maximum",100));}
   if(name.equals("list_cabinets"))props.put("zoneId",field("当前机房区域稳定ID"));if(name.equals("list_devices"))props.put("type",object("type","string","enum",Arrays.asList("SWITCH","SERVER","STORAGE","OTHER")));
   out.add(object("type","function","function",object("name",name,"description","只读当前机房资产台账："+name+"。缺失字段不可推测；列表必须说明分页范围，可继续翻页。","parameters",object("type","object","properties",props,"required",required,"additionalProperties",false))));
  }return out;
 }
 private Map<String,Object>field(String description){return object("type","string","description",description,"minLength",1);}
}
