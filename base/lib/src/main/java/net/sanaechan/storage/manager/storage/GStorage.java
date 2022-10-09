package net.sanaechan.storage.manager.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import net.sanaechan.storage.manager.crypt.FileNameEncoder;

public class GStorage {

    private final ThreadLocal<Storage> storage = new ThreadLocal<>();

    private final BucketConfig config;

    private final ServiceAccountCredentials credentials;

    private List<BlobRef> list;

    public GStorage(BucketConfig config) throws IOException {
        this.config = config;
        this.credentials = ServiceAccountCredentials
                .fromStream(new ByteArrayInputStream(config.getKeyfile().getBytes()));
    }

    public Storage current() {
        Storage storage = this.storage.get();
        if (storage == null) {
            HttpTransportOptions options = HttpTransportOptions.newBuilder()
                    .setConnectTimeout((int) TimeUnit.MILLISECONDS.toSeconds(20))
                    .setReadTimeout((int) TimeUnit.MILLISECONDS.toSeconds(60))
                    .build();
            storage = StorageOptions.newBuilder()
                    .setCredentials(this.credentials)
                    .setTransportOptions(options)
                    .build()
                    .getService();
            this.storage.set(storage);
        }
        return storage;
    }

    public List<BlobRef> list(FileNameEncoder nameEncoder) {
        if (this.list == null) {
            Storage storage = this.current();
            String bucket = this.config.getLocation();
            this.list = StreamSupport.stream(storage.list(bucket).iterateAll().spliterator(), false)
                    .map(blob -> new BlobRef(this, blob, nameEncoder))
                    .filter(r -> r.getName() != null)
                    .collect(Collectors.toList());
            Collections.shuffle(this.list);
        }
        return this.list;
    }

    public void upload(Path path, String fileName, Map<String, String> metadata) throws IOException {
        BlobInfo.Builder builder = BlobInfo.newBuilder(BlobId.of(this.config.getLocation(), fileName));
        if (metadata != null)
            builder.setMetadata(metadata);
        BlobInfo info = builder.build();

        this.current().createFrom(info, path);
        this.update();
    }

    public void update() {
        this.list = null;
    }
}
