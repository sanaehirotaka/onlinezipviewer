package net.sanaechan.storage.manager.crypt;

import java.security.SecureRandom;
import java.util.Map;
import java.util.stream.Collectors;

import net.sanaechan.storage.manager.storage.BlobRef;

public interface FileNameEncoder extends AutoCloseable {

    String encode(String name, Map<String, String> metadata);

    String decode(BlobRef ref);

    @Override
    void close();

    public static FileNameEncoder getInstance(String password) {
        try {
            return new FileNameEncoderProxy(password);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isValidFileName(String str) {
        int len = str.length();
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (c <= '\u001F' || c == '\u007F')
                return false;
            if (c == '\u0022'
                    || c == '\u002F'
                    || c == '\u003C'
                    || c == '\u003E'
                    || c == '\u003F'
                    || c == '\u007C')
                return false;
            if (c >= '\uFFFC')
                return false;
        }
        return true;
    }

    public static String generatePassword() {
        SecureRandom rnd = new SecureRandom();
        int len = rnd.nextInt(15, 24);
        return rnd.ints(0x21, 0x7a)
                .filter(v -> Character.isAlphabetic(v))
                .mapToObj(i -> String.valueOf((char) i))
                .limit(len)
                .collect(Collectors.joining());
    }
}