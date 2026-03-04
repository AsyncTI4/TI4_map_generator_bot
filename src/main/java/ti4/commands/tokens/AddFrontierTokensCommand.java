package ti4.commands.tokens;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateCommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.explore.AddFrontierTokensService;

public class AddFrontierTokensCommand extends GameStateCommand {

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
        return List.of(new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES to confirm").setRequired(true));
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

        AddFrontierTokensService.addFrontierTokens(event, getGame());
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
