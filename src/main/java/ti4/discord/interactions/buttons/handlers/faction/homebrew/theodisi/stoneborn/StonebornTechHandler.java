package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.stoneborn;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.Mapper;
import ti4.service.explore.ExploreService;

@UtilityClass
public class StonebornTechHandler {
    private static final String DORMANT_XENOLITH = "dormantXenolith_";
    private static final String UNLEASHED_XENOLITH = "unleashedXenolith_";

    public static void addDormantXenolithExploreButton(
            List<Button> buttons, Player player, String planet, String trait) {
        if (!player.hasTechReady("thstoneborng")) {
            return;
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker() + DORMANT_XENOLITH + planet + "|" + trait, "Explore With Xenolith"));
    }

    public static void offerUnleashedXenolithAfterExplore(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, String planet, String trait) {
        if (!player.hasTechReady("thstoneborngu")
                || (!Constants.FRONTIER.equalsIgnoreCase(trait)
                        && (planet == null || !player.getPlanetsAllianceMode().contains(planet)))) {
            return;
        }
        String position = tile == null ? "none" : tile.getPosition();
        Button button = Buttons.green(
                player.factionButtonChecker()
                        + UNLEASHED_XENOLITH
                        + trait.toLowerCase()
                        + "|"
                        + (planet == null ? "none" : planet)
                        + "|"
                        + position,
                "Use Xenolith");
        MessageHelper.sendMessageToChannelWithButton(
                getOutputChannel(game, player, event),
                player.getRepresentation()
                        + ", you may exhaust _Xenolith Unleashed_ to reveal until you find a relic fragment.",
                button);
    }

    @ButtonHandler(DORMANT_XENOLITH)
    public static void resolveDormantXenolith(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String[] payload = buttonID.substring(DORMANT_XENOLITH.length()).split("\\|", 2);
        if (payload.length != 2
                || !player.hasTechReady("thstoneborng")
                || !player.getPlanetsAllianceMode().contains(payload[0])) {
            return;
        }
        game.setStoredValue(
                player.getFaction() + "planetsExplored",
                game.getStoredValue(player.getFaction() + "planetsExplored") + payload[0] + "*");
        resolveXenolith(
                event, game, player, "thstoneborng", payload[1], payload[0], game.getTileFromPlanet(payload[0]));
        event.getMessage().delete().queue();
    }

    @ButtonHandler(UNLEASHED_XENOLITH)
    public static void resolveUnleashedXenolith(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String[] payload = buttonID.substring(UNLEASHED_XENOLITH.length()).split("\\|", 3);
        if (payload.length != 3 || !player.hasTechReady("thstoneborngu")) {
            return;
        }
        String planet = "none".equals(payload[1]) ? null : payload[1];
        Tile tile = "none".equals(payload[2]) ? game.getTileFromPlanet(planet) : game.getTileByPosition(payload[2]);
        if (!Constants.FRONTIER.equalsIgnoreCase(payload[0])
                && (planet == null || !player.getPlanetsAllianceMode().contains(planet))) {
            return;
        }
        resolveXenolith(event, game, player, "thstoneborngu", payload[0], planet, tile);
        event.getMessage().delete().queue();
    }

    private static void resolveXenolith(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            String tech,
            String trait,
            String planet,
            Tile tile) {
        List<String> revealed = new ArrayList<>();
        String fragment = null;
        int cardsToCheck = game.getExploreDeck(trait).size()
                + game.getExploreDiscard(trait).size();
        for (int index = 0; index < cardsToCheck; index++) {
            String card = game.drawExplore(trait);
            if (card == null) {
                break;
            }
            ExploreModel model = Mapper.getExplore(card);
            if (model != null && Constants.FRAGMENT.equalsIgnoreCase(model.getResolution())) {
                fragment = card;
                break;
            }
            revealed.add(card);
        }
        returnXenolithReveals(game, revealed);
        player.exhaustTech(tech);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " exhausted _"
                        + Mapper.getTech(tech).getName() + "_.");
        if (fragment == null) {
            MessageHelper.sendMessageToChannel(
                    getOutputChannel(game, player, event), "No relic fragment was found in that exploration deck.");
            return;
        }
        ExploreService.resolveExplore(
                event,
                fragment,
                tile,
                planet,
                player.getRepresentationNoPing() + " resolved _Xenolith_ and found a relic fragment:",
                player,
                game);
    }

    private static void returnXenolithReveals(Game game, List<String> revealed) {
        if (revealed.isEmpty()) {
            return;
        }
        if (game.getAllExplores().isEmpty()) {
            game.getAllExplores().addAll(revealed);
            game.getAllExploreDiscard().removeAll(revealed);
        } else {
            revealed.forEach(game::addExplore);
        }
        game.shuffleExplores();
    }

    private static MessageChannel getOutputChannel(Game game, Player player, GenericInteractionCreateEvent event) {
        return game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel();
    }
}
