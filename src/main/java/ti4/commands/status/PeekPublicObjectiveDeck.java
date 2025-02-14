package ti4.commands.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;

class PeekPublicObjectiveDeck extends GameStateSubcommand {

    private static final String STAGE_COMMAND_NAME = "stage";

    public PeekPublicObjectiveDeck() {
        super("po_peek", "Peek Public Objective Deck", false, true);
        addOptions(new OptionData(OptionType.INTEGER, STAGE_COMMAND_NAME, "1 or 2").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.COUNT, "Number of cards to peek."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        int stage = event.getOption(STAGE_COMMAND_NAME).getAsInt();
        int count = Optional.ofNullable(event.getOption(Constants.COUNT))
                .map(OptionMapping::getAsInt)
                .orElse(1);
        List<String> publicObjectiveDeck = stage == 1 ? game.getPublicObjectives1() : game.getPublicObjectives2();

        List<MessageEmbed> publicObjectiveEmbedMessages = new ArrayList<>(count);
        for (int i = 0; i < count && i < publicObjectiveDeck.size(); i++) {
            String publicObjectiveId = publicObjectiveDeck.get(i);
            PublicObjectiveModel publicObjective = Mapper.getPublicObjective(publicObjectiveId);
            publicObjectiveEmbedMessages.add(publicObjective.getRepresentationEmbed(true));
        }

        Player player = getPlayer();
        MessageHelper.sendMessageToChannelWithEmbeds(player.getCardsInfoThread(), null, publicObjectiveEmbedMessages);
    }
}
