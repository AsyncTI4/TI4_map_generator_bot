package ti4.commands.ds;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.Nullable;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

public class TrapSwap extends DiscordantStarsSubcommandData {

    public TrapSwap() {
        super(Constants.LIZHO_SWAP_TRAP, "Select planets for which to swap traps");
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET2, "Planet2").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayer(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        OptionMapping planetOption = event.getOption(Constants.PLANET);
        OptionMapping planetOption2 = event.getOption(Constants.PLANET2);
        if (planetOption == null || planetOption2 == null) {
            return;
        }
        String planetName = planetOption.getAsString();
        if (!game.getPlanets().contains(planetName)) {
            MessageHelper.replyToMessage(event, "Planet not found in map");
            return;
        }

        String planetName2 = planetOption2.getAsString();
        if (!game.getPlanets().contains(planetName2)) {
            MessageHelper.replyToMessage(event, "Planet2 not found in map");
            return;
        }

        UnitHolder unitHolder = getUnitHolder(event, game, planetName);
        if (unitHolder == null) {
            MessageHelper.replyToMessage(event, "Planet not found in map");
            return;
        }

        UnitHolder unitHolder2 = getUnitHolder(event, game, planetName2);
        if (unitHolder2 == null) {
            MessageHelper.replyToMessage(event, "Planet2 not found in map");
            return;
        }

        if (unitHolder.getTokenList().contains(Constants.LIZHO_TRAP_PNG) &&
            unitHolder2.getTokenList().contains(Constants.LIZHO_TRAP_PNG)) {

            Map<String, String> trapCardsPlanets = player.getTrapCardsPlanets();
            String trap1 = null;
            String trap2 = null;
            for (Map.Entry<String, String> entry : trapCardsPlanets.entrySet()) {
                String planet = entry.getValue();
                String trap = entry.getKey();
                if (planetName.equals(planet)) {
                    trap1 = trap;
                }
                if (planetName2.equals(planet)) {
                    trap2 = trap;
                }
            }
            if (trap1 != null && trap2 != null) {
                player.setTrapCardPlanet(trap1, planetName2);
                player.setTrapCardPlanet(trap2, planetName);
            }
            return;
        }
        MessageHelper.replyToMessage(event, "Check planets, not all planets have traps");
    }

    @Nullable
    private static UnitHolder getUnitHolder(SlashCommandInteractionEvent event, Game game, String planetName) {
        Tile tile = null;
        UnitHolder unitHolder = null;
        for (Tile tile_ : game.getTileMap().values()) {
            if (tile != null) {
                break;
            }
            for (Map.Entry<String, UnitHolder> unitHolderEntry : tile_.getUnitHolders().entrySet()) {
                if (unitHolderEntry.getValue() instanceof Planet && unitHolderEntry.getKey().equals(planetName)) {
                    tile = tile_;
                    unitHolder = unitHolderEntry.getValue();
                    break;
                }
            }
        }
        if (tile == null) {
            MessageHelper.replyToMessage(event, "Planet not found");
            return null;
        }
        return unitHolder;
    }
}
