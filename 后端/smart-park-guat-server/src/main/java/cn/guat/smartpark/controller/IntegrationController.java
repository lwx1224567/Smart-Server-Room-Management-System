package cn.guat.smartpark.controller;
import cn.guat.smartpark.common.R;
import cn.guat.smartpark.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @CrossOrigin @RequestMapping("/api/v1")
public class IntegrationController {
 private final IntegrationService service;private final ConsoleService console;
 @org.springframework.beans.factory.annotation.Autowired private DemoMonitoringService demo;
 public IntegrationController(IntegrationService service,ConsoleService console){this.service=service;this.console=console;}
 @GetMapping("/integrations") public R capabilities(){return R.success(service.capabilities());}
 @GetMapping("/integrations/collector") public R collectorStatus(){return R.success(service.collectorStatus());}
 @PostMapping("/integrations/collector/pull") public R collect(){return R.success(service.collect());}
 @PostMapping("/rooms/{room}/ingest") public R ingest(@PathVariable String room,@RequestBody Map<String,Object>b){return R.success(service.ingest(room,b));}
 @PostMapping("/rooms/{room}/demo-monitoring/temperature") public R demoTemperature(@PathVariable String room,@RequestHeader("Authorization")String auth,@RequestBody Map<String,Object>b){return R.success(demo.submit(room,b,console.identity(auth)));}
 @GetMapping("/rooms/{room}/readings/history") public R history(@PathVariable String room,@RequestParam String entityType,@RequestParam String entityId,@RequestParam String metric,@RequestParam String from,@RequestParam String to){return R.success(service.history(room,entityType,entityId,metric,from,to));}
 @GetMapping("/rooms/{room}/alarm-rules") public R rules(@PathVariable String room){return R.success(service.rules(room));}
 @PutMapping("/rooms/{room}/alarm-rules/{id}") public R rule(@PathVariable String room,@PathVariable String id,@RequestBody Map<String,Object>b){return R.success(service.saveRule(room,id,b));}
 @DeleteMapping("/rooms/{room}/alarm-rules/{id}") public R deleteRule(@PathVariable String room,@PathVariable String id){service.deleteRule(room,id);return R.success();}
 @PostMapping("/rooms/{room}/alarms/evaluate") public R evaluate(@PathVariable String room,@RequestParam(defaultValue="REAL")String recordOrigin){return R.success(service.evaluate(room,recordOrigin));}
 @PutMapping("/rooms/{room}/cameras/{id}/video") public R configureVideo(@PathVariable String room,@PathVariable String id,@RequestBody Map<String,Object>b){return R.success(service.videoConfig(room,id,b));}
 @GetMapping("/rooms/{room}/cameras/{id}/video") public R video(@PathVariable String room,@PathVariable String id){return R.success(service.video(room,id));}
 @PostMapping("/rooms/{room}/cabinets/{id}/commands") public R command(@PathVariable String room,@PathVariable String id,@RequestHeader("Authorization")String auth,@RequestBody Map<String,Object>b){return R.success(service.command(room,id,b,console.identity(auth)));}
 @GetMapping("/rooms/{room}/cabinets/{id}/commands") public R commands(@PathVariable String room,@PathVariable String id){return R.success(service.commands(room,id));}
}
