package ti4.contest.replay.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.springframework.stereotype.Service;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.contest.replay.house.CombatReplayHouseAbility;
import ti4.game.Game;

@Service
@RequiredArgsConstructor
public class CombatReplayHouseAbilityPhaseService {

    private final List<CombatReplayHouseAbility> houseAbilities;
    private final CombatReplaySideBetService sideBetService;

    public void postDiscussionWindowAbilities(
            Game game, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        for (CombatReplayHouseAbility ability : houseAbilities) {
            ability.postDiscussionWindowAbilities(game, contest, candidate);
        }
    }

    public void openSideBetMarket(
            MessageChannel channel, Game game, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {
        for (CombatReplayHouseAbility ability : houseAbilities) {
            ability.beforeSideBetMarket(channel, game, contest, candidate);
        }
        sideBetService.postSideBetButtonsIfNeeded(channel, game, contest, candidate);
    }
}
