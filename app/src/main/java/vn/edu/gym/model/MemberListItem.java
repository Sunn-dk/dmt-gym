package vn.edu.gym.model;

public record MemberListItem(
        Member member,
        String gymSummary,
        String ptSummary,
        boolean hasOpenVisit
) {
}
