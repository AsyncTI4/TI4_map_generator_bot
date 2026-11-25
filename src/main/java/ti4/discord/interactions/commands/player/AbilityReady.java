package ti4.discord.interactions.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.Mapper;

class AbilityReady extends GameStateSubcommand {

    AbilityReady() {
        super(Constants.ABILITY_READY, "Ready a player ability", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.ABILITY, "Ability to ready")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        String abilityId = event.getOption(Constants.ABILITY).getAsString();
        if (!player.hasAbility(abilityId)) {
            MessageHelper.sendMessageToEventChannel(event, "You do not have that ability.");
            return;
        }
        if (!player.removeExhaustedAbility(abilityId)) {
            MessageHelper.sendMessageToEventChannel(event, "That ability is already ready.");
            return;
        }
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentationNoPing() + " readied _"
                        + Mapper.getAbility(abilityId).getName() + "_.");
    }
}
