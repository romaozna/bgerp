<%@ page contentType="text/html; charset=UTF-8"%>
<%@ include file="/WEB-INF/jspf/taglibs.jsp"%>

<html:form action="/admin/plugin/callboard/work">
	<input type="hidden" name="action" value="shiftList"/>

	 <c:url var="url" value="/admin/plugin/callboard/work.do">
		<c:param name="action" value="shiftGet"/>
		<c:param name="id" value="-1"/>
		<c:param name="returnUrl" value="${form.requestUrl}"/>
	</c:url>
	<button type="button" class="btn-green mr1" onclick="$$.ajax.loadContent('${url}')">+</button>

	<ui:select-single list="${allowOnlyCategories}" hiddenName="categoryId" value=${form.param.categoryId}" onSelect="$$.ajax.loadContent($hidden[0].form)"
		style="width: 200px;" placeholder="Выберите категорию"/>

	<ui:page-control/>
</html:form>

<table style="width: 100%;" class="data mt1">
	<tr>
		<td width="30">&#160;</td>
		<td width="30">ID</td>
		<td width="30">Обозн.</td>
		<td width="50%">${l.l('Title')}</td>
		<td width="50%">Комментарий</td>
	</tr>

	<c:forEach var="item" items="${form.response.data.list}">
		<tr>
			<c:url var="editUrl" value="/admin/plugin/callboard/work.do">
				<c:param name="action" value="shiftGet"/>
				<c:param name="id" value="${item.id}"/>
				<c:param name="returnUrl" value="${form.requestUrl}"/>
			</c:url>
			<c:url var="deleteAjaxUrl" value="/admin/plugin/callboard/work.do">
				<c:param name="action" value="shiftDelete"/>
				<c:param name="id" value="${item.id}"/>
			</c:url>
			<c:url var="deleteAjaxCommandAfter" value="$$.ajax.loadContent('${form.requestUrl}')"/>

			<td nowrap="nowrap"><%@ include file="/WEB-INF/jspf/edit_buttons.jsp"%></td>

			<td>${item.id}</td>
			<c:set var="color"><c:if test="${not empty item.color}">style="background-color:${item.color}"</c:if></c:set>
			<td ${color}>
				${item.symbol}
			</td>
			<td>${item.title}</td>
			<td>${item.comment}</td>
		</tr>
	</c:forEach>
</table>

<shell:title text="${l.l('Шаблоны смен')}"/>
<shell:state/>