package ti4.commands.tokens;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.GameStateCommand;
import ti4.commands.uncategorized.ShowGame;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class AddFrontierTokens extends GameStateCommand {

    public AddFrontierTokens() {
        super(true, false);
    }

    @Override
    public String getName() {
        return Constants.ADD_FRONTIER_TOKENS;
    }

    @Override
    public String getDescription() {
        return "Add frontier tokens.";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES to confirm")
                        .setRequired(true));
    }

    public static void parsingForTile(GenericInteractionCreateEvent event, Game game) {
        Collection<Tile> tileList = game.getTileMap().values();
        for (Tile tile : tileList) {
            if (((tile.getPlanetUnitHolders().isEmpty() && tile.getUnitHolders().size() == 2) || Mapper.getFrontierTileIds().contains(tile.getTileID())) && !game.isBaseGameMode()) {
                boolean hasMirage = false;
                for (UnitHolder unitholder : tile.getUnitHolders().values()) {
                    if (unitholder.getName().equals(Constants.MIRAGE)) {
                        hasMirage = true;
                        break;
                    }
                }
                if (!hasMirage) AddToken.addToken(event, tile, Constants.FRONTIER, game);
            }
        }
        if (game.getRound() == 1) {
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green("deal2SOToAll", "Deal 2 SO To All"));
            MessageHelper.sendMessageToChannelWithButtons(game.getMainGameChannel(), "Press this button after every player is setup", buttons);
        }
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        parsingForTile(event, game);
        GameSaveLoadManager.saveGame(game, event);
        ShowGame.simpleShowGame(game, event);
    }
}
