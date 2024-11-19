package ti4.commands.fow;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.ShowGameService;

class ShowGameAsPlayer extends GameStateSubcommand {

    public ShowGameAsPlayer() {
        super(Constants.SHOW_GAME_AS_PLAYER, "Shows map as the specified player sees it.", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to which to show the map as").setAutoComplete(true).setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ShowGameService.simpleShowGame(getGame(), new SlashCommandCustomUserWrapper(event, getPlayer().getUser()));
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