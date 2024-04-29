package ti4.commands.franken;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.game.SetOrder;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;
import ti4.draft.items.SpeakerOrderDraftItem;
import ti4.map.Game;
import ti4.map.Player;

public class ApplyDraftBags extends FrankenSubcommandData {

    public ApplyDraftBags() {
        super("apply_draft_bags", "Begin selecting items from draft bags to apply them to your faction.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        for (Player player : game.getPlayers().values()) {
            DraftBag bag = player.getDraftHand();
            List<DraftItem.Category> categories = bag.Contents.stream().map(item -> item.ItemCategory).distinct().collect(Collectors.toList());
        }

        setSpeakerOrder(event, game);
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

    private static void getFrankenApplyButtons() {

    }

}
