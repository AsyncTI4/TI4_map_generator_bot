package ti4.discord.interactions.commands.developer;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
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
import ti4.message.MessageHelper;

class RunAgainstAllGames extends Subcommand {

    private static final String DRY_RUN_OPTION = "dry_run";
    private static final LocalDate PLAYER_TRACKING_START_DATE = LocalDate.of(2026, 8, 1);
    /**
     * Older games stored every Council Keleres as a bare "keleres", which no faction file answers
     * to - faction_alias maps it to keleres_dont_use_this. Every statistic that reads a faction
     * model drops those players, so retype them as the flavour their home system says they were.
     */
    private static final String LEGACY_KELERES_FACTION = "keleres";

    /**
     * What a player nothing in their game can place becomes. The handful this catches are all
     * unfinished or fog games that no statistic reads, so the flavour is a label rather than a
     * finding - each one is listed in the report so the guess stays visible.
     */
    private static final String DEFAULT_KELERES_FACTION = "keleresm";

    /**
     * The Keleres-only home systems. Nobody else sits on these, so one of them anywhere on a board
     * names the flavour even when the player it belonged to cannot be placed any other way.
     */
    private static final Map<String, String> KELERES_FACTION_BY_KELERES_TILE =
            Map.of("92new", "keleresx", "93new", "keleresa", "94new", "keleresm");

    /**
     * The same three home systems as the base factions wear them. Keleres predates the re-skinned
     * 92new/93new/94new tiles, so the older games this command exists for seat Keleres on 02, 14 and
     * 58 instead - which are also Mentak's, Xxcha's and Argent's, so these only count when the tile
     * is the legacy Keleres player's own.
     */
    private static final Map<String, String> KELERES_FACTION_BY_OWN_TILE = Map.ofEntries(
            Map.entry("92new", "keleresx"),
            Map.entry("93new", "keleresa"),
            Map.entry("94new", "keleresm"),
            Map.entry("14", "keleresx"),
            Map.entry("58", "keleresa"),
            Map.entry("02", "keleresm"),
            Map.entry("2", "keleresm"));

    /**
     * The faction each flavour borrows its home system from. Keleres never shares a table with that
     * faction, so a home system of theirs in a game without them can only be the Keleres player's.
     */
    private static final Map<String, String> BORROWED_FROM =
            Map.of("keleresx", "xxcha", "keleresa", "argent", "keleresm", "mentak");

    private static final Map<String, String> KELERES_FACTION_BY_HOME_PLANET = Map.ofEntries(
            Map.entry("archonrenk", "keleresx"),
            Map.entry("archontauk", "keleresx"),
            Map.entry("valkk", "keleresa"),
            Map.entry("ylirk", "keleresa"),
            Map.entry("avark", "keleresa"),
            Map.entry("mollprimusk", "keleresm"),
            Map.entry("archonren", "keleresx"),
            Map.entry("archontau", "keleresx"),
            Map.entry("valk", "keleresa"),
            Map.entry("ylir", "keleresa"),
            Map.entry("avar", "keleresa"),
            Map.entry("mollprimus", "keleresm"));

    RunAgainstAllGames() {
        super("run_against_all_games", "Retypes legacy 'keleres' players as keleresm, keleresa or keleresx.");
        addOptions(new OptionData(
                OptionType.BOOLEAN, DRY_RUN_OPTION, "Report what would change without saving anything."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean dryRun = event.getOption(DRY_RUN_OPTION, false, OptionMapping::getAsBoolean);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Adding TE ACs to all TE games that started after " + PLAYER_TRACKING_START_DATE
                        + (dryRun ? " (dry run, nothing will be saved)." : "."));

        List<String> changedGames = new ArrayList<>();
        int[] migratedTargets = {0};

        ConsumeGameUtility.consumeAllGames(
                game -> {
                    if (!startedAfterPlayerTracking(game) || game.isHasEnded()) {
                        return;
                    }
                    int migrated = migrateLegacyTargets(game, dryRun);
                    migratedTargets[0] += migrated;
                    changedGames.add(game.getName() + " ");
                    if (!dryRun) {
                        GameManager.save(game, "Added TE ACs.");
                    }
                },
                dryRun ? ExecutionLockType.READ : ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                (dryRun ? "[DRY RUN] Would remove " : "Removed ") + "convert " + migratedTargets[0]
                        + " leftover legacy targets"
                        + " across " + changedGames.size() + " games out of " + GameManager.getGameCount() + " games: "
                        + String.join(", ", changedGames));
    }

    /**
     * The tile the player is anchored to. Safe to read the base home systems from, unlike a sweep of
     * the whole board, because this one is theirs.
     */
    static String factionFromTheirOwnHomeTile(Game game, Player player) {
        return Stream.of(player.getHomeSystemPosition(), player.getPlayerStatsAnchorPosition())
                .filter(position -> StringUtils.isNotBlank(position) && !"null".equalsIgnoreCase(position))
                .map(game::getTileByPosition)
                .filter(Objects::nonNull)
                .map(tile -> KELERES_FACTION_BY_OWN_TILE.get(tile.getTileID()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
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
