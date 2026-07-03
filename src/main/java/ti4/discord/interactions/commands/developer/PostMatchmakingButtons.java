package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.Subcommand;
import ti4.message.MessageHelper;

class PostMatchmakingButtons extends Subcommand {

    private static final String QUEUE_BUTTON_ID = "queueForGame~MDL";
    private static final String QUEUE_TIGL_BUTTON_ID = "queueForTigl~MDL";
    private static final String FORM_GROUP_BUTTON_ID = "formGroup~MDL";
    private static final String LEAVE_QUEUE_BUTTON_ID = "leaveQueueForGame";
    private static final String VIEW_QUEUE_BUTTON_ID = "viewMatchmakingQueue";
    private static final String ADDITIONAL_SETTINGS_BUTTON_ID = "queueForGameAdditionalSettings~MDL";

    private static final String OPTION_TIGL = "tigl";

    PostMatchmakingButtons() {
        super("post_matchmaking_buttons", "Post the matchmaking buttons.");
        addOptions(new OptionData(
                OptionType.BOOLEAN, OPTION_TIGL, "Post the TIGL variant (Queue for TIGL instead of Queue for Game)."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean tigl = event.getOption(OPTION_TIGL) != null
                && event.getOption(OPTION_TIGL).getAsBoolean();
        Button queueButton = tigl
                ? Buttons.green(QUEUE_TIGL_BUTTON_ID, "Queue for TIGL")
                : Buttons.green(QUEUE_BUTTON_ID, "Queue for Game");
        List<Button> buttons = new ArrayList<>();
        buttons.add(queueButton);
        buttons.add(Buttons.red(LEAVE_QUEUE_BUTTON_ID, "Leave Queue"));
        buttons.add(Buttons.blue(VIEW_QUEUE_BUTTON_ID, "View Queue"));
        if (!tigl) {
            buttons.add(Buttons.green(FORM_GROUP_BUTTON_ID, "Form Group"));
        }
        buttons.add(Buttons.gray(ADDITIONAL_SETTINGS_BUTTON_ID, "Additional Settings"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "You can use these buttons to search for a game. The more specific your preferences are, "
                        + "the longer it will take to find a game.",
                buttons);
    }
}
