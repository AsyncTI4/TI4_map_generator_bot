package ti4.discord.interactions.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.StringHelper;
import ti4.helpers.TransactionHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;
import ti4.service.leader.CommanderUnlockCheckService;

class SendTG extends GameStateSubcommand {

    public SendTG() {
        super(Constants.SEND_TG, "Sent trade goods to player/faction", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.TG, "Trade goods count").setRequired(true));
        addOptions(CommonOptions.targetFactionOrColor("Faction or Color receiving the trade goods"));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                Constants.CLEAR_DEBT,
                "True to automatically clear any debt with receiving player"));
        addOptions(CommonOptions.factionColor("Faction or Color sending the trade goods (defaults to you)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        int sendTG = event.getOption(Constants.TG, 0, OptionMapping::getAsInt);
        int tg = player.getTg();
        sendTG = Math.min(sendTG, tg);
        tg -= sendTG;
        player.setTg(tg);
        ButtonHelperAbilities.pillageCheck(player, game);

        Player targetPlayer = CommandHelper.getOtherPlayerOrReply(game, event);
        if (targetPlayer == null) return;
        int targetTG = targetPlayer.getTg();
        targetTG += sendTG;
        targetPlayer.setTg(targetTG);
        ButtonHelperAbilities.pillageCheck(targetPlayer, game);

        String p1 = player.getRepresentation();
        String p2 = targetPlayer.getRepresentation();
        CommanderUnlockCheckService.checkPlayer(player, "hacan");
        String tgString = sendTG + " " + MiscEmojis.getTGorNomadCoinEmoji(game) + " trade goods";
        String message = p1 + " sent " + tgString + " to " + p2;
        MessageHelper.sendMessageToEventChannel(event, message);

        if (event.getOption(Constants.CLEAR_DEBT, Boolean.FALSE, OptionMapping::getAsBoolean)) {
            targetPlayer.clearDebt(player, sendTG);
            MessageHelper.sendMessageToEventChannel(
                    event,
                    targetPlayer.getRepresentation() + " cleared " + StringHelper.pluralize(sendTG, "debt token")
                            + " owned by " + player.getRepresentation() + ", from their \"Debt Account\" pool.");
        }

        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(targetPlayer.getPrivateChannel(), message);

            // Add extra message for transaction visibility
            FoWHelper.pingPlayersTransaction(game, event, player, targetPlayer, tgString, null);
        }
        TransactionHelper.checkTransactionLegality(game, player, targetPlayer);
    }
}
