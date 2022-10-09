package net.sanaechan.storage.manager.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import net.sanaechan.storage.manager.crypt.OpenSSLLikeCrypt;

/**
 * アプリケーションの設定を読み書きします
 *
 */
public final class EncryptedConfig implements AutoCloseable {

    private static final byte[][] SALTS = new byte[][] {
            Base64.getDecoder().decode("Q/TiLTkO/F2VmY3FKvxcVA=="),
            Base64.getDecoder().decode("dp07ISNZCQHarROLPAI8ag=="),
            Base64.getDecoder().decode("HbzJbGMmCXGVb70KMAp+ww=="),
    };

    private final Path dir;

    private final Map<Class<?>, Object> map = new ConcurrentHashMap<>();

    private final ObjectMapper mapper;

    private final char[] password;

    private final char[][] passwords;

    /**
     * アプリケーション設定の読み書きを指定のディレクトリで行います
     *
     * @param dir アプリケーション設定ディレクトリ
     * @throws GeneralSecurityException
     */
    public EncryptedConfig(Path dir, char[] password) throws IOException {
        try {
            this.mapper = new ObjectMapper();
            this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
            this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            this.dir = dir;
            this.passwords = passwords(password);
            this.password = password(this.passwords);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private static char[][] passwords(char[] password) throws GeneralSecurityException {
        char[][] passwords = new char[SALTS.length][];
        for (int i = 0; i < SALTS.length; i++) {
            int index = i;
            byte[] key;
            try {
                SecretKeyFactory kf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
                KeySpec ks = new PBEKeySpec(password, SALTS[index], 100000, 32 * 8);
                SecretKey pbekey = kf.generateSecret(ks);
                key = pbekey.getEncoded();
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
            if (key.length == 0) {
                throw new IllegalStateException("key.length == 0");
            }
            passwords[index] = Base64.getEncoder().encodeToString(key).toCharArray();
        }
        return passwords;
    }

    private static char[] password(char[][] passwords) {
        return passwords[new SecureRandom().nextInt(SALTS.length)];
    }

    /**
     * clazzで指定された型からインスタンスを復元します
     *
     * @param <T>   Bean型
     * @param clazz Bean型 Classオブジェクト
     * @param def   デフォルト値を供給するSupplier
     * @return 設定
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clazz, Supplier<T> def) {
        Objects.requireNonNull(clazz);
        Objects.requireNonNull(def);

        T instance = (T) this.map.computeIfAbsent(clazz, key -> {
            T v = this.read((Class<T>) key);
            if (v == null) {
                v = def.get();
            }
            return v;
        });
        return instance;
    }

    public <T> void set(Class<T> clazz, T value) {
        Objects.requireNonNull(clazz);
        this.map.put(clazz, value);
    }

    /**
     * 読み込まれたすべてのインスタンスをファイルに書き込みます
     */
    @Override
    public void close() {
        this.map.entrySet().forEach(this::store);
        this.map.clear();
        Arrays.fill(this.password, (char) 0);
        for (char[] password : this.passwords) {
            Arrays.fill(password, (char) 0);
        }
    }

    private void store(Entry<Class<?>, ?> entry) {
        this.write(entry.getKey(), entry.getValue());
    }

    private <T> T read(Class<T> clazz) {
        T instance = null;
        try {
            tryRead: {
                Path filepath = this.jsonPath(clazz);
                IOException suppress = null;
                // 通常ファイル読み込み
                if (Files.isReadable(filepath) && (Files.size(filepath) > 0)) {
                    for (char[] password : this.passwords) {
                        try (InputStream in = OpenSSLLikeCrypt.decrypt(filepath, password)) {
                            instance = this.mapper.readValue(in, clazz);
                            break tryRead;
                        } catch (IOException e) {
                            suppress = e;
                        }
                    }
                }
                // ファイルが読み込めないまたはサイズがゼロの場合バックアップファイルを読み込む
                filepath = this.backupPath(filepath);
                if (Files.isReadable(filepath) && (Files.size(filepath) > 0)) {
                    for (char[] password : this.passwords) {
                        try (InputStream in = OpenSSLLikeCrypt.decrypt(filepath, password)) {
                            instance = this.mapper.readValue(in, clazz);
                            break tryRead;
                        } catch (IOException e) {
                            suppress = e;
                        }
                    }
                }
                if (suppress != null)
                    throw suppress;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return instance;
    }

    private void write(Class<?> clazz, Object instance) {
        try {
            Path filepath = this.jsonPath(clazz);

            // create parent directory
            if (!Files.exists(filepath)) {
                Path parent = filepath.getParent();
                if (parent != null) {
                    if (!Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }
                }
            }

            // write JSON
            if (Files.exists(filepath) && (Files.size(filepath) > 0)) {
                Path backup = this.backupPath(filepath);
                // ファイルが存在してかつサイズが0を超える場合、ファイルをバックアップにリネームする
                Files.move(filepath, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            try (OutputStream out = OpenSSLLikeCrypt.encrypt(filepath, this.password)) {
                this.mapper.writeValue(out, instance);
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(this.getClass()).warn("設定の書き込み中に例外が発生しました。", e);
        }
    }

    private Path jsonPath(Class<?> clazz) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        String name = Base64.getUrlEncoder().encodeToString(digest.digest(clazz.getCanonicalName().getBytes()))
                .replace("=", "");
        return this.dir.resolve(name); //$NON-NLS-1$
    }

    private Path backupPath(Path filepath) {
        return filepath.resolveSibling(filepath.getFileName() + ".backup"); //$NON-NLS-1$
    }
}