package cn.guat.smartpark;
import cn.guat.smartpark.mapper.InventoryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class InventoryFailureResponseTest {
 @MockBean cn.guat.smartpark.service.ConsoleService console;
 @org.junit.jupiter.api.BeforeEach void authorizeInventory(){org.mockito.Mockito.when(console.identity(org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(cn.guat.smartpark.dto.inventory.InventoryDtos.object("role","ADMIN"));}
 @Autowired MockMvc mvc; @MockBean InventoryMapper mapper;
 @Test void databaseFailureIsUnavailableWithoutSqlLeak() throws Exception {
  when(mapper.rooms()).thenThrow(new DataAccessResourceFailureException("private connection diagnostic"));
  mvc.perform(get("/api/v1/rooms")).andExpect(status().isServiceUnavailable())
    .andExpect(jsonPath("$.code").value(503)).andExpect(jsonPath("$.error.type").value("DATA_SOURCE_UNAVAILABLE"))
    .andExpect(jsonPath("$.msg").value("台账数据库暂不可用，请重试"));
 }
}
