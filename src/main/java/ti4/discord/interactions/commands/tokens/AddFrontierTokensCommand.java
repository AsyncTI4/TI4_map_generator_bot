package ti4.discord.interactions.commands.tokens;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateCommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.explore.AddFrontierTokensService;

public class AddFrontierTokensCommand extends GameStateCommand {

    private static final String INCLUDE_FRACTURE = "include_fracture";

    public AddFrontierTokensCommand() {
        super(true, false);
    }

    @Override
    public String getName() {
        return Constants.ADD_FRONTIER_TOKENS;
    }

    @Override
    public String getDescription() {
        return "Add frontier tokens.";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES to confirm").setRequired(true),
                new OptionData(
                        OptionType.BOOLEAN,
                        INCLUDE_FRACTURE,
                        "True to also place tokens in empty Fracture systems (default false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())) {
            MessageHelper.replyToMessage(
                    event,
                    "Must confirm with `YES`"
                            + ("YES".equalsIgnoreCase(option.getAsString()) ? " - this is case sensitive" : "") + ".");
            return;
        }

        if (event.getOption(INCLUDE_FRACTURE, false, OptionMapping::getAsBoolean)) {
            AddFrontierTokensService.addFrontierTokensIncludingFracture(event, getGame());
        } else {
            AddFrontierTokensService.addFrontierTokens(event, getGame());
        }
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
