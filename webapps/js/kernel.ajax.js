// "use strict";

$$.ajax = new function () {
	const debug = $$.debug("ajax");

	/**
	 * Maximum length of query string param to be moved in request body.
	 */
	const MAX_QUERY_STRING_LENGTH = 150;

	const trim100 = (value) => {
		if (typeof value !== "string")
			return value;
		return value.length > 100 ? value.substr(0, 100) + "..." : value;
	}

	/**
	 * Sends AJAX request and returns a promise.
	 * input - URL string, or HTMLFormElement, or $(HTMLFormElement), or 'BUTTON' element
	 * options.toPostNames - array of names of POST body parameters, automatically derived, case not presented
	 * options.html = true - treat result as HTML
	 * options.control - button to add there progress spinner, extracted if input is 'BUTTON'
	 * options.failAlert = false - do now show alert on failing promise
	 * By default the promise is processed by checkResponse() function.
	 */
	const post = (input, options) => {
		debug("post", trim100(input));

		options = options || {};

		if (input.tagName === 'BUTTON') {
			options.control = input;
			input = input.form;
		}

		const form = getForm(input);
		if (form) {
			$(form).find("input").removeClass("error");
		}

		const separated = separatePostParams(formUrl(input), options.toPostNames, !options.html);

		// progress spinner
		let requestDone = () => {};
		if (options.control) {
			options.control.disabled = true;

			const $control = $(options.control);
			$control.prepend("<span class='progress'><i class='progress-icon ti-reload'></i></span>");
			const $progress = $control.find(">.progress");

			requestDone = () => {
				options.control.disabled = false;
				$progress.remove();
			}
		}

		const def = $.Deferred();

		$.ajax({
			type: "POST",
			url: separated.url,
			data: separated.data,
		}).fail(function (jqXHR, textStatus, errorThrown) {
			requestDone();
			if (options.failAlert !== false)
				onAJAXError(separated.url, jqXHR, textStatus, errorThrown);
			def.reject();
		}).done((data) => {
			requestDone();
			if (!options.html) {
				if (checkResponse(data, form))
					def.resolve(data);
				else
					def.reject();
			} else
				def.resolve(data);
		});

		return def.promise();
	}

	let loadCnt = 0;
	let loadDfd;

	const getLoadDfd = () => {
		return loadDfd ? loadDfd : {
			resolve: () => {}
		}
	}

	/*
	 * Sends HTTP request and set result HTML on element.
	 * input - URL string or HTMLFormElement or $(HTMLFormElement).
	 * $selector - jQuery selector, target area.
	 * options.dfd - deferred, being resolved after all onLoad JS on chained loads are done.
	 * options.append  - append HTML into the element, deprecated.
	 * options.control - will be passed to 'post' function.
	 */
	const load = (input, $selector, options) => {
		debug("load", trim100(input), $selector);

		options = options || {};
		options.html = true;

		if (typeof $selector === 'string')
			$selector = $($selector);

		// erasing of existing value, speeds up load process significantly in some cases
		// the reason is not clear, was found in callboard, probably because of removing of onLoad listeners
		// !!! the erasing was disabled because of problems with generation URL from form elements, which already were gone

		// parameter runs cascaded load
		let dfd = options.dfd;
		if (dfd) {
			loadDfd = {
				key: "dfd",

				/** Wrapping object, contains Deferred + URL for debug. */
				create: function (dfd, url) {
					const result = {
						dfd: dfd,
						url: trim100(url)
					};
					debug("Create wrap", result);
					return result;
				},

				resolve: function ($selector) {
					const dfd = $selector.data(loadDfd.key);

					const wait = [];
					$selector.find(".loader").each(function () {
						const subDfd = $(this).data(loadDfd.key);
						if (subDfd)
							wait.push(subDfd);
					})

					if (wait) {
						debug("Resolve when", dfd.url, wait);
						$.when.apply($, wait.map(el => el.dfd)).done(() => {
							debug("Resolve", dfd.url, wait);
							dfd.dfd.resolve();
						});
					} else {
						debug("Resolve", dfd.url);
						dfd.dfd.resolve();
					}
				}
			}

			dfd.done(() => {
				debug("Set loadDfd null", input);
				loadDfd = null;
			});
		}

		if (!loadDfd) {
			$selector.toggleClass("ajax-loading");
			return post(input, options).done((result) => {
				if (options.replace) {
					$selector.replaceWith(result);
				} else if (options.append) {
					$selector.append(result);
				} else {
					$selector.html(result);
				}
			}).always(() => {
				$selector.toggleClass("ajax-loading");
			});
		} else {
			const existingDfd = $selector.data(loadDfd.key);
			if (existingDfd) {
				if (existingDfd.state() === 'resolved') {
					debug("Existing resolved dfd", existingDfd, input);
					$selector.removeData(loadDfd.key);
				} else {
					console.error("Existing not resolved dfd", existingDfd, input);
					return existingDfd;
				}
			}

			if (!dfd)
				dfd = $.Deferred();

			$selector
				.addClass("loader")
				.toggleClass("ajax-loading")
				.data(loadDfd.key, loadDfd.create(dfd, input));

			post(input, options).done((result) => {
				debug("Done", trim100(input));

				const id = "load-" + (loadCnt++);
				const afterLoadScript =
					`<script id="${id}"> {
						const $selector = $("#${id}").parent();
						$(() => {
							$$.ajax.loadDfd().resolve($selector);
						});
					} </script>`;

				if (options.append) {
					$selector.append(result + afterLoadScript);
				} else {
					$selector.html(result + afterLoadScript);
				}
			}).always(() => {
				$selector.toggleClass("ajax-loading");
			});

			return dfd;
		}
	}

	/**
	 * Calls load with $selector $$.shell.$content()
	 * @param {*} input URL to be loaded, or HTMLFormElement, or $(HTMLFormElement), or input/button inside form, required parameter
	 * @param {*} obj DOM element, placed in the loaded area, not needed when 'input' is a form input/button field.
	 */
	const loadContent = (input, obj) => {
		const options = {};

		if (input.form && input.form instanceof HTMLFormElement)
			input = input.form;

		if (!obj)
			obj = input;

		if (input.tagName === 'BUTTON')
			options.control = input;
		else if (obj && obj.tagName === 'BUTTON')
			options.control = obj;

		return load(input, $$.shell.$content(obj), options);
	}

	/**
	 * Moves to POST part HTTP request parameters. Separated param names have to be placed in toPostNames or start from prefix 'data'.
	 * @param {*} url initial URL.
	 * @param {*} toPostNames array with POST params.
	 * @param {*} json object with properties url and data.
	 * @returns object with 'url' field - for request URL and 'data' - for placing in request body.
	 */
	const separatePostParams = function (url, toPostNames, json) {
		if (!toPostNames && url.length > MAX_QUERY_STRING_LENGTH)
			toPostNames = getToPostNames(url);

		// query string in request body
		let data = "";

		if (toPostNames) {
			let pos = url.indexOf('?');
			if (pos > 0) {
				let urlWithoutData = url.substring(0, pos + 1);

				while (pos > 0) {
					const posSeparator = url.indexOf('=', pos + 1);
					if (posSeparator < pos) {
						console.warn("Not found separator after pos: ", pos, "; url: ", url);
						break;
					}

					let posEnd = url.indexOf('&', posSeparator + 1);
					if (posEnd < posSeparator)
						posEnd = url.length;

					const key = url.substring(pos + 1, posSeparator);
					const pair = url.substring(pos + 1, posEnd);

					if (toPostNames.includes(key)) {
						if (data.length)
							data += "&"
						data += pair;
					}
					else {
						if (!urlWithoutData.endsWith('?'))
							urlWithoutData += "&";
						urlWithoutData += pair;
					}

					pos = url.indexOf('&', pos + 1);
				}

				url = urlWithoutData;
			}
		}

		if (json) {
			if (url.indexOf("?") < 0)
				url += "?";

			if (!url.endsWith("?"))
				url += "&";

			url += "responseType=json";
		}

		return {"url": url, "data": data};
	}

	/**
	 * Extracts parameter names, should be moved to request body.
	 * @param {*} url initial URL with query string of all parameters.
	 * @returns array with parameter names, should be moved to request body.
	 */
	const getToPostNames = function (url) {
		const counts = {};

		let pos = url.indexOf('?');
		// no request parameters
		if (pos < 0)
			return null;

		// first param after ?
		while (pos > 0) {
			const posSeparator = url.indexOf('=', pos + 1);
			if (posSeparator < pos) {
				console.warn("Not found separator after pos: ", pos, "; url: ", url);
				break;
			}

			const key = url.substring(pos + 1, posSeparator);
			if (counts[key])
				counts[key] += 1;
			else
				counts[key] = 1;

			// large parameter value, move to post even a single
			if (counts[key] < 2) {
				let posEnd = url.indexOf('&', posSeparator);
				if (posEnd < posSeparator)
					posEnd = url.length;

				if ((posEnd - pos) > MAX_QUERY_STRING_LENGTH)
					counts[key] = 2;
			}

			// second and following params after &
			pos = url.indexOf('&', pos + 1);
		}

		const result = [];

		for (const key in counts)
			if (counts[key] >= 2)
				result.push(key);

		debug('getToPostNames', url, result);

		return result;
	}

	/**
	 * Checks AJAX response.
	 * @param {*} data param and values
	 * @param {*} form optional form object, to mark incorrect fields there
	 */
	const checkResponse = function (data, form) {
		var result = false;

		if (data.status == 'ok') {
			result = data;

			processClientEvents(data);

			if (data.message) {
				alert(data.message);
			}
		} else {
			const message = data.message;

			if (form) {
				const paramName = data.data && data.data.paramName;
				if (paramName) {
					const $input = $(form).find("input[name='" + paramName + "']");
					$input.addClass("error");
					$input[0].scrollIntoView();
				}
			}

			alert("Error: " + message);

			processClientEvents(data);
		}

		return result;
	}

	const processClientEvents = function (data) {
		for (var i = 0; i < data.eventList.length; i++) {
			if (data.eventList[i] != null) {
				processEvent(data.eventList[i]);
			}
		}
	}

	/**
	 * Get HTMLFormElement object if it has passed.
	 * @param {*} obj - array of forms, or a single form
	 * @return HTMLFormElement.
	 */
	const getForm = function (obj) {
		if (obj instanceof Array || obj instanceof jQuery)
			return obj[0];
		else if (obj instanceof HTMLFormElement)
			return obj;
		return null;
	}

	/**
	 * Builds URL string from form.
	 * @param {*} param string with ready URL or form's selector or form itself
	 * @param {*} excludeParams skipping params
	 */
	const formUrl = function (param, excludeParams) {
		if (typeof param === 'string')
			return param;

		let forms = param;

		if (forms instanceof HTMLFormElement) {
			forms = [forms];
		}

		var commonUrl = "";

		for (var k = 0; k < forms.length; k++) {
			var form = forms[k];

			var param = $(form).attr('action');
			var params = $(form).serializeAnything(excludeParams);
			if (params.length > 0) {
				if (commonUrl.indexOf('?') > 0 || param.indexOf('?') > 0) {
					param += "&" + params;
				} else {
					param += "?" + params;
				}
			}

			// удаление параметров page.
			for (var i = 0; i < form.length; i++) {
				var el = form.elements[i];
				if (el.name == 'page.pageIndex') {
					el.value = 1;
				} else if (el.name.indexOf("page.") == 0) {
					form.removeChild(el);
					i--;
				}
			}

			if (commonUrl.length > 0) {
				commonUrl += "&";
			}
			commonUrl += param;
		}

		return commonUrl;
	}

	/**
	 * Builds URL from key-value pairs.
	 * @param {*} requestParams key value param pairs
	 * @param {*} subParam NO IDEA
	 */
	const requestParamsToUrl = function (requestParams, subParam) {
		let url = "";
		for (const k in requestParams) {
			url += "&";
			if (subParam) {
				url += subParam + "(";
			}
			url += encodeURIComponent(k);
			if (subParam) {
				url += ")";
			}
			url += "=" + encodeURIComponent(requestParams[k]);
		}
		return url;
	}

	/**
	 * File upload.
	 * @param {*} formId hidden form's CSS ID.
	 * @param {*} iframeId hidden iframe's CSS ID.
	 * @param {*} complete callback function on upload is done.
	 */
	const upload = function (formId, iframeId, complete) {
		const $form = $('#' + formId);
		$form.iframePostForm({
			json: true,
			iframeID: iframeId,
			post: function () {
				if (!$form.find('input[type=file]').val()) {
					alert("Missing file!");
					return false;
				}
			},
			complete: function (response) {
				complete(response);
			}
		});
	}

	/**
	 * Manage all necessary events and listeners to make file upload.
	 * Should be added as CLICK EVENT on the triggering HTML element
	 *
	 * @param formId - ID of the form element
	 */
	const triggerUpload = function (formId) {
		const form = document.getElementById(formId);
		const inputFile = form.querySelectorAll('input[name=file]')[0];

		if (inputFile) {
			const onChange = function () {
				if (typeof form.requestSubmit === 'function') {
					form.requestSubmit();
				} else {
					form.submit();
				}
				form.reset();
				inputFile.onchange = function () {};
			};
			inputFile.onchange = onChange;
			inputFile.click();
		}
	}

	// public functions
	this.debug = debug;
	this.post = post;
	this.load = load;
	this.loadDfd = getLoadDfd;
	this.loadContent = loadContent;
	this.checkResponse = checkResponse;
	this.formUrl = formUrl;
	this.requestParamsToUrl = requestParamsToUrl;
	this.upload = upload;
	this.triggerUpload = triggerUpload;
	// deprecated
	this.separatePostParamsInt = separatePostParams;
}

