package com.codeatlas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动类。
 *
 * <p>采用模块化单体架构，各业务模块位于 com.codeatlas 下的独立包中。
 */
@SpringBootApplication
public class CodeAtlasApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeAtlasApplication.class, args);
    }
}
