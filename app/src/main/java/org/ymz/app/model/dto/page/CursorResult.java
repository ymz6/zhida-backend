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
     * 下一次查询更早数据时传入的游标。
     */
    private Long nextCursor;

    /**
     * 是否还有更早数据。
     */
    private boolean hasMore;

    public static <T> CursorResult<T> of(List<T> list, Long nextCursor, boolean hasMore) {
        CursorResult<T> result = new CursorResult<>();
        result.setList(list);
        result.setNextCursor(nextCursor);
        result.setHasMore(hasMore);
        return result;
    }
}
