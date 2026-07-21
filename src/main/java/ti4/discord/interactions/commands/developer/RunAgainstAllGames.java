package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.helpers.AliasHandler;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;

class RunAgainstAllGames extends Subcommand {

    // The 5 removed Eronous factions and their component ids; the models no longer exist, so ids are hardcoded here.
    private static final Set<String> ERONOUS_FACTIONS = Set.of("canto", "eidolon", "shadows", "mechi", "saera");
    private static final Map<String, String> ERONOUS_HOME_TILES =
            Map.of("canto", "as01", "eidolon", "as02", "shadows", "as03", "mechi", "as04", "saera", "as05");
    private static final Set<String> ERONOUS_PLANETS = Set.of(
            "thyolcian", "voyd", "etyr", "ecconv", "tyriaprime", "akredrite", "meccna", "gaia", "gensis", "aeva");
    private static final Set<String> ERONOUS_TECHS = Set.of(
            "cantoy",
            "cantor",
            "eidolonff",
            "eidolonb",
            "shadowssd",
            "shadowsy",
            "mechig",
            "mechiy",
            "saeracr",
            "saeray");
    private static final Set<String> ERONOUS_PNS = Set.of("cantopn", "eidolonpn", "shadowspn", "mechipn", "saerapn");
    private static final Set<String> ERONOUS_LEADERS = Set.of(
            "cantoagent",
            "cantocommander",
            "cantohero",
            "eidolonagent",
            "eidoloncommander",
            "eidolonhero",
            "shadowsagent",
            "shadowscommander",
            "shadowshero",
            "mechiagent",
            "mechicommander",
            "mechihero",
            "saeraagentprosperity",
            "saeraagentwarning",
            "saeraagentprotection",
            "saeracommander",
            "saerahero");
    private static final Set<String> ERONOUS_ABILITIES = Set.of(
            "enslave",
            "dominate",
            "seamless_integration",
            "void_tap",
            "dark_weaver",
            "abyssal_propagation",
            "creeping_shades",
            "silent_growth",
            "tomb_worlds",
            "protocols",
            "machine_cult",
            "protocol_distribution",
            "protocol_command",
            "protocol_excavation",
            "protocol_espionage",
            "protocol_conflict",
            "angelic_hosts",
            "guidance",
            "celestial_being");
    private static final Set<String> ERONOUS_UNITS = Set.of(
            "canto_flagship",
            "canto_mech",
            "eidolon_flagship",
            "eidolon_mech",
            "eidolon_fighter",
            "eidolon_fighter2",
            "shadows_flagship",
            "shadows_mech",
            "shadows_spacedock",
            "shadows_spacedock2",
            "mechi_flagship",
            "mechi_mech",
            "saera_flagship",
            "saera_mech",
            "saera_cruiser",
            "saera_cruiser2");

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        Set<String> changedGames = new HashSet<>();
        ConsumeGameUtility.consumeAllGames(
                game -> {
                    boolean changed = removeEronousFactions(game);
                    if (changed) {
                        changedGames.add(game.getName());
                        GameManager.save(game, "Removed Eronous factions from game state.");
                    }
                },
                ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info("Changes made to " + changedGames.size() + " games out of " + GameManager.getGameCount()
                + " games: " + String.join(", ", changedGames));
    }

