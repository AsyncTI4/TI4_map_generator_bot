package ti4.commands.relic;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.RelicHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;

class RelicDrawSpecific extends GameStateSubcommand {

    public RelicDrawSpecific() {
        super(Constants.RELIC_DRAW_SPECIFIC, "Draw a specific relic", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to exhaust").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you do edit"));
        addOption(OptionType.BOOLEAN, Constants.FORCE, "Force draw the relic (even if it's not in the deck)");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String relicID = event.getOption(Constants.RELIC).getAsString();
        boolean forced = event.getOption(Constants.FORCE, false, OptionMapping::getAsBoolean);
        List<String> allRelics = game.getAllRelics();
        if (!allRelics.contains(relicID) && !forced) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Invalid relic or relic not present in deck: `" + relicID + "`");
            return;
        }
        allRelics.remove(relicID);
        Player player = getPlayer();
        player.addRelic(relicID);
        RelicModel relicModel = Mapper.getRelic(relicID);
        String message = player.getFactionEmoji() + " Drew Relic: " + relicModel.getName();
        if (forced) {
            message += " (FORCE DRAW: This relic was not in the deck but was forcefully drawn from the ether)";
        }
        MessageHelper.sendMessageToChannelWithEmbed(event.getMessageChannel(), message, relicModel.getRepresentationEmbed(false, true));
        RelicHelper.resolveRelicEffects(event, game, player, relicID);
    }
}
