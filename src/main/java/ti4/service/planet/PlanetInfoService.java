package ti4.service.planet;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.buttons.Buttons;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
public class PlanetInfoService {

    public static void sendPlanetInfo(Player player) {
        List<MessageEmbed> planetEmbeds = player.getPlanets().stream()
                .map(planetID -> getPlanetEmbed(player, planetID))
                .toList();

        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                player.getCardsInfoThread(), "__**Planets:**__", planetEmbeds, List.of(Buttons.REFRESH_PLANET_INFO));
    }

    private static MessageEmbed getPlanetEmbed(Player player, String planetID) {
        Game game = player.getGame();
        Planet planet = game.getPlanetsInfo().get(planetID);
        PlanetModel planetModel = Mapper.getPlanet(planetID);
        Tile tile = game.getTileFromPlanet(planetID);

        EmbedBuilder eb = new EmbedBuilder();

        StringBuilder sb = new StringBuilder();
        sb.append(planetModel.getEmoji())
                .append("__")
                .append(planetModel.getName())
                .append("__");
        if (tile != null) sb.append(" (").append(tile.getPosition()).append(")");
        eb.setTitle(sb.toString());

        sb = new StringBuilder();
        if (player.getReadiedPlanets().contains(planetID)) {
            sb.append("Ready: ");
        } else {
            sb.append("Exhausted: ");
        }
        sb.append(MiscEmojis.getResourceEmoji(planet.getResources()))
                .append(MiscEmojis.getInfluenceEmoji(planet.getInfluence()))
                .append("\n");
        eb.setDescription(sb.toString());
        Mapper.getTokensToName();
        if (!planet.getTokenList().isEmpty())
            eb.addField(
                    "Attachments",
                    planet.getTokenList().stream()
                            .map(Mapper::getTokenIDFromTokenPath)
                            .toList()
                            .toString(),
                    true);

        if (planetModel.getLegendaryAbilityName() != null)
            eb.addField(
                    MiscEmojis.LegendaryPlanet + planetModel.getLegendaryAbilityName(),
                    planetModel.getLegendaryAbilityText(),
                    false);

        return eb.build();
    }
}
