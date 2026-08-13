package com.mailagent.service;

import com.mailagent.agent.AgentLoop;
import com.mailagent.audit.AuditLog;
import com.mailagent.llm.ChatMessage;
import com.mailagent.mail.MailChannel;
import com.mailagent.mail.Msg;
import com.mailagent.store.SeenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives one poll cycle: unread messages -> (skip already-seen) -> run
 * through the agent tool-loop -> reply -> mark seen. Never logs message
 * bodies/subjects — only the stable message id, per the PII rule.
 */
public class MailAgentService {

    private static final Logger log = LoggerFactory.getLogger(MailAgentService.class);

    public static final String FALLBACK_REPLY =
            "Извините, не получилось обработать ваш запрос автоматически. Мы разберёмся и ответим отдельно.";

    private static final String SYSTEM_PROMPT =
            "Ты — почтовый ассистент. Отвечай кратко и по делу на русском языке. "
                    + "Используй доступные инструменты, когда это нужно для ответа на запрос из письма. "
                    + "Если инструмент вернул ошибку (JSON с полем error), никогда не цитируй её дословно "
                    + "пользователю — вежливо сообщи, что не получилось выполнить действие, без технических деталей.";

    private final MailChannel mailChannel;
    private final SeenStore seenStore;
    private final AgentLoop agentLoop;
    private final AuditLog auditLog;

    public MailAgentService(MailChannel mailChannel, SeenStore seenStore, AgentLoop agentLoop, AuditLog auditLog) {
        this.mailChannel = mailChannel;
        this.seenStore = seenStore;
        this.agentLoop = agentLoop;
        this.auditLog = auditLog;
    }

    public void processUnread() {
        for (Msg msg : mailChannel.fetchUnread()) {
            if (seenStore.isSeen(msg.getId())) {
                continue;
            }
            processOne(msg);
        }
    }

    private void processOne(Msg msg) {
        auditLog.append("agent_mail_seen", details("msgId", msg.getId()));
        log.info("agent_mail_seen msgId={}", msg.getId());

        try {
            List<ChatMessage> initial = Arrays.asList(
                    ChatMessage.system(SYSTEM_PROMPT),
                    ChatMessage.user(msg.getBody())
            );

            String reply = agentLoop.run(initial, toolName -> {
                auditLog.append("agent_tool_call", details("msgId", msg.getId(), "tool", toolName));
                log.info("agent_tool_call msgId={} tool={}", msg.getId(), toolName);
            });

            mailChannel.reply(msg, reply);
            auditLog.append("agent_mail_replied", details("msgId", msg.getId()));
            log.info("agent_mail_replied msgId={}", msg.getId());
        } catch (RuntimeException e) {
            log.warn("agent_mail_failed msgId={} reason={}", msg.getId(), e.getClass().getSimpleName());
            try {
                mailChannel.reply(msg, FALLBACK_REPLY);
                auditLog.append("agent_mail_fallback", details("msgId", msg.getId(), "reason", e.getClass().getSimpleName()));
            } catch (RuntimeException replyFailure) {
                log.warn("agent_mail_fallback_reply_failed msgId={} reason={}", msg.getId(), replyFailure.getClass().getSimpleName());
                auditLog.append("agent_mail_fallback_failed",
                        details("msgId", msg.getId(), "reason", replyFailure.getClass().getSimpleName()));
            }
        } finally {
            seenStore.markSeen(msg.getId());
        }
    }

    private static Map<String, String> details(String k1, String v1) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(k1, v1);
        return map;
    }

    private static Map<String, String> details(String k1, String v1, String k2, String v2) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }
}
