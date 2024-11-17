package ti4.commands2.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.DiscordantStarsHelper;

class DrawBlueBackTile extends GameStateSubcommand {

    public DrawBlueBackTile() {
        super(Constants.DRAW_BLUE_BACK_TILE, "Draw a random blue back tile (for Star Charts and Decrypted Cartoglyph)", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "How many to draw? Default: 1"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
        DiscordantStarsHelper.drawBlueBackTiles(event, getGame(), getPlayer(), count);
    }
}
