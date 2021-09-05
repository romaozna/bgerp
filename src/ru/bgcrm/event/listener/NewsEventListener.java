package ru.bgcrm.event.listener;

import ru.bgcrm.cache.UserNewsCache;
import ru.bgcrm.event.EventProcessor;
import ru.bgcrm.event.GetPoolTasksEvent;
import ru.bgcrm.event.client.NewsInfoEvent;
import ru.bgcrm.util.sql.ConnectionSet;

public class NewsEventListener {
    public NewsEventListener() {
        EventProcessor.subscribe((e, conSet) -> processEvent(conSet, e), GetPoolTasksEvent.class);
    }

    private void processEvent(ConnectionSet conSet, GetPoolTasksEvent e) throws Exception {
        NewsInfoEvent event = UserNewsCache.getUserEvent(conSet.getConnection(), e.getUser().getId());
        e.getForm().getResponse().addEvent(event);
    }
}
