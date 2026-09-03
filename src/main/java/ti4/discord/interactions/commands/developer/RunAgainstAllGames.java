package ti4.discord.interactions.commands.developer;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.helper.GameHelper;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

class RunAgainstAllGames extends Subcommand {

    private static final String DRY_RUN_OPTION = "dry_run";

    // Games older than this did not have TE as the default.
    private static final LocalDate PLAYER_TRACKING_START_DATE = LocalDate.of(2026, 8, 1);

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
        int[] migratedTargets = {0};
        ConsumeGameUtility.consumeAllGames(
                game -> {
                    int migrated = migrateLegacyTargets(game, dryRun);
                    if (!startedAfterPlayerTracking(game)) {
                        return;
                    }
                    migratedTargets[0] += migrated;
                    changedGames.add(game.getName() + " ");
                    if (!dryRun) {
                        GameManager.save(game, "Added TE ACs.");
                    }
                },
                dryRun ? ExecutionLockType.READ : ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info((dryRun ? "[DRY RUN] Would remove " : "Removed ") + "convert " + migratedTargets[0]
                + " leftover legacy targets"
                + " across " + changedGames.size() + " games out of " + GameManager.getGameCount() + " games: "
                + String.join(", ", changedGames));
    }

    private static int removeFabricatedCancels(Game game, boolean dryRun) {
        return dryRun
                ? game.getGameStats().findFabricatedCancels().size()
                : game.getGameStats().removeFabricatedCancels();
    }

    @SuppressWarnings("deprecation")
    private static int migrateLegacyTargets(Game game, boolean dryRun) {
        if (dryRun) {
            if (!game.getAcDeckID().equalsIgnoreCase("action_cards_te")) {
                return 0;
            }
            // Counting instead of converting - a dry run must not touch the game.
            int acCount = game.getActionCards().size()
                    + game.getPurgedActionCards().size()
                    + game.getDiscardActionCards().size();
            for (Player player : game.getRealPlayers()) {
                acCount += player.getActionCards().size();
            }
            if (acCount < 140) {
                return 1;
            } else {
                return 0;
            }
        }
        int pending = migrateLegacyTargets(game, true);
        if (pending == 0) {
            return 0;
        }
        game.addTeACs();
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
