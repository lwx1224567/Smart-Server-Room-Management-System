package cn.guat.smartpark.service;
import cn.guat.smartpark.entity.*;
import cn.guat.smartpark.mapper.InventoryMapper;
import cn.guat.smartpark.exception.PlatformException;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import java.time.*;
@Service @Transactional(readOnly=true)
public class InventoryService {
 private final InventoryMapper mapper; private final MonitoringService monitoring;
 public InventoryService(InventoryMapper mapper,MonitoringService monitoring){this.mapper=mapper;this.monitoring=monitoring;}
 private PlatformException invalid(String message){return new PlatformException(400,"INVALID_ARGUMENT",message);}
 private PlatformException missing(String message){return new PlatformException(404,"NOT_FOUND",message);}
 private long id(String v){try{if(v==null||!v.matches("[1-9][0-9]*"))throw invalid("ID格式无效");return Long.parseLong(v);}catch(NumberFormatException e){throw invalid("ID超出范围");}}
 private void origin(String v){if(!Arrays.asList("DEMO","REAL").contains(v))throw invalid("数据来源筛选无效");}
 private void query(String q){if(q.length()>100)throw invalid("关键词最多100字");}
 private boolean matches(InventoryRecord r,String q){return r.getCode().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))||r.getName().toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT));}
 private Room findRoom(long id){return mapper.rooms().stream().filter(r->r.getId()==id).findFirst().orElseThrow(()->missing("机房不存在"));}
 private Cabinet findCabinet(long id){Cabinet c=mapper.cabinet(id);if(c==null)throw missing("机柜不存在");return c;}
 private Park park(long id){return mapper.parks().stream().filter(p->p.getId()==id).findFirst().orElseThrow(()->missing("园区不存在"));}
 private Map<String,Object> meta(InventoryRecord r){return object("recordOrigin",UnifiedDataSource.origin(r.getRecordOrigin()),"source",r.getSource(),"sourceUpdatedAt",r.getSourceUpdatedAt()==null?null:r.getSourceUpdatedAt().toInstant(ZoneOffset.UTC).toString(),"quality","GOOD","generatedAt",Instant.now().toString());}
 private Map<String,Object> base(InventoryRecord r){return object("id",r.getId().toString(),"code",r.getCode(),"name",r.getName(),"meta",meta(r));}
 private Map<String,Object> roomDto(Room r){Map<String,Object> m=base(r);m.putAll(object("parkId",r.getParkId().toString(),"parkName",park(r.getParkId()).getName(),"location",r.getLocation(),"managerDisplayName",r.getManagerDisplayName(),"sceneModelKey",r.getSceneModelKey()));return m;}
 private Map<String,Object> cabinetDto(Cabinet c,List<Device> devices){Zone z=mapper.zone(c.getZoneId());Map<String,Object> m=base(c);m.putAll(object("roomId",z.getRoomId().toString(),"zoneId",c.getZoneId().toString(),"code",c.getCode(),"sceneModelKey",c.getSceneModelKey(),"capacityU",c.getCapacityU(),"registrationStatus",c.getRegistrationStatus(),"registeredDeviceCount",devices.stream().filter(d->d.getCabinetId().equals(c.getId())).count(),"runtimeStatus","UNKNOWN"));return m;}
 private Map<String,Object> deviceDto(Device d){Map<String,Object> m=base(d);m.putAll(object("cabinetId",d.getCabinetId().toString(),"type",d.getDeviceType(),"sceneModelKey",d.getSceneModelKey(),"legacyKey",d.getLegacyKey(),"registrationStatus",d.getRegistrationStatus(),"runtimeStatus","UNKNOWN","uStart",d.getUStart(),"uHeight",d.getUHeight()));return m;}
 private <T extends InventoryRecord> Map<String,Object> page(List<T> rows,int page,int size,String sort,String[] allowed,java.util.function.Function<T,Map<String,Object>> project,InventoryRecord scope){
   if(page<1||size<1||size>100)throw invalid("分页参数无效");
   String[] parts=sort.split(",",-1);if(parts.length!=2||!Arrays.asList(allowed).contains(parts[0])||!Arrays.asList("asc","desc").contains(parts[1].toLowerCase(Locale.ROOT)))throw invalid("排序参数无效");
   Comparator<T> cmp=parts[0].equals("id")?Comparator.comparing(InventoryRecord::getId):parts[0].equals("name")?Comparator.comparing(InventoryRecord::getName):Comparator.comparing(InventoryRecord::getCode);
   if(parts[1].equalsIgnoreCase("desc"))cmp=cmp.reversed();rows.sort(cmp.thenComparing(InventoryRecord::getId));
   long start=((long)page-1)*size;List<Map<String,Object>> items=start>=rows.size()?Collections.emptyList():rows.subList((int)start,(int)Math.min(start+size,rows.size())).stream().map(project).collect(Collectors.toList());
   return object("items",items,"total",rows.size(),"page",page,"pageSize",size,"meta",scope==null?object("recordOrigin","REAL","source","MOCK_IMPORT","sourceUpdatedAt",null,"quality","GOOD","generatedAt",Instant.now().toString()):meta(scope));
 }
 public Object rooms(String q,String recordOrigin,int page,int size,String sort){query(q);origin(recordOrigin);List<Room> rows=mapper.rooms().stream().filter(r->matches(r,q)).collect(Collectors.toList());Map<String,Object> result=page(rows,page,size,sort,new String[]{"id","name"},this::roomDto,rows.isEmpty()?null:rows.get(0));if(rows.isEmpty())result.put("meta",object("recordOrigin",UnifiedDataSource.origin(recordOrigin),"source","MOCK_IMPORT","sourceUpdatedAt",null,"quality","GOOD","generatedAt",Instant.now().toString()));return result;}
 public Object room(String roomId){Room r=findRoom(id(roomId));Map<String,Object> m=roomDto(r);m.putAll(object("zoneCount",mapper.zones(r.getId()).size(),"cabinetCount",mapper.cabinets(r.getId()).size(),"registeredDeviceCount",mapper.devices(r.getId()).size()));return m;}
 public Object scene(String roomId,String recordOrigin){origin(recordOrigin);Room r=findRoom(id(roomId));
   List<Zone> zones=mapper.zones(r.getId());List<Cabinet> cabinets=mapper.cabinets(r.getId());List<Device> devices=mapper.devices(r.getId());
   List<Map<String,Object>> z=zones.stream().map(v->{Map<String,Object> m=base(v);m.put("displayOrder",v.getDisplayOrder());return m;}).collect(Collectors.toList());
   List<Map<String,Object>> c=cabinets.stream().filter(v->v.getRecordOrigin().equals(recordOrigin)).map(v->{Map<String,Object> m=cabinetDto(v,devices);m.putAll(object("position",Arrays.asList(v.getPositionX(),v.getPositionY(),v.getPositionZ()),"gridRow",v.getGridRow(),"gridColumn",v.getGridColumn()));return m;}).collect(Collectors.toList());
   return object("roomId",r.getId().toString(),"zones",z,"cabinets",c,"devices",devices.stream().filter(v->v.getRecordOrigin().equals(recordOrigin)).map(this::deviceDto).collect(Collectors.toList()),"meta",meta(r));
 }
 public Object cabinets(String roomId,String zoneId,String q,int page,int size,String sort){query(q);Room r=findRoom(id(roomId));Long zid=zoneId==null?null:id(zoneId);if(zid!=null){Zone z=mapper.zone(zid);if(z==null||!z.getRoomId().equals(r.getId()))throw missing("区域不属于该机房");}
   List<Device> ds=mapper.devices(r.getId());List<Cabinet> rows=mapper.cabinets(r.getId()).stream().filter(c->(zid==null||c.getZoneId().equals(zid))&&matches(c,q)).collect(Collectors.toList());return page(rows,page,size,sort,new String[]{"id","code"},c->cabinetDto(c,ds),r);
 }
 public Object cabinet(String cabinetId){Cabinet c=findCabinet(id(cabinetId));Zone z=mapper.zone(c.getZoneId());Room r=findRoom(z.getRoomId());List<Device> ds=mapper.cabinetDevices(c.getId());Map<String,Object> m=cabinetDto(c,ds);
   Integer used=ds.isEmpty()||ds.stream().anyMatch(d->d.getUStart()==null||d.getUHeight()==null)?null:ds.stream().mapToInt(Device::getUHeight).sum();
   m.putAll(object("roomName",r.getName(),"location",r.getLocation(),"zoneCode",z.getCode(),"serialNumber",c.getSerialNumber(),"usedU",used,"telemetry",object("runtimeStatus","UNKNOWN","powerState","UNKNOWN","networkState","UNKNOWN","temperature",monitoring.reading(r.getId().toString(),"CABINET",c.getId().toString(),"temperature","°C"),"power",monitoring.reading(r.getId().toString(),"CABINET",c.getId().toString(),"power","kW"))));return m;
 }
 private Map<String,Object> reading(String unit){return object("value",null,"unit",unit,"observedAt",null,"quality","MISSING");}
 public Object devices(String cabinetId,String q,String type,int page,int size,String sort){query(q);Cabinet c=findCabinet(id(cabinetId));if(type!=null&&!Arrays.asList("SWITCH","SERVER","STORAGE","OTHER").contains(type))throw invalid("设备类型无效");List<Device> rows=mapper.cabinetDevices(c.getId()).stream().filter(d->(type==null||d.getDeviceType().equals(type))&&matches(d,q)).collect(Collectors.toList());return page(rows,page,size,sort,new String[]{"id","code"},this::deviceDto,c);}
 /** Resolve identity within a room before exposing cabinet details or devices. */
 public String scopedCabinet(String roomId,String cabinetId,String sceneKey){
  Room room=findRoom(id(roomId));Long wanted=cabinetId==null?null:id(cabinetId);
  List<Cabinet> matches=mapper.cabinets(room.getId()).stream().filter(c->wanted!=null?wanted.equals(c.getId()):Objects.equals(sceneKey,c.getSceneModelKey())).collect(Collectors.toList());
  if(matches.size()!=1)throw missing("当前机房没有唯一匹配机柜");return matches.get(0).getId().toString();
 }
 public Object device(String roomId,String deviceId){
  Room room=findRoom(id(roomId));long wanted=id(deviceId);
  Device device=mapper.devices(room.getId()).stream().filter(d->d.getId()==wanted).findFirst().orElseThrow(()->missing("设备不属于当前机房"));
  Map<String,Object> result=deviceDto(device);result.put("roomId",roomId);result.put("networkLayer",device.getNetworkLayer());return result;
 }
}

