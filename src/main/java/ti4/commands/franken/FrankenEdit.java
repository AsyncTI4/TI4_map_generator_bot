package ti4.commands.franken;

import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.draft.DraftBag;
import ti4.draft.DraftItem;
import ti4.helpers.Constants;
import ti4.helpers.FrankenDraftHelper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class FrankenEdit extends FrankenSubcommandData {
    public FrankenEdit() {
        super(Constants.FRANKEN_EDIT, "Frankendraft Edit Commands");
        addOptions(new OptionData(OptionType.STRING, Constants.FRANKEN_EDIT_ACTION, "add/remove/swap/view")
            .addChoice("Force Bag Swap", "forceSwap")
            .addChoice("Add Card To Bag", "addBag").addChoice("Remove Card From Bag", "removeBag")
            .addChoice("Swap Cards In Bag", "swapBag").addChoice("View Bag", "viewBag")
            .addChoice("Add Card To Hand", "addHand").addChoice("Remove Card From Hand", "removeHand")
            .addChoice("Swap Cards In Hand", "swapHand").addChoice("View Hand", "viewHand")
            .addChoice("Add Card To Queue", "addQueue").addChoice("Remove Card From Queue", "removeQueue")
            .addChoice("Swap Cards In Queue", "swapQueue").addChoice("View Queue", "viewQueue")
            .addChoice("View All", "viewAll")
            .setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FRANKEN_ITEM + "1", "The card to edit"));
        addOptions(new OptionData(OptionType.STRING, Constants.FRANKEN_ITEM + "2", "Use with 'swap'. The card to swap Arg1 with."));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player @playername"));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        OptionMapping editOption = event.getOption(Constants.FRANKEN_EDIT_ACTION);
        OptionMapping card1 = event.getOption(Constants.FRANKEN_ITEM + "1");
        OptionMapping card2 = event.getOption(Constants.FRANKEN_ITEM + "2");
        String command = editOption.getAsString();

        if ("viewAll".equals(command)) {
            MessageHelper.sendMessageToUser("====================\n" + game.getName() +
                " Frankendraft Status\n====================", getUser());
            for (var player : game.getRealPlayers()) {
                dmPlayerBag(game, player, player.getCurrentDraftBag(), "Held Bag");
                dmPlayerBag(game, player, player.getDraftHand(), "Hand");
                dmPlayerBag(game, player, player.getDraftQueue(), "Queue");
            }
            return;
        }

        if ("forceSwap".equals(command)) {
            FrankenDraftHelper.passBags(game);
            GameSaveLoadManager.saveGame(game, event);
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
            editingBag = editingPlayer.getCurrentDraftBag();
            bagName = "Held Bag";
        }
        if (command.contains("Hand")) {
            editingBag = editingPlayer.getDraftHand();
            bagName = "Hand";
        }
        if (command.contains("Queue")) {
            editingBag = editingPlayer.getDraftQueue();
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

            dmPlayerBag(game, editingPlayer, editingBag, bagName);
        }

        GameSaveLoadManager.saveGame(game, event);
    }

    private void dmPlayerBag(Game game, Player player, DraftBag bag, String bagName) {
        StringBuilder sb = new StringBuilder();
        sb.append(game.getName()).append(" ").append(player.getUserName()).append(" Current ").append(bagName).append(":\n");
        for (DraftItem item : bag.Contents) {
            sb.append(item.getAlias());
            sb.append("\n");
        }
        MessageHelper.sendMessageToUser(sb.toString(), getUser());
    }
}
