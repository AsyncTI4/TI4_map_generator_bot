package ti4.commands.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DealSOToAll extends SOCardsSubcommandData {
    public DealSOToAll() {
        super(Constants.DEAL_SO_TO_ALL, "Deal Secret Objective (count) to all game players");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        OptionMapping option = event.getOption(Constants.COUNT);
        int count = 1;
        if (option != null) {
            int providedCount = option.getAsInt();
            count = providedCount > 0 ? providedCount : 1;
        }
        for (Player player : activeMap.getPlayers().values()) {
            if (player.getFaction() != null && player.getColor() != null && !player.getColor().equals("null")) {
                for (int i = 0; i < count; i++) {
                    activeMap.drawSecretObjective(player.getUserID());
                }
                SOInfo.sendSecretObjectiveInfo(activeMap, player, event);
            }
        }
        sendMessage(count + " SO Dealt to All");
    }
}
