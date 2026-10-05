package com.jon.santafelab;

import org.json.JSONObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** Genuine JCE encryption and failure checks. Android storage/Keystore wiring requires device verification. */
public final class AccountStoreTest {
    private static int checks;
    private static final String USER = "fixture+account@example.invalid";
    private static final String PASSWORD = "FIXTURE_PASSWORD_NOT_A_REAL_SECRET_$\\\"_🌲";
    private static final String PIN = "1234";

    private static final class Disk implements AccountStore.RecordFile {
        byte[] record;
        boolean failWrite, ignoreWrite, failRead, failDelete;
        int writes;
        @Override public byte[] read() throws Exception {
            if (failRead) throw new IOException("synthetic read failure " + PASSWORD);
            return record == null ? null : record.clone();
        }
        @Override public void write(byte[] data) throws Exception {
            writes++;
            if (failWrite) throw new IOException("synthetic write failure " + PASSWORD);
            if (!ignoreWrite) record = data.clone();
        }
        @Override public boolean delete() throws Exception {
            if (failDelete) throw new IOException("synthetic delete failure " + PASSWORD);
            record = null;
            return true;
        }
    }
    private static final class TestKeys implements AccountStore.Keys {
        SecretKey key;
        boolean failGet, failDelete;
        int generations;
        @Override public SecretKey get(boolean create) throws Exception {
            if (failGet) throw new IOException("synthetic key failure " + PASSWORD);
            if (key == null && create) {
                byte[] bytes = new byte[32];
                new java.security.SecureRandom().nextBytes(bytes);
                key = new SecretKeySpec(bytes, "AES");
                generations++;
                Arrays.fill(bytes, (byte) 0);
            }
            return key;
        }
        @Override public boolean delete() throws Exception {
            if (failDelete) throw new IOException("synthetic delete failure " + PASSWORD);
            key = null;
            return true;
        }
    }

