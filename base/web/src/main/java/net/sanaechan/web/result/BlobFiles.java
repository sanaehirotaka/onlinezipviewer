package net.sanaechan.web.result;

import java.util.List;

import lombok.Data;

@Data
public class BlobFiles implements LogicResult {

    private List<File> files;

    private boolean success = true;

    @Data
    public static class File {

        private String bucket;

        private String realName;

        private String displayName;

        private long size;
    }
}
