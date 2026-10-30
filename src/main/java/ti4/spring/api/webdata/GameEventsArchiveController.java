package ti4.spring.api.webdata;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ti4.spring.service.gameevent.GameEventArchiveService;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/public/games")
public class GameEventsArchiveController {

    private static final String ZIP_CONTENT_TYPE = "application/zip";

    private final GameEventArchiveService gameEventArchiveService;

    @GetMapping("/events")
    public void getEvents(@RequestParam(defaultValue = "0") long since, HttpServletResponse response)
            throws IOException {
        long sinceEpochMillis = Math.max(since, 0);
        response.setContentType(ZIP_CONTENT_TYPE);
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                        .filename("game-events-since-" + sinceEpochMillis + ".zip")
                        .build()
                        .toString());
        gameEventArchiveService.writeEventsSinceAsZip(response.getOutputStream(), sinceEpochMillis);
    }
}
