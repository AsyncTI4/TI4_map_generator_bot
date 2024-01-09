package ti4.commands.planet;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.uncategorized.InfoThreadCommand;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.GameManager;
import ti4.map.Planet;
import ti4.message.MessageHelper;
import ti4.model.PlanetTypeModel;
import ti4.model.TechSpecialtyModel;
import ti4.model.TileModel;

public class PlanetInfo extends PlanetSubcommandData implements InfoThreadCommand {
    public PlanetInfo() {
        super(Constants.PLANET_INFO, "List Planets");
    }

    @Override
    public String getActionID() {
        return Constants.PLANET_INFO;
    }

    public boolean accept(SlashCommandInteractionEvent event) {
        return acceptEvent(event, getActionID());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var user = event.getUser();
        var activeGame = GameManager.getInstance().getUserActiveGame(user.getId());

        var player = activeGame.getPlayer(user.getId());
        var planets = activeGame.getPlayer(user.getId()).getPlanets()
            .stream()
            .map(planetId -> {
                var planet = (Planet) activeGame.getPlanetsInfo().get(planetId);
                var planetModel = Mapper.getPlanet(planetId);

                EmbedBuilder eb = new EmbedBuilder();

                StringBuilder sb = new StringBuilder();
                sb.append(planetModel.getEmoji()).append("__").append(planetModel.getName()).append("__");
                eb.setTitle(sb.toString());

                TileModel tile = TileHelper.getTile(planetModel.getTileId());
                sb = new StringBuilder();
                sb.append(Emojis.getResourceEmoji(planet.getResources())).append(Emojis.getInfluenceEmoji(planet.getInfluence())).append("\n");

                if (tile != null) sb.append("System: ").append(tile.getName());
                System.err.println(sb.toString());
                eb.setDescription(sb.toString());
                if (planetModel.getLegendaryAbilityName() != null) eb.addField(Emojis.LegendaryPlanet + planetModel.getLegendaryAbilityName(), planetModel.getLegendaryAbilityText(), false);
                if (planetModel.getFlavourText() != null) eb.addField("", planetModel.getFlavourText(), false);

                sb = new StringBuilder();
                sb.append("ID: ").append(planetId);
                eb.setFooter(sb.toString());

                if (planetModel.getEmojiURL() != null) eb.setThumbnail(planetModel.getEmojiURL());

                return eb.build();
            })
            .toList();

        MessageHelper.sendMessageEmbedsToCardsInfoThread(activeGame, player, "__**Planets:**__\n", planets);
    }

    protected String getActionDescription() {
        return "Sends list of owned planets to your Cards-Info thread";
    }
}
