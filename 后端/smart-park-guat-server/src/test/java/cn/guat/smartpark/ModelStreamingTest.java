package cn.guat.smartpark;

import cn.guat.smartpark.service.*;
import cn.guat.smartpark.exception.PlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModelStreamingTest {
 HttpServer server;ExecutorService pool;IntegrationService service;ObjectMapper json=new ObjectMapper();
 volatile String first="",tail="";volatile Map<String,Object>request;volatile String authorization;
 CountDownLatch received,release;
 @BeforeEach void setup()throws Exception{
  received=new CountDownLatch(1);release=new CountDownLatch(1);pool=Executors.newCachedThreadPool();
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(pool);
  server.createContext("/chat",exchange->{try{
   authorization=exchange.getRequestHeaders().getFirst("Authorization");request=json.readValue(exchange.getRequestBody(),Map.class);
   exchange.getResponseHeaders().set("Content-Type","text/event-stream;charset=UTF-8");exchange.sendResponseHeaders(200,0);
   exchange.getResponseBody().write(first.getBytes(StandardCharsets.UTF_8));exchange.getResponseBody().flush();
   if(!release.await(5,TimeUnit.SECONDS))return;
   exchange.getResponseBody().write(tail.getBytes(StandardCharsets.UTF_8));exchange.getResponseBody().flush();
  }catch(InterruptedException e){Thread.currentThread().interrupt();}finally{exchange.close();}});server.start();
  service=new IntegrationService(mock(JdbcTemplate.class),mock(MonitoringService.class),json,new MockEnvironment().withProperty("integration.model.url","http://127.0.0.1:"+server.getAddress().getPort()+"/chat").withProperty("integration.model.name","deepseek-flash").withProperty("integration.model.key","stream-test-key"));
 }
 @AfterEach void stop(){release.countDown();server.stop(0);pool.shutdownNow();}
 String chunk(Map<String,Object>delta,String finish)throws Exception{return "data: "+json.writeValueAsString(object("choices",Arrays.asList(object("index",0,"delta",delta,"finish_reason",finish))))+"\r\n\r\n";}
 @Test void firstTextArrivesBeforeUpstreamFinishes()throws Exception{
  first=": keep-alive\r\n\r\n"+chunk(object("content","第一段中文。"),null);
  tail=chunk(object("content","第二段。"),null)+chunk(object(),"stop")+"data: [DONE]\r\n\r\n";
  List<String>parts=new CopyOnWriteArrayList<>();Future<Map<String,Object>>future=pool.submit(()->service.modelToolsStream(Collections.emptyList(),Collections.emptyList(),text->{parts.add(text);received.countDown();}));
  assertTrue(received.await(3,TimeUnit.SECONDS));assertFalse(future.isDone());assertEquals(Arrays.asList("第一段中文。"),parts);
  release.countDown();assertEquals("第一段中文。第二段。",future.get(3,TimeUnit.SECONDS).get("content"));
  assertEquals(true,request.get("stream"));assertEquals("Bearer stream-test-key",authorization);
 }
 @Test void toolArgumentsAreAssembledAndReasoningIsNotEmitted()throws Exception{
  first=chunk(object("reasoning_content","不展示的推理","tool_calls",Arrays.asList(object("index",0,"id","call_1","function",object("name","get_","arguments","{")))),null);
  tail=chunk(object("tool_calls",Arrays.asList(object("index",0,"function",object("name","orders","arguments","}")))),"tool_calls")+"data: [DONE]\n\n";release.countDown();
  List<String>parts=new ArrayList<>();Map<String,Object>result=service.modelToolsStream(Collections.emptyList(),Collections.emptyList(),parts::add);
  Map<?,?>call=(Map<?,?>)((List<?>)result.get("tool_calls")).get(0);Map<?,?>fn=(Map<?,?>)call.get("function");
  assertEquals("get_orders",fn.get("name"));assertEquals("{}",fn.get("arguments"));assertEquals("call_1",call.get("id"));assertTrue(parts.isEmpty());
 }
 @Test void incompleteOrTruncatedStreamIsNotSuccessful()throws Exception{
  first=chunk(object("content","部分回答"),null);tail="";release.countDown();
  assertThrows(PlatformException.class,()->service.modelToolsStream(Collections.emptyList(),Collections.emptyList(),text->{}));
  first=chunk(object("content","被截断"),"length");tail="data: [DONE]\n\n";
  assertThrows(PlatformException.class,()->service.modelToolsStream(Collections.emptyList(),Collections.emptyList(),text->{}));
 }
}
