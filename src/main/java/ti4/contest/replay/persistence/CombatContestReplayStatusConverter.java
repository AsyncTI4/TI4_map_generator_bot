package ti4.contest.replay.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ti4.contest.replay.core.CombatContestReplayStatus;

@Converter(autoApply = true)
public class CombatContestReplayStatusConverter implements AttributeConverter<CombatContestReplayStatus, String> {

    @Override
    public String convertToDatabaseColumn(CombatContestReplayStatus attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public CombatContestReplayStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CombatContestReplayStatus.valueOf(dbData);
    }
}
