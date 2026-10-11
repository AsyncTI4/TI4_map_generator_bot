package ti4.discord.interactions.commands.tokens;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateCommand;
import ti4.game.Tile;
import ti4.helpers.Constants;

public class RemoveAllCC extends GameStateCommand {

    public RemoveAllCC() {
        super(Constants.REMOVE_ALL_CC, "Remove all command tokens", true, false);
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES to confirm").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!CommandHelper.confirmedWithYes(event, event.getOption(Constants.CONFIRM))) return;

        for (Tile tile : getGame().getTileMap().values()) {
            tile.removeAllCC();
        }
    }
}
