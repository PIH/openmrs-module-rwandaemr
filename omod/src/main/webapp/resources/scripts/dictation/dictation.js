/**
 * Wires up ".dictation-mic-btn" buttons on an HTML Form so a clinician can dictate into the
 * textarea inside the same ".dictation-field" wrapper instead of typing. Transcription runs
 * entirely inside this browser tab via a Whisper speech-to-text model (see dictation-worker.js) -
 * patient audio and its transcript are never sent anywhere. The only network traffic involved is a
 * one-time download of a public, non-patient model file from a CDN, cached by the browser after
 * first use.
 *
 * Markup contract (see backend_configuration/htmlforms/ipd-general-followup.xml):
 *   <span class="dictation-field">
 *     <obs style="textarea" .../>
 *     <button type="button" class="dictation-mic-btn">Dictate</button>
 *     <span class="dictation-status"></span>
 *   </span>
 *
 * Loaded as an ES module (<script type="module" src="dictation.js">) since it spawns a module Worker.
 */

var MAX_RECORDING_MS = 120000;

var TARGET_SAMPLE_RATE = 16000;

var worker = null;

var nextRequestId = 1;

var pendingRequests = {};

function getWorker() {
	if (!worker) {
		worker = new Worker(new URL("./dictation-worker.js", import.meta.url), { type: "module" });
		worker.addEventListener("message", onWorkerMessage);
	}
	return worker;
}

function onWorkerMessage(event) {
	var data = event.data || {};
	var pending = pendingRequests[data.id];
	if (!pending) {
		return;
	}
	if (data.type === "loading-model") {
		setStatus(pending.button, "transcribing", "Loading speech model (first use only)…");
	}
	else if (data.type === "transcribing") {
		setStatus(pending.button, "transcribing", "Transcribing…");
	}
	else if (data.type === "result") {
		appendTranscript(pending.button, data.text);
		setStatus(pending.button, "done", "");
		finishRequest(data.id);
	}
	else if (data.type === "error") {
		setStatus(pending.button, "error", data.message || "Unable to transcribe audio");
		finishRequest(data.id);
	}
}

function finishRequest(id) {
	var pending = pendingRequests[id];
	if (pending) {
		pending.button.disabled = false;
		delete pendingRequests[id];
	}
}

function findWrapper(button) {
	return button.closest(".dictation-field");
}

function findTargetTextarea(button) {
	var wrapper = findWrapper(button);
	return wrapper ? wrapper.querySelector("textarea") : null;
}

function findStatusEl(button) {
	var wrapper = findWrapper(button);
	return wrapper ? wrapper.querySelector(".dictation-status") : null;
}

function setStatus(button, state, message) {
	var statusEl = findStatusEl(button);
	if (statusEl) {
		statusEl.textContent = message || "";
		statusEl.setAttribute("data-state", state);
	}
}

function appendTranscript(button, transcript) {
	if (!transcript) {
		return;
	}
	var field = findTargetTextarea(button);
	if (!field) {
		return;
	}
	var existing = field.value || "";
	var separator = existing.length > 0 && !/\s$/.test(existing) ? " " : "";
	field.value = existing + separator + transcript;
}

function pickSupportedMimeType() {
	var candidates = ["audio/webm;codecs=opus", "audio/webm", "audio/ogg", "audio/mp4"];
	for (var i = 0; i < candidates.length; i++) {
		if (window.MediaRecorder && MediaRecorder.isTypeSupported && MediaRecorder.isTypeSupported(candidates[i])) {
			return candidates[i];
		}
	}
	return "";
}

/** Decodes a recorded audio Blob into the 16kHz mono Float32Array the ASR model expects. */
function decodeToWhisperInput(blob) {
	return blob.arrayBuffer().then(function (arrayBuffer) {
		var AudioContextImpl = window.AudioContext || window.webkitAudioContext;
		var audioCtx = new AudioContextImpl();
		return audioCtx.decodeAudioData(arrayBuffer).then(function (decoded) {
			return resampleTo16kMono(decoded);
		}).then(function (samples) {
			audioCtx.close();
			return samples;
		}, function (err) {
			audioCtx.close();
			throw err;
		});
	});
}

