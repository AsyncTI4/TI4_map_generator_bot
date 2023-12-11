package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.draft.DraftItem;
import ti4.helpers.Constants;

public class FrankenViewCard extends FrankenSubcommandData {
    public FrankenViewCard() {

        super("view_card", "Frankendraft Edit Commands");
        addOptions(new OptionData(OptionType.STRING, "alias", "card alias").setRequired(true));
    }
    @Override
    public void execute(SlashCommandInteractionEvent event) {

        OptionMapping editOption = event.getOption("alias");
        String alias = editOption.getAsString();

        DraftItem item = DraftItem.GenerateFromAlias(alias);

        sendMessage(item.getItemEmoji() + " " + item.getShortDescription() + "\n" + item.getLongDescription());
    }
}
