<%@ page contentType="text/html; charset=UTF-8"%>
<%@ include file="/WEB-INF/jspf/taglibs.jsp"%>

<c:set var="uiid" value="${u:uiid()}"/>

<html:form action="/user/process" styleId="${uiid}" styleClass="center1020">
	<html:hidden property="id" />
	<input type="hidden" name="action" value="processStatusHistory" />

	<button class="btn-white mb1" type="button" onclick="$$.ajax.load('${form.returnUrl}', $('#${uiid}').parent());">${l.l('Close')}</button>

	<table class="data">
		<tr>
			<td>${l.l('Status')}</td>
			<td>${l.l('Комментарий')}</td>
			<td>${l.l('Время')}</td>
			<td>${l.l('User')}</td>
		</tr>
		<c:forEach var="item" items="${frd.list}">
			<tr>
				<td>${item.statusTitle}</td>
				<td>${item.comment}</td>
				<td>${tu.format( item.date, 'ymdhms' )}</td>
				<td>${item.userTitle}</td>
			</tr>
		</c:forEach>
	</table>

</html:form>

<shell:state text="${l.l('История статусов')}"/>