package ti4.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

class DishonorCount extends GameStateSubcommand {

    public DishonorCount() {
        super(Constants.DISHONOR_COUNT, "Set dishonor amount", true, true);
        addOptions(new OptionData(OptionType.INTEGER, "count", "Count").setRequired(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int count = Math.max(event.getOption("count").getAsInt(), 0);
        Player player = getPlayer();
        player.setDishonorCounter(count);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Set dishonor count to " + count + ".");
        ButtonHelperFactionSpecific.correctHonorAbilities(player, getGame());
    }
}
