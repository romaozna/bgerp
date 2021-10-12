package ru.bgcrm.struts.action;

import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

import ru.bgcrm.cache.ProcessTypeCache;
import ru.bgcrm.cache.UserCache;
import ru.bgcrm.dao.CommonDAO;
import ru.bgcrm.dao.message.MessageDAO;
import ru.bgcrm.dao.message.MessageSearchDAO;
import ru.bgcrm.dao.message.MessageType;
import ru.bgcrm.dao.message.MessageTypeSearch;
import ru.bgcrm.dao.message.config.MessageTypeConfig;
import ru.bgcrm.dao.process.ProcessDAO;
import ru.bgcrm.dao.process.ProcessLinkDAO;
import ru.bgcrm.dao.user.UserDAO;
import ru.bgcrm.event.EventProcessor;
import ru.bgcrm.event.MessageRemovedEvent;
import ru.bgcrm.event.link.LinkAddedEvent;
import ru.bgcrm.model.BGException;
import ru.bgcrm.model.BGMessageException;
import ru.bgcrm.model.CommonObjectLink;
import ru.bgcrm.model.SearchResult;
import ru.bgcrm.model.message.TagConfig;
import ru.bgcrm.model.message.Message;
import ru.bgcrm.model.process.Process;
import ru.bgcrm.model.process.ProcessLinkProcess;
import ru.bgcrm.servlet.ActionServlet.Action;
import ru.bgcrm.struts.form.DynActionForm;
import ru.bgcrm.util.Preferences;
import ru.bgcrm.util.Utils;
import ru.bgcrm.util.sql.ConnectionSet;

@Action(path = "/user/message")
public class MessageAction extends BaseAction {
    public static final String UNPROCESSED_MESSAGES_PERSONAL_KEY = "unprocessedMessages";

    private static final String PATH_JSP = PATH_JSP_USER + "/message";

    @Override
    protected ActionForward unspecified(ActionMapping mapping, DynActionForm form, ConnectionSet conSet)
            throws Exception {
        return message(form, conSet);
    }

    public ActionForward message(DynActionForm form, ConnectionSet conSet) throws Exception {
        int typeId = form.getParamInt("typeId");
        String messageId = form.getParam("messageId");

        Message message = null;

        // processed message
        if (form.getId() > 0) {
            var messageDao = new MessageDAO(conSet.getConnection());

            message = messageDao.getMessageById(form.getId());
        }
        // new message, not loaded
        else if (typeId > 0 && Utils.notBlankString(messageId)) {
            var type = getType(typeId);

            message = type.newMessageGet(conSet, messageId);

            linksSearch(form, conSet);

            // TODO: Only types with group of the current executor.
            form.setRequestAttribute("typeTreeRoot", ProcessTypeCache.getTypeTreeRoot());
        }

        form.setResponseData("message", message);

        return html(conSet, form, PATH_JSP + "/message.jsp");
    }

    public ActionForward messageUpdateProcess(DynActionForm form, Connection con) throws Exception {
        MessageTypeConfig config = setup.getConfig(MessageTypeConfig.class);

        MessageDAO messageDao = new MessageDAO(con);

        Message message = null;
        if (form.getId() > 0)
            message = new MessageDAO(con).getMessageById(form.getId());
        else {
            int typeId = form.getParamInt("messageTypeId");
            String messageId = form.getParam("messageId");

            MessageType type = config.getTypeMap().get(typeId);

            message = type.newMessageLoad(con, messageId);
        }

        if (message == null)
            throw new BGMessageException("Сообщение не найдено.");

        message.setProcessed(true);

        form.setResponseData("id", message.getId());

        int processId = form.getParamInt("processId", -1);
        if (processId >= 0) {
            message.setProcessId(processId);

            if (processId > 0) {
                int contactSaveMode = form.getParamInt("contactSaveMode");

                MessageType type = config.getTypeMap().get(message.getTypeId());

                Process process = new ProcessDAO(con).getProcess(processId);
                if (process == null)
                    throw new BGException("Process not found.");

                if (form.getParamBoolean("notification", false))
                    messageDao.updateMessage(type.messageLinkedToProcess(message));
                else if (contactSaveMode > 0)
                    type.getContactSaver().saveContact(form, con, message, process, contactSaveMode);
            }
        }

        messageDao.updateMessageProcess(message);

        return json(con, form);
    }

    public ActionForward messageUpdateTags(DynActionForm form, ConnectionSet conSet) throws Exception {
        Connection con = conSet.getConnection();

        new MessageDAO(con).updateMessageTags(form.getId(), form.getSelectedValues("tagId"));

        return json(conSet, form);
    }

