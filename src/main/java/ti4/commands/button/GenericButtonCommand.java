package ti4.commands.button;

import java.util.Collections;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.buttons.Buttons;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.helpers.SlashCommandAcceptanceHelper;
import ti4.message.MessageHelper;

public class GenericButtonCommand implements Command {

    @Override
    public String getActionId() {
        return Constants.BUTTON;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfActivePlayerOfGame(getActionId(), event);
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

    protected String getActionDescription() {
        return "Send a single generic button to the channel to collect faction responses.";
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionId(), getActionDescription())
                .addOptions(new OptionData(OptionType.STRING, Constants.BUTTON_TEXT, "The text/prompt that will appear on the button itself. Max 80 characters.").setRequired(true))
                .addOptions(new OptionData(OptionType.STRING, "spoof_id", "Spoof the buttonID, mainly for debugging purposes"))
        );
    }
}
