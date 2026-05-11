package org.ymz.app;

import com.mybatisflex.codegen.Generator;
import com.mybatisflex.codegen.config.GlobalConfig;
import com.mybatisflex.codegen.dialect.IDialect;
import com.zaxxer.hikari.HikariDataSource;

/**
 * MyBatis Flex 代码生成器
 *
 * @author ymz
 */
public class MyBatisFlexCodeGenerator {

    private static final String URL = "jdbc:mysql://localhost:3306/zhida";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "123456";
    private static final String TABLE_NAME = "llm_log";

    public static void main(String[] args) {
        // 配置 Hikari 数据源
        HikariDataSource hikariDataSource = new HikariDataSource();
        hikariDataSource.setJdbcUrl(URL);
        hikariDataSource.setUsername(USERNAME);
        hikariDataSource.setPassword(PASSWORD);

        // 代码生成器配置
        GlobalConfig globalConfig = new GlobalConfig();
        // 注释配置
        globalConfig.getJavadocConfig()
                .setAuthor("ymz")
                .setSince("");
        // 包配置
        String codegenDir = System.getProperty("user.dir") + "/mybatis-flex-code-gen";
        globalConfig.getPackageConfig()
                .setBasePackage("org.ymz.app")  // 生成的代码包名
                .setEntityPackage("org.ymz.app.model.entity")
                .setSourceDir(codegenDir + "/src/main/java")  // 明确指定输出目录
                .setMapperXmlPath(codegenDir + "/src/main/resources/mapper");  // XML 输出目录
        // 策略配置
        globalConfig.getStrategyConfig()
                // 生成指定表名的代码
                .setGenerateTable(TABLE_NAME)
                // 设置逻辑删除的字段名称
                .setLogicDeleteColumn("del_flag");
        // entity 配置
        globalConfig.enableEntity()
                .setWithLombok(true)
                .setJdkVersion(21)
                .setOverwriteEnable(true);
        // Mapper 配置
        globalConfig.enableMapper()
                .setOverwriteEnable(true);
        // Mapper XML 配置
        globalConfig.enableMapperXml()
                .setOverwriteEnable(true);
        // Service 配置
        globalConfig.enableService()
                .setOverwriteEnable(true);
        // Service Impl 配置
        globalConfig.enableServiceImpl()
                .setOverwriteEnable(true);

        Generator generator = new Generator(hikariDataSource, globalConfig, IDialect.MYSQL);
        generator.generate();
    }
}
