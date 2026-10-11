package ti4.discord.interactions.commands.tf;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.Constants;

class DrawRandomSpliceCard extends GameStateSubcommand {

    private final String spliceType;

    private DrawRandomSpliceCard(String name, String cardDescription, String spliceType) {
        super(name, "Draw a random Twilight's Fall " + cardDescription + " splice card", true, true);
        markSuspicious();
        this.spliceType = spliceType;
        addOptions(CommonOptions.factionColorForStats());
    }

    static DrawRandomSpliceCard ability() {
        return new DrawRandomSpliceCard(Constants.DRAW_RANDOM_ABILITY, "ability", "spoof_ability");
    }

    static DrawRandomSpliceCard genome() {
        return new DrawRandomSpliceCard(Constants.DRAW_RANDOM_GENOME, "genome", "spoof_genome");
    }

    static DrawRandomSpliceCard unit() {
        return new DrawRandomSpliceCard(Constants.DRAW_RANDOM_UNIT, "unit upgrade", "spoof_units");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ButtonHelperTwilightsFall.drawSingularNewSpliceCard(getGame(), spliceType, getPlayer(), event);
    }
}
