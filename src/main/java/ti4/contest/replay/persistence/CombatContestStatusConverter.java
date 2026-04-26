package ti4.contest.replay.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ti4.spring.service.contest.CombatContestStatus;

@Converter(autoApply = true)
public class CombatContestStatusConverter implements AttributeConverter<CombatContestStatus, String> {

    @Override
    public String convertToDatabaseColumn(CombatContestStatus attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public CombatContestStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CombatContestStatus.valueOf(dbData);
    }
}
