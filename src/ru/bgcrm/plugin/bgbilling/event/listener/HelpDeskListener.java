package ru.bgcrm.plugin.bgbilling.event.listener;

import java.math.BigDecimal;

import ru.bgcrm.model.message.Message;
import ru.bgcrm.model.process.Process;
import ru.bgcrm.model.process.ProcessExecutor;

import java.sql.Connection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bgerp.model.Pageable;

import ru.bgcrm.cache.UserCache;
import ru.bgcrm.dao.message.MessageDAO;
import ru.bgcrm.dao.message.MessageType;
import ru.bgcrm.dao.message.config.MessageTypeConfig;
import ru.bgcrm.dao.process.ProcessLinkDAO;
import ru.bgcrm.event.EventProcessor;
import ru.bgcrm.event.ParamChangedEvent;
import ru.bgcrm.event.client.ProcessChangedEvent;
import ru.bgcrm.event.listener.EventListener;
import ru.bgcrm.event.process.ProcessChangingEvent;
import ru.bgcrm.model.BGException;
import ru.bgcrm.model.BGMessageException;
import ru.bgcrm.model.CommonObjectLink;
import ru.bgcrm.model.Pair;
import ru.bgcrm.model.user.User;
import ru.bgcrm.plugin.bgbilling.dao.MessageTypeHelpDesk;
import ru.bgcrm.plugin.bgbilling.proto.dao.HelpDeskDAO;
import ru.bgcrm.plugin.bgbilling.proto.model.helpdesk.HdTopic;
import ru.bgcrm.util.Setup;
import ru.bgcrm.util.Utils;
import ru.bgcrm.util.sql.ConnectionSet;

/*
 * Слушатель для интеграции с HelpDesk ами.
 */
public class HelpDeskListener {
    // private static final Logger log = Logger.getLogger( HelpDeskListener.class );

    public HelpDeskListener() {
        EventProcessor.subscribe(new EventListener<ProcessChangingEvent>() {
            @Override
            public void notify(ProcessChangingEvent e, ConnectionSet connectionSet) throws Exception {
                processChanged(e, connectionSet);
            }
        }, ProcessChangingEvent.class);

        EventProcessor.subscribe(new EventListener<ParamChangedEvent>() {
            @Override
            public void notify(ParamChangedEvent e, ConnectionSet connectionSet) throws BGException {
                paramChanged(e, connectionSet);
            }
        }, ParamChangedEvent.class);
    }

    @SuppressWarnings("unchecked")
    private void paramChanged(ParamChangedEvent e, ConnectionSet conSet) throws BGException {
        // TODO: Предварительно можно сделать отсев по кодам параметров, которые могут
        // быть связаны с хелпдеском.
        if (!Process.OBJECT_TYPE.equals(e.getParameter().getObject())) {
            return;
        }

        int paramId = e.getParameter().getId();

        Pair<MessageTypeHelpDesk, Integer> pair = getTypeAndTopic(conSet.getConnection(), e.getObjectId());
        if (pair == null) {
            for (MessageTypeHelpDesk mt : getMessageTypes()) {
                if (paramId == mt.getStatusParamId() || paramId == mt.getCostParamId()) {
                    throw new BGMessageException(
                            "Данные параметры разрешено править только для процессов, связанных с HelpDesk темами.");
                }
            }
            return;
        }

        Integer topicId = pair.getSecond();

        MessageTypeHelpDesk mt = pair.getFirst();
        HelpDeskDAO hdDao = new HelpDeskDAO(e.getUser(), mt.getDbInfo());

        Pageable<HdTopic> topicSearch = new Pageable<HdTopic>();
        hdDao.seachTopicList(topicSearch, null, null, false, topicId);

        HdTopic topic = Utils.getFirst(topicSearch.getList());
        if (topic == null) {
            throw new BGException("Не найден топик HelpDesk с кодом: " + topicId);
        }

        String mode = hdDao.getContractMode(topic.getContractId());

        // статус (категория)
        if (paramId == mt.getStatusParamId()) {
            hdDao.setTopicStatus(topic.getContractId(), topicId, Utils.getFirst((Set<Integer>) e.getValue()));
        }
        // автозакрытие
        else if (paramId == mt.getAutoCloseParamId()) {
            hdDao.setTopicAutoClose(topic.getContractId(), topicId, ((Set<Integer>) e.getValue()).size() > 0);
        }
        // стоимость
        else if (paramId == mt.getCostParamId()) {
            if (HelpDeskDAO.MODE_PACKAGE.equals(mode)) {
                throw new BGMessageException("Режим HelpDesk договора пакетный.");
            }
            hdDao.setTopicCost(topic.getContractId(), topicId,
                    Utils.parseBigDecimal((String) e.getValue(), BigDecimal.ZERO));
        }
        // вхождение в пакет
        else if (paramId == mt.getPackageParamId()) {
            if (HelpDeskDAO.MODE_ON.equals(mode)) {
                throw new BGMessageException("Режим HelpDesk договора обычный.");
            }
            hdDao.setTopicPackageState(topic.getContractId(), topicId, ((Set<Integer>) e.getValue()).size() > 0);
        }
    }

