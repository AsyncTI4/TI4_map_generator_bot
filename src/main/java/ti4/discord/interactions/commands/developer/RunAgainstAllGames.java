package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import ti4.game.UnitHolder;
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
                "Retyping legacy Keleres players across all games"
                        + (dryRun ? " (dry run, nothing will be saved)." : "."));

        List<String> retypedGames = new ArrayList<>();
        List<String> undeterminedGames = new ArrayList<>();
        int[] retypedPlayers = {0};
        int[] restoredPositions = {0};

        ConsumeGameUtility.consumeAllGames(
                game -> {
                    List<Player> legacyPlayers = game.getPlayers().values().stream()
                            .filter(player -> LEGACY_KELERES_FACTION.equalsIgnoreCase(player.getFaction()))
                            .toList();
                    if (legacyPlayers.isEmpty()) {
                        return;
                    }

                    String boardFaction = factionFromTheOnlyKeleresHomeOnTheBoard(game);
                    String orphanedBaseFaction = factionFromAnOrphanedBaseHome(game);
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
                            faction = orphanedBaseFaction;
                        }
                        if (faction == null) {
                            undeterminedGames.add(describeUndetermined(game, player));
                            continue;
                        }
                        String homePosition = homeSystemPositionFor(game, faction);
                        if (homePosition != null) {
                            restoredPositions[0]++;
                        }
                        if (!dryRun) {
                            player.setFaction(faction);
                            if (homePosition != null) {
                                player.setHomeSystemPosition(homePosition);
                            }
                        }
                        retyped.add(faction + (homePosition == null ? "" : "@" + homePosition));
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

        report(event, dryRun, retypedGames, undeterminedGames, retypedPlayers[0], restoredPositions[0]);
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

    /**
     * The last resort, for a player who lost their home system and has no position left to name it.
     * A borrowed home system in a game its own faction is not playing can only be the Keleres seat,
     * whether it is still a tile on the board or only survives as planets in a conqueror's hands -
     * the oldest games no longer carry the tile at all.
     */
    static String factionFromAnOrphanedBaseHome(Game game) {
        Set<String> factionsAtTheTable = game.getPlayers().values().stream()
                .map(Player::getFaction)
                .filter(StringUtils::isNotBlank)
                .map(faction -> faction.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        Set<String> candidates = Stream.concat(
                        game.getTileMap().values().stream().map(Tile::getTileID).map(KELERES_FACTION_BY_OWN_TILE::get),
                        game.getPlayers().values().stream()
                                .flatMap(player -> player.getPlanets().stream())
                                .map(KELERES_FACTION_BY_HOME_PLANET::get))
                .filter(Objects::nonNull)
                .filter(keleresFaction -> !isAtTheTable(factionsAtTheTable, BORROWED_FROM.get(keleresFaction)))
                .collect(Collectors.toSet());
        return candidates.size() == 1 ? candidates.iterator().next() : null;
    }

    private static boolean isAtTheTable(Set<String> factionsAtTheTable, String baseFaction) {
        return factionsAtTheTable.stream()
                .anyMatch(faction -> faction.equals(baseFaction) || faction.endsWith("_" + baseFaction));
    }

    /**
     * Where the flavour's home system is sitting, found by its planets rather than its tile id -
     * these games carry it as 02, 14 or 58 as often as the re-skinned 93new or 94new. Null when the
     * board no longer holds the tile at all, which is half of them.
     */
    static String homeSystemPositionFor(Game game, String keleresFaction) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .map(UnitHolder::getName)
                        .map(KELERES_FACTION_BY_HOME_PLANET::get)
                        .anyMatch(keleresFaction::equals))
                .map(Tile::getPosition)
                .filter(StringUtils::isNotBlank)
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
        String factions = game.getPlayers().values().stream()
                .map(Player::getFaction)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(","));
        return game.getName() + " (" + player.getUserName() + ") pos=" + (position == null ? "-" : position)
                + " tile=" + (tile == null ? "-" : tile.getTileID())
                + " tiles=" + game.getTileMap().size()
                + " planets=" + (planets.isEmpty() ? "-" : String.join(",", planets))
                + " factions=" + factions;
    }

    private static void report(
            SlashCommandInteractionEvent event,
            boolean dryRun,
            List<String> retypedGames,
            List<String> undeterminedGames,
            int retypedPlayers,
            int restoredPositions) {
        StringBuilder message = new StringBuilder();
        message.append(dryRun ? "Would retype " : "Retyped ")
                .append(retypedPlayers)
                .append(" legacy Keleres player(s) across ")
                .append(retypedGames.size())
                .append(" game(s), out of ")
                .append(GameManager.getGameCount())
                .append(" games, ")
                .append(restoredPositions)
                .append(" of them with a home system position to restore.\n");
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
