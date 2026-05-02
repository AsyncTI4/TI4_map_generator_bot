package ti4.contest.replay.house;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.contest.replay.entities.CombatCandidateEntity;
import ti4.contest.replay.entities.CombatReplayContestEntity;
import ti4.game.Game;

public interface CombatReplayHouseAbility {
    default void postDiscussionWindowAbilities(
            Game game, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {}

    default void beforeSideBetMarket(
            MessageChannel channel, Game game, CombatReplayContestEntity contest, CombatCandidateEntity candidate) {}
}
