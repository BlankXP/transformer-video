import { Settings } from './types';
import { DEFAULT_SETTINGS, CACHE_EXPIRY_DAYS } from './constants';

export async function getSettings(): Promise<Settings> {
  return new Promise((resolve) => {
    chrome.storage.local.get('settings', (result) => {
      if (result.settings) {
        resolve({ ...DEFAULT_SETTINGS, ...result.settings });
      } else {
        resolve({ ...DEFAULT_SETTINGS });
      }
    });
  });
}

export async function saveSettings(settings: Partial<Settings>): Promise<void> {
  const current = await getSettings();
  const updated = { ...current, ...settings };
  return new Promise((resolve) => {
    chrome.storage.local.set({ settings: updated }, () => resolve());
  });
}

export async function getCachedTranslation(key: string): Promise<string | null> {
  return new Promise((resolve) => {
    chrome.storage.local.get(`cache_${key}`, (result) => {
      const cached = result[`cache_${key}`] as { text: string; timestamp: number } | undefined;
      if (cached && Date.now() - cached.timestamp < CACHE_EXPIRY_DAYS * 24 * 60 * 60 * 1000) {
        resolve(cached.text);
      } else {
        resolve(null);
      }
    });
  });
}

export async function setCachedTranslation(key: string, text: string): Promise<void> {
  return new Promise((resolve) => {
    chrome.storage.local.set({
      [`cache_${key}`]: { text, timestamp: Date.now() },
    }, () => resolve());
  });
}
