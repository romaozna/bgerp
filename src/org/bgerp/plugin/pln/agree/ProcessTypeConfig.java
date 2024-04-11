package org.bgerp.plugin.pln.agree;

import java.util.Date;

import org.bgerp.app.cfg.ConfigMap;
import org.bgerp.app.event.EventProcessor;
import org.bgerp.app.exception.BGMessageException;
import org.bgerp.app.l10n.Localization;
import org.bgerp.cache.UserCache;
import org.bgerp.plugin.pln.agree.event.AgreementEvent;

import ru.bgcrm.dao.process.ProcessDAO;
import ru.bgcrm.dao.process.StatusChangeDAO;
import ru.bgcrm.model.process.Process;
import ru.bgcrm.model.process.ProcessExecutor;
import ru.bgcrm.model.process.ProcessGroup;
import ru.bgcrm.model.process.StatusChange;
import ru.bgcrm.struts.form.DynActionForm;
import ru.bgcrm.util.sql.ConnectionSet;

public class ProcessTypeConfig extends org.bgerp.app.cfg.Config {
    private final int statusFromId;
    private final int statusToId;
    private final int groupId;
    private final int roleId;
    private final boolean all;

    protected ProcessTypeConfig(ConfigMap config) throws InitStopException {
        super(null);
        config = config.sub(Plugin.ID + ":");
        statusFromId = config.getInt("status.from");
        statusToId = config.getInt("status.to");
        groupId = config.getInt("executor.group");
        roleId = config.getInt("executor.role", 0);
        all = config.getBoolean("executor.all", false);
        initWhen(statusFromId > 0 && statusToId > 0 && groupId > 0);
    }

    public void statusChanged(DynActionForm form, ConnectionSet conSet, Process process) throws Exception {
        int statusId = process.getStatusId();
        var dao = new ProcessDAO(conSet.getConnection());

        if (statusId == statusFromId) {
            if (!process.getGroupIds().contains(groupId)) {
                var groups = process.getGroups();
                groups.add(new ProcessGroup(groupId, roleId));
                dao.updateProcessGroups(groups, process.getId());
            }

            var executors = process.getExecutors();
            UserCache.getUserList().stream()
                .filter(u -> u.getGroupIds().contains(groupId))
                .forEach(user -> executors.add(new ProcessExecutor(user.getId(), groupId, roleId)));
            dao.updateProcessExecutors(executors, process.getId());

            EventProcessor.processEvent(new AgreementEvent(form, process, false), conSet);
        } else if (statusId == statusToId) {
            var l = Localization.getLocalizer(Plugin.ID, form.getHttpRequest());

            var executors = process.getExecutors();
            if (!executors.removeIf(pe -> pe.getUserId() == form.getUserId() && pe.getGroupId() == groupId && pe.getRoleId() == roleId))
                throw new BGMessageException(l.l("Only members of {} group can do the status change", UserCache.getUserGroup(groupId).getTitle()));

            if (!all || executors.stream().filter(pe -> pe.getGroupId() == groupId && pe.getRoleId() == roleId).count() == 0) {
                log.debug("Agreement has done, process: {}", process.getId());
                EventProcessor.processEvent(new AgreementEvent(form, process, true), conSet);
            } else {
                log.debug("Agreement has not done, process: {}", process.getId());

                var statusChange = new StatusChange(process.getId(), new Date(), form.getUserId(), statusFromId, l.l("Not all the required executors have agreed"));
                new StatusChangeDAO(conSet.getConnection()).changeStatus(process, process.getType(), statusChange);

                // TODO: Notification about partial agreement?
            }

            dao.updateProcessExecutors(executors, process.getId());
        }
    }

}
