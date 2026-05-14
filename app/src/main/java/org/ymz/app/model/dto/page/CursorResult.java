package org.ymz.app.model.dto.page;

import lombok.Data;

import java.util.List;

/**
 * 游标查询结果封装类。
 *
 * @author ymz
 */
@Data
public class CursorResult<T> {

    /**
     * 当前批次数据列表。
     */
    private List<T> list;

    /**
     * 下一批数据查询游标；没有更多数据时为 null。
     */
    private String nextCursor;

    /**
     * 是否还有更多数据。
     */
    private boolean hasMore;

    public static <T> CursorResult<T> of(List<T> list, String nextCursor, boolean hasMore) {
        CursorResult<T> result = new CursorResult<>();
        result.setList(list);
        result.setNextCursor(nextCursor);
        result.setHasMore(hasMore);
        return result;
    }
}
