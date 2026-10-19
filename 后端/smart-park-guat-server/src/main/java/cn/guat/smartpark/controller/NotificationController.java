package cn.guat.smartpark.controller;
import cn.guat.smartpark.common.R;
import cn.guat.smartpark.service.ConsoleService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/notifications")
public class NotificationController {
 private final ConsoleService service;
 public NotificationController(ConsoleService service){this.service=service;}
 private Map<String,Object> me(String header){return service.identity(header);}
 @GetMapping public R notices(@RequestHeader("Authorization")String h){return R.success(service.notices(me(h)));}
 @PutMapping("/{id}/read") public R read(@RequestHeader("Authorization")String h,@PathVariable Long id){service.readNotice(me(h),id);return R.success();}
 @DeleteMapping("/read") public R deleteRead(@RequestHeader("Authorization")String h){service.deleteRead(me(h));return R.success();}
}
