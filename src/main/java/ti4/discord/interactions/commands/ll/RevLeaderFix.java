package ti4.discord.interactions.commands.ll;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Revenant.RevenantAbilityHandler;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.message.MessageHelper;

class RevLeaderFix extends GameStateSubcommand {
    RevLeaderFix() {
        super("rev_leader_fix", "Reset your Revenant pantheon leader set", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!RevenantAbilityHandler.resetCallOfTheHauntedLeaders(getGame(), getPlayer())) {
            MessageHelper.sendMessageToEventChannel(event, "You do not have Call of the Haunted.");
        }
    }
}
