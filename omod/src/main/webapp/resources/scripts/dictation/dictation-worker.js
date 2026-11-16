// Runs entirely in the browser: loads a public, non-patient Whisper model from a CDN once (cached by
// the browser afterward) and transcribes audio locally. No audio or transcript is ever sent anywhere -
// this worker never makes a network call carrying patient data, only the one-time model download.
import { pipeline } from "https://cdn.jsdelivr.net/npm/@huggingface/transformers@4.2.0";

var transcriberPromise = null;

function getTranscriber() {
	if (!transcriberPromise) {
		// dtype is forced to fp32 (instead of the default quantized weights) to avoid a known
		// ONNX Runtime Web bug where its graph optimizer crashes fusing a quantized Whisper
		// decoder's tied embedding/lm_head weights into a MatMulNBits op ("Missing required
		// scale ... weight_transposed_DequantizeLinear"). See microsoft/onnxruntime#28306 and
		// huggingface/transformers.js#1707. fp32 is a larger one-time download but loads reliably.
		transcriberPromise = pipeline("automatic-speech-recognition", "Xenova/whisper-base", { dtype: "fp32" });
	}
	return transcriberPromise;
}

// A single worker instance is shared by every mic button on the page, so each request carries an id
// that the caller uses to route the response back to the right button (recordings can overlap).
self.addEventListener("message", function (event) {
	var id = event.data && event.data.id;
	var audio = event.data && event.data.audio;
	if (!audio) {
		return;
	}
	self.postMessage({ id: id, type: "loading-model" });
	getTranscriber()
		.then(function (transcriber) {
			self.postMessage({ id: id, type: "transcribing" });
			return transcriber(audio);
		})
		.then(function (result) {
			self.postMessage({ id: id, type: "result", text: (result && result.text ? result.text : "").trim() });
		})
		.catch(function (err) {
			self.postMessage({ id: id, type: "error", message: (err && err.message) || "Transcription failed" });
		});
});
