package ti4.contest.replay.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatReplayHouseAbilityUseEntity;
import ti4.contest.replay.entities.CombatReplayHouseAbilityVoteEntity;
import ti4.contest.replay.repository.CombatReplayHouseAbilityUseRepository;
import ti4.contest.replay.repository.CombatReplayHouseAbilityVoteRepository;

@Service
@RequiredArgsConstructor
public class CombatReplayHouseAbilityVoteService {

    private final CombatContestSettings settings;
    private final CombatReplayHouseAbilityUseRepository useRepository;
    private final CombatReplayHouseAbilityVoteRepository voteRepository;
    private final CombatReplayHouseFavorService favorService;

    public VoteResult recordVote(
            long candidateId,
            CombatReplayHouse house,
            String optionKey,
            String optionLabel,
            String discordUserId,
            String discordUserName,
            Function<String, Integer> favorCost,
            Function<Long, String> voteSummary,
            String alreadyResolvedMessage,
            String lacksFavorMessage) {
        if (StringUtils.isBlank(discordUserId)) {
            return VoteResult.rejected("Could not record that delegation ability vote.");
        }
        if (useRepository.existsByCandidateIdAndHouse(candidateId, house)) {
            return VoteResult.rejected(alreadyResolvedMessage);
        }
        int cost = favorCost.apply(optionKey);
        if (cost > 0 && !favorService.canAfford(house, cost)) {
            return VoteResult.rejected(lacksFavorMessage);
        }

        CombatReplayHouseAbilityVoteEntity vote = voteRepository
                .findByCandidateIdAndHouseAndDiscordUserId(candidateId, house, discordUserId)
                .orElse(null);
        if (vote != null && optionKey.equals(vote.getOptionKey())) {
            voteRepository.delete(vote);
            return VoteResult.accepted(appendSummary("Withdrew vote.", voteSummary.apply(candidateId)));
        }
        if (vote == null) {
            vote = new CombatReplayHouseAbilityVoteEntity();
            vote.setCandidateId(candidateId);
            vote.setHouse(house);
            vote.setDiscordUserId(discordUserId);
        }
        vote.setOptionKey(optionKey);
        vote.setDiscordUserName(StringUtils.defaultIfBlank(discordUserName, "Unknown User"));
        vote.setVotedAt(LocalDateTime.now());
        voteRepository.save(vote);
        return VoteResult.accepted(
                appendSummary("Cast vote for **" + optionLabel + "**.", voteSummary.apply(candidateId)));
    }

    public boolean claimUse(
            long candidateId, CombatReplayHouse house, int favorCost, String discordUserId, String discordUserName) {
        if (StringUtils.isBlank(discordUserId)) return false;
        if (useRepository.existsByCandidateIdAndHouse(candidateId, house)) return false;
        if (favorCost > 0 && !favorService.canAfford(house, favorCost)) return false;

        CombatReplayHouseAbilityUseEntity use = new CombatReplayHouseAbilityUseEntity();
        use.setCandidateId(candidateId);
        use.setHouse(house);
        use.setFavorCost(favorCost);
        use.setDiscordUserId(discordUserId);
        use.setDiscordUserName(StringUtils.defaultIfBlank(discordUserName, "Unknown User"));
        use.setUsedAt(LocalDateTime.now());
        try {
            useRepository.saveAndFlush(use);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    public WinningVote winningVote(Long candidateId, CombatReplayHouse house, Function<String, String> optionLabel) {
        List<CombatReplayHouseAbilityVoteEntity> votes = voteRepository.findByCandidateIdAndHouse(candidateId, house);
        if (votes.isEmpty()) return null;
        if (distinctVoterCount(votes) < minimumAbilityVotesToResolve()) return null;

        return CombatReplayVoteTally.tallies(
                        votes,
                        vote -> new AbilityVoteOption(vote.getOptionKey(), optionLabel.apply(vote.getOptionKey())))
                .stream()
                .sorted(Comparator.comparingInt(
                                (CombatReplayVoteTally.Tally<CombatReplayHouseAbilityVoteEntity, AbilityVoteOption>
                                                tally) -> tally.voteCount())
                        .thenComparing(tally -> tally.option().label(), Comparator.reverseOrder())
                        .reversed())
                .findFirst()
                .map(tally -> {
                    CombatReplayHouseAbilityVoteEntity firstVote = tally.firstVote();
                    return new WinningVote(
                            tally.option().optionKey(),
                            tally.option().label(),
                            firstVote.getDiscordUserId(),
                            firstVote.getDiscordUserName(),
                            tally.voteCount());
                })
                .orElse(null);
    }

    public String voteSummary(Long candidateId, CombatReplayHouse house, Function<String, String> optionLabel) {
        List<CombatReplayHouseAbilityVoteEntity> votes = voteRepository.findByCandidateIdAndHouse(candidateId, house);
        if (votes.isEmpty()) return "No votes recorded.";
        Map<String, Integer> countsByOption = new HashMap<>();
        for (CombatReplayHouseAbilityVoteEntity vote : votes) {
            countsByOption.merge(vote.getOptionKey(), 1, Integer::sum);
        }
        List<String> lines = countsByOption.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(entry -> optionLabel.apply(entry.getKey())))
                .map(entry -> "- " + optionLabel.apply(entry.getKey()) + ": `" + entry.getValue() + "`")
                .toList();
        return "Current tally:\n" + String.join("\n", lines);
    }

    public int minimumAbilityVotesToResolve() {
        return Math.max(1, settings.getHouseAbilities().getMinimumAbilityVotesToResolve());
    }

    private long distinctVoterCount(List<CombatReplayHouseAbilityVoteEntity> votes) {
        return CombatReplayVoteTally.distinctVoterCount(votes, CombatReplayHouseAbilityVoteEntity::getDiscordUserId);
    }

    private String appendSummary(String message, String summary) {
        return StringUtils.isBlank(summary) ? message : message + "\n" + summary;
    }

    private record AbilityVoteOption(String optionKey, String label) {}

    public record WinningVote(
            String optionKey, String label, String discordUserId, String discordUserName, int voteCount) {}

    public record VoteResult(boolean accepted, String message) {
        public static VoteResult accepted(String message) {
            return new VoteResult(true, message);
        }

        public static VoteResult rejected(String message) {
            return new VoteResult(false, message);
        }
    }
}
