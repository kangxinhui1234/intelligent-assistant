package com.kxh.aiagent.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * 显式 MyBatis-Plus 配置 — 当 Auto-config 没正确装配 SqlSessionFactory 时由此兜底。
 */
@Configuration
@MapperScan({"com.kxh.aiagent.mapper", "com.kxh.aiagent.ops.mapper"})
@ConditionalOnProperty(name = "spring.datasource.url")
public class MybatisPlusConfig {

    private static final Logger log = LoggerFactory.getLogger(MybatisPlusConfig.class);

    @Bean
    public MybatisSqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setTypeAliasesPackage("com.kxh.aiagent.entity,com.kxh.aiagent.ops.entity");
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] mappers1 = resolver.getResources("classpath*:mapper/*.xml");
            Resource[] mappers2 = resolver.getResources("classpath*:com/kxh/aiagent/mapper/xml/*.xml");
            Resource[] all = new Resource[mappers1.length + mappers2.length];
            System.arraycopy(mappers1, 0, all, 0, mappers1.length);
            System.arraycopy(mappers2, 0, all, mappers1.length, mappers2.length);
            factory.setMapperLocations(all);
            log.info("MyBatis-Plus 已加载 {} 个 Mapper XML", all.length);
        } catch (Exception e) {
            log.warn("Mapper XML 扫描失败(可忽略,若不用 XML): {}", e.getMessage());
        }
        return factory;
    }
}
