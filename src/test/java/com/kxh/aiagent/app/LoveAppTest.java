package com.kxh.aiagent.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kxh.aiagent.agent.InvestManus;
import com.kxh.aiagent.agent.KxhManus;
import com.kxh.aiagent.tools.financeTool.ValuationQuery;
import com.kxh.aiagent.tools.financeTool.financeIndexQuery;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
public class LoveAppTest {

    @Autowired
    LoveApp loveApp;

    @Autowired
    KxhManus manus;

    @Autowired
    InvestManus investManus;

    @Autowired
    financeIndexQuery financeIndexQuery;
    @Autowired
    ValuationQuery valuationQuery;


    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private Advisor loveAppRagCloudAdvisor;

    @Test
    public void chat(){
        String id = UUID.randomUUID().toString();

        String chatMsg1 = "你好 我叫kxh,我的另一半叫徐思甜";
        loveApp.doChat(chatMsg1,id);

//        String chatMsg2 = "我是单身状态";
//        loveApp.doChat(chatMsg2,id);

        String chatMsg3 = "我想让xst更爱我";
        loveApp.doChat(chatMsg3,id);

        String chatMsg4 = "帮我回忆一下，我的另一半叫啥";
        loveApp.doChat(chatMsg4,id);

    }


    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我是程序员鱼皮，我想让另一半（编程导航）更爱我，但我不知道该怎么做";
        LoveApp.LoveReport loveReport = loveApp.generateLoveReport(message, chatId);
        Assertions.assertNotNull(loveReport);
    }

    @Test
    void generateInvestAdviceByQuestionAnswer(){
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我投资入门2年，我该投资哪些品种呢";
        String msg = loveApp.generateInvestAdviceByQuestionAnswer(message, chatId);
        Assertions.assertNotNull(msg);
    }

    @Test
    void generateInvestAdviceByRAG(){
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "小狗";
        String msg = loveApp.generateInvestAdviceByRAG(message, chatId);
        Assertions.assertNotNull(msg);
    }












    @Test
    void doChatWithTools() {


        testMessage("帮我查找本地 customer信息 id=42");

        // 测试联网搜索问题的答案
        testMessage("周末想带女朋友去上海约会，推荐几个适合情侣的小众打卡地？");

        // 测试网页抓取：恋爱案例分析
        testMessage("最近和对象吵架了，看看编程导航网站（codefather.cn）的其他情侣是怎么解决矛盾的？");

        // 测试资源下载：图片下载
        testMessage("直接下载一张适合做手机壁纸的星空情侣图片为文件");

        // 测试终端操作：执行代码
        testMessage("执行 Python3 脚本来生成数据分析报告");

        // 测试文件操作：保存用户档案
        testMessage("保存我的恋爱档案为文件");

        // 测试 PDF 生成
        testMessage("生成一份‘七夕约会计划’PDF，包含餐厅预订、活动流程和礼物清单");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = loveApp.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }



    @Test
    public void doChatWithMcp(){
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "我想投资中国平安,请你帮我分析下";
        System.out.println("中国人");
        String msg = loveApp.doChatWithMcp(message, chatId);
        Assertions.assertNotNull(msg);
    }

    @Test
    public void doChatWithManus(){
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "我的另一半居住在上海静安区，请帮我找到5公里的约会地点，并结合一些网络图片，指定一分详细的计划，以pdf格式输出";
        manus.run(message);
    }


    @Test
    public void financeIndexQuery() throws JsonProcessingException {
        valuationQuery.queryCompanyValuation("000001",5);
    }


    @Test
    public void investManus(){
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "我想投资中国平安,请你帮我分析下";
        InvestManus manus = new InvestManus(allTools,dashscopeChatModel,loveAppRagCloudAdvisor);
        manus.run(message);
    }

}
