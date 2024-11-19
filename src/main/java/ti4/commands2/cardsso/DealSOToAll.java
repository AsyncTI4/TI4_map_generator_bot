package ti4.commands2.cardsso;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class DealSOToAll extends GameStateSubcommand {

    public DealSOToAll() {
        super(Constants.DEAL_SO_TO_ALL, "Deal Secret Objective (count) to all game players", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        dealSOToAll(event, count, getGame());
    }

    public static void dealSOToAll(GenericInteractionCreateEvent event, int count, Game game) {
        if (count > 0) {
            for (Player player : game.getRealPlayers()) {
                for (int i = 0; i < count; i++) {
                    game.drawSecretObjective(player.getUserID());
                }
                if (player.hasAbility("plausible_deniability")) {
                    game.drawSecretObjective(player.getUserID());
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " due to Plausible Deniability, you were dealt an extra SO. You must also discard an extra SO.");
                }
                SecretObjectiveHelper.sendSecretObjectiveInfo(game, player, event);
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), count + Emojis.SecretObjective + " dealt to all players. Check your Cards-Info threads.");
        if (game.getRound() == 1) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("startOfGameObjReveal", "Reveal Objectives and Start Strategy Phase"));
            MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), "Press this button after everyone has discarded", buttons);
            Player speaker = null;
            if (game.getPlayer(game.getSpeakerUserID()) != null) {
                speaker = game.getPlayers().get(game.getSpeakerUserID());
            }
            if (speaker == null) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Speaker is not yet assigned. Secrets have been dealt, but please assign speaker soon (command is `/player speaker`)");
            }
            Helper.setOrder(game);
        }
    }

    @ButtonHandler("deal2SOToAll")
    public static void deal2SOToAll(ButtonInteractionEvent event, Game game) {
        dealSOToAll(event, 2, game);
        ButtonHelper.deleteMessage(event);
    }
}
