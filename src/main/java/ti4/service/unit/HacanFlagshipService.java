package ti4.service.unit;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.RegexHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.combat.CombatRollService;
import ti4.service.emoji.UnitEmojis;
import ti4.service.regex.RegexService;

@UtilityClass
public class HacanFlagshipService {

    public static void startHacanFlagshipNormal(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, int amount) {
        if (player.getTg() == 0 && amount > 0) {
            String message = player.getRepresentation() + ", you would have been able to score up to ";
            message += amount + " extra hits, but you don't have any trade goods :(";
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            } else {
                MessageHelper.sendMessageToEventChannel(event, message);
            }
        } else if (amount > 0) {
            sendHacanFlagshipButtons(event, game, player, tile.getPosition(), amount, 0);
        }
    }

    private static Button hacanFSButton(Player player, String pos, int misses, int hits, int added) {
        String id = player.finChecker() + "hacanFlagship_" + pos + "_" + misses + "_" + hits;
        String label = "Add " + added + " hit" + (added > 1 ? "s" : "") + " with Wrath of Kenara";
        return Buttons.green(id, label, UnitEmojis.flagship);
    }

    private static void sendHacanFlagshipButtons(
            GenericInteractionCreateEvent event, Game game, Player player, String pos, int nearMisses, int hitsSoFar) {
        List<Button> buttons = new ArrayList<>();
        if (hitsSoFar > 0) {
            String id = "hacanFlagship_" + pos + "_" + (nearMisses + hitsSoFar) + "_0";
            String label = "Start over";
            buttons.add(Buttons.gray(id, label));
        }

        int tot = hitsSoFar + nearMisses;
        int mostHits = Math.min(tot, player.getTg());
        int amtRemaining = Math.min(tot - hitsSoFar, player.getTg() - hitsSoFar);
        // +1 hit button
        if (amtRemaining >= 1) {
            buttons.add(hacanFSButton(player, pos, nearMisses - 1, hitsSoFar + 1, 1));
        }
        // +5 hits button (if applicable)
        if (amtRemaining >= 6) {
            buttons.add(hacanFSButton(player, pos, nearMisses - 5, hitsSoFar + 5, 5));
        }
        // max hits button
        if (amtRemaining >= 2) {
            buttons.add(hacanFSButton(player, pos, tot - mostHits, mostHits, amtRemaining));
        }
        if (hitsSoFar > 0) {
            String id = "hacanFlagshipFinish_" + pos + "_" + hitsSoFar;
            String label = "Finish & Produce " + hitsSoFar + " Hit(s)";
            buttons.add(Buttons.red(id, label));
        }
        String label = hitsSoFar > 0 ? "Cancel" : "Decline";
        buttons.add(Buttons.DONE_DELETE_BUTTONS.withLabel(label));

        String message = player.getRepresentation() + ", there are " + tot + " dice eligible to produce an";
        message += " additional hit using the _Wrath of Kenara_ flagship ability.";
        if (!game.isFowMode()) {
            message += "\n-# You currently have " + player.getTg() + " trade goods.";
        } else {
            String msg2 = "You currently have " + player.getTg() + " trade goods to use for the Wrath of Kenara.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        }
        MessageHelper.sendMessageToEventChannelWithButtons(event, message, buttons);
    }

    @ButtonHandler("hacanFlagship_")
    private static void hacanFlagshipEditing(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String rx = "hacanFlagship_" + RegexHelper.posRegex() + "_" + RegexHelper.intRegex("misses") + "_"
                + RegexHelper.intRegex("hits");
        RegexService.runMatcher(rx, buttonID, matcher -> {
            int misses = Integer.parseInt(matcher.group("misses"));
            int hits = Integer.parseInt(matcher.group("hits"));
            String pos = matcher.group("pos");
            sendHacanFlagshipButtons(event, game, player, pos, misses, hits);
            ButtonHelper.deleteMessage(event);
        });
    }

    @ButtonHandler("hacanFlagshipFinish_")
    private static void hacanFlagshipFinish(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String rx = "hacanFlagshipFinish_" + RegexHelper.posRegex() + "_" + RegexHelper.intRegex("hits");
        RegexService.runMatcher(rx, buttonID, matcher -> {
            int hits = Integer.parseInt(matcher.group("hits"));
            Tile tile = game.getTileByPosition(matcher.group("pos"));

            String playersInCombat = game.getStoredValue("factionsInCombat");
            if (!playersInCombat.isBlank() && playersInCombat.contains(player.getFaction())) {
                for (Player opponent : game.getRealPlayersExcludingThis(player)) {
                    if (playersInCombat.contains(opponent.getFaction())) {
                        CombatRollService.sendSpaceAssignHitsButtons(event, game, opponent, tile, hits);
                        break;
                    }
                }
            }

            String gain = player.gainTG(-1 * hits);
            String msg = player.getRepresentationNoPing() + " has spent " + hits + " trade goods";
            if (!game.isFowMode()) msg += " " + gain;
            msg += " to score " + hits + " additional hits.";
            MessageHelper.sendMessageToEventChannel(event, msg);

            ButtonHelper.deleteMessage(event);
        });
    }

    @ButtonHandler("hacanFlagshipThalnos_")
    private static void hacanFlagshipThalnos() {
        // This is a whole other can of worms
    }
}
