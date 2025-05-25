package ti4.selections.selectmenus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.apache.commons.collections4.ListUtils;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.listeners.context.SelectionMenuContext;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.selections.Selection;
import ti4.service.emoji.FactionEmojis;

public class SelectFaction implements Selection {

    public static final String selectionID = "selectFaction";

    @Override
    public String getSelectionID() {
        return selectionID;
    }

    @Override
    public void execute(StringSelectInteractionEvent event, SelectionMenuContext context) {
        Game game = context.getGame();
        if (game == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Game could not be found");
            return;
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You selected: " + event.getSelectedOptions().getFirst().getLabel());

        String fakeButtonID = selectionID + "_" + event.getUser().getId() + "_" + event.getValues().getFirst();
        ButtonHelper.resolveSetupStep2(game, event, fakeButtonID);
    }

    public static void offerFactionSelectionMenu(GenericInteractionCreateEvent event) {
        List<FactionModel> factions = Mapper.getFactionsValues().stream().sorted(Comparator.comparing(FactionModel::getFactionName)).sorted(Comparator.comparing(FactionModel::getSource)).toList();
        List<List<FactionModel>> factionPages = ListUtils.partition(factions, 25);
        List<StringSelectMenu> menus = new ArrayList<>();

        for (List<FactionModel> factionPage : factionPages) {
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(selectionID);
            for (FactionModel faction : factionPage) {
                Emoji emojiToUse = FactionEmojis.getFactionIcon(faction.getAlias()).asEmoji();
                SelectOption option = SelectOption.of(faction.getFactionName(), faction.getAlias())
                    .withDescription(faction.getAlias())
                    .withLabel(faction.getAutoCompleteName());
                option = option.withEmoji(emojiToUse);
                menuBuilder.addOptions(option);
            }
            menuBuilder.setRequiredRange(1, 1);
            menus.add(menuBuilder.build());
        }
        for (StringSelectMenu menu : menus) {
            event.getMessageChannel().sendMessage("").addComponents(ActionRow.of(menu)).queue();
        }
    }
}
