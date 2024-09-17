package ti4.commands.relic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RefreshRelic extends RelicSubcommandData {

    public RefreshRelic() {
        super(Constants.RELIC_REFRESH, "Ready a Relic");
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to exhaust").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you do edit\"").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayer(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        OptionMapping option = event.getOption(Constants.RELIC);
        if (option == null) {
            MessageHelper.sendMessageToEventChannel(event, "Specify relic");
            return;
        }

        String relicId = option.getAsString();
        if (player.hasRelic(relicId)) {
            player.removeExhaustedRelic(relicId);
            String relicName = Mapper.getRelic(relicId).getName();
            MessageHelper.sendMessageToEventChannel(event, "Refreshed " + Emojis.Relic + "Relic: " + relicName);
		} else {
            MessageHelper.sendMessageToEventChannel(event, "Invalid Relic or player does not have specified Relic");
        }
    }
}
