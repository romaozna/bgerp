<%@ page contentType="text/html; charset=UTF-8"%>
<%@ include file="/WEB-INF/jspf/taglibs.jsp"%>

<script>
	$(function() {
		$(".searchForm").hide();
		$("#searchForm-customer").show();

		addAddressSearch("#searchForm-customer");
	})
</script>

<div style="height: 100%; width: 100%; display: table-row;">
	<div style="vertical-align: top; display: table-cell; min-width: 300px;" class="in-w100p">
		<c:set var="allowedForms" value="${u:toSet( form.permission['allowedForms'] )}" scope="request"/>
		<c:set var="defaultForm" value="${form.permission['defaultForm']}"/>

		<u:sc>
			<c:set var="onSelect">
				const value = $('#searchForm > input[type=hidden]').val();
				$('.searchForm').hide(); $('#searchForm-' + value).show();
			</c:set>

			<ui:combo-single id="searchForm" hiddenName="searchMode" prefixText="${l.l('Искать')}:" onSelect="${onSelect}">
				<jsp:attribute name="valuesHtml">
					<c:if test="${empty allowedForms or allowedForms.contains('customer')}">
						<li value="customer">${l.l('Customer')}</li>
					</c:if>
					<c:if test="${empty allowedForms or allowedForms.contains('process')}">
						<li value="process">${l.l('Process')}</li>
					</c:if>

					<c:set var="mode" value="items" scope="request"/>
					<plugin:include endpoint="user.search.jsp"/>
				</jsp:attribute>
			</ui:combo-single>

			<script>
				$(function () {
					${onSelect}
				})
			</script>
		</u:sc>

		<div id="searchForms">
			<html:form action="/user/search"
				styleId="searchForm-customer" styleClass="searchForm in-mb1 mt1 in-w100p">
				<html:hidden property="action" value="customerSearch" />
				<html:hidden property="searchBy" />

				<ui:input-text
					name="title" placeholder="${l.l('Title')}" title="${l.l('Для поиска введите подстороку названия и нажмите Enter')}"
					onSelect="this.form.elements['searchBy'].value='title';
							  $$.ajax.load(this.form, '#searchResult')"/>

				<%@ include file="search_address_filter.jsp"%>
				<ui:input-text
					name="id" placeholder="ID" title="${l.l('Для поиска введите код контрагента и нажмите Enter')}"
					onSelect="this.form.elements['searchBy'].value='id';
							  $$.ajax.load(this.form, '#searchResult')"/>

				<div>
					<button type="button" class="btn-white" onclick="$('#searchForm-customer').each (function(){this.reset(); });">${l.l('Очистить')}</button>
				</div>
			</html:form>

			<html:form action="/user/search"
				styleId="searchForm-process" styleClass="searchForm in-mb1 mt1 in-w100p">
				<html:hidden property="action" value="processSearch" />
				<html:hidden property="searchBy" />
				<ui:input-text name="id" placeholder="${l.l('Поиск процесса по ID')}"
							title="${l.l('Для поиска введите код процесса и нажмите Enter')}"
							onSelect="this.form.elements['searchBy'].value='id';
									  $$.ajax.load(this.form, $('#searchResult')); return false;" />
				<div style="display: flex;">
					<u:sc>
						<%@ include file="process_search_constants.jsp"%>
						<ui:combo-single hiddenName="mode" style="width: 100%;">
							<jsp:attribute name="valuesHtml">
								<li value="${MODE_USER_CREATED}">${l.l('Cозданные мной')}</li>
								<li value="${MODE_USER_CLOSED}">${l.l('Закрытые мной')}</li>
								<li value="${MODE_USER_STATUS_CHANGED}">${l.l('Статус изменён мной')}</li>
							</jsp:attribute>
						</ui:combo-single>
					</u:sc>
					<div class="pl05">
						<ui:button type="out" onclick="this.form.elements['searchBy'].value='userId'; $$.ajax.load(this.form, '#searchResult');"/>
					</div>
				</div>
			</html:form>

			<c:remove var="mode"/>
			<plugin:include endpoint="user.search.jsp"/>
		</div>
	</div>

	<c:if test="${not empty defaultForm}">
		<script>
			$(function()
			{
				$("#searchForm > ul.drop > li[value='${defaultForm}']").click();
			})
		</script>
	</c:if>

	<div id="searchResult" class="pl1" style="display: table-cell; width: 100%; vertical-align: top;">
		<%--  сюда вставляются DIV ки --%>
		&#160;
	</div>
</div>

<c:set var="title" value="${l.l('Поиск')}"/>
<%@ include file="/WEB-INF/jspf/shell_title.jsp"%>