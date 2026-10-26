package com.example.backend.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FrontendControllerTests {

  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FrontendController()).build();

  @Test
  void browserWorkspaceRoutesUseThePackagedEntry() throws Exception {
    mvc.perform(get("/workspace")).andExpect(forwardedUrl("/index.html"));
    mvc.perform(get("/workspace/planning")).andExpect(forwardedUrl("/index.html"));
  }

  @Test
  void fallbackDoesNotCaptureApiActuatorOrAssetRequests() throws Exception {
    for (String path :
        new String[] {"/api/v1/missing", "/actuator/missing", "/assets/missing.js"}) {
      mvc.perform(get(path)).andExpect(status().isNotFound());
    }
  }
}
