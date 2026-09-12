package com.codeatlas.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 相关配置。
 *
 * <p>API Key 一律通过环境变量注入，禁止写进配置文件或代码。
 */
@ConfigurationProperties(prefix = "codeatlas.ai")
public class AiProperties {

    private final Chat chat = new Chat();

    private final Embedding embedding = new Embedding();

    public Chat getChat() {
        return chat;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public static class Chat {

        /** 对话模型提供方：deepseek / openai / ollama */
        private String provider = "deepseek";

        private String model = "deepseek-chat";

        private String apiKey = "";

        private String baseUrl = "https://api.deepseek.com";

        private int timeoutSeconds = 60;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Embedding {

        /** 向量化提供方：ollama / siliconflow / openai */
        private String provider = "ollama";

        private int dimension = 1024;

        private String model = "bge-m3";

        private String apiKey = "";

        private String baseUrl = "http://localhost:11434";

        private int timeoutSeconds = 60;

        private int batchSize = 16;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int dimension) {
            this.dimension = dimension;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
