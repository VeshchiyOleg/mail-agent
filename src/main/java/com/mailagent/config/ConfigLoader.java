package com.mailagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    private ConfigLoader() {
    }

    public static AppConfig load(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new ConfigException("Config file not found: " + path);
        }

        AppConfig config;
        try (InputStream in = Files.newInputStream(path)) {
            config = MAPPER.readValue(in, AppConfig.class);
        } catch (IOException e) {
            throw new ConfigException("Failed to parse config file: " + path, e);
        }

        validate(config, path);
        return config;
    }

    private static void validate(AppConfig config, Path path) {
        if (config == null) {
            throw new ConfigException("Config file is empty: " + path);
        }
        if (config.getLlm() == null) {
            throw new ConfigException("Missing 'llm' section in config: " + path);
        }
        if (config.getAgent() == null) {
            throw new ConfigException("Missing 'agent' section in config: " + path);
        }
        if (config.getStore() == null) {
            throw new ConfigException("Missing 'store' section in config: " + path);
        }
        if (config.getMail() == null) {
            throw new ConfigException("Missing 'mail' section in config: " + path);
        }
    }
}
