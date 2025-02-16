package ti4.model;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;

import org.apache.commons.lang3.StringUtils;
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

    public enum TileBack {
        GREEN, BLUE, RED, BLACK;

        @JsonCreator
        public static TileBack fromString(String value) {
            return value == null ? TileBack.BLACK : TileBack.valueOf(value.toUpperCase());
        }

        @JsonValue
        public String toValue() {
            return this.name().toLowerCase();
        }
    }

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
    private TileBack tileBack = TileBack.BLACK;

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

    public MessageEmbed getRepresentationEmbed(boolean includeAliases) {
        EmbedBuilder eb = new EmbedBuilder();

        //TITLE
        eb.setTitle(getEmbedTitle());

        StringBuilder sb = new StringBuilder();
        if (isEmpty()) sb.append(ExploreEmojis.Frontier);
        if (isAsteroidField()) sb.append(MiscEmojis.Asteroids);
        if (isSupernova()) sb.append(MiscEmojis.Supernova);
        if (isNebula()) sb.append(MiscEmojis.Nebula);
        if (isGravityRift()) sb.append(MiscEmojis.GravityRift);
        if (hasPlanets()) sb.append("\nPlanets: ").append(getPlanets().toString());
        eb.setDescription(sb.toString());

        // Image
        TI4Emoji emoji = getEmoji();
        if (emoji != null && emoji.asEmoji() instanceof CustomEmoji customEmoji) {
            if (emoji.name().endsWith("Back") && !StringUtils.isEmpty(getImagePath())) {
                eb.setThumbnail("https://github.com/AsyncTI4/TI4_map_generator_bot/blob/master/src/main/resources/tiles/" + getImagePath() + "?raw=true");
            } else {
                eb.setThumbnail(customEmoji.getImageUrl());
            }
        } 

        if (includeAliases) eb.setFooter("Aliases: " + getAliases());
        return eb.build();
    }

    public String getEmbedTitle() {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(getId()).append(") ");
        if (getEmoji() != null) sb.append(getEmoji().emojiString()).append(" ");
        if (!getNameNullSafe().isEmpty()) sb.append("__").append(getNameNullSafe()).append("__");
        return sb.toString();
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
        return getRepresentationEmbed(false);
    }

    @JsonIgnore
    public String getAlias() {
        return getId();
    }

    @JsonIgnore
    @Nullable
    public TI4Emoji getEmoji() {
        return TileEmojis.getTileEmojiFromTileID(getId());
    }
}
