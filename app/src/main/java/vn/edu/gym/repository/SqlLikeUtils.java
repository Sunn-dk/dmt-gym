package vn.edu.gym.repository;

public final class SqlLikeUtils {

    private SqlLikeUtils() {
    }

    public static String containsPattern(String raw) {
        String escaped = raw
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
