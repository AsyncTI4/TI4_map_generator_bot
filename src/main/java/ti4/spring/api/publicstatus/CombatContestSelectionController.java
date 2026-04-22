package ti4.spring.api.publicstatus;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ti4.spring.service.contest.CombatContestSelectionService;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/public/contest")
public class CombatContestSelectionController {

    private final CombatContestSelectionService combatContestSelectionService;

    @GetMapping("/selection")
    public CombatContestSelectionService.Snapshot getSelection() {
        return combatContestSelectionService.getSelectionSnapshot();
    }
}
