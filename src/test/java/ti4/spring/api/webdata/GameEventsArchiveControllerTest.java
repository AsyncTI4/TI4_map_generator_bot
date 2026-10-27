package ti4.spring.api.webdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import ti4.spring.service.gameevent.GameEventArchiveService;

class GameEventsArchiveControllerTest {

    private final GameEventArchiveService service = mock(GameEventArchiveService.class);
    private final GameEventsArchiveController controller = new GameEventsArchiveController(service);

    @Test
    void streamsTheZipAsAnAttachmentNamedAfterTheTimestamp() throws IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.getEvents(1_700_000_000_000L, response);

        verify(service).writeEventsSinceAsZip(any(), eq(1_700_000_000_000L));
        assertThat(response.getContentType()).isEqualTo("application/zip");
        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains("game-events-since-1700000000000.zip");
    }

    @Test
    void treatsANegativeTimestampAsTheBeginningOfTime() throws IOException {
        controller.getEvents(-5L, new MockHttpServletResponse());

        verify(service).writeEventsSinceAsZip(any(), eq(0L));
    }
}
