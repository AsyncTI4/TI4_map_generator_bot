package ti4.discord.interactions.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

class ShowAC extends GameStateSubcommand {

    public ShowAC() {
        super(Constants.SHOW_AC, "Show an action card to one player", true, true);
        addOptions(new OptionData(
                        OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action Card ID, which is found between ()")
                .setRequired(true));
        addOptions(CommonOptions.targetFactionOrColor());
        addOptions(CommonOptions.factionColor());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        int acIndex = event.getOption(Constants.ACTION_CARD_ID).getAsInt();

        String acID = CommandHelper.requireOrReport(
                event,
                CommandHelper.cardIdForNumber(player.getActionCards(), acIndex),
                "No such action card ID found, please retry.");
        if (acID == null) return;

        Game game = getGame();
        String sb = "---------\n" + "Game: "
                + game.getName() + "\n" + "Player: "
                + player.getUserName() + "\n" + "Shown Action Cards:"
                + "\n" + Mapper.getActionCard(acID).getRepresentation(game)
                + "\n" + "---------\n";
        player.setActionCard(acID);

        Player playerToShowTo = CommandHelper.getOtherPlayerOrReply(game, event);
        if (playerToShowTo == null) return;

        ActionCardHelper.sendActionCardInfo(game, player);
        MessageHelper.sendMessageToPlayerCardsInfoThread(playerToShowTo, sb);
    }
}
