<%@ page contentType="text/html; charset=UTF-8"%>
<%@ include file="/WEB-INF/jspf/taglibs.jsp"%>

<c:set var="uiid" value="${u:uiid()}"/>

<h1>Редактор</h1>

<html:form action="/user/plugin/bgbilling/proto/contractTariff" styleId="${uiid}" styleClass="in-table-cell">
	<html:hidden property="id"/>
	<html:hidden property="method"/>
	<html:hidden property="returnUrl"/>
	<html:hidden property="billingId"/>
	<html:hidden property="contractId"/>

	<c:set var="reload" value="$$.ajax.load(this.form, $('#${uiid}').parent())"/>
	<c:set var="onclick">onclick="$(this).toggleClass( 'btn-blue btn-white' ); this.value = $(this).hasClass( 'btn-blue' ) ? 1 : 0; ${reload}"</c:set>

	<div style="white-space: nowrap;">
		<button type="button" class="${form.param.showUsed eq '1' ? 'btn-blue' : 'btn-white'}"
			name="showUsed" value="${form.param.showUsed}" ${onclick}>Только используемые</button>

		<button type="button" class="${form.param.useFilter eq '1' ? 'btn-blue' : 'btn-white'} ml1"
			name="useFilter" value="${form.param.useFilter}" ${onclick}>Фильтр по договору</button>

		<c:if test="${ctxPluginManager.pluginMap['bgbilling'].dbInfoManager.dbInfoMap[form.param.billingId].versionCompare( '5.2' ) ge 0}">
			<button type="button" class="${form.param.tariffGroupFilter eq '1' ? 'btn-blue' : 'btn-white'} ml1"
				name="tariffGroupFilter" value="${form.param.tariffGroupFilter}" ${onclick}>Фильтр по группе тарифа</button>
		</c:if>
	</div>
	<div style="width: 100%;" class="pl1">
		<ui:select-single list="${frd.moduleList}" hiddenName="moduleId" value="${form.param.moduleId}" onSelect="${reload}"
			style="width: 100%;" placeholder="Фильтр по модулю"/>
	</div>
</html:form>

<c:set var="contractTariff" value="${frd.contractTariff}"/>

<html:form action="/user/plugin/bgbilling/proto/contractTariff" styleClass="mt1">
	<input type="hidden" name="method" value="updateContractTariff" />
	<html:hidden property="billingId"/>
	<html:hidden property="contractId"/>
	<html:hidden property="id"/>

	<div class="in-table-cell">
		<div style="width: 100%">
			<ui:select-single list="${frd.tariffList}" hiddenName="tariffPlanId" value="${contractTariff.tariffPlanId}"
				style="width: 100%;" placeholder="Тариф"/>
		</div>

		<div style="white-space: nowrap;" class="pl1">
			Позиция:
			<input type="text" style="text-align:center; width:50px" name="position" value="${contractTariff.position}"/>

			Период c
			<ui:date-time paramName="dateFrom" value="${tu.format(contractTariff.dateFrom, 'ymd')}"/>
			по
			<ui:date-time paramName="dateTo" value="${tu.format(contractTariff.dateTo, 'ymd')}"/>
		</div>
	</div>

	<div class="mt1">
		Комментарий: <br>
		<textarea name="comment" rows="10" style="width:100%; resize: vertical;">${contractTariff.comment}</textarea>
	</div>

	<%@ include file="/WEB-INF/jspf/ok_cancel_in_form.jsp"%>
</html:form>