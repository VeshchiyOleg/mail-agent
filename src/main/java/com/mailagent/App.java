package com.mailagent;

import com.mailagent.agent.AgentLoop;
import com.mailagent.audit.AuditLog;
import com.mailagent.config.AppConfig;
import com.mailagent.config.ConfigException;
import com.mailagent.config.ConfigLoader;
import com.mailagent.llm.HttpLlmClient;
import com.mailagent.llm.LlmClient;
import com.mailagent.mail.MailChannel;
import com.mailagent.mail.OutlookMailChannel;
import com.mailagent.service.MailAgentService;
import com.mailagent.store.ReminderStore;
import com.mailagent.store.SeenStore;
import com.mailagent.tools.AddReminderTool;
import com.mailagent.tools.CurrentDatetimeTool;
import com.mailagent.tools.FindItemsTool;
import com.mailagent.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;

public final class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private App() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (ConfigException e) {
            log.error("config_error message={}", e.getMessage());
            System.exit(1);
        } catch (LinkageError e) {
            // JACOB's native bridge (com.jacob.com.ComThread / JacobObject
            // static initializers) throws UnsatisfiedLinkError — a
            // LinkageError, not an Exception — on any platform without the
            // matching native DLL on java.library.path. Expected on every
            // machine except the Windows+Outlook grading stand.
            log.error("outlook_com_unavailable message=\"JACOB native library not available on this platform — "
                    + "OutlookMailChannel only works on Windows with Outlook installed and jacob-1.20.x64.dll on PATH\"");
            System.exit(1);
        } catch (RuntimeException | IOException e) {
            log.error("agent_fatal_startup_error type={} message={}", e.getClass().getSimpleName(), e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        Path configPath = args.length > 0 ? Paths.get(args[0]) : Paths.get("config.yaml");
        AppConfig config = ConfigLoader.load(configPath);

        String apiKey = System.getenv(config.getLlm().getApiKeyEnv());
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new ConfigException("Environment variable '" + config.getLlm().getApiKeyEnv() + "' is not set");
        }

        Clock clock = Clock.systemUTC();
        Path storeDir = Paths.get(config.getStore().getPath());
        Files.createDirectories(storeDir);

        SeenStore seenStore = new SeenStore(storeDir.resolve("seen.jsonl"));
        ReminderStore reminderStore = new ReminderStore(storeDir.resolve("reminders.json"));
        AuditLog auditLog = new AuditLog(storeDir.resolve("audit.jsonl"), clock);

        ToolRegistry toolRegistry = new ToolRegistry()
                .register(new CurrentDatetimeTool(clock))
                .register(new AddReminderTool(reminderStore, clock))
                .register(new FindItemsTool(reminderStore));

        LlmClient llmClient = new HttpLlmClient(
                config.getLlm().getEndpoint(),
                config.getLlm().getModel(),
                apiKey,
                config.getLlm().getTimeoutMs()
        );

        AgentLoop agentLoop = new AgentLoop(llmClient, toolRegistry, config.getAgent().getMaxSteps());

        MailChannel mailChannel = new OutlookMailChannel(config.getMail().getProfile(), config.getMail().getFolder());

        MailAgentService service = new MailAgentService(mailChannel, seenStore, agentLoop, auditLog);

        long pollMillis = config.getMail().getPollSeconds() * 1000L;
        log.info("agent_started pollSeconds={} maxSteps={}", config.getMail().getPollSeconds(), config.getAgent().getMaxSteps());

        while (true) {
            try {
                service.processUnread();
            } catch (RuntimeException e) {
                log.warn("agent_poll_cycle_failed type={} message={}", e.getClass().getSimpleName(), e.getMessage());
            }

            try {
                Thread.sleep(pollMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("agent_stopped reason=interrupted");
                return;
            }
        }
    }
}
