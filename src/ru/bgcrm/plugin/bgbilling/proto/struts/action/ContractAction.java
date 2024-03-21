package ru.bgcrm.plugin.bgbilling.proto.struts.action;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.apache.commons.collections.CollectionUtils;
import org.apache.struts.action.ActionForward;
import org.bgerp.app.exception.BGMessageExceptionTransparent;
import org.bgerp.model.Pageable;
import org.bgerp.model.base.IdTitle;

import ru.bgcrm.dao.AddressDAO;
import ru.bgcrm.model.BGException;
import ru.bgcrm.model.BGIllegalArgumentException;
import ru.bgcrm.model.BGMessageException;
import ru.bgcrm.model.Pair;
import ru.bgcrm.model.ParamList;
import ru.bgcrm.model.param.ParameterAddressValue;
import ru.bgcrm.model.param.ParameterEmailValue;
import ru.bgcrm.model.param.ParameterPhoneValue;
import ru.bgcrm.model.param.ParameterPhoneValueItem;
import ru.bgcrm.model.param.ParameterSearchedObject;
import ru.bgcrm.model.param.address.AddressHouse;
import ru.bgcrm.model.user.User;
import ru.bgcrm.plugin.bgbilling.Plugin;
import ru.bgcrm.plugin.bgbilling.proto.dao.ContractDAO;
import ru.bgcrm.plugin.bgbilling.proto.dao.ContractDAO.SearchOptions;
import ru.bgcrm.plugin.bgbilling.proto.dao.ContractHierarchyDAO;
import ru.bgcrm.plugin.bgbilling.proto.dao.ContractObjectDAO;
import ru.bgcrm.plugin.bgbilling.proto.dao.ContractObjectParamDAO;
import ru.bgcrm.plugin.bgbilling.proto.dao.ContractParamDAO;
import ru.bgcrm.plugin.bgbilling.proto.dao.ContractScriptDAO;
import ru.bgcrm.plugin.bgbilling.proto.dao.ContractServiceDAO;
import ru.bgcrm.plugin.bgbilling.proto.dao.ContractStatusDAO;
import ru.bgcrm.plugin.bgbilling.proto.dao.DirectoryDAO;
import ru.bgcrm.plugin.bgbilling.proto.model.Contract;
import ru.bgcrm.plugin.bgbilling.proto.model.ContractFace;
import ru.bgcrm.plugin.bgbilling.proto.model.ContractMode;
import ru.bgcrm.plugin.bgbilling.proto.model.ContractObjectParameter;
import ru.bgcrm.plugin.bgbilling.proto.model.ContractParameter;
import ru.bgcrm.plugin.bgbilling.proto.model.ContractService;
import ru.bgcrm.plugin.bgbilling.proto.model.ParamAddressValue;
import ru.bgcrm.plugin.bgbilling.proto.model.ParameterType;
import ru.bgcrm.plugin.bgbilling.proto.model.limit.LimitChangeTask;
import ru.bgcrm.plugin.bgbilling.proto.model.limit.LimitLogItem;
import ru.bgcrm.plugin.bgbilling.proto.model.script.ContractScriptLogItem;
import ru.bgcrm.plugin.bgbilling.struts.action.BaseAction;
import ru.bgcrm.servlet.ActionServlet.Action;
import ru.bgcrm.struts.form.DynActionForm;
import ru.bgcrm.struts.form.Response;
import ru.bgcrm.util.TimeUtils;
import ru.bgcrm.util.Utils;
import ru.bgcrm.util.sql.ConnectionSet;

@Action(path = "/user/plugin/bgbilling/proto/contract")
public class ContractAction extends BaseAction {
    private static final String PATH_JSP = Plugin.PATH_JSP_USER;
    private static final String PATH_JSP_CONTRACT = PATH_JSP + "/contract";

