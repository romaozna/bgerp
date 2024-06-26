package ru.bgcrm.plugin.slack.action;

import org.apache.struts.action.ActionForward;
import org.bgerp.app.exception.BGIllegalArgumentException;

import ru.bgcrm.dao.process.ProcessDAO;
import ru.bgcrm.model.process.Process;
import ru.bgcrm.plugin.slack.ExpressionObject;
import ru.bgcrm.servlet.ActionServlet.Action;
import ru.bgcrm.struts.form.DynActionForm;
import ru.bgcrm.util.sql.ConnectionSet;

@Action(path = "/user/plugin/slack/channel")
public class ChannelAction extends org.bgerp.action.base.BaseAction {

    public ActionForward addProcessChannelLink(DynActionForm form, ConnectionSet conSet) throws Exception {
        Process process = new ProcessDAO(conSet.getConnection()).getProcess(form.getParamInt("processId"));
        if (process == null)
            throw new BGIllegalArgumentException();

        new ExpressionObject().linkChannel(process);

        return json(conSet, form);
    }
}