package ti4.commands.capture;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Tile;

class AddCaptureUnits extends CaptureUnitsCommand {

    public AddCaptureUnits() {
        super(Constants.ADD_UNITS, "Capture units");
    }

    // TODO: this feels super hacky and would may be be better re-written...
    @Override
    protected void subExecute(SlashCommandInteractionEvent event, Tile tile) {
        ti4.commands.units.AddUnits addUnits = new ti4.commands.units.AddUnits() {
            @Override
            public Tile getTileObject(SlashCommandInteractionEvent event, String tileID, Game game) {
                return tile;
            }

            @Override
            protected String recheckColorForUnit(String unit, String color, GenericInteractionCreateEvent event) {
                if (unit.contains("ff") || unit.contains("gf")) {
                    return getPlayerColor(event);
                }
                return color;
            }
        };
        addUnits.execute(event);
    }
}