    static boolean removeEronousFactions(Game game) {
        boolean changed = false;

        // Swap any player playing a removed Eronous faction to a random official faction
        for (Player player : game.getPlayers().values()) {
            String oldFaction = player.getFaction();
            if (oldFaction == null || !ERONOUS_FACTIONS.contains(oldFaction)) continue;
            changed = true;

            FactionModel replacement = pickReplacementFaction(game);
            if (replacement == null) {
                BotLogger.warning("removeEronousFactions: no replacement faction available in game " + game.getName()
                        + " for player " + player.getUserName());
                continue;
            }

            Tile oldHomeTile = null;
            String oldHomeTileId = ERONOUS_HOME_TILES.get(oldFaction);
            for (Tile tile : game.getTileMap().values()) {
                if (oldHomeTileId.equals(tile.getTileID())) {
                    oldHomeTile = tile;
                    break;
                }
            }

            player.setFaction(game, replacement.getAlias());
            player.setFactionEmoji(null);
            player.setFactionTechs(new ArrayList<>(replacement.getFactionTech()));
            player.setUnitsOwned(new HashSet<>(replacement.getUnits()));

            Set<String> ownedNotes = new HashSet<>(player.getPromissoryNotesOwned());
            ownedNotes.removeAll(ERONOUS_PNS);
            ownedNotes.addAll(replacement.getPromissoryNotes());
            player.setPromissoryNotesOwned(ownedNotes);

            if (oldHomeTile != null) {
                String newHomeTileId = AliasHandler.resolveTile(replacement.getHomeSystem());
                Tile newHomeTile = new Tile(newHomeTileId, oldHomeTile.getPosition(), oldHomeTile.getSpaceUnitHolder());
                game.setTile(newHomeTile);
                for (String planet : replacement.getHomePlanets()) {
                    String planetId = AliasHandler.resolvePlanet(planet.toLowerCase());
                    if (!player.getPlanets().contains(planetId)) {
                        player.getPlanets().add(planetId);
                    }
                }
            }
            player.setCommoditiesBase(replacement.getCommodities());
            int commoditiesTotal = player.getCommoditiesTotal();
            if (player.getCommodities() > commoditiesTotal) {
                player.setCommodities(commoditiesTotal);
            }

            BotLogger.info("removeEronousFactions: in game " + game.getName() + ", swapped " + player.getUserName()
                    + " from " + oldFaction + " to " + replacement.getAlias());
        }

        // Scrub stray Eronous component ids from every player (covers franken drafts and traded PNs)
        for (Player player : game.getPlayers().values()) {
            changed |= player.getLeaders().removeIf(leader -> ERONOUS_LEADERS.contains(leader.getId()));
            changed |= player.getAbilities().removeAll(ERONOUS_ABILITIES);
            changed |= player.getExhaustedAbilities().removeAll(ERONOUS_ABILITIES);
            // direct list ops on purpose: removeTech() side effects can NPE on ids with no model
            changed |= player.getTechs().removeAll(ERONOUS_TECHS);
            changed |= player.getExhaustedTechs().removeAll(ERONOUS_TECHS);
            changed |= player.getPurgedTechs().removeAll(ERONOUS_TECHS);
            changed |= player.getFactionTechs().removeAll(ERONOUS_TECHS);
            changed |= player.getUnitsOwned().removeAll(ERONOUS_UNITS);
            changed |= player.getPromissoryNotesOwned().removeAll(ERONOUS_PNS);
            changed |= player.getPromissoryNotesInPlayArea().removeAll(ERONOUS_PNS);
            for (String pn : ERONOUS_PNS) {
                if (player.getPromissoryNotes().containsKey(pn)) {
                    player.removePromissoryNote(pn);
                    changed = true;
                }
            }
            changed |= player.getPlanets().removeAll(ERONOUS_PLANETS);
            changed |= player.getExhaustedPlanets().removeAll(ERONOUS_PLANETS);
            changed |= player.getExhaustedPlanetsAbilities().removeAll(ERONOUS_PLANETS);
        }

        changed |= game.getPurgedPN().removeAll(ERONOUS_PNS);

        // Remove any Eronous home system tiles left on the map (e.g. from an abandoned setup)
        for (Tile tile : new ArrayList<>(game.getTileMap().values())) {
            if (ERONOUS_HOME_TILES.containsValue(tile.getTileID())) {
                game.removeTile(tile.getPosition());
                changed = true;
            }
        }

        return changed;
    }

    private static FactionModel pickReplacementFaction(Game game) {
        List<FactionModel> pool = Mapper.getFactionsValues().stream()
                .filter(f -> f.getSource().isOfficial())
                .filter(f -> game.isTwilightsFallMode() == (f.getSource() == ComponentSource.twilights_fall))
                .filter(f -> !"neutral".equals(f.getAlias()) && !"keleres".equals(f.getAlias()))
                .filter(f -> game.getPlayerFromColorOrFaction(f.getAlias()) == null)
                .filter(f -> game.getTile(AliasHandler.resolveTile(f.getHomeSystem())) == null)
                .collect(Collectors.toCollection(ArrayList::new));
        if (pool.isEmpty()) return null;
        Collections.shuffle(pool);
        return pool.getFirst();
    }
}
