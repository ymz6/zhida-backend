package org.ymz.app.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.mapper.AppChatMessageMapper;
import org.ymz.app.service.AppChatMessageService;
import org.springframework.stereotype.Service;

/**
 * 应用对话消息表 服务层实现。
 *
 * @author ymz
 */
@Service
public class AppChatMessageServiceImpl extends ServiceImpl<AppChatMessageMapper, AppChatMessage>  implements AppChatMessageService{

}
