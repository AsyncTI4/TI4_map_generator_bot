package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.draft.DraftItem;
import ti4.message.MessageHelper;

public class FrankenViewCard extends GameStateSubcommand {
    public FrankenViewCard() {

        super("view_card", "View a Frankendraft card from its alias", false, false);
        addOptions(new OptionData(OptionType.STRING, "alias", "card alias").setRequired(true));
    }
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping editOption = event.getOption("alias");
        String alias = editOption.getAsString();

        DraftItem item = DraftItem.GenerateFromAlias(alias);

        MessageHelper.sendMessageToEventChannel(event, item.getItemEmoji() + " " + item.getShortDescription() + "\n" + item.getLongDescription());
    }
}
