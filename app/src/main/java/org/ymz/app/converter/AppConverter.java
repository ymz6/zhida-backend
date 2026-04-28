package org.ymz.app.converter;

import org.mapstruct.Mapper;
import org.ymz.app.model.dto.app.AppChatMessageInfo;
import org.ymz.app.model.dto.app.AppDetail;
import org.ymz.app.model.dto.app.AppSummary;
import org.ymz.app.model.dto.app.AppTaskInfo;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.entity.AppTask;

/**
 * 应用生成模块转换器。
 *
 * @author ymz
 */
@Mapper(componentModel = "spring")
public interface AppConverter {

    AppSummary toAppSummary(App app);

    AppDetail toAppDetail(App app);

    AppTaskInfo toAppTaskInfo(AppTask task);

    AppChatMessageInfo toAppChatMessageInfo(AppChatMessage message);
}
