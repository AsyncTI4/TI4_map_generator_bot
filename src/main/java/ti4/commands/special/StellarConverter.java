package ti4.commands.special;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.AsyncTI4DiscordBot;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class StellarConverter extends SpecialSubcommandData {

    public StellarConverter() {
        super(Constants.STELLAR_CONVERTER, "Select planet to use Stellar Converter on it");
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        OptionMapping planetOption = event.getOption(Constants.PLANET);
        if (planetOption == null) {
            return;
        }
        String planetName = planetOption.getAsString();
        if (!activeGame.getPlanets().contains(planetName)) {
            MessageHelper.replyToMessage(event, "Planet not found in map");
            return;
        }
        secondHalfOfStellar(activeGame, planetName, event);
    }

    public static void secondHalfOfStellar(Game activeGame, String planetName, GenericInteractionCreateEvent event) {

        Tile tile = activeGame.getTileFromPlanet(planetName);
        if (tile == null) {
            MessageHelper.replyToMessage(event, "System not found that contains planet");
            return;
        }
        UnitHolder unitHolder = tile.getUnitHolderFromPlanet(planetName);
        if (unitHolder == null) {
            MessageHelper.replyToMessage(event, "System not found that contains planet");
            return;
        }

        String message1 = "There is a great disturbance in the Force, as if millions of voices suddenly cried out in terror and were suddenly silenced";
        postTileInDisasterWatch(activeGame, tile, 1, "Moments before disaster in game " + activeGame.getName());
        MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), message1);

        for (Player p2 : activeGame.getRealPlayers()) {
            if (p2.getPlanets().contains(planetName)) {
                MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),
                    p2.getRepresentation(true, true) + " we regret to inform you but " + Mapper.getPlanet(planetName).getName() + " has been stellar converted");
            }
        }
        activeGame.removePlanet(unitHolder);
        unitHolder.removeAllTokens();
        unitHolder.addToken(Constants.WORLD_DESTROYED_PNG);

        StringBuilder message2 = new StringBuilder();
        message2.append(Mapper.getPlanet(planetName).getName());
        message2.append(" has been stellar converted");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2.toString());

        message2.append(" by ");
        message2.append(activeGame.getPlayer(event.getUser().getId()).getRepresentation());
        postTileInDisasterWatch(activeGame, tile, 0, message2.toString());
    }

    public static void postTileInDisasterWatch(Game activeGame, Tile tile, Integer rings, String message) {
        if (AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("disaster-watch-party", true).size() > 0 && !activeGame.isFoWMode()) {
            TextChannel watchPary = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("disaster-watch-party", true).get(0);
            FileUpload systemWithContext = GenerateTile.getInstance().saveImage(activeGame, rings, tile.getPosition(), null);
            MessageHelper.sendMessageWithFile(watchPary, systemWithContext, message, false);
        }
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        SpecialCommand.reply(event);
    }
}
