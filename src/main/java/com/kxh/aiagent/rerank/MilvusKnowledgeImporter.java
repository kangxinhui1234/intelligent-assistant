package com.kxh.aiagent.rerank;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@Profile("milvus")
public class MilvusKnowledgeImporter {

    @Resource(name = "vectorStore")
    VectorStore vectorStore;

    public void importFromPdf(String pdfPath) {
        List<Document> documents = getDocsFromPdf(pdfPath);

        for (int i = 0; i < documents.size(); i += 10) {
            List<Document> chunk = documents.subList(i, Math.min(i + 10, documents.size()));
            vectorStore.add(chunk);
            log.info("已导入 {} / {} 文档到 Milvus", Math.min(i + 10, documents.size()), documents.size());
        }

        log.info("PDF 导入完成，共 {} 个文档片段", documents.size());
    }

    List<Document> getDocsFromPdf(String pdfPath) {
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(
                new FileSystemResource(pdfPath),
                PdfDocumentReaderConfig.builder()
                        .withPageTopMargin(0)
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                                .withNumberOfTopTextLinesToDelete(0)
                                .build())
                        .withPagesPerDocument(1)
                        .build());

        return pdfReader.read();
    }
}
