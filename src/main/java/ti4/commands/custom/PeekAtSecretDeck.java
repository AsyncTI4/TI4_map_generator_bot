package ti4.commands.custom;

import java.util.Collections;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;

class PeekAtSecretDeck extends GameStateSubcommand {

    public PeekAtSecretDeck() {
        super("peek_secret_objective_deck", "Peek at the top of the secret objective deck", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Number of secrets to peek at, default 1").setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, "index_to_draw", "Which of the secrets to draw (1, 2, 3, ...)"));
        addOptions(new OptionData(OptionType.BOOLEAN, "shuffle", "True to shuffle"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // this is a bit funky, but I am doing something hacky to make my life easier for ACD2. Fix it later!
        Game game = getGame();
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        List<String> peekedObjectives = game.peekAtSecrets(count);

        Integer peekedSoIndexToDraw = event.getOption("index_to_draw", null, OptionMapping::getAsInt);
        if (peekedSoIndexToDraw == null) {
            var embeds = peekedObjectives.stream()
                .map(secretId -> Mapper.getSecretObjective(secretId).getRepresentationEmbed(true))
                .toList();
            MessageHelper.sendMessageEmbedsToCardsInfoThread(getPlayer(), "", embeds);
            MessageHelper.sendMessageToChannel(event.getChannel(), "To draw the peeked card, use the draw option and the index of the " +
                "card (1, 2, 3; from top to bottom).");
        } else {
            peekedSoIndexToDraw -= 1;
            if (peekedSoIndexToDraw < 0) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Draw index must be greater than 0.");
                return;
            } else if (peekedSoIndexToDraw >= peekedObjectives.size()) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Draw index must be less than or equal to the number of peeked secrets.");
                return;
            }
            Player player = getPlayer();
            var secretId = peekedObjectives.get(peekedSoIndexToDraw);
            game.drawSpecificSecretObjective(secretId, player.getUserID());
            SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
        }

        boolean shuffle = event.getOption("shuffle", false, OptionMapping::getAsBoolean);
        if (shuffle) {
            Collections.shuffle(game.getSecretObjectives());
        }
    }
}
