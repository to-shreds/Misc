export const FORMAT = "torbox-tv-portable-key-v1";
export const AAD = "torbox-tv-portable-vault-v1";
export const ITERATIONS = 180000;
const utf8 = new TextEncoder();
const decoder = new TextDecoder("utf-8", {fatal:true});
function encode64(bytes) { return btoa(Array.from(bytes, b => String.fromCharCode(b)).join("")); }
function decode64(text) {
  if (!/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(text)) throw Error("Invalid vault encoding");
  return Uint8Array.from(atob(text), character => character.charCodeAt(0));
}
async function derive(password, salt, cryptoProvider) {
  const base = await cryptoProvider.subtle.importKey("raw", utf8.encode(password), "PBKDF2", false, ["deriveKey"]);
  return cryptoProvider.subtle.deriveKey(
    {name:"PBKDF2", salt, iterations:ITERATIONS, hash:"SHA-256"},
    base, {name:"AES-GCM", length:256}, false, ["encrypt", "decrypt"]
  );
}
export async function encryptPortableKey(apiKey, password, cryptoProvider = globalThis.crypto) {
  apiKey = apiKey.trim();
  if (apiKey.length < 16 || apiKey.length > 512 || /\s|[\x00-\x1f\x7f]/.test(apiKey))
    throw Error("Enter a complete TorBox API key.");
  if (password.length < 4 || password.length > 128)
    throw Error("Choose an app password of at least four characters.");
  if (!cryptoProvider?.subtle || !cryptoProvider?.getRandomValues) throw Error("Your browser does not support secure local encryption.");
  const salt = cryptoProvider.getRandomValues(new Uint8Array(16));
  const iv = cryptoProvider.getRandomValues(new Uint8Array(12));
  const key = await derive(password, salt, cryptoProvider);
  const ciphertext = new Uint8Array(await cryptoProvider.subtle.encrypt(
    {name:"AES-GCM", iv, additionalData:utf8.encode(AAD), tagLength:128}, key, utf8.encode(apiKey)
  ));
  return JSON.stringify({format:FORMAT, iterations:ITERATIONS, salt:encode64(salt), iv:encode64(iv), ciphertext:encode64(ciphertext)}, null, 2) + "\n";
}
export async function decryptPortableKey(serialized, password, cryptoProvider = globalThis.crypto) {
  const obj = JSON.parse(serialized);
  if (Object.keys(obj).length !== 5 || obj.format !== FORMAT || obj.iterations !== ITERATIONS) throw Error("Unsupported vault");
  const salt = decode64(obj.salt), iv = decode64(obj.iv), data = decode64(obj.ciphertext);
  if (salt.length !== 16 || iv.length !== 12 || data.length < 32 || data.length > 1024) throw Error("Invalid vault");
  const key = await derive(password, salt, cryptoProvider);
  return decoder.decode(await cryptoProvider.subtle.decrypt(
    {name:"AES-GCM",iv,additionalData:utf8.encode(AAD),tagLength:128},key,data
  ));
}
