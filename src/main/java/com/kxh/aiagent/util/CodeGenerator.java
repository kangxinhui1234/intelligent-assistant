package com.kxh.aiagent.util;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.nio.file.Paths;

public class CodeGenerator {


    public static void main(String[] args) {
        FastAutoGenerator.create("jdbc:mysql://192.168.1.5:3306/finance", "root", "123456")
                .globalConfig(builder -> builder
                        .author("kxh")
                        .outputDir(Paths.get(System.getProperty("user.dir")) + "/src/main/java/codeDemo")
                        .commentDate("yyyy-MM-dd")
                )
                .packageConfig(builder -> builder
                        .parent("")
                        .entity("entity")
                        .mapper("mapper")
                        .service("service")
                        .serviceImpl("service.impl")
                        .xml("mapper.xml")
                )
                .strategyConfig(builder -> builder
                        .entityBuilder()
                        .enableLombok()
                )
               // .templateEngine(new FreemarkerTemplateEngine())
                .execute();
    }

}
