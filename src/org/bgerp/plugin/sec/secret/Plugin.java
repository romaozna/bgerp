package org.bgerp.plugin.sec.secret;

import java.sql.Connection;

import ru.bgcrm.event.EventProcessor;
import ru.bgcrm.dao.expression.Expression.ContextInitEvent;

public class Plugin extends ru.bgcrm.plugin.Plugin {
    public static final String ID = "secret";

    public Plugin() {
        super(ID);
    }

    @Override
    public void init(Connection con) throws Exception {
        super.init(con);

        EventProcessor.subscribe((e, conSet) -> {
            e.getContext().put(ID, new ExpressionBean());
        }, ContextInitEvent.class);
    }
}
