package net.sanaechan.storage.manager.workspace;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * アプリケーションの設定を読み書きします
 *
 */
public final class Config {

    private static final Path CONFIG_DIR = Paths.get("./config"); //$NON-NLS-1$

    private static final Config DEFAULT = new Config(CONFIG_DIR);

    private final Path dir;

    private final Map<Class<?>, Object> map = new ConcurrentHashMap<>();

    private final ObjectMapper mapper;

    /**
     * アプリケーション設定の読み書きを指定のディレクトリで行います
     *
     * @param dir アプリケーション設定ディレクトリ
     */
    public Config(Path dir) {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.dir = dir;
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
    public synchronized void store() {
        this.map.entrySet().forEach(this::store);
    }

    private void store(Entry<Class<?>, ?> entry) {
        this.write(entry.getKey(), entry.getValue());
    }

    private <T> T read(Class<T> clazz) {
        T instance = null;
        try {
            tryRead: {
                Path filepath = this.jsonPath(clazz);
                // 通常ファイル読み込み
                if (Files.isReadable(filepath) && (Files.size(filepath) > 0)) {
                    try (Reader reader = Files.newBufferedReader(filepath)) {
                        instance = this.mapper.readValue(reader, clazz);
                        break tryRead;
                    } catch (Exception e) {
                        instance = null;
                        e.printStackTrace();
                    }
                }
                // ファイルが読み込めないまたはサイズがゼロの場合バックアップファイルを読み込む
                filepath = this.backupPath(filepath);
                if (Files.isReadable(filepath) && (Files.size(filepath) > 0)) {
                    try (Reader reader = Files.newBufferedReader(filepath)) {
                        instance = this.mapper.readValue(reader, clazz);
                        break tryRead;
                    }
                }
            }
        } catch (Exception e) {
            instance = null;
            LoggerFactory.getLogger(this.getClass()).warn("設定の読み込み中に例外が発生しました。", e);
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
            try (Writer writer = Files.newBufferedWriter(filepath, StandardOpenOption.CREATE)) {
                this.mapper.writeValue(writer, instance);
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(this.getClass()).warn("設定の書き込み中に例外が発生しました。", e);
        }
    }

    private Path jsonPath(Class<?> clazz) {
        return this.dir.resolve(clazz.getCanonicalName() + ".json"); //$NON-NLS-1$
    }

    private Path backupPath(Path filepath) {
        return filepath.resolveSibling(filepath.getFileName() + ".backup"); //$NON-NLS-1$
    }

    /**
     * アプリケーションのデフォルト設定ディレクトリから設定を取得します
     *
     * @return アプリケーションのデフォルト設定ディレクトリ
     */
    public static Config getDefault() {
        return DEFAULT;
    }
}