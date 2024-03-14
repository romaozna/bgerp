<%@ page contentType="text/html; charset=UTF-8"%>
<%@ include file="/WEB-INF/jspf/taglibs.jsp"%>

<div class="pt1 pb1">
	<c:set var="closeEditor">$$.ajax.load('${form.returnUrl}', $('#${form.returnChildUiid}').parent());</c:set>
	<c:set var="saveCommand">$$.ajax.post(this).done(() => {${closeEditor}})</c:set>

	<button class="btn-grey mr1" type="button" onclick="${saveCommand}">OK</button>
	<button class="btn-white mr1" type="button" onclick="${closeEditor}">${l.l('Cancel')}</button>
</div>