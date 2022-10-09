package net.sanaechan.storage.manager.storage;

import lombok.Data;

@Data
public class BucketConfig {

    private long timestamp;

    private String name;

    private String location;

    private String password;

    private String keyfile;

    @Override
    public String toString() {
        return this.name;
    }

    public void update() {
        this.timestamp = this.timestamp ^ (this.timestamp << 13);
        this.timestamp = this.timestamp ^ (this.timestamp >> 17);
        this.timestamp = this.timestamp ^ (this.timestamp << 5);
    }
}
