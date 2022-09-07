package ti4.commands.admin;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.Constants;

public class Testing extends AdminSubcommandData {

    public Testing() {
        super(Constants.TESTING, "Testing new stuff");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        event.getHook().sendMessage("-").queue();
        Button button1 = Button.primary("test_button1", "Test Button1");
        Button button2 = Button.primary("test_button2", "Test Button2");
        Button button3 = Button.primary("test_button3", "Test Button3");

        Emoji emoji = Emoji.fromUnicode(":100:");

        Button button4 = Button.primary("test_button4", "Test Button4");
        Button button5 = Button.primary("test_button5", emoji);
        Message message = new MessageBuilder()
                .append("Testing message with Buttons")
                .setActionRows(ActionRow.of(button1, button2, button3),
                        ActionRow.of(button4)).build();
        event.getChannel().sendMessage(message).queue();
    }
}
