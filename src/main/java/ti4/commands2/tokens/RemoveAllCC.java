package ti4.commands2.tokens;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateCommand;
import ti4.helpers.Constants;
import ti4.map.Tile;

public class RemoveAllCC extends GameStateCommand {

    public RemoveAllCC() {
        super(true, false);
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES to confirm")
                        .setRequired(true));
    }

    @Override
    public String getName() {
        return Constants.REMOVE_ALL_CC;
    }

    @Override
    public String getDescription() {
        return "Remove all command tokens";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        for (Tile tile : getGame().getTileMap().values()) {
            tile.removeAllCC();
        }
    }
}
