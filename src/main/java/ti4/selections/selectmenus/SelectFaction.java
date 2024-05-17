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
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.selections.Selection;

public class SelectFaction implements Selection {

    public static final String selectionID = "selectFaction";

    @Override
    public String getSelectionID() {
        return selectionID;
    }

    @Override
    public void execute(StringSelectInteractionEvent event) {
        Game activeGame = GameManager.getInstance().getUserActiveGame(event.getUser().getId());
        if (activeGame == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Game could not be found");
            return;
        }
        Player player = activeGame.getPlayer(event.getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player could not be found");
            return;
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You selected: " + event.getSelectedOptions().get(0).getLabel());
        String fakeButtonID = selectionID + "_" + event.getUser().getId() + "_" + event.getValues().get(0);
        ButtonHelper.resolveSetupStep2(player, activeGame, event, fakeButtonID);
    }

    public static void offerFactionSelectionMenu(GenericInteractionCreateEvent event) {
        List<FactionModel> factions = Mapper.getFactions().stream().sorted(Comparator.comparing(FactionModel::getFactionName)).sorted(Comparator.comparing(FactionModel::getSource)).toList();
        List<List<FactionModel>> factionPages = ListUtils.partition(factions, 25);
        List<StringSelectMenu> menus = new ArrayList<>();

        for (List<FactionModel> factionPage : factionPages) {
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(selectionID);
            for (FactionModel faction : factionPage) {
                Emoji emojiToUse = Emoji.fromFormatted(Emojis.getFactionIconFromDiscord(faction.getAlias()));
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
