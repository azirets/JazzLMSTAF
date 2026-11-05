package kg.jazzlms.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Элемент GET /api/groups: { "group": {...}, "members": N } */
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupListItem {

    private GroupResponse group;
    private int members;
}