    public ActionForward searchContract(DynActionForm form, ConnectionSet conSet) throws Exception {
        String searchBy = form.getParam("searchBy");
        String billingId = form.getParam("billingId");

        String searchBySuffix = form.getParam("searchBySuffix");
        if (Utils.notBlankString(searchBySuffix)) {
            searchBy += searchBySuffix;
        }

        boolean showClosed = form.getParamBoolean("show_closed", false);
        boolean showSub = form.getParamBoolean("show_sub", false);
        boolean showHidden = form.getParamBoolean("show_invisible", false);
        SearchOptions searchOptions = new SearchOptions(showHidden, showClosed, showSub);

        User user = form.getUser();

        if (Utils.notBlankString(searchBy)) {
            ContractDAO contractDAO = ContractDAO.getInstance(user, billingId);

            if ("address".equals(searchBy)) {
                Set<Integer> addressParamIds = Utils.toIntegerSet(setup.get(Plugin.ID + ":search.contract.param.address.paramIds"));
                Pageable<ParameterSearchedObject<Contract>> res = new Pageable<>(form);
                contractDAO.searchContractByAddressParam(res, searchOptions, addressParamIds, form.getParamInt("streetId"),
                        form.getParamInt("houseId"), form.getParam("house"), form.getParam("flat"), form.getParam("room"));
            } else if ("addressObject".equals(searchBy)) {
                Pageable<ParameterSearchedObject<Contract>> result = new Pageable<>(form);
                contractDAO.searchContractByObjectAddressParam(result, searchOptions, null,
                        form.getParamInt("streetId"), form.getParam("house"), form.getParam("flat"), form.getParam("room"));
            } else if ("id".equals(searchBy)) {
                Pageable<IdTitle> result = new Pageable<IdTitle>(form);
                Contract contract = contractDAO.getContractById(form.getParamInt("id"));
                if (contract != null) {
                    result.getList().add(new IdTitle(contract.getId(), contract.getTitle()));
                }
            } else if ("title".equals(searchBy) || "comment".equals(searchBy)) {
                Pageable<IdTitle> result = new Pageable<IdTitle>(form);
                contractDAO.searchContractByTitleComment(result, form.getParam("title"), form.getParam("comment"),
                        searchOptions);
            } else if (searchBy.equals("parameter_text")) {
                Pageable<Contract> result = new Pageable<Contract>(form);
                contractDAO.searchContractByTextParam(result, searchOptions,
                        getParasmIdsSet(form), form.getParam("value"));
            } else if (searchBy.equals("parameter_date")) {
                Pageable<Contract> result = new Pageable<Contract>(form);
                contractDAO.searchContractByDateParam(result, searchOptions,
                        getParasmIdsSet(form),
                        form.getParamDate("date_from"), form.getParamDate("date_to"));
            } else if (searchBy.equals("parameter_phone")) {
                Pageable<Contract> result = new Pageable<Contract>(form);
                contractDAO.searchContractByPhoneParam(result, searchOptions,
                        getParasmIdsSet(form), form.getParam("value"));
            }
        }

        return html(conSet, form, PATH_JSP + "/search_contract_result.jsp");
    }

    private Set<Integer> getParasmIdsSet(DynActionForm form) throws BGMessageException {
        String[] vals = form.getParamArray("paramIds");
        if (vals == null) {
            return getParamListImpl(form).stream()
                    .map(i -> i.getId())
                    .collect(Collectors.toSet());
        }
        return Arrays.stream(vals).map(i -> Utils.parseInt(i, -1)).collect(Collectors.toSet());
    }

    private List<ContractParameter> filterParameterList(List<ContractParameter> contractParameterList,
            Set<Integer> requiredParameterIds) {
        if (requiredParameterIds.isEmpty()) {
            return contractParameterList;
        }

        List<ContractParameter> filteredContractParameterList = new ArrayList<ContractParameter>();

        for (ContractParameter contractParameter : contractParameterList) {
            if (requiredParameterIds.contains(contractParameter.getParamId())) {
                filteredContractParameterList.add(contractParameter);
            }
        }

        return filteredContractParameterList;
    }

    public ActionForward parameterList(DynActionForm form, ConnectionSet conSet) throws Exception {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");
        Set<Integer> requiredParameterIds = Utils.toIntegerSet(form.getParam("requiredParameterIds", ""));

        ContractParamDAO paramDAO = new ContractParamDAO(form.getUser(), billingId);

        Pair<ParamList, List<ContractParameter>> parameterListWithDir = paramDAO.getParameterListWithDir(contractId,
                true, form.getParamBoolean("onlyFromGroup", false));

        form.getResponse().setData("group", parameterListWithDir.getFirst());
        form.getResponse().setData("contractParameterList",
                filterParameterList(parameterListWithDir.getSecond(), requiredParameterIds));

        return html(conSet, form, PATH_JSP_CONTRACT + "/parameter_list.jsp");
    }

    public ActionForward parameterGet(DynActionForm form, ConnectionSet conSet) throws Exception {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");
        Integer paramId = form.getParamInt("paramId");
        int paramType = form.getParamInt("paramType");

        ContractParamDAO paramDAO = new ContractParamDAO(form.getUser(), billingId);

        Response resp = form.getResponse();

        if (paramType <= 0) {
            throw new BGMessageExceptionTransparent("Параметр не поддерживается для редактирования");
        }

        switch (paramType) {
            case ParameterType.ContractType.TYPE_TEXT: {
                break;
            }
            case ParameterType.ContractType.TYPE_ADDRESS: {
                ParameterAddressValue addressValue = ContractParamDAO
                        .toCrmObject(paramDAO.getAddressParam(contractId, paramId), conSet.getConnection());
                if (addressValue != null) {
                    int houseId = addressValue.getHouseId();

                    AddressHouse house = new AddressDAO(conSet.getConnection()).getAddressHouse(houseId, true, true, true);
                    if (house != null) {
                        resp.setData("house", house);
                    }
                }
                resp.setData("address", addressValue);
                break;
            }
            case ParameterType.ContractType.TYPE_DATE: {
                /*
                 * if( Utils.notBlankString( parameter.getValue() ) ) {
                 * resp.setData( "dateValue", new SimpleDateFormat( "yyyy-MM-dd"
                 * ).format( TimeUtils.parseDateWithPattern(
                 * parameter.getValue(), TimeUtils.PATTERN_DDMMYYYY ) ) ); }
                 */
                break;
            }
            case ParameterType.ContractType.TYPE_LIST: {
                resp.setData("value", paramDAO.getListParamValue(contractId, paramId));
                break;
            }
            case ParameterType.ContractType.TYPE_PHONE: {
                form.setResponseData("value", new ParameterPhoneValue(paramDAO.getPhoneParam(contractId, paramId)));
                break;
            }
            case ParameterType.ContractType.TYPE_EMAIL: {
                resp.setData("emails", paramDAO.getEmailParam(contractId, paramId));
                break;
            }
            default: {
                break;
            }
        }

        return html(conSet, form, PATH_JSP + "/contract/parameter_editor.jsp");
    }

