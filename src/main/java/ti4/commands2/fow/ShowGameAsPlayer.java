package ti4.commands2.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.ShowGameService;
import ti4.service.fow.UserOverridenSlashCommandInteractionEvent;

class ShowGameAsPlayer extends GameStateSubcommand {

    public ShowGameAsPlayer() {
        super(Constants.SHOW_GAME_AS_PLAYER, "Shows map as the specified player sees it.", false, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to which to show the map as").setAutoComplete(true).setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ShowGameService.simpleShowGame(getGame(), new UserOverridenSlashCommandInteractionEvent(event, getPlayer().getUser()));
    }
}