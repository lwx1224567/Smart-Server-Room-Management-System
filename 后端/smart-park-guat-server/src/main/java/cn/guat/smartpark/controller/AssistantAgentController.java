package cn.guat.smartpark.controller;

import cn.guat.smartpark.common.R;
import cn.guat.smartpark.service.AssistantAgentService;
import cn.guat.smartpark.service.ConsoleService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;

@RestController @CrossOrigin @RequestMapping("/api/v1/assistant/rooms/{room}")
public class AssistantAgentController {
 private final AssistantAgentService agent;private final ConsoleService console;private final ObjectMapper json;
 public AssistantAgentController(AssistantAgentService agent,ConsoleService console,ObjectMapper json){this.agent=agent;this.console=console;this.json=json;}
 private Map<String,Object> actor(String h,String page){
  if(!page.matches("[A-Za-z0-9_-]{1,64}"))throw new cn.guat.smartpark.exception.PlatformException(400,"ASSISTANT_ERROR","页面会话标识无效");
  Map<String,Object>a=new java.util.LinkedHashMap<>(console.identity(h));a.put("assistantPageId",page);return a;
 }
 @PostMapping("/session/end") public R end(@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room){agent.end(actor(h,page),room);return R.success(object());}
 @GetMapping("/session") public R current(@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room){return R.success(agent.current(actor(h,page),room));}
 @PostMapping("/confirm") public R confirm(@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room,@RequestBody Map<String,Object>b){return R.success(agent.confirm(actor(h,page),room,b));}
 @PostMapping("/confirmation/status") public R confirmationStatus(@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room,@RequestBody Map<String,Object>b){return R.success(agent.confirmationStatus(actor(h,page),room,b));}
 @PostMapping("/pending/dismiss") public R dismiss(@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room,@RequestBody Map<String,Object>b){return R.success(agent.dismissPending(actor(h,page),room,b));}
 @PostMapping("/chat") public Object chat(@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room,@RequestBody Map<String,Object>b,HttpServletResponse response){return reply(h,page,room,b,response,false);}
 @PostMapping("/scene-result") public Object scene(@RequestHeader("Authorization")String h,@RequestHeader(value="X-Assistant-Page",defaultValue="default")String page,@PathVariable String room,@RequestBody Map<String,Object>b,HttpServletResponse response){return reply(h,page,room,b,response,true);}
 private Object reply(String h,String page,String room,Map<String,Object>b,HttpServletResponse response,boolean scene){
  Map<String,Object>actor=actor(h,page);console.admin(actor);
  if(!Boolean.TRUE.equals(b.get("stream")))return R.success(scene?agent.sceneResult(actor,room,b):agent.chat(actor,room,b));
  response.setContentType("text/event-stream");response.setCharacterEncoding("UTF-8");response.setHeader("Cache-Control","no-cache, no-transform");response.setHeader("X-Accel-Buffering","no");
  try{Map<String,Object>session=agent.stream(actor,room,b,scene,event->send(response,event.get("type").toString(),event.get("data")));send(response,"done",session);}
  catch(UncheckedIOException disconnected){/* Browser disconnected: stop upstream reading, never replay a write. */}
  catch(Exception failure){try{send(response,"error",object("message","对话流中断，请查询最新工单状态后重试。"));}catch(UncheckedIOException ignored){}}
  return null;
 }
 private void send(HttpServletResponse response,String type,Object data){try{byte[]bytes=("event: "+type+"\ndata: "+json.writeValueAsString(data)+"\n\n").getBytes(StandardCharsets.UTF_8);response.getOutputStream().write(bytes);response.flushBuffer();}catch(IOException e){throw new UncheckedIOException(e);}}
}
