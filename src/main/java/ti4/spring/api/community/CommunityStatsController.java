package ti4.spring.api.community;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/public/community")
public class CommunityStatsController {

    private final CommunityStatsService communityStatsService;

    @GetMapping("/stats")
    public CommunityStatsResponse get() {
        return communityStatsService.get();
    }
}
