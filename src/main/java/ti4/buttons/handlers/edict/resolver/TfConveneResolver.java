package ti4.buttons.handlers.edict.resolver;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.buttons.handlers.edict.EdictResolveButtonHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class TfConveneResolver implements EdictResolver {

    @Getter
    public String edict = "tf-convene";

    public void handle(ButtonInteractionEvent event, Game game, Player player) {
        game.setStoredValue("conveneStarter", player.getFaction());
        List<String> techs = ButtonHelperTwilightsFall.getDeckForSplicing(
                game, "ability", game.getRealPlayers().size());
        List<MessageEmbed> embeds = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();

        for (String tech : techs) {
            buttons.add(Buttons.green(
                    "conveneStep1_" + tech, "Assign " + Mapper.getTech(tech).getName()));
            embeds.add(Mapper.getTech(tech).getRepresentationEmbed());
        }
        MessageHelper.sendMessageToChannelWithEmbeds(
                player.getCorrectChannel(), "_Convene_ has revealed these abilities.", embeds);

        Player speaker = game.getSpeaker();
        String addl = "You need to assign the first ability to " + player.getRepresentation()
                + ", then that player assigns the rest.";
        MessageHelper.sendMessageToChannelWithButtons(speaker.getCorrectChannel(), playerPing(speaker, addl), buttons);
    }

    @ButtonHandler("conveneStep1_")
    public static void conveneStep1_(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String cardID = buttonID.split("_")[1];
        if (player == game.getSpeaker()
                && !game.getStoredValue("conveneStarter").isEmpty()) {
            Player tyrant = EdictResolveButtonHandler.getEdictResolver(game);
            game.removeStoredValue("conveneStarter");
            game.setStoredValue("convenePlayers", game.getStoredValue("convenePlayers") + tyrant.getFaction());
            tyrant.addTech(cardID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    game.getActionsChannel(),
                    tyrant.getRepresentation() + " has acquired the ability: "
                            + Mapper.getTech(cardID).getName(),
                    Mapper.getTech(cardID).getRepresentationEmbed());
        } else {
            List<Button> buttons = new ArrayList<>();
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player || game.getStoredValue("convenePlayers").contains(p2.getFaction())) {
                    continue;
                }
                buttons.add(Buttons.green(
                        "conveneStep2_" + cardID + "_" + p2.getFaction(),
                        p2.getFactionNameOrColor(),
                        p2.fogSafeEmoji()));
            }
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", please choose the player you wish to give _"
                            + Mapper.getTech(cardID).getName() + "_ to.",
                    buttons);
        }

        Player tyrant = EdictResolveButtonHandler.getEdictResolver(game);
        String msg = tyrant.getRepresentationUnfogged() + ", the speaker has chosen an ability to";
        msg += " assign to you. You must now choose abilities to assign to the rest of the table";
        if (game.isFowMode()) {
            if (player.isSpeaker()) {
                msg += " using the buttons below:";
                List<Button> buttons = event.getMessage().getComponentTree().findAll(Button.class).stream()
                        .filter(b -> !b.getCustomId().contains(buttonID))
                        .toList();
                MessageHelper.sendMessageToChannelWithButtons(tyrant.getCorrectChannel(), msg, buttons);
                ButtonHelper.deleteMessage(event);
            } else {
                ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            }
        } else {
            if (player.isSpeaker()) {
                msg += " using the remaining buttons above.";
                MessageHelper.replyToMessage(event, msg);
            }
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
    }

    @ButtonHandler("conveneStep2_")
    public static void conveneStep2(ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        String cardID = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[2]);
        game.setStoredValue("convenePlayers", game.getStoredValue("convenePlayers") + p2.getFaction());
        p2.addTech(cardID);

        MessageHelper.sendMessageToChannelWithEmbed(
                game.getActionsChannel(),
                p2.getRepresentation() + " has acquired the ability: "
                        + Mapper.getTech(cardID).getName(),
                Mapper.getTech(cardID).getRepresentationEmbed());
        ButtonHelper.deleteMessage(event);
    }
}
