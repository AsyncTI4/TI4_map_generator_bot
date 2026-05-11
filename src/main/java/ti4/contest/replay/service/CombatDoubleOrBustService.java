package ti4.contest.replay.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.stereotype.Service;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatDoubleOrBustEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.repository.CombatCandidateRepository;
import ti4.contest.replay.repository.CombatDoubleOrBustRepository;
import ti4.contest.replay.repository.CombatReplayContestRepository;

@Service
@RequiredArgsConstructor
public class CombatDoubleOrBustService {

    private final CombatReplayContestRepository replayContestRepository;
    private final CombatCandidateRepository candidateRepository;
    private final CombatDoubleOrBustRepository doubleOrBustRepository;
    private final CombatReplaySideBetUiService sideBetUiService;

    public synchronized ToggleResult toggle(ButtonInteractionEvent event, Long contestId) {
        if (event == null || contestId == null) return ToggleResult.rejected("Double or Bust context is incomplete.");

        CombatReplayContestEntity contest =
                replayContestRepository.findById(contestId).orElse(null);
        if (contest == null) return ToggleResult.rejected("This combat contest is no longer available.");
        if (contest.getReplayStartAt() == null || !LocalDateTime.now().isBefore(contest.getReplayStartAt())) {
            return ToggleResult.rejected("The Double or Bust window is closed.");
        }

        String userId = event.getUser().getId();
        String userName = event.getUser().getEffectiveName();
        CombatDoubleOrBustEntity doubleOrBust = doubleOrBustRepository
                .findByContestIdAndDiscordUserId(contestId, userId)
                .orElseGet(CombatDoubleOrBustEntity::new);
        doubleOrBust.setContestId(contestId);
        doubleOrBust.setDiscordUserId(userId);
        doubleOrBust.setDiscordUserName(userName);
        doubleOrBust.setEnabled(!Boolean.TRUE.equals(doubleOrBust.getEnabled()));
        doubleOrBust.setUpdatedAt(LocalDateTime.now());
        doubleOrBustRepository.save(doubleOrBust);
        refreshSummary(event, contest);

        if (Boolean.TRUE.equals(doubleOrBust.getEnabled())) {
            return ToggleResult.accepted("You are doubling for this combat. Correct prediction: double payout. "
                    + "Incorrect prediction: regular loss plus your predicted side's would-have-won payout.");
        }
        return ToggleResult.accepted("You are no longer doubling for this combat.");
    }

    private void refreshSummary(ButtonInteractionEvent event, CombatReplayContestEntity contest) {
        if (event == null || contest == null || contest.getCandidateId() == null) return;
        CombatCandidateEntity candidate =
                candidateRepository.findById(contest.getCandidateId()).orElse(null);
        sideBetUiService.refreshSummaryMessage(event.getMessageChannel(), contest, candidate);
    }

    public Set<String> lockedDoubleOrBustUserIds(Long contestId) {
        if (contestId == null) return Set.of();
        Set<String> userIds = new HashSet<>();
        for (CombatDoubleOrBustEntity doubleOrBust : doubleOrBustRepository.findByContestIdAndEnabledTrue(contestId)) {
            userIds.add(doubleOrBust.getDiscordUserId());
        }
        return userIds;
    }

    public List<CombatDoubleOrBustEntity> findForUsers(Long contestId, Set<String> userIds) {
        if (contestId == null || userIds == null || userIds.isEmpty()) return List.of();
        return doubleOrBustRepository.findByContestIdAndDiscordUserIdIn(contestId, userIds);
    }

    public record ToggleResult(boolean accepted, String message) {
        public static ToggleResult accepted(String message) {
            return new ToggleResult(true, message);
        }

        public static ToggleResult rejected(String message) {
            return new ToggleResult(false, message);
        }
    }
}