    public ActionForward messageUpdateProcessToCopy(DynActionForm form, Connection con) throws Exception {
        MessageDAO messageDao = new MessageDAO(con);
        ProcessDAO processDao = new ProcessDAO(con);
        ProcessLinkDAO linkDao = new ProcessLinkDAO(con);

        Message message = messageDao.getMessageById(form.getId());
        if (message == null)
            throw new BGMessageException("Сообщение не найдено.");

        Process process = processDao.getProcess(message.getProcessId());
        if (process == null)
            throw new BGMessageException("Процесс не найден.");

        String linkType = form.getParam("linkType");

        Process newProcess = new Process();
        newProcess.setTypeId(process.getTypeId());
        newProcess.setStatusId(process.getStatusId());
        newProcess.setStatusUserId(form.getUserId());
        newProcess.setDescription(message.getSubject());
        newProcess.setCreateUserId(form.getUserId());
        newProcess.setCreateUserId(form.getUserId());

        processDao.updateProcess(newProcess);

        processDao.updateProcessGroups(process.getGroups(), newProcess.getId());
        processDao.updateProcessExecutors(process.getExecutors(), newProcess.getId());

        if (StringUtils.isBlank(linkType))
            linkDao.copyLinks(process.getId(), newProcess.getId(), null);
        else {
            linkDao.addLink(new ProcessLinkProcess(process.getId(), linkType, newProcess.getId()));
            if (Process.LINK_TYPE_DEPEND.equals(linkType)) {
                for (var linkedProcess : linkDao.getLinkedProcessList(process.getId(), Process.LINK_TYPE_MADE, false, null)) {
                    linkDao.addLink(new ProcessLinkProcess.Made(linkedProcess.getId(), newProcess.getId()));
                }
            }
        }

        message.setProcessId(newProcess.getId());
        message.setText(l.l("Перенесено из процесса #{}", process.getId()) + "\n\n" + message.getText());

        messageDao.updateMessage(message);

        form.setResponseData("process", newProcess);

        return json(con, form);
    }

    public ActionForward messageDelete(DynActionForm form, ConnectionSet conSet)
            throws Exception {
        MessageTypeConfig config = setup.getConfig(MessageTypeConfig.class);

        Map<MessageType, List<String>> typeSystemIds = new HashMap<>(10);
        for (String pair : form.getParamArray("typeId-systemId")) {
            int typeId = Utils.parseInt(StringUtils.substringBefore(pair, "-"));

            MessageType type = config.getTypeMap().get(typeId);
            if (type == null)
                throw new BGException("Не найден тип сообщения.");

            List<String> systemIds = typeSystemIds.get(type);
            if (systemIds == null)
                typeSystemIds.put(type, systemIds = new ArrayList<>(10));

            systemIds.add(StringUtils.substringAfter(pair, "-"));
        }

        // если нет разрешения на удаления чужих, проверим, чтобы все сообщения принадлежали ему.
        if (UserCache.getPerm(form.getUserId(), "ru.bgcrm.struts.action.MessageAction:deleteEditOtherUsersNotes") == null) {
            MessageDAO messageDao = new MessageDAO(conSet.getConnection());
            for (Integer sysId : typeSystemIds.values().stream().flatMap(List::stream).map(Utils::parseInt).collect(Collectors.toSet())) {
                Message message = messageDao.getMessageById(sysId);
                if (message != null && message.getUserId() != form.getUserId()) {
                    throw new BGMessageException("Удаление чужих сообщений запрещено!");
                }
            }
        }

        for (Map.Entry<MessageType, List<String>> me : typeSystemIds.entrySet())
            me.getKey().messageDelete(conSet, me.getValue().toArray(new String[me.getValue().size()]));

        EventProcessor.processEvent(new MessageRemovedEvent(form, form.getId()), conSet);

        return html(conSet, form, PATH_JSP + "/message.jsp");
    }

