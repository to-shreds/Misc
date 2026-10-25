import test from "node:test";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import { encryptPortableKey, decryptPortableKey, FORMAT, ITERATIONS } from "./vault.mjs";

const fixture='{"format":"torbox-tv-portable-key-v1","iterations":180000,"salt":"AAECAwQFBgcICQoLDA0ODw==","iv":"AAECAwQFBgcICQoL","ciphertext":"TUO+pL5WEk5psfKLfZkpzeDqjC/J/xf5LF4iL1i4ZvZMlka5W1hWcmAwppyBV+i2"}';
test("The Java/Android golden AES-GCM vault decrypts in Web Crypto", async () => {
  assert.equal(await decryptPortableKey(fixture,"reusable sample password",webcrypto),"0123456789abcdef0123456789abcdef");
});
test("encryption makes a portable file and always uses fresh IV/salt", async () => {
  const key="0123456789abcdef0123456789abcdef";
  const first=await encryptPortableKey(key,"my app password",webcrypto);
  const second=await encryptPortableKey(key,"my app password",webcrypto);
  assert.notEqual(first,second);
  assert.equal(JSON.parse(first).format,FORMAT);
  assert.equal(JSON.parse(first).iterations,ITERATIONS);
  assert.equal(await decryptPortableKey(first,"my app password",webcrypto),key);
  await assert.rejects(decryptPortableKey(first,"wrong password",webcrypto));
  assert.ok(!first.includes(key));
});
test("malformed input and incomplete API keys are rejected",async()=>{
  await assert.rejects(encryptPortableKey("short","test",webcrypto));
  await assert.rejects(encryptPortableKey("0123456789abcdef0123456789abcdef","abc",webcrypto));
  const doc=JSON.parse(fixture);doc.iterations=1;
  await assert.rejects(decryptPortableKey(JSON.stringify(doc),"reusable sample password",webcrypto));
});
