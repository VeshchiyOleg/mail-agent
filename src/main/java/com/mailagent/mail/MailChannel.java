package com.mailagent.mail;

import java.util.List;

public interface MailChannel {

    List<Msg> fetchUnread();

    void reply(Msg msg, String body);
}