//загружает URL на какой-то последний видимый элемент, selectorStart - селектор элемента
function openUrl(url, selectorStart) {
	console.warn($$.deprecated);

	var result = getAJAXHtml(url);
	if (result) {
		$(selectorStart + ':visible:last').html(result);
	}
}

//загружает URL на элемент
//selector - селектор
function openUrlTo(url, $selector, vars) {
	console.warn($$.deprecated);

	var result = undefined;
	if (vars) {
		result = getAJAXHtml(url, vars.toPostNames);
	} else {
		result = getAJAXHtml(url);
	}

	if (result) {
		if (vars && vars.replace) {
			$selector.replaceWith(result);
		} else if (vars && vars.append) {
			$selector.append(result);
		} else {
			$selector.html(result);
		}
	}
	return result;
}

//отправка AJAX с результатом HTML страница
function getAJAXHtml(url, toPostNames) {
	console.warn($$.deprecated);

	var result = false;

	var separated = $$.ajax.separatePostParamsInt(url, toPostNames);

	$.ajax({
		type: "POST",
		url: separated.url,
		data: separated.data,
		async: false,
		success: function (response) {
			result = response;
		},
		error: function (jqXHR, textStatus, errorThrown) {
			onAJAXError(separated.url, jqXHR, textStatus, errorThrown);
		}
	});

	return result;
}