    private void processChanged(ProcessChangingEvent e, ConnectionSet conSet) throws Exception {
        // при закрытии/открытии темы - закрытие в HelpDesk
        if (e.isOpening() || e.isClosing()) {
            Pair<MessageTypeHelpDesk, Integer> pair = getTypeAndTopic(conSet.getConnection(), e.getProcess().getId());
            if (pair != null) {
                new HelpDeskDAO(e.getUser(), pair.getFirst().getDbInfo()).setTopicState(pair.getSecond(),
                        e.isClosing());
            }
        }
        // изменение исполнителей - установка в HelpDesk
        else if (e.isExecutors()) {
            Pair<MessageTypeHelpDesk, Integer> pair = getTypeAndTopic(conSet.getConnection(), e.getProcess().getId());
            if (pair != null) {
                Set<Integer> executors = ProcessExecutor.getExecutorsWithRole(e.getProcessExecutors(), 0);
                if (executors.size() > 1) {
                    throw new BGMessageException(
                            "Для процесса связанного с HelpDesk запрещена установка более одного исполнителя с ролью 0.");
                }

                User user = null;

                Integer userId = Utils.getFirst(executors);
                if (userId != null) {
                    user = UserCache.getUser(userId);
                }

                HelpDeskDAO hdDao = new HelpDeskDAO(e.getUser(), pair.getFirst().getDbInfo());
                if (user == null) {
                    hdDao.setTopicExecutor(pair.getSecond(), 0);
                } else if (user.getId() == e.getUser().getId()) {
                    hdDao.setTopicExecutorMe(pair.getSecond());
                } else {
                    int billingUserId = pair.getFirst().getDbInfo().getBillingUserId(user);
                    if (billingUserId <= 0) {
                        throw new BGException(
                                "Исполнителю " + user.getTitle() + " не сопоставлен пользователь биллинга.");
                    }
                    hdDao.setTopicExecutor(pair.getSecond(), billingUserId);
                }
            }
        }

        if (e.isStatus()) {
            // TODO: Также можно предварительно фильтровать по статусам.
            Pair<MessageTypeHelpDesk, Integer> pair = getTypeAndTopic(conSet.getConnection(), e.getProcess().getId());
            if (pair != null) {
                MessageTypeHelpDesk mt = pair.getFirst();
                if (mt.getMarkMessagesReadStatusIds().contains(e.getStatusChange().getStatusId())) {
                    MessageDAO messageDao = new MessageDAO(conSet.getConnection());

                    Pageable<Message> searchResult = new Pageable<Message>();
                    messageDao.searchMessageList(searchResult, e.getProcess().getId(), mt.getId(),
                            Message.DIRECTION_INCOMING, null, null, null, null, null);

                    HelpDeskDAO hdDao = new HelpDeskDAO(e.getUser(), mt.getDbInfo());
                    for (Message msg : searchResult.getList()) {
                        if (msg.getToTime() != null) {
                            continue;
                        }

                        hdDao.markMessageRead(Utils.parseInt(msg.getSystemId()));

                        msg.setToTime(new Date());
                        msg.setUserId(e.getUser().getId());

                        messageDao.updateMessageProcess(msg);
                    }
                }
                e.getForm().getResponse().addEvent(new ProcessChangedEvent(e.getProcess().getId()));
            }
        }
    }

    private Pair<MessageTypeHelpDesk, Integer> getTypeAndTopic(Connection con, int processId) throws BGException {
        ProcessLinkDAO linkDao = new ProcessLinkDAO(con);

        List<CommonObjectLink> linkList = linkDao.getObjectLinksWithType(processId, "bgbilling-helpdesk%");
        if (linkList.size() > 0) {
            Set<MessageTypeHelpDesk> messageTypes = getMessageTypes();
            for (MessageTypeHelpDesk mt : messageTypes) {
                for (CommonObjectLink link : linkList) {
                    if (link.getLinkedObjectType().equals(mt.getObjectType())) {
                        return new Pair<MessageTypeHelpDesk, Integer>(mt, link.getLinkedObjectId());
                    }
                }
            }
        }

        return null;
    }

    private Set<MessageTypeHelpDesk> getMessageTypes() {
        Set<MessageTypeHelpDesk> result = new HashSet<MessageTypeHelpDesk>();

        MessageTypeConfig config = Setup.getSetup().getConfig(MessageTypeConfig.class);
        for (MessageType type : config.getTypeMap().values()) {
            if (!(type instanceof MessageTypeHelpDesk)) {
                continue;
            }
            result.add((MessageTypeHelpDesk) type);
        }

        return result;
    }
}
