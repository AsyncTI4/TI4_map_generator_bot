package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.message.MessageHelper;

public class SCCount extends GameSubcommandData {
    public SCCount() {
        super(Constants.SC_COUNT, "Strategy Cards count in game");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC, "Strategy Cards count").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        OptionMapping scOption = event.getOption(Constants.SC);
        if (scOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify Strategy Card");
            return;

        }

        int sc = scOption.getAsInt();
        if (sc < 8) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Strategy Card is by default 8, if you want more, please select number greater that 8");
            return;
        }
        for (int i = 8; i < sc; i++) {
            activeMap.setScTradeGood(i + 1, 0);
        }
    }
}
