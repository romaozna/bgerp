<%@ tag pageEncoding="UTF-8" description="Button"%> 
<%@ include file="/WEB-INF/jspf/taglibs.jsp"%>

<%-- 
	Icons: https://themify.me/themify-icons
--%>

<%@ attribute name="type" required="true" description="Type of the button"%>
<%@ attribute name="onclick" description="onclick JS"%>
<%@ attribute name="styleClass" description="Button CSS class(es)"%>

<c:choose>
	<c:when test="${type eq 'ok'}">
		<button type="button" class="btn-grey ${styleClass}" onclick="${onclick}">OK</button>
	</c:when>
	<c:when test="${type eq 'cancel'}">
		<button type="button" class="btn-white ${styleClass}" onclick="${onclick}">${l.l('Отмена')}</button>
	</c:when>
	<c:when test="${type eq 'add'}">
		<button type="button" class="btn-green icon ${styleClass}" onclick="${onclick}"><i class="ti-plus"></i></button>
	</c:when>
	<c:when test="${type eq 'edit'}">
		<button type="button" title="${l.l('Редактировать')}" class="btn-white icon ${styleClass}" onclick="${onclick}"><i class="ti-pencil"></i></button>
	</c:when>
	<c:when test="${type eq 'del'}">
		<button type="button" title="${l.l('Удалить')}" class="btn-white icon ${styleClass}" 
			onclick="if (confirm('${l.l('Вы уверены, что хотите удалить?')}')) { ${onclick} }"><i class="ti-trash"></i></button>
	</c:when>
	<c:when test="${type eq 'cut'}">
		<button type="button" title="${l.l('Вырезать')}" class="btn-white icon ${styleClass}" onclick="${onclick}"><i class="ti-cut"></i></button>
	</c:when>
	<c:when test="${type eq 'out'}">
		<button type="button" title="${l.l('Вывести')}" class="btn-grey icon ${styleClass}" onclick="${onclick}"><i class="ti-control-play"></i></button>
	</c:when>
</c:choose>
