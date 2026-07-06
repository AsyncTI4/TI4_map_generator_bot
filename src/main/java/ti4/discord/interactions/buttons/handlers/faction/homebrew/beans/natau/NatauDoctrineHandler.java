package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.natau;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.model.TechSpecialtyModel.TechSpecialty;
import ti4.model.TechnologyModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.planet.PlanetService;
import ti4.service.tech.ListTechService;

@UtilityClass
public class NatauDoctrineHandler {

    // Discovery
    private static final String DISCOVERY = "doctrine_discovery";
    private static final String USE_DISCOVERY = "natauUseDiscovery";
    private static final String CHOOSE_DISCOVERY_TILE = "natauChooseDiscoveryTile_";
    // Knowledge
    private static final String KNOWLEDGE = "doctrine_knowledge";
    private static final String USE_KNOWLEDGE = "natauUseKnowledge";
    private static final String SELECT_KNOWLEDGE_PLANET = "natauKnowledgeTarget_";

    // Discovery
    public static boolean hasDiscovery(Player player) {
        return player != null && player.hasAbility(DISCOVERY);
    }

    public static boolean canUseDiscoveryExhaust(Player player) {
        return hasDiscovery(player) && !player.getExhaustedAbilities().contains(DISCOVERY);
    }

    public static Button getUseDiscoveryButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + USE_DISCOVERY, "Use Discovery", FactionEmojis.natau);
    }

    @ButtonHandler(USE_DISCOVERY)
    public static void offerDiscoveryTileButtons(ButtonInteractionEvent event, Game game, Player player) {
        if (event == null || game == null || player == null || !canUseDiscoveryExhaust(player)) {
            return;
        }

        List<Tile> eligibleTiles = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (!tile.getPlanetUnitHolders().isEmpty()) {
                continue;
            }
            if (!FoWHelper.playerHasShipsInSystem(player, tile)) {
                continue;
            }
            eligibleTiles.add(tile);
        }

        if (eligibleTiles.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There are no planetless system that contain your ships.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Tile tile : eligibleTiles) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + CHOOSE_DISCOVERY_TILE + tile.getPosition(),
                    tile.getRepresentationForButtons()));
        }

        player.addExhaustedAbility(DISCOVERY);

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", please choose which planetless system containing your ships to explore using _Discovery_:",
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CHOOSE_DISCOVERY_TILE)
    public static void resolveDiscoveryTileChoice(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null || !hasDiscovery(player)) {
            return;
        }

        String pos = buttonID.replace(CHOOSE_DISCOVERY_TILE, "");
        Tile tile = game.getTileByPosition(pos);

        if (tile == null || !tile.getPlanetUnitHolders().isEmpty() || !FoWHelper.playerHasShipsInSystem(player, tile)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "That system is no longer eligible for _Discovery_.");
            return;
        }

        player.addExhaustedAbility(DISCOVERY);
        ExploreService.expFront(event, tile, game, player, true, null);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("natauDiscovery_")
    public static void resolveNatauDiscovery(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        if (buttonID.contains("Decline")) {
            String drawColor = buttonID.split("_")[2];
            String cardID = buttonID.split("_")[3];
            String planetName = buttonID.split("_")[4];
            Tile tile = game.getTileFromPlanet(planetName);
            if (tile == null) return;
            String messageText = player.getRepresentation() + " explored the planet "
                    + ExploreEmojis.getTraitEmoji(drawColor)
                    + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + " in tile "
                    + tile.getPosition() + ":";
            ExploreService.resolveExplore(event, cardID, tile, planetName, messageText, player, game);
            if (game.playerHasLeaderUnlockedOrAlliance(player, "florzencommander")
                    && game.getPhaseOfGame().contains("agenda")) {
                PlanetService.refreshPlanet(player, planetName);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        "Planet has been readied because of Quaxdol Junitas, the Florzen Commander.");
                if (!game.isFowMode()) AgendaHelper.listVoteCount(game, game.getMainGameChannel());
            }
            if (game.playerHasLeaderUnlockedOrAlliance(player, "lanefircommander")) {
                UnitKey infKey = Mapper.getUnitKey("gf", player.getColor());
                tile.getUnitHolders().get(planetName).addUnit(infKey, 1);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        "Added 1 infantry to planet because of Master Halbert, the Lanefir Commander.");
            }
            if (player.hasTech("dslaner")) {
                player.setAtsCount(player.getAtsCount() + 1);
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(), player.getRepresentation() + " put 1 commodity on _ATS Armaments_.");
            }
        } else {
            int oldTg = player.getTg();
            player.setTg(oldTg + 1);
            ButtonHelperAbilities.pillageCheck(player, game);
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation()
                            + " used _Discovery_ to decline exploration and gained 1 trade good (trade goods went from "
                            + oldTg + "->" + player.getTg() + ").");
            String planetID = buttonID.split("_")[2];
            if (player.hasAbility("awaken")
                    && !game.getAllPlanetsWithSleeperTokens().contains(planetID)
                    && player.getPlanets().contains(planetID)
                    && !game.isTwilightsFallMode()) {
                Button placeSleeper = Buttons.green(
                        "putSleeperOnPlanet_" + planetID,
                        "Put Sleeper on " + Helper.getPlanetRepresentation(planetID, game),
                        MiscEmojis.Sleeper);
                Button decline = Buttons.red("deleteButtons", "Decline To Put a Sleeper Down");
                List<Button> buttons = List.of(placeSleeper, decline);
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(), "You may place down some Sleeper tokens if you wish.", buttons);
            }
        }
        ButtonHelper.deleteMessage(event);
    }

    // Knowledge
    public static boolean hasKnowledge(Player player) {
        return player != null && player.hasAbility(KNOWLEDGE);
    }

    public static boolean canUseKnowledgeExhaust(Player player) {
        return hasKnowledge(player) && !player.getExhaustedAbilities().contains(KNOWLEDGE);
    }

    public static Button getUseKnowledgeButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + USE_KNOWLEDGE, "Use Knowledge", FactionEmojis.natau);
    }

    public static List<String> eligibleKnowledgePlanets(Player player) {
        List<String> eligiblePlanets = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            PlanetModel planetModel = Mapper.getPlanet(planetName);
            if (planetModel == null) {
                continue;
            }
            List<TechSpecialty> techSpecialties = planetModel.getTechSpecialties();
            if (techSpecialties == null || techSpecialties.isEmpty()) {
                continue;
            }
            if (!player.hasPlanetReady(planetName)) {
                continue;
            }
            eligiblePlanets.add(planetName);
        }
        return eligiblePlanets;
    }

    @ButtonHandler(USE_KNOWLEDGE)
    public static void useKnowledge(ButtonInteractionEvent event, Game game, Player player) {
        if (event == null || game == null || player == null || !canUseKnowledgeExhaust(player)) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String techPlanets : eligibleKnowledgePlanets(player)) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_KNOWLEDGE_PLANET + techPlanets,
                    Helper.getPlanetRepresentation(techPlanets, game)));
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "You do not have a ready planet with a technology specialty for _Knowledge_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", please choose a tech specialty planet to exhaust for _Knowledge_:",
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_KNOWLEDGE_PLANET)
    public static void resolveSelectKnowledgePlanet(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null || !canUseKnowledgeExhaust(player)) {
            return;
        }

        String planetId = buttonID.replace(SELECT_KNOWLEDGE_PLANET, "");

        if (!player.getPlanets().contains(planetId)) {
            return;
        }
        if (player.getExhaustedPlanets().contains(planetId)) {
            return;
        }
        PlanetModel planetModel = Mapper.getPlanet(planetId);
        if (planetModel == null) {
            return;
        }
        List<TechSpecialty> techSpecialties = planetModel.getTechSpecialties();
        if (techSpecialties == null || techSpecialties.isEmpty()) {
            return;
        }

        player.addExhaustedAbility(KNOWLEDGE);
        player.exhaustPlanet(planetId);

        List<TechnologyModel> techs = new ArrayList<>();
        Set<String> seenTechs = new HashSet<>();

        for (TechSpecialty techSpecialty : techSpecialties) {
            if (techSpecialty != TechSpecialty.BIOTIC
                    && techSpecialty != TechSpecialty.CYBERNETIC
                    && techSpecialty != TechSpecialty.PROPULSION
                    && techSpecialty != TechSpecialty.WARFARE) {
                continue;
            }

            List<TechnologyModel> techsOfType =
                    ListTechService.getAllTechOfAType(game, techSpecialty.toString(), player, false, true);

            for (TechnologyModel tech : techsOfType) {
                if (seenTechs.add(tech.getAlias())) {
                    techs.add(tech);
                }
            }
        }

        List<Button> buttons = ListTechService.getTechButtons(techs, player, "free");

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you can research a tech of the same color as the exhausted planet's specialty:",
                buttons);

        ButtonHelper.deleteMessage(event);
    }
}
