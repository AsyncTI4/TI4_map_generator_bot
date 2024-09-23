package ti4.commands.cardsso;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DealSOToAll extends SOCardsSubcommandData {
    public DealSOToAll() {
        super(Constants.DEAL_SO_TO_ALL, "Deal Secret Objective (count) to all game players");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        dealSOToAll(event, count, game);
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
                SOInfo.sendSecretObjectiveInfo(game, player, event);
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), count + Emojis.SecretObjective + " dealt to all players. Check your Cards-Info threads.");
        if (game.getRound() == 1) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("startOfGameObjReveal", "Reveal Objectives and Start Strategy Phase"));
            MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), "Press this button after everyone has discarded", buttons);
            Player speaker = null;
            if (game.getPlayer(game.getSpeaker()) != null) {
                speaker = game.getPlayers().get(game.getSpeaker());
            }
            if (speaker == null) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Speaker is not yet assigned. Secrets have been dealt, but please assign speaker soon (command is `/player speaker`)");
            }
            // List<Button> buttons2 = new ArrayList<>();
            // buttons2.add(Buttons.green("setOrder", "Set Speaker Order"));
            // buttons2.add(Buttons.red("deleteButtons", "Decline"));
            // MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(),
            //     game.getPing() + " if your map has all players' HS in the same ring, you should set speaker order using this button", buttons2);
            Helper.setOrder(game);
        }
    }
}
