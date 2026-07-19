package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
class HeistAcd2ButtonHandler {

    @ButtonHandler("resolveHeist")
    public static void resolveHeist(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            String id = player.factionButtonChecker() + "heistStep2_" + p2.getFaction() + "_2";
            if (game.isFowMode()) {
                buttons.add(Buttons.gray(id, p2.getColor()));
            } else {
                buttons.add(Buttons.gray(id, p2.getColor()).withEmoji(Emoji.fromFormatted(p2.getFactionEmoji())));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose the player you took the planet from to resolve _Heist_.",
                buttons);
    }

    @ButtonHandler("heistStep2_")
    public static void heistStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String payload = buttonID.replace("heistStep2_", "");
        int separator = payload.lastIndexOf('_');
        Player p2 = separator < 0 ? null : game.getPlayerFromColorOrFaction(payload.substring(0, separator));
        int remaining = separator < 0 ? 0 : parseInt(payload.substring(separator + 1));
        ButtonHelper.deleteMessage(event);
        if (p2 == null || remaining <= 0) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String fragId : p2.getFragments()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "heistTake_" + p2.getFaction() + "_frag-" + fragId + "_"
                            + remaining,
                    "Take " + fragmentLabel(fragId),
                    fragmentEmoji(fragId)));
        }
        if (p2.getTg() > 0) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "heistTake_" + p2.getFaction() + "_tg_" + remaining,
                    "Take 1 Trade Good",
                    MiscEmojis.tg));
        }
        if (p2.getCommodities() > 0) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "heistTake_" + p2.getFaction() + "_comm_" + remaining,
                    "Take 1 Commodity",
                    MiscEmojis.comm));
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    p2.getRepresentationNoPing()
                            + " has no relic fragments, trade goods, or commodities left to take for _Heist_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));

        String remainingText = remaining == 1 ? "1 item remaining" : remaining + " items remaining";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose what to take from " + p2.getRepresentationNoPing()
                        + " for _Heist_ (" + remainingText + ").",
                buttons);
    }

    @ButtonHandler("heistTake_")
    public static void heistTake(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("heistTake_", "").split("_");
        if (parts.length < 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Player p2 = game.getPlayerFromColorOrFaction(parts[0]);
        String kind = parts[1];
        int remaining = parseInt(parts[2]);
        ButtonHelper.deleteMessage(event);
        if (p2 == null || remaining <= 0) {
            return;
        }

        String tookMsg;
        if (kind.startsWith("frag-")) {
            String fragId = kind.substring("frag-".length());
            if (!p2.getFragments().contains(fragId)) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Heist_.");
                return;
            }
            p2.removeFragment(fragId);
            player.addFragment(fragId);
            tookMsg = "a " + fragmentEmoji(fragId) + fragmentLabel(fragId);
        } else if ("tg".equals(kind)) {
            if (p2.getTg() <= 0) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Heist_.");
                return;
            }
            p2.setTg(p2.getTg() - 1);
            player.setTg(player.getTg() + 1);
            tookMsg = "1 " + MiscEmojis.tg + " trade good";
        } else if ("comm".equals(kind)) {
            if (p2.getCommodities() <= 0) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Heist_.");
                return;
            }
            p2.setCommodities(p2.getCommodities() - 1);
            // Commodities taken from another player become trade goods.
            player.setTg(player.getTg() + 1);
            tookMsg = "1 " + MiscEmojis.comm + " commodity (gained as 1 " + MiscEmojis.tg + " trade good)";
        } else {
            return;
        }

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " took " + tookMsg + " from " + p2.getRepresentationNoPing()
                        + " with _Heist_.");

        if (remaining > 1) {
            heistStep2(player, game, event, "heistStep2_" + p2.getFaction() + "_" + (remaining - 1));
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String fragmentLabel(String fragId) {
        return switch (Mapper.getExplore(fragId).getType().toLowerCase()) {
            case "cultural" -> "cultural relic fragment";
            case "industrial" -> "industrial relic fragment";
            case "hazardous" -> "hazardous relic fragment";
            default -> "unknown relic fragment";
        };
    }

    private static String fragmentEmoji(String fragId) {
        return switch (Mapper.getExplore(fragId).getType().toLowerCase()) {
            case "cultural" -> ExploreEmojis.CFrag.toString();
            case "industrial" -> ExploreEmojis.IFrag.toString();
            case "hazardous" -> ExploreEmojis.HFrag.toString();
            default -> ExploreEmojis.UFrag.toString();
        };
    }
}
