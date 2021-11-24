<%@ tag body-content="empty" pageEncoding="UTF-8" description="Tag box" %>
<%@ include file="/WEB-INF/jspf/taglibs.jsp" %>

<%@ attribute name="id" description="id of the input, if not mentioned will be generated" %>
<%@ attribute name="inputName" description="name of input element" %>
<%@ attribute name="value" description="current value of the checkbox input" %>
<%@ attribute name="placeholder" description="input placeholder" %>
<%@ attribute name="style" description="CSS styles" %>
<%@ attribute name="choices" description="data to choose from" %>
<%@ attribute name="showOptions" description="show options on focus" %>
<%@ attribute name="url" description="URL for AJAX request to get values for autocomplete" %>

<c:choose>
	<c:when test="${not empty id}">
		<c:set var="uiid" value="${id}"/>
	</c:when>
	<c:otherwise>
		<c:set var="uiid" value="${u:uiid()}"/>
	</c:otherwise>
</c:choose>

<c:choose>
	<c:when test="${empty showOptions or showOptions eq '0'}">
		<c:set var="onFocus" value="false"/>
	</c:when>
	<c:otherwise>
		<c:set var="onFocus" value="true"/>
	</c:otherwise>
</c:choose>

<input id="${uiid}" type="text" class="tagator" style="${style}"
		value="${value}"
		<c:if test="${not empty placeholder}">placeholder="${placeholder}"</c:if>
		name="${inputName}">

<script>
	(function () {
		$$.ui.tagBoxInit($('#${uiid}'), '${choices}', ${onFocus}, '${url}');
	})();
</script>


