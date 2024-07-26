package ti4.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DrawAC extends ACCardsSubcommandData {
    public DrawAC() {
        super(Constants.DRAW_AC, "Draw an action card.");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw (default: 1; max: 10)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found.");
            return;
        }
        OptionMapping option = event.getOption(Constants.COUNT);
        int count = 1;
        if (option != null) {
            int providedCount = option.getAsInt();
            count = providedCount > 0 ? providedCount : 1;
            if (count > 10) {
                count = 10;
            }
        }

        drawActionCards(game, player, count, false);
    }

    public static void drawActionCards(Game game, Player player, int count, boolean addScheming) {
        if (count > 10) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "You probably shouldn't need to ever draw more than 10 cards, double check what you're doing please.");
            return;
        }
        String message = player.getRepresentation() + " Drew " + count + " action card" + (count == 1 ? "" : "s") + ".";
        if (addScheming && player.hasAbility("scheming")) {
            message = "Drew [" + count + "+1=" + ++count + "] action cards (Scheming).";
        }

        for (int i = 0; i < count; i++) {
            game.drawActionCard(player.getUserID());
        }
        ACInfo.sendActionCardInfo(game, player);
        ButtonHelper.checkACLimit(game, null, player);
        if (addScheming && player.hasAbility("scheming")) ACInfo.sendDiscardActionCardButtons(game, player, false);
        if (player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "yssaril", null);
        }

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
    }
}
