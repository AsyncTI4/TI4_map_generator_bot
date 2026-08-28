package ti4.discord.interactions.commands.developer;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.helper.GameHelper;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.spring.service.statistics.overrule.OverruleStatsService;

class RunAgainstAllGames extends Subcommand {

    private static final String DRY_RUN_OPTION = "dry_run";

    // Games older than this recorded no player on any play, so a player-less cancel there says
    // nothing about whether it really happened and the cleanup below cannot judge it.
    private static final LocalDate PLAYER_TRACKING_START_DATE = LocalDate.of(2026, 5, 23);

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
        addOptions(new OptionData(
                OptionType.BOOLEAN, DRY_RUN_OPTION, "Report what would change without saving anything."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean dryRun = event.getOption(DRY_RUN_OPTION, false, OptionMapping::getAsBoolean);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Running custom command against all games" + (dryRun ? " (dry run, nothing will be saved)." : "."));

        List<String> changedGames = new ArrayList<>();
        int[] removedPlays = {0};
        int[] migratedTargets = {0};
        ConsumeGameUtility.consumeAllGames(
                game -> {
                    int migrated = migrateLegacyTargets(game, dryRun);
                    int removed = startedAfterPlayerTracking(game) ? removeFabricatedCancels(game, dryRun) : 0;
                    if (migrated == 0 && removed == 0) {
                        return;
                    }
                    migratedTargets[0] += migrated;
                    removedPlays[0] += removed;
                    changedGames.add(
                            game.getName() + " (" + removed + (migrated == 0 ? "" : ", " + migrated + "T)") + ")");
                    if (!dryRun) {
                        GameManager.save(game, "Removed action card cancels that never happened.");
                    }
                },
                dryRun ? ExecutionLockType.READ : ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info((dryRun ? "[DRY RUN] Would remove " : "Removed ") + removedPlays[0] + " fabricated cancel plays"
                + " and convert " + migratedTargets[0] + " leftover legacy targets"
                + " across " + changedGames.size() + " games out of " + GameManager.getGameCount() + " games: "
                + String.join(", ", changedGames));
    }

    private static int removeFabricatedCancels(Game game, boolean dryRun) {
        return dryRun
                ? game.getGameStats().findFabricatedCancels().size()
                : game.getGameStats().removeFabricatedCancels();
    }

    /**
     * Targets kept being written until 2026-08-08, after the migration that converts them last ran,
     * so a handful of games still carry some. Sweep them up here so the whole legacy target path can
     * be deleted once this has run.
     */
    @SuppressWarnings("deprecation")
    private static int migrateLegacyTargets(Game game, boolean dryRun) {
        if (dryRun) {
            // Counting instead of converting - a dry run must not touch the game.
            return (int) game.getGameStats().getActionCardPlays().stream()
                    .filter(play -> play.getTarget() != null)
                    .count();
        }
        int pending = migrateLegacyTargets(game, true);
        if (pending == 0) {
            return 0;
        }
        Map<String, Integer> strategyCardChoices =
                game.getGameStats().migrateTargetsToCanceledFlags().strategyCardChoices();
        if (!strategyCardChoices.isEmpty()) {
            OverruleStatsService.get().addMigratedCounts(game.getName(), strategyCardChoices);
        }
        return pending;
    }

    static boolean startedAfterPlayerTracking(Game game) {
        if (StringUtils.isBlank(game.getCreationDate())) {
            return false;
        }
        try {
            return GameHelper.getCreationDateAsLocalDate(game).isAfter(PLAYER_TRACKING_START_DATE);
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
