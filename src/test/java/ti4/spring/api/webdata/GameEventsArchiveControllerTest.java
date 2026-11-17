package ti4.spring.api.webdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import ti4.spring.service.gameevent.GameEventArchiveService;

class GameEventsArchiveControllerTest {

    private final GameEventArchiveService service = mock(GameEventArchiveService.class);
    private final GameEventsArchiveController controller = new GameEventsArchiveController(service);

    @Test
    void defaultsToTheFirstHundredGamesAndIgnoresTheRemovedSinceParameter() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/api/public/games/events").param("since", "1700000000000"))
                .andExpect(status().isOk());

        verify(service).writeCompletedGamesAsZip(any(), eq(0), eq(100));
    }

    @Test
    void streamsTheRequestedPageAsAZipAttachment() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.getEvents(2, 50, response);

        verify(service).writeCompletedGamesAsZip(any(), eq(2), eq(50));
        assertThat(response.getContentType()).isEqualTo("application/zip");
        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("completed-games-page-2.zip");
    }

    @Test
    void capsTheLimitAtFiveHundredGames() throws IOException {
        controller.getEvents(0, 501, new MockHttpServletResponse());

        verify(service).writeCompletedGamesAsZip(any(), eq(0), eq(500));
    }

    @Test
    void rejectsNegativePagesAndNonpositiveLimitsBeforeStreaming() {
        for (int[] parameters : new int[][] {{-1, 100}, {0, 0}, {0, -1}}) {
            assertThatThrownBy(() -> controller.getEvents(parameters[0], parameters[1], new MockHttpServletResponse()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("400");
        }
        verifyNoInteractions(service);
    }
}