    public ActionForward messageList(DynActionForm form, final ConnectionSet conSet)
            throws Exception {
        restoreRequestParams(conSet.getConnection(), form, true, true, "order", "typeId");

        boolean processed = form.getParamBoolean("processed", false);
        final boolean reverseOrder = form.getParamBoolean("order", true);

        Set<Integer> allowedTypeIds = Utils.toIntegerSet(form.getPermission().get("allowedTypeIds", ""));

        var config = setup.getConfig(MessageTypeConfig.class);
        form.setRequestAttribute("config", config);
        SortedMap<Integer, MessageType> typeMap =  Maps.filterKeys(
                config.getTypeMap(),
                k -> allowedTypeIds.isEmpty() || allowedTypeIds.contains(k));

        int typeId = form.getParamInt("typeId", -1);

        if (processed) {
            new MessageSearchDAO(conSet.getConnection())
                .withTypeId(typeId).withDirection(Message.DIRECTION_INCOMING).withProcessed(true)
                .withAttach(form.getParamBoolean("attach", null))
                .withDateFrom(form.getParamDate("dateFrom", null), form.getParamDate("dateTo", null))
                .withFrom(CommonDAO.getLikePatternSub(form.getParam("from")))
                .withFromTimeReverseOrder(reverseOrder)
                .search(new SearchResult<Message>(form));
        } else {
            // when external system isn't available, an empty table of messages should be however shown
            try {
                List<Message> result = new ArrayList<>(typeMap.get(typeId).newMessageList(conSet));

                Collections.sort(result, (Message o1, Message o2) -> {
                    if (reverseOrder) {
                        Message tmp = o1;
                        o1 = o2;
                        o2 = tmp;
                    }
                    return o1.getFromTime() == null ? -1 : o1.getFromTime().compareTo(o2.getFromTime());
                });

                form.getResponse().setData("list", result);

                Preferences prefs = new Preferences();
                prefs.put(UNPROCESSED_MESSAGES_PERSONAL_KEY, String.valueOf(config.getUnprocessedMessagesCount()));
                new UserDAO(conSet.getConnection()).updatePersonalization(form.getUser(), prefs);
            } catch (Exception e) {
                log.error(e);
            }
        }

        form.getHttpRequest().setAttribute("typeMap", typeMap);

        return html(conSet, form, PATH_JSP + "/list.jsp");
    }

    public ActionForward newMessageLoad(DynActionForm form, ConnectionSet conSet)
            throws Exception {
        MessageTypeConfig config = setup.getConfig(MessageTypeConfig.class);

        int typeId = form.getParamInt("typeId");
        String messageId = form.getParam("messageId");

        MessageType type = config.getTypeMap().get(typeId);
        if (type == null)
            throw new BGException("Message type not found:" + typeId);

        type.newMessageLoad(conSet.getConnection(), messageId);

        return json(conSet, form);
    }

    private void linksSearch(DynActionForm form, ConnectionSet conSet) throws Exception {
        var type = getType(form.getParamInt("typeId"));
        var message = type.newMessageGet(conSet, form.getParam("messageId"));

        // automatically search by first search mode
        // TODO: Use all search modes, and make them given by message type.
        int searchId = form.getParamInt("searchId", 1);
        if (CollectionUtils.isNotEmpty(type.getSearchMap().values())) {
            MessageTypeSearch search = type.getSearchMap().get(searchId);

            Set<CommonObjectLink> searchedList = new LinkedHashSet<CommonObjectLink>();
            search.search(form, conSet, message, searchedList);
            form.setResponseData("searchedList", searchedList);
        }

        // return html(conSet, form, PATH_JSP + "/message_search_result.jsp");
    }

    public ActionForward processCreate(DynActionForm form, ConnectionSet conSet) throws Exception {
        var con = conSet.getConnection();

        var process = ProcessAction.processCreate(form, con);

        var linkDao = new ProcessLinkDAO(con, form.getUser());
        for (String link : form.getSelectedValuesListStr("link")) {
            var tokens = link.split("\\*");
            if (tokens.length != 3) {
                log.warn("Incorrect link: '{}'", link);
                continue;
            }

            var olink = new CommonObjectLink(Process.OBJECT_TYPE, process.getId(), tokens[0], Utils.parseInt(tokens[1]),
                    tokens[2]);
            linkDao.addLink(olink);

            EventProcessor.processEvent(new LinkAddedEvent(form, olink), conSet);
        }

        form.setParam("processId", String.valueOf(process.getId()));
        messageUpdateProcess(form, con);

        return json(con, form);
    }

    private MessageType getType(int typeId) throws BGException {
        var config = setup.getConfig(MessageTypeConfig.class);

        var type = config.getTypeMap().get(typeId);
        if (type == null)
            throw new BGException("Message type not found: " + typeId);

        return type;
    }

