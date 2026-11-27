package ti4.discord.interactions.commands.homebrew.lost_legacies;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.HydromorphicHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;

class HydromorphicDiscard extends GameStateSubcommand {
    HydromorphicDiscard() {
        super("hc_discard", "Discard a Hydromorphic card without resolving it", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.HYDROMORPHIC_CARD_ID, "Hydromorphic card ID")
                .setRequired(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        int identifier = event.getOption(Constants.HYDROMORPHIC_CARD_ID).getAsInt();
        String cardId = player.removeHydromorphicCard(identifier);
        if (cardId == null) {
            MessageHelper.sendMessageToEventChannel(event, "No Hydromorphic card with that ID was found.");
            return;
        }
        game.returnHydromorphicCard(cardId);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " discarded the Hydromorphic card _"
                        + Mapper.getHydromorphicCard(cardId).getName() + "_.");
        HydromorphicHelper.sendHydromorphicInfo(game, player);
    }
}
