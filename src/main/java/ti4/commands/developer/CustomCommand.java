package ti4.commands.developer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.message.MessageHelper;

class CustomCommand extends GameStateSubcommand {

    CustomCommand() {
        super("custom_command", "Custom command written for a custom purpose.", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "The game to run the command against.")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.FACTION_COLOR,
                        "Faction/color of the player to spawn Alliance Rider buttons for")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String gameName = event.getOption(Constants.GAME_NAME, null, OptionMapping::getAsString);
        if (!GameManager.isValid(gameName)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Game not found: " + gameName);
            return;
        }

        Game game = GameManager.getManagedGame(gameName).getGame();
        Player player = getPlayer();

        List<MessageEmbed> embeds = new ArrayList<>();

        for (String objectiveId : game.getPublicObjectives1Peekable()) {
            embeds.add(Mapper.getPublicObjective(objectiveId).getRepresentationEmbed());
        }

        for (String objectiveId : game.getPublicObjectives2Peekable()) {
            embeds.add(Mapper.getPublicObjective(objectiveId).getRepresentationEmbed());
        }

        for (String secretId : game.peekAtSecrets(5)) {
            embeds.add(Mapper.getSecretObjective(secretId).getRepresentationEmbed(true));
        }

        MessageHelper.sendMessageEmbedsToCardsInfoThread(
                player,
                "Showing all unrevealed public objectives and the top 5 secret objectives from the deck.",
                embeds);
        Collections.shuffle(game.getSecretObjectives());
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Sent _Oracle_ results to " + player.getFactionEmojiOrColor()
                        + " `#cards-info` thread and shuffled the secret objective deck.");
    }
}
