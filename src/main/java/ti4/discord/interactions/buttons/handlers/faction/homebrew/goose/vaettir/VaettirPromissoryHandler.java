package ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.vaettir;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.Mapper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.transaction.SendPromissoryService;

@UtilityClass
public class VaettirPromissoryHandler {
    private static final String RATATOSKRS_GUIDANCE = "aopnvaettir";
    private static final String SELECT_PLANET = "selectRatatoskrsGuidancePlanet_";
    private static final String SELECT_ROOT = "selectRatatoskrsGuidanceRoot_";
    private static final String RESOLVE_EXPLORE = "resolveRatatoskrsGuidanceExplore_";

    public static void offerRatatoskrsGuidance(Game game, Player player) {
        List<Button> buttons = player.getPlanets().stream()
                .filter(planet -> game.getUnitHolderFromPlanet(planet) != null)
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + SELECT_PLANET + planet,
                        Helper.getPlanetRepresentation(planet, game),
                        FactionEmojis.vaettir))
                .toList();
        if (buttons.isEmpty()) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", please choose a planet for _Ratatoskr's Guidance_.",
                buttons);
    }

    public static void returnRatatoskrsGuidance(Game game) {
        Player owner = game.getPNOwner(RATATOSKRS_GUIDANCE);
        if (owner == null) {
            return;
        }
        for (Player holder : game.getRealPlayers()) {
            if (holder != owner && holder.getPromissoryNotesInPlayArea().contains(RATATOSKRS_GUIDANCE)) {
                SendPromissoryService.returnPromissoryFromPlayAreaToOwner(
                        null, game, holder, owner, RATATOSKRS_GUIDANCE);
            }
        }
    }

    @ButtonHandler(SELECT_PLANET)
    public static void selectPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonId) {
        String planetId = buttonId.substring(SELECT_PLANET.length());
        Planet planet = game.getUnitHolderFromPlanet(planetId);
        if (!player.getPromissoryNotesInPlayArea().contains(RATATOSKRS_GUIDANCE)
                || planet == null
                || !player.getPlanets().contains(planetId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = VaettirAbilityHandler.getRootTypes().stream()
                .filter(rootType -> VaettirAbilityHandler.isRootTokenAvailable(game, rootType))
                .filter(rootType -> !planet.getTokenList().contains("attachment_root_" + rootType + ".png"))
                .map(rootType -> Buttons.green(
                        player.factionButtonChecker() + SELECT_ROOT + planetId + "|" + rootType,
                        "Add " + rootType + " Root Token",
                        FactionEmojis.vaettir))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose the Root token to place on "
                        + Helper.getPlanetRepresentation(planetId, game) + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_ROOT)
    public static void selectRoot(ButtonInteractionEvent event, Game game, Player player, String buttonId) {
        String[] payload = buttonId.substring(SELECT_ROOT.length()).split("\\|", 2);
        Planet planet = payload.length == 2 ? game.getUnitHolderFromPlanet(payload[0]) : null;
        if (!player.getPromissoryNotesInPlayArea().contains(RATATOSKRS_GUIDANCE)
                || planet == null
                || !player.getPlanets().contains(payload[0])
                || !VaettirAbilityHandler.getRootTypes().contains(payload[1])
                || !VaettirAbilityHandler.isRootTokenAvailable(game, payload[1])
                || planet.getTokenList().contains("attachment_root_" + payload[1] + ".png")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        planet.addToken("attachment_root_" + payload[1] + ".png");
        VaettirAbilityHandler.syncOldGrowthRootCards(game);
        List<String> exploreCards = VaettirAbilityHandler.getRootTypes().stream()
                .filter(rootType -> hasTrait(planet, rootType))
                .map(VaettirPromissoryHandler::exploreDeck)
                .map(game::drawExplore)
                .filter(cardId -> cardId != null)
                .toList();
        Tile tile = game.getTileFromPlanet(payload[0]);
        if (exploreCards.isEmpty() || tile == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " placed a " + payload[1] + " Root token on "
                            + Helper.getPlanetRepresentation(payload[0], game) + " with _Ratatoskr's Guidance_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = exploreCards.stream()
                .map(cardId -> exploreButton(player, payload[0], cardId))
                .toList();
        List<MessageEmbed> embeds = exploreCards.stream()
                .map(Mapper::getExplore)
                .map(ExploreModel::getRepresentationEmbed)
                .toList();
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " placed a " + payload[1] + " Root token on "
                        + Helper.getPlanetRepresentation(payload[0], game)
                        + " with _Ratatoskr's Guidance_. Please choose 1 exploration card to resolve.",
                embeds,
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(RESOLVE_EXPLORE)
    public static void resolveExplore(ButtonInteractionEvent event, Game game, Player player, String buttonId) {
        String[] payload = buttonId.substring(RESOLVE_EXPLORE.length()).split("\\|", 2);
        if (payload.length != 2 || Mapper.getExplore(payload[1]) == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile tile = game.getTileFromPlanet(payload[0]);
        if (tile == null || !player.getPlanets().contains(payload[0])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String message = player.getRepresentationNoPing() + " chose to resolve _"
                + Mapper.getExplore(payload[1]).getName() + "_ with _Ratatoskr's Guidance_.";
        ExploreService.resolveExplore(event, payload[1], tile, payload[0], message, player, game);
        ButtonHelper.deleteMessage(event);
    }

    private static boolean hasTrait(Planet planet, String rootType) {
        return planet.getPlanetTypes().contains(rootType)
                || planet.getTokenList().contains("attachment_root_" + rootType + ".png");
    }

    private static String exploreDeck(String rootType) {
        return switch (rootType) {
            case "cultural" -> Constants.CULTURAL;
            case "industrial" -> Constants.INDUSTRIAL;
            default -> Constants.HAZARDOUS;
        };
    }

    private static Button exploreButton(Player player, String planetId, String cardId) {
        return Buttons.green(
                player.factionButtonChecker() + RESOLVE_EXPLORE + planetId + "|" + cardId,
                "Resolve " + Mapper.getExplore(cardId).getName(),
                FactionEmojis.vaettir);
    }
}
