package ti4.helpers.thundersedge;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.commands.CommandHelper;
import ti4.helpers.BreakthroughHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.BreakthroughModel;
import ti4.service.breakthrough.StellarGenesisService;

public class BreakthroughCommandHelper {

    private static void withBreakthrough(Player player, Consumer<BreakthroughModel> action) {
        BreakthroughModel bt = player.getBreakthroughModel();
        if (bt == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Player does not have a breakthrough");
        } else {
            action.accept(bt);
        }
    }

    public static void sendBreakthroughInfo(Game game, Player player) {
        withBreakthrough(player, bt -> {
            MessageHelper.sendMessageEmbedsToCardsInfoThread(player, "", List.of(bt.getRepresentationEmbed()));
        });
    }

    public static void sendBreakthroughInfo(GenericInteractionCreateEvent event, Game game, Player player) {
        withBreakthrough(player, bt -> {
            String headerText = player.getRepresentation() + CommandHelper.getHeaderText(event);
            MessageHelper.sendMessageEmbedsToCardsInfoThread(player, headerText, List.of(bt.getRepresentationEmbed()));
        });
    }

    public static void exhaustBreakthrough(GenericInteractionCreateEvent event, Player player) {
        withBreakthrough(player, bt -> {
            player.setBreakthroughExhausted(true);
            String message = player.getRepresentation() + " exhausted their breakthrough " + bt.getName() + ":";
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(), message, bt.getRepresentationEmbed());
        });
    }

    public static void readyBreakthrough(Player player) {
        withBreakthrough(player, bt -> {
            player.setBreakthroughExhausted(false);
            String message = player.getRepresentation() + " readied their breakthrough " + bt.getName() + ":";
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(), message, bt.getRepresentationEmbed());
        });
    }

    public static void unlockBreakthrough(Game game, Player player) {
        withBreakthrough(player, bt -> {
            if (player.isBreakthroughUnlocked()) return;

            player.setBreakthroughUnlocked(true);
            player.setBreakthroughExhausted(false);
            String message = player.getRepresentation() + " unlocked their breakthrough " + bt.getName() + ".";
            List<MessageEmbed> embeds = Collections.singletonList(bt.getRepresentationEmbed());
            MessageHelper.sendMessageToChannelWithEmbeds(player.getCorrectChannel(), message, embeds);
            if ("yinbt".equalsIgnoreCase(bt.getID())) {
                BreakthroughHelper.resolveYinBreakthroughAbility(player.getGame(), player);
            }
            if (bt.getAlias().equals("muaatbt")) StellarGenesisService.serveAvernusButtons(game, player);
            if (bt.getAlias().equals("keleresbt")) player.gainCustodiaVigilia();
        });
    }

    public static void lockBreakthrough(Player player) {
        withBreakthrough(player, bt -> {
            if (!player.isBreakthroughUnlocked()) return;

            player.setBreakthroughUnlocked(false);
            String message = player.getRepresentation() + " locked their breakthrough " + bt.getName() + ".";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        });
    }

    public static void activateBreakthrough(GenericInteractionCreateEvent event, Player player) {
        withBreakthrough(player, bt -> {
            if (!player.isBreakthroughActive()) {
                player.setBreakthroughActive(true);
                String message = player.getRepresentation() + " activated their breakthrough: " + bt.getName();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            }
        });
    }

    public static void deactivateBreakthrough(GenericInteractionCreateEvent event, Player player) {
        withBreakthrough(player, bt -> {
            if (player.isBreakthroughActive()) {
                player.setBreakthroughActive(false);
                String message = player.getRepresentation() + " de-activated their breakthrough: " + bt.getName();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            }
        });
    }

    public static void setBreakthroughTGs(Player player, int newTgs) {
        if (newTgs < 0) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "You cannot have negative trade goods on your breakthrough.");
        } else if (!player.isBreakthroughUnlocked()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "You do not have your breakthrough unlocked, so you cannot add trade goods to it.");
        } else {
            int initial = player.getBreakthroughTGs();
            player.setBreakthroughTGs(newTgs);
            String msg = player.getRepresentation() + " set the TGs on their breakthrough to " + newTgs + ". ("
                    + initial + "->" + newTgs + ")";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        }
    }

    public static void updateBreakthroughTradeGoods(Player player, String option) {
        withBreakthrough(player, bt -> {
            int newTgs = readPlusMinus(player.getBreakthroughTGs(), option);
            setBreakthroughTGs(player, newTgs);
        });
    }

    private static int readPlusMinus(int initial, String option) {
        final Pattern pattern = Pattern.compile("(?<pm>([\\+\\-]?))(?<amt>(\\d+))");
        Matcher matcher = pattern.matcher(option);
        if (matcher.matches()) {
            String pm = matcher.group("pm");
            int amt = Integer.parseInt(matcher.group("amt"));
            if (pm != null && !pm.isBlank()) {
                if (pm.equals("-")) return initial - amt;
                return initial + amt;
            }
            return amt;
        }
        return initial;
    }
}