    public ActionForward parameterUpdate(DynActionForm form, ConnectionSet conSet)
            throws BGMessageException {
        String billingId = form.getParam("billingId");
        int contractId = form.getParamInt("contractId");
        int paramBillingId = form.getParamInt("paramId");
        int parameterType = form.getParamInt("paramType");

        Set<Integer> allowedParamIds = Utils.toIntegerSet(form.getPermission().get("parameterIds"));
        if (!allowedParamIds.isEmpty() && !allowedParamIds.contains(paramBillingId))
            throw new BGMessageExceptionTransparent("Параметр с кодом " + paramBillingId + " запрещен для изменения.");

        ContractParamDAO paramDAO = new ContractParamDAO(form.getUser(), billingId);
        switch (parameterType) {
            case ParameterType.ContractType.TYPE_FLAG: {
                paramDAO.updateFlagParameter(contractId, paramBillingId, form.getParamBoolean("value", false));
                break;
            }
            case ParameterType.ContractType.TYPE_TEXT: {
                paramDAO.updateTextParameter(contractId, paramBillingId, form.getParam("value"));
                break;
            }
            case ParameterType.ContractType.TYPE_ADDRESS: {
                ParamAddressValue address = new ParamAddressValue();

                address.setStreetId(form.getParamInt("streetId "));
                address.setHouseId(form.getParamInt("houseId"));
                address.setStreetTitle(form.getParam("streetTitle"));
                address.setHouse(form.getParam("house"));
                address.setFlat(form.getParam("flat"));
                address.setRoom(form.getParam("room "));
                address.setPod(form.getParam("pod"));
                address.setFloor(form.getParam("floor"));
                address.setComment(form.getParam("comment"));

                paramDAO.updateAddressParameter(contractId, paramBillingId, address);
                break;
            }
            case ParameterType.ContractType.TYPE_DATE: {
                paramDAO.updateDateParameter(contractId, paramBillingId, form.getParam("value"));
                break;
            }
            case ParameterType.ContractType.TYPE_LIST: {
                paramDAO.updateListParameter(contractId, paramBillingId, form.getParam("value"));
                break;
            }
            case ParameterType.ContractType.TYPE_PHONE: {
                ParameterPhoneValue phoneValue = new ParameterPhoneValue();

                Iterator<String> phones = form.getParamValuesListStr("phone").iterator();
                Iterator<String> comments = form.getParamValuesListStr("comment").iterator();
                while (phones.hasNext())
                    phoneValue.addItem(new ParameterPhoneValueItem(phones.next(), comments.next()));

                paramDAO.updatePhoneParameter(contractId, paramBillingId, phoneValue);
                break;
            }
            case ParameterType.ContractType.TYPE_EMAIL: {
                List<ParameterEmailValue> emails = new ArrayList<ParameterEmailValue>();

                for (String mail : Utils.toList(form.getParam("emails"), "\n")) {
                    try {
                        InternetAddress addr = InternetAddress.parse(mail)[0];
                        emails.add(new ParameterEmailValue(addr.getAddress(), addr.getPersonal()));
                    } catch (AddressException e) {
                        throw new BGException("Некорректный адрес: " + mail, e);
                    }
                }

                paramDAO.updateEmailParameter(contractId, paramBillingId, emails);
                break;
            }
            default: {
                break;
            }
        }

        return json(conSet, form);
    }

    public ActionForward parameterGroupUpdate(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");
        int paramGroupId = form.getParamInt("paramGroupId");

        new ContractParamDAO(form.getUser(), billingId).updateParameterGroup(contractId, paramGroupId);

        return json(conSet, form);
    }

