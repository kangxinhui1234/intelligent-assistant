package com.kxh.aiagent.etl;

import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * 读取markdown文本然后解析成a list of Document objects
 */

@Component
public class MyMarkdownReader {
    @Autowired
    ChatModel dashscopeChatModel;

    private final ResourcePatternResolver resourcePatternResolver;

    MyMarkdownReader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 文档读取，分片提取出Document
     * @return
     * @throws IOException
     */
    public List<Document> loadMarkdown() throws IOException {
        List<Document> list = new ArrayList<>();
        Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
        for(int i=0;i< resources.length;i++){
            String filename = resources[i].getFilename();
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withIncludeCodeBlock(false)
                    .withIncludeBlockquote(false)
                    .withAdditionalMetadata("filename", filename)  // 增加标签  元数据
                    .build();
            MarkdownDocumentReader reader = new MarkdownDocumentReader(resources[i], config);
            list.addAll(reader.get());

        }
        return transformDocumentKeyMetadata(list); // get document metadata字段增加关键词信息
    }

    /**
     * 文档转换 对提取的文档进行处理转换 转换器实现DocumentTransformer接口
     */
    public List<Document> transformDocument(List<Document> documents){
        TextSplitter textSplitter = new TokenTextSplitter(true);
        return textSplitter.apply(documents);
    }


    /**
     * 文档转换 元数据增强 为文档生成新的元数据 摘要等信息 通过向ai发送文本生成新的关键词信息，成本较高
     */
    public List<Document> transformDocumentKeyMetadata(List<Document> documents){
        KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(dashscopeChatModel,3); // 数字是生成关键字的个数
        return keywordMetadataEnricher.apply(documents);
    }
}
