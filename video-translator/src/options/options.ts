import { Settings } from '../shared/types';
import { ASR_PROVIDERS, TRANSLATE_PROVIDERS, SUPPORTED_LANGUAGES } from '../shared/constants';
import { getSettings, saveSettings } from '../shared/storage';

async function loadSettings(): Promise<void> {
  const settings = await getSettings();

  const asrSelect = document.getElementById('asr-provider') as HTMLSelectElement;
  ASR_PROVIDERS.forEach((p) => {
    const opt = document.createElement('option');
    opt.value = p.id;
    opt.textContent = p.label;
    asrSelect.appendChild(opt);
  });
  asrSelect.value = settings.asrProvider;

  const translateSelect = document.getElementById('translate-provider') as HTMLSelectElement;
  TRANSLATE_PROVIDERS.forEach((p) => {
    const opt = document.createElement('option');
    opt.value = p.id;
    opt.textContent = p.label;
    translateSelect.appendChild(opt);
  });
  translateSelect.value = settings.translateProvider;

  const langSelect = document.getElementById('default-target-lang') as HTMLSelectElement;
  SUPPORTED_LANGUAGES.forEach((l) => {
    const opt = document.createElement('option');
    opt.value = l.code;
    opt.textContent = l.label;
    langSelect.appendChild(opt);
  });
  langSelect.value = settings.defaultTargetLang;

  (document.getElementById('asr-api-key') as HTMLInputElement).value = settings.asrApiKey;
  (document.getElementById('translate-api-key') as HTMLInputElement).value = settings.translateApiKey;
  (document.getElementById('auto-detect-source') as HTMLInputElement).checked = settings.autoDetectSource;
  (document.getElementById('default-source-lang') as HTMLInputElement).value = settings.defaultSourceLang;
  (document.getElementById('subtitle-font-size') as HTMLInputElement).value = String(settings.subtitleStyle.fontSize);
  (document.getElementById('subtitle-font-color') as HTMLInputElement).value = settings.subtitleStyle.fontColor;
  (document.getElementById('subtitle-bg-color') as HTMLInputElement).value = settings.subtitleStyle.bgColor;
  (document.getElementById('subtitle-position') as HTMLSelectElement).value = settings.subtitleStyle.position;
  (document.getElementById('subtitle-opacity') as HTMLInputElement).value = String(settings.subtitleStyle.opacity);
  (document.getElementById('segment-duration') as HTMLInputElement).value = String(settings.segmentDuration);
  (document.getElementById('cache-enabled') as HTMLInputElement).checked = settings.cacheEnabled;
  (document.getElementById('auto-switch-provider') as HTMLInputElement).checked = settings.autoSwitchProvider;

  updateKeyVisibility('asr', settings.asrProvider);
  updateKeyVisibility('translate', settings.translateProvider);
}

function updateKeyVisibility(type: 'asr' | 'translate', providerId: string): void {
  const providers = type === 'asr' ? ASR_PROVIDERS : TRANSLATE_PROVIDERS;
  const provider = providers.find((p) => p.id === providerId);
  const group = document.getElementById(`${type}-key-group`)!;
  group.style.display = provider && !provider.requiresKey ? 'none' : 'flex';
}

function collectSettings(): Settings {
  return {
    asrProvider: (document.getElementById('asr-provider') as HTMLSelectElement).value,
    asrApiKey: (document.getElementById('asr-api-key') as HTMLInputElement).value,
    translateProvider: (document.getElementById('translate-provider') as HTMLSelectElement).value,
    translateApiKey: (document.getElementById('translate-api-key') as HTMLInputElement).value,
    defaultTargetLang: (document.getElementById('default-target-lang') as HTMLSelectElement).value,
    autoDetectSource: (document.getElementById('auto-detect-source') as HTMLInputElement).checked,
    defaultSourceLang: (document.getElementById('default-source-lang') as HTMLInputElement).value,
    subtitleStyle: {
      fontSize: Number((document.getElementById('subtitle-font-size') as HTMLInputElement).value),
      fontColor: (document.getElementById('subtitle-font-color') as HTMLInputElement).value,
      bgColor: (document.getElementById('subtitle-bg-color') as HTMLInputElement).value,
      position: (document.getElementById('subtitle-position') as HTMLSelectElement).value as 'bottom' | 'top',
      opacity: Number((document.getElementById('subtitle-opacity') as HTMLInputElement).value),
    },
    segmentDuration: Number((document.getElementById('segment-duration') as HTMLInputElement).value),
    cacheEnabled: (document.getElementById('cache-enabled') as HTMLInputElement).checked,
    autoSwitchProvider: (document.getElementById('auto-switch-provider') as HTMLInputElement).checked,
  };
}

document.getElementById('asr-provider')!.addEventListener('change', (e) => {
  updateKeyVisibility('asr', (e.target as HTMLSelectElement).value);
});

document.getElementById('translate-provider')!.addEventListener('change', (e) => {
  updateKeyVisibility('translate', (e.target as HTMLSelectElement).value);
});

document.getElementById('save-btn')!.addEventListener('click', async () => {
  const settings = collectSettings();
  await saveSettings(settings);
  const status = document.getElementById('save-status')!;
  status.textContent = '已保存';
  setTimeout(() => {
    status.textContent = '';
  }, 3000);
});

loadSettings();
