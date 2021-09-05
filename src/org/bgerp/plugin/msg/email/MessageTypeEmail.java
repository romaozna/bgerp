package org.bgerp.plugin.msg.email;

import java.io.File;
import java.io.OutputStream;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;

import org.apache.commons.io.IOUtils;
import org.bgerp.plugin.msg.email.MessageParser.MessageAttach;

import ru.bgcrm.cache.ProcessTypeCache;
import ru.bgcrm.cache.UserCache;
import ru.bgcrm.dao.FileDataDAO;
import ru.bgcrm.dao.Locker;
import ru.bgcrm.dao.expression.Expression;
import ru.bgcrm.dao.message.MessageDAO;
import ru.bgcrm.dao.message.MessageType;
import ru.bgcrm.dao.message.config.MessageTypeConfig;
import ru.bgcrm.dao.process.ProcessDAO;
import ru.bgcrm.dao.user.UserDAO;
import ru.bgcrm.event.EventProcessor;
import ru.bgcrm.event.process.ProcessMessageAddedEvent;
import ru.bgcrm.model.BGException;
import ru.bgcrm.model.BGMessageException;
import ru.bgcrm.model.FileData;
import ru.bgcrm.model.SearchResult;
import ru.bgcrm.model.message.Message;
import ru.bgcrm.model.message.TagConfig;
import ru.bgcrm.model.message.TagConfig.Tag;
import ru.bgcrm.model.param.ParameterSearchedObject;
import ru.bgcrm.model.process.Process;
import ru.bgcrm.model.process.ProcessType;
import ru.bgcrm.model.user.User;
import ru.bgcrm.struts.action.FileAction.FileInfo;
import ru.bgcrm.struts.action.FileAction.SessionTemporaryFiles;
import ru.bgcrm.struts.action.ProcessAction;
import ru.bgcrm.struts.form.DynActionForm;
import ru.bgcrm.util.AlarmErrorMessage;
import ru.bgcrm.util.AlarmSender;
import ru.bgcrm.util.MailConfig;
import ru.bgcrm.util.MailMsg;
import ru.bgcrm.util.ParameterMap;
import ru.bgcrm.util.Setup;
import ru.bgcrm.util.TimeUtils;
import ru.bgcrm.util.Utils;
import ru.bgcrm.util.sql.ConnectionSet;
import ru.bgcrm.util.sql.SingleConnectionConnectionSet;
import ru.bgerp.util.Log;

public class MessageTypeEmail extends MessageType {
    private static final Log log = Log.getLog();

    private static final String AUTOREPLY_SYSTEM_ID = "autoreply";
    public static final String RE_PREFIX = "Re: ";

    private static final RecipientType[] RECIPIENT_TYPES = new RecipientType[] { RecipientType.TO, RecipientType.CC };

    private final Pattern processIdPattern;
    private final Pattern quickAnswerPattern;
    private final int quickAnswerEmailParamId;
    private final int autoCreateProcessTypeId;
    private final boolean autoCreateProcessNotification;

    private final String replayTo;
    private final MailConfig mailConfig;

    private final String folderIncoming;
    private final String folderSkipped;
    private final String folderProcessed;
    private final String folderSent;
    private final String folderTrash;
    private final String signExpression;
    private final boolean signStandard;

    private final FolderCache incomingCache = new FolderCache(this);

    public MessageTypeEmail(int id, ParameterMap config) throws BGException {
        super(id, config.get("title"), config);

        mailConfig = new MailConfig(config);

        replayTo = config.get("replayTo");

        folderIncoming = config.get("folderIn");
        folderProcessed = config.get("folderProcessed", "CRM_PROCESSED");
        folderSkipped = config.get("folderSkipped", "CRM_SKIPPED");
        folderSent = config.get("folderSent", "CRM_SENT");
        folderTrash = config.get("folderTrash", "Trash");
        signExpression = config.get("signExpression");
        signStandard = config.getBoolean("signStandard", false);
        processIdPattern = Pattern.compile(mailConfig.getEmail().replaceAll("\\.", "\\\\.") + "#(\\d+)");
        quickAnswerPattern = Pattern.compile("QA:(\\d+)");
        quickAnswerEmailParamId = config.getInt("quickAnswerEmailParamId", -1);
        autoCreateProcessTypeId = config.getInt("autoCreateProcess.typeId", -1);
        autoCreateProcessNotification = config.getBoolean("autoCreateProcess.notification", true);

        if (!mailConfig.check() || Utils.isBlankString(folderIncoming) ) {
            throw new BGException("Incorrect message type, email: " + mailConfig.getEmail());
        }
    }

