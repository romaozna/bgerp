package ru.bgcrm.event.listener;

import org.bgerp.app.event.EventProcessor;
import org.bgerp.model.param.Parameter;

import ru.bgcrm.event.ParamChangingEvent;
import ru.bgcrm.util.RegexpCheckerConfig;
import ru.bgcrm.util.sql.ConnectionSet;

public class ParamValidatorSystemListener {
    public ParamValidatorSystemListener() {
        EventProcessor.subscribe((e, conSet) -> paramChanging(e, conSet), ParamChangingEvent.class);
    }

    private void paramChanging(ParamChangingEvent e, ConnectionSet conSet) throws Exception {
        Parameter param = e.getParameter();

        if (!Parameter.TYPE_TEXT.equals(param.getType())) {
            return;
        }

        RegexpCheckerConfig config = param.getConfigMap().getConfig(RegexpCheckerConfig.class);
        config.checkValue((String) e.getValue());
    }
}
