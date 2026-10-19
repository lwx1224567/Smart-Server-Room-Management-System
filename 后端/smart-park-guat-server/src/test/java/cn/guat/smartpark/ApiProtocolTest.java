package cn.guat.smartpark;

import cn.guat.smartpark.controller.AssistantAgentController;
import cn.guat.smartpark.exception.PlatformExceptionHandler;
import cn.guat.smartpark.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import java.util.function.Consumer;
import static cn.guat.smartpark.dto.inventory.InventoryDtos.object;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiProtocolTest {
    @Test void servletErrorsUseTheSameEnvelope() throws Exception {
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new cn.guat.smartpark.controller.ApiErrorController()).build();
        mvc.perform(get("/error").requestAttr(javax.servlet.RequestDispatcher.ERROR_STATUS_CODE,404))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.error.type").value("NOT_FOUND")).andExpect(jsonPath("$.requestId").isNotEmpty());
    }
    @Test void agentChatUsesSharedJsonEnvelope() throws Exception {
        AssistantAgentService agent=mock(AssistantAgentService.class);ConsoleService console=mock(ConsoleService.class);
        when(console.identity("Bearer test")).thenReturn(object("id",1,"role","ADMIN"));
        when(agent.chat(anyMap(),eq("1"),anyMap())).thenReturn(object("reply","模型回复","messages",java.util.Collections.emptyList()));
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new AssistantAgentController(agent,console,new ObjectMapper()))
                .setControllerAdvice(new PlatformExceptionHandler()).build();
        mvc.perform(post("/api/v1/assistant/rooms/1/chat").header("Authorization","Bearer test").contentType("application/json").content("{\"text\":\"查询\",\"requestId\":\"r1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reply").value("模型回复"))
                .andExpect(jsonPath("$.requestId").isNotEmpty()).andExpect(jsonPath("$.serverTime").isNotEmpty())
                .andExpect(jsonPath("$.error").isEmpty());
        verify(agent).chat(anyMap(),eq("1"),anyMap());
    }

    @Test void assistantSseKeepsEventFramesAndFinalSession() throws Exception {
        AssistantAgentService agent=mock(AssistantAgentService.class);
        ConsoleService console=mock(ConsoleService.class);
        when(console.identity(anyString())).thenReturn(object("id",1,"role","ADMIN"));
        when(agent.stream(anyMap(),eq("1"),anyMap(),eq(false),any())).thenAnswer(invocation->{
            Consumer<Map<String,Object>> events=invocation.getArgument(4);
            events.accept(object("type","delta","data",object("text","正在查询")));
            return object("reply","完成","messages",java.util.Collections.emptyList());
        });
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new AssistantAgentController(agent,console,new ObjectMapper()))
                .setControllerAdvice(new PlatformExceptionHandler()).build();
        mvc.perform(post("/api/v1/assistant/rooms/1/chat").header("Authorization","Bearer test").header("X-Assistant-Page","page-test")
                .contentType("application/json").content("{\"text\":\"查询\",\"requestId\":\"r1\",\"stream\":true}"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: delta\ndata: ")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: done\ndata: ")));
    }
}
