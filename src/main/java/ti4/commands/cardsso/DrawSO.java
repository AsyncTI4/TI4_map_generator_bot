package ti4.commands.cardsso;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DrawSO extends SOCardsSubcommandData {
    public DrawSO() {
        super(Constants.DRAW_SO, "Draw Secret Objective");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        count = Math.max(count, 1);
        drawSO(event, game, player, count, false);
    }

    public static void drawSO(GenericInteractionCreateEvent event, Game game, Player player) {
        drawSO(event, game, player, 1, true);
    }

    public static void drawSO(GenericInteractionCreateEvent event, Game game, Player player, int count, boolean useTnelis) {
        String output = "Drew " + count + " Secret Objective" + (count > 1 ? "s" : "");
        if (useTnelis && player.hasAbility("plausible_deniability")) {
            output = "Used Plausible Deniablity to draw [" + count + " + 1 = " + (count + 1) + "] Secret Objectives";
            count++;
        }
        for (int i = 0; i < count; i++) {
            game.drawSecretObjective(player.getUserID());
        }
        MessageHelper.sendMessageToEventChannel(event, player.getRepresentation() + " " + output);
        SOInfo.sendSecretObjectiveInfo(game, player, event);
        if (useTnelis && player.hasAbility("plausible_deniability")) {
            SOInfo.sendSODiscardButtons(game, player);
        }
    }
}
