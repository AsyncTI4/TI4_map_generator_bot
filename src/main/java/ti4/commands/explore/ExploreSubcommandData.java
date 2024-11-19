package ti4.commands.explore;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public abstract class ExploreSubcommandData extends SubcommandData {

    private Game game;
    private User user;
    protected final OptionData typeOption = new OptionData(OptionType.STRING, Constants.TRAIT, "Cultural, Industrial, Hazardous, or Frontier.").setAutoComplete(true);
    protected final OptionData idOption = new OptionData(OptionType.STRING, Constants.EXPLORE_CARD_ID, "Explore card id sent between (). May include multiple comma-separated ids.");

    public String getActionID() {
        return getName();
    }

    public ExploreSubcommandData(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    public Game getActiveGame() {
        return game;
    }

    public User getUser() {
        return user;
    }

    abstract public void execute(SlashCommandInteractionEvent event);

    public void preExecute(SlashCommandInteractionEvent event) {
        user = event.getUser();
        game = GameManager.getUserActiveGame(user.getId());
    }

    protected Tile getTile(SlashCommandInteractionEvent event, String tileID, Game game) {
        if (game.isTileDuplicated(tileID)) {
            MessageHelper.sendMessageToEventChannel(event, "Duplicate tile name found, please use position coordinates");
            return null;
        }
        Tile tile = game.getTile(AliasHandler.resolveTile(tileID));
        if (tile == null) {
            tile = game.getTileByPosition(tileID);
        }
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return null;
        }
        return tile;
    }
}
