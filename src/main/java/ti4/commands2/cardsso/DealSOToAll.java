package ti4.commands2.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.objectives.DrawSecretService;

class DealSOToAll extends GameStateSubcommand {

    public DealSOToAll() {
        super(Constants.DEAL_SO_TO_ALL, "Deal Secret Objective (count) to all game players", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Count of how many to draw, default 1"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        DrawSecretService.dealSOToAll(event, count, getGame());
    }
}
