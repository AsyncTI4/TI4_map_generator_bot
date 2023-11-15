package ti4.selections.selectmenus;

import java.util.List;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import ti4.generator.Mapper;
import ti4.helpers.Emojis;
import ti4.model.FactionModel;
import ti4.selections.Selection;

public class SelectFaction implements Selection {

    public static final String selectionID = "select_faction";

    @Override
    public String getSelectionID() {
        return selectionID;
    }

    @Override
    public void execute(StringSelectInteractionEvent event) {
        event.reply("hello").queue();
        event.getChannel().sendMessage("You're selecting a faction!").queue();
    }

    public static void offerFactionSelectionMenu(GenericInteractionCreateEvent event) {
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(selectionID);
        List<FactionModel> factions = Mapper.getFactions().stream().limit(25).toList();
        for (FactionModel faction : factions) {
            menuBuilder.addOptions(SelectOption.of(faction.getFactionName(), faction.getAlias())
                .withDescription(faction.getAlias())
                .withEmoji(Emoji.fromFormatted(Emojis.getFactionIconFromDiscord(faction.getAlias())))
                .withLabel(faction.getAutoCompleteName())
            );
        }
        menuBuilder.setRequiredRange(1, 1);
        StringSelectMenu menu = menuBuilder.build();
        event.getMessageChannel().sendMessage("Select a faction:").addComponents(ActionRow.of(menu)).queue();
    }

    
}
