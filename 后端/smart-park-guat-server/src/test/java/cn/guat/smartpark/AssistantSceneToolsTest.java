package cn.guat.smartpark;
import cn.guat.smartpark.service.*;
import cn.guat.smartpark.exception.PlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import java.util.*;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Pure model/scene protocol simulation, no Spring lifecycle or database connection. */
class AssistantSceneToolsTest {
 JdbcTemplate db=mock(JdbcTemplate.class);IntegrationService model=mock(IntegrationService.class);ConsoleService console=mock(ConsoleService.class);
 AssistantAgentService agent=new AssistantAgentService(db,new ObjectMapper(),model,console,mock(MonitoringService.class));
 Map<String,Object>actor=object("id",1,"role","ADMIN");
 Map<String,Object>dispatch(String args){
  when(db.queryForList(anyString(),eq("1"),eq("A-01"))).thenReturn(Arrays.asList(object("id","10","sceneModelKey","A-01")));
  when(model.modelTools(anyList(),anyList())).thenReturn(object("role","assistant","tool_calls",Arrays.asList(object("id","scene-call","type","function","function",object("name","scene_action","arguments",args))))).thenReturn(object("role","assistant","content","结束"));
  return agent.chat(actor,"1",object("text","打开柜门","requestId",UUID.randomUUID().toString()));
 }
 Map<String,Object>receipt(Map<?,?>action,String status){return object("token",action.get("token"),"type",action.get("type"),"cabinetSceneModelKey",action.get("cabinetSceneModelKey"),"open",action.get("open"),"status",status,"arrived",false,"result","模拟动画回执");}
 @Test void doorRequiresMatchingReceiptAndRejectsReplay(){
  Map<?,?>action=(Map<?,?>)dispatch("{\"action\":\"door\",\"cabinetKey\":\"A-01\",\"open\":true}").get("clientAction");assertEquals(true,action.get("open"));
  Map<String,Object>r=receipt(action,"SUCCEEDED");r.put("cabinetSceneModelKey","B-01");assertThrows(PlatformException.class,()->agent.sceneResult(actor,"1",r));
  r.put("cabinetSceneModelKey","A-01");r.put("open",false);assertThrows(PlatformException.class,()->agent.sceneResult(actor,"1",r));
  r.put("open",true);r.put("token","wrong");assertThrows(PlatformException.class,()->agent.sceneResult(actor,"1",r));r.put("token",action.get("token"));
  agent.sceneResult(actor,"1",r);assertThrows(PlatformException.class,()->agent.sceneResult(actor,"1",r));verify(console,never()).action(anyMap(),anyString(),anyString(),anyMap());
 }
 @Test void failedAndCancelledReceiptsStopContinuation(){
  for(String status:Arrays.asList("FAILED","CANCELLED")){
   Map<?,?>action=(Map<?,?>)dispatch("{\"action\":\"door\",\"cabinetKey\":\"A-01\",\"open\":false}").get("clientAction");
   clearInvocations(model);Map<String,Object>r=receipt(action,status);agent.sceneResult(actor,"1",r);verifyNoInteractions(model);assertNull(agent.current(actor,"1").get("clientAction"));
  }
 }
 @Test void invalidToolsNeverDispatch(){
  for(String args:Arrays.asList("{\"action\":\"door\",\"cabinetKey\":\"A-01\",\"open\":\"true\"}","{\"action\":\"sql\"}","{\"action\":\"resume\",\"open\":true}","{\"action\":\"door\",\"cabinetKey\":\"B-01\",\"open\":true}","{\"action\":\"resume\",\"extra\":1}"))assertNull(dispatch(args).get("clientAction"));
 }
}
