<%@ page contentType="text/html; charset=UTF-8"%>
<%@ include file="/WEB-INF/jspf/taglibs.jsp"%>

<%@ include file="process_message_edit_upload.jsp"%>

<c:set var="message" scope="request" value="${form.response.data.message}"/>
<c:set var="config" value="${ctxSetup.getConfig('ru.bgcrm.dao.message.config.MessageTypeConfig')}"/>

<c:set var="editorUiid" value="${u:uiid()}"/>
<c:set var="typeComboUiid" value="${u:uiid()}"/>

<c:set var="typeChangedScript">
	$$.message.editorTypeChanged('${editorUiid}', '${typeComboUiid}', '${uploadFormId}');
</c:set>

<html:form action="/user/message" styleId="${editorUiid}" styleClass="editorStopReload">
	<input type="hidden" name="action" value="messageUpdate"/>
	<html:hidden property="processId"/>
	<html:hidden property="id"/>
	<html:hidden property="areaId"/>

	<c:if test="${not empty message}">
		<input type="hidden" id="lock-${message.lockEdit}" name="lockFree"/>
	</c:if>

	<div style="display: table; width: 100%;">
		<div class="in-table-cell">
			<div style="vertical-align: top; width: 30px;" id="typeSelectContainer">
				<h2>${l.l('Тип')}</h2>

				<c:remove var="disable"/>
				<c:choose>
					<c:when test="${not empty message}">
						<c:set var="value" value="${message.typeId}"/>
						<c:set var="disable" value="1"/>
					</c:when>
					<c:when test="${not empty form.param.messageType}">
						<c:set var="value" value="${form.param.messageType}"/>
						<c:set var="disable" value="1"/>
					</c:when>
					<c:when test="${not empty form.param.messageTypeAdd}">
						<c:set var="value" value="${form.param.messageTypeAdd}"/>
					</c:when>
				</c:choose>
				
				<c:set var="perm" value="${p:get(ctxUser.id, 'ru.bgcrm.struts.action.MessageAction:messageUpdate')}"/>
				<c:set var="allowedTypeIds" value="${u.toIntegerSet(perm['allowedTypeIds'])}"/>

				<ui:combo-single
					id="${typeComboUiid}" hiddenName="typeId" widthTextValue="120px"
					value="${value}" disable="${disable}" onSelect="${typeChangedScript}">
					<jsp:attribute name="valuesHtml">
						<c:forEach var="item" items="${config.typeMap}">
							<c:if test="${empty allowedTypeIds or allowedTypeIds.contains(item.key)}">
								<c:set var="messageType" value="${item.value}"/>
								<c:choose>
									<%-- special editor --%>
									<c:when test="${not empty messageType.editorJsp}">
										<li value="${item.key}" editor="${messageType.id}">${item.value.title}</li>
									</c:when>
									<%-- or default one for notes --%>
									<c:otherwise>
										<li value="${item.key}">${item.value.title}</li>
									</c:otherwise>
								</c:choose>
							</c:if>
						</c:forEach>
					</jsp:attribute>
				</ui:combo-single>
			</div>
			<c:set var="tagConfig" value="${ctxSetup.getConfig('ru.bgcrm.model.message.TagConfig')}"/>
			<c:if test="${not empty tagConfig and not empty tagConfig.tagList}">
				<div class="pl1 w100p">
					<h2>${l.l('Теги')}</h2>
					<input type="hidden" name="updateTags" value="1"/>
					<ui:select-mult list="${tagConfig.tagList}" values="${form.response.data.messageTagIds}" hiddenName="tagId"/>
				</div>
			</c:if>
		</div>
		<%@ include file="process_message_edit_text.jsp"%>
		<div>
			<%@ include file="process_message_edit_upload_list.jsp"%>
		</div>
	</div>

	<%@ include file="process_message_edit_ok_cancel.jsp"%>
</html:form>

<%-- preparation editor forms --%>
<c:forEach var="messageType" items="${config.typeMap.values()}">
	<c:set var="editorJsp" value="${messageType.editorJsp}"/>
	<c:if test="${not empty editorJsp}">
		<html:form action="/user/message" styleId="${editorUiid}-${messageType.id}" styleClass="editorStopReload" style="display: none;">
			<input type="hidden" name="action" value="messageUpdate"/>
			<html:hidden property="processId"/>
			<html:hidden property="id"/>

			<plugin:include endpoint="${editorJsp}"/>
		</html:form>
	</c:if>
</c:forEach>

<script>
	$(function () {
		${typeChangedScript}
	});
</script>
