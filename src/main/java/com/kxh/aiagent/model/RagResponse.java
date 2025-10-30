package com.kxh.aiagent.model;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class RagResponse {
   String question;
    String  answer;
    String  contexts;
    String  response_time;
    String  search_type;
}
