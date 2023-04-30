package ti4.commands.capture;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Tile;

public class RemoveUnits extends CaptureReleaseUnits {

    public RemoveUnits() {
        super(Constants.REMOVE_UNITS, "Release units");
    }

    @Override
    protected void subExecute(SlashCommandInteractionEvent event, Tile tile) {
        ti4.commands.units.RemoveUnits addUnits = new ti4.commands.units.RemoveUnits() {
            @Override
            public Tile getTileObject(SlashCommandInteractionEvent event, String tileID, Map activeMap) {
                return tile;
            }

            @Override
            protected String recheckColorForUnit(String unit, String color, SlashCommandInteractionEvent event) {
                if (unit.contains("ff") || unit.contains("gf")) {
                    return RemoveUnits.this.getPlayerColor(event);
                }
                return color;
            }
        };
        addUnits.execute(event);
    }
}