function resampleTo16kMono(audioBuffer) {
	if (audioBuffer.numberOfChannels === 1 && audioBuffer.sampleRate === TARGET_SAMPLE_RATE) {
		return Promise.resolve(audioBuffer.getChannelData(0));
	}
	var length = Math.ceil(audioBuffer.duration * TARGET_SAMPLE_RATE);
	var offlineCtx = new OfflineAudioContext(1, length, TARGET_SAMPLE_RATE);
	var source = offlineCtx.createBufferSource();
	source.buffer = audioBuffer;
	source.connect(offlineCtx.destination);
	source.start(0);
	return offlineCtx.startRendering().then(function (rendered) {
		return rendered.getChannelData(0);
	});
}

function transcribeAudio(button, blob) {
	setStatus(button, "transcribing", "Preparing audio…");
	decodeToWhisperInput(blob).then(function (audioData) {
		var id = nextRequestId++;
		pendingRequests[id] = { button: button };
		getWorker().postMessage({ id: id, audio: audioData }, [audioData.buffer]);
	}).catch(function () {
		setStatus(button, "error", "Unable to process recorded audio");
		button.disabled = false;
	});
}

function startRecording(button) {
	// Browsers only expose navigator.mediaDevices in a secure context (HTTPS, or localhost). On a
	// plain http:// origin (e.g. a LAN IP) it is simply absent, which looks identical to "this
	// browser doesn't support it" unless we check isSecureContext separately and say so.
	if (window.isSecureContext === false) {
		setStatus(button, "error", "Voice dictation requires HTTPS - this page was loaded over an insecure connection");
		return;
	}
	if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia || !window.MediaRecorder) {
		setStatus(button, "error", "Voice dictation is not supported in this browser");
		return;
	}
	navigator.mediaDevices.getUserMedia({ audio: true }).then(function (stream) {
		var mimeType = pickSupportedMimeType();
		var recorder = mimeType ? new MediaRecorder(stream, { mimeType: mimeType }) : new MediaRecorder(stream);
		var chunks = [];
		var stopTimer = window.setTimeout(function () {
			if (recorder.state !== "inactive") {
				recorder.stop();
			}
		}, MAX_RECORDING_MS);

		recorder.addEventListener("dataavailable", function (event) {
			if (event.data && event.data.size > 0) {
				chunks.push(event.data);
			}
		});

		recorder.addEventListener("stop", function () {
			window.clearTimeout(stopTimer);
			stream.getTracks().forEach(function (track) {
				track.stop();
			});
			button.classList.remove("recording");
			button.dataset.recording = "false";
			button.disabled = true;
			var blob = new Blob(chunks, { type: mimeType || "audio/webm" });
			transcribeAudio(button, blob);
		});

		button._dictationRecorder = recorder;
		recorder.start();
		button.classList.add("recording");
		button.dataset.recording = "true";
		setStatus(button, "recording", "Recording… click again to stop");
	}).catch(function () {
		setStatus(button, "error", "Microphone access was denied or unavailable");
	});
}

function stopRecording(button) {
	var recorder = button._dictationRecorder;
	if (recorder && recorder.state !== "inactive") {
		recorder.stop();
	}
}

function onButtonClick(event) {
	event.preventDefault();
	var button = event.currentTarget;
	if (button.dataset.recording === "true") {
		stopRecording(button);
	}
	else {
		startRecording(button);
	}
}

function init() {
	var buttons = document.querySelectorAll(".dictation-mic-btn");
	buttons.forEach(function (button) {
		button.addEventListener("click", onButtonClick);
	});
}

if (document.readyState === "loading") {
	document.addEventListener("DOMContentLoaded", init);
}
else {
	init();
}
