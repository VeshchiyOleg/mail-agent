package com.mailagent.config;

import org.junit.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class ConfigLoaderTest {

    private Path resource(String name) throws URISyntaxException {
        return Paths.get(getClass().getClassLoader().getResource(name).toURI());
    }

    @Test
    public void loadsAllSectionsFromValidYaml() throws URISyntaxException {
        AppConfig config = ConfigLoader.load(resource("valid-config.yaml"));

        assertEquals("https://api.example.com/v1/chat", config.getLlm().getEndpoint());
        assertEquals("test-model", config.getLlm().getModel());
        assertEquals("MAIL_AGENT_LLM_API_KEY", config.getLlm().getApiKeyEnv());
        assertEquals(30000L, config.getLlm().getTimeoutMs());

        assertEquals(6, config.getAgent().getMaxSteps());

        assertEquals("./data", config.getStore().getPath());

        assertEquals(30, config.getMail().getPollSeconds());
        assertEquals("Outlook", config.getMail().getProfile());
        assertEquals("Inbox", config.getMail().getFolder());
    }

    @Test(expected = ConfigException.class)
    public void throwsConfigExceptionWhenFileMissing() {
        ConfigLoader.load(Paths.get("does/not/exist.yaml"));
    }

    @Test(expected = ConfigException.class)
    public void throwsConfigExceptionWhenSectionMissing() throws URISyntaxException {
        ConfigLoader.load(resource("incomplete-config.yaml"));
    }
}
