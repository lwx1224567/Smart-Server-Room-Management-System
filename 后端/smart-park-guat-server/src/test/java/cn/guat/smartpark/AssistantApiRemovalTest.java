package cn.guat.smartpark;

import cn.guat.smartpark.controller.*;
import cn.guat.smartpark.exception.*;
import cn.guat.smartpark.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real controllers and auth filter, mocked services; no application or datasource. */
class AssistantApiRemovalTest {
 final ConsoleService console=mock(ConsoleService.class);
 final IntegrationService integration=mock(IntegrationService.class);
 final AssistantAgentService agent=mock(AssistantAgentService.class);
 final Map<String,Object>admin=object("id",1,"role","ADMIN"),maintainer=object("id",2,"role","MAINTAINER");
 MockMvc mvc(){
  when(console.identity(any())).thenAnswer(i->{if("Bearer admin".equals(i.getArgument(0)))return admin;if("Bearer tech".equals(i.getArgument(0)))return maintainer;throw new PlatformException(401,"UNAUTHORIZED","请先登录");});
  doAnswer(i->{if(!"ADMIN".equals(((Map<?,?>)i.getArgument(0)).get("role")))throw new PlatformException(403,"FORBIDDEN","需要管理员");return null;}).when(console).admin(anyMap());
  ObjectMapper json=new ObjectMapper();
  return MockMvcBuilders.standaloneSetup(new IntegrationController(integration,console),new AssistantAgentController(agent,console,json))
   .setControllerAdvice(new PlatformExceptionHandler()).addFilters(new ConsoleAccessFilter(console,json)).build();
 }
 @Test void oldPathIsUnmappedAndCannotBeRedirectedOrHandledByAgent()throws Exception{
  MockMvc mvc=mvc();
  for(String path:Arrays.asList("/api/v1/assistant/chat","/api/v1/assistant/chat/")){
   org.springframework.test.web.servlet.MvcResult result=mvc.perform(post(path).header("Authorization","Bearer admin").contentType("application/json").content("{\"text\":\"确认指派\",\"probeModel\":true}"))
    .andExpect(status().isNotFound()).andExpect(header().doesNotExist("Location")).andReturn();
   assertNull(result.getHandler());assertNull(result.getResponse().getForwardedUrl());
  }
  verifyNoInteractions(agent,integration);
  assertFalse(Arrays.stream(IntegrationController.class.getDeclaredMethods()).anyMatch(m->m.getName().equals("model")));
  assertFalse(Arrays.stream(IntegrationService.class.getDeclaredMethods()).anyMatch(m->m.getName().equals("model")));
 }
 @Test void agentAuthenticationAndRoleChecksRejectBeforeDispatch()throws Exception{
  MockMvc mvc=mvc();
  for(String route:Arrays.asList("chat","confirm","confirmation/status","scene-result")){
   mvc.perform(post("/api/v1/assistant/rooms/1/"+route).contentType("application/json").content("{}"))
    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.type").value("UNAUTHORIZED"));
   mvc.perform(post("/api/v1/assistant/rooms/1/"+route).header("Authorization","Bearer tech").contentType("application/json").content("{}"))
    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.type").value("FORBIDDEN"));
  }verifyNoInteractions(agent,integration);
 }
 @Test void newChatStillDispatchesToAgentAndNeverToLegacyGateway()throws Exception{
  MockMvc mvc=mvc();when(agent.chat(anyMap(),eq("1"),anyMap())).thenReturn(object("reply","请核对确认卡片","messages",Collections.emptyList()));
  mvc.perform(post("/api/v1/assistant/rooms/1/chat").header("Authorization","Bearer admin").header("X-Assistant-Page","page-test")
   .contentType("application/json").content("{\"text\":\"把工单指派给user1\",\"requestId\":\"new-chat\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.data.reply").value("请核对确认卡片"));
  verify(agent).chat(argThat(a->"page-test".equals(a.get("assistantPageId"))),eq("1"),argThat(b->"new-chat".equals(b.get("requestId"))));
  verify(agent,never()).confirm(anyMap(),anyString(),anyMap());verifyNoInteractions(integration);
 }
}
