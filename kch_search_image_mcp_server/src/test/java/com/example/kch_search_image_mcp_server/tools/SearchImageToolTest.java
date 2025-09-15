package com.example.kch_search_image_mcp_server.tools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SearchImageToolTest {

    @Autowired
    SearchImageTool tool;


    @Test
    public void searchImage(){
        String imaage = "树木";
     String images =    tool.searchImage(imaage);
    }
}
