package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
public class SarcosaPnHandler {
    private static final String PLUNDERERS = "dspnsarc";
    private static final String GIVE_PLUNDERERS = "giveSarcPnToOpponent_";
    private static final String PLUNDER = "plunderOpponent_";
    private static final String FINISH_PLUNDER = "finishPlunderOpponent_";

    public static Button offerPlunderersButton(Player player, Player opponent) {
        return Buttons.green(
                player.factionButtonChecker() + GIVE_PLUNDERERS + opponent.getFaction(),
                "Give Plunderers",
                FactionEmojis.sarcosa);
    }

    @ButtonHandler(GIVE_PLUNDERERS)
    public static void sendPlunderersAndOfferSteal(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String factionName = buttonID.substring(GIVE_PLUNDERERS.length());
        Player opponent = game.getPlayerFromColorOrFaction(factionName);
        Player sarcosa = game.getPNOwner(PLUNDERERS);
        if (opponent == null
                || sarcosa == null
                || opponent == player
                || !player.getPromissoryNotesInPlayArea().contains(PLUNDERERS)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.removePromissoryNote(PLUNDERERS);
        opponent.setPromissoryNote(PLUNDERERS);
        if (opponent != sarcosa && !opponent.isPlayerMemberOfAlliance(sarcosa)) {
            opponent.addPromissoryNoteToPlayArea(PLUNDERERS);
        }
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, opponent, false);
        sendPlunderButtons(game, player, opponent, 2);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLUNDER)
    public static void resolvePlunderOpponent(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(PLUNDER.length()).split("\\|", 3);
        if (values.length != 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Player opponent = game.getPlayerFromColorOrFaction(values[0]);
        int remaining = parseInt(values[1]);
        String resource = values[2];
        if (opponent == null || remaining < 1 || !takeResource(player, opponent, resource)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " took 1 " + resourceName(resource) + " from "
                        + opponent.getRepresentationNoPing() + " using _Plunderers_.");
        if (remaining == 1) {
            finishPlunder(game);
            return;
        }
        sendPlunderButtons(game, player, opponent, remaining - 1);
    }

    @ButtonHandler(FINISH_PLUNDER)
    public static void finishPlunderOpponent(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelper.deleteMessage(event);
        finishPlunder(game);
    }

    private static void sendPlunderButtons(Game game, Player player, Player opponent, int remaining) {
        List<Button> buttons = new ArrayList<>();
        if (opponent.getTg() > 0) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLUNDER + opponent.getFaction() + "|" + remaining + "|tg",
                    "Take 1 Trade Good",
                    MiscEmojis.tg));
        }
        if (opponent.getCommodities() > 0) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLUNDER + opponent.getFaction() + "|" + remaining + "|comm",
                    "Take 1 Commodity",
                    MiscEmojis.comm));
        }
        if (buttons.isEmpty()) {
            finishPlunder(game);
            return;
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + FINISH_PLUNDER, "Done Taking Resources"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose up to " + remaining + " resources to take from "
                        + opponent.getRepresentationNoPing() + " using _Plunderers_.",
                buttons);
    }

    private static boolean takeResource(Player player, Player opponent, String resource) {
        if ("tg".equals(resource) && opponent.getTg() > 0) {
            opponent.setTg(opponent.getTg() - 1);
            player.gainTG(1);
            return true;
        }
        if ("comm".equals(resource) && opponent.getCommodities() > 0) {
            opponent.setCommodities(opponent.getCommodities() - 1);
            player.gainTG(1);
            return true;
        }
        return false;
    }

    private static String resourceName(String resource) {
        return "comm".equals(resource) ? "commodity as 1 trade good" : "trade good";
    }

    private static void finishPlunder(Game game) {
        Player sarcosa = game.getPNOwner(PLUNDERERS);
        if (sarcosa == null) {
            return;
        }
        sarcosa.gainCommodities(1);
        MessageHelper.sendMessageToChannel(
                sarcosa.getCorrectChannel(),
                sarcosa.getRepresentationNoPing() + " gained 1 commodity from _Plunderers_.");
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
