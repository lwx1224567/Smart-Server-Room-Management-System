package cn.guat.smartpark.controller;
import cn.guat.smartpark.common.R;
import cn.guat.smartpark.service.ConsoleService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/weather")
public class WeatherController {
 private final ConsoleService service;
 public WeatherController(ConsoleService service){this.service=service;}
 private Map<String,Object> me(String header){return service.identity(header);}
 @Value("${console.weather.latitude:31.2304}") private double latitude;
 @Value("${console.weather.longitude:121.4737}") private double longitude;
 @Value("${console.weather.city:上海}") private String city;
 private Map<String,Object> weatherCache; private long weatherAt;
 @GetMapping public synchronized R weather(@RequestHeader("Authorization")String h){me(h);if(weatherCache!=null&&System.currentTimeMillis()-weatherAt<600000)return R.success(weatherCache);
  try{SimpleClientHttpRequestFactory f=new SimpleClientHttpRequestFactory();f.setConnectTimeout(5000);f.setReadTimeout(5000);Map response=new RestTemplate(f).getForObject("https://api.open-meteo.com/v1/forecast?latitude="+latitude+"&longitude="+longitude+"&current=temperature_2m,weather_code&timezone=Asia%2FShanghai",Map.class);Map current=(Map)response.get("current");weatherCache=object("city",city,"temperature",current.get("temperature_2m"),"code",current.get("weather_code"),"updatedAt",current.get("time"),"available",true,"provider","Open-Meteo");weatherAt=System.currentTimeMillis();return R.success(weatherCache);}catch(Exception e){return R.success(object("city",city,"latitude",latitude,"longitude",longitude,"available",false,"message","天气暂不可用，请稍后重试"));}
 }
}
