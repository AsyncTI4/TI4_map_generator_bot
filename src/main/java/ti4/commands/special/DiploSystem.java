package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.DiploSystemHelper;

class DiploSystem extends GameStateSubcommand {

    public DiploSystem() {
        super(Constants.DIPLO_SYSTEM, "Diplomacy a system", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player that is using Diplo"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color that is using Diplo").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        DiploSystemHelper.diploSystem(event, getGame(), getPlayer(), event.getOption(Constants.TILE_NAME).getAsString().toLowerCase());
    }
}
