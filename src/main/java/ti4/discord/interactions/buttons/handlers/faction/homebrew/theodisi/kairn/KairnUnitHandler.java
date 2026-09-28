package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Kairn;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
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
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.explore.ExploreService;

@UtilityClass
public class KairnUnitHandler {
    private static final String EXCAVATOR_EXPLORE = "explorePlanetWithExcavators_";

    public static Button getExcavatorButtons(Player player, Game game, Planet planet) {
        return Buttons.green(
                player.factionButtonChecker() + EXCAVATOR_EXPLORE + planet.getName(),
                "Explore " + planet.getRepresentation(game));
    }

    @ButtonHandler(EXCAVATOR_EXPLORE)
    public static void resolveExcavatorExplore(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (player == null || game == null || !player.ownsUnit("kairn_mech")) {
            return;
        }

        String planetName = buttonID.replace(EXCAVATOR_EXPLORE, "");
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null || tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve the selected planet.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (planet.getUnitCount(UnitType.Mech, player) != 2) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "This planet does not have exactly 2 Excavators (Kairn mechs).");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Set<String> traits = planet.getPlanetTypes();
        if (traits.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "This planet does not have an exploration trait.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> multiType = new ArrayList<>();
        for (String trait : traits) {
            multiType.add(Buttons.gray(
                    player.factionButtonChecker() + "movedNExplored_filler_" + planetName + "_" + trait,
                    "Explore " + Helper.getPlanetRepresentation(planetName, game) + " As "
                            + StringUtils.capitalize(trait),
                    ExploreEmojis.getTraitEmoji(trait)));
        }

        if (multiType.size() == 1) {
            ExploreService.explorePlanet(
                    event, tile, planetName, traits.iterator().next(), player, true, game, 1, false);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", please choose the trait to use when exploring this planet.",
                    multiType);
        }

        game.setStoredValue(player.getFaction() + "usedExcavatorThisAction", "yes");
        ButtonHelper.deleteMessage(event);
    }

    public static void clearExcavatorMechExplore(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(player.getFaction() + "usedExcavatorThisAction");
        }
    }
}
