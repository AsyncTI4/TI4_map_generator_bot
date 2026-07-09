package ti4.discord.interactions.commands.combat;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.message.MessageHelper;
import ti4.service.combat.v2.CombatV2Config;

class CombatV2 extends GameStateSubcommand {
    private static final String ENABLED = "enabled";

    CombatV2() {
        super("v2", "Configure staged combat handling for this game.", true, true);
        addOption(OptionType.BOOLEAN, ENABLED, "Enable or disable the staged combat flow for this game.", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean enabled = event.getOption(ENABLED).getAsBoolean();
        CombatV2Config.setEnabled(getGame(), enabled);
        MessageHelper.sendMessageToEventChannel(
                event,
                "Combat V2 is now **" + (enabled ? "enabled" : "disabled")
                        + "** for this game. Combat threads, messages, and buttons are unchanged.");
    }
}
