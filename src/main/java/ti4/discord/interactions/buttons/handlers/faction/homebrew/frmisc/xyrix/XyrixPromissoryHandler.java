package ti4.discord.interactions.buttons.handlers.faction.homebrew.frmisc.xyrix;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.Mapper;
import ti4.service.explore.ExploreService;

@UtilityClass
public class XyrixPromissoryHandler {
    private static final String DARK_MATTER_SPAWNING_STATION = "bepnxyrix";
    private static final String ATTACHMENT = "darkmatterspawningstation";
    private static final String ATTACH = "xyrixAttachDmss_";
    private static final String RESOLVE_FRONTIER = "xyrixResolveDmssFrontier_";
    private static final String PENDING_FRONTIER = "xyrixDmssFrontier_";

    public static boolean hasLegalDarkMatterSpawningStationTarget(Game game, Player player) {
        return player.getPlanets().stream().anyMatch(planet -> isLegalAttachmentTarget(game, planet));
    }

    public static void offerDarkMatterSpawningStationTarget(Game game, Player player) {
        List<Button> buttons = getAttachmentButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " has no eligible planet for _Dark Matter Spawning Station_.");
            return;
        }
        NewStuffHelper.sendPaginated(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose a planet for _Dark Matter Spawning Station_:",
                buttons,
                List.of(),
                player.factionButtonChecker() + ATTACH);
    }

    @ButtonHandler(ATTACH)
    public static void attachDarkMatterSpawningStation(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getAttachmentButtons(game, player);
        String message = player.getRepresentationNoPing() + ", choose a planet for _Dark Matter Spawning Station_:";
        String prefix = player.factionButtonChecker() + ATTACH;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, prefix, buttonID)) {
            return;
        }

        String planet = buttonID.substring(ATTACH.length());
        Tile tile = game.getTileFromPlanet(planet);
        UnitHolder holder = tile == null ? null : tile.getUnitHolderFromPlanet(planet);
        if (!player.getPromissoryNotesInPlayArea().contains(DARK_MATTER_SPAWNING_STATION)
                || !player.getPlanets().contains(planet)
                || holder == null
                || !isLegalAttachmentTarget(game, planet)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        holder.addToken(Mapper.getAttachmentImagePath(ATTACHMENT));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " attached _Dark Matter Spawning Station_ to "
                        + Helper.getPlanetRepresentation(planet, game) + ".");
        ButtonHelper.deleteMessage(event);
    }

    public static void placeFrontierTokenOnPass(Game game, Player player) {
        Tile tile = getSpawningStationTile(game, player);
        if (tile == null || tile.getSpaceUnitHolder().getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER))) {
            return;
        }
        tile.addToken(Mapper.getTokenID(Constants.FRONTIER), Constants.SPACE);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed a frontier token in "
                        + tile.getRepresentationForButtons(game, player) + " with _Dark Matter Spawning Station_.");
    }

    public static void clearPendingFrontierExplore(Game game, Player player) {
        String pending = game.getStoredValue(pendingFrontierKey(player));
        String[] cards = pending.split("\\|", 3);
        if (cards.length == 3) {
            game.addExplore(cards[1]);
            game.addExplore(cards[2]);
            game.shuffleExplores();
        }
        game.removeStoredValue(pendingFrontierKey(player));
    }

    public static boolean offerAdditionalFrontierExplore(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile) {
        if (tile == null || getSpawningStationTile(game, player) != tile) {
            return false;
        }
        String firstCard = game.drawExplore(Constants.FRONTIER);
        String secondCard = game.drawExplore(Constants.FRONTIER);
        if (firstCard == null) {
            return false;
        }
        if (secondCard == null) {
            ExploreService.expFrontAlreadyDone(event, tile, game, player, firstCard);
            return true;
        }

        game.setStoredValue(pendingFrontierKey(player), tile.getPosition() + "|" + firstCard + "|" + secondCard);
        List<Button> buttons =
                List.of(resolveFrontierButton(player, firstCard), resolveFrontierButton(player, secondCard));
        List<net.dv8tion.jda.api.entities.MessageEmbed> embeds = List.of(
                Mapper.getExplore(firstCard).getRepresentationEmbed(),
                Mapper.getExplore(secondCard).getRepresentationEmbed());
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + ", choose 1 frontier exploration card to resolve with _Dark Matter Spawning Station_. The other will return to the frontier exploration deck and be shuffled.",
                embeds,
                buttons);
        return true;
    }

    @ButtonHandler(RESOLVE_FRONTIER)
    public static void resolveDarkMatterSpawningStationFrontier(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String chosenCard = buttonID.substring(RESOLVE_FRONTIER.length());
        String[] pending = game.getStoredValue(pendingFrontierKey(player)).split("\\|", 3);
        if (pending.length != 3 || (!chosenCard.equals(pending[1]) && !chosenCard.equals(pending[2]))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileByPosition(pending[0]);
        if (tile == null || getSpawningStationTile(game, player) != tile) {
            game.addExplore(pending[1]);
            game.addExplore(pending[2]);
            game.shuffleExplores();
            game.removeStoredValue(pendingFrontierKey(player));
            ButtonHelper.deleteMessage(event);
            return;
        }

        String returnedCard = chosenCard.equals(pending[1]) ? pending[2] : pending[1];
        game.addExplore(returnedCard);
        game.shuffleExplores();
        game.removeStoredValue(pendingFrontierKey(player));
        ExploreService.expFrontAlreadyDone(event, tile, game, player, chosenCard);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getAttachmentButtons(Game game, Player player) {
        return player.getPlanets().stream()
                .filter(planet -> isLegalAttachmentTarget(game, planet))
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + ATTACH + planet, Helper.getPlanetRepresentation(planet, game)))
                .toList();
    }

    private static boolean isLegalAttachmentTarget(Game game, String planet) {
        Tile tile = game.getTileFromPlanet(planet);
        return tile != null && !tile.isHomeSystem(game) && !tile.isMecatol(game);
    }

    private static Tile getSpawningStationTile(Game game, Player player) {
        if (!player.getPromissoryNotesInPlayArea().contains(DARK_MATTER_SPAWNING_STATION)) {
            return null;
        }
        String attachment = Mapper.getAttachmentImagePath(ATTACHMENT);
        return player.getPlanets().stream()
                .map(game::getTileFromPlanet)
                .filter(tile -> tile != null)
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .filter(planet -> player.getPlanets().contains(planet.getName()))
                        .anyMatch(planet -> planet.getTokenList().contains(attachment)))
                .findFirst()
                .orElse(null);
    }

    private static Button resolveFrontierButton(Player player, String cardId) {
        ExploreModel card = Mapper.getExplore(cardId);
        return Buttons.green(player.factionButtonChecker() + RESOLVE_FRONTIER + cardId, "Resolve " + card.getName());
    }

    private static String pendingFrontierKey(Player player) {
        return PENDING_FRONTIER + player.getFaction();
    }
}
