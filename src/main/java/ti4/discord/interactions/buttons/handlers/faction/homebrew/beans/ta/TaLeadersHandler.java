package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
public class TaLeadersHandler {
    private static final String COMMANDER_CONVERT_PREFIX = "taCommanderConvert_";
    private static final String HERO_ATTACH_PREFIX = "taHeroAttachGrand_";

    public static void resolveTaCommander(Player player, Tile tile, Game game, String planetName) {
        if (tile == null || player == null) {
            return;
        }

        Planet planet = tile.getUnitHolderFromPlanet(planetName);
        player.gainCommodities(1);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                "Gained 1 "
                        + MiscEmojis.comm
                        + " from exploring "
                        + Helper.getPlanetRepresentationPlusEmoji(planetName)
                        + " due to _Zul_, the Ta Commander.");
        if (planet.hasAttachment()
                || TaAbilityHandler.planetHasAnyDesignAttached(tile, planetName) && player.getCommodities() > 0) {
            List<Button> buttons = List.of(Buttons.green(
                    player.factionButtonChecker() + COMMANDER_CONVERT_PREFIX + tile.getPosition() + "|" + planetName,
                    "Convert Commodity to Trade Good",
                    MiscEmojis.comm));

            String typeOfAttachment =
                    (TaAbilityHandler.planetHasAnyDesignAttached(tile, planetName) ? "a design, " : "an attachment, ");

            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", because "
                            + Helper.getPlanetRepresentation(planetName, game)
                            + " has "
                            + typeOfAttachment
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
                || !(planet.hasAttachment() || TaAbilityHandler.planetHasAnyDesignAttached(tile, planetName))) {
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
                        + " due to _Zul_, the Ta Commander.");
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
            if (!player.getPlanetsAllianceMode().contains(planetName)) {
                continue;
            }
            if (TaAbilityHandler.planetHasAnyDesignAttached(hs, planetName)) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + HERO_ATTACH_PREFIX + hs.getPosition() + "|" + planetName,
                    "Attach Grand Design (Pinnacle) to " + Helper.getPlanetRepresentation(planetName, game)));
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged() + ", there are no eligible home planets for _Zat_.");
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

        if (!player.getPlanetsAllianceMode().contains(planetName)
                || player.getHomeSystemTile() == null
                || !tile.getPosition().equals(player.getHomeSystemTile().getPosition())
                || TaAbilityHandler.planetHasAnyDesignAttached(tile, planetName)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", that planet is no longer eligible for _Zat_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tokenPath = Mapper.getAttachmentImagePath("designgrand");
        if (tokenPath != null) {
            tile.addToken(tokenPath, planetName);
            TaUnitHandler.offerTaMechDeploy(event, player, game, tile, planetName);
        }

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation()
                        + " used _Zat_ to attach _Grand Design (Pinnacle)_ to "
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

            String message =
                    player.getRepresentationUnfogged() + " readied the following planets due to _Zat_: " + readiedList;

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
                    "You may explore " + Helper.getPlanetRepresentation(planetName, game) + " due to _Zat_.",
                    exploreButtons);
        }
    }
}
