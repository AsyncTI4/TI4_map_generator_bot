package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.image.Mapper;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.model.ActionCardModel;
import ti4.service.actioncard.SabotageService;

@UtilityClass
class SabotageWindow {

    private static final String MOVE_ALONG = "moveAlongAfterAllHaveReactedToAC_";
    private static final String NO_SABOTAGE = "no_sabotage";
    private static final String SABOTAGE_PREFIX = "sabotage_ac_";
    private static final long CLOCK_SKEW_MILLIS = 5_000L;
    private static final long REGISTRATION_GRACE_MILLIS = 15_000L;

    static String title(String alias) {
        ActionCardModel model = Mapper.getActionCard(alias);
        return model == null ? alias : model.getName();
    }

    static boolean expected(Game game, Player seat, String alias) {
        ActionCardModel model = Mapper.getActionCard(alias);
        return model != null
                && !ActionCardHelper.cannotBeSabotaged(model)
                && !seat.hasTech("baarvag")
                && SabotageService.isSaboAllowed(game, seat);
    }

    static Optional<AiPrompt> announcement(AiTurnContext context, String alias, long playedAt) {
        String title = title(alias);
        String faction = context.faction();
        String sabotage = SABOTAGE_PREFIX + title + "_" + faction;
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || prompt.createdAtMillis() < playedAt - CLOCK_SKEW_MILLIS) continue;
            boolean moveAlong = prompt.buttons().stream()
                    .anyMatch(button -> button.isOwnedBy(faction) && (MOVE_ALONG + title).equals(button.handlerId()));
            boolean sabotageButtons = prompt.buttons().stream().anyMatch(button -> sabotage.equals(button.handlerId()))
                    && prompt.buttons().stream().anyMatch(button -> NO_SABOTAGE.equals(button.handlerId()));
            if (moveAlong || sabotageButtons) return Optional.of(prompt);
        }
        return Optional.empty();
    }

    static boolean open(AiTurnContext context, String announcementId, long announcedAt) {
        Game game = context.game();
        Optional<GameMessage> entry = GameMessageManager.getOne(game.getName(), announcementId);
        if (entry.isEmpty()) return context.now() < announcedAt + REGISTRATION_GRACE_MILLIS;
        List<String> others = game.getRealPlayers().stream()
                .filter(player -> player != context.seat())
                .map(Player::getFaction)
                .toList();
        return !entry.get().factionsThatReacted().containsAll(others);
    }

    static boolean canceled(Game game, Player seat, String alias, int playsBefore) {
        String title = title(alias);
        String playerId = GameStats.getTrackedPlayerId(seat);
        List<GameStats.ActionCardPlay> plays = game.getGameStats().getActionCardPlays();
        for (int index = plays.size() - 1; index >= Math.max(0, playsBefore); index--) {
            GameStats.ActionCardPlay play = plays.get(index);
            if (title.equals(play.getActionCard()) && playerId != null && playerId.equals(play.getPlayerId())) {
                return play.isCanceled();
            }
        }
        return false;
    }
}
