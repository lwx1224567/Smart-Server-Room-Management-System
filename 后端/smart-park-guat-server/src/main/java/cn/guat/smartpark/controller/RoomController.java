package cn.guat.smartpark.controller;
import cn.guat.smartpark.service.InventoryService;
import cn.guat.smartpark.common.R;
import org.springframework.web.bind.annotation.*;

@CrossOrigin @RestController @RequestMapping("/api/v1/rooms")
public class RoomController {
 private final InventoryService service;
 public RoomController(InventoryService service){this.service=service;}
 private R<Object> ok(Object value){return R.success(value);}
 @GetMapping public R<Object> rooms(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="REAL") String recordOrigin,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(defaultValue="id,asc") String sort){return ok(service.rooms(q,recordOrigin,page,pageSize,sort));}
 @GetMapping("/{roomId}") public R<Object> room(@PathVariable String roomId){return ok(service.room(roomId));}
 @GetMapping("/{roomId}/scene") public R<Object> scene(@PathVariable String roomId,@RequestParam(defaultValue="REAL") String recordOrigin){return ok(service.scene(roomId,recordOrigin));}
 @GetMapping("/{roomId}/cabinets") public R<Object> cabinets(@PathVariable String roomId,@RequestParam(required=false) String zoneId,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(defaultValue="id,asc") String sort){return ok(service.cabinets(roomId,zoneId,q,page,pageSize,sort));}
}