    public String getEmail() {
        return mailConfig.getEmail();
    }

    @Override
    public void process() {
        log.info("Starting EMail daemon, box: " + mailConfig.getEmail());

        readBox();
        sendMessages();
    }

    @Override
    public boolean isAnswerSupport() {
        return true;
    }

    @Override
    public boolean isAttachmentSupport() {
        return true;
    }

    @Override
    public boolean isEditable(Message message) {
        // incoming and not sent
        return message.getDirection() == Message.DIRECTION_OUTGOING && message.getToTime() == null;
    }

    @Override
    public boolean isProcessChangeSupport() {
        return true;
    }

    @Override
    public String getHeaderJsp() {
        return Plugin.PATH_JSP_USER + "/process_message_header.jsp";
    }

    @Override
    public String getEditorJsp() {
        return Plugin.PATH_JSP_USER + "/process_message_editor.jsp";
    }

    private static final FetchProfile FETCH_PROFILE = new FetchProfile();
    static {
        FETCH_PROFILE.add(FetchProfile.Item.SIZE);
    }

    @Override
    public List<Message> newMessageList(ConnectionSet conSet) throws Exception {
        List<Message> result = new ArrayList<Message>();

        long time = System.currentTimeMillis();
        try (var store = mailConfig.getImapStore();
            var incomingFolder = store.getFolder(folderIncoming);) {

            log.debug("Get imap store time: %s %s", System.currentTimeMillis() - time, "ms.");

            incomingFolder.open(Folder.READ_ONLY);

            result = incomingCache.list(incomingFolder);

            log.debug("New message list time: %s %s",  System.currentTimeMillis() - time, "ms.");
        }

        unprocessedMessagesCount = result.size();

        return result;
    }

    @Override
    public Message newMessageGet(ConnectionSet conSet, String messageId) throws Exception {
        Message result = null;

        try (var store = mailConfig.getImapStore();
            var incomingFolder = store.getFolder(folderIncoming)) {
            incomingFolder.open(Folder.READ_ONLY);

            var messages = incomingFolder.getMessages();
            int index = incomingCache.idToIndex(messageId);

            var mp = new MessageParser(messages[index]);

            result = extractMessage(mp, true);
            addAttaches(mp, result);
        }

        return result;
    }

    @Override
    public void messageDelete(ConnectionSet conSet, String... messageIds) throws Exception {
        try (var store = mailConfig.getImapStore();
            var incomingFolder = store.getFolder(folderIncoming);
            var trashFolder = store.getFolder(folderTrash)) {

            incomingFolder.open(Folder.READ_WRITE);
            trashFolder.open(Folder.READ_WRITE);

            var messages = incomingFolder.getMessages();

            var list = new ArrayList<javax.mail.Message>(messageIds.length);

            for (var messageId : messageIds) {
                int index = incomingCache.idToIndex(messageId);
                var message = messages[index];

                message.setFlag(Flags.Flag.DELETED, true);
                list.add(message);
            }

            incomingFolder.copyMessages(list.toArray(new javax.mail.Message[0]), trashFolder);

            incomingCache.delete(messageIds);
        }
    }

    private void addAttaches(MessageParser mp, Message msg) throws Exception {
        for (MessageAttach attach : mp.getAttachContent()) {
            FileData file = new FileData();
            file.setTitle(attach.title);
            msg.addAttach(file);
        }
    }

