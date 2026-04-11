package org.ymz.app.model.dto.page;

import com.mybatisflex.core.paginate.Page;
import lombok.Data;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页查询结果封装类
 * @author ymz
 */
@Data
public class PageResult<T> {
    /**
     * 当前页数据列表
     */
    private List<T> list;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 当前页码
     */
    private long pageNum;

    /**
     * 每页大小
     */
    private long pageSize;

    /**
     * 总页数
     */
    private long totalPages;

    /**
     * 是否存在下一页
     */
    private boolean hasNext;

    /**
     * 是否存在上一页
     */
    private boolean hasPrevious;


    /**
     * 从 MyBatis-Flex Page 对象构建 PageResult
     *
     * @param page Page 对象
     * @param <T>  数据类型
     * @return PageResult 实例
     */
    public static <T> PageResult<T> of(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setList(page.getRecords());
        result.setTotal(page.getTotalRow());
        result.setPageNum(page.getPageNumber());
        result.setPageSize(page.getPageSize());
        result.setTotalPages(page.getTotalPage());
        result.setHasNext(page.hasNext());
        result.setHasPrevious(page.hasPrevious());
        return result;
    }

    /**
     * 从 MyBatis-Flex Page 对象构建 PageResult，并转换数据类型
     *
     * @param page     Page 对象
     * @param converter 数据转换函数
     * @param <S>      源数据类型
     * @param <T>      目标数据类型
     * @return PageResult 实例
     */
    public static <S, T> PageResult<T> of(Page<S> page, Function<S, T> converter) {
        PageResult<T> result = new PageResult<>();
        result.setList(page.getRecords().stream()
                .map(converter)
                .collect(Collectors.toList()));
        result.setTotal(page.getTotalRow());
        result.setPageNum(page.getPageNumber());
        result.setPageSize(page.getPageSize());
        result.setTotalPages(page.getTotalPage());
        result.setHasNext(page.hasNext());
        result.setHasPrevious(page.hasPrevious());
        return result;
    }
}
