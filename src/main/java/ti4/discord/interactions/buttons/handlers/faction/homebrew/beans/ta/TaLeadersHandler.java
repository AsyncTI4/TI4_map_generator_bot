package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.emoji.MiscEmojis;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
public class TaLeadersHandler {
    private static final String COMMANDER_CONVERT_PREFIX = "taCommanderConvert_";
    private static final String HERO_ATTACH_PREFIX = "taHeroAttachGrand_";
    private static final String AGENT_CHOOSE_TRAIT_PREFIX = "taAgentChooseTrait_";
    private static final String AGENT_RESOLVE_TRAIT_PREFIX = "taAgentResolveTrait_";

    public static void resolveTaAgentTarget(Game game, Player target) {
        if (game == null || target == null) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String planetName : target.getPlanets()) {
            Tile tile = game.getTileFromPlanet(planetName);
            Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(planetName);
            if (planet != null && !planet.getPlanetTypes().isEmpty()) {
                buttons.add(Buttons.green(
                        target.factionButtonChecker() + AGENT_CHOOSE_TRAIT_PREFIX + planetName,
                        Helper.getPlanetRepresentation(planetName, game)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    target.getRepresentationUnfogged() + " has no explorable planets for Len, the Ta agent.");
            return;
        }
        String message = target.getRepresentationUnfogged() + ", choose a planet for Len, the Ta agent.";
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                message,
                NewStuffHelper.buttonPagination(buttons, target.factionButtonChecker() + AGENT_CHOOSE_TRAIT_PREFIX, 0));
    }

