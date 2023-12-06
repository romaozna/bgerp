<%@ page contentType="text/html; charset=UTF-8"%>
<%@ include file="/WEB-INF/jspf/taglibs.jsp"%>

<table class="data">
	<tr>
		<td>Биллинг</td>
		<td>ID</td>
		<td>Номер</td>
		<td></td>
	</tr>

	<c:forEach items="${form.response.data.list}" var="link">
		<c:set var="billingId" value="${su.substringAfter( link.linkObjectType, ':' )}"/>
		<c:set var="customerId" value="${form.response.data.customerId}" />
		<tr>
			<td>${ctxPluginManager.pluginMap['bgbilling'].dbInfoManager.dbInfoMap[billingId].title}</td>
			<td>${link.linkObjectId}</td>
			<td width="100%"><a href="#" onclick="$$.bgbilling.contract.open( '${billingId}', '${link.linkObjectId}' ); return false;">${link.linkObjectTitle}</a></td>
		</tr>
	</c:forEach>
</table>
