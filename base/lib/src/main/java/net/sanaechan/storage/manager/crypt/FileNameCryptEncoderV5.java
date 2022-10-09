package net.sanaechan.storage.manager.crypt;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import net.sanaechan.storage.manager.storage.BlobRef;

public class FileNameCryptEncoderV5 implements FileNameEncoder {

    private final Cipher cipher;
    private final MessageDigest digest;
    private final Holder<byte[]> key;

    private final Holder<Deflater> deflater = new Holder<>(() -> new Deflater(Deflater.BEST_COMPRESSION, true));
    private final Holder<Inflater> inflater = new Holder<>(() -> new Inflater(true));
    private final Holder<SecureRandom> rnd = new Holder<>(SecureRandom::new);

    public FileNameCryptEncoderV5(String password) {
        try {
            this.cipher = Cipher.getInstance("AES/CTR/NoPadding");
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        this.key = new Holder<>(() -> {
            try {
                byte[] salt = this.digest.digest(password.getBytes());
                SecretKeyFactory kf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                KeySpec ks = new PBEKeySpec(password.toCharArray(), salt, 10000, 32 * 8);
                SecretKey pbekey = kf.generateSecret(ks);
                return pbekey.getEncoded();
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Override
    public String encode(String name, Map<String, String> metadata) {
        try {
            boolean isUnicode = !Charset.forName("MS932").newEncoder().canEncode(name);
            boolean isCompress;
            byte[] rawBytes = name.getBytes(isUnicode ? StandardCharsets.UTF_8 : Charset.forName("MS932"));
            {
                byte[] buffer = new byte[rawBytes.length + 16];
                Deflater deflater = this.deflater.get();
                deflater.reset();
                deflater.setInput(rawBytes);
                deflater.finish();
                int len;
                if ((len = deflater.deflate(buffer, 0, buffer.length, Deflater.FULL_FLUSH)) < rawBytes.length) {
                    isCompress = true;
                    rawBytes = Arrays.copyOfRange(buffer, 0, len);
                } else {
                    isCompress = false;
                }
            }
            String version = "5"
                    + (isUnicode ? "u" : "s")
                    + (isCompress ? "c" : "_");

            setMetadata(name, metadata, version);

            byte[] salt = new byte[4];
            this.rnd.get().nextBytes(salt);

            byte[] encodedBytes = new byte[salt.length + rawBytes.length];
            System.arraycopy(salt, 0, encodedBytes, 0, salt.length);

            byte[] iv = this.digest.digest(salt);
            this.cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(this.key.get(), "AES"),
                    new IvParameterSpec(iv, 0, 16));
            this.cipher.doFinal(rawBytes, 0, rawBytes.length, encodedBytes, salt.length);

            return Base64.getUrlEncoder().encodeToString(encodedBytes).replace("=", "");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String decode(BlobRef ref) {
        try {
            String version = ref.getMetadata().getOrDefault("_v", "5u_");
            boolean isUnicode = version.charAt(1) == 'u';
            boolean isCompress = version.charAt(2) == 'c';

            byte[] encodedBytes = Base64.getUrlDecoder().decode(ref.getRealName());
            byte[] rawBytes = new byte[encodedBytes.length - 4];

            this.digest.update(encodedBytes, 0, 4);
            byte[] iv = this.digest.digest();
            this.cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(this.key.get(), "AES"),
                    new IvParameterSpec(iv, 0, 16));
            this.cipher.doFinal(encodedBytes, 4, encodedBytes.length - 4, rawBytes, 0);

            if (isCompress) {
                Inflater inflater = this.inflater.get();
                inflater.reset();
                inflater.setInput(rawBytes);
                int total = 0;
                List<byte[]> buffers = new ArrayList<>();
                while (!inflater.finished()) {
                    byte[] buffer = new byte[128];
                    int len = inflater.inflate(buffer);
                    if (len > 0) {
                        buffers.add(buffer);
                        total += len;
                    }
                }
                rawBytes = new byte[total];
                int remaining = total;
                for (byte[] buffer : buffers) {
                    int len = Math.min(remaining, buffer.length);
                    int position = total - remaining;
                    System.arraycopy(buffer, 0, rawBytes, position, len);
                    remaining -= len;
                }
            }
            return new String(rawBytes, isUnicode ? StandardCharsets.UTF_8 : Charset.forName("MS932"));
        } catch (GeneralSecurityException | DataFormatException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        if (!this.key.isEmpty())
            Arrays.fill(this.key.get(), (byte) 0);
        if (!this.deflater.isEmpty())
            this.deflater.get().end();
        if (!this.inflater.isEmpty())
            this.inflater.get().end();
    }

    private static void setMetadata(String text, Map<String, String> metadata, String version) {
        metadata.put("_v", version);

    }
}
