package ti4.spring.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import ti4.testUtils.BaseTi4Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class RateLimitIntegrationTest extends BaseTi4Test {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void hittingPingMoreThanThirtyTimesReturns429() throws Exception {
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(get("/api/public/ping"))
                .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/public/ping"))
            .andExpect(status().isTooManyRequests());
    }
}
