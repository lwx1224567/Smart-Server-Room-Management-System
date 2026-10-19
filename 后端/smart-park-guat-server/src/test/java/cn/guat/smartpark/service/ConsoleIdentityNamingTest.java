package cn.guat.smartpark.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;

class ConsoleIdentityNamingTest {
 @Test void migratesExistingOwnerColumnOnlyOnce(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  ConsoleService service=new ConsoleService(db,new ObjectMapper(),mock(MonitoringService.class));
  when(db.queryForObject(anyString(),eq(Integer.class))).thenReturn(1,0);
  service.migrateOwnerColumn();service.migrateOwnerColumn();
  verify(db,times(1)).execute("ALTER TABLE console_order CHANGE COLUMN owner_id owner_account_id BIGINT NULL");
 }
 @Test void readsHistoricalOwnerWithoutChangingAccountId(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  ConsoleService service=new ConsoleService(db,new ObjectMapper(),mock(MonitoringService.class));
  when(db.queryForList("SELECT payload FROM console_order ORDER BY created_at DESC")).thenReturn(Arrays.asList(
   object("payload","{\"id\":\"WO-old\",\"ownerId\":42}"),
   object("payload","{\"id\":\"WO-free\",\"ownerId\":null}"),
   object("payload","{\"id\":\"WO-new\",\"ownerAccountId\":71,\"ownerId\":42}")));
  List<Map<String,Object>> orders=service.orders(object("role","ADMIN"));
  assertEquals(42,orders.get(0).get("ownerAccountId"));
  assertTrue(orders.get(1).containsKey("ownerAccountId"));
  assertNull(orders.get(1).get("ownerAccountId"));
  assertEquals(71,orders.get(2).get("ownerAccountId"));
  for(Map<String,Object> order:orders)assertFalse(order.containsKey("ownerId"));
 }
}
