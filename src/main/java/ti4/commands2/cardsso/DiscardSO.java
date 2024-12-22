package ti4.commands2.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.objectives.DiscardSecretService;

class DiscardSO extends GameStateSubcommand {

    public DiscardSO() {
        super(Constants.DISCARD_SO, "Discard Secret Objective", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID, which is found between ()").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int secretId = event.getOption(Constants.SECRET_OBJECTIVE_ID).getAsInt();
        DiscardSecretService.discardSO(getPlayer(), secretId, getGame());
    }
}
