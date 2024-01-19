<%@ tag body-content="scriptless" pageEncoding="UTF-8" description="Menu group"%>
<%@ include file="/WEB-INF/jspf/taglibs.jsp"%>

<%@ attribute name="id" description="ul element ID" required="true"%>

<div style="max-height: 0; max-width: 0;">
	<ul id="${id}" style="display: none;" class="menu">
		<jsp:doBody/>
	</ul>
</div>
