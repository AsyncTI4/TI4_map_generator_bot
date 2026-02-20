package ti4.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.map.Player;
import ti4.message.MessageHelper;

class StarBalanceCount extends GameStateSubcommand {

    public StarBalanceCount() {
        super("steelbalance_count", "Set steel balance amount", true, true);
        addOptions(new OptionData(OptionType.INTEGER, "count", "Count").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int count = Math.max(event.getOption("count").getAsInt(), 0);
        Player player = getPlayer();
        player.setSteelbalanceCounter(count);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Set Steel Balance count to " + count + ".");
    }
}
