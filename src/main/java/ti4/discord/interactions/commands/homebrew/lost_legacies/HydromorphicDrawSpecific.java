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

class HydromorphicDrawSpecific extends GameStateSubcommand {
    HydromorphicDrawSpecific() {
        super("hc_draw_specific", "Draw a specific Hydromorphic card", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.HYDROMORPHIC_CARD, "Hydromorphic card alias")
                .setRequired(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        String cardId =
                event.getOption(Constants.HYDROMORPHIC_CARD).getAsString().toLowerCase();
        if (Mapper.getHydromorphicCard(cardId) == null) {
            MessageHelper.sendMessageToEventChannel(event, "No Hydromorphic card with that alias exists.");
            return;
        }
        game.initializeHydromorphicDeck();
        if (!game.getHydromorphicCards().remove(cardId)) {
            MessageHelper.sendMessageToEventChannel(event, "That Hydromorphic card is not in the shared deck.");
            return;
        }
        player.setHydromorphicCard(cardId);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " drew the "
                        + Mapper.getHydromorphicCard(cardId).getNameRepresentation() + " Hydromorphic card.");
        HydromorphicHelper.sendHydromorphicInfo(game, player);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
