package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class AddCustomPO extends GameStateSubcommand {

    public AddCustomPO() {
        super(
                Constants.ADD_CUSTOM,
                "Add a custom public objective (as a arbitrary source of victory points)",
                true,
                false);
        addOptions(new OptionData(OptionType.STRING, Constants.PO_NAME, "Public objective name").setRequired(true));
        addOptions(new OptionData(
                        OptionType.INTEGER, Constants.PO_VP_WORTH, "Victory points the public objective is worth")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        OptionMapping poNameOption = event.getOption(Constants.PO_NAME);
        if (poNameOption == null || poNameOption.getName().trim().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Must specify a name for the custom public objective");
            return;
        }

        if (poNameOption.getName().contains(",") || poNameOption.getName().contains(";")) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "The characters `;` and `,` are not allowed in the custom public objective name because reasons.");
            return;
        }
        OptionMapping vpOption = event.getOption(Constants.PO_VP_WORTH);
        if (vpOption == null || vpOption.getName().trim().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Must specify a name for the custom public objective");
            return;
        }
        String poName = poNameOption.getAsString();
        if (poName.contains(",")) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "The characters `;` and `,` are not allowed in the custom public objective name because reasons.");
            return;
        }
        int vp = vpOption.getAsInt();

        Integer poIndex = game.addCustomPO(poName, vp);
        String sb = "**Public Objective added:**" + "\n" + "(" + poIndex + ") " + "\n" + poName + "\n";
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);
    }
}
