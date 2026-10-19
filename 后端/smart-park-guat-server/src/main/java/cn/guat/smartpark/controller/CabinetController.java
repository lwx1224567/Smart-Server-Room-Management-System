package cn.guat.smartpark.controller;
import cn.guat.smartpark.service.InventoryService;
import cn.guat.smartpark.common.R;
import org.springframework.web.bind.annotation.*;

@CrossOrigin @RestController @RequestMapping("/api/v1/cabinets")
public class CabinetController {
 private final InventoryService service;
 public CabinetController(InventoryService service){this.service=service;}
 private R<Object> ok(Object value){return R.success(value);}
 @GetMapping("/{cabinetId}") public R<Object> cabinet(@PathVariable String cabinetId){return ok(service.cabinet(cabinetId));}
 @GetMapping("/{cabinetId}/devices") public R<Object> devices(@PathVariable String cabinetId,@RequestParam(defaultValue="") String q,@RequestParam(required=false) String type,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(defaultValue="id,asc") String sort){return ok(service.devices(cabinetId,q,type,page,pageSize,sort));}
}
