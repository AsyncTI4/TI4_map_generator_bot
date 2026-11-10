package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.migo;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;

@UtilityClass
public class MigoTechHandler {
    private static final String LAGH_METAL_ATTRACTORS = "bemigob";
    private static final String LAGH_METAL_ACTIVE = "laghMetalAttractors_";
    private static final String USE_LAGH_METAL_ATTRACTORS = "migoUseLaghMetalAttractors_";

    public static void offerSynapticFeedbackLoop(Player player, Game game, String planet) {
        if (!player.hasTechReady("bemigog")
                || !player.getExhaustedPlanets().contains(planet)
                || !MigoAbilityHandler.hasTechSpecialty(game, planet)) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                player.factionButtonChecker() + "readyPlanetWithSFL_" + planet,
                "Ready " + Helper.getPlanetRepresentation(planet, game)));
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", you may exhaust _Synaptic Feedback Loop_ to ready "
                        + Helper.getPlanetRepresentation(planet, game) + ".",
                buttons);
    }

    @ButtonHandler("readyPlanetWithSFL_")
    public static void readyPlanetWithSFL(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.replace("readyPlanetWithSFL_", "");
        if (!player.hasTechReady("bemigog")
                || !player.hasPlanet(planetName)
                || !player.getExhaustedPlanets().contains(planetName)
                || !MigoAbilityHandler.hasTechSpecialty(game, planetName)) {
            return;
        }

        player.exhaustTech("bemigog");
        player.refreshPlanet(planetName);
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " readied " + Helper.getPlanetRepresentation(planetName, game)
                        + " using _Synaptic Feedback Loop_.");
    }

    public static void offerLaghMetalAttractors(Game game, Player player, Tile tile) {
        boolean hasSpecialtyPlanet = tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> MigoAbilityHandler.hasTechSpecialty(game, planet.getName()));

        if (!player.hasTechReady(LAGH_METAL_ATTRACTORS) || !hasSpecialtyPlanet) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may exhaust _Lagh-Metal Attractors_ to give each of your ships +1 MOVE during this tactical action.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + USE_LAGH_METAL_ATTRACTORS + tile.getPosition(),
                                "Use Lagh-Metal Attractors"),
                        Buttons.DECLINE_DELETE_BUTTONS));
    }

    @ButtonHandler(USE_LAGH_METAL_ATTRACTORS)
    public static void useLaghMetalAttractors(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.substring(USE_LAGH_METAL_ATTRACTORS.length());
        Tile tile = game.getTileByPosition(position);

        boolean hasSpecialtyPlanet = tile != null
                && tile.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> MigoAbilityHandler.hasTechSpecialty(game, planet.getName()));

        if (!position.equals(game.getActiveSystem())
                || !player.hasTechReady(LAGH_METAL_ATTRACTORS)
                || !hasSpecialtyPlanet) {
            return;
        }

        player.exhaustTech(LAGH_METAL_ATTRACTORS);
        game.setStoredValue(LAGH_METAL_ACTIVE + player.getFaction(), "yes");

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " exhausted _Lagh-Metal Attractors_; each of their ships has +1 MOVE during this tactical action.");
        ButtonHelper.deleteMessage(event);
    }

    public static boolean hasLaghMetalAttractorsMovementBonus(Game game, Player player) {
        return !game.getStoredValue(LAGH_METAL_ACTIVE + player.getFaction()).isEmpty();
    }

    public static void clearLaghMetalAttractorsMovementBonus(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(LAGH_METAL_ACTIVE + player.getFaction());
        }
    }
}
