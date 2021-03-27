<%@ page contentType="text/html; charset=UTF-8"%>
<%@ include file="/WEB-INF/jspf/taglibs.jsp"%>

<c:if test="${uri.startsWith('/open/test')}">
	<c:set var="id" value="${su.substringBefore(su.substringAfterLast(uri, '/'), '#')}"/>
	<c:import url="/open/test.do?id=${id}"/>
</c:if>
