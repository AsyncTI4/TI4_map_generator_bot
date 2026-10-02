package ti4.discord.interactions.commands.frankendraz;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.message.MessageHelper;

class FrankenDrazHelp extends Subcommand {

    FrankenDrazHelp() {
        super("help", "Show FrankenDraz help");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToEventChannel(event, """
                ## FrankenDraz
                Start FrankenDraz with `/franken start_franken_draft draft_mode: frankendraz`.

                In FrankenDraz, players draft entire factions instead of individual faction components. Each player drafts 6 factions, 3 blue tiles, and 2 red tiles. Discordant Stars factions are included by default. The Obsidian and The Firmament are automatically banned.

                After the draft is complete, each player gets category buttons in their cards info thread. Open a category to view the drafted components and choose which ones to add to the faction. Home systems and starting fleets are informational and must still be set up manually (for now).

                The limits for components follow powered franken limits by default. (4 abilities, 3 faction techs, 1 of everything else)

                Use the following post-draft commands in case of any issues:

                `/frankendraz faction_limit`
                `/frankendraz set_kept_component_limit`
                `/frankendraz add_faction`
                `/frankendraz remove_faction`
                `/frankendraz swap_faction`
                """);
    }
}
