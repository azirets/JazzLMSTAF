package kg.jazzlms.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Элемент GET /api/branches: { "branch": {...}, "members": N } */
@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BranchListItem {

    private BranchResponse branch;
    private int members;
}