    @Override
    public Message newMessageLoad(Connection con, String messageId) throws Exception {
        try (var store = mailConfig.getImapStore();
            var incomingFolder = store.getFolder(folderIncoming);
            var processedFolder = store.getFolder(folderProcessed);
            var skippedFolder = store.getFolder(folderSkipped);) {
            incomingFolder.open(Folder.READ_WRITE);
            processedFolder.open(Folder.READ_WRITE);
            skippedFolder.open(Folder.READ_WRITE);

            int index = incomingCache.idToIndex(messageId);
            var message = incomingFolder.getMessages()[index];

            return processMessage(con, incomingFolder, processedFolder, skippedFolder, message);
        }
    }

    public String getMessageDescription(Message message) {
        StringBuilder result = new StringBuilder(200);

        result.append("EMail: \"");
        result.append(message.getSubject());
        result.append("\"; ");
        result.append(message.getFrom());
        result.append(" => ");
        result.append(message.getTo());
        result.append("; ");
        if (message.getDirection() == Message.DIRECTION_INCOMING) {
            result.append("получено: ");
            result.append(TimeUtils.format(message.getFromTime(), TimeUtils.FORMAT_TYPE_YMDHM));
        } else {
            result.append("отправлено: ");
            result.append(TimeUtils.format(message.getToTime(), TimeUtils.FORMAT_TYPE_YMDHM));
        }

        return result.toString();
    }

    private void sendMessages() {
        String encoding = MailMsg.getParamMailEncoding(Setup.getSetup());
        Session session = mailConfig.getSmtpSession(Setup.getSetup());

        try (var con = Setup.getSetup().getDBConnectionFromPool();
            var transport = session.getTransport();
            var imapStore = mailConfig.getImapStore();
            var imapSentFolder = imapStore.getFolder(folderSent);) {

            var messageDAO = new MessageDAO(con);

            transport.connect();
            imapSentFolder.open(Folder.READ_WRITE);

            List<Message> toSendList = messageDAO.getUnsendMessageList(id, 100);
            for (Message msg : toSendList) {
                log.info("Send message subject: {} to: {}", msg.getSubject(), msg.getTo());

                if (Locker.checkLock(msg.getLockEdit())) {
                    log.info("Skipping message on lock");
                    continue;
                }

                try {
                    MimeMessage message = new MimeMessage(session);
                    message.setFrom(new InternetAddress(mailConfig.getFrom()));
                    if (Utils.notBlankString(replayTo)) {
                        message.setReplyTo(InternetAddress.parse(replayTo));
                    }

                    Map<RecipientType, List<InternetAddress>> addrMap = parseAddresses(msg.getTo(), null, null);
                    for (RecipientType type : RECIPIENT_TYPES) {
                        List<InternetAddress> addrList = addrMap.get(type);
                        if (addrList == null) {
                            continue;
                        }

                        for (InternetAddress addr : addrList) {
                            message.addRecipient(type, addr);
                        }
                    }

                    String subject = msg.getSubject();

                    int processId = getProcessId(subject);
                    if (processId <= 0 && msg.getProcessId() > 0) {
                        subject = getSubjectWithProcessIdSuffix(msg);
                    }

                    message.setSubject(subject, encoding);
                    message.setSentDate(new Date());

                    createEmailText(message, encoding, msg);

                    transport.sendMessage(message, message.getAllRecipients());

                    if (imapSentFolder != null) {
                        message.setFlag(Flags.Flag.SEEN, true);
                        imapSentFolder.appendMessages(new javax.mail.Message[] { message });
                        log.info("Saved copy to folder: " + folderSent);
                    }
                } catch (Exception e) {
                    log.error(e);
                }

                if (AUTOREPLY_SYSTEM_ID.equals(msg.getSystemId())) {
                    messageDAO.deleteMessage(msg.getId());
                } else {
                    msg.setToTime(new Date());
                    messageDAO.updateMessageProcess(msg);
                }

                con.commit();
            }
        } catch (Exception e) {
            log.error(e);
        }
    }

    public String getSubjectWithProcessIdSuffix(Message msg) {
        return msg.getSubject() + " [" + mailConfig.getEmail() + "#" + msg.getProcessId() + "]";
    }

