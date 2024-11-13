package ti4.commands.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DealSO extends GameStateSubcommand {

    public DealSO() {
        super(Constants.DEAL_SO, "Deal Secret Objective", true,  true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to deal, default 1"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        count = Math.max(count, 1);
        for (int i = 0; i < count; i++) {
            game.drawSecretObjective(player.getUserID());
        }
        MessageHelper.sendMessageToEventChannel(event, count + " SO Dealt");
        SOInfo.sendSecretObjectiveInfo(game, player, event);
    }
}
