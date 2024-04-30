package ti4.commands.franken;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands.game.SetOrder;
import ti4.draft.BagDraft;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;
import ti4.draft.DraftItem.Category;
import ti4.draft.items.SpeakerOrderDraftItem;
import ti4.helpers.Constants;
import ti4.helpers.FrankenDraftHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ApplyDraftBags extends FrankenSubcommandData {

    public ApplyDraftBags() {
        super("apply_draft_bags", "Begin selecting items from draft bags to apply them to your faction.");
        addOption(OptionType.BOOLEAN, Constants.FORCE, "Force apply current bags, even if the bag draft is not complete.");
    }

    final List<DraftItem.Category> categories = List.of(
        Category.ABILITY,
        Category.TECH,
        Category.AGENT,
        Category.COMMANDER,
        Category.HERO,
        Category.MECH,
        Category.FLAGSHIP,
        Category.COMMODITIES,
        Category.PN,
        Category.HOMESYSTEM,
        Category.STARTINGTECH,
        Category.STARTINGFLEET
    // Category.BLUETILE,
    // Category.REDTILE,
    // Category.DRAFTORDER
    );

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        BagDraft draft = game.getActiveBagDraft();

        boolean force = event.getOption(Constants.FORCE, false, OptionMapping::getAsBoolean);
        if (!draft.isDraftStageComplete() && !force) {
            String message = "The draft stage of the FrankenDraft is NOT complete. Please finish the draft or rerun the command with the force option set.";
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), message);
            return;
        }

        setSpeakerOrder(event, game); // Category.DRAFTORDER

        for (Player player : game.getPlayers().values()) {
            DraftBag bag = player.getDraftHand();
            for (DraftItem.Category category : categories) {
                List<DraftItem> items = bag.Contents.stream().filter(item -> item.ItemCategory == category).toList();
                if (items.isEmpty()) {
                    continue;
                }
                List<Button> buttons = new ArrayList<>();
                for (DraftItem item : items) {
                    buttons.add(item.getAddButton().withEmoji(Emoji.fromFormatted(item.getItemEmoji())));
                }
                String message = FrankenDraftHelper.getLongCategoryRepresentation(draft, bag, category) +
                    "\nClick the buttons below to add or remove items from your faction.";
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
            }
        }

        //start building map // Category.BLUETILE, Category.REDTILE
        game.setShowMapSetup(true);
    }

    private static void setSpeakerOrder(SlashCommandInteractionEvent event, Game game) {
        List<User> users = game.getPlayers().values().stream()
            .filter(player -> player.getDraftHand() != null)
            .filter(player -> player.getDraftHand().Contents.stream().anyMatch(item -> item.ItemCategory == DraftItem.Category.DRAFTORDER))
            .sorted(Comparator.comparing(player -> {
                SpeakerOrderDraftItem order = (SpeakerOrderDraftItem) player.getDraftHand().Contents.stream()
                    .filter(item -> item.ItemCategory == DraftItem.Category.DRAFTORDER)
                    .findFirst()
                    .get();
                return order.getSpeakerOrder();
            }))
            .map(Player::getUser)
            .toList();
        SetOrder.setPlayerOrder(event, game, users);
    }
}
