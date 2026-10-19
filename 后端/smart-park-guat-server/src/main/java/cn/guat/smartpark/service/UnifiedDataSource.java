package cn.guat.smartpark.service;

import java.util.*;

/** DEMO is a legacy input alias. REAL is the single business data classification. */
public final class UnifiedDataSource {
 private UnifiedDataSource(){}
 public static String origin(Object value){return "DEMO".equals(value)||"REAL".equals(value)||"MANUAL".equals(value)?"REAL":String.valueOf(value);}
 public static <T> T normalize(T value){
  if(value instanceof Map){Map<Object,Object>m=(Map<Object,Object>)value;for(Map.Entry<Object,Object>e:m.entrySet()){
   if(Arrays.asList("recordOrigin","record_origin","mode").contains(e.getKey())&&("DEMO".equals(e.getValue())||"MANUAL".equals(e.getValue())))e.setValue("REAL");
   else if(Arrays.asList("retestNote","note").contains(e.getKey())&&e.getValue() instanceof String)e.setValue(note((String)e.getValue()));
   else normalize(e.getValue());
  }}else if(value instanceof List)for(Object item:(List<?>)value)normalize(item);
  return value;
 }
 public static String note(String value){return value.replace("DEMO 数据库复检","数据库复检").replace("（非真实采集验收）","").replace("有效 DEMO 温度数据","有效温度数据").replace("提交后的 DEMO 采集数据","提交后的有效监测数据").replace("提交后的真实采集数据","提交后的有效监测数据");}
}
