package ti4.commands.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Map;
import ti4.map.Player;

public class DealSOToAll extends SOCardsSubcommandData {
    public DealSOToAll() {
        super(Constants.DEAL_SO_TO_ALL, "Deal Secret Objective (count) to all game players");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        if (count < 1) {
            sendMessage("`count` option must be greater than 0");
            return;
        }

        for (Player player : activeMap.getRealPlayers()) {
                for (int i = 0; i < count; i++) {
                    activeMap.drawSecretObjective(player.getUserID());
                }
                SOInfo.sendSecretObjectiveInfo(activeMap, player, event);
        }
        sendMessage(count + Emojis.SecretObjective + " dealt to all players. Check your Cards-Info threads.");
    }
}
