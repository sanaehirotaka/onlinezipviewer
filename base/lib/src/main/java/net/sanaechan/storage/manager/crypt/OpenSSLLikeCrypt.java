package net.sanaechan.storage.manager.crypt;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class OpenSSLLikeCrypt {

    public static void encrypt(Path from, Path to, char[] password)
            throws GeneralSecurityException, IOException {
        try (OutputStream out = encrypt(to, password)) {
            Files.copy(from, out);
        }
    }

    public static OutputStream encrypt(Path path, char[] password) throws GeneralSecurityException, IOException {
        return encrypt(new BufferedOutputStream(Files.newOutputStream(path)), password);
    }

    public static OutputStream encrypt(OutputStream out, char[] password) throws GeneralSecurityException, IOException {
        return EncryptionType.EXTEND.encrypt(out, password);
    }

    public static void decrypt(Path from, Path to, char[] password)
            throws GeneralSecurityException, IOException {
        try (InputStream in = decrypt(from, password)) {
            Files.copy(in, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static InputStream decrypt(Path path, char[] password)
            throws GeneralSecurityException, IOException {
        return decrypt(new BufferedInputStream(Files.newInputStream(path)), password);
    }

    public static InputStream decrypt(InputStream in, char[] password) throws GeneralSecurityException, IOException {
        return detectType(in).decrypt(in, password);
    }

    private static EncryptionType detectType(InputStream in) throws IOException {
        byte[] head = new byte[8];
        in.read(head);

        if (Arrays.equals(head, EncryptionBasic.HEADER))
            return EncryptionType.BASIC;

        if (Arrays.equals(head, EncryptionExtend.HEADER))
            return EncryptionType.EXTEND;

        throw new IOException("read error");
    }

    private static class EncryptionBasic {
        static final byte[] HEADER = "Salted__".getBytes();

        static final String ALGORITHM = "PBKDF2WithHmacSHA256";
        static final String TRANSFORMATION = "AES/CTR/NoPadding";

        static OutputStream encrypt(OutputStream out, char[] password) throws GeneralSecurityException, IOException {
            byte[] salt;
            {
                salt = new byte[8];
                SecureRandom.getInstanceStrong().nextBytes(salt);
            }
            SecretKeySpec key;
            IvParameterSpec iv;
            {
                SecretKeyFactory kf = SecretKeyFactory.getInstance(ALGORITHM);
                KeySpec ks = new PBEKeySpec(password, salt, 10000, 48 * 8);
                SecretKey pbekey = kf.generateSecret(ks);
                byte[] encodedKey = pbekey.getEncoded();

                key = new SecretKeySpec(encodedKey, 0, 32, "AES");
                iv = new IvParameterSpec(encodedKey, 32, 16);
            }

            out.write(HEADER);
            out.write(salt);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);

            return new CipherOutputStream(out, cipher);
        }

        static InputStream decrypt(InputStream in, char[] password) throws GeneralSecurityException, IOException {
            byte[] salt;
            {
                salt = new byte[8];
                in.read(salt);
            }
            SecretKeySpec key;
            IvParameterSpec iv;
            {
                SecretKeyFactory kf = SecretKeyFactory.getInstance(ALGORITHM);
                KeySpec ks = new PBEKeySpec(password, salt, 10000, 48 * 8);
                SecretKey pbekey = kf.generateSecret(ks);
                byte[] encodedKey = pbekey.getEncoded();

                key = new SecretKeySpec(encodedKey, 0, 32, "AES");
                iv = new IvParameterSpec(encodedKey, 32, 16);
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, iv);
            return new CipherInputStream(in, cipher);
        }
    }

    private static class EncryptionExtend {
        static final byte[] HEADER = "Extend__".getBytes();

        static final String ALGORITHM = "PBKDF2WithHmacSHA512";
        static final String TRANSFORMATION = "AES/CTR/NoPadding";

        static OutputStream encrypt(OutputStream out, char[] password) throws GeneralSecurityException, IOException {
            byte[] salt;
            {
                salt = new byte[16];
                SecureRandom.getInstanceStrong().nextBytes(salt);
            }
            SecretKeySpec key;
            IvParameterSpec iv;
            {
                SecretKeyFactory kf = SecretKeyFactory.getInstance(ALGORITHM);
                KeySpec ks = new PBEKeySpec(password, salt, 100000, 48 * 8);
                SecretKey pbekey = kf.generateSecret(ks);
                byte[] encodedKey = pbekey.getEncoded();

                key = new SecretKeySpec(encodedKey, 0, 32, "AES");
                iv = new IvParameterSpec(encodedKey, 32, 16);
            }

            out.write(HEADER);
            out.write(salt);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);

            return new CipherOutputStream(out, cipher);
        }

        static InputStream decrypt(InputStream in, char[] password) throws GeneralSecurityException, IOException {
            byte[] salt;
            {
                salt = new byte[16];
                in.read(salt);
            }
            SecretKeySpec key;
            IvParameterSpec iv;
            {
                SecretKeyFactory kf = SecretKeyFactory.getInstance(ALGORITHM);
                KeySpec ks = new PBEKeySpec(password, salt, 100000, 48 * 8);
                SecretKey pbekey = kf.generateSecret(ks);
                byte[] encodedKey = pbekey.getEncoded();

                key = new SecretKeySpec(encodedKey, 0, 32, "AES");
                iv = new IvParameterSpec(encodedKey, 32, 16);
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, iv);
            return new CipherInputStream(in, cipher);
        }
    }

    private enum EncryptionType {
        BASIC {
            @Override
            OutputStream encrypt(OutputStream out, char[] password) throws GeneralSecurityException, IOException {
                return EncryptionBasic.encrypt(out, password);
            }

            @Override
            InputStream decrypt(InputStream in, char[] password) throws GeneralSecurityException, IOException {
                return EncryptionBasic.decrypt(in, password);
            }
        },
        EXTEND {
            @Override
            OutputStream encrypt(OutputStream out, char[] password) throws GeneralSecurityException, IOException {
                return EncryptionExtend.encrypt(out, password);
            }

            @Override
            InputStream decrypt(InputStream in, char[] password) throws GeneralSecurityException, IOException {
                return EncryptionExtend.decrypt(in, password);
            }
        };

        OutputStream encrypt(OutputStream out, char[] password) throws GeneralSecurityException, IOException {
            throw new UnsupportedOperationException();
        }

        InputStream decrypt(InputStream in, char[] password) throws GeneralSecurityException, IOException {
            throw new UnsupportedOperationException();
        }
    }
}
