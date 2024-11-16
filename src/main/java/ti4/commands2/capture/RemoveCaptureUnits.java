package ti4.commands2.capture;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.units.RemoveUnits;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Tile;

class RemoveCaptureUnits extends CaptureUnitsCommand {

    public RemoveCaptureUnits() {
        super(Constants.REMOVE_UNITS, "Release units");
    }

    @Override
    protected void subExecute(SlashCommandInteractionEvent event, Tile tile) {
        RemoveUnits addUnits = new RemoveUnits() {
            @Override
            public Tile getTileObject(SlashCommandInteractionEvent event, String tileID, Game game) {
                return tile;
            }

            @Override
            private String recheckColorForUnit(String unit, String color, GenericInteractionCreateEvent event) {
                if (unit.contains("ff") || unit.contains("gf")) {
                    return getPlayerColor(event);
                }
                return color;
            }
        };
        addUnits.execute(event);
    }
}
