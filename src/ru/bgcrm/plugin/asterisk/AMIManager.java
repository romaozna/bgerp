package ru.bgcrm.plugin.asterisk;

import java.util.ArrayList;
import java.util.List;

import org.bgerp.util.Log;

import ru.bgcrm.dao.message.MessageType;
import ru.bgcrm.dao.message.MessageTypeCall;
import ru.bgcrm.dao.message.config.MessageTypeConfig;
import ru.bgcrm.dynamic.DynamicClassManager;
import ru.bgcrm.event.EventProcessor;
import ru.bgcrm.event.SetupChangedEvent;
import ru.bgcrm.util.ParameterMap;
import ru.bgcrm.util.Setup;

/**
 * Asterisk event listener.
 *
 * @author Shamil Vakhitov
 */
public class AMIManager {
    private static final Log log = Log.getLog();

    private static AMIManager instance;

    static final int CONNECT_TIMEOUT = 5 * 60 * 1000;
    static final int RECONNECT_TIMEOUT = 60 * 1000;

    private List<AmiEventListener> threadList = new ArrayList<AmiEventListener>();

    public AMIManager() {
        if (instance != null) {
            log.warn("Attempt of creation a second singleton instance");
            return;
        }

        instance = this;

        init();
        EventProcessor.subscribe((e, conSet) -> init(), SetupChangedEvent.class);
    }

    // http://www.asterisk-java.org/development/tutorial.html
    private void init() {
        log.info("Reinit..");

        try {
            for (AmiEventListener listener : threadList) {
                listener.logoff();
            }
            threadList = new ArrayList<AmiEventListener>();

            Setup setup = Setup.getSetup();

            MessageTypeConfig mtConfig = setup.getConfig(MessageTypeConfig.class);

            for (ParameterMap config : setup.subIndexed("asterisk:amiManager.").values()) {
                int messageTypeId = config.getInt("messageTypeId", 0);

                MessageType messageType = mtConfig.getTypeMap().get(messageTypeId);
                if (messageType == null || !(messageType instanceof MessageTypeCall)) {
                    log.error("Incorrect messageTypeId: " + messageTypeId);
                    continue;
                }

                Class<?> listenerClass = DynamicClassManager.getClass(config.get("listenerClass", "ru.bgcrm.plugin.asterisk.AmiEventListener"));
                try {
                    AmiEventListener listener = (AmiEventListener) listenerClass.getConstructor(MessageTypeCall.class, ParameterMap.class)
                            .newInstance((MessageTypeCall) messageType, config);
                    threadList.add(listener);
                } catch (Exception e) {
                    log.error(e);
                }
            }
        } catch (Exception e) {
            log.error(e);
        }
    }
}