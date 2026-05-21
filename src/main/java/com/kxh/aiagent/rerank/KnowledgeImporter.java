package com.kxh.aiagent.rerank;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import com.kxh.aiagent.rerank.entity.PdfDocRecord;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RequestOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;

@Component
@Slf4j
@Profile("elasticsearch")
public class KnowledgeImporter {

    @Autowired
    private  ElasticsearchClient esClient;
    @Resource(name = "vectorStore")
    VectorStore vectorStore;

    public KnowledgeImporter() {
    }

    public void importFromDirectory(String dirPath) throws IOException {

        List<Document> documents =  getDocsFromPdf();
         for(int i = 0;i<documents.size();i+=10){
             List<Document> chunk = documents.subList(i,Math.min(i + 10, documents.size()));
             vectorStore.add(chunk);
         }


        for (Document doc : documents) {
            String id = UUID.randomUUID().toString();
            String content = doc.getText();
            String fileName = doc.getMetadata().get("file_name").toString();
            String pageNum = doc.getMetadata().get("page_number").toString();
            // === 2.2 插入 Elasticsearch ===
            IndexResponse response = esClient.index(i -> i
                            .index("pdf_docs")
                            .id(id)
                            .document(new PdfDocRecord(fileName, Integer.parseInt(pageNum), content)));

            log.info("✅ 已入库: %s [page %s]%n", fileName, pageNum);
        }

    }

    /**
     * springai提供的文档
     * @return
     */
    List<Document> getDocsFromPdf() {
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(new FileSystemResource(System.getProperty("user.dir") + "/tmp/rag/000001_2022_ZGPA_2022_YEAR_2023-03-08.pdf"),
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
