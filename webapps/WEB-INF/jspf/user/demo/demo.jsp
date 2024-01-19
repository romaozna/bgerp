<%@ page contentType="text/html; charset=UTF-8"%>
<%@ include file="/WEB-INF/jspf/taglibs.jsp"%>

<%@ include file="/WEB-INF/jspf/demo.jsp"%>

<h1>AJAX</h1>

<div>
	<p:check action="${form.httpRequestURI}:formSend">
		<h2>Send Form with Parameter Validation</h2>
		<form action="${form.httpRequestURI}">
			<input type="hidden" name="action" value="formSend"/>
			<input name="title" type="text" size="50" placeholder="Title"/>
			<button type="button" class="btn-grey ml1" onclick="
				$$.ajax.post(this.form).done((response) =>
					$$.shell.message.show(response.data.messageTitle, response.data.messageText)
				)
			">Send</button>
		</form>
	</p:check>

	<p:check action="${form.httpRequestURI}:entityList">
		<h2>Entities</h2>
		<div>
			<c:url var="url" value="${form.httpRequestURI}">
				<c:param name="action" value="entityList"/>
			</c:url>
			<c:import url="${url}"/>
		</div>
	</p:check>
</div>

<shell:title text="Demo Title"/>

<c:set var="rnd" value="${u.newInstance('java.util.Random').nextInt(3)}"/>
<c:choose>
	<c:when test="${rnd == 0}">
		<shell:state text="Demo State" help="project/index.html#mvc-iface-demo"/>
	</c:when>
	<c:when test="${rnd == 1}">
		<c:set var="uiid" value="${u:uiid()}"/>
		<button id="${uiid}" class="btn-white">Demo State Component</button>
		<shell:state moveSelector="#${uiid}"/>
	</c:when>
	<c:otherwise>
		<shell:state error="Demo State Error"/>
	</c:otherwise>
</c:choose>