    public ActionForward processMessageList(DynActionForm form, ConnectionSet conSet)
            throws Exception {
        int tagId = form.getParamInt("tagId");
        int processId = form.getParamInt("processId");
        Set<Integer> processIds = new TreeSet<>(Collections.singleton(processId));

        Set<String> linkProcess = Utils.toSet(form.getParam("linkProcess"));
        if (!linkProcess.isEmpty()) {
            List<Integer> linkProcessIds = new ProcessLinkDAO(conSet.getSlaveConnection())
                .getObjectLinksWithType(processId, "process%").stream()
                .filter(l -> linkProcess.contains(l.getLinkedObjectType()))
                .map(CommonObjectLink::getLinkedObjectId).collect(Collectors.toList());
            processIds.addAll(linkProcessIds);
        }

        log.debug("processIds: %s", processIds);

        Set<Integer> allowedTypeIds = Utils.toIntegerSet(form.getPermission().get("allowedTypeIds", ""));

        new MessageSearchDAO(conSet.getConnection())
            .withProcessIds(processIds)
            .withTypeIds(allowedTypeIds)
            .withAttach(tagId == TagConfig.Tag.TAG_ATTACH_ID ? true : null)
            .withDateFrom(form.getParamDate("dateFrom"), form.getParamDate("dateTo"))
            .withFromTimeReverseOrder(true)
            .withTagId(tagId)
            .search(new SearchResult<>(form));

        Map<Integer, Set<Integer>> messageTagMap = new MessageDAO(conSet.getConnection()).getProcessMessageTagMap(processIds);
        form.setResponseData("messageTagMap", messageTagMap);

        Set<Integer> tagIds = messageTagMap.values().stream().flatMap(mt -> mt.stream()).collect(Collectors.toSet());
        form.setResponseData("tagIds", tagIds);

        return html(conSet, form, PATH_JSP + "/process_message_list.jsp");
    }

    public ActionForward processMessageEdit(DynActionForm form, ConnectionSet conSet) throws Exception {
        MessageDAO dao = new MessageDAO(conSet.getSlaveConnection());

        restoreRequestParams(conSet.getConnection(), form, true, false, "messageTypeAdd");

        Message message = null;

        var replyToId = form.getParamInt("replyToId");
        if (replyToId > 0) {
            message = dao.getMessageById(replyToId);
            if (message == null)
                throw new BGException("Message not found: " + replyToId);
            message = getType(message.getTypeId()).getAnswerMessage(message);
        }
        else if (form.getId() > 0) {
            message = dao.getMessageById(form.getId());
        }

        var tagConfig = setup.getConfig(TagConfig.class);
        if (tagConfig != null)
            form.setResponseData("messageTagIds", dao.getMessageTags(form.getId()));

        if (message != null)
            form.setResponseData("message", message);

        return html(conSet, form, PATH_JSP + "/process_message_edit.jsp");
    }

    public ActionForward messageUpdate(DynActionForm form, ConnectionSet conSet)
            throws Exception {
        var type = getType(form.getParamInt("typeId"));

        // сохранение типа сообщения, чтобы в следующий раз выбрать в редакторе его
        if (form.getId() <= 0) {
            form.setParam("messageTypeAdd", String.valueOf(type.getId()));
            restoreRequestParams(conSet.getConnection(), form, false, true, "messageTypeAdd");
        }

        Message message = new Message();
        if (form.getId() > 0)
            message = new MessageDAO(conSet.getConnection()).getMessageById(form.getId());

        if (message.getId() > 0 && message.getUserId() != form.getUserId()) {
            if (UserCache.getPerm(form.getUserId(), "ru.bgcrm.struts.action.MessageAction:deleteEditOtherUsersNotes") == null) {
                throw new BGMessageException("Редактирование чужих сообщений запрещено!");
            }
        }

        Set<Integer> allowedTypeIds = Utils.toIntegerSet(form.getPermission().get("allowedTypeIds", ""));
        if (CollectionUtils.isNotEmpty(allowedTypeIds) && !allowedTypeIds.contains(type.getId())) {
            throw new BGMessageException("Вам запрещено создавать/редактировать данный тип сообщения!");
        }

        message.setId(form.getId());
        message.setUserId(form.getUserId());
        message.setTypeId(type.getId());
        message.setDirection(Message.DIRECTION_OUTGOING);
        message.setFromTime(new Date());
        message.setProcessId(form.getParamInt("processId"));
        message.setSubject(form.getParam("subject"));
        message.setTo(form.getParam("to", ""));
        message.setText(form.getParam("text"));

        String systemId = form.getParam("systemId");
        if (Utils.notBlankString(systemId))
            message.setSystemId(systemId);

        type.updateMessage(conSet.getConnection(), form, message);

        message.getAttachList().forEach(a -> { // remove output Fix
            try {
                if( a.getOutputStream() != null ) {
                    a.getOutputStream().close();
                    a.setOutputStream(null);
                }
            } catch (IOException e) {
                log.error(e.getMessage(),e);
            }
        });

        if (form.getParamBoolean("updateTags")) {
            form.setParam("id", String.valueOf(message.getId()));
            messageUpdateTags(form, conSet);
        }

        form.getResponse().setData("message", message);

        return json(conSet, form);
    }

}