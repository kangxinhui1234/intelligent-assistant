package com.kxh.aiagent.rerank.entity;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PdfDocRecord {
    private String fileName;
    private int pageNum;
    private String content;
}
