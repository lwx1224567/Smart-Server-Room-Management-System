package cn.guat.smartpark.dto.inventory;
import java.util.*;
/** Named JSON projections. Entity IDs are converted explicitly, never globally. */
public final class InventoryDtos {
 private InventoryDtos() {}
 public static Map<String,Object> object(Object... pairs) {
   Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;
 }
}
