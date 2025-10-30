package com.kxh.aiagent.model;


import lombok.Data;

@Data
public class RagRequest {
    String  question;
    String  search_type;
    String top_k;
}