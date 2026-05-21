package ca.openbox.process.entities;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HistoryVisibility {
    private boolean allowed;
    private HistoryVisibilityScope scope;
    private String groupName;
}
