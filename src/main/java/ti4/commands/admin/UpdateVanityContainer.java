package ti4.commands.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.buttons.Buttons;
import ti4.commands.Subcommand;
import ti4.message.componentsV2.MessageV2Builder;
import ti4.message.componentsV2.MessageV2Editor;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.SourceEmojis;

class UpdateVanityContainer extends Subcommand {

    private static final String MESSAGE_ID = "message_id";
    private static final String MESSAGE_ID_TXT = "Add or remove from the embed";

    public UpdateVanityContainer() {
        super("update_vanity_container", "Update the vanity role embed in this channel");
        addOption(OptionType.STRING, MESSAGE_ID, MESSAGE_ID_TXT);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String messageID = event.getOption(MESSAGE_ID, null, OptionMapping::getAsString);

        // update the embed from the event channel
        if (messageID == null) {
            MessageV2Builder builder = new MessageV2Builder(event.getMessageChannel());
            builder.append(generateFreshContainer());
            builder.send();
        } else {
            event.getChannel()
                    .getHistoryAround(messageID, 1)
                    .onSuccess(updateEmbed())
                    .complete();
        }
    }

    private static Consumer<MessageHistory> updateEmbed() {
        return msgs -> {
            List<Message> list = msgs.getRetrievedHistory();
            if (list.isEmpty()) return;

            MessageV2Editor editor = new MessageV2Editor();
            editor.replace(m -> true, generateFreshContainer());
            editor.applyToMessage(list.getFirst());
        };
    }

    private static Container generateFreshContainer() {
        List<ContainerChildComponent> components = new ArrayList<>();
        String spiel = "# Rep Your Favorite Faction!!!\n";
        spiel += "> Use the buttons below to get a role asssociated with your favorite faction.";
        spiel += " You'll get a color and your faction symbol on this server";
        components.add(TextDisplay.of(spiel));

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("chooseVanityRole_Base", "Base Game", SourceEmojis.TI4BaseGame));
        buttons.add(Buttons.green("chooseVanityRole_PoK", "Prophecy of Kings", SourceEmojis.PoK));
        buttons.add(Buttons.green("chooseVanityRole_TE", "Thunder's Edge", SourceEmojis.ThundersEdgeIcon));
        buttons.add(Buttons.green("chooseVanityRole_TF", "Twilight's Fall", MiscEmojis.tf_paradigm));
        // buttons.add(Buttons.green("chooseVanityRole_DS", "Discordant Stars", SourceEmojis.DiscordantStars));
        components.add(ActionRow.of(buttons));

        return Container.of(components);
    }
}
