package vn.edu.gym.model;

import java.util.List;

public record PageResult<T>(
        List<T> items,
        int page,
        int pageSize,
        long totalItems
) {
    public int totalPages() {
        if (totalItems <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalItems / pageSize);
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return page + 1 < totalPages();
    }

    /** Clamps a 0-based page request to the valid range [0, maxPage] using 64-bit arithmetic. */
    public static int clampPage(int requestedPage, long totalItems, int pageSize) {
        if (requestedPage <= 0 || totalItems <= 0 || pageSize <= 0) {
            return 0;
        }
        long totalPages = (totalItems + pageSize - 1L) / pageSize;
        long maxPage = Math.max(0L, totalPages - 1L);
        return (int) Math.min(requestedPage, maxPage);
    }

    /** Computes a non-negative 64-bit SQL OFFSET without 32-bit integer overflow. */
    public static long safeOffset(int clampedPage, int pageSize) {
        if (clampedPage <= 0 || pageSize <= 0) {
            return 0L;
        }
        return (long) clampedPage * (long) pageSize;
    }
}
