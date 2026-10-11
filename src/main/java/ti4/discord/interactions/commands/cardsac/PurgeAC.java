package ti4.discord.interactions.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.oblivion.OblivionUnitHandler;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

class PurgeAC extends GameStateSubcommand {

    PurgeAC() {
        super(Constants.PURGE_AC, "Purge an Action Card", true, true);
        addOptions(new OptionData(
                        OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action card ID, which is found between ()")
                .setRequired(true));
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
        boolean removed = game.purgedActionCard(player.getUserID(), acIndex);
        if (!removed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such action card ID found, please retry.");
            return;
        }
        OblivionUnitHandler.doOblivionMechCheck(game, player);

        String sb = "Player: " + player.getUserName() + " - " + "Purged Action Card:"
                + "\n" + Mapper.getActionCard(acID).getRepresentation(game)
                + "\n";
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);
        ActionCardHelper.sendActionCardInfo(game, player);
    }
}
