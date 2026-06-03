package ca.openbox.process.entities;

import java.util.Locale;
import java.util.Set;

public final class LeaveApplicationDeleteRules {
    public static final String PENDING_STATUS = "pending";
    public static final String DRAFT_STATUS = "draft";
    public static final String APPROVED_STATUS = "approved";
    public static final String REJECTED_STATUS = "rejected";
    public static final String CANCELLED_STATUS = "cancelled";

    private static final Set<String> DELETABLE_STATUSES = Set.of(PENDING_STATUS, DRAFT_STATUS);

    private LeaveApplicationDeleteRules() {
    }

    public static boolean canDeleteStatus(String status) {
        return DELETABLE_STATUSES.contains(normalizeStatus(status));
    }

    public static String normalizeStatus(String status) {
        if (status == null) {
            return "";
        }
        return status.trim().toLowerCase(Locale.ROOT);
    }
}