    public ActionForward objectLinkList(DynActionForm form, ConnectionSet conSet) throws Exception {
        Integer contractId = form.getParamInt("contractId");
        Integer cityId = form.getParamInt("cityId");

        /* Set<DeviceInfo.BaseLink> baseLinks = new DeviceInfo().getDeviceInfo(contractId, cityId);

        form.getResponse().setData("links", baseLinks); */

        return html(conSet, form, PATH_JSP_CONTRACT + "/object_link_list.jsp");
    }

    public ActionForward additionalActionList(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        ContractDAO crmDAO = ContractDAO.getInstance(form.getUser(), billingId);

        form.getResponse().setData("additionalActionList", crmDAO.additionalActionList(contractId));

        return html(conSet, form, PATH_JSP_CONTRACT + "/additional_action_list.jsp");
    }

    public ActionForward executeAdditionalAction(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");
        Integer actionId = form.getParamInt("actionId");

        ContractDAO crmDAO = ContractDAO.getInstance(form.getUser(), billingId);

        form.getResponse().setData("executeResult", crmDAO.executeAdditionalAction(contractId, actionId));
        form.getResponse().setData("additionalActionList", crmDAO.additionalActionList(contractId));

        return html(conSet, form, PATH_JSP_CONTRACT + "/additional_action_list.jsp");
    }

    public ActionForward groupList(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        Pair<List<IdTitle>, Set<Integer>> groupsGet = ContractDAO.getInstance(form.getUser(), billingId).groupsGet(contractId);

        form.getResponse().setData("groupList", groupsGet.getFirst());
        form.getResponse().setData("selectedGroupIds", groupsGet.getSecond());

        return html(conSet, form, PATH_JSP_CONTRACT + "/group_list.jsp");
    }

    @SuppressWarnings("unchecked")
    public ActionForward updateGroups(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");
        Set<Integer> groupIds = form.getParamValues("groupId");
        // String command = form.getParam( "command" );

        ContractDAO contractDao = ContractDAO.getInstance(form.getUser(), billingId);
        Set<Integer> currentGroups = contractDao.groupsGet(contractId).getSecond();

        for (Integer deleteGroup : (Iterable<Integer>) CollectionUtils.subtract(currentGroups, groupIds)) {
            contractDao.updateGroup("del", contractId, deleteGroup);
        }
        for (Integer addGroup : (Iterable<Integer>) CollectionUtils.subtract(groupIds, currentGroups)) {
            contractDao.updateGroup("add", contractId, addGroup);
        }

        return json(conSet, form);
    }

    public ActionForward memoList(DynActionForm form, ConnectionSet conSet) throws Exception {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        ContractDAO contractDAO = ContractDAO.getInstance(form.getUser(), billingId);
        form.getResponse().setData("memoList", contractDAO.getMemoList(contractId));

        return html(conSet, form, PATH_JSP_CONTRACT + "/memo/memo_list.jsp");
    }

    public ActionForward getMemo(DynActionForm form, ConnectionSet conSet) throws Exception {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        if (form.getId() > 0) {
            form.getResponse().setData("memo",
                    ContractDAO.getInstance(form.getUser(), billingId).getMemo(contractId, form.getId()));
        }

        return html(conSet, form, PATH_JSP_CONTRACT + "/memo/memo_edit.jsp");
    }

    public ActionForward updateMemo(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");
        Integer memoId = form.getParamInt("id", 0);
        String memoTitle = form.getParam("title");
        String memoText = form.getParam("text");

        ContractDAO crmDAO = ContractDAO.getInstance(form.getUser(), billingId);
        crmDAO.updateMemo(contractId, memoId, memoTitle, memoText);

        return json(conSet, form);
    }

    public ActionForward deleteMemo(DynActionForm form, ConnectionSet conSet) throws BGMessageException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");
        Integer memoId = form.getParamInt("id", 0);

        if (memoId <= 0) {
            throw new BGIllegalArgumentException();
        }

        ContractDAO crmDAO = ContractDAO.getInstance(form.getUser(), billingId);
        crmDAO.deleteMemo(contractId, memoId);

