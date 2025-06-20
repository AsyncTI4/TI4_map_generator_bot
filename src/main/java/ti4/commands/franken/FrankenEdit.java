package ti4.commands.franken;

import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.franken.FrankenDraftBagService;

class FrankenEdit extends GameStateSubcommand {
    private final String QUEUE_INDEX = "queue_index";

    public FrankenEdit() {
        super(Constants.FRANKEN_EDIT, "Frankendraft Edit Commands", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FRANKEN_EDIT_ACTION, "add/remove/swap/view")
            .addChoice("Force Bag Swap", "forceSwap")
            .addChoice("Add Card To Bag", "addBag").addChoice("Remove Card From Bag", "removeBag")
            .addChoice("Swap Cards In Bag", "swapBag").addChoice("View Bag", "viewBag")
            .addChoice("Add Card To Hand", "addHand").addChoice("Remove Card From Hand", "removeHand")
            .addChoice("Swap Cards In Hand", "swapHand").addChoice("View Hand", "viewHand")
            .addChoice("Add Card To Selection", "addQueue").addChoice("Remove Card From Selection", "removeQueue")
            .addChoice("Swap Cards In Selection", "swapQueue").addChoice("View Selection", "viewQueue")
            .addChoice("View All", "viewAll")
            .setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FRANKEN_ITEM + "1", "The card to edit"));
        addOptions(new OptionData(OptionType.STRING, Constants.FRANKEN_ITEM + "2", "Use with 'swap'. The card to swap Arg1 with."));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player @playername"));
        addOptions(new OptionData(OptionType.INTEGER, QUEUE_INDEX, "The index in the player's queue of the bag to edit. Default 0, the current bag.")
            .setMinValue(0));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        OptionMapping editOption = event.getOption(Constants.FRANKEN_EDIT_ACTION);
        OptionMapping card1 = event.getOption(Constants.FRANKEN_ITEM + "1");
        OptionMapping card2 = event.getOption(Constants.FRANKEN_ITEM + "2");
        // For some reason, getAsLong doesn't type check.
        int queueIndex = event.getOption(QUEUE_INDEX, 0, OptionMapping::getAsInt);
        String command = editOption.getAsString();

        if ("viewAll".equals(command)) {
            MessageHelper.sendMessageToUser("====================\n" + game.getName() +
                " Frankendraft Status\n====================", event.getUser());
            for (var player : game.getRealPlayers()) {
                int index = 0;
                for (DraftBag bag : player.getDraftBagQueue()) {
                    String bagName = index == 0 ? "Held Bag" : "Bag " + index + " in queue";
                    dmPlayerBag(game, player, bag, bagName, event.getUser());
                    index++;
                }
                dmPlayerBag(game, player, player.getDraftHand(), "Hand", event.getUser());
                dmPlayerBag(game, player, player.getDraftItemSelection(), "Selection", event.getUser());
            }
            return;
        }

        if ("forceSwap".equals(command)) {
            // TODO BAG_QUEUE this no longer makes sense
            // FrankenDraftBagService.passBags(game);
            return;
        }

        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        if (playerOption == null) {
            MessageHelper.replyToMessage(event, "That command requires a player argument.");
            return;
        }
        Player editingPlayer = game.getPlayer(playerOption.getAsUser().getId());

        DraftBag editingBag = null;
        String bagName = "";
        if (command.contains("Bag")) {
            // Hack for ArrayDeque not being a List
            editingBag = editingPlayer.getDraftBagQueue().stream()
                .skip(queueIndex)
                .findFirst()
                .orElse(null);
            // TODO BAG_QUEUE fuller editing support might be wanted
            // including creating bags, deleting bags,
            // moving bags to arbitrary positions.
            bagName = queueIndex == 0 ? "Held Bag" : "Queued bag " + queueIndex;
        }
        if (command.contains("Hand")) {
            editingBag = editingPlayer.getDraftHand();
            bagName = "Hand";
        }
        if (command.contains("Queue")) {
            editingBag = editingPlayer.getDraftItemSelection();
            bagName = "Queue";
        }

        if (editingBag != null) {
            if (command.contains("add")) {
                if (card1 != null) {
                    editingBag.Contents.add(DraftItem.generateFromAlias(card1.getAsString()));
                }
            } else if (command.contains("remove")) {
                if (card1 != null) {
                    editingBag.Contents.removeIf((DraftItem item) -> Objects.equals(item.getAlias(), card1.getAsString()));
                }
            } else if (command.contains("swap")) {
                if (card1 != null && card2 != null) {
                    editingBag.Contents.removeIf((DraftItem item) -> Objects.equals(item.getAlias(), card1.getAsString()));
                    editingBag.Contents.add(DraftItem.generateFromAlias(card2.getAsString()));
                }
            }

            dmPlayerBag(game, editingPlayer, editingBag, bagName, event.getUser());
        }
    }

    private void dmPlayerBag(Game game, Player player, @Nullable DraftBag bag, String bagName, User user) {
        StringBuilder sb = new StringBuilder();
        if (bag == null) {
            sb.append(game.getName()).append(" ").append(player.getUserName()).append(" No Current ").append(bagName).append(":\n");
        } else {
            sb.append(game.getName()).append(" ").append(player.getUserName()).append(" Current ").append(bagName).append(":\n");
            for (DraftItem item : bag.Contents) {
                sb.append(item.getAlias());
                sb.append("\n");
            }
        }
        MessageHelper.sendMessageToUser(sb.toString(), user);
    }
}
