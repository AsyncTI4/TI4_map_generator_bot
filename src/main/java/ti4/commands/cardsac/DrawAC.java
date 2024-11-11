package ti4.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DrawAC extends ACCardsSubcommandData {
    public DrawAC() {
        super(Constants.DRAW_AC, "Draw Action Card");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1, max 10"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getPlayerFromEvent(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.COUNT);
        int count = 1;
        if (option != null) {
            int providedCount = option.getAsInt();
            count = providedCount > 0 ? providedCount : 1;
            if (count > 10) {
                count = 10;
            }
        }

        drawActionCards(game, player, count, false);
    }

    public static void drawActionCards(Game game, Player player, int count, boolean resolveAbilities) {
        if (count > 10) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "You probably shouldn't need to ever draw more than 10 cards, double check what you're doing please.");
            return;
        }
        String message = player.getRepresentation() + " Drew " + count + " AC";
        if (resolveAbilities && player.hasAbility("scheming")) {
            message = player.getRepresentation() + " Drew [" + count + "+1=" + (count + 1) + "] AC (Scheming)";
            count++;
        }
        if (resolveAbilities && player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, count);
            return;
        }
        game.drawActionCard(player.getUserID(), count);

        ACInfo.sendActionCardInfo(game, player);
        ButtonHelper.checkACLimit(game, null, player);
        if (resolveAbilities && player.hasAbility("scheming")) ACInfo.sendDiscardActionCardButtons(player, false);
        CommanderUnlockCheck.checkPlayer(player, "yssaril");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
    }
}
