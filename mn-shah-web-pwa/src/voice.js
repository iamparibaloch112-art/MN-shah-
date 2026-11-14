export function detectAssetFromText(text) {
  const lower = (text || '').toLowerCase();
  if (lower.includes('nzd') || lower.includes('kiwi')) return 'NZD_USD';
  if (lower.includes('ngn') || lower.includes('naira')) return 'USD_NGN';
  if (lower.includes('axie') || lower.includes('axs') || lower.includes('infinity')) return 'AXS_USD';
  if (lower.includes('gold') || lower.includes('xau') || lower.includes('sona')) return 'XAU_USD';
  if (lower.includes('gbp') || lower.includes('pound')) return 'GBP_USD';
  if (lower.includes('jpy') || lower.includes('yen')) return 'USD_JPY';
  if (lower.includes('eur') || lower.includes('euro')) return 'EUR_USD';
  return null;
}

export function parseVoiceCommand(rawText) {
  const text = (rawText || '').trim();
  const lower = text.toLowerCase();
  const asset = detectAssetFromText(lower);

  const isSignalRequest =
    lower.includes('signal') ||
    lower.includes('analyze') ||
    lower.includes('analysis') ||
    lower.includes('tajziya') ||
    lower.includes('candle') ||
    lower.includes('mn shah mujhe') ||
    lower.includes('سگنل');

  if (isSignalRequest) {
    return { type: 'REQUEST_SIGNAL', asset };
  }
  if (asset && (lower.includes('open') || lower.includes('chart') || lower.includes('dikhao') || lower.includes('switch'))) {
    return { type: 'SWITCH_ASSET', asset };
  }
  return { type: 'GENERAL_QUESTION', query: text };
}

export function speakUrduText(text, { onStart, onEnd } = {}) {
  if (typeof window === 'undefined' || !('speechSynthesis' in window) || !text) {
    onEnd?.();
    return;
  }
  try {
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    const voices = window.speechSynthesis.getVoices();
    const urduVoice =
      voices.find((v) => v.lang.toLowerCase().startsWith('ur') && v.name.toLowerCase().includes('male')) ||
      voices.find((v) => v.lang.toLowerCase().startsWith('ur')) ||
      voices.find((v) => v.lang.toLowerCase().startsWith('hi'));
    if (urduVoice) utterance.voice = urduVoice;
    utterance.lang = urduVoice?.lang || 'ur-PK';
    utterance.pitch = 0.88;
    utterance.rate = 0.96;
    utterance.onstart = () => onStart?.();
    utterance.onend = () => onEnd?.();
    utterance.onerror = () => onEnd?.();
    window.speechSynthesis.speak(utterance);
  } catch {
    onEnd?.();
  }
}

export function stopSpeaking() {
  if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
    window.speechSynthesis.cancel();
  }
}

export function createSpeechListener({ onPartial, onFinal, onError, onStateChange }) {
  const SpeechRecognition =
    typeof window !== 'undefined' &&
    (window.SpeechRecognition || window.webkitSpeechRecognition);
  if (!SpeechRecognition) {
    return {
      supported: false,
      start: () => onError?.('Voice recognition is not supported on this browser. Type your command below.'),
      stop: () => {}
    };
  }

  let recognition = null;
  return {
    supported: true,
    start() {
      try {
        recognition = new SpeechRecognition();
        recognition.lang = 'ur-PK';
        recognition.interimResults = true;
        recognition.maxAlternatives = 1;
        onStateChange?.(true);

        recognition.onresult = (event) => {
          let interim = '';
          let finalText = '';
          for (let i = event.resultIndex; i < event.results.length; i += 1) {
            const transcript = event.results[i][0].transcript;
            if (event.results[i].isFinal) finalText += transcript;
            else interim += transcript;
          }
          if (interim) onPartial?.(interim);
          if (finalText.trim()) {
            onStateChange?.(false);
            onFinal?.(finalText.trim());
          }
        };

        recognition.onerror = (e) => {
          onStateChange?.(false);
          onError?.(`Voice input error (${e.error}). You can also type "MN SHAH mujhe signal do".`);
        };
        recognition.onend = () => onStateChange?.(false);
        recognition.start();
      } catch (err) {
        onStateChange?.(false);
        onError?.(err.message || 'Unable to start microphone.');
      }
    },
    stop() {
      try {
        recognition?.stop();
      } catch {}
      onStateChange?.(false);
    }
  };
}
