package ti4.commands2.button;

import java.util.Collections;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands2.ParentCommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class GenericButtonCommand implements ParentCommand {

    @Override
    public List<OptionData> getOptions() {
        return List.of(
            new OptionData(OptionType.STRING, Constants.BUTTON_TEXT, "The text/prompt that will appear on the button itself. Max 80 characters.").setRequired(true),
            new OptionData(OptionType.STRING, "spoof_id", "Spoof the buttonID, mainly for debugging purposes"));
    }

    @Override
    public String getName() {
        return Constants.BUTTON;
    }

    public String getDescription() {
        return "Send a single generic button to the channel to collect faction responses.";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String buttonText = event.getOption(Constants.BUTTON_TEXT, "Button", OptionMapping::getAsString);
        String message = null;
        // Max button text is 80, so if higher, post a separate message and just ask to record responses
        if (buttonText.length() > 80) {
            message = buttonText;
            buttonText = "Record Response";
        }

        String id = event.getOption("spoof_id", Constants.GENERIC_BUTTON_ID_PREFIX + event.getId(), OptionMapping::getAsString);
        Button button = Buttons.gray(id, buttonText);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, Collections.singletonList(button));
    }
}
