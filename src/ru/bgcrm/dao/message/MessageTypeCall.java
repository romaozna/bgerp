package ru.bgcrm.dao.message;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bgerp.app.bean.annotation.Bean;
import org.bgerp.app.cfg.ConfigMap;
import org.bgerp.app.cfg.Setup;
import org.bgerp.dao.message.MessageSearchDAO;
import org.bgerp.model.Pageable;
import org.bgerp.util.Dynamic;
import org.bgerp.util.Log;

import ru.bgcrm.dao.ParamValueDAO;
import ru.bgcrm.dao.expression.Expression;
import ru.bgcrm.model.BGException;
import ru.bgcrm.model.Pair;
import ru.bgcrm.model.message.Message;
import ru.bgcrm.struts.form.DynActionForm;
import ru.bgcrm.util.Utils;
import ru.bgcrm.util.sql.ConnectionSet;

@Bean
public class MessageTypeCall extends MessageType {
    private static final Log log = Log.getLog();

    // статические поля с данными регистрации, т.к. MessageTypeCall перезагружается при правке конфигурации
    private static final Map<Integer, Pair<Map<String, CallRegistration>, Map<Integer, CallRegistration>>> registedMap = new HashMap<>();

    public static class CallRegistration {
        private int userId;
        private String number;

        private Date lastPooling;
        private Message messageForOpen;

        public int getUserId() {
            return userId;
        }

        public String getNumber() {
            return number;
        }

        public Date getLastPooling() {
            return lastPooling;
        }

        public void setLastPooling(Date lastPooling) {
            this.lastPooling = lastPooling;
        }

        public Message getMessageForOpen() {
            return messageForOpen;
        }

        public void setMessageForOpen(Message value) {
            this.messageForOpen = value;
        }
    }

    private final String checkExpressionCallStore;

    public MessageTypeCall(Setup setup, int id, ConfigMap config) throws BGException {
        super(setup, id, config.get("title"), config);
        checkExpressionCallStore = config.get(Expression.CHECK_EXPRESSION_CONFIG_KEY + "CallStore");
    }

    @Override
    public boolean isProcessChangeSupport() {
        return true;
    }

    /**
     * Retrieves user offered number from text parameter.
     * @param userId user entity ID.
     * @return parameter value or empty string.
     */
    @Dynamic
    public String getUserOfferedNumber(int userId) {
        int paramId = configMap.getInt("offerNumberFromParamId");
        if (paramId > 0) {
            try (var con = Setup.getSetup().getDBSlaveConnectionFromPool()) {
                String value = new ParamValueDAO(con).getParamText(userId, paramId);
                return Utils.maskNull(value);
            } catch (Exception e) {
                log.error(e);
            }
        }
        return "";
    }

    private Pair<Map<String, CallRegistration>, Map<Integer, CallRegistration>> getRegMaps() {
        Pair<Map<String, CallRegistration>, Map<Integer, CallRegistration>> result = registedMap.get(id);
        if (result == null) {
            registedMap.put(id, result = new Pair<Map<String, CallRegistration>, Map<Integer, CallRegistration>>());
            result.setFirst(new HashMap<String, CallRegistration>());
            result.setSecond(new HashMap<Integer, CallRegistration>());
        }
        return result;
    }

    public void numberRegister(int userId, String number) {
        Pair<Map<String, CallRegistration>, Map<Integer, CallRegistration>> regMaps = getRegMaps();

        CallRegistration reg = regMaps.getFirst().get(number);
        if (reg != null) {
            reg.lastPooling = new Date();
        } else {
            reg = new CallRegistration();
            reg.userId = userId;
            reg.number = number;
            reg.lastPooling = new Date();

            regMaps.getFirst().put(number, reg);
            regMaps.getSecond().put(userId, reg);
        }
    }

    public void numberFree(int userId) {
        Pair<Map<String, CallRegistration>, Map<Integer, CallRegistration>> regMaps = getRegMaps();

        CallRegistration reg = regMaps.getSecond().get(userId);
        if (reg != null) {
            regMaps.getFirst().remove(reg.number);
            regMaps.getSecond().remove(reg.userId);
        }
    }

    public CallRegistration getRegistrationByUser(int userId) {
        return getRegMaps().getSecond().get(userId);
    }

    public CallRegistration getRegistrationByNumber(String number) {
        return getRegMaps().getFirst().get(number);
    }

    public String getCheckExpressionCallStore() {
        return checkExpressionCallStore;
    }

    @Override
    public void updateMessage(Connection con, DynActionForm form, Message message) throws SQLException {
        new MessageDAO(con).updateMessage(message);
    }

    @Override
    public List<Message> newMessageList(ConnectionSet conSet) throws SQLException {
        Pageable<Message> searchResult = new Pageable<Message>();

        new MessageSearchDAO(conSet.getConnection())
            .withTypeId(id)
            .withDirection(Message.DIRECTION_INCOMING)
            .withProcessed(false)
            .search(searchResult);

        return searchResult.getList();
    }

    @Override
    public Message newMessageGet(ConnectionSet conSet, String messageId) throws Exception {
        return new MessageDAO(conSet.getConnection()).getMessageBySystemId(id, messageId);
    }

    @Override
    public void messageDelete(ConnectionSet conSet, String... messageIds) throws Exception {
        MessageDAO messageDao = new MessageDAO(conSet.getConnection());

        for (String messageId : messageIds) {
            Message message = messageDao.getMessageBySystemId(id, messageId);
            if (message != null) {
                messageDao.deleteMessage(message.getId());
            }
        }
    }

    @Override
    public Message newMessageLoad(Connection con, String messageId) throws Exception {
        Message result = null;

        MessageDAO messageDao = new MessageDAO(con);

        result = messageDao.getMessageBySystemId(id, messageId);

        messageDao.updateMessage(result);

        return result;
    }
}