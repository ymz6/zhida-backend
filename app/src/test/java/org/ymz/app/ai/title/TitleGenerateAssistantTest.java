package org.ymz.app.ai.title;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class TitleGenerateAssistantTest {
    @Resource
    TitleGenerateAssistant titleGenerateAssistant;

    /**
     * 测试一下标题生成效果
     */
    @Test
    void chat() {
        List<String> prompts = List.of(
                // 正常有效输入
                "我想做一个大学生校园二手交易平台，支持发布闲置物品、关键词搜索、用户私信沟通和订单管理。",
                "做一个宠物领养平台，用户可以发布待领养宠物信息，浏览宠物资料，并在线提交领养申请。",
                "我想开发一个个人记账应用，支持分类记录收入和支出、按月统计、图表展示和预算提醒。",
                "帮我做一个在线博客系统，支持文章发布、分类标签、评论互动和后台管理。",
                "我想生成一个旅游攻略分享网站，用户可以发布游记、上传图片、收藏景点并查看热门路线推荐。",
                "做一个图书借阅管理系统，管理员可以录入图书信息，学生可以查询、借阅和归还图书。",

                // 口语化输入
                "想整一个给学生平时记作业和提醒截止时间的小工具，最好还能按课程分类。",
                "想做个那种能发帖子交流的地方，大家可以分享考研经验，也能问问题。",
                "我想搞一个给社团用的报名和活动管理页面，能看活动列表，还能统计报名人数。",

                // 边界输入
                "做一个待办清单应用",
                "校园跑腿平台",
                "课程表管理系统",
                "在线问卷小程序",

                // 过短或模糊
                "做个网站",
                "帮我生成一个应用",
                "想做点东西",
                "做一个厉害的系统",
                "随便做个好看的页面",

                // 无意义输入
                "啊啊啊啊啊啊",
                "123123123123",
                "....////?????",
                "测试测试测试测试测试",
                "qweqweqweasd",

                // 容易误判
                "我想做一个适合年轻人的平台，要简洁、高级、有未来感。",
                "帮我做一个功能很多的系统，用户体验要好，界面漂亮，最好比较创新。",
                "做一个和学习有关的应用，具体还没想好。",
                "想做个社交类的东西，但是我还不确定具体功能。",

                // 中英混合
                "我想做一个 Todo App，支持任务分类、截止时间提醒和完成状态统计。",
                "帮我做一个 AI 海报生成工具，输入文案后自动生成宣传海报。",
                "做一个 Blog CMS，支持文章发布、标签管理和评论审核。"
        );

        for (int i = 0; i < prompts.size(); i++) {
            String prompt = prompts.get(i);

            TitleGenerateResult result = titleGenerateAssistant.chat(prompt); // 如果方法名不同，改这里
            assertNotNull(result);

            System.out.println("==================================================");
            System.out.println("Case " + (i + 1));
            System.out.println("Prompt: " + prompt);
            System.out.println("accepted = " + result.isAccepted());
            System.out.println("title    = " + result.getTitle());
            System.out.println("reason   = " + result.getReason());
        }
    }
}