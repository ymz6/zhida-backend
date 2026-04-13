package org.ymz.app.model.dto.page;

import com.mybatisflex.core.paginate.Page;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 列表分页查询请求基类
 * 约定：所有的分页查询请求必须集成此类
 * @author ymz
 */
@Data
public class PageQuery {
    @Min(value = 1, message = "非法页码")
    private int pageNum = 1;

    @Min(value = 1, message = "非法页大小")
    private int pageSize = 10;

    /**
     * 转换为 MyBatisFlex 的 Page对象
     */
    public <T> Page<T> toPage() {
        return Page.of(pageNum, pageSize);
    }
}
