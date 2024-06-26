package ru.bgcrm.plugin.bgbilling.proto.struts.action;

import java.time.YearMonth;
import java.util.Date;

import org.apache.struts.action.ActionForward;
import org.bgerp.model.Pageable;
import org.bgerp.util.TimeConvert;

import ru.bgcrm.plugin.bgbilling.Plugin;
import ru.bgcrm.plugin.bgbilling.proto.dao.DirectoryDAO;
import ru.bgcrm.plugin.bgbilling.proto.dao.RscmDAO;
import ru.bgcrm.plugin.bgbilling.proto.model.rscm.RscmService;
import ru.bgcrm.plugin.bgbilling.struts.action.BaseAction;
import ru.bgcrm.servlet.ActionServlet.Action;
import ru.bgcrm.struts.form.DynActionForm;
import ru.bgcrm.util.TimeUtils;
import ru.bgcrm.util.sql.ConnectionSet;

@Action(path = "/user/plugin/bgbilling/proto/rscm")
public class RscmAction extends BaseAction {
    private static final String PATH_JSP = Plugin.PATH_JSP_USER + "/rscm";

    public ActionForward serviceList(DynActionForm form, ConnectionSet conSet) {
        String billingId = form.getParam("billingId");
        int contractId = form.getParamInt("contractId");
        int moduleId = form.getParamInt("moduleId");
        Date dateFrom = form.getParamDate("dateFrom", TimeConvert.toDate(YearMonth.now()));
        Date dateTo = form.getParamDate("dateTo", TimeUtils.getEndMonth(new Date()));

        new RscmDAO(form.getUser(), billingId, moduleId).getServices(new Pageable<>(form), contractId, dateFrom, dateTo);

        return html(conSet, form, PATH_JSP + "/service_list.jsp");
    }

    public ActionForward serviceGet(DynActionForm form, ConnectionSet conSet) throws Exception {
        String billingId = form.getParam("billingId");
        int contractId = form.getParamInt("contractId");
        int moduleId = form.getParamInt("moduleId");

        if (form.getId() > 0) {
            form.setResponseData("service",
                    new RscmDAO(form.getUser(), billingId, moduleId).getService(contractId, form.getId()));
        }
        form.setResponseData("serviceTypeList",
                new DirectoryDAO(form.getUser(), billingId).getServiceTypeList(moduleId));

        return html(conSet, form, PATH_JSP + "/service_editor.jsp");
    }

    public ActionForward serviceUpdate(DynActionForm form, ConnectionSet conSet) throws Exception {
        String billingId = form.getParam("billingId");
        int contractId = form.getParamInt("contractId");
        int moduleId = form.getParamInt("moduleId");

        RscmService service = new RscmService();
        service.setId(form.getId());
        service.setContractId(contractId);
        service.setServiceId(form.getParamInt("serviceId"));
        service.setDate(form.getParamDate("date"));
        service.setAmount(form.getParamInt("amount"));
        service.setComment(form.getParam("comment", ""));

        new RscmDAO(form.getUser(), billingId, moduleId).updateService(service);

        return json(conSet, form);
    }

    public ActionForward serviceDelete(DynActionForm form, ConnectionSet conSet) throws Exception {
        String billingId = form.getParam("billingId");
        int contractId = form.getParamInt("contractId");
        int moduleId = form.getParamInt("moduleId");
        Date month = form.getParamDate("month");

        new RscmDAO(form.getUser(), billingId, moduleId).deleteService(contractId, form.getId(), month);

        return json(conSet, form);
    }
}