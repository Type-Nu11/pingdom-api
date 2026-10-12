package com.typenull.pingdom.consultation.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typenull.pingdom.consultation.api.dto.AiChatResponse;
import com.typenull.pingdom.consultation.application.AiChatService;
import com.typenull.pingdom.shared.exception.handler.GlobalExceptionHandler;
import com.typenull.pingdom.shared.observability.AuthMetrics;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiChatControllerTest {
    private final AiChatService service = mock(AiChatService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AiChatController(service))
                .setControllerAdvice(new GlobalExceptionHandler(mock(AuthMetrics.class))).build();
    }

    @Test
    void returnsAssistantContent() throws Exception {
        when(service.chat(any())).thenReturn(new AiChatResponse("안녕하세요.", List.of()));
        mvc.perform(post("/ai/chat").contentType("application/json").content("""
                {"messages":[{"role":"user","content":"안녕"}],"tools":[]}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("안녕하세요."))
                .andExpect(jsonPath("$.tool_calls").isArray());
    }

    @Test
    void rejectsEmptyMessages() throws Exception {
        mvc.perform(post("/ai/chat").contentType("application/json").content("{\"messages\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(service);
    }
}