        return json(conSet, form);
    }

    public ActionForward contractObjectList(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        ContractObjectDAO contractObjectDAO = new ContractObjectDAO(form.getUser(), billingId);
        form.getResponse().setData("objectList", contractObjectDAO.getContractObjects(contractId));

        return html(conSet, form, PATH_JSP_CONTRACT + "/object/object_list.jsp");
    }

    public ActionForward getContractObject(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer objectId = form.getParamInt("objectId");

        ContractObjectDAO contractObjectDAO = new ContractObjectDAO(form.getUser(), billingId);
        form.getResponse().setData("object", contractObjectDAO.getContractObject(objectId));

        return html(conSet, form, PATH_JSP_CONTRACT + "/object/object_editor.jsp");
    }

    public ActionForward deleteContractObject(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");
        Integer objectId = form.getParamInt("objectId");

        ContractObjectDAO contractObjectDAO = new ContractObjectDAO(form.getUser(), billingId);
        contractObjectDAO.deleteContractObject(contractId, objectId);

        return json(conSet, form);
    }

    public ActionForward updateContractObject(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        int contractId = form.getParamInt("contractId");
        String title = form.getParam("title");
        int objectId = form.getParamInt("objectId");
        int typeId = form.getParamInt("typeId");
        Date dateFrom = form.getParamDate("dateFrom");
        Date dateTo = form.getParamDate("dateTo");


        ContractObjectDAO contractObjectDAO = new ContractObjectDAO(form.getUser(), billingId);
        contractObjectDAO.updateContractObject(objectId, title, dateFrom, dateTo, typeId, contractId);

        return json(conSet, form);
    }

    public ActionForward contractObjectParameterList(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer objectId = form.getParamInt("objectId");

        ContractObjectParamDAO paramDAO = new ContractObjectParamDAO(form.getUser(), billingId);
        form.getResponse().setData("parameterList", paramDAO.getParameterList(objectId));

        return html(conSet, form, PATH_JSP_CONTRACT + "/object/object_parameter_list.jsp");
    }

    public ActionForward getObjectParameter(DynActionForm form, ConnectionSet conSet) throws Exception {
        String billingId = form.getParam("billingId");
        Integer objectId = form.getParamInt("objectId");
        Integer paramId = form.getParamInt("paramId");

        ContractObjectParamDAO paramDAO = new ContractObjectParamDAO(form.getUser(), billingId);
        ContractObjectParameter parameter = paramDAO.getParameter(objectId, paramId);

        form.getResponse().setData("parameter", parameter);

        int paramType = parameter.getTypeId();
        if (paramType <= 0) {
            throw new BGMessageExceptionTransparent("Параметр не поддерживается для редактирования");
        }

        switch (paramType) {
            case ParameterType.ContractObjectType.TYPE_TEXT: {
                break;
            }

            case ParameterType.ContractObjectType.TYPE_ADDRESS: {
                ParameterAddressValue addressValue = ContractObjectParamDAO
                        .toCrmObject(paramDAO.getAddressParam(objectId, paramId), conSet.getConnection());
                if (addressValue != null) {
                    int houseId = addressValue.getHouseId();

                    AddressHouse house = new AddressDAO(conSet.getConnection()).getAddressHouse(houseId, true, true, true);
                    if (house != null) {
                        form.getResponse().setData("house", house);
                    }
                }

                form.getResponse().setData("address", addressValue);
                break;
            }

            case ParameterType.ContractObjectType.TYPE_DATE: {
                if (Utils.notBlankString(parameter.getValue())) {
                    form.getResponse().setData("dateValue", new SimpleDateFormat("yyyy-MM-dd")
                            .format(TimeUtils.parse(parameter.getValue(), TimeUtils.PATTERN_DDMMYYYY)));
                }
                break;
            }

            case ParameterType.ContractObjectType.TYPE_LIST: {
                form.getResponse().setData("valueList", paramDAO.getListParam(objectId, paramId));
                break;
            }
        }

        return html(conSet, form, PATH_JSP_CONTRACT + "/object/object_parameter_editor.jsp");
    }

    public ActionForward updateObjectParameter(DynActionForm form, ConnectionSet conSet) throws Exception {
        String billingId = form.getParam("billingId");
        Integer objectId = form.getParamInt("objectId");
        Integer paramBillingId = form.getParamInt("paramId");
        Integer parameterType = form.getParamInt("paramType");
        Integer contractId = form.getParamInt("contractId");

        ContractObjectParamDAO paramDAO = new ContractObjectParamDAO(form.getUser(), billingId);

        switch (parameterType) {
            case ParameterType.ContractObjectType.TYPE_TEXT:
                paramDAO.updateTextParameter(contractId,objectId, paramBillingId, form.getParam("textValue"));
                break;

            case ParameterType.ContractObjectType.TYPE_ADDRESS:
                ParamAddressValue address = new ParamAddressValue();

                address.setStreetId(form.getParamInt("streetId "));
                address.setHouseId(form.getParamInt("houseId"));
                address.setStreetTitle(form.getParam("streetTitle"));
                address.setHouse(form.getParam("house"));
                address.setFlat(form.getParam("flat"));
                address.setRoom(form.getParam("room "));
                address.setPod(form.getParam("pod"));
                address.setFloor(form.getParam("floor"));
                address.setComment(form.getParam("comment"));

                paramDAO.updateAddressParameter(contractId, objectId, paramBillingId, address);
                break;

            case ParameterType.ContractObjectType.TYPE_DATE:
                paramDAO.updateDateParameter(contractId, objectId, paramBillingId, form.getParam("dateValue"));
                break;

            case ParameterType.ContractObjectType.TYPE_LIST:
                paramDAO.updateListParameter(contractId, objectId, paramBillingId, form.getParam("listValueId"));
                break;

            default:
                break;
        }

        return json(conSet, form);
    }

    public ActionForward contractObjectModuleInfo(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer objectId = form.getParamInt("objectId");

        ContractObjectDAO dao = new ContractObjectDAO(form.getUser(), billingId);
        form.getResponse().setData("moduleInfo", dao.contractObjectModuleList(objectId));

        return json(conSet, form);
    }

    public ActionForward contractObjectModuleSummaryTable(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer objectId = form.getParamInt("objectId");

        ContractObjectDAO dao = new ContractObjectDAO(form.getUser(), billingId);
        form.getResponse().setData("moduleInfo", dao.contractObjectModuleList(objectId));

        return html(conSet, form, PATH_JSP_CONTRACT + "/object/object_module_summary_table.jsp");
    }

    public ActionForward contractSubcontractList(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        ContractHierarchyDAO crmDAO = new ContractHierarchyDAO(form.getUser(), billingId);
        form.getResponse().setData("subContractList", crmDAO.contractSubcontractList(contractId));
        form.getResponse().setData("superContract", crmDAO.contractSupercontract(contractId));

        return html(conSet, form, PATH_JSP_CONTRACT + "/subcontract_list.jsp");
    }

    public ActionForward scriptList(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        form.getResponse().setData("scriptList",
                new ContractScriptDAO(form.getUser(), billingId).contractScriptList(contractId));

        form.getHttpRequest().setAttribute("contractInfo", ContractDAO.getInstance(form.getUser(), billingId).getContractInfo(contractId));

        return html(conSet, form, PATH_JSP_CONTRACT + "/script/script_list.jsp");
    }

    public ActionForward getScript(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer scriptId = form.getParamInt("scriptId");

        ContractScriptDAO crmDAO = new ContractScriptDAO(form.getUser(), billingId);
        form.getResponse().setData("script", crmDAO.contractScriptGet(scriptId));
        form.getResponse().setData("scriptTypeList", new DirectoryDAO(form.getUser(), billingId).scriptTypeList());

        return html(conSet, form, PATH_JSP_CONTRACT + "/script/script_editor.jsp");
    }

    public ActionForward scriptLog(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        String dateFrom = form.getParam("dateFrom");
        String dateTo = form.getParam("dateTo");

        Pageable<ContractScriptLogItem> result = new Pageable<ContractScriptLogItem>(form);

        ContractScriptDAO crmDAO = new ContractScriptDAO(form.getUser(), billingId);
        crmDAO.contractScriptLogList(result, contractId, dateFrom, dateTo);

        return html(conSet, form, PATH_JSP_CONTRACT + "/script/script_log.jsp");
    }

    public ActionForward deleteScript(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer scriptId = form.getParamInt("scriptId");

        ContractScriptDAO crmDAO = new ContractScriptDAO(form.getUser(), billingId);
        crmDAO.deleteContractScript(scriptId);

        return json(conSet, form);
    }

    public ActionForward updateScript(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");
        Integer scriptId = form.getParamInt("scriptId");
        Integer scriptTypeId = form.getParamInt("scriptTypeId");
        String comment = form.getParam("comment");
        String dateFrom = form.getParam("dateFrom");
        String dateTo = form.getParam("dateTo");

        ContractScriptDAO crmDAO = new ContractScriptDAO(form.getUser(), billingId);
        crmDAO.updateContractScript(contractId, scriptId, scriptTypeId, comment, dateFrom, dateTo);

        return json(conSet, form);
    }

    public ActionForward faceLog(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        ContractDAO contractDao = ContractDAO.getInstance(form.getUser(), billingId);
        contractDao.faceLog(new Pageable<ContractFace>(form), contractId);

        form.getResponse().setData("contractInfo", ContractDAO.getInstance(form.getUser(), billingId).getContractInfo(contractId));

        return html(conSet, form, PATH_JSP_CONTRACT + "/face_log.jsp");
    }

    public ActionForward updateFace(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        ContractDAO.getInstance(form.getUser(), billingId).updateFace(contractId, form.getParamInt("value"));

        return json(conSet, form);
    }

    public ActionForward modeLog(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        ContractDAO contractDao = ContractDAO.getInstance(form.getUser(), billingId);
        contractDao.modeLog(new Pageable<ContractMode>(form), contractId);

        form.getResponse().setData("contractInfo", contractDao.getContractInfo(contractId));

        return html(conSet, form, PATH_JSP_CONTRACT + "/mode_log.jsp");
    }

    public ActionForward updateMode(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        ContractDAO.getInstance(form.getUser(), billingId).updateMode(contractId, form.getParamInt("value"));

        return json(conSet, form);
    }

    public ActionForward moduleList(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        Pair<List<IdTitle>, List<IdTitle>> pair = ContractDAO.getInstance(form.getUser(), billingId).moduleList(contractId);
        form.getResponse().setData("selectedList", pair.getFirst());
        form.getResponse().setData("availableList", pair.getSecond());

        return html(conSet, form, PATH_JSP_CONTRACT + "/module_list.jsp");
    }

    public ActionForward updateModules(DynActionForm form, ConnectionSet conSet) throws BGMessageException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");
        Set<Integer> moduleIds = form.getParamValues("moduleId");
        String command = form.getParam("command");

        if (Utils.notBlankString(command)) {
            ContractDAO contractDao = ContractDAO.getInstance(form.getUser(), billingId);
            for (Integer moduleId : moduleIds) {
                contractDao.updateModule(contractId, moduleId, command);
            }
        }

        return json(conSet, form);
    }

    public ActionForward status(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        DirectoryDAO directoryDAO = new DirectoryDAO(form.getUser(), billingId);
        Map<Integer, String> statusTitleMap = directoryDAO.getContractStatusList(false).stream()
                .collect(Collectors.toMap(IdTitle::getId, IdTitle::getTitle));

        ContractStatusDAO statusDao = new ContractStatusDAO(form.getUser(), billingId);
        form.getResponse().setData("statusList", statusDao.statusList(contractId, statusTitleMap));
        form.getResponse().setData("statusLog", statusDao.statusLog(contractId, statusTitleMap));
        form.getResponse().setData("availableStatusList", directoryDAO.getContractStatusList(true));

        form.getHttpRequest().setAttribute("contractInfo", ContractDAO.getInstance(form.getUser(), billingId).getContractInfo(contractId));

        return html(conSet, form, PATH_JSP_CONTRACT + "/status.jsp");
    }

    public ActionForward updateStatus(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        new ContractStatusDAO(form.getUser(), billingId).updateStatus(contractId, form.getParamInt("statusId"),
                form.getParamDate("dateFrom"), form.getParamDate("dateTo"), form.getParam("comment"));

        return json(conSet, form);
    }

    public ActionForward limit(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        Pageable<LimitLogItem> limitList = new Pageable<LimitLogItem>(form);
        List<LimitChangeTask> taskList = new ArrayList<LimitChangeTask>();

        BigDecimal limit = ContractDAO.getInstance(form.getUser(), billingId).limit(contractId, limitList, taskList);

        form.getResponse().setData("limit", limit);
        form.getResponse().setData("taskList", taskList);

        return html(conSet, form, PATH_JSP_CONTRACT + "/limit.jsp");
    }

    public ActionForward updateLimit(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        ContractDAO.getInstance(form.getUser(), billingId).updateLimit(contractId,
                Utils.parseBigDecimal(form.getParam("value")), form.getParamInt("period"),
                form.getParam("comment", ""));

        return json(conSet, form);
    }

    public ActionForward deleteLimitTask(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        ContractDAO.getInstance(form.getUser(), billingId).deleteLimitTask(contractId, form.getId());

        return json(conSet, form);
    }

    public ActionForward contractCards(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        ContractDAO contractDao = ContractDAO.getInstance(form.getUser(), billingId);
        form.getResponse().setData("cardTypeList", contractDao.getContractCardTypes(contractId));
        form.getResponse().setData("fullCard", contractDao.getContractFullCard(contractId));

        return html(conSet, form, PATH_JSP_CONTRACT + "/cards.jsp");
    }

    public ActionForward getContractCard(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        int contractId = form.getParamInt("contractId");
        String cardType = form.getParam("cardType");

        try {
            OutputStream out = form.getHttpResponse().getOutputStream();
            Utils.setFileNameHeaders(form.getHttpResponse(), "card.pdf");
            out.write(ContractDAO.getInstance(form.getUser(), billingId).getContractCard2Pdf(contractId, cardType));
        } catch (Exception ex) {
            throw new BGException(ex);
        }

        return null;
    }

    public ActionForward serviceList(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        int contractId = form.getParamInt("contractId");
        int moduleId = form.getParamInt("moduleId");

        form.getResponse().setData("list",
                new ContractServiceDAO(form.getUser(), billingId).getContractServiceList(contractId, moduleId));

        return html(conSet, form, PATH_JSP_CONTRACT + "/service/list.jsp");
    }

    public ActionForward serviceEdit(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        int contractId = form.getParamInt("contractId");
        int moduleId = form.getParamInt("moduleId");

        form.getResponse().setData("pair",
                new ContractServiceDAO(form.getUser(), billingId).getContractService(contractId, moduleId, form.getId(),
                        form.getId() > 0 ? false : form.getParamBoolean("onlyUsing", true)));

        return html(conSet, form, PATH_JSP_CONTRACT + "/service/edit.jsp");
    }

    public ActionForward serviceUpdate(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        int contractId = form.getParamInt("contractId");

        ContractServiceDAO serviceDAO = new ContractServiceDAO(form.getUser(), billingId);

        for (int serviceId : form.getParamValuesList("serviceId")) {
            ContractService service = new ContractService();
            service.setId(form.getId());
            service.setContractId(contractId);
            service.setServiceId(serviceId);
            service.setDateFrom(form.getParamDate("dateFrom"));
            service.setDateTo(form.getParamDate("dateTo"));
            service.setComment(form.getParam("comment"));

            serviceDAO.updateContractService(service);
        }

        return json(conSet, form);
    }

    public ActionForward serviceDelete(DynActionForm form, ConnectionSet conSet) throws BGException {
        String billingId = form.getParam("billingId");
        int contractId = form.getParamInt("contractId");

        new ContractServiceDAO(form.getUser(), billingId).deleteContractService(contractId, form.getId());

        return json(conSet, form);
    }

    // далее сомнительные функции, которые не очень идеологически ложатся в этот
    // класс

    public ActionForward getContractStatisticPassword(DynActionForm form, ConnectionSet conSet) throws Exception {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        ContractDAO contractDAO = ContractDAO.getInstance(form.getUser(), billingId);
        form.getResponse().setData("password", contractDAO.getContractStatisticPassword(contractId));

        return json(conSet, form);
    }

    public ActionForward addressList(DynActionForm form, ConnectionSet conSet) throws Exception {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        ContractDAO contractDAO = ContractDAO.getInstance(form.getUser(), billingId);

        form.getResponse().setData("contractAddressList", contractDAO.getContractAddress(contractId));

        return html(conSet, form, PATH_JSP + "/crm/contract_address_list.jsp");
    }

    public ActionForward bgbillingOpenContract(DynActionForm form, ConnectionSet conSet) throws Exception {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");

        ContractDAO contractDAO = ContractDAO.getInstance(form.getUser(), billingId);
        contractDAO.bgbillingOpenContract(contractId);

        return json(conSet, form);
    }

    public ActionForward bgbillingUpdateContractTitleAndComment(DynActionForm form, ConnectionSet conSet) throws Exception {
        String billingId = form.getParam("billingId");
        Integer contractId = form.getParamInt("contractId");
        String comment = form.getParam("comment");

        ContractDAO contractDAO = ContractDAO.getInstance(form.getUser(), billingId);
        contractDAO.bgbillingUpdateContractTitleAndComment(contractId, comment, 0);

        return json(conSet, form);
    }

    public ActionForward bgbillingGetContractPatternList(DynActionForm form, ConnectionSet conSet) throws Exception {
        String billingId = form.getParam("billingId");

        ContractDAO contractDAO = ContractDAO.getInstance(form.getUser(), billingId);
        form.getResponse().setData("patterns", contractDAO.bgbillingGetContractPatternList());

        return json(conSet, form);
    }

    public ActionForward getSubContractList(DynActionForm form, ConnectionSet conSet) throws Exception {
        String billingId = form.getParam("billingId");
        int contractId = form.getParamInt("contractId");

        ContractHierarchyDAO contractDAO = new ContractHierarchyDAO(form.getUser(), billingId);
        form.getResponse().setData("subContractList", contractDAO.getSubContracts(contractId));

        return json(conSet, form);
    }

    public ActionForward openContract(DynActionForm form, ConnectionSet conSet) throws Exception {
        String billingId = form.getParam("billingId");

        if (billingId == null) {
            throw new BGMessageExceptionTransparent("Не указан параметр запроса billingId");
        }

        if (billingId.length() == 0) {
            throw new BGMessageExceptionTransparent("Не указано значение параметра запроса billingId");
        }

        form.getResponse().setData("openContract", ContractDAO.getInstance(form.getUser(), billingId).openContract());

        return json(conSet, form);
    }

    public ActionForward getStreetsByCity(DynActionForm form, ConnectionSet conSet) throws Exception {
        String billingId = form.getParam("billingId");

        if (billingId == null) {
            throw new BGMessageExceptionTransparent("Не указан параметр запроса billingId");
        }

        if (billingId.length() == 0) {
            throw new BGMessageExceptionTransparent("Не указано значение параметра запроса billingId");
        }

        int cityId = form.getParamInt("cityId");

        if (cityId == 0) {
            throw new BGMessageExceptionTransparent("Не указано значение параметра запроса cityId");
        }

        form.getResponse().setData("streets", ContractDAO.getInstance(form.getUser(), billingId).getStreetsByCity(cityId));

        return json(conSet, form);
    }

    public ActionForward getParamList(DynActionForm form, ConnectionSet conSet) throws BGMessageException {
        form.getResponse().setData("paramType", form.getParamInt("paramType"));
        List<IdTitle> list = getParamListImpl(form);
        form.getResponse().setData("paramList", list);
        return html(conSet, form, PATH_JSP + "/search_param_list.jsp");
    }

    private List<IdTitle> getParamListImpl(DynActionForm form) throws BGMessageException {
        int type = form.getParamInt("paramType");
        String billingId = form.getParam("billingId");
        if (billingId == null) {
            throw new BGMessageExceptionTransparent("Не указан параметр запроса billingId");
        }
        return ContractDAO.getInstance(form.getUser(), billingId).getParameterList(type);
    }
}
