package cn.guat.smartpark.controller;
import cn.guat.smartpark.common.R;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import cn.guat.smartpark.service.InventoryMaintenanceService;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;
@RestController
@CrossOrigin
@RequestMapping("/api/v1/admin")
public class AdminInventoryController {
 private final InventoryMaintenanceService inventory;
 public AdminInventoryController(InventoryMaintenanceService inventory){this.inventory=inventory;}
 @GetMapping("/{kind}") public R inventory(@PathVariable String kind,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="100")int pageSize){
  if(page<1||pageSize<1||pageSize>100)throw new cn.guat.smartpark.exception.PlatformException(400,"INVALID_ARGUMENT","分页参数无效");
  List<Map<String,Object>> rows=inventory.list(kind);int start=(int)Math.min((long)(page-1)*pageSize,rows.size());
  return R.success(object("items",view(rows.subList(start,Math.min(start+pageSize,rows.size()))),"total",rows.size(),"page",page,"pageSize",pageSize));
 }
 @GetMapping("/{kind}/{id}") public R item(@PathVariable String kind,@PathVariable String id){return R.success(view(inventory.get(kind,id)));}
 @PostMapping("/{kind}") public R create(@PathVariable String kind,@RequestBody Map<String,Object>b){return R.success(view(inventory.save(kind,null,b)));}
 @PutMapping("/{kind}/{id}") public R edit(@PathVariable String kind,@PathVariable String id,@RequestBody Map<String,Object>b){return R.success(view(inventory.save(kind,id,b)));}
 @DeleteMapping("/{kind}/{id}") public R delete(@PathVariable String kind,@PathVariable String id){inventory.delete(kind,id);return R.success();}
 private Object view(Object value){
  if(value instanceof List){List<Object> out=new ArrayList<>();for(Object item:(List<?>)value)out.add(view(item));return out;}
  Map<String,Object> out=new LinkedHashMap<>();for(Map.Entry<String,Object> entry:((Map<String,Object>)value).entrySet()){String[] parts=entry.getKey().split("_");StringBuilder key=new StringBuilder(parts[0]);for(int i=1;i<parts.length;i++)key.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));out.put(key.toString(),entry.getValue());}return out;
 }
}
