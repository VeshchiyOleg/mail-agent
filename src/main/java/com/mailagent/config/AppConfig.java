package com.mailagent.config;

public class AppConfig {

    private LlmConfig llm;
    private AgentConfig agent;
    private StoreConfig store;
    private MailConfig mail;

    public LlmConfig getLlm() {
        return llm;
    }

    public void setLlm(LlmConfig llm) {
        this.llm = llm;
    }

    public AgentConfig getAgent() {
        return agent;
    }

    public void setAgent(AgentConfig agent) {
        this.agent = agent;
    }

    public StoreConfig getStore() {
        return store;
    }

    public void setStore(StoreConfig store) {
        this.store = store;
    }

    public MailConfig getMail() {
        return mail;
    }

    public void setMail(MailConfig mail) {
        this.mail = mail;
    }

    public static class LlmConfig {
        private String endpoint;
        private String model;
        private String apiKeyEnv;
        private long timeoutMs;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getApiKeyEnv() {
            return apiKeyEnv;
        }

        public void setApiKeyEnv(String apiKeyEnv) {
            this.apiKeyEnv = apiKeyEnv;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class AgentConfig {
        private int maxSteps;

        public int getMaxSteps() {
            return maxSteps;
        }

        public void setMaxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
        }
    }

    public static class StoreConfig {
        private String path;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static class MailConfig {
        private int pollSeconds;
        private String profile;
        private String folder;

        public int getPollSeconds() {
            return pollSeconds;
        }

        public void setPollSeconds(int pollSeconds) {
            this.pollSeconds = pollSeconds;
        }

        public String getProfile() {
            return profile;
        }

        public void setProfile(String profile) {
            this.profile = profile;
        }

        public String getFolder() {
            return folder;
        }

        public void setFolder(String folder) {
            this.folder = folder;
        }
    }
}
