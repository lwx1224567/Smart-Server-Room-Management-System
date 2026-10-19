package cn.guat.smartpark.controller;

import cn.guat.smartpark.common.R;
import cn.guat.smartpark.dto.WorkOrderRequests.*;
import cn.guat.smartpark.service.ConsoleService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/work-orders")
public class WorkOrderController {
    private final ConsoleService service;
    public WorkOrderController(ConsoleService service){this.service=service;}
    @GetMapping public R orders(@RequestHeader("Authorization")String auth){return R.success(service.orders(service.identity(auth)));}
    @GetMapping("/history") public R history(@RequestHeader("Authorization")String auth,@RequestParam String roomId,@RequestParam(required=false)String cabinetId,@RequestParam(required=false)String deviceId,@RequestParam(required=false)String recordOrigin){return R.success(service.history(service.identity(auth),roomId,cabinetId,deviceId,recordOrigin));}
    @PostMapping public R create(@RequestHeader("Authorization")String auth,@RequestBody Map<String,Object> body){return R.success(service.createOrder(service.identity(auth),body));}
    @PostMapping("/{id}/claim") public R claim(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody Action body){return action(auth,id,"claim",body);}
    @PostMapping("/{id}/assign") public R assign(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody Assignment body){return action(auth,id,"assign",body);}
    @PostMapping("/{id}/start") public R start(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody Action body){return action(auth,id,"start",body);}
    @PatchMapping("/{id}/processing-record") public R save(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody ProcessingRecord body){return action(auth,id,"save",body);}
    @PostMapping("/{id}/submit") public R submit(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody Submission body){return action(auth,id,"submit",body);}
    @PostMapping("/{id}/retest") public R retest(@RequestHeader("Authorization")String auth,@PathVariable String id,@RequestBody Action body){return action(auth,id,"retest",body);}
    private R action(String auth,String id,String action,Action body){return R.success(service.action(service.identity(auth),id,action,body.toMap()));}
}
