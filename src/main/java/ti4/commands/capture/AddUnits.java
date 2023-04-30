package ti4.commands.capture;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Tile;

public class AddUnits extends CaptureReleaseUnits {

    public AddUnits() {
        super(Constants.ADD_UNITS, "Capture units");
    }

    @Override
    protected void subExecute(SlashCommandInteractionEvent event, Tile tile) {
        ti4.commands.units.AddUnits addUnits = new ti4.commands.units.AddUnits() {
            @Override
            public Tile getTileObject(SlashCommandInteractionEvent event, String tileID, Map activeMap) {
                return tile;
            }

            @Override
            protected String recheckColorForUnit(String unit, String color, SlashCommandInteractionEvent event) {
                if (unit.contains("ff") || unit.contains("gf")) {
                    return AddUnits.this.getPlayerColor(event);
                }
                return color;
            }
        };
        addUnits.execute(event);
    }
}
