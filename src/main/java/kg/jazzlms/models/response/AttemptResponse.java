package kg.jazzlms.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Попытка прохождения теста: start (201), get, submit (200). Поля score/passed заполнены после submit */
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AttemptResponse {

    private UUID id;
    private UUID testId;
    private UUID userId;
    private String startedAt;
    private String submittedAt;
    private Integer durationMinutes;
    private String deadline;
    private Boolean passed;
    private Integer passScore;
    private Integer score;
    private List<Map<String, Object>> questions;
}
