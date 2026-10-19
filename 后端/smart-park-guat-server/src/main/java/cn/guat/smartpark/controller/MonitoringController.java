package cn.guat.smartpark.controller;
import cn.guat.smartpark.common.R;
import cn.guat.smartpark.service.MonitoringService;
import cn.guat.smartpark.service.ConsoleService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController @CrossOrigin @RequestMapping("/api/v1/rooms/{roomId}")
public class MonitoringController {
 private final MonitoringService service;
 private final ConsoleService console;
 public MonitoringController(MonitoringService service,ConsoleService console){this.service=service;this.console=console;}
 @PutMapping("/cabinets/{id}/controls") public R control(@PathVariable String roomId,@PathVariable String id,@RequestHeader("Authorization")String auth,@RequestBody Map<String,Object> body){return R.success(service.control(roomId,id,body,console.identity(auth)));}
 @GetMapping("/monitoring") public R snapshot(@PathVariable String roomId){return R.success(service.snapshot(roomId));}
 @GetMapping("/readings") public R readings(@PathVariable String roomId){return R.success(service.readings(roomId));}
 @GetMapping("/alarms") public R alarms(@PathVariable String roomId){return R.success(service.records(roomId,"ALARM"));}
 @GetMapping("/facilities") public R facilities(@PathVariable String roomId){return R.success(service.records(roomId,"FACILITY"));}
 @GetMapping("/network/topology") public R topology(@PathVariable String roomId){return R.success(cn.guat.smartpark.dto.inventory.InventoryDtos.object("roomId",roomId,"nodes",service.records(roomId,"NODE"),"links",service.records(roomId,"LINK")));}
 @GetMapping("/operation-logs") public R logs(@PathVariable String roomId){return R.success(service.records(roomId,"LOG"));}
 @GetMapping("/energy") public R energy(@PathVariable String roomId,@RequestParam(defaultValue="today")String period){return R.success(service.energy(roomId,period));}
 @PutMapping("/alarms/{id}/acknowledge") public R acknowledge(@PathVariable String roomId,@PathVariable String id){return R.success(service.acknowledge(roomId,id));}
}
