package ti4.commands.relic;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;

public class RelicDrawSpecific extends RelicSubcommandData {

    public RelicDrawSpecific() {
        super(Constants.RELIC_DRAW_SPECIFIC, "Draw a specific relic");
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to exhaust").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you do edit"));
        addOption(OptionType.BOOLEAN, Constants.FORCE, "Force draw the relic (even if it's not in the deck)");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        String relicID = event.getOption(Constants.RELIC, null, OptionMapping::getAsString);
        if (relicID == null) {
            MessageHelper.sendMessageToEventChannel(event, "Specify relic");
            return;
        }
        boolean forced = event.getOption(Constants.FORCE, false, OptionMapping::getAsBoolean);
        List<String> allRelics = game.getAllRelics();
        if (!allRelics.contains(relicID) && !forced) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid relic or relic not present in deck: `" + relicID + "`");
            return;
        }
        allRelics.remove(relicID);
        player.addRelic(relicID);
        RelicModel relicModel = Mapper.getRelic(relicID);
        String message = player.getFactionEmoji() + " Drew Relic: " + relicModel.getName();
        if (forced) {
            message += " (FORCE DRAW: This relic was not in the deck but was forcefully drawn from the ether)";
        }
        MessageHelper.sendMessageToChannelWithEmbed(event.getMessageChannel(), message, relicModel.getRepresentationEmbed(false, true));
        RelicDraw.resolveRelicEffects(event, game, player, relicID);
    }
}