    @ButtonHandler(AGENT_CHOOSE_TRAIT_PREFIX)
    public static void chooseTaAgentTrait(Game game, Player target, ButtonInteractionEvent event, String buttonID) {
        if (game == null || target == null) {
            return;
        }
        List<Button> planetButtons = new ArrayList<>();
        for (String controlledPlanet : target.getPlanets()) {
            Tile controlledTile = game.getTileFromPlanet(controlledPlanet);
            Planet controlledPlanetHolder =
                    controlledTile == null ? null : controlledTile.getUnitHolderFromPlanet(controlledPlanet);
            if (controlledPlanetHolder != null
                    && !controlledPlanetHolder.getPlanetTypes().isEmpty()) {
                planetButtons.add(Buttons.green(
                        target.factionButtonChecker() + AGENT_CHOOSE_TRAIT_PREFIX + controlledPlanet,
                        Helper.getPlanetRepresentation(controlledPlanet, game)));
            }
        }
        String message = target.getRepresentationUnfogged() + ", choose a planet for Len, the Ta agent.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                target.getCorrectChannel(),
                planetButtons,
                message,
                target.factionButtonChecker() + AGENT_CHOOSE_TRAIT_PREFIX,
                buttonID)) {
            return;
        }
        String planetName = buttonID.substring(AGENT_CHOOSE_TRAIT_PREFIX.length());
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(planetName);
        if (planet == null || !target.getPlanets().contains(planetName)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String trait : planet.getPlanetTypes()) {
            buttons.add(Buttons.green(
                    target.factionButtonChecker() + AGENT_RESOLVE_TRAIT_PREFIX + planetName + "|" + trait,
                    StringUtils.capitalize(trait)));
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged() + ", choose the matching exploration deck for Len, the Ta agent.",
                buttons);
    }

    @ButtonHandler(AGENT_RESOLVE_TRAIT_PREFIX)
    public static void resolveTaAgent(Game game, Player target, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.substring(AGENT_RESOLVE_TRAIT_PREFIX.length()).split("\\|", 2);
        if (game == null || target == null || parts.length != 2) {
            return;
        }
        Tile tile = game.getTileFromPlanet(parts[0]);
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(parts[0]);
        if (planet == null
                || !target.getPlanets().contains(parts[0])
                || !planet.getPlanetTypes().contains(parts[1])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String cardID = game.drawExplore(parts[1]);
        ExploreModel explore = Mapper.getExplore(cardID);
        String revealedCard = explore == null ? "an unknown card" : "_" + explore.getName() + "_";
        if (explore == null) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    target.getRepresentationUnfogged() + " revealed " + revealedCard + " with Len, the Ta agent.");
        } else {
            MessageHelper.sendMessageToChannelWithEmbeds(
                    target.getCorrectChannel(),
                    target.getRepresentationUnfogged() + " revealed " + revealedCard + " with Len, the Ta agent.",
                    List.of(explore.getRepresentationEmbed()));
        }
        if (explore != null && explore.getAttachmentId().isPresent()) {
            String attachment = explore.getAttachmentId().get();
            String attachmentPath = Mapper.getAttachmentImagePath(attachment);
            if (attachmentPath != null) {
                tile.addToken(attachmentPath, parts[0]);
                game.purgeExplore(cardID);
                TaUnitHandler.offerTaMechDeploy(event, target, game, tile, parts[0]);
                CommanderUnlockCheckService.checkPlayer(target, "ta");
                MessageHelper.sendMessageToChannel(
                        target.getCorrectChannel(),
                        target.getRepresentationUnfogged() + " attached _"
                                + Mapper.getAttachmentInfo(attachment).getName() + "_ to "
                                + Helper.getPlanetRepresentation(parts[0], game) + " with Len, the Ta agent.");
            } else {
                game.discardExplore(cardID);
            }
        } else {
            if (cardID != null) {
                game.discardExplore(cardID);
            }
            target.gainTG(2);
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    target.getRepresentationUnfogged() + " discarded it and gained 2 " + MiscEmojis.tg
                            + " with Len, the Ta agent.");
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveTaCommander(Player player, Tile tile, Game game, String planetName) {
        if (tile == null || player == null || game == null) {
            return;
        }

        Planet planet = tile.getUnitHolderFromPlanet(planetName);
        if (planet == null) {
            return;
        }
        int commoditiesBefore = player.getCommodities();
        player.gainCommodities(1);
        int commoditiesGained = player.getCommodities() - commoditiesBefore;
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " gained "
                        + commoditiesGained
                        + " "
                        + MiscEmojis.comm
                        + " from exploring "
                        + Helper.getPlanetRepresentationPlusEmoji(planetName)
                        + " due to Zul, the Ta commander.");
        if (TaAbilityHandler.planetHasAnyAttachment(tile, planetName) && commoditiesGained > 0) {
            List<Button> buttons = new ArrayList<>(List.of(Buttons.green(
                    player.factionButtonChecker() + COMMANDER_CONVERT_PREFIX + tile.getPosition() + "|" + planetName,
                    "Convert Commodity to Trade Good",
                    MiscEmojis.comm)));
            buttons.add(Buttons.red("deleteButtons", "Decline"));

            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", because "
                            + Helper.getPlanetRepresentation(planetName, game)
                            + " has an attachment, "
                            + "you may convert the recently gained commodity to a trade good.",
                    buttons);
        }
    }

    @ButtonHandler(COMMANDER_CONVERT_PREFIX)
    public static void resolveTaCommanderConvert(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null || !buttonID.startsWith(COMMANDER_CONVERT_PREFIX)) {
            return;
        }

        String payload = buttonID.substring(COMMANDER_CONVERT_PREFIX.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tilePosition = parts[0];
        String planetName = parts[1];
        Tile tile = game.getTileByPosition(tilePosition);
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(planetName);

        if (tile == null
                || planet == null
                || player.getCommodities() < 1
                || !game.playerHasLeaderUnlockedOrAlliance(player, "tacommander")
                || !TaAbilityHandler.planetHasAnyAttachment(tile, planetName)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.setCommodities(player.getCommodities() - 1);
        player.gainTG(1);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " converted 1 "
                        + MiscEmojis.comm
                        + " to 1 "
                        + MiscEmojis.tg
                        + " due to Zul, the Ta commander.");
        ButtonHelper.deleteMessage(event);
    }

    public static void postHeroButtons(Game game, Player player, GenericInteractionCreateEvent event) {
        if (game == null || player == null || event == null) {
            return;
        }

        Tile hs = player.getHomeSystemTile();
        if (hs == null) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Planet planet : hs.getPlanetUnitHolders()) {
            String planetName = planet.getName();
            if (!player.getPlanets().contains(planetName)) {
                continue;
            }
            if (TaAbilityHandler.planetHasGrandDesignAttached(hs, planetName)) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + HERO_ATTACH_PREFIX + hs.getPosition() + "|" + planetName,
                    "Attach Grand Design (Pinnacle) to " + Helper.getPlanetRepresentation(planetName, game)));
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged() + ", there are no eligible home planets for Zat, the Ta hero.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", choose a planet in your home system to attach _Grand Design (Pinnacle)_ to.",
                buttons);
    }

    @ButtonHandler(HERO_ATTACH_PREFIX)
    public static void resolveTaHeroAttach(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null || !buttonID.startsWith(HERO_ATTACH_PREFIX)) {
            return;
        }

        String payload = buttonID.substring(HERO_ATTACH_PREFIX.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String tilePosition = parts[0];
        String planetName = parts[1];

        Tile tile = game.getTileByPosition(tilePosition);
        if (tile == null) {
            return;
        }

        Planet planet = tile.getUnitHolderFromPlanet(planetName);
        if (planet == null) {
            return;
        }

        if (!player.getPlanets().contains(planetName)
                || player.getHomeSystemTile() == null
                || !tile.getPosition().equals(player.getHomeSystemTile().getPosition())
                || TaAbilityHandler.planetHasGrandDesignAttached(tile, planetName)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", that planet is no longer eligible for Zat, the Ta hero.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tokenPath = Mapper.getAttachmentImagePath("designgrand");
        if (tokenPath == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not find the _Grand Design (Pinnacle)_ attachment.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        tile.addToken(tokenPath, planetName);
        TaUnitHandler.offerTaMechDeploy(event, player, game, tile, planetName);
        CommanderUnlockCheckService.checkPlayer(player, "ta");

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation()
                        + " used Zat, the Ta hero to attach _Grand Design (Pinnacle)_ to "
                        + planet.getRepresentation(game));
        ButtonHelper.deleteMessage(event);

        resolveTaHeroFollowup(event, player, game);
    }

    public static void resolveTaHeroFollowup(GenericInteractionCreateEvent event, Player player, Game game) {
        if (player == null || game == null || event == null) {
            return;
        }

        List<String> designPlanets = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Tile tile = game.getTileFromPlanet(planetName);
            if (tile == null) {
                continue;
            }

            if (TaAbilityHandler.planetHasAnyDesignAttached(tile, planetName)) {
                designPlanets.add(planetName);
            }
        }

        if (designPlanets.isEmpty()) {
            return;
        }

        List<String> readiedPlanets = new ArrayList<>();
        for (String planetName : designPlanets) {
            if (!player.getExhaustedPlanets().contains(planetName)) {
                continue;
            }

            player.refreshPlanet(planetName);
            readiedPlanets.add(planetName);
        }

        if (!readiedPlanets.isEmpty()) {
            StringBuilder readiedList = new StringBuilder();
            for (int i = 0; i < readiedPlanets.size(); i++) {
                String planetName = readiedPlanets.get(i);
                readiedList.append(Helper.getPlanetRepresentation(planetName, game));

                if (i < readiedPlanets.size() - 1) {
                    readiedList.append(", ");
                }
            }

            String message = player.getRepresentationUnfogged()
                    + " readied the following planets due to Zat, the Ta hero: " + readiedList;

            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        }

        for (String planetName : designPlanets) {
            Tile tile = game.getTileFromPlanet(planetName);
            Planet planet = game.getPlanetsInfo().get(planetName);
            if (tile == null || planet == null) {
                continue;
            }

            List<Button> exploreButtons = ButtonHelper.getPlanetExplorationButtons(game, planet, player);
            if (exploreButtons == null || exploreButtons.isEmpty()) {
                continue;
            }

            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + ", you may explore "
                            + Helper.getPlanetRepresentation(planetName, game) + " due to Zat, the Ta hero.",
                    exploreButtons);
        }
    }
}
