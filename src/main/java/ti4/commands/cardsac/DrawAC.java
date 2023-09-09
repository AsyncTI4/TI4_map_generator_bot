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
        super(Constants.DRAW_AC, "Draw Action Card");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.COUNT);
        int count = 1;
        if (option != null) {
            int providedCount = option.getAsInt();
            count = providedCount > 0 ? providedCount : 1;
        }
        for (int i = 0; i < count; i++) {
            activeGame.drawActionCard(player.getUserID());
        }
        ACInfo.sendActionCardInfo(activeGame, player);
         ButtonHelper.checkACLimit(activeGame, event, player);
        if(player.getLeaderIDs().contains("yssarilcommander") && !player.hasLeaderUnlocked("yssarilcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "yssaril", event);
        }
    }
}
