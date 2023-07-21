<%@ page contentType="text/html; charset=UTF-8"%>
<%@ include file="/WEB-INF/jspf/taglibs.jsp"%>

<c:choose>
	<c:when test="${mode eq 'items'}">
		<c:if test="${empty allowedForms or allowedForms.contains( 'bgbilling-searchContract' ) }">
			<li value="bgbilling-searchContract">Договор</li>
		</c:if>
	</c:when>
	<c:otherwise>
		<script>
			$(function() {
				addAddressSearch( "#searchForm-bgbilling-searchContract" );
			})
		</script>

		<html:form action="/user/plugin/bgbilling/proto/contract.do"
			styleId="searchForm-bgbilling-searchContract" styleClass="searchForm in-mb1 mt1 in-w100p">
			<html:hidden property="action" value="searchContract"/>
			<html:hidden property="searchBy"/>

			<u:sc>
				<c:set var="valuesHtml">
					<c:set var="cityIds" value="${u.toIntegerSet(ctxUser.configMap['cityIds'])}"/>
					<c:forEach items="${plugin.dbInfoManager.dbInfoList}" var="db">
						<c:if test="${empty cityIds or empty db.setup['cityId'] or cityIds.contains(u:int(db.setup['cityId']))}">
							<li value="${db.id}">${db.title}</li>
						</c:if>
					</c:forEach>
				</c:set>
				<c:set var="hiddenName" value="billingId"/>
				<c:set var="prefixText" value="Биллинг:"/>
				<c:set var="onSelect" value="$('#paramIdsDiv').html('')"/>

				<%@ include file="/WEB-INF/jspf/combo_single.jsp"%>
			</u:sc>

			<c:set var="loadSearchResult" value="$$.ajax.load(this.form, $('#searchResult'))"/>

			<ui:input-text name="title" placeholder="Номер договора"
							onSelect="this.form.elements['searchBy'].value='title'; ${loadSearchResult}"/>

			<ui:input-text name="comment" placeholder="Комментарий"
							onSelect="this.form.elements['searchBy'].value='comment'; ${loadSearchResult}"/>

			<div>
				Адрес:
					 <input type="radio" name="searchBySuffix" value="" checked="checked"/> Договора
					 <input type="radio" name="searchBySuffix" value="Object"/> Объекта
			</div>

			<%@ include file="/WEB-INF/jspf/user/search/search_address_filter.jsp"%>

			<c:forEach var="item" items="${ctxSetup.subIndexed( 'bgbilling:search.contract.' ).values()}">
				<c:choose>
					<c:when test="${item.type eq 'dialUpLogin'}">
						<input type="text" name="login_${item.billingId}_${item.moduleId}" placeholder="${item.title}"
									title="Фрагмент логина либо алиаса минимум 3 символа"
									onkeypress="if( enterPressed( event ) ){ this.form.billingId.value='${item.billingId}';
													this.form.elements['searchBy'].value='dialUpLogin_${item.billingId}_${item.moduleId}';
													${loadSearchResult} }"/>
					</c:when>
				</c:choose>
			</c:forEach>

			<ui:input-text name="id" placeholder="ID"
							onSelect="this.form.elements['searchBy'].value='id'; ${loadSearchResult}"/>

			<c:url var="url" value="/user/plugin/bgbilling/proto/contract.do">
				<c:param name="action" value="getParamList" />
			</c:url>
			<ui:combo-single  >
				<jsp:attribute name="id">paramTypeCombo</jsp:attribute>
				<jsp:attribute name="hiddenName">paramType</jsp:attribute>
				<jsp:attribute name="prefixText">Поиск по параметру:</jsp:attribute>
				<jsp:attribute  name="valuesHtml">
					<li value="-1">--</li>
					<li value="1">Текст</li>
					<li value="9">Телефон</li>
					<li value="6">Дата</li>
				</jsp:attribute>
				<jsp:attribute name="onSelect">$$.ajax.load('${url}'+'&billingId='+$("input[name='billingId']").val()+'&paramType='+$("input[name='paramType']").val(), $('#paramIdsDiv'))</jsp:attribute>
			</ui:combo-single>

			<div id="paramIdsDiv" class="in-mb05">
				<%--  сюда вставляются списки параметров --%>
				&#160;
			</div>

			<div class="in-mb05">
				<p>Отображать:</p>
				<div>
					<input type="checkbox" name="show_invisible" class="mr05"/>Скрытые
				</div>
				<div>
					<input type="checkbox" name="show_closed" class="mr05"/>Закрытые
				</div>
				<div>
					<input type="checkbox" name="show_sub" class="mr05">Субдогвора</input>
				</div>
			</div>

			<div>
				<input type="button" class="btn-white" value="Очистить"
						onclick="$('#searchForm-bgbilling-searchContract').each (function(){this.reset(); });"/>
			</div>
		</html:form>
	</c:otherwise>
</c:choose>