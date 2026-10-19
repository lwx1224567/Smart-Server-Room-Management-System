package cn.guat.smartpark;
import cn.guat.smartpark.mapper.InventoryMapper;
import cn.guat.smartpark.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Transactional(readOnly=true)
class InventoryMapperIntegrationTest {
 @Autowired InventoryMapper mapper;
 @Test void inventoryAndCoordinatesComeFromIndependentMysql() {
  Room room=mapper.rooms().get(0);assertEquals("研发大厦 1F 机房",room.getName());
  assertEquals(5,mapper.zones(room.getId()).size());assertEquals(42,mapper.cabinets(room.getId()).size());assertEquals(129,mapper.devices(room.getId()).size());
  for(Cabinet c:mapper.cabinets(room.getId())) {
   assertEquals(-6+c.getGridColumn()*2.4,c.getPositionX().doubleValue(),0.00001);
   assertEquals(-4.8+c.getGridRow()*1.45,c.getPositionZ().doubleValue(),0.00001);
   assertEquals(c.getCode(),c.getSceneModelKey());assertEquals("REAL",c.getRecordOrigin());
   assertEquals(c.getCode().equals("A-01")?6:3,mapper.cabinetDevices(c.getId()).size());
  }
  for(Device d:mapper.devices(room.getId())){assertNull(d.getUStart());assertNull(d.getUHeight());assertEquals("REAL",d.getRecordOrigin());}
 }
}
