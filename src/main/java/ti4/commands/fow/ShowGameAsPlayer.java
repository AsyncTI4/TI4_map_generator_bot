package ti4.commands.fow;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import ti4.commands.PlayerGameStateSubcommand;
import ti4.commands.uncategorized.ShowGame;
import ti4.helpers.Constants;

public class ShowGameAsPlayer extends PlayerGameStateSubcommand {

    public ShowGameAsPlayer() {
        super(Constants.SHOW_GAME_AS_PLAYER, "Shows map as the specified player sees it.", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to which to show the map as").setAutoComplete(true).setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ShowGame.simpleShowGame(getGame(), new SlashCommandCustomUserWrapper(event, getPlayer().getUser()));
    }

    private static class SlashCommandCustomUserWrapper extends SlashCommandInteractionEvent {
        private final User overriddenUser;
        
        public SlashCommandCustomUserWrapper(SlashCommandInteractionEvent event, User overriddenUser) {
            super(event.getJDA(), event.getResponseNumber(), event.getInteraction());
            this.overriddenUser = overriddenUser;
        }

        @NotNull
        @Override
        public User getUser() {
            return overriddenUser;
        }
    }
}