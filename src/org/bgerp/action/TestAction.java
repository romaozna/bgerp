package org.bgerp.action;

import java.util.List;

import org.apache.struts.action.ActionForward;

import ru.bgcrm.servlet.ActionServlet.Action;
import ru.bgcrm.struts.action.BaseAction;
import ru.bgcrm.struts.form.DynActionForm;
import ru.bgcrm.util.sql.ConnectionSet;

@Action(path = "/user/test")
public class TestAction extends BaseAction {
    @Override
    public ActionForward unspecified(DynActionForm form, ConnectionSet conSet) throws Exception {
        return html(conSet, form, PATH_JSP_USER + "/test.jsp");
    }

    public ActionForward enumValues(DynActionForm form, ConnectionSet conSet) throws Exception {
        List<String> values = List.of(
                "mail1@domain.com",
                "Ivan2 Pupkin <mail2@domain.com>",
                "Ivan3 Pupkin <mail3@domain.com>");
        form.setResponseData("values", values);
        return json(conSet, form);
    }

    // TODO: Some helper methods for testing parameters validatation and so on.
}
