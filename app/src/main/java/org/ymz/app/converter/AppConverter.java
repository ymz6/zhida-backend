package org.ymz.app.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.ymz.app.model.dto.app.AppChatMessageInfo;
import org.ymz.app.model.dto.app.AppDetail;
import org.ymz.app.model.dto.app.AppSummary;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppChatMessage;

/**
 * 应用生成模块转换器。
 *
 * @author ymz
 */
@Mapper(componentModel = "spring")
public interface AppConverter {

    @Mapping(target = "author", ignore = true)
    AppSummary toAppSummary(App app);

    @Mapping(target = "author", ignore = true)
    AppDetail toAppDetail(App app);

    AppChatMessageInfo toAppChatMessageInfo(AppChatMessage message);
}
