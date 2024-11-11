package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SetFactionDisplayName extends FrankenSubcommandData {

    public SetFactionDisplayName() {
        super(Constants.SET_FACTION_DISPLAY_NAME, "Set your faction Display Name (instead of your username)");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_DISPLAY_NAME, "Name to use. Enter 'none' to delete currently set name.").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayerFromEvent(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        String displayName = event.getOption(Constants.FACTION_DISPLAY_NAME, null, OptionMapping::getAsString);
        if ("none".equals(displayName)) {
            player.setDisplayName(null);
            MessageHelper.sendMessageToEventChannel(event, "Faction Display Name removed");
            return;
        }
        MessageHelper.sendMessageToEventChannel(event, player.getFactionEmojiOrColor() + " Display Name set to: `" + displayName + "`");
        player.setDisplayName(displayName);
    }

}
