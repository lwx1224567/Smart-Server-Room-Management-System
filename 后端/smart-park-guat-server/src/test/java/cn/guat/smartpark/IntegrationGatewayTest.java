package cn.guat.smartpark;
import cn.guat.smartpark.service.*;
import cn.guat.smartpark.exception.PlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.InetSocketAddress;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class IntegrationGatewayTest {
 HttpServer server;ObjectMapper json=new ObjectMapper();JdbcTemplate db;MonitoringService monitor;IntegrationService service;
 String response,auth;Map<String,Object>request;
 @BeforeEach void setup()throws Exception {
  db=mock(JdbcTemplate.class);monitor=mock(MonitoringService.class);
  when(db.queryForList(anyString(),any(Object[].class))).thenReturn(Arrays.asList(object("id",1,"sceneModelKey","A-01")));
  when(db.queryForList(anyString(),eq("1"),eq("1"))).thenReturn(Arrays.asList(object("id",1)));
  when(db.queryForList(anyString(),eq("1"))).thenReturn(Arrays.asList(object("id",1,"sceneModelKey","A-01")));
  when(monitor.records(anyString(),anyString())).thenReturn(Collections.emptyList());
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/gateway",exchange->{try{auth=exchange.getRequestHeaders().getFirst("Authorization");request=json.readValue(exchange.getRequestBody(),Map.class);String result=response;if(result==null)result=json.writeValueAsString(object("success",true,"commandId",request.get("id"),"actualValue",request.get("value")));byte[]bytes=result.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);}finally{exchange.close();}});server.start();
  String endpoint="http://127.0.0.1:"+server.getAddress().getPort()+"/gateway";
  service=new IntegrationService(db,monitor,json,new MockEnvironment().withProperty("integration.control.url",endpoint).withProperty("integration.control.key","test-secret").withProperty("integration.model.url",endpoint).withProperty("integration.model.name","test-model").withProperty("integration.model.key","test-model-secret").withProperty("integration.video.url",endpoint));
 }
 @AfterEach void stop(){server.stop(0);}
 @Test void nativeToolCallsPreserveProtocolAndRejectInvalidReply()throws Exception{
  response=json.writeValueAsString(object("choices",Arrays.asList(object("message",object("role","assistant","content",null,"reasoning_content","tool plan","tool_calls",Arrays.asList(object("id","call_1","type","function","function",object("name","get_orders","arguments","{}"))))))));
  Map<String,Object>result=service.modelTools(Arrays.asList(object("role","user","content","查询工单")),Arrays.asList(object("type","function","function",object("name","get_orders"))));
  assertNotNull(result.get("tool_calls"));assertEquals("tool plan",result.get("reasoning_content"));assertNotNull(request.get("tools"));assertFalse(request.containsKey("response_format"));
  response="{\"choices\":[]}";assertThrows(PlatformException.class,()->service.modelTools(Collections.emptyList(),Collections.emptyList()));
 }
 @Test void toolGatewayRequiresFreshValidUpstreamResponse()throws Exception{
  response=json.writeValueAsString(object("choices",Arrays.asList(object("message",object("content","我是其他模型")))));
  Map<String,Object>result=service.modelTools(Arrays.asList(object("role","user","content","你是什么模型")),Collections.emptyList());
  assertEquals("我是其他模型",result.get("content"));assertEquals("test-model",request.get("model"));assertEquals("Bearer test-model-secret",auth);
  verifyNoInteractions(db,monitor);
  for(String invalid:Arrays.asList("{\"choices\":[]}","{\"choices\":[{\"message\":{\"content\":\" \"}}]}")){
   response=invalid;assertEquals(502,assertThrows(PlatformException.class,()->service.modelTools(Collections.emptyList(),Collections.emptyList())).status);
  }
  IntegrationService missing=new IntegrationService(db,monitor,json,new MockEnvironment());
  assertEquals(503,assertThrows(PlatformException.class,()->missing.modelTools(Collections.emptyList(),Collections.emptyList())).status);
 }
 @Test void hardwareSuccessPersistsOnlyConfirmedState(){Map<String,Object>result=service.command("1","1",object("key","power","value",false),object("id",1,"displayName","测试管理员"));assertEquals("SUCCEEDED",result.get("status"));assertEquals("Bearer test-secret",auth);verify(monitor).control(eq("1"),eq("1"),eq(object("key","power","value",false)),anyMap());verify(monitor).saveRecord(eq("1"),eq("COMMAND"),anyString(),anyMap());}
 @Test void invalidGatewayReplyDoesNotChangeState(){response="{\"success\":true,\"commandId\":\"different\",\"actualValue\":false}";assertEquals("FAILED",service.command("1","1",object("key","power","value",false),object("id",1)).get("status"));verify(monitor,never()).control(anyString(),anyString(),anyMap(),anyMap());}
 @Test void toolGatewayUsesOnlyServerSuppliedMessagesWithoutDatabaseAccess()throws Exception{
  response=json.writeValueAsString(object("choices",Arrays.asList(object("message",object("content","当前无活动告警")))));
  List<Map<String,Object>>messages=Arrays.asList(object("role","system","content","只使用服务端工具结果"),object("role","user","content","查看告警"));
  assertEquals("当前无活动告警",service.modelTools(messages,Collections.emptyList()).get("content"));
  assertEquals(messages,request.get("messages"));assertEquals("Bearer test-model-secret",auth);verifyNoInteractions(db,monitor);
  response="{\"choices\":[]}";assertEquals(502,assertThrows(PlatformException.class,()->service.modelTools(messages,Collections.emptyList())).status);
 }
 @Test void videoGatewayResolvesFreshPlaybackUrl(){when(monitor.records("1","FACILITY")).thenReturn(Arrays.asList(object("id","cam","type","CAMERA")));when(monitor.records("1","VIDEO")).thenReturn(Arrays.asList(object("id","cam","mode","GATEWAY","channelId","channel-1","protocol","MP4")));response="{\"url\":\"https://video.example/live.mp4\",\"expiresAt\":\"2026-10-10T04:00:00Z\"}";assertEquals("https://video.example/live.mp4",service.video("1","cam").get("url"));assertEquals("channel-1",request.get("channelId"));}
}