    private static void require(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }
    private static String account(String user, String password, String pin) throws Exception {
        return new JSONObject().put("username", user).put("password", password).put("pin", pin).toString();
    }
    private static JSONObject loaded(AccountStore store) throws Exception { return new JSONObject(store.loadAccount()); }
    private static void unreadable(AccountStore store, String label) throws Exception {
        String value = store.loadAccount();
        JSONObject result = new JSONObject(value);
        require(!result.getBoolean("saved") && result.length() == 2 && result.opt("error") instanceof String, label);
        require(!value.contains(USER) && !value.contains(PASSWORD) && !value.contains(PIN), label + " has no private values");
    }
    private static byte[] encrypt(SecretKey key, byte[] plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        cipher.updateAAD("Santa Fe API Lab saved account v1".getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(plaintext);
        return ByteBuffer.allocate(5 + 12 + encrypted.length)
            .put(new byte[] {'S', 'F', 'A', 'C', 1}).put(cipher.getIV()).put(encrypted).array();
    }

    public static void main(String[] args) throws Exception {
        Disk disk = new Disk();
        TestKeys keys = new TestKeys();
        AccountStore store = new AccountStore(disk, keys);
        require("{\"saved\":false}".equals(store.loadAccount()), "missing account has no error");
        require(keys.generations == 0, "missing account does not generate a key");
        String original = account(USER, PASSWORD, PIN);
        require(store.saveAccount(original), "account saved");
        require(keys.generations == 1, "one key generated");
        byte[] first = disk.record.clone();
        String bytes = new String(first, StandardCharsets.ISO_8859_1);
        require(!bytes.contains(USER) && !bytes.contains(PASSWORD) && !bytes.contains(PIN), "record contains ciphertext only");
        JSONObject result = loaded(store);
        require(result.getBoolean("saved") && result.length() == 4, "read returns only account and saved flag");
        require(USER.equals(result.getString("username")), "username round trip");
        require(PASSWORD.equals(result.getString("password")), "password unicode and escapes round trip");
        require(PIN.equals(result.getString("pin")), "PIN round trip");
        require(PASSWORD.equals(loaded(new AccountStore(disk, keys)).getString("password")), "new store instance reads saved account");
        require(store.saveAccount(original), "same account can be replaced");
        require(keys.generations == 1, "replacement reuses nonexportable key");
        require(!Arrays.equals(first, disk.record), "identical plaintext has different ciphertext");
        require(!Arrays.equals(Arrays.copyOfRange(first, 5, 17), Arrays.copyOfRange(disk.record, 5, 17)), "fresh nonce for each encryption");
        byte[] valid = disk.record.clone();

        disk.record[20] ^= 1;
        unreadable(store, "modified ciphertext fails authentication");
        disk.record = valid.clone(); disk.record[5] ^= 1;
        unreadable(store, "modified nonce fails authentication");
        disk.record = valid.clone(); disk.record[4] = 2;
        unreadable(store, "unknown record version rejected");
        disk.record = Arrays.copyOf(valid, 20);
        unreadable(store, "truncated record rejected");
        disk.record = Arrays.copyOf(valid, valid.length + 1);
        unreadable(store, "extra ciphertext byte rejected");
        disk.record = new byte[30001];
        unreadable(store, "oversized record rejected");
        disk.record = valid.clone();
        SecretKey correctKey = keys.key;
        keys.key = new SecretKeySpec(new byte[32], "AES");
        unreadable(store, "different key rejected");
        keys.key = null;
        unreadable(store, "missing key rejected");
        require(keys.generations == 1, "loading encrypted record never generates replacement key");
        keys.key = correctKey;
        keys.failGet = true;
        unreadable(store, "unavailable key is a generic error");
        require(!store.saveAccount(original) && Arrays.equals(valid, disk.record), "key failure preserves committed record");
        keys.failGet = false;
        disk.failRead = true;
        unreadable(store, "unavailable file is a generic error");
        disk.failRead = false;

        String[] invalid = {
            null, "", "[]", "{}", "not json",
            account("no-at-symbol", PASSWORD, PIN), account("bad name@example.invalid", PASSWORD, PIN),
            account("bad\nname@example.invalid", PASSWORD, PIN), account("two@@example.invalid", PASSWORD, PIN),
            account("x".repeat(320) + "@x", PASSWORD, PIN),
            account(USER, "", PIN), account(USER, "x".repeat(4097), PIN), account(USER, "a\u0000b", PIN),
            account(USER, PASSWORD, "1"), account(USER, PASSWORD, "12345"), account(USER, PASSWORD, "12a4"),
            account(USER, PASSWORD, "１２３４"),
            new JSONObject().put("username", USER).put("password", PASSWORD).toString(),
            new JSONObject().put("username", USER).put("password", PASSWORD).put("pin", 1234).toString(),
            new JSONObject().put("username", USER).put("password", PASSWORD).put("pin", PIN).put("accessToken", "do-not-store").toString(),
            new JSONObject().put("username", USER).put("password", JSONObject.NULL).put("pin", PIN).toString(),
            " ".repeat(10001)
        };
        int writesBeforeInvalid = disk.writes;
        for (int i = 0; i < invalid.length; i++)
            require(!store.saveAccount(invalid[i]) && Arrays.equals(valid, disk.record), "invalid account " + i + " does not replace record");
        require(writesBeforeInvalid == disk.writes, "invalid input never reaches disk write");

        disk.record = encrypt(keys.key, "{\"username\":\"fixture@example.invalid\",\"password\":\"test\",\"pin\":\"1234\",\"token\":\"reject\"}".getBytes(StandardCharsets.UTF_8));
        unreadable(store, "authenticated record with extra fields rejected");
        disk.record = encrypt(keys.key, new byte[] {(byte) 0xc3, (byte) 0x28});
        unreadable(store, "malformed UTF8 rejected");
        disk.record = valid.clone();
        disk.failWrite = true;
        require(!store.saveAccount(account(USER, "replacement", PIN)) && Arrays.equals(valid, disk.record), "failed write preserves earlier account");
        disk.failWrite = false; disk.ignoreWrite = true;
        require(!store.saveAccount(original) && Arrays.equals(valid, disk.record), "silent failed commit not reported as success");
        disk.ignoreWrite = false;
        require(store.saveAccount(account("  " + USER + "  ", "  pass  ", "")), "read-only account saves with empty PIN");
        result = loaded(store);
        require(USER.equals(result.getString("username")), "email trimmed on save");
        require("  pass  ".equals(result.getString("password")), "password whitespace preserved");
        require("".equals(result.getString("pin")), "optional PIN round trip");

        require(store.forgetAccount(), "forget deletes encrypted record and key");
        require(disk.record == null && keys.key == null, "record and key gone");
        require("{\"saved\":false}".equals(store.loadAccount()), "forgotten account is missing");
        require(store.forgetAccount(), "forget is idempotent");
        require(store.saveAccount(original) && keys.generations == 2, "saving after forget generates a new key");
        disk.failDelete = true;
        require(!store.forgetAccount(), "ciphertext deletion failure is reported");
        require(keys.key == null && disk.record != null, "key still deleted when file deletion fails");
        unreadable(store, "undeleted ciphertext cannot be reopened after key deletion");
        disk.failDelete = false;
        require(store.forgetAccount(), "delete failed ciphertext on retry");
        require(store.saveAccount(original), "save fixture for key deletion failure");
        keys.failDelete = true;
        require(!store.forgetAccount(), "key deletion failure is reported");
        require(disk.record == null && keys.key != null, "record still deleted when key deletion fails");
        keys.failDelete = false;
        require(store.forgetAccount(), "delete lingering key on retry");

        System.out.println("PASS AccountStoreTest: " + checks + " checks (real AES/GCM; Android Keystore and AtomicFile wiring require device verification)");
    }
}