//отправка AJAX команды c JSON ответом определённого формата
function sendAJAXCommand(url, toPostNames) {
	console.warn($$.deprecated);

	var result = false;

	var separated = $$.ajax.separatePostParamsInt(url, toPostNames, true);

	$.ajax({
		type: "POST",
		async: false,
		url: separated.url,
		data: separated.data,
		dataType: "json",
		success: function (data) {
			result = $$.ajax.checkResponse(data);
		},
		error: function (jqXHR, textStatus, errorThrown) {
			onAJAXError(separated.url, jqXHR, textStatus, errorThrown);
		}
	});

	return result;
}

//аналог предыдущей функции, за исключением, что для URL можно указывать параметры из хэша
function sendAJAXCommandWithParams(url, requestParams) {
	console.warn($$.deprecated);

	return sendAJAXCommand(url + $$.ajax.requestParamsToUrl(requestParams));
}

// move to $$.ui
function onAJAXError(url, jqXHR, textStatus, errorThrown) {
	if (jqXHR.status == 401) {
		$$.shell.login.show();
	} else {
		let text = "<b>URL</b>: " + url;
		if (jqXHR.responseText)
			text += "<br/><b>RESPONSE</b>:<br/>" + jqXHR.responseText;

		$$.shell.message.show("HTTP STATUS: " + jqXHR.status, text);
	}
}

//генерирует URL строку на основании введённых в форму параметров
function formUrl(forms, excludeParams) {
	console.warn($$.deprecated);

	return $$.ajax.formUrl(forms, excludeParams);
}

