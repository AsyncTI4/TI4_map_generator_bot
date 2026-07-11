package ti4.discord.interactions.buttons.handlers.faction.discordantstars.cymiae;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
class CymiaeButtonHandler {

    @ButtonHandler("cymiaeHeroAutonetic")
    public static void cymiaeHeroAutonetic(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = new ArrayList<>();
        String msg2 = player.getRepresentationNoPing() + " is choosing to resolve their **Autonetic Memory** ability.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        buttons.add(Buttons.green("autoneticMemoryStep3a", "Pick Action Card From the Discard"));
        buttons.add(Buttons.blue("autoneticMemoryStep3b", "Drop 1 infantry"));
        String msg = player.getRepresentationUnfogged()
                + ", you have the ability to either draw a card from the discard (and then discard a card) or place 1 infantry on a planet you control.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        buttons = new ArrayList<>();
        buttons.add(Buttons.green("cymiaeHeroStep1_" + (game.getRealPlayers().size()), "Resolve Cymiae Hero"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.toString() + ", please resuming resolving your hero after doing **Autonetic Memory** steps.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("cymiaeHeroStep1_")
    public static void resolveCymiaeHeroStart(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String num = buttonID.split("_")[1];
        int n = Integer.parseInt(num);
        List<Button> buttons = new ArrayList<>();
        MessageChannel channel = player.getCorrectChannel();
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            channel = player.getCardsInfoThread();
        }
        for (int x = 0; x < n; x++) {
            String acID = game.drawActionCardAndDiscard();
            String sb = Mapper.getActionCard(acID).getRepresentation(game) + "\n";
            MessageHelper.sendMessageToChannel(channel, sb);
            buttons.add(Buttons.green(
                    "cymiaeHeroStep2_" + acID, Mapper.getActionCard(acID).getName()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                channel, player.toString() + ", please use the buttons to give out action cards to players.", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("cymiaeHeroStep2_")
    public static void resolveCymiaeHeroStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String acID = buttonID.replace("cymiaeHeroStep2_", "");
        List<Button> buttons = new ArrayList<>();
        MessageChannel channel = player.getCorrectChannel();
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            channel = player.getCardsInfoThread();
        }
        for (Player p2 : game.getRealPlayers()) {
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("cymiaeHeroStep3_" + p2.getFaction() + "_" + acID, p2.getColor()));
            } else {
                Button button = Buttons.gray(
                        "cymiaeHeroStep3_" + p2.getFaction() + "_" + acID,
                        p2.getFactionModel().getShortName());
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                player.getRepresentationUnfogged() + ", please choose who you wish to give "
                        + Mapper.getActionCard(acID).getName() + " to.",
                buttons);
    }

    @ButtonHandler("cymiaeHeroStep3_")
    public static void resolveCymiaeHeroStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String acID = buttonID.replace("cymiaeHeroStep3_" + p2.getFaction() + "_", "");
        boolean picked =
                game.pickActionCard(p2.getUserID(), game.getDiscardActionCards().get(acID));
        if (!picked) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
            return;
        }
        ActionCardHelper.sendActionCardInfo(game, p2, event);
        if ("action".equalsIgnoreCase(game.getPhaseOfGame())) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + " has given "
                            + Mapper.getActionCard(acID).getName() + " to " + p2.toString() + ".");
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.toString() + " has given an action card to " + p2.toString() + ".");
        }
        ButtonHelper.deleteMessage(event);
        if (p2 != player && "action".equalsIgnoreCase(game.getPhaseOfGame())) {
            MessageHelper.sendMessageToChannel(
                    p2.getCardsInfoThread(),
                    "The Voice United, the Cymiae hero, has given "
                            + Mapper.getActionCard(acID).getName()
                            + " to you and you now have to discard 1 action card.");
            String msg = p2.getRepresentationUnfogged() + " use buttons to discard.";
            List<Button> buttons = ActionCardHelper.getDiscardActionCardButtons(p2, false);
            MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
        }
    }
}
