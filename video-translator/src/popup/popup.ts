import { SUPPORTED_LANGUAGES, DEFAULT_SETTINGS } from '../shared/constants';
import { sendMessageToTab } from '../shared/message';
import { Message, Settings } from '../shared/types';

const statusIndicator = document.getElementById('status-indicator') as HTMLDivElement;
const statusDot = document.getElementById('status-dot') as HTMLSpanElement;
const statusText = document.getElementById('status-text') as HTMLSpanElement;
const subtitleSource = document.getElementById('subtitle-source') as HTMLDivElement;
const sourceText = document.getElementById('source-text') as HTMLSpanElement;
const translateToggle = document.getElementById('translate-toggle') as HTMLInputElement;
const sourceLang = document.getElementById('source-lang') as HTMLSelectElement;
const targetLang = document.getElementById('target-lang') as HTMLSelectElement;
const fontSize = document.getElementById('font-size') as HTMLInputElement;
const fontSizeValue = document.getElementById('font-size-value') as HTMLSpanElement;
const subtitlePosition = document.getElementById('subtitle-position') as HTMLSelectElement;
const errorSection = document.getElementById('error-section') as HTMLDivElement;
const errorText = document.getElementById('error-text') as HTMLSpanElement;
const errorAction = document.getElementById('error-action') as HTMLButtonElement;
const settingsBtn = document.getElementById('settings-btn') as HTMLButtonElement;

async function getActiveTabId(): Promise<number> {
  const tabs = await chrome.tabs.query({ active: true, currentWindow: true });
  if (tabs.length > 0 && tabs[0].id != null) {
    return tabs[0].id;
  }
  throw new Error('No active tab found');
}

async function sendToContentScript(message: Message): Promise<any> {
  const tabId = await getActiveTabId();
  return sendMessageToTab(tabId, message);
}

function setStatusIndicator(status: string, text: string): void {
  statusIndicator.className = 'status-indicator status-' + status;
  statusText.textContent = text;
}

function populateLanguages(): void {
  SUPPORTED_LANGUAGES.forEach((lang) => {
    const opt1 = document.createElement('option');
    opt1.value = lang.code;
    opt1.textContent = lang.label;
    sourceLang.appendChild(opt1);

    const opt2 = document.createElement('option');
    opt2.value = lang.code;
    opt2.textContent = lang.label;
    targetLang.appendChild(opt2);
  });

  targetLang.value = DEFAULT_SETTINGS.defaultTargetLang;
}

async function queryStatus(): Promise<void> {
  try {
    const response = await sendToContentScript({ type: 'GET_STATUS', payload: {} });

    if (!response || !response.videoDetected) {
      setStatusIndicator('none', '未找到视频');
      translateToggle.disabled = true;
      return;
    }

    if (response.hasSubtitle) {
      setStatusIndicator('subtitle', '已检测到视频');
      subtitleSource.style.display = '';
      sourceText.textContent = response.subtitleSource || '未知';
    } else {
      setStatusIndicator('nosubtitle', '将使用音频识别');
      subtitleSource.style.display = 'none';
    }

    translateToggle.disabled = false;
    if (response.translating) {
      translateToggle.checked = true;
    }
  } catch {
    setStatusIndicator('none', '未找到视频');
    translateToggle.disabled = true;
  }
}

async function checkApiKey(): Promise<void> {
  const data: { settings?: Partial<Settings> } = await chrome.storage.local.get(['settings']);
  const settings = data.settings || DEFAULT_SETTINGS;

  if (!settings.translateApiKey || !settings.asrApiKey) {
    errorSection.style.display = '';
    const missing: string[] = [];
    if (!settings.asrApiKey) missing.push('语音识别');
    if (!settings.translateApiKey) missing.push('翻译');
    errorText.textContent = '未配置 API Key: ' + missing.join('、');
  } else {
    errorSection.style.display = 'none';
  }
}

translateToggle.addEventListener('change', () => {
  sendToContentScript({
    type: 'TOGGLE_TRANSLATION',
    payload: { enabled: translateToggle.checked },
  });
});

sourceLang.addEventListener('change', () => {
  const autoDetect = sourceLang.value === 'auto';
  sendToContentScript({
    type: 'UPDATE_SETTINGS',
    payload: {
      autoDetectSource: autoDetect,
      ...(autoDetect ? {} : { defaultSourceLang: sourceLang.value }),
    },
  });
});

targetLang.addEventListener('change', () => {
  sendToContentScript({
    type: 'UPDATE_SETTINGS',
    payload: { defaultTargetLang: targetLang.value },
  });
});

fontSize.addEventListener('input', () => {
  const size = parseInt(fontSize.value, 10);
  fontSizeValue.textContent = size + 'px';
  sendToContentScript({
    type: 'UPDATE_SETTINGS',
    payload: { subtitleStyle: { fontSize: size } } as Partial<Settings>,
  });
});

subtitlePosition.addEventListener('change', () => {
  sendToContentScript({
    type: 'UPDATE_SETTINGS',
    payload: { subtitleStyle: { position: subtitlePosition.value as 'bottom' | 'top' } } as Partial<Settings>,
  });
});

errorAction.addEventListener('click', () => {
  chrome.runtime.openOptionsPage();
});

settingsBtn.addEventListener('click', () => {
  chrome.runtime.openOptionsPage();
});

populateLanguages();
queryStatus();
checkApiKey();
