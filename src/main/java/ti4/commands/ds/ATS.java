package ti4.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class ATS extends GameStateSubcommand {

    public ATS() {
        super(Constants.LANEFIR_ATS_COUNT, "Set commodity count on the ATS Armaments technology", true, true);
        addOptions(new OptionData(OptionType.INTEGER, "count", "Count").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int count = Math.max(event.getOption("count").getAsInt(), 0);
        if (count > 0) {
            getPlayer().setAtsCount(count);
        }
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Set commodities count to " + count + " on _ATS Armaments_.");
    }
}
