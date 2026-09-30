package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Ponthous;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.combat.CombatRollService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
public class PonthousLeadersHandler {
    private static final String AGENT_ID = "ponthousagent";
    private static final String USE_AGENT_PREFIX = "ponthousagent_";
    private static final String SELECT_TARGET_PREFIX = "ponthousAgentTarget_";
    private static final String HERO_PLANET = "selectPonthousHeroPlanet_";
    private static final String HERO_SYSTEM = "selectPonthousHeroOpponentSystem_";

    public static Button getPonthousAgentButton(Player player, Tile tile) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_AGENT_PREFIX + tile.getPosition(),
                "Use Ponthous Agent",
                FactionEmojis.ponthous);
    }

    @ButtonHandler(USE_AGENT_PREFIX)
    public static void startPonthousAgent(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.substring(USE_AGENT_PREFIX.length());
        if (game == null
                || player == null
                || !player.hasUnexhaustedLeader(AGENT_ID)
                || game.getTileByPosition(position) == null) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        List<Button> buttons = game.getRealPlayers().stream()
                .map(target -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_TARGET_PREFIX + position + "_" + target.getFaction(),
                        target.getColorDisplayName(),
                        target.fogSafeEmoji()))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose the player on whom to use General Caelyn, the Ponthous agent.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_TARGET_PREFIX)
    public static void resolvePonthousAgent(
            ButtonInteractionEvent event, Game game, Player agentOwner, String buttonID) {
        String[] parts = buttonID.substring(SELECT_TARGET_PREFIX.length()).split("_", 2);
        if (parts.length != 2 || game == null || agentOwner == null || !agentOwner.hasUnexhaustedLeader(AGENT_ID)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        Player target = game.getPlayerFromColorOrFaction(parts[1]);
        Leader agent = agentOwner.getLeader(AGENT_ID).orElse(null);
        if (tile == null || target == null || agent == null) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                agentOwner.getRepresentation() + " exhausted General Caelyn, the Ponthous agent, on "
                        + target.getRepresentation() + ".");

        AddUnitService.addUnits(event, tile, game, target.getColor(), "2 fighter, 2 infantry");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Placed 2 fighters and 2 infantry for "
                        + target.getRepresentationUnfogged()
                        + " in the space area of "
                        + tile.getRepresentationForButtons(game, target)
                        + ".");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    // Hero
    public static void startPonthousHero(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        List<Button> planets = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planetName == null) continue;
            if (planet.isLegendary() || planet.isHomePlanet()) {
                continue;
            }

            planets.add(Buttons.green(
                    player.factionButtonChecker() + HERO_PLANET + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", please choose the planet to use for _Star-Sacrifice_.",
                planets);
    }

    @ButtonHandler(HERO_PLANET)
    public static void ponthousHeroStep2(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String planetName = buttonID.replace(HERO_PLANET, "");
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null) {
            return;
        }

        DestroyUnitService.destroyAllUnits(event, tile, game, planet, false);
        game.removePlanet(planet);
        planet.removeAllTokens();
        planet.addToken(Constants.THEODISI_WORLD_DESTROYED_PNG);

        int h = planet.getResources() + planet.getInfluence();

        List<Button> buttons = new ArrayList<>();

        if (tile == null) {
            return;
        }

        for (String adjacentPos : FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false)) {
            Tile adjacentTile = game.getTileByPosition(adjacentPos);
            if (adjacentTile == null
                    || adjacentTile.getTileModel().isHyperlane()
                    || !FoWHelper.otherPlayersHaveShipsInSystem(player, adjacentTile, game)
                    || adjacentTile.isHomeSystem()) {
                continue;
            }

            buttons.add(Buttons.red(
                    player.factionButtonChecker() + HERO_SYSTEM + adjacentPos + "|" + h,
                    adjacentTile.getRepresentationForButtons(game, player)));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Destroyed all units on " + planet.getRepresentation(game)
                        + ", purged all its attachments, and its planet card. Please choose the adjacent system in which to produce "
                        + h + " hit" + (h == 1 ? "" : "s") + ".",
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(HERO_SYSTEM)
    public static void resolvePonthousHero(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String[] payload = buttonID.substring(HERO_SYSTEM.length()).split("\\|", 2);
        if (payload.length != 2) {
            return;
        }
        String adjacentPos = payload[0];
        String h = payload[1];

        Tile adjacentTile = game.getTileByPosition(adjacentPos);
        int hits = h == null ? 0 : Integer.parseInt(h);
        if (adjacentTile == null || hits == 0) {
            return;
        }

        Player opponent = CombatRollService.getOpponent(player, List.of(adjacentTile.getSpaceUnitHolder()), game);
        if (opponent == null) {
            MessageHelper.sendMessageToEventChannel(event, "No opposing ships were found in the selected system.");
            return;
        }

        List<Button> hitButtons = new ArrayList<>();

        hitButtons.add(Buttons.red(
                opponent.factionButtonChecker() + "getDamageButtons_" + adjacentTile.getPosition()
                        + "deleteThis_spacecombat",
                "Manually Assign " + hits + " Hit" + (hits == 1 ? "" : "s")));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                opponent.getRepresentationUnfogged() + ", please assign " + hits + " produced hit"
                        + (hits == 1 ? "" : "s") + ". These hits are produced against your non-fighter ships.",
                hitButtons);

        ButtonHelper.deleteMessage(event);
    }
}
