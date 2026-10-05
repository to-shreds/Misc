package com.jon.santafelab;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** One encrypted account, kept outside automatic backups. Never persists access tokens. */
final class AccountStore {
    static final String FILE_NAME = "saved-account-v1.bin";
    static final String KEY_ALIAS = "com.jon.santafelab.saved-account.v1";
    private static final byte[] HEADER = new byte[] {'S', 'F', 'A', 'C', 1};
    private static final byte[] AAD = "Santa Fe API Lab saved account v1".getBytes(StandardCharsets.UTF_8);
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MAX_JSON_CHARS = 10000;
    private static final int MAX_FILE_BYTES = 30000;
    private static final String READ_ERROR = "{\"saved\":false,\"error\":\"Could not read the saved account. Save it again in Settings.\"}";
    // AtomicFile does not provide threading protection. Also serialize distinct Activity instances.
    private static final Object STORE_LOCK = new Object();
    private final RecordFile file;
    private final Keys keys;

    interface RecordFile {
        byte[] read() throws Exception; // null when no committed account exists
        void write(byte[] ciphertext) throws Exception;
        boolean delete() throws Exception;
    }
    interface Keys {
        SecretKey get(boolean create) throws Exception;
        boolean delete() throws Exception;
    }

    AccountStore(Context context) {
        this(new PrivateRecordFile(new File(context.getNoBackupFilesDir(), FILE_NAME)), new KeystoreKeys());
    }

    // Tests use genuine AES/GCM with replaceable disk/key failure points, not Android stub behavior.
    AccountStore(RecordFile file, Keys keys) { this.file = file; this.keys = keys; }

    String loadAccount() {
        synchronized (STORE_LOCK) {
            byte[] plaintext = null;
            try {
                byte[] record = file.read();
                if (record == null) return "{\"saved\":false}";
                if (record.length < HEADER.length + IV_BYTES + TAG_BITS / 8 || record.length > MAX_FILE_BYTES ||
                    !Arrays.equals(HEADER, Arrays.copyOf(record, HEADER.length))) throw new IOException();
                SecretKey key = keys.get(false);
                if (key == null) throw new IOException();
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(record, HEADER.length, HEADER.length + IV_BYTES)));
                cipher.updateAAD(AAD);
                plaintext = cipher.doFinal(record, HEADER.length + IV_BYTES, record.length - HEADER.length - IV_BYTES);
                String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(plaintext)).toString();
                JSONObject account = validatedAccount(json);
                account.put("saved", true);
                return account.toString();
            } catch (Exception ignored) {
                // A corrupted file, unavailable key, or malformed record cannot expose stored data.
                return READ_ERROR;
            } finally {
                if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
            }
        }
    }

    boolean saveAccount(String json) {
        synchronized (STORE_LOCK) {
            byte[] plaintext = null;
            try {
                plaintext = validatedAccount(json).toString().getBytes(StandardCharsets.UTF_8);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                // The provider creates a fresh random IV. AndroidKeyStore forbids caller-selected
                // encryption IVs for this key, preventing accidental nonce reuse.
                cipher.init(Cipher.ENCRYPT_MODE, keys.get(true));
                cipher.updateAAD(AAD);
                byte[] iv = cipher.getIV();
                if (iv == null || iv.length != IV_BYTES) throw new IOException();
                byte[] ciphertext = cipher.doFinal(plaintext);
                byte[] record = ByteBuffer.allocate(HEADER.length + IV_BYTES + ciphertext.length)
                    .put(HEADER).put(iv).put(ciphertext).array();
                if (record.length > MAX_FILE_BYTES) throw new IOException();
                file.write(record);
                // AtomicFile.finishWrite is void. Confirm its committed bytes before reporting success.
                return Arrays.equals(record, file.read());
            } catch (Exception ignored) {
                return false;
            } finally {
                if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
            }
        }
    }

    boolean forgetAccount() {
        synchronized (STORE_LOCK) {
            boolean removedFile = false, removedKey = false;
            try { removedFile = file.delete(); } catch (Exception ignored) { }
            // Delete the key even if deletion of ciphertext failed, so it cannot be decrypted later.
            try { removedKey = keys.delete(); } catch (Exception ignored) { }
            return removedFile && removedKey;
        }
    }

    private static JSONObject validatedAccount(String json) throws Exception {
        if (json == null || json.length() > MAX_JSON_CHARS) throw new IOException();
        JSONObject input = new JSONObject(json);
        if (input.length() != 3 || !(input.opt("username") instanceof String) ||
            !(input.opt("password") instanceof String) || !(input.opt("pin") instanceof String)) throw new IOException();
        String username = input.getString("username").trim();
        String password = input.getString("password");
        String pin = input.getString("pin");
        if (username.length() == 0 || username.length() > 320 ||
            !username.matches("[^\\s@\\p{Cntrl}]+@[^\\s@\\p{Cntrl}]+") ||
            password.length() == 0 || password.length() > 4096 || password.indexOf('\u0000') >= 0 ||
            !(pin.isEmpty() || pin.matches("[0-9]{4}"))) throw new IOException();
        JSONObject account = new JSONObject();
        account.put("username", username);
        account.put("password", password);
        account.put("pin", pin);
        return account;
    }

    private static final class KeystoreKeys implements Keys {
        private KeyStore store() throws Exception {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            return store;
        }
        @Override public SecretKey get(boolean create) throws Exception {
            KeyStore store = store();
            java.security.Key found = store.getKey(KEY_ALIAS, null);
            if (found != null) {
                if (!(found instanceof SecretKey)) throw new IOException();
                return (SecretKey) found;
            }
            if (!create) return null;
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false).build());
            return generator.generateKey();
        }
        @Override public boolean delete() throws Exception {
            KeyStore store = store();
            store.deleteEntry(KEY_ALIAS);
            return !store.containsAlias(KEY_ALIAS);
        }
    }

    private static final class PrivateRecordFile implements RecordFile {
        private final AtomicFile atomic;
        private final File base;
        PrivateRecordFile(File base) { this.base = base; atomic = new AtomicFile(base); }
        @Override public byte[] read() throws Exception {
            // API 26-29 may restore the backup on openRead; newer versions use a .new file.
            if (!base.exists() && !new File(base.getPath() + ".bak").exists()) return null;
            try (FileInputStream input = atomic.openRead(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_FILE_BYTES) throw new IOException();
                    output.write(buffer, 0, count);
                }
                return output.toByteArray();
            }
        }
        @Override public void write(byte[] ciphertext) throws Exception {
            FileOutputStream output = null;
            try {
                output = atomic.startWrite();
                output.write(ciphertext);
                output.getFD().sync();
                atomic.finishWrite(output);
                output = null;
            } finally {
                if (output != null) atomic.failWrite(output);
            }
        }
        @Override public boolean delete() {
            atomic.delete();
            return !base.exists() && !new File(base.getPath() + ".bak").exists() &&
                !new File(base.getPath() + ".new").exists();
        }
    }
}
