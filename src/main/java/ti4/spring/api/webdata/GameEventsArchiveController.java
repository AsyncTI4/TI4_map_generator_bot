package ti4.spring.api.webdata;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ti4.spring.service.gameevent.GameEventArchiveService;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/public/games")
public class GameEventsArchiveController {

    private static final String ZIP_CONTENT_TYPE = "application/zip";

    private final GameEventArchiveService gameEventArchiveService;

    @GetMapping("/events")
    public void getEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletResponse response)
            throws IOException {
        if (page < 0 || limit < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "page must be nonnegative and limit must be positive");
        }
        int pageSize = Math.min(limit, GameEventArchiveService.MAX_PAGE_SIZE);
        response.setContentType(ZIP_CONTENT_TYPE);
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                        .filename("completed-games-page-" + page + ".zip")
                        .build()
                        .toString());
        gameEventArchiveService.writeCompletedGamesAsZip(response.getOutputStream(), page, pageSize);
    }
}
