package com.mailagent.mail;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.ComThread;
import com.jacob.com.Dispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Outlook mail channel via COM/JACOB. Cannot be exercised outside a real
 * Windows + Outlook environment — JACOB's native bridge aborts the JVM on
 * any other platform, which is why it's excluded from the test classpath
 * (see pom.xml). Deliberately kept thin: all branching, idempotency and
 * fallback logic lives in MailAgentService, already covered by tests
 * against MockMailChannel. Every COM call is wrapped so a failure surfaces
 * as MailChannelException (a RuntimeException) instead of crashing —
 * App's poll loop catches broadly and continues on the next cycle.
 *
 * <p>COM/STA threading constraint: every method here must be called from
 * the same thread that constructed this instance (the one that called
 * {@link ComThread#InitSTA()}). App's poll loop is single-threaded, so
 * this holds by construction — do not call this from an executor/pool.
 */
public class OutlookMailChannel implements MailChannel {

    private static final Logger log = LoggerFactory.getLogger(OutlookMailChannel.class);
    private static final int OL_FOLDER_INBOX = 6;

    private final String profile;
    private final String folderName;
    private final ActiveXComponent outlook;
    private final Dispatch namespace;

    public OutlookMailChannel(String profile, String folderName) {
        this.profile = profile;
        this.folderName = folderName;
        try {
            ComThread.InitSTA();
            this.outlook = new ActiveXComponent("Outlook.Application");
            this.namespace = Dispatch.call(outlook, "GetNamespace", "MAPI").toDispatch();
        } catch (RuntimeException e) {
            throw new MailChannelException("Failed to connect to Outlook via COM", e);
        }
    }

    @Override
    public List<Msg> fetchUnread() {
        try {
            Dispatch folder = resolveFolder();
            Dispatch items = Dispatch.get(folder, "Items").toDispatch();
            Dispatch unreadItems = Dispatch.call(items, "Restrict", "[Unread] = true").toDispatch();
            int count = Dispatch.get(unreadItems, "Count").getInt();

            List<Msg> result = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                try {
                    Dispatch item = Dispatch.call(unreadItems, "Item", i).toDispatch();
                    result.add(toMsg(item));
                } catch (RuntimeException e) {
                    log.warn("outlook_fetch_item_failed index={} reason={}", i, e.getClass().getSimpleName());
                }
            }
            return result;
        } catch (RuntimeException e) {
            throw new MailChannelException("Failed to fetch unread mail from Outlook", e);
        }
    }

    @Override
    public void reply(Msg msg, String body) {
        try {
            Dispatch item = Dispatch.call(namespace, "GetItemFromID", msg.getId()).toDispatch();
            Dispatch replyItem = Dispatch.call(item, "Reply").toDispatch();
            Dispatch.put(replyItem, "Body", body);
            Dispatch.call(replyItem, "Send");
        } catch (RuntimeException e) {
            throw new MailChannelException("Failed to send reply via Outlook for msgId=" + msg.getId(), e);
        }
    }

    private Dispatch resolveFolder() {
        Dispatch root = (profile == null || profile.trim().isEmpty())
                ? Dispatch.call(namespace, "GetDefaultFolder", OL_FOLDER_INBOX).toDispatch()
                : Dispatch.call(Dispatch.get(namespace, "Folders").toDispatch(), "Item", profile).toDispatch();

        if (folderName == null || folderName.trim().isEmpty() || "Inbox".equalsIgnoreCase(folderName)) {
            if (profile == null || profile.trim().isEmpty()) {
                return root;
            }
            root = Dispatch.call(Dispatch.get(root, "Folders").toDispatch(), "Item", "Inbox").toDispatch();
            return root;
        }
        return Dispatch.call(Dispatch.get(root, "Folders").toDispatch(), "Item", folderName).toDispatch();
    }

    private Msg toMsg(Dispatch item) {
        String entryId = Dispatch.get(item, "EntryID").getString();
        String from = safeGetString(item, "SenderEmailAddress");
        String subject = safeGetString(item, "Subject");
        String body = safeGetString(item, "Body");
        Instant receivedAt = safeGetInstant(item, "ReceivedTime");
        return new Msg(entryId, from, subject, body, receivedAt);
    }

    private String safeGetString(Dispatch item, String property) {
        try {
            return Dispatch.get(item, property).getString();
        } catch (RuntimeException e) {
            log.warn("outlook_property_read_failed property={} reason={}", property, e.getClass().getSimpleName());
            return "";
        }
    }

    private Instant safeGetInstant(Dispatch item, String property) {
        try {
            return Dispatch.get(item, property).getJavaDate().toInstant();
        } catch (RuntimeException e) {
            log.warn("outlook_property_read_failed property={} reason={}", property, e.getClass().getSimpleName());
            return Instant.now();
        }
    }
}
