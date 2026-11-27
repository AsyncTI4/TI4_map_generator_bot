package ti4.discord.interactions.commands.homebrew.lost_legacies;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.HydromorphicHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

class HydromorphicSend extends GameStateSubcommand {
    HydromorphicSend() {
        super("hc_send", "Send a Hydromorphic card to a player", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.HYDROMORPHIC_CARD_ID, "Hydromorphic card ID")
                .setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or color to send to")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player sender = getPlayer();
        Player receiver = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (receiver == null || receiver == sender) {
            MessageHelper.sendMessageToEventChannel(event, "Choose another player to receive the Hydromorphic card.");
            return;
        }
        int identifier = event.getOption(Constants.HYDROMORPHIC_CARD_ID).getAsInt();
        String cardId = sender.removeHydromorphicCard(identifier);
        if (cardId == null) {
            MessageHelper.sendMessageToEventChannel(event, "No Hydromorphic card with that ID was found.");
            return;
        }
        receiver.setHydromorphicCard(cardId);
        if (game.isFowMode()) {
            FoWHelper.pingPlayersTransaction(
                    game, event, sender, receiver, CardEmojis.HydromorphicCard + " Hydromorphic card", null);
        }
        MessageHelper.sendMessageToChannel(
                sender.getCorrectChannel(),
                sender.getRepresentationNoPing() + " sent a Hydromorphic card to " + receiver.getRepresentationNoPing()
                        + ".");
        HydromorphicHelper.sendHydromorphicInfo(game, sender);
        HydromorphicHelper.sendHydromorphicInfo(game, receiver);
    }
}
