package ti4.discord.interactions.commands.developer;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.message.GameMessageManager;
import ti4.message.MessageHelper;
import ti4.service.strategycard.StrategyCardMessageService;

class RunAgainstAllGames extends Subcommand {

    private static final long ONE_DAY_MILLIS = Duration.ofDays(1).toMillis();
    private static final long THIRTY_DAYS_MILLIS = Duration.ofDays(30).toMillis();

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        Set<String> changedGames = new HashSet<>();
        ConsumeGameUtility.consumeAllGames(
                game -> {
                    boolean changed = makeChanges(game, event);
                    if (changed) {
                        changedGames.add(game.getName());
                        GameManager.save(
                                game, "Developer ran custom command against this game, probably migration related.");
                    }
                },
                ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info("Changes made to " + changedGames.size() + " games out of " + GameManager.getGameCount()
                + " games: " + String.join(", ", changedGames));
    }

    private static boolean makeChanges(Game game, SlashCommandInteractionEvent event) {
        if (!game.isHasEnded()) {
            return migrateScMessages(game);
        }

        return false;
    }

    /**
     * Migrates strategy card message data from the old game stored-value format
     * ({@code scPlayMsgID<sc>} / {@code scPlayMsgTime<round><sc>}) into
     * {@link GameMessageManager} so that {@code FastScFollowCron} and reaction
     * tracking work correctly after the refactor that removed these stored values.
     *
     * @return {@code true} if at least one SC message was migrated (triggering a game save)
     */
    private static boolean migrateScMessages(Game game) {
        boolean migrated = false;
        int round = game.getRound();

        for (int sc : game.getPlayedSCs()) {
            // Skip SCs that have already been migrated into GameMessageManager.
            if (StrategyCardMessageService.getStrategyCardMessage(game.getName(), round, sc)
                    .isPresent()) {
                continue;
            }

            // Read the message ID from the old stored value.
            String messageId = game.getStoredValue("scPlayMsgID" + sc);
            if (messageId.isEmpty()) {
                continue;
            }

            // Read the play timestamp; fall back to 0 if absent (FastScFollowCron will
            // then treat the SC as having been played a long time ago).
            String msgTimeStr = game.getStoredValue("scPlayMsgTime" + round + sc);
            long gameSaveTime = msgTimeStr.isEmpty() ? 0L : Long.parseLong(msgTimeStr);

            StrategyCardMessageService.replaceStrategyCardMessage(game.getName(), messageId, round, sc, gameSaveTime);

            // Populate factionsThatReacted for players who have already followed the SC,
            // so that ReactionService.progressGameIfAllPlayersHaveReacted works correctly.
            for (Player player : game.getRealPlayers()) {
                if (player.hasFollowedSC(sc)) {
                    GameMessageManager.addReaction(game.getName(), player.getFaction(), messageId);
                }
            }

            migrated = true;
        }

        return migrated;
    }
}
