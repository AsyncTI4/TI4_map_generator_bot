package ti4.commands.admin;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.ButtonListener;
import ti4.helpers.Constants;
import ti4.message.BotLogger;

import java.util.List;

public class Testing extends AdminSubcommandData {

    public Testing() {
        super(Constants.TESTING, "Testing new stuff");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        List<Emote> emotes = event.getJDA().getEmotes();
        for (Emote emote : emotes) {
            BotLogger.log(emote.getName() + " " + emote.getIdLong());
        }

//        Emoji emoji = Emoji.fromMarkdown("\uD83D\uDEAB");
//        Emoji sabotage = Emoji.fromMarkdown(":sabotage:");
//        Emoji noSabo = Emoji.fromMarkdown(":nosabo:");
//
//
//        if (System.getenv("TESTING").equals("true")){
//            sabotage = emoji;
//            noSabo = emoji;
//        }
//
//        event.getHook().sendMessage("-").queue();
//
//        Button button1 = Button.danger("sabotage", "Sabotage").withEmoji(sabotage);
//        Button button2 = Button.primary("no_sabotage", "No Sabotage").withEmoji(noSabo);
//        Button button3 = Button.danger("test_button3", "Test Button3");
//
//
//
//
//        Button button4 = Button.danger("test_button4", "Sabotage");
//        Button button5 = Button.primary("test_button5", "No Sabotage").withEmoji(sabotage);
//
//        Message message = new MessageBuilder()
//                .append("Testing message with Buttons")
//                .setActionRows(ActionRow.of(button1, button2, button3),
//                        ActionRow.of(button4, button5)).build();
////        String id = message.getId();
//
//
////        message.addReaction();
//        event.getChannel().sendMessage(message).queue();
    }
}
