package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
import ti4.game.Tile;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

class RunAgainstAllGames extends Subcommand {

    private static final String DRY_RUN_OPTION = "dry_run";

    /**
     * Older games stored every Council Keleres as a bare "keleres", which no faction file answers
     * to - faction_alias maps it to keleres_dont_use_this. Every statistic that reads a faction
     * model drops those players, so retype them as the flavour their home system says they were.
     */
    private static final String LEGACY_KELERES_FACTION = "keleres";

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
                "Retyping legacy Keleres players across all games"
                        + (dryRun ? " (dry run, nothing will be saved)." : "."));

        List<String> retypedGames = new ArrayList<>();
        List<String> undeterminedGames = new ArrayList<>();
        int[] retypedPlayers = {0};

        ConsumeGameUtility.consumeAllGames(
                game -> {
                    List<Player> legacyPlayers = game.getPlayers().values().stream()
                            .filter(player -> LEGACY_KELERES_FACTION.equalsIgnoreCase(player.getFaction()))
                            .toList();
                    if (legacyPlayers.isEmpty()) {
                        return;
                    }

                    String boardFaction = factionFromTheOnlyKeleresHomeOnTheBoard(game);
                    List<String> retyped = new ArrayList<>();
                    for (Player player : legacyPlayers) {
                        String faction = factionFromTheirHomePlanets(player);
                        if (faction == null) {
                            faction = factionFromTheirOwnHomeTile(game, player);
                        }
                        if (faction == null) {
                            faction = boardFaction;
                        }
                        if (faction == null) {
                            undeterminedGames.add(describeUndetermined(game, player));
                            continue;
                        }
                        if (!dryRun) {
                            player.setFaction(faction);
                        }
                        retyped.add(faction);
                    }

                    if (retyped.isEmpty()) {
                        return;
                    }
                    retypedPlayers[0] += retyped.size();
                    retypedGames.add(game.getName() + " -> " + String.join(", ", retyped));
                    if (!dryRun) {
                        GameManager.save(game, "Retyped legacy Keleres players from their home system.");
                    }
                },
                dryRun ? ExecutionLockType.READ : ExecutionLockType.WRITE);

        report(event, dryRun, retypedGames, undeterminedGames, retypedPlayers[0]);
    }

    /**
     * A player still sitting on a Keleres homeworld names their own flavour, which is the only
     * signal that survives a game holding more than one legacy player.
     */
    static String factionFromTheirHomePlanets(Player player) {
        Set<String> factions = player.getPlanets().stream()
                .map(KELERES_FACTION_BY_HOME_PLANET::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return factions.size() == 1 ? factions.iterator().next() : null;
    }

    /**
     * Only one Council Keleres is ever at a table, so a single Keleres home system on the board
     * says which flavour it was even after they lost the planets in it.
     */
    static String factionFromTheOnlyKeleresHomeOnTheBoard(Game game) {
        Set<String> factions = game.getTileMap().values().stream()
                .map(Tile::getTileID)
                .map(KELERES_FACTION_BY_KELERES_TILE::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return factions.size() == 1 ? factions.iterator().next() : null;
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

    /** Enough of the player to work out from a dry run why nothing placed them. */
    private static String describeUndetermined(Game game, Player player) {
        String position = StringUtils.defaultIfBlank(player.getHomeSystemPosition(), null);
        if (position == null) {
            position = StringUtils.defaultIfBlank(player.getPlayerStatsAnchorPosition(), null);
        }
        Tile tile = position == null ? null : game.getTileByPosition(position);
        List<String> planets = player.getPlanets().stream().limit(8).toList();
        return game.getName() + " (" + player.getUserName() + ") pos=" + (position == null ? "-" : position)
                + " tile=" + (tile == null ? "-" : tile.getTileID())
                + " tiles=" + game.getTileMap().size()
                + " planets=" + (planets.isEmpty() ? "-" : String.join(",", planets));
    }

    private static void report(
            SlashCommandInteractionEvent event,
            boolean dryRun,
            List<String> retypedGames,
            List<String> undeterminedGames,
            int retypedPlayers) {
        StringBuilder message = new StringBuilder();
        message.append(dryRun ? "Would retype " : "Retyped ")
                .append(retypedPlayers)
                .append(" legacy Keleres player(s) across ")
                .append(retypedGames.size())
                .append(" game(s), out of ")
                .append(GameManager.getGameCount())
                .append(" games.\n");
        if (undeterminedGames.isEmpty()) {
            message.append("Every legacy Keleres player was placed.");
        } else {
            message.append("**Could not be determined (")
                    .append(undeterminedGames.size())
                    .append("):**\n- ")
                    .append(String.join("\n- ", undeterminedGames));
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message.toString());

        BotLogger.info((dryRun ? "[DRY RUN] Would retype " : "Retyped ") + retypedPlayers
                + " legacy Keleres player(s) across " + retypedGames.size() + " games: "
                + String.join(", ", retypedGames)
                + (undeterminedGames.isEmpty() ? "" : " | Undetermined: " + String.join(", ", undeterminedGames)));
    }
}
