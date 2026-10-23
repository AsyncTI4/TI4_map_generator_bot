package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
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
import ti4.message.MessageHelper;
import ti4.service.async.DieRollSequenceService;
import ti4.service.emoji.ExploreEmojis;

@UtilityClass
class TombRaidersAcd2ButtonHandler {

    private static final int RELIC_THRESHOLD = 10;
    private static final int MAX_PLANET_BUTTONS = 25;
    private static final List<String> ACTIVITIES = List.of(
            "🧗 Climbing...",
            "🏊 Swimming...",
            "🚣 Rowing...",
            "🚵 Biking...",
            "🏇 Racing...",
            "🤺 Fencing...",
            "🤸 Yippee?...",
            "🪂 Parachuting...",
            "⛷️ Skiing...",
            "🗿 Sight-seeing...",
            "🛖 Consulting natives...",
            "🏚️ Breaking in...",
            "🏔️ Ascending...",
            "🌋 Krakatoa...",
            "🏜️ Investigating a mirage...",
            "🏝️ Shipwrecked...",
            "🏰 Courting...",
            "🗺️ X marks the spot?...",
            "🐉 Slaying...",
            "🏃 Running away...",
            "🥷 Stealthing...",
            "🧞 Wishing...",
            "🕵️ Sleuthing...");

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

        rollUntilTen(player, game, planetName, getDiceCount(planet), "is rolling");
    }

    @ButtonHandler("tombRaidersKeepRolling_")
    public static void tombRaidersKeepRolling(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("tombRaidersKeepRolling_", "");
        int separator = payload.indexOf('_');
        ButtonHelper.deleteMessage(event);
        if (separator < 0) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resume _Tomb Raiders_.");
            return;
        }
        int remainingDice = Integer.parseInt(payload.substring(0, separator));
        rollUntilTen(player, game, payload.substring(separator + 1), remainingDice, "keeps rolling");
    }

    private static void rollUntilTen(Player player, Game game, String planetName, int diceCount, String verb) {
        List<DiceHelper.Die> shownRolls = untilFirstSuccess(DiceHelper.rollDice(RELIC_THRESHOLD, diceCount));
        MessageChannel channel = player.getCorrectChannel();
        String header = player.getRepresentation() + " " + verb + " up to " + diceCount + " "
                + (diceCount == 1 ? "die" : "dice") + " for _Tomb Raiders_ on "
                + Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planetName, game) + ".";
        String relicButtonId = player.factionButtonChecker() + "drawRelic";
        String keepRollingButtonId = player.factionButtonChecker() + "tombRaidersKeepRolling_"
                + (diceCount - shownRolls.size()) + "_" + planetName;
        DieRollSequenceService.play(
                channel,
                header,
                pickActivities(shownRolls.size()),
                shownRolls,
                () -> announceOutcome(channel, shownRolls, diceCount, relicButtonId, keepRollingButtonId));
    }

    private static List<DiceHelper.Die> untilFirstSuccess(List<DiceHelper.Die> rolls) {
        for (int i = 0; i < rolls.size(); i++) {
            if (rolls.get(i).isSuccess()) {
                return rolls.subList(0, i + 1);
            }
        }
        return rolls;
    }

    private static void announceOutcome(
            MessageChannel channel,
            List<DiceHelper.Die> shownRolls,
            int diceCount,
            String relicButtonId,
            String keepRollingButtonId) {
        boolean foundRelic = DiceHelper.countSuccesses(shownRolls) > 0;
        String message = getTombRaidersLoreQuip(foundRelic);
        if (!foundRelic) {
            MessageHelper.sendMessageToChannel(channel, message);
            return;
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(relicButtonId, "Draw Relic", ExploreEmojis.Relic));
        int remainingDice = diceCount - shownRolls.size();
        if (remainingDice > 0) {
            message += "\nIf that 10 gets changed, use the other button to keep rolling the remaining " + remainingDice
                    + (remainingDice == 1 ? " die." : " dice.");
            buttons.add(Buttons.gray(keepRollingButtonId, "Keep Rolling (" + remainingDice + " left)"));
        }
        MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
    }

    private static List<String> pickActivities(int count) {
        List<String> activities = new ArrayList<>();
        while (activities.size() < count) {
            List<String> shuffled = new ArrayList<>(ACTIVITIES);
            Collections.shuffle(shuffled);
            activities.addAll(shuffled);
        }
        return activities.subList(0, count);
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
