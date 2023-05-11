package ru.bgcrm.plugin.mobile;

import java.sql.SQLException;
import java.util.Collection;
import java.util.stream.Collectors;

import org.bgerp.util.Log;

import ru.bgcrm.dao.expression.ExpressionContextAccessingObject;
import ru.bgcrm.model.process.Process;
import ru.bgcrm.model.user.User;
import ru.bgcrm.plugin.mobile.dao.MobileDAO;
import ru.bgcrm.plugin.mobile.model.Account;
import ru.bgcrm.struts.form.DynActionForm;
import ru.bgcrm.util.Setup;

public class ExpressionObject extends ExpressionContextAccessingObject {
    private static final Log log = Log.getLog();

    /**
     * Sends mobile app push notification to executors, except ones from the current {@link DynActionForm}.
     * @param subject message subject.
     * @param text message text.
     * @throws SQLException
     */
    public void sendMessageToExecutors(String subject, String text) throws SQLException {
        Process process = (Process)expression.getContextObject(Process.OBJECT_TYPE);
        DynActionForm form = (DynActionForm)expression.getContextObject(DynActionForm.KEY);

        Collection<Integer> userIds = process.getExecutorIds().stream()
            .filter(userId -> userId != form.getUserId())
            .collect(Collectors.toList());

        sendMessageToUsers(subject, text, userIds);
    }

    /**
     * Sends mobile app push notification to executors.
     * @param subject message subject.
     * @param text message text.
     * @param userIds recipient user IDs.
     * @throws SQLException
     */
    public void sendMessageToUsers(String subject, String text, Iterable<Integer> userIds) throws SQLException {
        try (var con = Setup.getSetup().getDBSlaveConnectionFromPool()) {
            GMS gms = Setup.getSetup().getConfig(GMS.class);
            for (int userId : userIds) {
                Account account = new MobileDAO(con).findAccount(User.OBJECT_TYPE, userId);
                if (account == null) {
                    log.debug("User {} isn't logged in.", userId);
                    continue;
                }
                gms.sendMessage(account.getKey(), subject, text);
            }
        }
    }
}
