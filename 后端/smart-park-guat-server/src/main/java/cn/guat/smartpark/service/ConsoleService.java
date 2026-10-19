package cn.guat.smartpark.service;

import cn.guat.smartpark.exception.PlatformException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import javax.annotation.PostConstruct;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.*;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;

/** Persisted console accounts, revocable sessions and maintenance work orders. */
@Service
public class ConsoleService {
 @org.springframework.beans.factory.annotation.Autowired private WorkOrderNumberService orderNumbers;
 @org.springframework.beans.factory.annotation.Autowired private ReadableIdentifierService readableIds;
 private final JdbcTemplate db; private final ObjectMapper json; private final MonitoringService monitoring; private boolean legacyAccounts;
 public ConsoleService(JdbcTemplate db,ObjectMapper json,MonitoringService monitoring){this.db=db;this.json=json;this.monitoring=monitoring;}
 @PostConstruct public void schema(){
  db.execute("CREATE TABLE IF NOT EXISTS console_account (id BIGINT PRIMARY KEY AUTO_INCREMENT,user_name VARCHAR(100) NOT NULL UNIQUE,display_name VARCHAR(100) NOT NULL,role VARCHAR(20) NOT NULL,password_hash VARCHAR(512) NOT NULL,phone VARCHAR(100),email VARCHAR(200),enabled BOOLEAN NOT NULL DEFAULT TRUE)");
  db.execute("CREATE TABLE IF NOT EXISTS console_session (token_hash CHAR(64) PRIMARY KEY,account_id BIGINT NOT NULL,expires_at TIMESTAMP NOT NULL)");
  if(readableIds!=null)readableIds.sessionSchema();
  db.execute("CREATE TABLE IF NOT EXISTS console_order (id VARCHAR(80) PRIMARY KEY,owner_account_id BIGINT NULL,status VARCHAR(30) NOT NULL,payload LONGTEXT NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
  migrateOwnerColumn();
  if(orderNumbers!=null)orderNumbers.backfill(json);
  db.execute("CREATE TABLE IF NOT EXISTS console_notice (id BIGINT PRIMARY KEY AUTO_INCREMENT,account_id BIGINT NOT NULL,title VARCHAR(200) NOT NULL,content TEXT NOT NULL,order_id VARCHAR(80),is_read BOOLEAN DEFAULT FALSE,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
  db.execute("CREATE TABLE IF NOT EXISTS console_person_profile (account_id BIGINT PRIMARY KEY,biography VARCHAR(1000) NOT NULL,skills TEXT NOT NULL)");
  legacyAccounts=db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='user'",Integer.class)>0;
  if(legacyAccounts){
   for(Map<String,Object>legacy:db.queryForList("SELECT * FROM `user`")){
    String name=String.valueOf(legacy.get("user_name"));
    if(db.queryForObject("SELECT COUNT(*) FROM console_account WHERE user_name=?",Integer.class,name)==0)
     db.update("INSERT INTO console_account(user_name,display_name,role,password_hash,phone,email,enabled) VALUES(?,?,?,?,?,?,?)",name,name,"admin".equalsIgnoreCase(name)?"ADMIN":"MAINTAINER",encode(String.valueOf(legacy.get("password"))),legacy.get("phone_number"),legacy.get("email"),!"0".equals(String.valueOf(legacy.get("status"))));
   }
  }
 }
 private PlatformException bad(int code,String text){return new PlatformException(code,"CONSOLE_ERROR",text);}
 private String str(Map<String,Object> b,String key){Object v=b.get(key);return v==null?"":String.valueOf(v).trim();}
 private String required(Map<String,Object>b,String key){String s=str(b,key);if(s.isEmpty()||s.length()>4000)throw bad(400,"请填写有效的"+key);return s;}
 private String digest(String s){try{return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")));}catch(Exception e){throw new IllegalStateException(e);}}
 private String hash(String password,String salt){try{return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(password.toCharArray(),Base64.getDecoder().decode(salt),120000,256)).getEncoded());}catch(Exception e){throw new IllegalStateException(e);}}
 private String encode(String password){byte[] s=new byte[16];new SecureRandom().nextBytes(s);String salt=Base64.getEncoder().encodeToString(s);return salt+":"+hash(password,salt);}
 private boolean matches(String password,String encoded){String[] p=encoded.split(":");return p.length==2&&MessageDigest.isEqual(hash(password,p[0]).getBytes(),p[1].getBytes());}
 private void password(String p){if(p.length()<8||p.length()>128)throw bad(400,"密码长度应为8至128位");}
 private Map<String,Object> publicUser(Map<String,Object>a){Map<String,Object> out=object("id",a.get("id"),"userName",a.get("user_name"),"displayName",a.get("display_name"),"role",a.get("role"),"phone",a.get("phone"),"email",a.get("email"),"enabled",a.get("enabled"));List<Map<String,Object>> profiles=db.queryForList("SELECT biography,skills FROM console_person_profile WHERE account_id=?",a.get("id"));out.put("biography",profiles.isEmpty()?"":profiles.get(0).get("biography"));out.put("skills",profiles.isEmpty()?Collections.emptyList():readSkills(String.valueOf(profiles.get(0).get("skills"))));return out;}
 private List<String> readSkills(String raw){try{return json.readValue(raw,new TypeReference<List<String>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
 private List<String> skills(Map<String,Object>b){Object raw=b.get("skills");if(raw==null)return Collections.emptyList();if(!(raw instanceof List))throw bad(400,"请选择有效技能标签");List<String> out=new ArrayList<>();for(Object tag:(List<?>)raw){if(!Arrays.asList("制冷散热","电气供电","网络通信","设备硬件").contains(tag))throw bad(400,"未知技能标签");if(!out.contains(tag))out.add((String)tag);}return out;}
 @Transactional public Map<String,Object> login(Map<String,Object>b){
  String name=required(b,"userName"),p=required(b,"password");
  List<Map<String,Object>> rows=db.queryForList("SELECT * FROM console_account WHERE user_name=?",name);
  if(rows.isEmpty())throw bad(401,"账号或密码错误");
  Map<String,Object>a=rows.get(0);if(!matches(p,(String)a.get("password_hash")))throw bad(401,"账号或密码错误");if(!Boolean.TRUE.equals(a.get("enabled"))&&!"1".equals(String.valueOf(a.get("enabled"))))throw bad(403,"账号已停用");
  String token=UUID.randomUUID().toString()+UUID.randomUUID();db.update("INSERT INTO console_session(token_hash,account_id,expires_at) VALUES(?,?,DATE_ADD(NOW(),INTERVAL 8 HOUR))",digest(token),a.get("id"));
  return object("token",token,"user",publicUser(a),"sessionCode",readableIds==null?null:readableIds.sessionCode(digest(token),java.time.Instant.now()));
 }
 public Map<String,Object> identity(String header){if(header==null||!header.startsWith("Bearer "))throw bad(401,"请先登录");List<Map<String,Object>>rows=db.queryForList("SELECT a.* FROM console_account a JOIN console_session s ON s.account_id=a.id WHERE s.token_hash=? AND s.expires_at>NOW() AND a.enabled=TRUE",digest(header.substring(7)));if(rows.isEmpty())throw bad(401,"登录已失效，请重新登录");return publicUser(rows.get(0));}
 public void admin(Map<String,Object>a){if(!"ADMIN".equals(a.get("role")))throw bad(403,"仅管理员可以操作");}
 public void logout(String header){if(header!=null&&header.startsWith("Bearer "))db.update("DELETE FROM console_session WHERE token_hash=?",digest(header.substring(7)));}
 @Transactional public void changePassword(Map<String,Object>a,Map<String,Object>b){String p=required(b,"newPassword");password(p);String current=db.queryForObject("SELECT password_hash FROM console_account WHERE id=?",String.class,a.get("id"));if(!matches(required(b,"oldPassword"),current))throw bad(400,"原密码不正确");db.update("UPDATE console_account SET password_hash=? WHERE id=?",encode(p),a.get("id"));db.update("DELETE FROM console_session WHERE account_id=?",a.get("id"));}
 public List<Map<String,Object>> people(Map<String,Object>a){admin(a);List<Map<String,Object>>out=new ArrayList<>();for(Map<String,Object>u:db.queryForList("SELECT * FROM console_account WHERE role='MAINTAINER' ORDER BY id DESC"))out.add(publicUser(u));return out;}
 @Transactional public void savePerson(Map<String,Object>a,Map<String,Object>b,Long id){admin(a);String biography=str(b,"biography");List<String> tags=skills(b);if(biography.length()>1000)throw bad(400,"个人简介最多1000字");String name=required(b,"displayName"),p=str(b,"password");if(name.length()>100)throw bad(400,"姓名过长");boolean enabled=!Boolean.FALSE.equals(b.get("enabled"));if(id==null){password(p);String userName=required(b,"userName");if(userName.length()>100||name.length()>100)throw bad(400,"姓名或账号过长");if(db.queryForObject("SELECT COUNT(*) FROM console_account WHERE user_name=?",Integer.class,userName)>0)throw bad(409,"账号已存在");db.update("INSERT INTO console_account(user_name,display_name,role,password_hash,phone,email,enabled) VALUES(?,?,'MAINTAINER',?,?,?,?)",userName,name,encode(p),str(b,"phone"),str(b,"email"),enabled);}else{if(db.update("UPDATE console_account SET display_name=?,phone=?,email=?,enabled=? WHERE id=? AND role='MAINTAINER'",name,str(b,"phone"),str(b,"email"),enabled,id)==0)throw bad(404,"维保人员不存在");if(!p.isEmpty()){password(p);db.update("UPDATE console_account SET password_hash=? WHERE id=?",encode(p),id);}if(!enabled||!p.isEmpty())db.update("DELETE FROM console_session WHERE account_id=?",id);}Long profileId=id==null?db.queryForObject("SELECT id FROM console_account WHERE user_name=?",Long.class,required(b,"userName")):id;if(db.update("UPDATE console_person_profile SET biography=?,skills=? WHERE account_id=?",biography,write(tags),profileId)==0)db.update("INSERT INTO console_person_profile(account_id,biography,skills) VALUES(?,?,?)",profileId,biography,write(tags));}
 @Transactional public void deletePerson(Map<String,Object>a,Long id){admin(a);if(db.queryForObject("SELECT COUNT(*) FROM console_order WHERE owner_account_id=? AND status<>'已完成'",Integer.class,id)>0)throw bad(409,"该人员还有未完成工单，请先处理工单");List<Map<String,Object>>u=db.queryForList("SELECT user_name FROM console_account WHERE id=? AND role='MAINTAINER'",id);if(u.isEmpty())throw bad(404,"维保人员不存在");db.update("DELETE FROM console_session WHERE account_id=?",id);db.update("DELETE FROM console_notice WHERE account_id=?",id);if(legacyAccounts)db.update("DELETE FROM `user` WHERE user_name=?",u.get(0).get("user_name"));db.update("DELETE FROM console_person_profile WHERE account_id=?",id);db.update("DELETE FROM console_account WHERE id=?",id);}
 // Renaming preserves existing account references; never re-number account IDs.
 void migrateOwnerColumn(){
  Integer legacy=db.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='console_order' AND column_name='owner_id'",Integer.class);
  if(legacy!=null&&legacy>0)db.execute("ALTER TABLE console_order CHANGE COLUMN owner_id owner_account_id BIGINT NULL");
 }
 private Map<String,Object> read(String raw){try{Map<String,Object> result=UnifiedDataSource.normalize(json.readValue(raw,new TypeReference<Map<String,Object>>(){}));if(result.containsKey("ownerId")){if(!result.containsKey("ownerAccountId"))result.put("ownerAccountId",result.get("ownerId"));result.remove("ownerId");}return result;}catch(Exception e){throw new IllegalStateException(e);}}
 private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
 public List<Map<String,Object>> orders(Map<String,Object>a){List<Map<String,Object>>out=new ArrayList<>();String sql="SELECT payload FROM console_order";List<Map<String,Object>>rows="ADMIN".equals(a.get("role"))?db.queryForList(sql+" ORDER BY created_at DESC"):db.queryForList(sql+" WHERE owner_account_id=? OR owner_account_id IS NULL ORDER BY created_at DESC",a.get("id"));for(Map<String,Object>r:rows){Map<String,Object>order=read((String)r.get("payload"));if(orderNumbers!=null)orderNumbers.assign(order);out.add(order);}return out;}
 public List<Map<String,Object>> history(Map<String,Object>a,String room,String cabinet,String device,String origin){admin(a);if(room==null||room.trim().isEmpty()||((cabinet==null||cabinet.trim().isEmpty())&&(device==null||device.trim().isEmpty())))throw bad(400,"请提供机房及机柜或设备的稳定ID");List<Map<String,Object>> out=new ArrayList<>();for(Map<String,Object>o:orders(a))if("已完成".equals(o.get("status"))&&room.equals(o.get("roomId"))&&(cabinet==null||cabinet.isEmpty()||cabinet.equals(o.get("cabinetId")))&&(device==null||device.isEmpty()||device.equals(o.get("deviceId")))&&(origin==null||origin.isEmpty()||UnifiedDataSource.origin(origin).equals(o.get("recordOrigin"))))out.add(o);return out;}
 private void notice(Object user,String title,String content,String order){db.update("INSERT INTO console_notice(account_id,title,content,order_id) VALUES(?,?,?,?)",user,title,content,order);}
 private void notifyRole(String role,String title,String content,String order){for(Map<String,Object>a:db.queryForList("SELECT id FROM console_account WHERE role=? AND enabled=TRUE",role))notice(a.get("id"),title,content,order);}
 @Transactional(isolation=Isolation.READ_COMMITTED) public Map<String,Object> createOrder(Map<String,Object>a,Map<String,Object>b){admin(a);UnifiedDataSource.normalize(b);String roomId=str(b,"roomId");if(roomId.isEmpty()){List<String> rooms=db.queryForList("SELECT CAST(id AS CHAR) FROM sp_room ORDER BY (record_origin='REAL') DESC,id LIMIT 1",String.class);if(rooms.isEmpty())throw bad(409,"尚未登记机房");roomId=rooms.get(0);b.put("roomId",roomId);}monitoring.lockRoom(roomId);String alarmId=str(b,"alarmEvent");if(!alarmId.isEmpty()&&!"manual".equals(alarmId)){Map<String,Object> linked=monitoring.records(roomId,"ALARM").stream().filter(v->alarmId.equals(v.get("id"))).findFirst().orElseThrow(()->bad(404,"关联告警不存在"));if("已恢复".equals(linked.get("status")))throw bad(409,"已恢复告警不能创建新工单");b.put("alarmSnapshot",new LinkedHashMap<>(linked));b.put("recordOrigin",linked.get("recordOrigin"));b.put("cabinetId",linked.get("cabinetId"));b.put("deviceId",linked.get("deviceId"));}String title=required(b,"title"),device=required(b,"device");String alarm=str(b,"alarmEvent");if(!alarm.isEmpty()&&!"manual".equals(alarm)){for(Map<String,Object>o:orders(a))if(roomId.equals(o.get("roomId"))&&alarm.equals(o.get("alarmEvent"))&&!"已完成".equals(o.get("status")))return o;}
  String id="WO-"+UUID.randomUUID().toString().substring(0,12).toUpperCase();Map<String,Object>o=object("id",id,"title",title,"device",device,"alarmEvent",alarm,"source",str(b,"source").isEmpty()?"告警转工单":str(b,"source"),"priority",str(b,"priority"),"note",str(b,"note"),"status","待受理","owner","未指派","ownerAccountId",null,"created",new Date().toInstant().toString(),"steps",new ArrayList<>(Arrays.asList("管理员创建工单")),"siteChecked",false,"verified",false,"recordOrigin",str(b,"recordOrigin"),"roomId",str(b,"roomId"),"cabinetId",str(b,"cabinetId"),"deviceId",str(b,"deviceId"),"updatedAt",new Date().toInstant().toString());if(b.get("alarmSnapshot")!=null)o.put("alarmSnapshot",b.get("alarmSnapshot"));if(orderNumbers!=null)orderNumbers.assign(o);db.update("INSERT INTO console_order(id,status,payload) VALUES(?,?,?)",id,"待受理",write(o));notifyRole("MAINTAINER","新工单待领取",title,id);return o;
 }
 @Transactional(isolation=Isolation.READ_COMMITTED) public Map<String,Object> action(Map<String,Object>a,String id,String action,Map<String,Object>b){lockOrderRoom(id);List<Map<String,Object>>rows=db.queryForList("SELECT * FROM console_order WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw bad(404,"工单不存在");Map<String,Object>row=rows.get(0),o=read((String)row.get("payload"));if(orderNumbers!=null)orderNumbers.assign(o);String status=(String)row.get("status");Object owner=row.get("owner_account_id");if(b.get("expectedState") instanceof Map){Map<?,?>expected=(Map<?,?>)b.get("expectedState");if(!Objects.equals(o.get("status"),expected.get("status"))||!Objects.equals(String.valueOf(o.get("ownerAccountId")),String.valueOf(expected.get("ownerAccountId")))||!Objects.equals(o.get("updatedAt"),expected.get("updatedAt")))throw bad(409,"工单状态或负责人已变化，请重新确认");}
  if("claim".equals(action)){if(!"MAINTAINER".equals(a.get("role")))throw bad(403,"请使用维保账号领取");if(!"待受理".equals(status)||owner!=null)throw bad(409,"工单已被领取，请刷新");owner=a.get("id");o.put("ownerAccountId",owner);o.put("owner",a.get("displayName"));status="已接单";}
  else if("assign".equals(action)){
   admin(a);if(!Arrays.asList("待受理","已接单","处理中").contains(status))throw bad(409,"当前状态不能指派");
   List<Map<String,Object>> people=db.queryForList("SELECT * FROM console_account WHERE id=? AND role='MAINTAINER' AND enabled=TRUE FOR UPDATE",required(b,"ownerAccountId"));
   if(people.isEmpty())throw bad(400,"请选择启用的维保人员");Map<String,Object>person=people.get(0);owner=person.get("id");o.put("ownerAccountId",owner);o.put("owner",person.get("display_name"));if("待受理".equals(status))status="已接单";
   notice(owner,"工单已指派给你",String.valueOf(o.get("title")),id);
  }
  else if("demo-retest".equals(action)){admin(a);if(!"待复测".equals(status)||!Boolean.TRUE.equals(o.get("siteChecked")))throw bad(409,"请先提交处理记录");status=inspectOrder(o,monitoring.demoRetest(o,required(b,"outcome")));}
  else if("retest".equals(action)){admin(a);if(!"待复测".equals(status))throw bad(409,"请先提交处理记录");status=inspectOrder(o);}
  else{if(owner==null||!owner.toString().equals(a.get("id").toString()))throw bad(403,"只能处理自己的工单");if("save".equals(action)&&"处理中".equals(status)){o.put("cause",str(b,"cause"));o.put("process",required(b,"process"));o.put("result",str(b,"result"));}else if("start".equals(action)&&"已接单".equals(status))status="处理中";else if("submit".equals(action)&&"处理中".equals(status)){o.put("cause",required(b,"cause"));o.put("process",required(b,"process"));o.put("result",required(b,"result"));o.put("siteChecked",true);o.put("verified",false);o.remove("retestEvidence");o.put("submittedAt",new Date().toInstant().toString());o.put("retestNote","小维正在等待提交后的有效监测数据，将自动复检");status="待复测";notifyRole("ADMIN","工单待复测",String.valueOf(o.get("title")),id);}else throw bad(409,"当前状态不允许该操作");}
  o.put("updatedAt",new Date().toInstant().toString());o.put("status",status);((List<Object>)o.get("steps")).add(new Date().toInstant()+" · "+a.get("displayName")+" · "+action+("assign".equals(action)?" → "+o.get("owner"):"submit".equals(action)?" · 原因："+o.get("cause")+" · 措施："+o.get("process")+" · 结果："+o.get("result"):""));db.update("UPDATE console_order SET owner_account_id=?,status=?,payload=? WHERE id=?",owner,status,write(o),id);return o;
 }
 private void lockOrderRoom(String id){
  List<String> payloads=db.queryForList("SELECT payload FROM console_order WHERE id=?",String.class,id);
  if(payloads.isEmpty())throw bad(404,"工单不存在");Object room=read(payloads.get(0)).get("roomId");
  if(room!=null)monitoring.lockRoom(room.toString());
 }
 private String inspectOrder(Map<String,Object>o){
  return inspectOrder(o,monitoring.retest(o));
 }
 private String inspectOrder(Map<String,Object>o,Map<String,Object>check){
  if(check.get("passed")!=null||!Objects.equals(o.get("retestNote"),check.get("note"))){
   if(!(o.get("retestChecks") instanceof List))o.put("retestChecks",new ArrayList<>());
   ((List<Object>)o.get("retestChecks")).add(check);
  }
  o.put("retestNote",check.get("note"));o.put("retestEvidence",check);
  String status="待复测";
  if(Boolean.TRUE.equals(check.get("passed"))){status="已完成";o.put("verified",true);o.put("archivedAt",new Date().toInstant().toString());for(Map<String,Object>alarm:monitoring.records(String.valueOf(o.get("roomId")),"ALARM"))if(Objects.equals(alarm.get("id"),o.get("alarmEvent"))){alarm.put("status","已恢复");alarm.put("recoveredAt",new Date().toInstant().toString());monitoring.saveRecord(String.valueOf(o.get("roomId")),"ALARM",String.valueOf(alarm.get("id")),alarm);}}
  else if(Boolean.FALSE.equals(check.get("passed")))status="处理中";
  if(!"待复测".equals(status)){String title=("已完成".equals(status)?"小维复检通过，工单已归档":"小维复检未通过，请继续处理");notifyRole("ADMIN",title,String.valueOf(o.get("title")),String.valueOf(o.get("id")));if(o.get("ownerAccountId")!=null)notice(o.get("ownerAccountId"),title,String.valueOf(o.get("retestNote")),String.valueOf(o.get("id")));}
  if("DEMO".equals(check.get("mode")))((List<Object>)o.get("steps")).add(check.get("checkedAt")+" · 小维演示复检 · "+check.get("note"));
  return status;
 }
 public List<String> pendingRetests(){return db.queryForList("SELECT id FROM console_order WHERE status='待复测'",String.class);}
 @Transactional(isolation=Isolation.READ_COMMITTED) public void automaticRetest(String id){
  lockOrderRoom(id);
  List<Map<String,Object>>rows=db.queryForList("SELECT * FROM console_order WHERE id=? FOR UPDATE",id);if(rows.isEmpty()||!"待复测".equals(rows.get(0).get("status")))return;
  Map<String,Object>o=read((String)rows.get(0).get("payload"));Object previous=o.get("retestNote");String status=inspectOrder(o);
  if("待复测".equals(status)&&Objects.equals(previous,o.get("retestNote")))return;
  o.put("status",status);o.put("updatedAt",new Date().toInstant().toString());((List<Object>)o.get("steps")).add(new Date().toInstant()+" · 小维自动复检 · "+o.get("retestNote"));db.update("UPDATE console_order SET status=?,payload=? WHERE id=?",status,write(o),id);
 }
 public List<Map<String,Object>> notices(Map<String,Object>a){return db.queryForList("SELECT id,title,content,order_id AS orderId,is_read AS isRead,created_at AS createdAt FROM console_notice WHERE account_id=? ORDER BY id DESC",a.get("id"));}
 public void readNotice(Map<String,Object>a,Long id){db.update("UPDATE console_notice SET is_read=TRUE WHERE id=? AND account_id=?",id,a.get("id"));}
 public void deleteRead(Map<String,Object>a){db.update("DELETE FROM console_notice WHERE account_id=? AND is_read=TRUE",a.get("id"));}
}



