package ti4.commands.cards;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DealSO extends CardsSubcommandData {
    public DealSO() {
        super(Constants.DEAL_SO, "Deal Secret Objective");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to deal, default 1"));
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

        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        if (playerOption != null) {
            User user = playerOption.getAsUser();
            Player player = activeMap.getPlayer(user.getId());
            for (int i = 0; i < count; i++) {
                activeMap.drawSecretObjective(player.getUserID());
            }
            CardsInfo.sentUserCardInfo(event, activeMap, player);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
        }
    }
}
