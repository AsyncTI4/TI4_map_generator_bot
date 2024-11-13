package ti4.commands.ds;

import java.util.Collection;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class TrapToken extends GameStateSubcommand {

    public TrapToken() {
        super(Constants.LIZHO_TRAP, "Select planets were to add/remove trap tokens", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.LIZHO_TRAP_ID, "Trap ID").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        String planetName = event.getOption(Constants.PLANET).getAsString();
        if (!game.getPlanets().contains(planetName)) {
            MessageHelper.replyToMessage(event, "Planet not found in map");
            return;
        }

        Collection<Integer> values = player.getTrapCards().values();
        int trapID = event.getOption(Constants.LIZHO_TRAP_ID).getAsInt();
        if (!values.contains(trapID)) {
            MessageHelper.replyToMessage(event, "Trap ID not found");
            return;
        }
        String stringTrapID = "";
        for (String trapIDS : player.getTrapCards().keySet()) {
            if (player.getTrapCards().get(trapIDS) == trapID) {
                stringTrapID = trapIDS;
            }
        }

        setTrapForPlanet(event, game, planetName, stringTrapID, player);
    }

    public void setTrapForPlanet(GenericInteractionCreateEvent event, Game game, String planetName, String trap, Player player) {
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planetName, game);
        Map<String, String> trapCardsPlanets = player.getTrapCardsPlanets();
        String planetUnitHolderName = trapCardsPlanets.get(trap);
        if (planetUnitHolderName != null) {
            MessageHelper.replyToMessage(event, "Trap used on other planet, please use trap swap or remove first");
            return;
        }
        ButtonHelperAbilities.addATrapToken(game, planetName);
        player.setTrapCardPlanet(trap, unitHolder.getName());
        player.setTrapCard(trap);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationUnfogged() + " put a trap on the planet " + Helper.getPlanetRepresentation(planetName, game));
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentationUnfogged() + " set the trap " + ButtonHelperAbilities.translateNameIntoTrapIDOrReverse(trap) + " on the planet " + Helper.getPlanetRepresentation(planetName, game));
        CommanderUnlockCheck.checkPlayer(player, "lizho");
    }
}
