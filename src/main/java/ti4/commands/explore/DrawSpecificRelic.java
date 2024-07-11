package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;

import java.util.List;

public class DrawSpecificRelic extends GenericRelicAction {

    public DrawSpecificRelic() {
        super(Constants.RELIC_DRAW_SPECIFIC, "Draw a specific relic", true);
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to exhaust").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you do edit"));
        addOption(OptionType.BOOLEAN, Constants.FORCE, "Force draw the relic (even if it's not in the deck)");
    }

    @Override
    public void doAction(Player player, SlashCommandInteractionEvent event) {
        String relicID = event.getOption(Constants.RELIC, null, OptionMapping::getAsString);
        if (relicID == null) {
            MessageHelper.sendMessageToEventChannel(event, "Specify relic");
            return;
        }
        boolean forced = event.getOption(Constants.FORCE, false, OptionMapping::getAsBoolean);
        Game game = getActiveGame();
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
        DrawRelic.resolveRelicEffects(event, game, player, relicID);
    }
}
