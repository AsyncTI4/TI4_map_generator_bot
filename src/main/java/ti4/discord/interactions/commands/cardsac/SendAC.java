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
import ti4.helpers.FoWHelper;
import ti4.service.emoji.CardEmojis;

class SendAC extends GameStateSubcommand {

    public SendAC() {
        super(Constants.SEND_AC, "Send an action card to a player", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action card ID to send")
                .setRequired(true));
        addOptions(CommonOptions.targetFactionOrColor("Faction or Color to send to"));
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
        Player playerToSendTo = CommandHelper.getOtherPlayerOrReply(game, event);
        if (playerToSendTo == null) return;

        // FoW specific pinging
        if (game.isFowMode()) {
            FoWHelper.pingPlayersTransaction(
                    game, event, player, playerToSendTo, CardEmojis.getACEmoji(game) + " Action Card", null);
        }

        ActionCardHelper.sendActionCard(event, game, player, playerToSendTo, acID);
    }
}
