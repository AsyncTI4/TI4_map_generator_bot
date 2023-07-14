package ti4.model;

import java.awt.Point;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.ResourceHelper;
import ti4.generator.Mapper;
import ti4.message.BotLogger;

@Data
public class TileModel {
    private String id;
    private String name;
    private List<String> aliases;
    private String imagePath;
    private List<String> planetIds;
    private ShipPositionModel.ShipPosition shipPositionsType;
    private List<Point> spaceTokenLocations;
    private Set<WormholeModel.Wormhole> wormholes;

    @JsonIgnore
    public String getNameNullSafe() {
        return Optional.ofNullable(name).orElse("");
    }

    public List<String> getPlanets() {
        return planetIds;
    }

    public void setPlanets(List<String> planetIds) {
        this.planetIds = planetIds;
    }

    public MessageEmbed getHelpMessageEmbed(boolean includeAliases) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("(" + getId() + ") __" + getNameNullSafe() + "__");
        eb.setThumbnail("attachment://" + getImagePath());
        if (!getPlanets().isEmpty()) eb.setDescription("Planets: " + getPlanets().toString());
        if (includeAliases) eb.setFooter("Aliases: " + getAliases());
        return eb.build();
    }

    @JsonIgnore
    public String getTilePath() {
        String tileName = Mapper.getTileID(getId());
        String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
        if (tilePath == null) {
            BotLogger.log("Could not find tile image: " + getId());
        }
        return tilePath;
    }
}
