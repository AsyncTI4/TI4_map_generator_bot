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
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import java.util.concurrent.ThreadLocalRandom;

public class StellarConverter extends SpecialSubcommandData {

    public StellarConverter() {
        super(Constants.STELLAR_CONVERTER, "Stellar Convert a planet.");
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet to be converted.").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayer(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found.");
            return;
        }

        OptionMapping planetOption = event.getOption(Constants.PLANET);
        if (planetOption == null) {
            return;
        }
        String planetName = planetOption.getAsString();
        if (!game.getPlanets().contains(planetName)) {
            MessageHelper.replyToMessage(event, "Planet not found in map.");
            return;
        }
        secondHalfOfStellar(game, planetName, event);
    }

    public static void secondHalfOfStellar(Game game, String planetName, GenericInteractionCreateEvent event) {

        Tile tile = game.getTileFromPlanet(planetName);
        if (tile == null) {
            MessageHelper.replyToMessage(event, "System not found that contains planet.");
            return;
        }
        UnitHolder unitHolder = tile.getUnitHolderFromPlanet(planetName);
        if (unitHolder == null) {
            MessageHelper.replyToMessage(event, "System not found that contains planet.");
            return;
        }

        String message1 = (ThreadLocalRandom.current().nextInt(20) == 0 ? "# _Hey, Stellar!_" :
            "There is a great disturbance in the Force, as if millions of voices suddenly cried out in terror and were suddenly silenced.");
        postTileInDisasterWatch(game, tile, 1, "Moments before disaster in game " + game.getName());
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), message1);

        for (Player p2 : game.getRealPlayers()) {
            if (p2.getPlanets().contains(planetName)) {
                MessageHelper.sendMessageToChannel(p2.getCorrectChannel(),
                    p2.getRepresentation(true, true) + " we regret to inform you but " + Mapper.getPlanet(planetName).getName() + " has been Stellar Converted.");
                int amountToKill = 0;
                amountToKill = unitHolder.getUnitCount(UnitType.Infantry, p2.getColor());
                if (p2.hasInf2Tech()) {
                    ButtonHelper.resolveInfantryDeath(game, p2, amountToKill);
                    boolean cabalMech = false;
                    if (unitHolder.getUnitCount(UnitType.Mech,
                        p2.getColor()) > 0
                        && p2.hasUnit("cabal_mech")
                        && !ButtonHelper.isLawInPlay(game, "articles_war")) {
                        cabalMech = true;
                    }
                    if (p2.hasAbility("amalgamation")
                        && (ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", p2, tile) || cabalMech)) {
                        ButtonHelperFactionSpecific.cabalEatsUnit(p2, game, p2, amountToKill, "infantry", event);
                    }
                }
            }
        }
        game.removePlanet(unitHolder);
        unitHolder.removeAllTokens();
        unitHolder.addToken(Constants.WORLD_DESTROYED_PNG);

        StringBuilder message2 = new StringBuilder();
        message2.append(Mapper.getPlanet(planetName).getName());
        message2.append(" has been stellar converted");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2.toString());

        message2.append(" by ");
        message2.append(game.getPlayer(event.getUser().getId()).getRepresentation());
        postTileInDisasterWatch(game, tile, 0, message2.toString());
    }

    public static void postTileInDisasterWatch(Game game, Tile tile, Integer rings, String message) {
        if (AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("disaster-watch-party", true).size() > 0 && !game.isFowMode()) {
            TextChannel watchPary = AsyncTI4DiscordBot.guildPrimary.getTextChannelsByName("disaster-watch-party", true).get(0);
            FileUpload systemWithContext = GenerateTile.getInstance().saveImage(game, rings, tile.getPosition(), null);
            MessageHelper.sendMessageWithFile(watchPary, systemWithContext, message, false);
        }
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        SpecialCommand.reply(event);
    }
}