    private void createEmailText(MimeMessage message, String encoding, Message msg)
            throws Exception {
        MessageTypeConfig typeConfig = Setup.getSetup().getConfig(MessageTypeConfig.class);

        StringBuilder text = new StringBuilder();
        text.append(msg.getText());
        text.append("\n");
        text.append("\n-- ");

        if (Utils.notBlankString(signExpression)) {
            Map<String, Object> context = new HashMap<String, Object>();
            context.put(User.OBJECT_TYPE, UserCache.getUser(msg.getUserId()));
            context.put("message", msg);

            text.append(new Expression(context).getString(signExpression));
        }

        if (signStandard) {
            text.append("\nСообщение подготовлено системой BGERP (https://bgerp.ru).");
            text.append("\nНе изменяйте, пожалуйста, тему сообщения и не цитируйте данное сообщение в ответе!");
            text.append("\nИсторию переписки вы можете посмотреть в приложенном файле History.txt");
        }

        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(text.toString(), encoding);

        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(textPart);

        addHistory(encoding, msg, typeConfig, multipart);

        if (msg.getAttachList().size() > 0) {
            try (var con = Setup.getSetup().getDBConnectionFromPool()) {
                FileDataDAO fileDao = new FileDataDAO(con);

                for (FileData attach : msg.getAttachList()) {
                    File file = fileDao.getFile(attach);

                    MimeBodyPart attachPart = new MimeBodyPart();
                    attachPart.setHeader("Content-Type", "charset=\"UTF-8\"; format=\"flowed\"");
                    attachPart.setDataHandler(new DataHandler(new FileDataSource(file)));
                    attachPart.setFileName(MimeUtility.encodeWord(attach.getTitle(), encoding, null));
                    multipart.addBodyPart(attachPart);

                    log.debug("Attach: %s", attach.getTitle());
                }
            }
        }

        message.setContent(multipart);
    }

    private void addHistory(String encoding, Message msg, MessageTypeConfig typeConfig, MimeMultipart multipart)
            throws Exception, MessagingException {
        var history = new StringBuilder(1000);

        int processId = msg.getProcessId();
        if (processId > 0) {
            history
                .append("История сообщений по процессу #")
                .append(processId)
                .append(":\n------------------------------------------");

            String to = msg.getTo();

            var tagsConfig = Setup.getSetup().getConfig(TagConfig.class);

            int historyModeTag = 0;
            List<Message> messageList = null;
            
            try (var con = Setup.getSetup().getDBSlaveConnectionFromPool()) {
                var dao = new MessageDAO(con);
                historyModeTag = tagsConfig.getSelectedHistoryTag(dao.getMessageTags(msg.getId()));
                if (historyModeTag != 0) {
                    messageList = dao.getProcessMessageList(processId, msg.getId());
                }
            }

            if (historyModeTag != 0) {
                for (Message historyItem : messageList) {
                    if (historyModeTag == Tag.TAG_HISTORY_WITH_ADDRESS_ID &&
                        !historyItem.getFrom().equals(to) &&
                        !historyItem.getTo().equals(to)) {
                        continue;
                    }

                    var type = typeConfig.getTypeMap().get(historyItem.getTypeId());
                    history
                        .append("\n\n")
                        .append(type.getMessageDescription(historyItem))
                        .append("\n------------------------------------------")
                        .append("\n")
                        .append(historyItem.getText());
                }
            }
        }

        var historyPart = new MimeBodyPart();
        historyPart.setText(history.toString(), encoding);
        historyPart.setFileName("History.txt");
        multipart.addBodyPart(historyPart);
    }

