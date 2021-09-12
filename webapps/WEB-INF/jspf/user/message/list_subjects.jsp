<%@ page contentType="text/html; charset=UTF-8"%>
<%@ include file="/WEB-INF/jspf/taglibs.jsp"%>

<u:sc>
	<%-- table ID --%>
	<c:set var="uiid" value="${u:uiid()}"/>

	<c:choose>
		<c:when test="${form.param['processed'] eq 1}">
			<table class="data hl fixed-header" id="${uiid}">
				<tr>
					<td width="30">ID</td>
					<td>${l.l('Тип')}</td>
					<td>${l.l('Тема')}</td>
					<td>${l.l('От')} -&gt; ${l.l('Кому')}</td>
					<td>${l.l('Время')}</td>
					<td>${l.l('Процесс')}</td>
				</tr>

				<c:forEach var="item" items="${form.response.data.list}">
					<c:url var="url" value="/user/message.do">
						<c:param name="id" value="${item.id}"/>
						<%-- <c:param name="returnUrl" value="${form.requestUrl}"/> --%>
					</c:url>

					<tr openUrl="${url}">
						<td>${item.id}</td>
						<td>${config.typeMap[item.typeId].title}</td>
						<td>${item.subject}</td>
						<%@ include file="from_to.jsp"%>
						<td>${tu.format(item.fromTime, 'ymdhm')}</td>
						<td><ui:process-link id="${item.process.id}" text="${ctxProcessTypeMap[item.process.typeId].title}"/></td>
					</tr>
				</c:forEach>
			</table>
		</c:when>
		<c:otherwise>
			<form action="/user/message.do">
				<input type="hidden" name="action" value="messageDelete"/>
				<table class="data hl fixed-header" id="${uiid}">
					<tr>
						<td width="30">
							<ui:button type="del" styleClass="btn-small" title="${l.l('Удалить выбранные')}" onclick="
								$$.ajax.post(this.form).done(() => {
									${script}
								})
							"/>
						</td>
						<td>${l.l('Тема')}</td>
						<td>${l.l('От')}</td>
						<td>${l.l('Время')}</td>
					</tr>

					<c:set var="today" value="<%=new java.util.Date()%>"/>

					<c:forEach var="item" items="${form.response.data.list}">
						<c:url var="url" value="/user/message.do">
							<c:param name="typeId" value="${item.typeId}"/>
							<c:param name="messageId" value="${item.systemId}"/>
							<%-- <c:param name="returnUrl" value="${form.requestUrl}"/> --%>
						</c:url>

						<tr valign="top" openUrl="${url}">
							<td style="text-align: center;">
								<input type="checkbox" name="typeId-systemId" value="${item.typeId}-${item.systemId}"/>
							</td>
							<td>${item.subject}</td>
							<%-- see from_to.jsp, support notes with user link --%>
							<td title="${item.from}">${item.from}</td>
							<td nowrap="nowrap">
								${tu.daysDelta(today, item.fromTime) eq 0 ?
									tu.format(item.fromTime, 'HH:mm') :
									tu.format(item.fromTime, 'ymdhm')
								}</td>
						</tr>
					</c:forEach>
				</table>
			</form>
		</c:otherwise>
	</c:choose>

	<script>
		$(function () {
			const $dataTable = $('#${uiid}');

			const callback = function ($clicked) {
				const $row = $clicked;

				const openUrl = $row.attr('openUrl');
				if (openUrl) {
					$$.ajax.load(openUrl, $('#${editorUiid}'));
					$dataTable.find('tr').removeClass('hl');
					$row.addClass('hl');
				} else {
					alert('Not found attribute openUrl!');
				}
			};

			doOnClick($dataTable, 'tr:gt(0)', callback);
		});
	</script>
</u:sc>