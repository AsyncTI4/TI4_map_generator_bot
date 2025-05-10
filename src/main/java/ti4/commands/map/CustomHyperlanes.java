package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.service.map.CustomHyperlaneService;

class CustomHyperlanes extends GameStateSubcommand {

    public CustomHyperlanes() {
        super("custom_hyperlanes", "Insert custom hyperlane data to HL tiles", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        CustomHyperlaneService.offerManageHyperlaneButtons(getGame(), event);
    }
}
