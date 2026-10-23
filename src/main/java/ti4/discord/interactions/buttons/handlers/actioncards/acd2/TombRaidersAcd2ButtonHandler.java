package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DiceHelper;
import ti4.helpers.Helper;
import ti4.helpers.RelicHelper;
import ti4.message.MessageHelper;

@UtilityClass
class TombRaidersAcd2ButtonHandler {

    private static final int RELIC_THRESHOLD = 10;
    private static final int MAX_PLANET_BUTTONS = 25;

    @ButtonHandler("resolveTombRaiders")
    public static void resolveTombRaiders(Player player, Game game, ButtonInteractionEvent event) {
        List<String> activeSystemPlanets = getActiveSystemPlanets(player, game);
        List<Button> buttons = new ArrayList<>(getPlanetButtons(player, game, activeSystemPlanets));
        if (buttons.isEmpty()) {
            buttons.addAll(getPlanetButtons(player, game, player.getPlanets()));
        } else if (!getOtherPlanets(player, activeSystemPlanets).isEmpty()) {
            buttons.add(Buttons.gray(player.factionButtonChecker() + "resolveTombRaidersOther", "Other"));
        }
        offerPlanets(player, game, event, buttons);
    }

    @ButtonHandler("resolveTombRaidersOther")
    public static void resolveTombRaidersOther(Player player, Game game, ButtonInteractionEvent event) {
        List<String> otherPlanets = getOtherPlanets(player, getActiveSystemPlanets(player, game));
        offerPlanets(player, game, event, getPlanetButtons(player, game, otherPlanets));
    }

    @ButtonHandler("resolveTombRaidersStep2_")
    public static void resolveTombRaidersStep2(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String planetName = buttonID.replace("resolveTombRaidersStep2_", "");
        Planet planet = game.getPlanetsInfo().get(planetName);
        ButtonHelper.deleteMessage(event);
        if (planet == null || !player.hasPlanet(planetName)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not resolve _Tomb Raiders_ for that planet.");
            return;
        }

        int diceCount = getDiceCount(planet);
        List<DiceHelper.Die> rolls = DiceHelper.rollDice(RELIC_THRESHOLD, diceCount);
        boolean foundRelic = DiceHelper.countSuccesses(rolls) > 0;
        String message = player.getRepresentation() + " rolled " + diceCount + " "
                + (diceCount == 1 ? "die" : "dice") + " for _Tomb Raiders_ on "
                + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + ".\n"
                + DiceHelper.formatDiceOutput(rolls) + "\n" + getTombRaidersLoreQuip(foundRelic);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);

        if (foundRelic) {
            RelicHelper.drawRelicAndNotify(player, event, game);
        }
    }

    private static int getDiceCount(Planet planet) {
        if (planet.getPlanetTypes().isEmpty()) {
            return planet.getSumResourcesInfluence();
        }
        return planet.getHigherofInfluenceOrResource();
    }

    private static void offerPlanets(Player player, Game game, ButtonInteractionEvent event, List<Button> buttons) {
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no planets to target with _Tomb Raiders_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose the planet you just gained for _Tomb Raiders_.",
                buttons);
    }

    private static List<Button> getPlanetButtons(Player player, Game game, List<String> planets) {
        return planets.stream()
                .limit(MAX_PLANET_BUTTONS)
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + "resolveTombRaidersStep2_" + planet,
                        Helper.getPlanetRepresentation(planet, game)))
                .toList();
    }

    private static List<String> getOtherPlanets(Player player, List<String> activeSystemPlanets) {
        return player.getPlanets().stream()
                .filter(planet -> !activeSystemPlanets.contains(planet))
                .toList();
    }

    private static List<String> getActiveSystemPlanets(Player player, Game game) {
        Tile activeSystem = game.getTileByPosition(game.getCurrentActiveSystem());
        if (activeSystem == null) {
            return List.of();
        }
        return activeSystem.getPlanetUnitHolders().stream()
                .map(Planet::getName)
                .filter(player::hasPlanet)
                .toList();
    }

    private static String getTombRaidersLoreQuip(boolean foundRelic) {
        List<String> quips = foundRelic
                ? List.of(
                        "\"It belongs in a museum!\" — Indiana Jones, _Indiana Jones and the Last Crusade_",
                        "\"Fortune and glory, kid. Fortune and glory.\" — Indiana Jones, _Indiana Jones and the Temple of Doom_",
                        "\"You have chosen... wisely.\" — Grail Knight, _Indiana Jones and the Last Crusade_")
                : List.of(
                        "\"They're digging in the wrong place!\" — Indiana Jones, _Raiders of the Lost Ark_",
                        "\"X never, ever marks the spot.\" — Indiana Jones, _Indiana Jones and the Last Crusade_",
                        "\"He chose... poorly.\" — Grail Knight, _Indiana Jones and the Last Crusade_");
        return quips.get(ThreadLocalRandom.current().nextInt(quips.size()));
    }
}
