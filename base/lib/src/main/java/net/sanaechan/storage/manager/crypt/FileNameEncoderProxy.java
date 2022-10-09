package net.sanaechan.storage.manager.crypt;

import java.security.GeneralSecurityException;
import java.util.Map;

import net.sanaechan.storage.manager.storage.BlobRef;

class FileNameEncoderProxy implements FileNameEncoder {

    private Holder<FileNameEncoder> v5;

    FileNameEncoderProxy(String password) throws GeneralSecurityException {
        this.v5 = new Holder<>(() -> new FileNameCryptEncoderV5(password));
    }

    @Override
    public String encode(String name, Map<String, String> metadata) {
        return this.v5.get().encode(name, metadata);
    }

    @Override
    public String decode(BlobRef ref) {
        return this.v5.get().decode(ref);
    }

    @Override
    public void close() {
        if (!this.v5.isEmpty())
            this.v5.get().close();
    }

}
