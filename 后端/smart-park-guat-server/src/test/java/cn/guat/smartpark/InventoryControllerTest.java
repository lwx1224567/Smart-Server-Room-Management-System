package cn.guat.smartpark;
import cn.guat.smartpark.mapper.InventoryMapper;
import cn.guat.smartpark.entity.Cabinet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class InventoryControllerTest {
 @org.springframework.boot.test.mock.mockito.MockBean cn.guat.smartpark.service.ConsoleService console;
 @org.junit.jupiter.api.BeforeEach void authorizeInventory(){org.mockito.Mockito.when(console.identity(org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(cn.guat.smartpark.dto.inventory.InventoryDtos.object("role","ADMIN"));}
 @Autowired MockMvc mvc; @Autowired InventoryMapper mapper;
 @Test void sixEndpointsAndUnknownTelemetry() throws Exception {
  String room=mapper.rooms().get(0).getId().toString();
  Cabinet cabinet=mapper.cabinets(Long.valueOf(room)).stream().filter(c->c.getCode().equals("A-01")).findFirst().get();String id=cabinet.getId().toString();
  mvc.perform(get("/api/v1/rooms")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id",isA(String.class))).andExpect(jsonPath("$.data.total").value(1));
  mvc.perform(get("/api/v1/rooms/"+room)).andExpect(status().isOk()).andExpect(jsonPath("$.data.cabinetCount").value(42)).andExpect(jsonPath("$.data.registeredDeviceCount").value(129));
  mvc.perform(get("/api/v1/rooms/"+room+"/scene")).andExpect(status().isOk()).andExpect(jsonPath("$.data.zones",hasSize(5))).andExpect(jsonPath("$.data.cabinets",hasSize(42))).andExpect(jsonPath("$.data.devices",hasSize(129))).andExpect(jsonPath("$.data.devices[0].runtimeStatus").value("UNKNOWN"));
  mvc.perform(get("/api/v1/rooms/"+room+"/cabinets").param("pageSize","100")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items",hasSize(42)));
  mvc.perform(get("/api/v1/cabinets/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.telemetry.powerState").value("UNKNOWN")).andExpect(jsonPath("$.data.telemetry.networkState").value("UNKNOWN")).andExpect(jsonPath("$.data.telemetry.temperature.value").value(nullValue())).andExpect(jsonPath("$.data.usedU").value(nullValue()));
  mvc.perform(get("/api/v1/cabinets/"+id+"/devices")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(6)).andExpect(jsonPath("$.data.items[0].cabinetId",isA(String.class)));
 }
 @Test void invalidInputsAndStablePaging() throws Exception {
  String room=mapper.rooms().get(0).getId().toString();
  mvc.perform(get("/api/v1/rooms").param("pageSize","101")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.type").value("INVALID_ARGUMENT"));
  mvc.perform(get("/api/v1/rooms").param("sort","password,asc")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/rooms").param("recordOrigin","ALL")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/cabinets/999999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
  mvc.perform(get("/api/v1/cabinets/not-an-id")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/rooms/"+room+"/cabinets").param("zoneId","999999")).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/rooms/"+room+"/cabinets").param("q","A-").param("sort","code,desc")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(8)).andExpect(jsonPath("$.data.items[0].code").value("A-08"));
 }
 @Test void generatedCabinetMatchesInventory() throws Exception {
  Long room=mapper.rooms().get(0).getId();String id=mapper.cabinets(room).stream().filter(c->c.getCode().equals("E-09")).findFirst().get().getId().toString();
  mvc.perform(get("/api/v1/cabinets/"+id+"/devices")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items",hasSize(3))).andExpect(jsonPath("$.data.total").value(3));
 }
}
