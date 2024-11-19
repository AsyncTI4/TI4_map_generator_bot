package ti4.commands.planet;

import java.util.List;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.buttons.Buttons;
import ti4.image.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;

public class PlanetInfo extends PlanetSubcommandData {
    public PlanetInfo() {
        super(Constants.PLANET_INFO, "List Planets");
    }

    @NotNull
    @Override
    public String getName() {
        return Constants.PLANET_INFO;
    }

    protected String getActionDescription() {
        return "Sends list of owned planets to your Cards-Info thread";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        Game game = GameManager.getUserActiveGame(user.getId());

        Player player = game.getPlayer(user.getId());
        sendPlanetInfo(player);
    }

    @ButtonHandler(Constants.REFRESH_PLANET_INFO)
    public static void sendPlanetInfo(Player player) {
        List<MessageEmbed> planetEmbeds = player.getPlanets()
            .stream()
            .map(planetID -> getPlanetEmbed(player, planetID))
            .toList();

        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(player.getCardsInfoThread(), "__**Planets:**__", planetEmbeds, List.of(Buttons.REFRESH_PLANET_INFO));
    }

    private static MessageEmbed getPlanetEmbed(Player player, String planetID) {
        Game game = player.getGame();
        Planet planet = game.getPlanetsInfo().get(planetID);
        PlanetModel planetModel = Mapper.getPlanet(planetID);
        Tile tile = game.getTileFromPlanet(planetID);

        EmbedBuilder eb = new EmbedBuilder();

        StringBuilder sb = new StringBuilder();
        sb.append(planetModel.getEmoji()).append("__").append(planetModel.getName()).append("__");
        if (tile != null) sb.append(" (").append(tile.getPosition()).append(")");
        eb.setTitle(sb.toString());

        sb = new StringBuilder();
        if (player.getReadiedPlanets().contains(planetID)) {
            sb.append("Ready: ");
        } else {
            sb.append("Exhausted: ");
        }
        sb.append(Emojis.getResourceEmoji(planet.getResources())).append(Emojis.getInfluenceEmoji(planet.getInfluence())).append("\n");
        eb.setDescription(sb.toString());
        Mapper.getTokensToName();
        if (!planet.getTokenList().isEmpty()) eb.addField("Attachments", planet.getTokenList().stream().map(Mapper::getTokenIDFromTokenPath).toList().toString(), true);

        if (planetModel.getLegendaryAbilityName() != null) eb.addField(Emojis.LegendaryPlanet + planetModel.getLegendaryAbilityName(), planetModel.getLegendaryAbilityText(), false);

        return eb.build();
    }
}
