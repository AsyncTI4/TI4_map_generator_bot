package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

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
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.strategycard.PlayStrategyCardService;
import ti4.service.strategycard.StrategyCardSecondaryButtonService;

@UtilityClass
class OverthrowAcd2ButtonHandler {

    @ButtonHandler("resolveOverthrow")
    public static void resolveOverthrow(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        List<Button> buttons = List.of(
                Buttons.green(player.factionButtonChecker() + "overthrowChoosePrimary", "Perform Primary"),
                Buttons.blue(player.factionButtonChecker() + "overthrowChooseSecondary", "Perform Secondary"),
                Buttons.red("deleteButtons", "Done resolving"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", resolve an ability of 1 of the strategy cards of the player you took the planet from."
                        + " Perform the **primary** ability if that planet is in their home system, and the"
                        + " **secondary** ability otherwise.",
                buttons);
    }

    @ButtonHandler("overthrowChoosePrimary")
    public static void chooseOverthrowPrimary(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the strategy card whose **primary** ability you are resolving for _Overthrow_.",
                getPrimaryAbilityButtons(game, player));
    }

    @ButtonHandler("overthrowChooseSecondary")
    public static void chooseOverthrowSecondary(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the **secondary** ability you are resolving for _Overthrow_.",
                getSecondaryAbilityButtons(game));
    }

    @ButtonHandler("overthrowPrimary_")
    public static void resolveOverthrowPrimary(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        int sc = Integer.parseInt(buttonID.split("_")[1]);
        ButtonHelper.deleteMessage(event);
        PlayStrategyCardService.playSC(event, sc, game, game.getMainGameChannel(), player, true, true, "overthrown");
    }

    private static List<Button> getPrimaryAbilityButtons(Game game, Player player) {
        List<Button> scButtons = new ArrayList<>();
        for (int sc : game.getSCList()) {
            if (sc <= 0) continue;
            String buttonID = player.factionButtonChecker() + "overthrowPrimary_" + sc;
            String label = Helper.getSCName(sc, game);
            TI4Emoji scEmoji = CardEmojis.getSCBackFromInteger(sc);
            if (scEmoji != CardEmojis.SCBackBlank && !game.isHomebrewSCMode()) {
                scButtons.add(Buttons.gray(buttonID, label, scEmoji));
            } else {
                scButtons.add(Buttons.gray(buttonID, sc + " " + label));
            }
        }
        scButtons.add(Buttons.red("deleteButtons", "Done resolving"));
        return scButtons;
    }

    private static List<Button> getSecondaryAbilityButtons(Game game) {
        List<Button> scButtons =
                new ArrayList<>(StrategyCardSecondaryButtonService.getSecondaryAbilityButtons(game, game.getSCList()));
        scButtons.add(Buttons.red("deleteButtons", "Done resolving"));
        return scButtons;
    }
}
