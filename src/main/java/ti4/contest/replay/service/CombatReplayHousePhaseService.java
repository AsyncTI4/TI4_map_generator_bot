package ti4.contest.replay.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatCandidatePromotionStatus;
import ti4.contest.replay.core.CombatCandidateStatus;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.repository.CombatReplayContestRepository;

@Service
@RequiredArgsConstructor
public class CombatReplayHousePhaseService {

    private final CombatContestSettings settings;
    private final CombatReplayContestRepository contestRepository;

    public boolean discussionOpen(CombatReplayContestEntity contest) {
        if (!settings.isHousesEnabled()) return false;
        if (contest == null || contest.getId() == null || contest.getPostedAt() == null) return false;
        if (contest.getSideBetMarketPostedAt() != null) return false;
        if (contest.getReplayStartAt() == null || !LocalDateTime.now().isBefore(contest.getReplayStartAt()))
            return false;
        return LocalDateTime.now().isBefore(contest.getPostedAt().plusSeconds(discussionWindowSeconds()));
    }

    public boolean discussionOpenForCandidate(CombatCandidateEntity candidate) {
        return settings.isHousesEnabled() && candidate != null;
    }

    public boolean mentakPreviewOpen(CombatCandidateEntity candidate) {
        return settings.isHousesEnabled()
                && candidate != null
                && candidate.getStatus() == CombatCandidateStatus.RESOLVED
                && candidate.getPromotionStatus() == CombatCandidatePromotionStatus.PENDING
                && candidate.getMentakPreviewPostedAt() != null;
    }

    public boolean postCombatVoteOpen(CombatReplayContestEntity contest) {
        return settings.isHousesEnabled()
                && contest != null
                && contest.getId() != null
                && !contestRepository.existsByIdGreaterThanAndReplayCompletedAtIsNotNull(contest.getId())
                && (contest.getReplayCompletedAt() != null || contest.getLeaderboardPostedAt() != null);
    }

    public int discussionWindowSeconds() {
        return settings.getReplayExecution().getDiscussionWindowSeconds();
    }
}
