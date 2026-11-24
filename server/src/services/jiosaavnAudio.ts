/**
 * JioSaavn Audio Decryption Service
 * Uses DES-ECB with key '38346591' to decrypt JioSaavn's encrypted_media_url
 * into direct, unproxied 320kbps Akamai CDN mp4 audio stream URLs.
 */

import CryptoJS from 'crypto-js';

const DES_KEY = '38346591';

/**
 * Decrypts a JioSaavn encrypted_media_url into a 320kbps CDN stream URL.
 */
export function decryptJioSaavnMediaUrl(encryptedUrl?: string | null): string | null {
  if (!encryptedUrl || typeof encryptedUrl !== 'string') return null;
  const trimmed = encryptedUrl.trim();
  if (!trimmed) return null;

  try {
    const key = CryptoJS.enc.Utf8.parse(DES_KEY);
    const cipherParams = CryptoJS.lib.CipherParams.create({
      ciphertext: CryptoJS.enc.Base64.parse(trimmed),
    });
    const decrypted = CryptoJS.DES.decrypt(cipherParams, key, {
      mode: CryptoJS.mode.ECB,
      padding: CryptoJS.pad.Pkcs7,
    });
    const rawUrl = decrypted.toString(CryptoJS.enc.Utf8);
    if (rawUrl && rawUrl.startsWith('http')) {
      return rawUrl.replace(/_96\.mp4|_160\.mp4/, '_320.mp4');
    }
  } catch {
    // Return null on decryption error
  }

  return null;
}
