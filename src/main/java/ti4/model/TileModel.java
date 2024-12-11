package ti4.model;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jetbrains.annotations.Nullable;
import ti4.ResourceHelper;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TileEmojis;

@Data
public class TileModel implements ModelInterface, EmbeddableModel {
    private String id;
    private String name;
    private List<String> aliases;
    private String imagePath;
    private List<String> planets;
    @Nullable
    private ShipPositionModel.ShipPosition shipPositionsType;
    private List<Point> spaceTokenLocations;
    private Set<WormholeModel.Wormhole> wormholes;
    @JsonProperty("isHyperlane")
    private boolean hyperlane = false;
    @JsonProperty("isAsteroidField")
    private boolean asteroidField = false;
    @JsonProperty("isSupernova")
    private boolean supernova = false;
    @JsonProperty("isNebula")
    private boolean nebula = false;
    @JsonProperty("isGravityRift")
    private boolean gravityRift = false;
    private String imageURL;
    private ComponentSource source;
    private String tileBack;

    @Override
    @JsonIgnore
    public boolean isValid() {
        return id != null
            && imagePath != null
            && source != null;
    }

    @JsonIgnore
    public String getNameNullSafe() {
        return Optional.ofNullable(name).orElse("");
    }

    public MessageEmbed getHelpMessageEmbed(boolean includeAliases) {
        EmbedBuilder eb = new EmbedBuilder();

        //TITLE
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(getId()).append(") __").append(getNameNullSafe()).append("__");
        eb.setTitle(sb.toString());

        sb = new StringBuilder();
        if (isEmpty()) sb.append(ExploreEmojis.Frontier);
        if (isAsteroidField()) sb.append(MiscEmojis.Asteroids);
        if (isSupernova()) sb.append(MiscEmojis.Supernova);
        if (isNebula()) sb.append(MiscEmojis.Nebula);
        if (isGravityRift()) sb.append(MiscEmojis.GravityRift);
        if (hasPlanets()) sb.append("\nPlanets: ").append(getPlanets().toString());
        eb.setDescription(sb.toString());

        eb.setThumbnail("attachment://" + getImagePath());
        if (includeAliases) eb.setFooter("Aliases: " + getAliases());
        return eb.build();
    }

    @JsonIgnore
    public String getTilePath() {
        String tileName = Mapper.getTileID(getId());
        return ResourceHelper.getInstance().getTileFile(tileName);
    }

    @JsonIgnore
    public boolean hasWormhole() {
        return getWormholes() != null && !getWormholes().isEmpty();
    }

    @JsonIgnore
    public boolean hasPlanets() {
        return getPlanets() != null && !getPlanets().isEmpty();
    }

    @JsonIgnore
    public int getNumPlanets() {
        return getPlanets() == null ? 0 : getPlanets().size();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return !hasPlanets();
    }

    @JsonIgnore
    public boolean isAsteroidField() {
        return asteroidField;
    }

    @JsonIgnore
    public boolean isSupernova() {
        return supernova;
    }

    @JsonIgnore
    public boolean isNebula() {
        return nebula;
    }

    @JsonIgnore
    public boolean isGravityRift() {
        return gravityRift;
    }

    @JsonIgnore
    public boolean isAnomaly() {
        return isAsteroidField() || isGravityRift() || isNebula() || isSupernova();
    }

    @JsonIgnore
    public String getAutoCompleteName() {
        StringBuilder sb = new StringBuilder();
        sb.append(getId()).append(" ");
        if (getName() != null) sb.append(getName());
        return sb.toString();
    }

    @JsonIgnore
    public boolean search(String searchString) {
        return getId().toLowerCase().contains(searchString) ||
            getNameNullSafe().toLowerCase().contains(searchString) ||
            (getAliases() != null && getAliases().stream().anyMatch(a -> a.toLowerCase().contains(searchString)));
    }

    @JsonIgnore
    public MessageEmbed getRepresentationEmbed() {
        throw new UnsupportedOperationException("Unimplemented method 'getRepresentationEmbed'");
    }

    @JsonIgnore
    public String getAlias() {
        return getId();
    }

    @JsonIgnore
    public Optional<String> getTileBackOption() {
        return Optional.ofNullable(tileBack);
    }

    @JsonIgnore
    public TI4Emoji getEmoji() {
        return TileEmojis.getTileEmojiFromTileID(tileBack);
    }
}
