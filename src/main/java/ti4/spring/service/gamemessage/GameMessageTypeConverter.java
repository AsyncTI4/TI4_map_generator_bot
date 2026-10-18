package ti4.spring.service.gamemessage;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ti4.message.GameMessageType;

@Converter
class GameMessageTypeConverter implements AttributeConverter<GameMessageType, String> {

    @Override
    public String convertToDatabaseColumn(GameMessageType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public GameMessageType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : GameMessageType.valueOf(dbData);
    }
}
