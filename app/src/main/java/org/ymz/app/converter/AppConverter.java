package org.ymz.app.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.ymz.app.model.dto.app.AppVO;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.User;

/**
 * 应用转换器
 * @author ymz
 */
@Mapper(componentModel = "spring", uses = UserConverter.class)
public interface AppConverter {

    /**
     * App 和作者信息转 AppVO
     */
    @Mapping(target = "id", source = "app.id")
    @Mapping(target = "author", source = "author")
    AppVO toAppVO(App app, User author);
}
