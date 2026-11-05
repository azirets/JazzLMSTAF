package kg.jazzlms.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Страница ленты: { items, total, page (с 0), size } */
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TimelinePageResponse {

    private List<ActivityEventResponse> items;
    private long total;
    private int page;
    private int size;
}
