package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.Constants;

public abstract class PlayerSubcommandData extends SubcommandData {

    public String getActionID()
    {
        return getName();
    }

    public PlayerSubcommandData(@NotNull String name, @NotNull String description) {
        super(name, description);
        addOptions(new OptionData(OptionType.STRING, Constants.CC, "CC's Example: 3/3/2"))
        .addOptions(new OptionData(OptionType.STRING, Constants.TG, "Trade goods count"))
        .addOptions(new OptionData(OptionType.STRING, Constants.COMMODITIES, "Commodity count"));
    }


    abstract public void execute(SlashCommandInteractionEvent event);
}
