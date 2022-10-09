package net.sanaechan.storage.manager.storage;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage.CopyRequest;

import net.sanaechan.storage.manager.crypt.FileNameEncoder;

public class BlobRef {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.JAPAN);

    private final GStorage storage;

    private final Blob blob;

    private final FileNameEncoder nameEncoder;

    public BlobRef(GStorage storage, Blob blob, FileNameEncoder nameEncoder) {
        this.storage = storage;
        this.blob = blob;
        this.nameEncoder = nameEncoder;
    }

    private String realName;

    public String getRealName() {
        if (this.realName == null) {
            this.realName = this.blob.getName();
        }
        return this.realName;
    }

    private Optional<String> name;

    @Nullable
    public String getName() {
        if (this.name == null) {
            this.name = Optional.ofNullable(this.nameEncoder.decode(this));
        }
        return this.name.orElse(null);
    }

    private Optional<String> displayName;

    @Nullable
    public String getDisplayName() {
        if (this.displayName == null) {
            String name = this.getName();
            if (name != null) {
                int idx = name.lastIndexOf('.');
                if (idx != -1) {
                    this.displayName = Optional.of(name.substring(0, idx));
                } else {
                    this.displayName = Optional.of(name);
                }
            } else {
                this.displayName = Optional.empty();
            }
        }
        return this.displayName.orElse(null);
    }

    private Optional<String> fileKey;

    @Nullable
    public String getFileKey() {
        if (this.fileKey == null) {
            String name = this.getName();
            if (name != null) {
                int idx = name.lastIndexOf('.');
                if (idx != -1) {
                    this.fileKey = Optional.of(name.substring(idx + 1));
                } else {
                    this.fileKey = Optional.empty();
                }
            } else {
                this.fileKey = Optional.empty();
            }
        }
        return this.fileKey.orElse(null);
    }

    private String displayDate;

    public String getDisplayDate() {
        if (this.displayDate == null) {
            Instant instant = Instant.ofEpochMilli(this.blob.getCreateTime());
            ZonedDateTime time = ZonedDateTime.ofInstant(instant, ZoneId.of("Asia/Tokyo"));
            this.displayDate = FORMATTER.format(time);
        }
        return this.displayDate;
    }

    public long getSize() {
        return this.blob.getSize();
    }

    private String storageClass;

    public String getStorageClass() {
        if (this.storageClass == null) {
            this.storageClass = this.blob.getStorageClass().name().toLowerCase();
        }
        return this.storageClass;
    }

    private Map<String, String> metadata;

    public Map<String, String> getMetadata() {
        if (this.metadata == null) {
            if ((this.metadata = this.blob.getMetadata()) == null) {
                this.metadata = Collections.emptyMap();
            }
        }
        return this.metadata;
    }

    public void transferTo(OutputStream out) throws IOException {
        this.blob.downloadTo(out);
    }

    public void delete() {
        this.storage.current().delete(this.blob.getBlobId());
        this.storage.update();
    }

    public void move(String newName, Map<String, String> metadata) {
        this.move(this.blob.getBucket(), newName, metadata);
    }

    public void move(String bucket, String newName, Map<String, String> metadata) {
        BlobInfo target = BlobInfo.newBuilder(BlobId.of(bucket, newName))
                .setMetadata(metadata)
                .build();
        CopyRequest copyRequest = CopyRequest.newBuilder()
                .setSource(this.blob.getBlobId())
                .setTarget(target)
                .build();
        this.storage.current().copy(copyRequest).getResult();
        this.delete();
    }
}
