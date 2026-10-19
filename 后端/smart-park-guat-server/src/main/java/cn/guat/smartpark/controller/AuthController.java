package cn.guat.smartpark.controller;
import cn.guat.smartpark.common.R;
import cn.guat.smartpark.service.ConsoleService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/auth")
public class AuthController {
 private final ConsoleService service;
 public AuthController(ConsoleService service){this.service=service;}
 private Map<String,Object> me(String header){return service.identity(header);}
 @PostMapping("/login") public R login(@RequestBody Map<String,Object>b){return R.success(service.login(b));}
 @GetMapping("/me") public R current(@RequestHeader(value="Authorization",required=false)String h){return R.success(me(h));}
 @PostMapping("/logout") public R logout(@RequestHeader(value="Authorization",required=false)String h){service.logout(h);return R.success();}
 @PutMapping("/password") public R password(@RequestHeader("Authorization")String h,@RequestBody Map<String,Object>b){service.changePassword(me(h),b);return R.success();}
}
