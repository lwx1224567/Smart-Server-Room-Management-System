package cn.guat.smartpark.controller;
import cn.guat.smartpark.common.R;
import cn.guat.smartpark.service.ConsoleService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/users")
public class UserController {
 private final ConsoleService service;
 public UserController(ConsoleService service){this.service=service;}
 private Map<String,Object> me(String header){return service.identity(header);}
 @GetMapping public R people(@RequestHeader("Authorization")String h){return R.success(service.people(me(h)));}
 @PostMapping public R add(@RequestHeader("Authorization")String h,@RequestBody Map<String,Object>b){service.savePerson(me(h),b,null);return R.success();}
 @PutMapping("/{id}") public R edit(@RequestHeader("Authorization")String h,@PathVariable Long id,@RequestBody Map<String,Object>b){service.savePerson(me(h),b,id);return R.success();}
 @DeleteMapping("/{id}") public R delete(@RequestHeader("Authorization")String h,@PathVariable Long id){service.deletePerson(me(h),id);return R.success();}
}
