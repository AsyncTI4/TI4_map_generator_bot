package ti4.commands.cardsso;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.MapGenerator;
import ti4.commands.cards.CardsInfo;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;

public class DealSO extends SOCardsSubcommandData {
    public DealSO() {
        super(Constants.DEAL_SO, "Deal Secret Objective");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
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

        Player player_ = Helper.getPlayer(activeMap, null, event);
        if (player_ == null) {
            sendMessage("Player not found");
            return;
        }
        User user = MapGenerator.jda.getUserById(player_.getUserID());
        if (user == null) {
            sendMessage("User for faction not found. Report to ADMIN");
            return;
        }

        for (int i = 0; i < count; i++) {
            activeMap.drawSecretObjective(player_.getUserID());
        }
        sendMessage(count + " SO Dealt");
        CardsInfo.sentUserCardInfo(event, activeMap, player_);
    }
}
