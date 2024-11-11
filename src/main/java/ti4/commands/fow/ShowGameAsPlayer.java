package ti4.commands.fow;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.uncategorized.ShowGame;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ShowGameAsPlayer extends FOWSubcommandData {

    public ShowGameAsPlayer() {
        super(Constants.SHOW_GAME_AS_PLAYER, "Shows map as the specified player sees it.");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to which to show the map as").setAutoComplete(true).setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);

        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You're not a player of this game");
            return;
        }

        Player showMapAsPlayer = Helper.getPlayerFromEvent(game, null, event);
        if (showMapAsPlayer == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player could not be found");
            return;
        }

        ShowGame.simpleShowGame(game, new SlashCommandCustomUserWrapper(event, showMapAsPlayer.getUser()));
    }

    public static class SlashCommandCustomUserWrapper extends SlashCommandInteractionEvent {
        private final User overriddenUser;
        
        public SlashCommandCustomUserWrapper(SlashCommandInteractionEvent event, User overriddenUser) {
            super(event.getJDA(), event.getResponseNumber(), event.getInteraction());
            this.overriddenUser = overriddenUser;
        }

        @Override
        public User getUser() {
            return overriddenUser;
        }
    }
}