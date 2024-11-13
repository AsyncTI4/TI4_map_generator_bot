package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class AddCustomPO extends StatusSubcommandData {
    public AddCustomPO() {
        super(Constants.ADD_CUSTOM, "Add custom Public Objective");
        addOptions(new OptionData(OptionType.STRING, Constants.PO_NAME, "Public Objective name").setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.PO_VP_WORTH, "Public Objective worth in VP").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        OptionMapping poNameOption = event.getOption(Constants.PO_NAME);
        if (poNameOption == null || poNameOption.getName().trim().isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify Public Objective Name");
            return;
        }

        if (poNameOption.getName().contains(",") || poNameOption.getName().contains(";")) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Symbol ; or , is not allowed");
            return;
        }
        OptionMapping vpOption = event.getOption(Constants.PO_VP_WORTH);
        if (vpOption == null || vpOption.getName().trim().isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify Public Objective Name");
            return;

        }
        String poName = poNameOption.getAsString();
        if (poName.contains(",")) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Objective must not contain comma ,");
            return;
        }
        int vp = vpOption.getAsInt();

        Integer poIndex = game.addCustomPO(poName, vp);
        String sb = "**Public Objective added:**" + "\n" +
            "(" + poIndex + ") " + "\n" +
            poName + "\n";
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);
    }
}