    private void readBox() {
        try (Connection con = Setup.getSetup().getDBConnectionFromPool();
            Store store = mailConfig.getImapStore();
            Folder incomingFolder = store.getFolder(folderIncoming);
            Folder processedFolder = store.getFolder(folderProcessed);
            Folder skippedFolder = store.getFolder(folderSkipped);) {

            checkFolders(processedFolder, skippedFolder, store.getFolder(folderTrash));

            incomingFolder.open(Folder.READ_WRITE);
            processedFolder.open(Folder.READ_WRITE);
            skippedFolder.open(Folder.READ_WRITE);

            javax.mail.Message[] messages = null;

            var list = incomingCache.list(incomingFolder);
            for (int i = 0; i < list.size(); i++) {
                var message = list.get(i);
                var subject = message.getSubject();
                if (getProcessId(subject) > 0 || getQuickAnswerMessageId(subject) > 0 || autoCreateProcessTypeId > 0) {
                    if (messages == null) {
                        messages = incomingFolder.getMessages();
                    }
                    processMessage(con, incomingFolder, processedFolder, skippedFolder, messages[i]);
                    continue;
                }
                log.debug("Skipping message with subject: %s", subject);
            }

            unprocessedMessagesCount = list.size();
        } catch (Exception e) {
            log.error("Reading box " + mailConfig.getEmail() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Обрабатывает сообщение и производит перемещение между папками.
     */
    private Message processMessage(Connection con, Folder incomingFolder, Folder processedFolder, Folder skippedFolder,
            javax.mail.Message message) throws MessagingException {
        Message result = null;

        try {
            // клонирование сообщения для избежания ошибки "Unable to load BODYSTRUCTURE"
            // http://www.oracle.com/technetwork/java/javamail/faq/index.html#imapserverbug
            // MessageParser mp = new MessageParser(new MimeMessage((MimeMessage) message));
            MessageParser mp = new MessageParser(message);
            Message msg = extractMessage(mp, true);

            FileDataDAO fileDao = new FileDataDAO(con);
            for (MessageAttach attach : mp.getAttachContent()) {
                FileData file = new FileData();
                file.setTitle(attach.title);

                OutputStream out = fileDao.add(file);
                IOUtils.copy(attach.inputStream, out);

                msg.addAttach(file);
            }

            result = processMessage(con, msg );

            incomingFolder.copyMessages(new javax.mail.Message[] { message }, processedFolder);
            con.commit();
        } catch (Exception e) {
            log.error(e);
            incomingFolder.copyMessages(new javax.mail.Message[] { message }, skippedFolder);
        }

        message.setFlag(Flags.Flag.DELETED, true);

        return result;
    }

    private Message processMessage(Connection con, Message msg) throws BGException {
        String subject = "";

        try {
            MessageDAO messageDAO = new MessageDAO(con);
            ProcessDAO processDAO = new ProcessDAO(con);

            subject = msg.getSubject();
            int processId = getProcessId(subject);
            int quickAnsweredMessageId = getQuickAnswerMessageId(subject);

            log.info("Mailbox: " + mailConfig.getEmail() + " found message " + " From: "
                    + msg.getFrom() + "\t Subject: " + subject + "\t Content: " + msg.getText() + "\t Process ID: " + processId
                    + "\t Quick answer message ID: " + quickAnsweredMessageId); // + "\t Content-Type: " + message.getContentType()

            Process process = null;

            // определение кода процесса
            if (processId > 0) {
                // TODO: Подумать, чтобы не хулиганили и не писали в чужие процессы..
                // Может к коду процесса чего добавить.
                process = processDAO.getProcess(processId);
                if (process == null)
                    log.error("Not found process with code: " + processId);
                else
                    setMessageProcessed(msg, processId);
            }
            // сообщение переделывается в исходящее и отправляется
            else if (quickAnsweredMessageId > 0) {
                Message quickAnsweredMessage = messageDAO.getMessageById(quickAnsweredMessageId);
                if (quickAnsweredMessage == null) {
                    log.error("Message not found: " + quickAnsweredMessageId);
                }
                else {
                    MessageType quickAnsweredMessageType =
                            Setup.getSetup().getConfig(MessageTypeConfig.class)
                            .getTypeMap().get(quickAnsweredMessage.getTypeId());

                    // изменение входящего сообщения в исходящее
                    msg.setTypeId(quickAnsweredMessage.getTypeId());
                    msg.setDirection(Message.DIRECTION_OUTGOING);
                    msg.setProcessId(quickAnsweredMessage.getProcessId());
                    msg.setProcessed(true);
                    msg.setTo(quickAnsweredMessage.getFrom());
                    msg.setFromTime(new Date());
                    msg.setToTime(null);

                    String quickAnswerSubject = quickAnsweredMessage.getSubject();
                    if (!quickAnswerSubject.startsWith(RE_PREFIX))
                        quickAnswerSubject = RE_PREFIX + quickAnswerSubject;

                    msg.setSubject(quickAnswerSubject);

                    // поиск пользователя по E-Mail
                    SearchResult<ParameterSearchedObject<User>> searchResult = new SearchResult<>();
                    new UserDAO(con).searchUserListByEmail(searchResult, Collections.singletonList(quickAnswerEmailParamId), msg.getFrom());
                    ParameterSearchedObject<User> user = Utils.getFirst(searchResult.getList());

                    if (user != null) {
                        log.info("Creating quick answer on message: " + quickAnsweredMessageId);
                        quickAnsweredMessageType.updateMessage(con, new DynActionForm(user.getObject()), msg);
                        return msg;
                    }
                }
            } else if (autoCreateProcessTypeId > 0) {
                process = new Process();
                process.setTypeId(autoCreateProcessTypeId);
                process.setDescription(msg.getSubject());
                ProcessAction.processCreate(DynActionForm.SERVER_FORM, con, process);

                log.info("Created process: %s", process.getId());

                setMessageProcessed(msg, process.getId());

                if (autoCreateProcessNotification)
                    messageDAO.updateMessage(messageLinkedToProcess(msg));
            }

            messageDAO.updateMessage(msg);

            if (process != null) {
                ProcessType type = ProcessTypeCache.getProcessType(process.getTypeId());
                if (type == null) {
                    log.error("Not found process type with id:" + process.getTypeId());
                } else {
                    EventProcessor.processEvent(new ProcessMessageAddedEvent(DynActionForm.SERVER_FORM, msg, process),
                            type.getProperties().getActualScriptName(), new SingleConnectionConnectionSet(con));
                }
            }

            return msg;
        } catch (Exception e) {
            String key = "email.parse.error";
            long time = System.currentTimeMillis();

            if (AlarmSender.needAlarmSend(key, time, 0)) {
                AlarmSender.sendAlarm(new AlarmErrorMessage(key, "Ошибка разбора E-Mail", "Тема письма " + subject), time);
            }

            log.error(e);

            throw new BGException(e);
        }
    }

    private void setMessageProcessed(Message msg, int processId) {
        msg.setProcessId(processId);
        msg.setProcessed(true);
        msg.setToTime(new Date());
        msg.setUserId(User.USER_SYSTEM_ID);
    }

    private Message extractMessage(MessageParser mp, boolean extractText)
            throws Exception, MessagingException {
        Message msg = new Message();
        msg.setTypeId(id);
        msg.setFrom(mp.getFrom());
        msg.setTo(mp.getTo());
        msg.setSystemId(mp.getMessageId());
        msg.setFromTime(mp.getFromTime());

        // время прочтения = времени получения, чтобы не считалось непрочитанным
        msg.setToTime(new Date());
        msg.setDirection(ru.bgcrm.model.message.Message.DIRECTION_INCOMING);
        msg.setSubject(mp.getMessageSubject());

        if (extractText)
            msg.setText(mp.getTextContent());

        return msg;
    }

    /** Выделяет из темы письма код привязанного процесса. */
    private int getProcessId(String subject) {
        Matcher m = processIdPattern.matcher(subject);
        if (m.find())
            return Utils.parseInt(m.group(1));
        return -1;
    }

    /** Выделяет из письма код сообщения для быстрого ответа. */
    private int getQuickAnswerMessageId(String subject) {
        Matcher m = quickAnswerPattern.matcher(subject);
        if (m.find())
            return Utils.parseInt(m.group(1));
        return -1;
    }

    @Override
    public void updateMessage(Connection con, DynActionForm form, Message message) throws BGException {
        message.setSystemId("");
        message.setFrom(mailConfig.getEmail());

        if (Utils.isBlankString(message.getTo())) {
            throw new BGMessageException("Не указан EMail получателя.");
        }

        try {
            parseAddresses(message.getTo(), null, null);
        } catch (Exception ex) {
            throw new BGMessageException("Некорректный EMail получателя. " + ex.getMessage());
        }

        Map<Integer, FileInfo> tmpFiles = processMessageAttaches(con, form, message);

        new MessageDAO(con).updateMessage(message);

        SessionTemporaryFiles.deleteFiles(form, tmpFiles.keySet());
    }

    @Override
    public Message messageLinkedToProcess(Message message) throws BGException {
        Message result = new Message();

        String text = message.getText().replace("\r", "");
        text = ">" + text.replace("\n", "\n>");

        text = "Уважаемый клиент, ваше обращение зарегистрировано!\n"
                + "Для него назначен исполнитель и в ближайшее возможное время вам будет дан ответ.\n"
                + "Пожалуйста, при возникновении дополнительных сообщений по данному вопросу отвечайте на это письмо,\n"
                + "так чтобы в теме письма сохранялся числовой идентификатор обращения.\n"
                + "Это позволит нам быстрее обработать ваш запрос.\n\n" + text;

        result.setSystemId(AUTOREPLY_SYSTEM_ID);
        result.setDirection(Message.DIRECTION_OUTGOING);
        result.setTypeId(id);
        result.setProcessId(message.getProcessId());
        result.setFrom(mailConfig.getEmail());
        result.setTo(serializeAddresses(parseAddresses(message.getTo(), message.getFrom(), mailConfig.getEmail())));
        result.setFromTime(new Date());
        result.setText(text);
        result.setSubject(message.getSubject());
        if (!result.getSubject().startsWith("Re:")) {
            result.setSubject("Re: " + result.getSubject());
        }

        return result;
    }

    /** Create folders, if they don't exist */
    private void checkFolders(Folder... folders) throws MessagingException {
        for (Folder folder : folders) {
            if (!folder.exists()) {
                folder.create(Folder.HOLDS_MESSAGES);
            }
        }
    }

    private Map<RecipientType, List<InternetAddress>> parseAddresses(String addresses, String addAddress,
            String excludeAddress) throws BGException {
        Map<RecipientType, List<InternetAddress>> result = new HashMap<RecipientType, List<InternetAddress>>();

        try {
            for (String token : addresses.split("\\s*;\\s*")) {
                int pos = token.indexOf(':');

                String prefix = null;
                if (pos > 0) {
                    prefix = token.substring(0, pos);
                    token = token.substring(pos + 1);
                }

                try {
                    RecipientType type = null;
                    if (Utils.isBlankString(prefix)) {
                        type = RecipientType.TO;
                    } else if (prefix.equalsIgnoreCase("CC")) {
                        type = RecipientType.CC;
                    } else {
                        throw new BGMessageException("Не поддерживаемый префикс: " + prefix);
                    }

                    List<InternetAddress> addressList = new ArrayList<InternetAddress>();
                    for (InternetAddress addr : InternetAddress.parse(token)) {
                        if (excludeAddress != null && addr.getAddress().equals(excludeAddress)) {
                            continue;
                        }
                        addressList.add(addr);
                    }

                    if (addressList.size() > 0) {
                        result.put(type, addressList);
                    }
                } catch (Exception e) {
                    log.error(e);
                }
            }

            if (addAddress != null) {
                List<InternetAddress> toAddressList = result.get(RecipientType.TO);
                if (toAddressList == null) {
                    result.put(RecipientType.TO, toAddressList = new ArrayList<InternetAddress>(1));
                }
                toAddressList.add(0, new InternetAddress(addAddress));
            }
        } catch (Exception e) {
        }

        return result;
    }

    private String serializeAddresses(Map<RecipientType, List<InternetAddress>> addressMap) {
        StringBuilder result = new StringBuilder();

        for (RecipientType type : RECIPIENT_TYPES) {
            List<InternetAddress> addressList = addressMap.get(type);
            if (addressList == null) {
                continue;
            }

            StringBuilder part = new StringBuilder();
            for (InternetAddress addr : addressList) {
                Utils.addCommaSeparated(part, addr.getAddress());
            }

            if (type != RecipientType.TO) {
                part.insert(0, type.toString() + ": ");
            }
            Utils.addSeparated(result, "; ", part.toString());
        }

        return result.toString();
    }

}