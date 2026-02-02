package ti4.commands.relic;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;

class RelicBottom extends GameStateSubcommand {

    RelicBottom() {
        super("put_on_bottom", "Put a specific relic on the bottom", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to bottom")
                .setAutoComplete(true)
                .setRequired(true));
        ;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String relicID = event.getOption(Constants.RELIC).getAsString();
        List<String> allRelics = game.getAllRelics();
        if (!allRelics.contains(relicID)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Invalid relic or relic not present in deck: `" + relicID + "`");
            return;
        }
        allRelics.remove(relicID);
        game.getAllRelics().addLast(relicID);
        RelicModel relicModel = Mapper.getRelic(relicID);
        String message = "Put _" + relicModel.getName() + "_ on the bottom of the relic deck.";

        MessageHelper.sendMessageToChannelWithEmbed(
                event.getMessageChannel(), message, relicModel.getRepresentationEmbed(false, true));
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
