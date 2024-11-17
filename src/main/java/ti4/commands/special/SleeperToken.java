package ti4.commands.special;

import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class SleeperToken extends GameStateSubcommand {

    public SleeperToken() {
        super(Constants.SLEEPER_TOKEN, "Select planets were to add/remove Sleeper tokens", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET2, "2nd Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET3, "3rd Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET4, "4th Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET5, "5th Planet").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET6, "6th Planet").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        sleeperForPlanet(event, game, Constants.PLANET, player);
        sleeperForPlanet(event, game, Constants.PLANET2, player);
        sleeperForPlanet(event, game, Constants.PLANET3, player);
        sleeperForPlanet(event, game, Constants.PLANET4, player);
        sleeperForPlanet(event, game, Constants.PLANET5, player);
        sleeperForPlanet(event, game, Constants.PLANET6, player);
    }

    private void sleeperForPlanet(SlashCommandInteractionEvent event, Game game, String planet, Player player) {
        OptionMapping planetOption = event.getOption(planet);
        if (planetOption == null) {
            return;
        }
        String planetName = planetOption.getAsString();
        addOrRemoveSleeper(event, game, planetName, player);
    }

    public void addOrRemoveSleeper(GenericInteractionCreateEvent event, Game game, String planetName, Player player) {
        if (!game.getPlanets().contains(planetName)) {
            MessageHelper.replyToMessage(event, "Planet not found in map");
            return;
        }
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
            MessageHelper.replyToMessage(event, "System not found that contains planet");
            return;
        }

        if (unitHolder.getTokenList().contains(Constants.TOKEN_SLEEPER_PNG)) {
            tile.removeToken(Constants.TOKEN_SLEEPER_PNG, unitHolder.getName());
        } else {
            tile.addToken(Constants.TOKEN_SLEEPER_PNG, unitHolder.getName());
            String ident = player.getFactionEmoji();
            if (game.getSleeperTokensPlacedCount() > 5) {
                String message2 = ident + " has more than 5 Sleeper tokens out. Use buttons to remove a Sleeper token.";
                List<Button> buttons = ButtonHelper.getButtonsForRemovingASleeper(player, game);
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message2, buttons);
            }
        }
    }

}
